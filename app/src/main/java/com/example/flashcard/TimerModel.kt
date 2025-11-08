package com.example.flashcard

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Enums
enum class TimerMode { ONE_TIME, REPETITIVE }
enum class TimerState { SETTING, RUNNING, PAUSED, FINISHED }
enum class RepetitivePhase { FOCUS, BREAK }

// ViewModel
class TimerModel : ViewModel() {
    var timerMode by mutableStateOf(TimerMode.ONE_TIME)
    var timerState by mutableStateOf(TimerState.SETTING)

    // One-Time Timer
    var hoursInput by mutableStateOf("00")
    var minutesInput by mutableStateOf("00")
    var secondsInput by mutableStateOf("00")

    // Repetitive Timer
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
    var enableDND by mutableStateOf(false)

    private var countdownJob: Job? = null

    // Start Timer
    fun startTimer(context: Context) {
        if (timerMode == TimerMode.ONE_TIME) startOneTimeTimer()
        else startRepetitiveTimer(context)
    }

    private fun startOneTimeTimer() {
        val totalMillis = ((hoursInput.toLongOrNull() ?: 0) * 3600 +
                (minutesInput.toLongOrNull() ?: 0) * 60 +
                (secondsInput.toLongOrNull() ?: 0)) * 1000
        if (totalMillis > 0) {
            remainingTimeMillis = totalMillis
            timerState = TimerState.RUNNING
            startCountdown(false)
        }
    }

    private fun startRepetitiveTimer(context: Context) {
        val focusMillis = ((focusHours.toLongOrNull() ?: 0) * 3600 +
                (focusMinutes.toLongOrNull() ?: 0) * 60 +
                (focusSeconds.toLongOrNull() ?: 0)) * 1000
        val breakMillis = ((breakHours.toLongOrNull() ?: 0) * 3600 +
                (breakMinutes.toLongOrNull() ?: 0) * 60 +
                (breakSeconds.toLongOrNull() ?: 0)) * 1000

        if (focusMillis > 0 && totalCycles.toIntOrNull() ?: 0 > 0) {
            currentCycle = 1
            currentPhase = RepetitivePhase.FOCUS
            remainingTimeMillis = focusMillis
            timerState = TimerState.RUNNING
            startCountdown(true, focusMillis, breakMillis, context)
        }
    }

    fun pauseTimer() {
        countdownJob?.cancel()
        timerState = TimerState.PAUSED
    }

    fun resumeTimer(context: Context) {
        timerState = TimerState.RUNNING
        if (timerMode == TimerMode.REPETITIVE) {
            val focusMillis = ((focusHours.toLongOrNull() ?: 0) * 3600 +
                    (focusMinutes.toLongOrNull() ?: 0) * 60 +
                    (focusSeconds.toLongOrNull() ?: 0)) * 1000
            val breakMillis = ((breakHours.toLongOrNull() ?: 0) * 3600 +
                    (breakMinutes.toLongOrNull() ?: 0) * 60 +
                    (breakSeconds.toLongOrNull() ?: 0)) * 1000
            startCountdown(true, focusMillis, breakMillis, context)
        } else {
            startCountdown(false)
        }
    }

    fun resetTimer(context: Context) {
        countdownJob?.cancel()
        timerState = TimerState.SETTING
        remainingTimeMillis = 0
        currentCycle = 1
        currentPhase = RepetitivePhase.FOCUS
        if (enableDND) disableDND(context)
    }

