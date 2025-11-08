package com.example.flashcard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.graphics.vector.ImageVector

// Enum to manage timer mode
enum class TimerMode {
    ONE_TIME, REPETITIVE
}

// Enum to manage state
enum class TimerState {
    SETTING,
    RUNNING,
    PAUSED,
    FINISHED
}

// Enum to track phase in repetitive mode
enum class RepetitivePhase {
    FOCUS,
    BREAK
}

class TimerModel : ViewModel() {
    var timerMode by mutableStateOf(TimerMode.ONE_TIME)
    var timerState by mutableStateOf(TimerState.SETTING)

    // One-time timer inputs
    var hoursInput by mutableStateOf("00")
    var minutesInput by mutableStateOf("00")
    var secondsInput by mutableStateOf("00")

    // Repetitive timer inputs
    var focusHours by mutableStateOf("00")
    var focusMinutes by mutableStateOf("00")
    var focusSeconds by mutableStateOf("00")

    var breakHours by mutableStateOf("00")
    var breakMinutes by mutableStateOf("00")
    var breakSeconds by mutableStateOf("00")

    var totalCycles by mutableStateOf("1")
    var currentCycle by mutableStateOf(1)
    var currentPhase by mutableStateOf(RepetitivePhase.FOCUS)

    var remainingTimeMillis by mutableStateOf(0L)
    private var countdownJob: Job? = null

    fun startTimer() {
        if (timerMode == TimerMode.ONE_TIME) {
            startOneTimeTimer()
        } else {
            startRepetitiveTimer()
        }
    }

    private fun startOneTimeTimer() {
        val totalMillis = ((hoursInput.toLongOrNull() ?: 0L) * 3600 +
                (minutesInput.toLongOrNull() ?: 0L) * 60 +
                (secondsInput.toLongOrNull() ?: 0L)) * 1000

        if (totalMillis > 0) {
            remainingTimeMillis = totalMillis
            timerState = TimerState.RUNNING
            startCountdown(isRepetitive = false)
        }
    }

    private fun startRepetitiveTimer() {
        val focusMillis = ((focusHours.toLongOrNull() ?: 0L) * 3600 +
                (focusMinutes.toLongOrNull() ?: 0L) * 60 +
                (focusSeconds.toLongOrNull() ?: 0L)) * 1000

        val breakMillis = ((breakHours.toLongOrNull() ?: 0L) * 3600 +
                (breakMinutes.toLongOrNull() ?: 0L) * 60 +
                (breakSeconds.toLongOrNull() ?: 0L)) * 1000

        if (focusMillis > 0 && totalCycles.toIntOrNull() ?: 0 > 0) {
            currentCycle = 1
            currentPhase = RepetitivePhase.FOCUS
            remainingTimeMillis = focusMillis
            timerState = TimerState.RUNNING
            startCountdown(isRepetitive = true, focusMillis = focusMillis, breakMillis = breakMillis)
        }
    }

    fun pauseTimer() {
        countdownJob?.cancel()
        timerState = TimerState.PAUSED
    }

    fun resumeTimer() {
        timerState = TimerState.RUNNING
        if (timerMode == TimerMode.REPETITIVE) {
            val focusMillis = ((focusHours.toLongOrNull() ?: 0L) * 3600 +
                    (focusMinutes.toLongOrNull() ?: 0L) * 60 +
                    (focusSeconds.toLongOrNull() ?: 0L)) * 1000

            val breakMillis = ((breakHours.toLongOrNull() ?: 0L) * 3600 +
                    (breakMinutes.toLongOrNull() ?: 0L) * 60 +
                    (breakSeconds.toLongOrNull() ?: 0L)) * 1000

            startCountdown(isRepetitive = true, focusMillis = focusMillis, breakMillis = breakMillis)
        } else {
            startCountdown(isRepetitive = false)
        }
    }

    fun resetTimer() {
        countdownJob?.cancel()
        timerState = TimerState.SETTING
        remainingTimeMillis = 0L
        currentCycle = 1
        currentPhase = RepetitivePhase.FOCUS
    }

