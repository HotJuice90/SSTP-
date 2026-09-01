package kittoku.osc.ui

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket


private const val PING_INTERVAL = 3_000L
private const val PING_TIMEOUT = 2_000
private const val PING_PORT = 53

/**
 * Время отклика узла на том конце туннеля.
 *
 * Считать SSTP-эхо было бы точнее, но оно живёт в запретной зоне, поэтому меряем
 * своим замером: сокет приложения не защищён через protect(), значит идёт внутрь
 * туннеля, и время до ответа шлюза — это и есть задержка канала.
 */
@Composable
internal fun rememberPing(target: String?): Int? {
    var ping by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(target) {
        if (target == null) {
            ping = null
            return@LaunchedEffect
        }

        while (true) {
            ping = measurePing(target)
            delay(PING_INTERVAL)
        }
    }

    return ping
}

private suspend fun measurePing(host: String): Int? = withContext(Dispatchers.IO) {
    val startedAt = SystemClock.elapsedRealtime()

    try {
        Socket().use {
            it.connect(InetSocketAddress(host, PING_PORT), PING_TIMEOUT)
        }

        (SystemClock.elapsedRealtime() - startedAt).toInt()
    } catch (_: ConnectException) {
        // Отказ в соединении — это ответ узла, время замера остаётся честным.
        (SystemClock.elapsedRealtime() - startedAt).toInt()
    } catch (_: Exception) {
        null
    }
}
