package app.what.schedule.ecg

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import app.what.schedule.ws.EspWebSocketClient
import ir.ehsannarmani.compose_charts.LineChart
import ir.ehsannarmani.compose_charts.models.DrawStyle
import ir.ehsannarmani.compose_charts.models.GridProperties
import ir.ehsannarmani.compose_charts.models.Line

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcgScreen(viewModel: EcgViewModel) {
    val wave by viewModel.wave.collectAsState()
    val bpm by viewModel.bpm.collectAsState()
    val state by viewModel.connection.collectAsState()
    val rate by viewModel.packetRate.collectAsState()
    val sdnn by viewModel.sdnnMs.collectAsState()
    val lastRr by viewModel.lastRrMs.collectAsState()
    val avgBpm by viewModel.avgBpm.collectAsState()
    val minBpm by viewModel.minBpm.collectAsState()
    val maxBpm by viewModel.maxBpm.collectAsState()

    LaunchedEffect(Unit) { viewModel.start() }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("ECG Monitor") }, actions = {
                when (state) {
                    EspWebSocketClient.WsState.CONNECTED -> {
                        IconButton(onClick = { viewModel.stop() }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Stop"
                            )
                        }
                    }

                    else -> {
                        IconButton(onClick = { viewModel.start() }) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Start"
                            )
                        }
                    }
                }
            })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = when (state) {
                                EspWebSocketClient.WsState.CONNECTED -> "Connected"
                                EspWebSocketClient.WsState.CONNECTING -> "Connecting"
                                EspWebSocketClient.WsState.DISCONNECTED -> "Disconnected"
                            }
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = when (state) {
                            EspWebSocketClient.WsState.CONNECTED -> Color(0xFF1B5E20)
                            EspWebSocketClient.WsState.CONNECTING -> Color(0xFFEF6C00)
                            EspWebSocketClient.WsState.DISCONNECTED -> Color(0xFFB71C1C)
                        }
                    )
                )
                AssistChip(onClick = {}, label = { Text("Packets/s: ${"%.0f".format(rate)}") })
                AssistChip(onClick = {}, label = { Text("Buffer: ${wave.size}") })
            }

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(text = "BPM", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = (bpm ?: 0).toString(),
                        style = MaterialTheme.typography.displaySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("avg: ${avgBpm ?: "--"}")
                        Text("min: ${minBpm ?: "--"}")
                        Text("max: ${maxBpm ?: "--"}")
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    EcgLineChart(samples = wave)
                }
            }

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text("Analytics", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("SDNN: ${sdnn?.let { "%.0f ms".format(it) } ?: "--"}")
                    Spacer(Modifier.height(4.dp))
                    Text("Last RR: ${lastRr?.let { "$it ms" } ?: "--"}")
                }
            }
        }
    }
}

@Composable
private fun EcgLineChart(samples: FloatArray) {
    if (samples.isEmpty()) return
    val values = samples.map { it.toDouble() }

    LineChart(
        modifier = Modifier.fillMaxSize(),
        animationDelay = 0,
        data = listOf(
            Line(
                label = "ECG",
                values = values,
                color = SolidColor(colorScheme.primary),
                firstGradientFillColor = Color.Transparent,
                secondGradientFillColor = Color.Transparent,
                strokeAnimationSpec = tween(0),
                gradientAnimationSpec = tween(0),
                gradientAnimationDelay = 0,
                curvedEdges = false,
                drawStyle = DrawStyle.Stroke(width = 2.dp),
            )
        ),
        gridProperties = GridProperties(enabled = true),
        minValue = values.min(),
        maxValue = values.max()
    )
}