    private fun startCountdown(isRepetitive: Boolean, focusMillis: Long = 0L, breakMillis: Long = 0L) {
        countdownJob = viewModelScope.launch {
            while (remainingTimeMillis >= 0 && timerState == TimerState.RUNNING) {
                delay(1000)
                remainingTimeMillis -= 1000
                if (remainingTimeMillis <= 0) {
                    // Ensure 0 is visible
                    delay(1000)

                    if (isRepetitive) {
                        val total = totalCycles.toIntOrNull() ?: 1
                        if (currentPhase == RepetitivePhase.FOCUS) {
                            if (currentCycle < total) {
                                currentPhase = RepetitivePhase.BREAK
                                remainingTimeMillis = breakMillis
                            } else {
                                timerState = TimerState.FINISHED
                                break
                            }
                        } else {
                            currentCycle++
                            currentPhase = RepetitivePhase.FOCUS
                            remainingTimeMillis = focusMillis
                        }
                    } else {
                        timerState = TimerState.FINISHED
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(viewModel: TimerModel, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Timer") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (viewModel.timerState) {
                TimerState.SETTING -> SetTimerScreen(viewModel)
                TimerState.RUNNING, TimerState.PAUSED -> CountdownScreen(viewModel)
                TimerState.FINISHED -> FinishedScreen(viewModel)
            }
        }
    }
}

@Composable
fun SetTimerScreen(viewModel: TimerModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = { viewModel.timerMode = TimerMode.ONE_TIME },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (viewModel.timerMode == TimerMode.ONE_TIME)
                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                )
            ) { Text("One Time") }

            Button(
                onClick = { viewModel.timerMode = TimerMode.REPETITIVE },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (viewModel.timerMode == TimerMode.REPETITIVE)
                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                )
            ) { Text("Repetitive") }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (viewModel.timerMode == TimerMode.ONE_TIME) {
            Text("Set Time", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = viewModel.hoursInput,
                    onValueChange = { viewModel.hoursInput = it },
                    label = { Text("HH") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = viewModel.minutesInput,
                    onValueChange = { viewModel.minutesInput = it },
                    label = { Text("MM") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = viewModel.secondsInput,
                    onValueChange = { viewModel.secondsInput = it },
                    label = { Text("SS") },
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Text("Focus Time", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = viewModel.focusHours, onValueChange = { viewModel.focusHours = it }, label = { Text("HH") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = viewModel.focusMinutes, onValueChange = { viewModel.focusMinutes = it }, label = { Text("MM") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = viewModel.focusSeconds, onValueChange = { viewModel.focusSeconds = it }, label = { Text("SS") }, modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(16.dp))
            Text("Break Time", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = viewModel.breakHours, onValueChange = { viewModel.breakHours = it }, label = { Text("HH") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = viewModel.breakMinutes, onValueChange = { viewModel.breakMinutes = it }, label = { Text("MM") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = viewModel.breakSeconds, onValueChange = { viewModel.breakSeconds = it }, label = { Text("SS") }, modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = viewModel.totalCycles,
                onValueChange = { viewModel.totalCycles = it },
                label = { Text("Number of Cycles") }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = { viewModel.startTimer() }, modifier = Modifier.fillMaxWidth()) {
            Text("Start")
        }
    }
}

@Composable
fun CountdownScreen(viewModel: TimerModel) {
    val minutes = viewModel.remainingTimeMillis / 1000 / 60
    val seconds = viewModel.remainingTimeMillis / 1000 % 60
    val hours = viewModel.remainingTimeMillis / 1000 / 3600
    val formattedTime = String.format("%02d:%02d:%02d", hours, minutes % 60, seconds)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (viewModel.timerMode == TimerMode.REPETITIVE) {
            Text(
                if (viewModel.currentPhase == RepetitivePhase.FOCUS)
                    "Focus Time of Cycle ${viewModel.currentCycle}/${viewModel.totalCycles}"
                else
                    "Break Time of Cycle ${viewModel.currentCycle}/${viewModel.totalCycles}",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
        }

        Text(formattedTime, fontSize = 48.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val icon: ImageVector
            val description: String
            if (viewModel.timerState == TimerState.PAUSED) {
                icon = Icons.Default.PlayArrow
                description = "Resume"
            } else {
                icon = Icons.Default.Pause
                description = "Pause"
            }

            IconButton(onClick = {
                if (viewModel.timerState == TimerState.PAUSED) viewModel.resumeTimer()
                else viewModel.pauseTimer()
            }) {
                Icon(icon, description, modifier = Modifier.size(48.dp))
            }

            IconButton(onClick = { viewModel.resetTimer() }) {
                Icon(Icons.Default.Close, "Cancel", modifier = Modifier.size(48.dp))
            }
        }
    }
}

@Composable
fun FinishedScreen(viewModel: TimerModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("All Cycles Complete!", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { viewModel.resetTimer() }) {
            Text("Set New Timer")
        }
    }
}
