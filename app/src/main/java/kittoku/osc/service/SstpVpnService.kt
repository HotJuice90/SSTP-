package kittoku.osc.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Network
import android.net.TrafficStats
import android.net.VpnService
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.service.quicksettings.TileService
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import home.keenetic.sstp.R
import kittoku.osc.SharedBridge
import kittoku.osc.activity.MainActivity
import kittoku.osc.control.Controller
import kittoku.osc.control.LogWriter
import kittoku.osc.control.UnderlyingNetworkObserver
import kittoku.osc.preference.OscPrefKey
import kittoku.osc.preference.STATE_CONNECTED
import kittoku.osc.preference.STATE_CONNECTING
import kittoku.osc.preference.STATE_DISCONNECTED
import kittoku.osc.preference.STATE_RECONNECTING
import kittoku.osc.preference.accessor.getBooleanPrefValue
import kittoku.osc.preference.accessor.getStringPrefValue
import kittoku.osc.preference.accessor.getURIPrefValue
import kittoku.osc.preference.accessor.setBooleanPrefValue
import kittoku.osc.preference.accessor.setStringPrefValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


internal const val ACTION_VPN_CONNECT = "kittoku.osc.connect"
internal const val ACTION_VPN_DISCONNECT = "kittoku.osc.disconnect"

internal const val NOTIFICATION_ERROR_CHANNEL = "ERROR"
internal const val NOTIFICATION_RECONNECT_CHANNEL = "RECONNECT"
internal const val NOTIFICATION_DISCONNECT_CHANNEL = "DISCONNECT"
internal const val NOTIFICATION_CERTIFICATE_CHANNEL = "CERTIFICATE"

internal const val NOTIFICATION_ERROR_ID = 1
internal const val NOTIFICATION_RECONNECT_ID = 2
internal const val NOTIFICATION_DISCONNECT_ID = 3
internal const val NOTIFICATION_CERTIFICATE_ID = 4


internal class SstpVpnService : VpnService() {
    private lateinit var prefs: SharedPreferences
    private lateinit var listener: SharedPreferences.OnSharedPreferenceChangeListener
    private lateinit var notificationManager: NotificationManagerCompat
    internal lateinit var scope: CoroutineScope

    internal var logWriter: LogWriter? = null
    private var controller: Controller?  = null

    private var jobReconnect: Job? = null
    private var networkObserver: UnderlyingNetworkObserver? = null
    private var jobNotification: Job? = null

    // Пауза перед попыткой переподключения, в секундах. Дальше последнего элемента
    // не растёт: сервер может быть недоступен часами, а телефон должен подхватить
    // соединение в течение минуты после того, как связь вернётся.
    private val backoffSeconds = intArrayOf(1, 2, 4, 8, 15, 30, 60)
    private var reconnectAttempt = 0

    // Смена сети — не отказ сервера: ждать перед первой попыткой незачем.
    private var isImmediateReconnectRequested = false
    private var reconnectStartedAt = 0L

    // Резолв имени сервера кэшируется: во время реконнекта tun ещё может быть жив,
    // и тогда DNS указывает на недостижимый роутер.
    private var cachedHostname: String? = null
    private var cachedAddress: InetAddress? = null

    private fun setRootState(state: Boolean) {
        setBooleanPrefValue(state, OscPrefKey.ROOT_STATE, prefs)
    }

    // ROOT_STATE отвечает только на вопрос «сервис жив» — на нём висит плитка в
    // шторке. Экрану нужна градация, поэтому состояние публикуется отдельно.
    private fun setUiState(state: String) {
        setStringPrefValue(state, OscPrefKey.HOME_STATE, prefs)

        updateOngoingNotification(state)
    }

