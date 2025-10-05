package app.what.schedule.ws

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.ws
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class EspWebSocketClient(
    private val scope: CoroutineScope,
    private val host: String = "192.168.4.1",
    private val port: Int = 80,
    private val path: String = "/ws",
) {
    private val client = HttpClient(CIO) { install(WebSockets) }
    private val outChannel = Channel<FloatArray>(capacity = Channel.BUFFERED)
    private var loopJob: Job? = null

    enum class WsState { DISCONNECTED, CONNECTING, CONNECTED }

    private val _state = MutableStateFlow(WsState.DISCONNECTED)
    val state: StateFlow<WsState> = _state

    private val _packetRateHz = MutableStateFlow(0f)
    val packetRateHz: StateFlow<Float> = _packetRateHz
    private var packetsInWindow: Int = 0

    fun stream(): Flow<FloatArray> = outChannel.consumeAsFlow()

    fun start() {
        if (loopJob != null) return
        loopJob = scope.launch {
            var attempt = 0
            while (isActive) {
                try {
                    _state.value = WsState.CONNECTING
                    client.ws(host = host, port = port, path = path) {
                        attempt = 0
                        _state.value = WsState.CONNECTED
                        // reset packet rate window
                        packetsInWindow = 0
                        val rateUpdater = launch {
                            while (isActive) {
                                delay(1000)
                                _packetRateHz.value = packetsInWindow.toFloat()
                                packetsInWindow = 0
                            }
                        }
                        for (frame in incoming) {
                            val text = (frame as? Frame.Text)?.readText() ?: continue
                            val samples = parseArrayOfFloats(text)
                            if (samples != null) {
                                packetsInWindow++
                                outChannel.trySend(samples)
                            }
                        }
                        rateUpdater.cancel()
                    }
                } catch (_: Throwable) {
                    // swallow and retry
                }
                _state.value = WsState.DISCONNECTED
                attempt++
                val backoffMs = (1000L * attempt).coerceAtMost(15_000L)
                delay(backoffMs)
            }
        }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
        _state.value = WsState.DISCONNECTED
        _packetRateHz.value = 0f
    }

    private fun parseArrayOfFloats(text: String): FloatArray? {
        // Expected: "[f1, f2, ...]" possibly with nested pairs; we will extract numbers
        return try {
            val json = Json.parseToJsonElement(text)
            val arr = json.jsonArray
            val result = FloatArray(arr.size)
            var idx = 0
            for (el in arr) {
                result[idx++] = el.jsonPrimitive.float
            }
            result
        } catch (_: Throwable) {
            null
        }
    }
}


