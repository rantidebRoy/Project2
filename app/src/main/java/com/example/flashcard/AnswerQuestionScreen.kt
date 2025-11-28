package com.example.flashcard

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val scope = rememberCoroutineScope()

    // Search state
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var tagSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // Selected question & view
    var selectedQuestion by remember { mutableStateOf<Map<String, Any>?>(null) }
    var subView by remember { mutableStateOf("main") }

    // Other answers
    var otherAnswers by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var loadingOthers by remember { mutableStateOf(false) }

    // 🔎 REALTIME TAG SUGGESTIONS
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            tagSuggestions = emptyList()
            return@LaunchedEffect
        }

        try {
            val snapshot = db.collection("qna_tag")
                .whereGreaterThanOrEqualTo("name", searchQuery)
                .whereLessThanOrEqualTo("name", searchQuery + "\uf8ff")
                .limit(3) // ▩ Show at most 3 items
                .get()
                .await()

            tagSuggestions = snapshot.documents.mapNotNull { it.getString("name") }
        } catch (_: Exception) {}
    }

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

                // ------------------------------- MAIN SEARCH VIEW -------------------------------
                "main" -> {
                    Column(modifier = Modifier.fillMaxWidth()) {

                        // TEXT FIELD
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("Enter tag") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // ▼ DROPDOWN SHOWING MATCHING TAGS
                        if (tagSuggestions.isNotEmpty()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                LazyColumn(
                                    modifier = Modifier
                                        .heightIn(max = 150.dp) // scrollable max height
                                        .background(Color.White)
                                ) {
                                    items(tagSuggestions) { tag ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    // Select tag from dropdown
                                                    searchQuery = tag
                                                    tagSuggestions = emptyList()

                                                    // Auto-search immediately
                                                    isLoading = true
                                                    scope.launch {
                                                        try {
                                                            val snapshot = db.collection("questions")
                                                                .whereEqualTo("tag", tag)
                                                                .get()
                                                                .await()

                                                            searchResults = snapshot.documents.mapNotNull {
                                                                it.data?.plus("id" to it.id)
                                                            }
                                                        } catch (_: Exception) {
                                                            Toast.makeText(context, "Search failed", Toast.LENGTH_SHORT).show()
                                                        } finally {
                                                            isLoading = false
                                                        }
                                                    }
                                                }
                                                .padding(12.dp)
                                        ) {
                                            Text(tag)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // SEARCH BUTTON
                        Button(
                            onClick = {
                                if (searchQuery.isBlank()) {
                                    Toast.makeText(context, "Enter a tag", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                isLoading = true
                                scope.launch {
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
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Search failed", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Search") }

                        Spacer(modifier = Modifier.height(16.dp))

                        // SEARCH RESULTS
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
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
                                            Text(question["title"].toString())
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(question["body"].toString())
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ------------------------------- QUESTION DETAIL -------------------------------
                "question_detail" -> {
                    selectedQuestion?.let { question ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {

                            Text(question["title"].toString(), style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(8.dp))
                            Text(question["body"].toString())
                            Spacer(Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    navController.navigate("submit_answer_screen/${question["id"]}")
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Answer the question") }

                            Spacer(Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    subView = "others_answers"
                                    loadingOthers = true
                                    scope.launch {
                                        try {
                                            val snapshot = db.collection("questions")
                                                .document(question["id"].toString())
                                                .collection("answers")
                                                .get()
                                                .await()

                                            otherAnswers = snapshot.documents.mapNotNull { d ->
                                                d.data?.plus("id" to d.id)
                                            }
                                        } catch (_: Exception) {
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

                // ------------------------------- OTHER ANSWERS -------------------------------
                "others_answers" -> {
                    Column {
                        if (loadingOthers) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        } else if (otherAnswers.isEmpty()) {
                            Text("No answers yet.", modifier = Modifier.align(Alignment.CenterHorizontally))
                        } else {
                            LazyColumn {
                                items(otherAnswers) { ans ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp)
                                    ) {
                                        Column(Modifier.padding(16.dp)) {
                                            Text("Answer by: ${ans["answerer_id"]}")
                                            Spacer(Modifier.height(4.dp))
                                            Text(ans["answer_body"].toString())
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