    private fun requestTileListening() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            TileService.requestListeningState(this,
                ComponentName(this, SstpTileService::class.java)
            )
        }
    }

    override fun onCreate() {
        notificationManager = NotificationManagerCompat.from(this)

        prefs = PreferenceManager.getDefaultSharedPreferences(this)

        listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == OscPrefKey.ROOT_STATE.name) {
                val newState = getBooleanPrefValue(OscPrefKey.ROOT_STATE, prefs)

                setBooleanPrefValue(newState, OscPrefKey.HOME_CONNECTOR, prefs)
                requestTileListening()
            }
        }

        prefs.registerOnSharedPreferenceChangeListener(listener)

        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_VPN_CONNECT -> {
                controller?.kill(false, null)

                beForegrounded()
                reconnectAttempt = 0
                invalidateAddressCache()
                if (getBooleanPrefValue(OscPrefKey.LOG_DO_SAVE_LOG, prefs)) {
                    prepareLogWriter()
                }

                logWriter?.write("Establish VPN connection")

                initializeClient()
                startNetworkObserver()

                setUiState(STATE_CONNECTING)
                setRootState(true)

                START_STICKY
            }

            else -> {
                stopNetworkObserver()

                // ensure that reconnection has been completely canceled or done
                runBlocking { jobReconnect?.cancelAndJoin() }

                controller?.disconnect()
                controller = null

                close()

                START_NOT_STICKY
            }
        }
    }

    private fun initializeClient() {
        controller = Controller(SharedBridge(this)).also {
            it.launchJobMain()
        }
    }

    private fun prepareLogWriter() {
        // Имя по дате, а не по времени: файл открывается на дозапись, поэтому все
        // подключения за сутки ложатся в один журнал. Раньше каждое нажатие
        // «Подключиться» плодило отдельный файл.
        val currentDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val filename = "log_sstp_${currentDate}.txt"

        val prefURI = getURIPrefValue(OscPrefKey.LOG_DIR, prefs)
        if (prefURI == null) {
            notifyError("LOG: ERR_NULL_PREFERENCE")
            return
        }

        // Доступ к выбранному каталогу мог не пережить перезагрузку или отзыв
        // прав, поэтому причина отказа сообщается явно: иначе «файл просто не
        // создаётся» и понять, что пошло не так, невозможно.
        try {
            val dirURI = DocumentFile.fromTreeUri(this, prefURI)
            if (dirURI == null) {
                notifyError("LOG: ERR_NULL_DIRECTORY")
                return
            }

            if (!dirURI.canWrite()) {
                notifyError("LOG: ERR_NO_PERMISSION (выбери папку заново)")
                return
            }

            val fileURI = dirURI.findFile(filename) ?: dirURI.createFile("text/plain", filename)
            if (fileURI == null) {
                notifyError("LOG: ERR_NULL_FILE")
                return
            }

            // Не каждый провайдер SAF умеет режим дозаписи.
            val stream = contentResolver.openOutputStream(fileURI.uri, "wa")
                ?: contentResolver.openOutputStream(fileURI.uri, "w")
            if (stream == null) {
                notifyError("LOG: ERR_NULL_STREAM")
                return
            }

            logWriter = LogWriter(stream)
        } catch (e: Exception) {
            notifyError("LOG: ${e.javaClass.simpleName}")
        }
    }

    internal fun launchJobReconnect() {
        setUiState(STATE_RECONNECTING)
        setStringPrefValue("", OscPrefKey.HOME_CONNECTED_AT, prefs)

        jobReconnect = scope.launch {
            try {
                val isImmediate = isImmediateReconnectRequested
                isImmediateReconnectRequested = false

                if (isImmediate) {
                    logWriter?.report("Reconnecting now")
                } else {
                    val delaySec = backoffSeconds[minOf(reconnectAttempt, backoffSeconds.lastIndex)]
                    reconnectAttempt++

                    val message = "Reconnecting in ${delaySec}s (attempt $reconnectAttempt)"
                    notifyMessage(message, NOTIFICATION_RECONNECT_ID, NOTIFICATION_RECONNECT_CHANNEL)
                    logWriter?.report(message)

                    delay(delaySec * 1000L)
                }

                initializeClient()
            } catch (_: CancellationException) { }
            finally {
                cancelNotification(NOTIFICATION_RECONNECT_ID)
            }
        }
    }

    // Вызывается контроллером, когда туннель поднялся: следующий разрыв должен
    // начинать отсчёт пауз заново, а не с того места, где закончился прошлый.
    internal fun onConnected() {
        reconnectAttempt = 0

        setUiState(STATE_CONNECTED)
        setStringPrefValue(
            System.currentTimeMillis().toString(),
            OscPrefKey.HOME_CONNECTED_AT,
            prefs
        )

        reconnectStartedAt.also {
            if (it > 0L) {
                reconnectStartedAt = 0L

                val elapsed = SystemClock.elapsedRealtime() - it
                scope.launch {
                    logWriter?.report("Tunnel is up ${elapsed}ms after network change")
                }
            }
        }
    }

    private fun startNetworkObserver() {
        if (networkObserver != null) return

        networkObserver = UnderlyingNetworkObserver(this) { network ->
            onUnderlyingNetworkChanged(network)
        }.also { it.start() }
    }

    private fun stopNetworkObserver() {
        networkObserver?.close()
        networkObserver = null
    }

    private fun onUnderlyingNetworkChanged(network: Network?) {
        if (network == null) return // сети нет — ждём появления, дёргаться незачем

        setUnderlyingNetworks(arrayOf(network))

        if (!getBooleanPrefValue(OscPrefKey.RECONNECTION_ENABLED, prefs)) return

        reconnectStartedAt = SystemClock.elapsedRealtime()

        scope.launch {
            logWriter?.report("Underlying network changed")

            jobReconnect?.cancelAndJoin()
            reconnectAttempt = 0 // новая сеть — backoff с нуля
            isImmediateReconnectRequested = true

            val currentController = controller
            if (currentController != null && !currentController.isKilled) {
                currentController.kill(true, null) // сам вызовет launchJobReconnect()
            } else {
                // Соединение уже умерло само, и его вызов launchJobReconnect()
                // мы только что отменили — планируем попытку заново.
                launchJobReconnect()
            }
        }
    }

    internal fun cachedAddressFor(hostname: String): InetAddress? {
        return if (hostname == cachedHostname) cachedAddress else null
    }

    internal fun cacheAddress(hostname: String, address: InetAddress) {
        cachedHostname = hostname
        cachedAddress = address
    }

    internal fun invalidateAddressCache() {
        cachedHostname = null
        cachedAddress = null
    }

    private fun beForegrounded() {
        listOf(
            Triple(
                NOTIFICATION_DISCONNECT_CHANNEL,
                R.string.notification_channel_status,
                NotificationManager.IMPORTANCE_LOW, // постоянное уведомление не должно звенеть
            ),
            Triple(
                NOTIFICATION_RECONNECT_CHANNEL,
                R.string.notification_channel_reconnect,
                NotificationManager.IMPORTANCE_LOW,
            ),
            Triple(
                NOTIFICATION_ERROR_CHANNEL,
                R.string.notification_channel_error,
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
            Triple(
                NOTIFICATION_CERTIFICATE_CHANNEL,
                R.string.notification_channel_certificate,
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        ).map { (id, nameId, importance) ->
            NotificationChannel(id, getString(nameId), importance)
        }.also {
            notificationManager.createNotificationChannels(it)
        }

        startForeground(NOTIFICATION_DISCONNECT_ID, buildOngoingNotification(STATE_CONNECTING))
    }

    /**
     * Постоянное уведомление: имя профиля, состояние, кнопка отключения и переход
     * в приложение по тапу. Раньше здесь была пустая карточка с надписью DISCONNECT.
     */
    private fun buildOngoingNotification(state: String, traffic: String? = null): Notification {
        val disconnectIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, SstpVpnService::class.java).setAction(ACTION_VPN_DISCONNECT),
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        val title = getStringPrefValue(OscPrefKey.HOME_ACTIVE_PROFILE, prefs)
            .ifEmpty { getStringPrefValue(OscPrefKey.HOME_HOSTNAME, prefs) }
            .ifEmpty { getString(R.string.app_name) }

        val stateText = if (state == STATE_CONNECTED) {
            // Когда туннель поднят, полезнее скорость и время, чем слово «Подключено».
            listOfNotNull(traffic, formatSessionTime()?.let { getString(R.string.notification_session, it) })
                .joinToString("  ")
                .ifEmpty { getString(R.string.state_connected) }
        } else {
            getString(
                when (state) {
                    STATE_RECONNECTING -> R.string.state_reconnecting
                    STATE_DISCONNECTED -> R.string.state_disconnected
                    else -> R.string.state_connecting
                }
            )
        }

        return NotificationCompat.Builder(this, NOTIFICATION_DISCONNECT_CHANNEL).also {
            it.priority = NotificationCompat.PRIORITY_LOW
            it.setOngoing(true)
            it.setAutoCancel(false)
            it.setShowWhen(false)
            it.setCategory(NotificationCompat.CATEGORY_SERVICE)
            it.setSmallIcon(R.drawable.ic_stat_sstp)
            it.setContentTitle(title)
            it.setContentText(stateText)
            it.setContentIntent(openIntent)
            it.addAction(
                R.drawable.ic_baseline_close_24,
                getString(R.string.action_disconnect),
                disconnectIntent,
            )
        }.build()
    }

    private fun updateOngoingNotification(state: String) {
        if (state == STATE_DISCONNECTED) {
            jobNotification?.cancel()
            return // сервис уже уходит, карточку снимет система
        }

        if (state == STATE_CONNECTED) {
            launchJobNotification()
        } else {
            jobNotification?.cancel()
            tryNotify(buildOngoingNotification(state), NOTIFICATION_DISCONNECT_ID)
        }
    }

    /** Раз в секунду обновляет карточку скоростью и временем сессии. */
    private fun launchJobNotification() {
        jobNotification?.cancel()

        jobNotification = scope.launch {
            val uid = Process.myUid()
            var lastRx = TrafficStats.getUidRxBytes(uid)
            var lastTx = TrafficStats.getUidTxBytes(uid)
            var lastAt = SystemClock.elapsedRealtime()

            tryNotify(buildOngoingNotification(STATE_CONNECTED), NOTIFICATION_DISCONNECT_ID)

            while (true) {
                delay(1000)

                val now = SystemClock.elapsedRealtime()
                val rx = TrafficStats.getUidRxBytes(uid)
                val tx = TrafficStats.getUidTxBytes(uid)
                val seconds = (now - lastAt).coerceAtLeast(1L) / 1000.0

                val traffic = if (rx < 0 || tx < 0) {
                    null // счётчики по UID доступны не на всех прошивках
                } else {
                    val down = ((rx - lastRx).coerceAtLeast(0L) / seconds).toLong()
                    val up = ((tx - lastTx).coerceAtLeast(0L) / seconds).toLong()

                    getString(R.string.notification_traffic, formatRate(up), formatRate(down))
                }

                lastRx = rx
                lastTx = tx
                lastAt = now

                tryNotify(
                    buildOngoingNotification(STATE_CONNECTED, traffic),
                    NOTIFICATION_DISCONNECT_ID,
                )
            }
        }
    }

    private fun formatRate(bytesPerSecond: Long): String {
        return when {
            bytesPerSecond < 1024 -> getString(R.string.unit_bytes_per_second, bytesPerSecond)

            bytesPerSecond < 1024 * 1024 -> getString(
                R.string.unit_kilobytes_per_second,
                bytesPerSecond / 1024.0,
            )

            else -> getString(
                R.string.unit_megabytes_per_second,
                bytesPerSecond / (1024.0 * 1024.0),
            )
        }
    }

    private fun formatSessionTime(): String? {
        val startedAt = getStringPrefValue(OscPrefKey.HOME_CONNECTED_AT, prefs).toLongOrNull()
            ?: return null

        val totalSeconds = ((System.currentTimeMillis() - startedAt).coerceAtLeast(0L)) / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600

        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }

    internal fun notifyMessage(message: String, id: Int, channel: String) {
        val titleId = if (channel == NOTIFICATION_ERROR_CHANNEL) {
            R.string.notification_error_title
        } else {
            R.string.notification_reconnect_title
        }

        NotificationCompat.Builder(this, channel).also {
            it.setSmallIcon(R.drawable.ic_stat_sstp)
            it.setContentTitle(getString(titleId))
            it.setContentText(message)
            it.setStyle(NotificationCompat.BigTextStyle().bigText(message))
            it.priority = NotificationCompat.PRIORITY_DEFAULT
            it.setAutoCancel(true)

            tryNotify(it.build(), id)
        }
    }

    internal fun notifyError(message: String) {
        notifyMessage(message, NOTIFICATION_ERROR_ID, NOTIFICATION_ERROR_CHANNEL)
    }

    internal fun tryNotify(notification: Notification, id: Int) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            notificationManager.notify(id, notification)
        }
    }

    internal fun cancelNotification(id: Int) {
        notificationManager.cancel(id)
    }

    internal fun close() {
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        stopNetworkObserver()

        logWriter?.write("Terminate VPN connection")
        logWriter?.close()
        logWriter = null

        jobNotification?.cancel()

        controller?.kill(false, null)
        controller = null

        scope.cancel()

        setUiState(STATE_DISCONNECTED)
        setStringPrefValue("", OscPrefKey.HOME_CONNECTED_AT, prefs)
        setRootState(false)
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
