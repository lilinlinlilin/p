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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
                        onSelect = { selectedDesc = it },
                        selected = selectedDesc
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

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 空实现
    }

    private fun playAudio(desc: String) {
        currentPlayer?.release()
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
}

@Composable
fun SoundScreen(
    selected: String?,
    onSelect: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var descriptions by remember { mutableStateOf(listOf<String>()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var inputDesc by remember { mutableStateOf("") }
    var editingDesc by remember { mutableStateOf<String?>(null) }

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

    val dirPath = remember {
        context.getExternalFilesDir(null)?.resolve("sounds")?.absolutePath ?: "无法获取路径"
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

            Text(
                "把音频文件放到：\n$dirPath\n" +
                        "文件名必须完全等于描述（包括后缀，例如 '开心.ogg' 或 '笑声.mp3'）\n" +
                        "路径通常在：Android/data/你的包名/files/sounds",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
            Spacer(Modifier.height(32.dp))

            if (descriptions.isEmpty()) {
                Text("还没有声音描述\n点击右下角 + 添加", color = Color.Gray)
            } else {
                LazyColumn {
                    items(descriptions) { desc ->
                        val isSelected = desc == selected

                        Surface(
                            onClick = { onSelect(desc) },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                width = 2.dp,
                                color = if (isSelected) Color.Blue else Color.LightGray
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onLongPress = {
                                            editingDesc = desc
                                        }
                                    )
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = desc,
                                    color = if (isSelected) Color.Blue else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 添加对话框
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("添加新声音") },
            text = {
                OutlinedTextField(
                    value = inputDesc,
                    onValueChange = { inputDesc = it.trim() },
                    label = { Text("描述（必须包含后缀，如 '开心.ogg'）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
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

    // 编辑/删除对话框（长按触发）
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
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
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
                        if (selected == toDelete) onSelect("") // 可选：删除后清空选中
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
