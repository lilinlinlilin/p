package com.ncorti.kotlin.template.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

val Context.soundDataStore: DataStore<Preferences> by preferencesDataStore(name = "sounds")

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var currentPlayer: MediaPlayer? = null
    private var selectedDescription by mutableStateOf<String?>(null)

    private val shakeThreshold = 15f
    private var lastShakeTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        setContent {
            MaterialTheme {
                Surface {
                    SoundManagerScreen(
                        selected = selectedDescription,
                        onSelect = { selectedDescription = it }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        currentPlayer?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        currentPlayer?.release()
        super.onDestroy()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return

        val now = System.currentTimeMillis()
        if (now - lastShakeTime < 400) return
        lastShakeTime = now

        val accel = sqrt(event.values.map { it * it }.sum()) - SensorManager.GRAVITY_EARTH
        if (abs(accel) > shakeThreshold && selectedDescription != null) {
            playSound(selectedDescription!!)
        }
    }

    private fun playSound(desc: String) {
        currentPlayer?.release()
        val dir = File(filesDir, "sounds")
        val file = File(dir, "$desc.ogg")  // 或改成 .mp3，根据你的文件后缀
        if (file.exists()) {
            currentPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

@Composable
fun SoundManagerScreen(
    selected: String?,
    onSelect: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sounds by remember { mutableStateOf(listOf<String>()) }
    var showDialog by remember { mutableStateOf(false) }
    var newDesc by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        sounds = context.soundDataStore.data
            .map { it[stringPreferencesKey("descriptions")]?.split(",")?.filter { it.isNotBlank() } ?: emptyList() }
            .first()
    }

    val soundsDirPath = remember { context.filesDir.resolve("sounds").absolutePath }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Text("+")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("摇晃播放声音", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text("把音频文件放到：\n$soundsDirPath\n文件名 = 描述.ogg 或 .mp3", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(24.dp))

            if (sounds.isEmpty()) {
                Text("点击 + 添加描述", color = Color.Gray)
            }

            LazyColumn {
                items(sounds) { desc ->
                    OutlinedButton(
                        onClick = { onSelect(desc) },
                        border = BorderStroke(2.dp, if (desc == selected) Color.Blue else Color.LightGray),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(desc)
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("添加声音描述") },
            text = {
                OutlinedTextField(
                    value = newDesc,
                    onValueChange = { newDesc = it },
                    label = { Text("描述（按钮名称）") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newDesc.isNotBlank()) {
                        scope.launch {
                            context.soundDataStore.updateData { prefs ->
                                val current = prefs[stringPreferencesKey("descriptions")] ?: ""
                                val updated = if (current.isEmpty()) newDesc else "$current,$newDesc"
                                prefs.toMutablePreferences().apply {
                                    set(stringPreferencesKey("descriptions"), updated)
                                }
                            }
                        }
                        sounds = sounds + newDesc
                    }
                    showDialog = false
                    newDesc = ""
                }) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("取消") } }
        )
    }
}
