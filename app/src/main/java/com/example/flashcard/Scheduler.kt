package com.example.flashcard

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulerScreen(viewModel: SchedulerViewModel = viewModel(), onBack: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Top bar with back button
        TopAppBar(
            title = { Text("Scheduler") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Task Title") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Task Description") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                if (title.isNotBlank() && description.isNotBlank()) {
                    val task = SchedulerTask(
                        title = title,
                        description = description,
                        timeMillis = System.currentTimeMillis() + 60000
                    )
                    viewModel.addTask(task)
                    title = ""
                    description = ""
                } else {
                    Toast.makeText(context, "Fill all fields", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add Task")
        }

        Spacer(Modifier.height(20.dp))

        // Display tasks without scrolling, sorted by time
        viewModel.tasks.sortedBy { it.timeMillis }.forEach { task ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth(),
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
}
