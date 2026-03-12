package com.ncorti.kotlin.template.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

val Context.soundDataStore: DataStore<Preferences> by preferencesDataStore(name = "sounds")

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var currentPlayer: MediaPlayer? = null
    private var selectedDesc by mutableStateOf<String?>(null)

    private val shakeThreshold = 15f
    private var lastShake = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SoundScreen(
                        selected = selectedDesc,
                        onSelect = { selectedDesc = it },
                        onPlayToggle = { desc ->
                            if (currentPlayer?.isPlaying == true && selectedDesc == desc) {
                                stopAudio()
                            } else {
                                playAudio(desc)
                                selectedDesc = desc
                            }
                        }
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
        super.onPause()
        sensorManager.unregisterListener(this)
        currentPlayer?.pause()
    }

    override fun onDestroy() {
        currentPlayer?.release()
        currentPlayer = null
        super.onDestroy()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return

        val now = System.currentTimeMillis()
        if (now - lastShake < 400) return
        lastShake = now

        val x = event.values[0].toDouble()
        val y = event.values[1].toDouble()
        val z = event.values[2].toDouble()
        val speed = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH

        if (abs(speed) > shakeThreshold && selectedDesc != null) {
            playAudio(selectedDesc!!)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun playAudio(desc: String) {
        currentPlayer?.release()
        currentPlayer = null

        val soundsDir = getExternalFilesDir(null)?.resolve("sounds") ?: return
        if (!soundsDir.exists()) soundsDir.mkdirs()

        val audioFile = File(soundsDir, desc)

        if (audioFile.exists()) {
            try {
                currentPlayer = MediaPlayer().apply {
                    setDataSource(audioFile.absolutePath)
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "播放失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this@MainActivity, "未找到音频文件：$desc", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopAudio() {
        currentPlayer?.stop()
        currentPlayer?.release()
        currentPlayer = null
    }
}

@Composable
fun SoundScreen(
    selected: String?,
    onSelect: (String) -> Unit,
    onPlayToggle: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var descriptions by remember { mutableStateOf(listOf<String>()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var inputDesc by remember { mutableStateOf("") }
    var editingDesc by remember { mutableStateOf<String?>(null) }

    // 当前是否正在播放（用于视觉反馈）
    var isPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        context.soundDataStore.data
            .map { prefs ->
                val saved = prefs[stringPreferencesKey("descriptions")] ?: ""
                if (saved.isNotEmpty()) saved.split(",").filter { it.isNotBlank() } else emptyList()
            }
            .collect { newList ->
                descriptions = newList
            }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Text("+")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "摇一摇播放声音",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(16.dp))

            if (descriptions.isEmpty()) {
                Text("还没有声音描述\n点击右下角 + 添加", color = Color.Gray)
            } else {
                LazyColumn {
                    items(descriptions) { desc ->
                        val isSelected = desc == selected
                        val playingThis = isPlaying && isSelected

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // 主按钮（样式与原脚本一致）
                            OutlinedButton(
                                onClick = { onSelect(desc) },
                                modifier = Modifier
                                    .weight(1f)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onLongPress = { editingDesc = desc }
                                        )
                                    },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    width = 2.dp,
                                    color = if (isSelected) Color.Blue else Color.LightGray
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) Color.Blue.copy(alpha = 0.08f) else Color.Transparent,
                                    contentColor = if (isSelected) Color.Blue else MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Text(
                                    text = desc,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Start
                                )
                            }

                            Spacer(Modifier.width(8.dp))

                            // 小播放按钮（无文字、无图标、圆形、极简）
                            Button(
                                onClick = {
                                    onPlayToggle(desc)
                                    isPlaying = !isPlaying || !playingThis
                                },
                                modifier = Modifier.size(40.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (playingThis) Color.Red.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                // 留空：无文字、无图标，只用颜色表示状态
                            }
                        }
                    }
                }
            }
        }
    }

    // 添加对话框（保持原样）
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("添加新声音") },
            text = {
                OutlinedTextField(
                    value = inputDesc,
                    onValueChange = { inputDesc = it.trim() },
                    label = { Text("描述（必须包含后缀，如 '开心.ogg'）") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (inputDesc.isNotBlank()) {
                        val newList = (descriptions + inputDesc).distinct()
                        scope.launch {
                            context.soundDataStore.updateData { prefs ->
                                val updated = newList.joinToString(",")
                                prefs.toMutablePreferences().apply {
                                    set(stringPreferencesKey("descriptions"), updated)
                                }
                            }
                        }
                        inputDesc = ""
                    }
                    showAddDialog = false
                }) {
                    Text("添加")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("取消") }
            }
        )
    }

    // 编辑/删除对话框（保持原样）
    if (editingDesc != null) {
        var editInput by remember { mutableStateOf(editingDesc ?: "") }
        AlertDialog(
            onDismissRequest = { editingDesc = null },
            title = { Text("编辑或删除：$editingDesc") },
            text = {
                OutlinedTextField(
                    value = editInput,
                    onValueChange = { editInput = it.trim() },
                    label = { Text("修改描述（保持后缀）") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editInput.isNotBlank() && editInput != editingDesc) {
                        val old = editingDesc!!
                        val newList = descriptions.map { if (it == old) editInput else it }
                        scope.launch {
                            context.soundDataStore.updateData { prefs ->
                                val updated = newList.joinToString(",")
                                prefs.toMutablePreferences().apply {
                                    set(stringPreferencesKey("descriptions"), updated)
                                }
                            }
                        }
                    }
                    editingDesc = null
                }) {
                    Text("保存修改")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        val toDelete = editingDesc!!
                        val newList = descriptions.filter { it != toDelete }
                        scope.launch {
                            context.soundDataStore.updateData { prefs ->
                                val updated = newList.joinToString(",")
                                prefs.toMutablePreferences().apply {
                                    set(stringPreferencesKey("descriptions"), updated)
                                }
                            }
                        }
                        if (selected == toDelete) onSelect("")
                        editingDesc = null
                    }) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { editingDesc = null }) { Text("取消") }
                }
            }
        )
    }
}
