package com.example.flashcard

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

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

    // Fetch user's sequential ID (stored under "id" field)
    LaunchedEffect(userId) {
        if (userId.isNotEmpty()) {
            db.collection("users").document(userId).get()
                .addOnSuccessListener { snapshot ->
                    val id = snapshot.getLong("id")?.toInt()
                    sequentialId = id
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Failed to fetch user ID", Toast.LENGTH_SHORT).show()
                }
        }
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
                                Toast.makeText(context, "User ID not loaded yet", Toast.LENGTH_SHORT).show()
                                return@TextButton
                            }

                            val question = hashMapOf(
                                "title" to title,
                                "body" to body,
                                "tag" to tag,
                                "owner_id" to sequentialId
                            )

                            db.collection("questions")
                                .add(question)
                                .addOnSuccessListener {
                                    Toast.makeText(context, "Question published!", Toast.LENGTH_SHORT).show()
                                    onBack()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
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

        OutlinedTextField(
            value = tag,
            onValueChange = { tag = it },
            label = { Text("Tag") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

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
