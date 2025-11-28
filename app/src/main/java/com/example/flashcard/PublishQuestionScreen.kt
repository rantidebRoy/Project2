package com.example.flashcard

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublishQuestionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("") }

    var sequentialId by remember { mutableStateOf<Int?>(null) }

    // Tag Suggestions
    var suggestions by remember { mutableStateOf(listOf<String>()) }
    var showDropdown by remember { mutableStateOf(false) }

    // Fetch user's sequential ID
    LaunchedEffect(userId) {
        if (userId.isNotEmpty()) {
            db.collection("users").document(userId).get()
                .addOnSuccessListener { snapshot ->
                    sequentialId = snapshot.getLong("id")?.toInt()
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Failed to fetch user ID", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // ------------------------------
    // REALTIME TAG MATCHING
    // ------------------------------
    LaunchedEffect(tag) {
        if (tag.isBlank()) {
            suggestions = emptyList()
            showDropdown = false
            return@LaunchedEffect
        }

        val result = db.collection("qna_tag")
            .whereGreaterThanOrEqualTo("name", tag)
            .whereLessThanOrEqualTo("name", tag + "\uf8ff")
            .limit(3)
            .get()
            .await()

        suggestions = result.documents.mapNotNull { it.getString("name") }
        showDropdown = suggestions.isNotEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {

        TopAppBar(
            title = { Text("Publish Question") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                TextButton(
                    onClick = {
                        if (title.isNotBlank() && body.isNotBlank() && tag.isNotBlank()) {

                            if (sequentialId == null) {
                                Toast.makeText(context, "User ID not loaded", Toast.LENGTH_SHORT).show()
                                return@TextButton
                            }

                            // ------------------------------
                            // CHECK IF TAG EXISTS FIRST
                            // ------------------------------
                            db.collection("qna_tag")
                                .whereEqualTo("name", tag)
                                .get()
                                .addOnSuccessListener { snapshot ->
                                    if (snapshot.isEmpty) {
                                        // Add new tag if not present
                                        val newTag = hashMapOf("name" to tag)
                                        db.collection("qna_tag").add(newTag)
                                    }

                                    // ------------------------------
                                    // PUBLISH QUESTION
                                    // ------------------------------
                                    val question = hashMapOf(
                                        "title" to title,
                                        "body" to body,
                                        "tag" to tag,
                                        "owner_id" to sequentialId
                                    )

                                    db.collection("questions")
                                        .add(question)
                                        .addOnSuccessListener {
                                            Toast.makeText(
                                                context,
                                                "Question Published!",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            onBack()
                                        }
                                        .addOnFailureListener { e ->
                                            Toast.makeText(
                                                context,
                                                "Failed: ${e.message}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                }

                        } else {
                            Toast.makeText(context, "Fill all fields", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Publish")
                }
            }
        )

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Question Title") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            label = { Text("Question Body") },
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        )

        Spacer(Modifier.height(16.dp))

        // ------------------------------
        // TAG INPUT + DROPDOWN SUGGESTIONS
        // ------------------------------
        Box {
            OutlinedTextField(
                value = tag,
                onValueChange = {
                    tag = it
                },
                label = { Text("Tag") },
                modifier = Modifier.fillMaxWidth()
            )

            DropdownMenu(
                expanded = showDropdown,
                onDismissRequest = { showDropdown = false },
                modifier = Modifier
                    .background(Color.White)
                    .heightIn(max = 150.dp)   // max 3 items scrollable
            ) {
                suggestions.forEach { suggestion ->
                    DropdownMenuItem(
                        text = { Text(suggestion) },
                        onClick = {
                            tag = suggestion
                            showDropdown = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        if (sequentialId == null) {
            Text(
                text = "Fetching user ID...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            Text("Your User ID: $sequentialId", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
