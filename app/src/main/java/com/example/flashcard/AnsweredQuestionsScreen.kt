package com.example.flashcard

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnsweredQuestionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val user = FirebaseAuth.getInstance().currentUser
    var answeredList by remember { mutableStateOf<List<Pair<Map<String, Any>, Map<String, Any>>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(user?.uid) {
        if (user?.uid == null) {
            isLoading = false
            Toast.makeText(context, "User not logged in", Toast.LENGTH_SHORT).show()
            return@LaunchedEffect
        }

        coroutineScope.launch {
            try {
                val userIdSnapshot = db.collection("users").document(user.uid).get().await()
                val userId = userIdSnapshot.getLong("id") ?: run {
                    isLoading = false
                    Toast.makeText(context, "User ID not found", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val tempList = mutableListOf<Pair<Map<String, Any>, Map<String, Any>>>()

                // Fetch all questions
                val questionsSnapshot = db.collection("questions").get().await()
                for (questionDoc in questionsSnapshot.documents) {
                    val questionData = questionDoc.data ?: continue

                    // Fetch answers subcollection for this question
                    val answersSnapshot = db.collection("questions")
                        .document(questionDoc.id)
                        .collection("answers")
                        .whereEqualTo("owner_id", userId)
                        .get()
                        .await()

                    for (answerDoc in answersSnapshot.documents) {
                        val answerData = answerDoc.data ?: continue
                        tempList.add(questionData to answerData)
                    }
                }

                answeredList = tempList
                isLoading = false

            } catch (e: Exception) {
                isLoading = false
                Toast.makeText(context, "Failed to fetch answered questions", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Answered Questions") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                answeredList.isEmpty() -> Text(
                    text = "You haven’t answered any questions yet.",
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    items(answeredList) { (question, answer) ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = question["title"]?.toString() ?: "(No Title)",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = question["body"]?.toString() ?: "(No Description)",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Your Answer:",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = answer["body"]?.toString() ?: "(No Answer Provided)",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
