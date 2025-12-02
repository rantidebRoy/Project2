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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
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

    // ---------------- USER SEQUENTIAL ID ----------------
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    var sequentialId by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(userId) {
        if (userId.isNotEmpty()) {
            db.collection("users").document(userId).get()
                .addOnSuccessListener { snap ->
                    sequentialId = snap.getLong("id")?.toInt()
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Failed to load ID", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // Search state
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var tagSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // Selected question
    var selectedQuestion by remember { mutableStateOf<Map<String, Any>?>(null) }
    var subView by remember { mutableStateOf("main") }

    // Other answers
    var otherAnswers by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var loadingOthers by remember { mutableStateOf(false) }

    // ---------------- TAG SUGGESTIONS ----------------
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            tagSuggestions = emptyList()
            return@LaunchedEffect
        }

        try {
            val snapshot = db.collection("qna_tag")
                .whereGreaterThanOrEqualTo("name", searchQuery)
                .whereLessThanOrEqualTo("name", searchQuery + "\uf8ff")
                .limit(3)
                .get()
                .await()

            tagSuggestions = snapshot.documents.mapNotNull { it.getString("name") }
        } catch (_: Exception) { }
    }

    Scaffold(
        containerColor = Color.White,
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

                // ---------------- MAIN SEARCH VIEW ----------------
                "main" -> {
                    Column(modifier = Modifier.fillMaxWidth()) {

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("Enter tag") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (tagSuggestions.isNotEmpty()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                LazyColumn(
                                    modifier = Modifier.heightIn(max = 150.dp)
                                ) {
                                    items(tagSuggestions) { tag ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    searchQuery = tag
                                                    tagSuggestions = emptyList()

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
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Search") }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        } else {
                            LazyColumn {
                                items(searchResults) { q ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp)
                                            .clickable {
                                                selectedQuestion = q
                                                subView = "question_detail"
                                            }
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(q["title"].toString())
                                            Text(q["body"].toString())
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ---------------- QUESTION DETAIL ----------------
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

                                            otherAnswers = snapshot.documents.mapNotNull {
                                                it.data?.plus("id" to it.id)
                                            }
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

                // ---------------- OTHER ANSWERS ----------------
                "others_answers" -> {
                    Column {

                        if (loadingOthers) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        } else if (otherAnswers.isEmpty()) {
                            Text("No answers yet.", modifier = Modifier.align(Alignment.CenterHorizontally))
                        } else {
                            LazyColumn {
                                items(otherAnswers) { ans ->

                                    val answererId = ans["answerer_id"]?.toString()
                                    val answerId = ans["id"].toString()
                                    val questionId = selectedQuestion!!["id"].toString()

                                    val isMine = sequentialId != null && answererId == sequentialId.toString()

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp)
                                    ) {
                                        Column(Modifier.padding(16.dp)) {

                                            Row(
                                                Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "Answer by: " +
                                                            if (isMine) "me"
                                                            else answererId
                                                )

                                                if (isMine) {
                                                    IconButton(onClick = {
                                                        scope.launch {
                                                            try {
                                                                db.collection("questions")
                                                                    .document(questionId)
                                                                    .collection("answers")
                                                                    .document(answerId)
                                                                    .delete()
                                                                    .await()

                                                                otherAnswers =
                                                                    otherAnswers.filter { it["id"] != answerId }

                                                                Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                                                            } catch (e: Exception) {
                                                                Toast.makeText(context, "Delete failed", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    }) {
                                                        Icon(
                                                            Icons.Default.Delete,
                                                            contentDescription = "Delete Answer",
                                                            tint = Color.Red
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(Modifier.height(4.dp))
                                            Text(ans["answer_body"].toString())
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        // Show My Sequential ID
                        sequentialId?.let {
                            Text(
                                "My Sequential ID: $it",
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}
