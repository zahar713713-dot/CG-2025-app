package app.what.schedule.ecg

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.what.schedule.ws.EspWebSocketClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.sqrt

class EcgViewModel : ViewModel() {
    private val buffer = EkgBuffer(capacity = 2048)
    private val bpmEstimator = BpmEstimator(samplingRateHz = 250)
    private val wsClient = EspWebSocketClient(viewModelScope)

    private val _wave: MutableStateFlow<FloatArray> = MutableStateFlow(FloatArray(0))
    val wave: StateFlow<FloatArray> = _wave.asStateFlow()

    private val _bpm: MutableStateFlow<Int?> = MutableStateFlow(null)
    val bpm: StateFlow<Int?> = _bpm.asStateFlow()

    private val _connection = MutableStateFlow(EspWebSocketClient.WsState.DISCONNECTED)
    val connection: StateFlow<EspWebSocketClient.WsState> = _connection.asStateFlow()

    private val _packetRate = MutableStateFlow(0f)
    val packetRate: StateFlow<Float> = _packetRate.asStateFlow()

    private val rrList: MutableList<Float> = ArrayList()
    private val _sdnnMs = MutableStateFlow<Float?>(null)
    val sdnnMs: StateFlow<Float?> = _sdnnMs.asStateFlow()

    private val _lastRrMs = MutableStateFlow<Int?>(null)
    val lastRrMs: StateFlow<Int?> = _lastRrMs.asStateFlow()

    private val bpmHistory: ArrayDeque<Int> = ArrayDeque()
    private val _avgBpm = MutableStateFlow<Int?>(null)
    private val _minBpm = MutableStateFlow<Int?>(null)
    private val _maxBpm = MutableStateFlow<Int?>(null)
    val avgBpm: StateFlow<Int?> = _avgBpm.asStateFlow()
    val minBpm: StateFlow<Int?> = _minBpm.asStateFlow()
    val maxBpm: StateFlow<Int?> = _maxBpm.asStateFlow()

    fun stop() {
        wsClient.stop()
    }

    fun start() {
        wsClient.start()
        viewModelScope.launch {
            wsClient.stream().collect { samples ->
                buffer.append(samples)
                val snap = buffer.snapshot()
                _wave.value = downsampleForChart(snap)
                bpmEstimator.updateAndEstimateBpm(samples)?.let { bpmNow ->
                    _bpm.value = bpmNow
                    bpmHistory.addLast(bpmNow)
                    while (bpmHistory.size > 30) bpmHistory.removeFirst()
                    _avgBpm.value = (bpmHistory.sum().toFloat() / bpmHistory.size).toInt()
                    _minBpm.value = bpmHistory.minOrNull()
                    _maxBpm.value = bpmHistory.maxOrNull()
                }
                bpmEstimator.lastRrMilliseconds()?.let { rrMs ->
                    _lastRrMs.value = rrMs
                    val rrSec = rrMs / 1000f
                    rrList.add(rrSec)
                    if (rrList.size > 64) rrList.removeAt(0)
                    computeSdnn()
                }
            }
        }
        viewModelScope.launch(Dispatchers.Default) {
            combine(wsClient.state, wsClient.packetRateHz) { s, r -> s to r }
                .collect { (s, r) ->
                    _connection.value = s
                    _packetRate.value = r
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        wsClient.stop()
    }

    private fun computeSdnn() {
        if (rrList.size < 2) return
        val mean = rrList.sum() / rrList.size
        var sumSq = 0.0
        for (v in rrList) sumSq += (v - mean).toDouble().pow(2.0)
        val sd = sqrt(sumSq / rrList.size)
        _sdnnMs.value = (sd * 1000.0).toFloat()
    }

    private fun downsampleForChart(input: FloatArray, maxPoints: Int = 600): FloatArray {
        if (input.size <= maxPoints) return input
        val factor = input.size.toFloat() / maxPoints
        val output = FloatArray(maxPoints)
        var acc = 0f
        var count = 0
        var outIndex = 0
        var nextThreshold = factor
        var i = 0
        while (i < input.size && outIndex < maxPoints) {
            acc += input[i]
            count++
            if (i + 1 >= nextThreshold) {
                output[outIndex++] = acc / count
                acc = 0f
                count = 0
                nextThreshold += factor
            }
            i++
        }
        if (outIndex < maxPoints && count > 0) {
            output[outIndex] = acc / count
        }
        return output
    }
}


