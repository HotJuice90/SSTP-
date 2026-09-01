package kittoku.osc.control

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import kittoku.osc.service.SstpVpnService


/**
 * Наблюдатель за сетью, поверх которой строится туннель: Wi-Fi ↔ мобильные данные
 * ↔ отсутствие связи.
 *
 * Живёт на уровне сервиса, а не Controller: он должен пережить смерть соединения
 * и рестарт клиента. Существующий [NetworkObserver] слушает собственный туннель
 * и собирает сводку для UI — это другая задача и другое время жизни.
 *
 * Собственный туннель отфильтрован намеренно. Приложение-VPN по умолчанию само
 * находится внутри своего туннеля (потому и нужен protect()), так что дефолтной
 * сетью для нашего процесса после подъёма соединения может стать tun0. Если его
 * не отсечь, наблюдатель увидит «новую сеть», дёрнет реконнект, тот поднимет
 * туннель — и так по кругу.
 */
internal class UnderlyingNetworkObserver(
    private val service: SstpVpnService,
    private val onChanged: (Network?) -> Unit,
) {
    private val manager = service.getSystemService(ConnectivityManager::class.java)
    private var current: Network? = null

    private val request = NetworkRequest.Builder().let {
        it.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        it.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        it.build()
    }

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (isVpn(network)) return

            if (network != current) {
                current = network
                onChanged(network)
            }
        }

        override fun onLost(network: Network) {
            if (network == current) {
                current = null
                onChanged(null)
            }
        }
    }

    private fun isVpn(network: Network): Boolean {
        return manager.getNetworkCapabilities(network)?.hasTransport(
            NetworkCapabilities.TRANSPORT_VPN
        ) ?: false
    }

    internal val currentNetwork: Network?
        get() = current

    internal fun start() {
        // Сеть, на которой соединение уже поднимается, запоминаем до регистрации:
        // иначе колбэк сразу отдаст её как «новую» и оборвёт только что созданного клиента.
        current = manager.activeNetwork?.takeUnless { isVpn(it) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Ровно одна сеть — лучшая из подходящих под запрос, выбранная системой
            // по её же правилам (validated, тип транспорта, метрики).
            manager.registerBestMatchingNetworkCallback(
                request,
                callback,
                Handler(Looper.getMainLooper())
            )
        } else {
            manager.registerDefaultNetworkCallback(callback)
        }
    }

    internal fun close() {
        try {
            manager.unregisterNetworkCallback(callback)
        } catch (_: IllegalArgumentException) {} // уже снят
    }
}
