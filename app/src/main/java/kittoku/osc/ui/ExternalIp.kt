package kittoku.osc.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL


private const val EXTERNAL_IP_URL = "https://api.ipify.org"
private const val EXTERNAL_IP_TIMEOUT = 4_000

/**
 * Внешний адрес, с которым нас видит интернет.
 *
 * Сокеты приложения не защищены через protect(), поэтому запрос уходит внутрь
 * туннеля и наружу выходит уже с роутера — то есть возвращается его WAN-адрес,
 * а не адрес оператора. Спрашиваем стороннюю службу: по PPP роутер свой внешний
 * адрес не сообщает.
 */
@Composable
internal fun rememberExternalIp(isConnected: Boolean, connectedAt: Long?): String? {
    return produceState<String?>(null, isConnected, connectedAt) {
        value = if (isConnected) fetchExternalIp() else null
    }.value
}

private suspend fun fetchExternalIp(): String? = withContext(Dispatchers.IO) {
    try {
        val connection = (URL(EXTERNAL_IP_URL).openConnection() as HttpURLConnection).also {
            it.connectTimeout = EXTERNAL_IP_TIMEOUT
            it.readTimeout = EXTERNAL_IP_TIMEOUT
            it.requestMethod = "GET"
        }

        try {
            connection.inputStream.bufferedReader().readText().trim().ifEmpty { null }
        } finally {
            connection.disconnect()
        }
    } catch (_: Exception) {
        null
    }
}
