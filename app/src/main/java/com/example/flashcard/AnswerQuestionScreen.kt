package com.example.flashcard

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnswerQuestionScreen(
    navController: NavController,
    onBack: () -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Search state
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchResults by rememberSaveable { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // Selected question & view
    var selectedQuestion by remember { mutableStateOf<Map<String, Any>?>(null) }
    var subView by remember { mutableStateOf("main") } // "main", "question_detail", "others_answers"

    // Others' answers
    var otherAnswers by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var loadingOthers by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Find Questions") },
                navigationIcon = {
                    IconButton(onClick = {
                        when (subView) {
                            "main" -> onBack()
                            else -> subView = "main"
                        }
                    }) {
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
                .padding(16.dp)
        ) {
            when (subView) {

                // --- Main search view ---
                "main" -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("Enter tag") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (searchQuery.isNotBlank()) {
                                    isLoading = true
                                    coroutineScope.launch {
                                        try {
                                            val snapshot = db.collection("questions")
                                                .whereEqualTo("tag", searchQuery.trim())
                                                .get()
                                                .await()
                                            searchResults = snapshot.documents.mapNotNull {
                                                it.data?.plus("id" to it.id)
                                            }
                                            if (searchResults.isEmpty()) {
                                                Toast.makeText(context, "No questions found", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Search failed", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                } else {
                                    Toast.makeText(context, "Enter a tag to search", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Search") }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (isLoading) {
                            CircularProgressIndicator()
                        } else if (searchResults.isNotEmpty()) {
                            LazyColumn {
                                items(searchResults) { question ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp)
                                            .clickable {
                                                selectedQuestion = question
                                                subView = "question_detail"
                                            }
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                question["title"]?.toString() ?: "(No Title)",
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                question["body"]?.toString() ?: "(No Body)",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("No results yet. Enter a tag and tap Search.")
                        }
                    }
                }

                // --- Question detail view ---
                "question_detail" -> {
                    selectedQuestion?.let { question ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                question["title"]?.toString() ?: "(No Title)",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                question["body"]?.toString() ?: "(No Body)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    navController.navigate("submit_answer_screen/${question["id"]}")
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Answer the question") }

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    // Load others' answers from subcollection
                                    subView = "others_answers"
                                    loadingOthers = true
                                    otherAnswers = emptyList()
                                    coroutineScope.launch {
                                        try {
                                            val answersSnapshot = db.collection("questions")
                                                .document(question["id"].toString())
                                                .collection("answers")
                                                .get()
                                                .await()

                                            otherAnswers = answersSnapshot.documents.mapNotNull { doc ->
                                                doc.data?.plus("id" to doc.id)
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Failed to load answers", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            loadingOthers = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Other answers") }
                        }
                    }
                }

                // --- Others' answers view ---
                "others_answers" -> {
                    Column {
                        if (loadingOthers) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        } else if (otherAnswers.isEmpty()) {
                            Text(
                                "No answers yet.",
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(otherAnswers) { answer ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                text = "Answer by ${answer["answerer_id"] ?: "Unknown"}",
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = answer["answer_body"]?.toString() ?: "(No Answer Provided)",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
