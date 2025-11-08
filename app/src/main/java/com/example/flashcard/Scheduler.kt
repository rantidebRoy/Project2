package com.example.flashcard

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*
import androidx.compose.ui.platform.LocalContext


data class SchedulerTask(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val description: String,
    val timeMillis: Long
)

// ViewModel for Scheduler
class SchedulerViewModel : ViewModel() {
    var tasks by mutableStateOf(listOf<SchedulerTask>())
        private set

    fun addTask(task: SchedulerTask) {
        tasks = tasks + task
    }

    fun removeTask(task: SchedulerTask) {
        tasks = tasks - task
    }
}

// BroadcastReceiver for Alarm
class SchedulerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "Reminder"
        val description = intent.getStringExtra("description") ?: ""
        Toast.makeText(context, "Alarm: $title\n$description", Toast.LENGTH_LONG).show()
    }
}

// Scheduler Composable
@Composable
fun SchedulerScreen(viewModel: SchedulerViewModel = viewModel(), onBack: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var timeMillis by remember { mutableStateOf(System.currentTimeMillis() + 60000) } // default 1 min later
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Scheduler", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Task Title") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Task Description") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                val task = SchedulerTask(title = title, description = description, timeMillis = timeMillis)
                viewModel.addTask(task)

                // Set alarm
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, SchedulerAlarmReceiver::class.java).apply {
                    putExtra("title", title)
                    putExtra("description", description)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    task.id.toInt(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)

                title = ""
                description = ""
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add Task")
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn {
            items(viewModel.tasks.size) { index ->
                val task = viewModel.tasks[index]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Title: ${task.title}")
                            Text("Desc: ${task.description}")
                        }
                        Button(onClick = { viewModel.removeTask(task) }) {
                            Text("Delete")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}
