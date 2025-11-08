package com.example.flashcard

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

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
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Scheduler", style = MaterialTheme.typography.headlineMedium)
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
                Spacer(Modifier.height(20.dp))

                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text("Back")
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
                Text(if (dateFilter == "today") "Today's Schedule" else "Tomorrow's Schedule",
                    style = MaterialTheme.typography.headlineMedium)
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

                Spacer(Modifier.height(20.dp))
                Button(onClick = { currentView = "main" }, modifier = Modifier.fillMaxWidth()) {
                    Text("Back")
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
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Event Details", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(16.dp))
                    Text("Title: ${event["title"]}")
                    Spacer(Modifier.height(8.dp))
                    Text("Description: ${event["description"]}")
                    Spacer(Modifier.height(8.dp))
                    Text("Time: $timeStr")
                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (userId.isNotEmpty() && docId != null) {
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
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete Event")
                    }

                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { currentView = if (dateFilter == "today") "today" else "tomorrow" },
                        modifier = Modifier.fillMaxWidth()) {
                        Text("Back")
                    }
                }
            }
        }
    }
}

// --- Composable for Adding Event ---
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
        Text("Add Event", style = MaterialTheme.typography.headlineMedium)
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

        Spacer(Modifier.height(12.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}
