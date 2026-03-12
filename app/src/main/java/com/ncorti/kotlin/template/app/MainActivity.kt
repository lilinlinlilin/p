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
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat  // 重要 import
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
    private var currentlyPlayingDesc by mutableStateOf<String?>(null)

    private val shakeThreshold = 15f
    private var lastShake = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 關鍵：強制視窗占滿螢幕（DecorView 拉滿），解決「不是軟件區域」
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 啟用 edge-to-edge + 透明系統欄
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT
            )
        )

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
                        onPlayToggle = { desc -> togglePlay(desc) }
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
        currentlyPlayingDesc = null
    }

    override fun onDestroy() {
        currentPlayer?.release()
        currentPlayer = null
        currentlyPlayingDesc = null
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
            if (currentPlayer?.isPlaying != true || currentlyPlayingDesc != selectedDesc) {
                playAudio(selectedDesc!!)
            }
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
                currentlyPlayingDesc = desc
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "播放失敗：${e.message}", Toast.LENGTH_LONG).show()
                currentlyPlayingDesc = null
            }
        } else {
            Toast.makeText(this@MainActivity, "未找到音频文件：$desc", Toast.LENGTH_SHORT).show()
            currentlyPlayingDesc = null
        }
    }

    fun togglePlay(desc: String) {
        if (currentPlayer?.isPlaying == true && currentlyPlayingDesc == desc) {
            currentPlayer?.stop()
            currentPlayer?.release()
            currentPlayer = null
            currentlyPlayingDesc = null
        } else {
            playAudio(desc)
            selectedDesc = desc
        }
    }
}

@Composable
fun SoundScreen(
    selected: String?,
    onSelect: (String?) -> Unit,
    onPlayToggle: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var descriptions by remember { mutableStateOf(listOf<String>()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var inputDesc by remember { mutableStateOf("") }
    var editingDesc by remember { mutableStateOf<String?>(null) }
    var currentlyPlaying by remember { mutableStateOf<String?>(null) }

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

    // 用 Box 確保內容填滿視窗（現在視窗已滿，內容會跟著滿）
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .safeContentPadding(),  // 避開系統欄蓋住內容，但不壓縮高度
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            if (descriptions.isEmpty()) {
                Spacer(Modifier.weight(1f))
                Text("尚未添加描述，點擊 + 添加")
                Spacer(Modifier.weight(1f))
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(descriptions) { desc ->
                        // Row 內容保持原樣
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    width = 2.dp,
                                    color = if (desc == selected) Color.Blue else Color.LightGray
                                ),
                                color = if (desc == selected) Color.Blue.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .weight(1f)
                                    .pointerInput(desc) {
                                        detectTapGestures(
                                            onTap = { onSelect(desc) },
                                            onLongPress = { editingDesc = desc }
                                        )
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = desc,
                                        color = if (desc == selected) Color.Blue else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Spacer(Modifier.width(8.dp))

                            Button(
                                onClick = {
                                    onPlayToggle(desc)
                                    currentlyPlaying = if (desc == currentlyPlaying) null else desc
                                },
                                modifier = Modifier.size(36.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (desc == currentlyPlaying) Color.Red.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                ),
                                contentPadding = PaddingValues(0.dp),
                                elevation = ButtonDefaults.buttonElevation(0.dp)
                            ) {}
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Text("+")
        }
    }

    // 添加對話框
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { },
            text = {
                OutlinedTextField(
                    value = inputDesc,
                    onValueChange = { inputDesc = it.trim() },
                    label = { Text("描述") },
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
                                prefs.toMutablePreferences().apply {
                                    set(stringPreferencesKey("descriptions"), newList.joinToString(","))
                                }
                            }
                        }
                        inputDesc = ""
                    }
                    showAddDialog = false
                }) { Text("確定") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { } }
        )
    }

    // 編輯/刪除對話框
    editingDesc?.let { current ->
        var editInput by remember { mutableStateOf(current) }

        AlertDialog(
            onDismissRequest = { editingDesc = null },
            title = { },
            text = {
                OutlinedTextField(
                    value = editInput,
                    onValueChange = { editInput = it.trim() },
                    label = { Text("修改") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editInput.isNotBlank() && editInput != current) {
                        val newList = descriptions.map { if (it == current) editInput else it }
                        scope.launch {
                            context.soundDataStore.updateData { prefs ->
                                prefs.toMutablePreferences().apply {
                                    set(stringPreferencesKey("descriptions"), newList.joinToString(","))
                                }
                            }
                        }
                    }
                    editingDesc = null
                }) { Text("保存") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        val toDelete = current
                        val newList = descriptions.filter { it != toDelete }
                        scope.launch {
                            context.soundDataStore.updateData { prefs ->
                                prefs.toMutablePreferences().apply {
                                    set(stringPreferencesKey("descriptions"), newList.joinToString(","))
                                }
                            }
                        }
                        if (selected == toDelete) onSelect(null)
                        if (currentlyPlaying == toDelete) {
                            onPlayToggle(toDelete)
                            currentlyPlaying = null
                        }
                        editingDesc = null
                    }) {
                        Text("刪除", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { editingDesc = null }) { }
                }
            }
        )
    }
}
