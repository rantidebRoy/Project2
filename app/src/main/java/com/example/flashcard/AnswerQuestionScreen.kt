package com.example.flashcard

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnswerQuestionScreen(
    navController: NavController,
    onBack: () -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current

    // ✅ Use remember Save able so data persists when navigating away and back
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchResults by rememberSaveable { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Answer Questions") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (searchQuery.isNotBlank()) {
                                isLoading = true
                                db.collection("questions")
                                    .whereEqualTo("tag", searchQuery.trim())
                                    .get()
                                    .addOnSuccessListener { snapshot ->
                                        searchResults = snapshot.documents.mapNotNull { it.data?.plus("id" to it.id) }
                                        isLoading = false
                                        if (searchResults.isEmpty()) {
                                            Toast.makeText(context, "No questions found", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .addOnFailureListener {
                                        isLoading = false
                                        Toast.makeText(context, "Search failed", Toast.LENGTH_SHORT).show()
                                    }
                            } else {
                                Toast.makeText(context, "Enter a tag to search", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Search")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Enter tag") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                CircularProgressIndicator()
            } else if (searchResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Results for \"$searchQuery\"",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    searchResults.forEach { question ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable {
                                    val qId = question["id"].toString()
                                    navController.navigate("submit_answer_screen/$qId")
                                }
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
                                    text = question["body"]?.toString() ?: "(No Body)",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tag: ${question["tag"] ?: "N/A"}",
                                    style = MaterialTheme.typography.labelMedium
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
}