    private fun startCountdown(
        isRepetitive: Boolean,
        focusMillis: Long = 0,
        breakMillis: Long = 0,
        context: Context? = null
    ) {
        countdownJob = viewModelScope.launch {
            while (timerState == TimerState.RUNNING && remainingTimeMillis >= 0) {
                delay(1000)
                remainingTimeMillis -= 1000

                if (remainingTimeMillis <= 0) {
                    delay(1000) // show 0

                    if (isRepetitive) {
                        val total = totalCycles.toIntOrNull() ?: 1
                        if (currentPhase == RepetitivePhase.FOCUS) {
                            if (enableDND && context != null) disableDND(context)
                            if (currentCycle < total) {
                                currentPhase = RepetitivePhase.BREAK
                                remainingTimeMillis = breakMillis
                            } else {
                                timerState = TimerState.FINISHED
                                break
                            }
                        } else {
                            currentCycle++
                            if (currentCycle <= total) {
                                currentPhase = RepetitivePhase.FOCUS
                                remainingTimeMillis = focusMillis
                                if (enableDND && context != null) enableDND(context)
                            } else {
                                timerState = TimerState.FINISHED
                                break
                            }
                        }
                    } else {
                        timerState = TimerState.FINISHED
                    }
                }
            }
        }
    }

    // DND Helpers
    fun enableDND(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.isNotificationPolicyAccessGranted) nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
    }

    fun disableDND(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.isNotificationPolicyAccessGranted) nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
    }

    fun requestDNDPermission(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }
}

// ---------------- Composables ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(viewModel: TimerModel, context: Context, onBack: () -> Unit) {
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
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (viewModel.timerState) {
                TimerState.SETTING -> SetTimerScreen(viewModel, context)
                TimerState.RUNNING, TimerState.PAUSED -> CountdownScreen(viewModel, context)
                TimerState.FINISHED -> FinishedScreen(viewModel, context)
            }
        }
    }
}

@Composable
fun SetTimerScreen(viewModel: TimerModel, context: Context) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
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
                OutlinedTextField(value = viewModel.hoursInput, onValueChange = { viewModel.hoursInput = it }, label = { Text("HH") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = viewModel.minutesInput, onValueChange = { viewModel.minutesInput = it }, label = { Text("MM") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = viewModel.secondsInput, onValueChange = { viewModel.secondsInput = it }, label = { Text("SS") }, modifier = Modifier.weight(1f))
            }
        } else {
            // Repetitive Timer Inputs
            Text("Focus Time", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = viewModel.focusHours, onValueChange = { viewModel.focusHours = it }, label = { Text("HH") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = viewModel.focusMinutes, onValueChange = { viewModel.focusMinutes = it }, label = { Text("MM") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = viewModel.focusSeconds, onValueChange = { viewModel.focusSeconds = it }, label = { Text("SS") }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Text("Break Time", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = viewModel.breakHours, onValueChange = { viewModel.breakHours = it }, label = { Text("HH") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = viewModel.breakMinutes, onValueChange = { viewModel.breakMinutes = it }, label = { Text("MM") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = viewModel.breakSeconds, onValueChange = { viewModel.breakSeconds = it }, label = { Text("SS") }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = viewModel.totalCycles, onValueChange = { viewModel.totalCycles = it }, label = { Text("Number of Cycles") })
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = viewModel.enableDND, onCheckedChange = {
                    viewModel.enableDND = it
                    if (it) viewModel.requestDNDPermission(context)
                })
                Text("Enable DND during Focus?")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = { viewModel.startTimer(context) }, modifier = Modifier.fillMaxWidth()) { Text("Start") }
    }
}

@Composable
fun CountdownScreen(viewModel: TimerModel, context: Context) {
    val hours = viewModel.remainingTimeMillis / 1000 / 3600
    val minutes = viewModel.remainingTimeMillis / 1000 / 60 % 60
    val seconds = viewModel.remainingTimeMillis / 1000 % 60
    val formattedTime = String.format("%02d:%02d:%02d", hours, minutes, seconds)

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
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
        Spacer(Modifier.height(32.dp))

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
                if (viewModel.timerState == TimerState.PAUSED) viewModel.resumeTimer(context)
                else viewModel.pauseTimer()
            }) { Icon(icon, description, modifier = Modifier.size(48.dp)) }

            IconButton(onClick = { viewModel.resetTimer(context) }) {
                Icon(Icons.Default.Close, "Cancel", modifier = Modifier.size(48.dp))
            }
        }
    }
}

@Composable
fun FinishedScreen(viewModel: TimerModel, context: Context) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("All Cycles Complete!", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { viewModel.resetTimer(context) }) { Text("Set New Timer") }
    }
}
