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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
                        onPlay = { desc -> playAudio(desc) },
                        onStop = { stopAudio() }
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

        if (abs(speed) > shakeThreshold && !selectedDesc.isNullOrBlank()) {
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
        if (!audioFile.exists()) {
            Toast.makeText(this, "未找到音檔：$desc", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            currentPlayer = MediaPlayer().apply {
                setDataSource(audioFile.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "播放失敗：${e.message}", Toast.LENGTH_LONG).show()
            currentPlayer?.release()
            currentPlayer = null
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
    onPlay: (String) -> Unit,
    onStop: () -> Unit
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
                "搖一搖播放聲音",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(24.dp))

            if (descriptions.isEmpty()) {
                Text(
                    "還沒有聲音描述\n點擊右下角 + 新增",
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(descriptions) { desc ->
                        val isSelected = desc == selected
                        val isPlaying = desc == currentlyPlaying

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    width = 2.dp,
                                    color = if (isSelected) Color.Blue else Color.LightGray
                                ),
                                color = if (isSelected) Color.Blue.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .weight(1f)
                                    .pointerInput(desc) {
                                        detectTapGestures(
                                            onTap = { onSelect(desc) },
                                            onLongPress = { editingDesc = desc }
                                        )
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = desc,
                                        color = if (isSelected) Color.Blue else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )

                                    IconButton(
                                        onClick = {
                                            if (isPlaying) {
                                                onStop()
                                                currentlyPlaying = null
                                            } else {
                                                onPlay(desc)
                                                currentlyPlaying = desc
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            contentDescription = if (isPlaying) "暫停" else "播放",
                                            tint = if (isPlaying) Color.Red else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 新增聲音對話框
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("新增聲音") },
            text = {
                OutlinedTextField(
                    value = inputDesc,
                    onValueChange = { inputDesc = it.trim() },
                    label = { Text("描述（必須包含副檔名，例如 '開心.ogg'）") },
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
                }) { Text("新增") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("取消") } }
        )
    }

    // 編輯/刪除對話框
    editingDesc?.let { current ->
        var editInput by remember { mutableStateOf(current) }

        AlertDialog(
            onDismissRequest = { editingDesc = null },
            title = { Text("編輯或刪除：$current") },
            text = {
                OutlinedTextField(
                    value = editInput,
                    onValueChange = { editInput = it.trim() },
                    label = { Text("修改描述（保留副檔名）") },
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
                }) { Text("儲存修改") }
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
                        if (selected == toDelete) onSelect("")
                        if (currentlyPlaying == toDelete) {
                            onStop()
                            currentlyPlaying = null
                        }
                        editingDesc = null
                    }) {
                        Text("刪除", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { editingDesc = null }) { Text("取消") }
                }
            }
        )
    }
}
