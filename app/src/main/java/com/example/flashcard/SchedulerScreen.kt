package com.example.flashcard

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// ----------------------------------------------------------------------
// SchedulerScreen
// ----------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulerScreen(onBack: () -> Unit) {
    var currentView by remember { mutableStateOf("main") }
    var selectedEvent by remember { mutableStateOf<Map<String, Any>?>(null) }
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    var dateFilter by remember { mutableStateOf("") }

    when (currentView) {

        // --- Main Menu ---
        "main" -> {
            val schedulerCards = listOf(
                CardItem(
                    title = "Today's\nSchedule",
                    icon = Icons.Default.CalendarToday,
                    onClick = {
                        dateFilter = "today"
                        currentView = "today"
                    }
                ),
                CardItem(
                    title = "Tomorrow's\nSchedule",
                    icon = Icons.Default.CalendarToday,
                    onClick = {
                        dateFilter = "tomorrow"
                        currentView = "tomorrow"
                    }
                ),
                CardItem(
                    title = "Add Event",
                    icon = Icons.Default.Add,
                    onClick = { currentView = "add" }
                )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            ) {
                TopAppBar(
                    title = { Text("Scheduler") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp)   // content only
                ) {
                    Spacer(modifier = Modifier.height(24.dp))

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(schedulerCards.size) { index ->
                            FeatureCard(schedulerCards[index])
                        }
                    }
                }
            }
        }

        // --- Add Event ---
        "add" -> AddEventScreen(
            onBack = { currentView = "main" },
            onSave = { title, description, date, hour, minute ->
                if (userId.isNotEmpty()) {
                    val calendar = java.util.Calendar.getInstance()
                    if (date == "tomorrow") {
                        calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
                    }
                    // Set time to the event time or just keep the date part logic? 
                    // Requirement says "before 12.00 am" implying midnight of that day.
                    // We just store the creation/target timestamp to help calculate deadlines if needed, 
                    // or simply rely on "date" string and current time check.
                    // Let's store the target event time as timestamp for easier comparison.
                    calendar.set(java.util.Calendar.HOUR_OF_DAY, hour)
                    calendar.set(java.util.Calendar.MINUTE, minute)
                    calendar.set(java.util.Calendar.SECOND, 0)
                    
                    val event = mapOf(
                        "title" to title,
                        "description" to description,
                        "date" to date,
                        "hour" to hour,
                        "minute" to minute,
                        "isChecked" to false,
                        "timestamp" to calendar.timeInMillis
                    )
                    db.collection("users")
                        .document(userId)
                        .collection("events")
                        .add(event)
                        .addOnSuccessListener { docRef ->
                            Toast.makeText(context, "Event saved!", Toast.LENGTH_SHORT).show()

                            NotificationScheduler.scheduleEvent(
                                context = context,
                                title = title,
                                description = description,
                                dateType = date,
                                hour = hour,
                                minute = minute,
                                docId = docRef.id,
                                userId = userId
                            )

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

            DisposableEffect(dateFilter) {
                var listener: com.google.firebase.firestore.ListenerRegistration? = null
                
                if (userId.isNotEmpty()) {
                     listener = db.collection("users")
                        .document(userId)
                        .collection("events")
                        .whereEqualTo("date", dateFilter)
                        .addSnapshotListener { snapshot, e ->
                            if (e != null) {
                                Toast.makeText(context, "Failed to load events", Toast.LENGTH_SHORT).show()
                                return@addSnapshotListener
                            }
                            if (snapshot != null) {
                                events = snapshot.documents.mapNotNull { doc ->
                                    val data = doc.data ?: return@mapNotNull null
                                    val hour = (data["hour"] as? Long ?: 0L).toInt()
                                    val minute = (data["minute"] as? Long ?: 0L).toInt()
                                    data + ("docId" to doc.id) + ("sortTime" to hour * 60 + minute)
                                }.sortedBy { it["sortTime"] as Int }
                            }
                        }
                }
                
                onDispose {
                    listener?.remove()
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            ) {
                TopAppBar(
                    title = {
                        Text(
                            if (dateFilter == "today") "Today's Schedule" else "Tomorrow's Schedule"
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { currentView = "main" }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)   // content only
                ) {
                    Spacer(Modifier.height(16.dp))

                    if (events.isEmpty()) {
                        Text("No events found.")
                    } else {
                        LazyColumn {
                            items(events.size) { index ->
                                val event = events[index]
                                val docId = event["docId"] as? String ?: ""
                                val isChecked = event["isChecked"] as? Boolean ?: false
                                
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            selectedEvent = event
                                            currentView = "details"
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    elevation = CardDefaults.cardElevation(2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Auto-tick logic kept (via Notification), but Manual tick also allowed.
                                        if (dateFilter == "today") {
                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = { checked ->
                                                    if (userId.isNotEmpty() && docId.isNotEmpty()) {
                                                        db.collection("users")
                                                            .document(userId)
                                                            .collection("events")
                                                            .document(docId)
                                                            .update("isChecked", checked)
                                                            .addOnSuccessListener {
                                                                // Update local list
                                                                events = events.map { 
                                                                    if (it["docId"] == docId) it + ("isChecked" to checked) else it
                                                                }
                                                            }
                                                    }
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        
                                        Text(
                                            text = event["title"] as? String ?: "",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
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
                        .background(Color.White)
                ) {
                    TopAppBar(
                        title = {
                            Text(
                                "Event Details",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                currentView = if (dateFilter == "today") "today" else "tomorrow"
                            }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
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
                                            Toast.makeText(
                                                context,
                                                "Event deleted",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            currentView =
                                                if (dateFilter == "today") "today" else "tomorrow"
                                        }
                                        .addOnFailureListener { e ->
                                            Toast.makeText(
                                                context,
                                                "Delete failed: ${e.message}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete Event"
                                    )
                                }
                            }
                        }
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            "Title: ${event["title"]}",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Description: ${event["description"]}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Time: $timeStr",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(24.dp))
                        
                        val isChecked = event["isChecked"] as? Boolean ?: false
                        val titleVal = event["title"] as? String ?: "Task"
                        
                        if (isChecked) {
                            Text(
                                text = "The task has been accomplished",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = Color(0xFF4CAF50), // Green
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------
// AddEventScreen
// ----------------------------------------------------------------------

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
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        TopAppBar(
            title = { Text("Add Event") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        )

        // CONTENT ONLY – no fillMaxSize here
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            )
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = { date = "today" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (date == "today")
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (date == "today")
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) { Text("Today") }

                Button(
                    onClick = { date = "tomorrow" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (date == "tomorrow")
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (date == "tomorrow")
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) { Text("Tomorrow") }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
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
                    // Fix: Check if hour and minute strings are actually filled
                    if (title.isNotBlank() && description.isNotBlank() && hour.isNotBlank() && minute.isNotBlank()) {
                         // Validate time range
                        if (hourInt in 0..23 && minuteInt in 0..59) {
                            onSave(title, description, date, hourInt, minuteInt)
                        } else {
                            Toast.makeText(context, "Invalid time", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Fill all fields", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Event")
            }
        }
    }
}
