package com.example.flashcard

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtherAnswersListScreen(
    questionId: String,
    onBack: () -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var otherAnswers by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    // Logged-in user's UID
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    // Logged-in user's sequential ID
    var mySequentialId by remember { mutableStateOf<Int?>(null) }

    // Fetch CURRENT USER'S sequential ID
    LaunchedEffect(userId) {
        if (userId.isNotEmpty()) {
            db.collection("users").document(userId).get()
                .addOnSuccessListener { snapshot ->
                    mySequentialId = snapshot.getLong("id")?.toInt()
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Failed to fetch user ID", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // Fetch all answers
    LaunchedEffect(questionId) {
        coroutineScope.launch {
            try {
                val snapshot = db.collection("questions")
                    .document(questionId)
                    .collection("answers")
                    .get()
                    .await()

                otherAnswers = snapshot.documents.mapNotNull { d ->
                    d.data?.plus("id" to d.id) // ⭐ include Firestore doc ID ⭐
                }

            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load answers", Toast.LENGTH_SHORT).show()
            } finally {
                loading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Other Answers") },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {

            if (loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else if (otherAnswers.isEmpty()) {
                Text("No answers yet.", modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {

                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    items(otherAnswers) { ans ->

                        val answererId = (ans["answerer_id"] as? Long)?.toInt()
                            ?: ans["answerer_id"]?.toString()?.toIntOrNull()

                        val answerId = ans["id"]?.toString() ?: ""

                        val isMine = answererId != null && answererId == mySequentialId

                        val displayName = if (isMine) "me" else answererId?.toString() ?: "Unknown"

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Answer by: $displayName",
                                        style = MaterialTheme.typography.titleSmall
                                    )

                                    // ⭐ SHOW DELETE ONLY FOR MY OWN ANSWERS ⭐
                                    if (isMine) {
                                        IconButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    try {
                                                        db.collection("questions")
                                                            .document(questionId)
                                                            .collection("answers")
                                                            .document(answerId)
                                                            .delete()
                                                            .await()

                                                        // Update UI instantly
                                                        otherAnswers =
                                                            otherAnswers.filter { it["id"] != answerId }

                                                        Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Delete failed", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    ans["answer_body"]?.toString() ?: "(Empty)",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            // ⭐ SHOW YOUR SEQUENTIAL ID ⭐
            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Your ID: ${mySequentialId ?: "Loading..."}",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}
