package com.example.flashcard

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulerScreen(onBack: () -> Unit) {
    var currentView by remember { mutableStateOf("main") } // "main", "add", "today", "tomorrow", "details"
    var selectedEvent by remember { mutableStateOf<Map<String, Any>?>(null) }
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    var dateFilter by remember { mutableStateOf("") }

    when (currentView) {

        // --- Main Menu ---
        "main" -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TopAppBar(
                    title = { Text("Scheduler") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )

                Spacer(Modifier.height(20.dp))

                Button(onClick = {
                    dateFilter = "today"
                    currentView = "today"
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Today's Schedule")
                }
                Spacer(Modifier.height(12.dp))

                Button(onClick = {
                    dateFilter = "tomorrow"
                    currentView = "tomorrow"
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Tomorrow's Schedule")
                }
                Spacer(Modifier.height(12.dp))

                Button(onClick = { currentView = "add" }, modifier = Modifier.fillMaxWidth()) {
                    Text("Add Event")
                }
            }
        }

        // --- Add Event ---
        "add" -> AddEventScreen(
            onBack = { currentView = "main" },
            onSave = { title, description, date, hour, minute ->
                if (userId.isNotEmpty()) {
                    val event = mapOf(
                        "title" to title,
                        "description" to description,
                        "date" to date,
                        "hour" to hour,
                        "minute" to minute
                    )
                    db.collection("users")
                        .document(userId)
                        .collection("events")
                        .add(event)
                        .addOnSuccessListener {
                            Toast.makeText(context, "Event saved!", Toast.LENGTH_SHORT).show()

                            // >>> FIX: Schedule the system notification immediately <<<
                            NotificationScheduler.scheduleEvent(
                                context = context,
                                title = title,
                                description = description,
                                dateType = date, // "today" or "tomorrow"
                                hour = hour,
                                minute = minute
                            )
                            // >>> END FIX <<<

                            currentView = "main"
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Toast.makeText(context, "User not logged in", Toast.LENGTH_SHORT).show()
                }
            }
        )

        // --- Event List for Today/Tomorrow ---
        "today", "tomorrow" -> {
            var events by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }

            LaunchedEffect(dateFilter) {
                if (userId.isNotEmpty()) {
                    db.collection("users")
                        .document(userId)
                        .collection("events")
                        .whereEqualTo("date", dateFilter)
                        .get()
                        .addOnSuccessListener { snapshot ->
                            events = snapshot.documents.mapNotNull { doc ->
                                val data = doc.data ?: return@mapNotNull null
                                val hour = (data["hour"] as? Long ?: 0L).toInt()
                                val minute = (data["minute"] as? Long ?: 0L).toInt()
                                data + ("docId" to doc.id) + ("sortTime" to hour * 60 + minute)
                            }.sortedBy { it["sortTime"] as Int }
                        }
                        .addOnFailureListener {
                            Toast.makeText(context, "Failed to load events", Toast.LENGTH_SHORT).show()
                        }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                TopAppBar(
                    title = {
                        Text(if (dateFilter == "today") "Today's Schedule" else "Tomorrow's Schedule")
                    },
                    navigationIcon = {
                        IconButton(onClick = { currentView = "main" }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )

                Spacer(Modifier.height(16.dp))

                if (events.isEmpty()) {
                    Text("No events found.")
                } else {
                    LazyColumn {
                        items(events.size) { index ->
                            val event = events[index]
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        selectedEvent = event
                                        currentView = "details"
                                    }
                            ) {
                                Box(Modifier.padding(16.dp)) {
                                    Text(event["title"] as? String ?: "")
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Event Details ---
        "details" -> {
            selectedEvent?.let { event ->
                val docId = event["docId"] as? String
                val hour = (event["hour"] as? Long ?: 0L).toInt()
                val minute = (event["minute"] as? Long ?: 0L).toInt()
                val timeStr = String.format("%02d:%02d", hour, minute)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    TopAppBar(
                        title = { Text("Event Details") },
                        navigationIcon = {
                            IconButton(onClick = {
                                currentView = if (dateFilter == "today") "today" else "tomorrow"
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            if (userId.isNotEmpty() && docId != null) {
                                IconButton(onClick = {
                                    db.collection("users")
                                        .document(userId)
                                        .collection("events")
                                        .document(docId)
                                        .delete()
                                        .addOnSuccessListener {
                                            Toast.makeText(context, "Event deleted", Toast.LENGTH_SHORT).show()
                                            currentView = if (dateFilter == "today") "today" else "tomorrow"
                                        }
                                        .addOnFailureListener { e ->
                                            Toast.makeText(context, "Delete failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Event")
                                }
                            }
                        }
                    )

                    // Scrollable content
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Spacer(Modifier.height(16.dp))
                        Text("Title: ${event["title"]}", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(12.dp))
                        Text("Description: ${event["description"]}", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(12.dp))
                        Text("Time: $timeStr", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventScreen(
    onBack: () -> Unit,
    onSave: (String, String, String, Int, Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("today") }
    var hour by remember { mutableStateOf("") }
    var minute by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TopAppBar(
            title = { Text("Add Event") },
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
            label = { Text("Event Title") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Event Description") },
            modifier = Modifier.fillMaxWidth().height(120.dp)
        )
        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(
                onClick = { date = "today" },
                colors = ButtonDefaults.buttonColors(
                    if (date == "today") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
            ) { Text("Today") }

            Button(
                onClick = { date = "tomorrow" },
                colors = ButtonDefaults.buttonColors(
                    if (date == "tomorrow") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
            ) { Text("Tomorrow") }
        }

        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedTextField(
                value = hour,
                onValueChange = { hour = it.filter { c -> c.isDigit() } },
                label = { Text("Hour") },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(
                value = minute,
                onValueChange = { minute = it.filter { c -> c.isDigit() } },
                label = { Text("Minute") },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                val hourInt = hour.toIntOrNull() ?: 0
                val minuteInt = minute.toIntOrNull() ?: 0
                if (title.isNotBlank() && description.isNotBlank()) {
                    onSave(title, description, date, hourInt, minuteInt)
                } else {
                    Toast.makeText(context, "Fill all fields", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save Event") }
    }
}