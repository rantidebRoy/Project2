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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublishedQuestionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val user = FirebaseAuth.getInstance().currentUser
    var userId by remember { mutableStateOf<Long?>(null) }
    var publishedQuestions by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // --- Step 1: Fetch user's sequential id from Firestore ---
    LaunchedEffect(user?.uid) {
        val uid = user?.uid
        if (uid != null) {
            db.collection("users").document(uid).get()
                .addOnSuccessListener { snapshot ->
                    userId = snapshot.getLong("id")
                    if (userId != null) {
                        // Step 2: Fetch all questions for this user
                        db.collection("questions")
                            .whereEqualTo("owner_id", userId)
                            .get()
                            .addOnSuccessListener { querySnapshot ->
                                publishedQuestions = querySnapshot.documents.mapNotNull { it.data }
                                isLoading = false
                            }
                            .addOnFailureListener {
                                isLoading = false
                                Toast.makeText(context, "Failed to load questions", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        isLoading = false
                        Toast.makeText(context, "User ID not found", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    isLoading = false
                    Toast.makeText(context, "Failed to fetch user info", Toast.LENGTH_SHORT).show()
                }
        } else {
            isLoading = false
            Toast.makeText(context, "User not logged in", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Published Questions") },
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
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                publishedQuestions.isEmpty() -> {
                    Text(
                        text = "You haven't published any questions yet.",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        items(publishedQuestions) { question ->
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
                }
            }
        }
    }
}


