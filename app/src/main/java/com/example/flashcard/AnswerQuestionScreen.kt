package com.example.flashcard

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
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
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import coil.compose.AsyncImage
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.filled.Close

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
    var searchQuery by rememberSaveable(stateSaver = androidx.compose.ui.text.input.TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var tagSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // Selected question
    var selectedQuestion by remember { mutableStateOf<Map<String, Any>?>(null) }
    var selectedAnswer by remember { mutableStateOf<Map<String, Any>?>(null) }
    var subView by remember { mutableStateOf("main") }

    // Other answers
    var otherAnswers by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var loadingOthers by remember { mutableStateOf(false) }

    // Helper to perform search
    fun performSearch(queryText: String) {
        if (queryText.isBlank()) {
            Toast.makeText(context, "Enter a tag", Toast.LENGTH_SHORT).show()
            return
        }
        isLoading = true
        scope.launch {
            try {
                val snapshot = db.collection("questions")
                    .whereEqualTo("tag", queryText.trim())
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

    // ---------------- TAG SUGGESTIONS ----------------
    LaunchedEffect(searchQuery.text) {
        if (searchQuery.text.isBlank()) {
            tagSuggestions = emptyList()
            return@LaunchedEffect
        }

        try {
            val snapshot = db.collection("qna_tag")
                .whereGreaterThanOrEqualTo("name", searchQuery.text)
                .whereLessThanOrEqualTo("name", searchQuery.text + "\uf8ff")
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
                            "question_detail" -> subView = "main"
                            "others_answers" -> subView = "question_detail"
                            "answer_detail" -> subView = "others_answers"
                            else -> subView = "main"
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->

        var viewingImage by remember { mutableStateOf<String?>(null) }

        // --- Image Viewer Dialog ---
        if (viewingImage != null) {
            Dialog(onDismissRequest = { viewingImage = null }) {
                var scale by remember { mutableStateOf(1f) }
                var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                val state = rememberTransformableState { zoomChange, panChange, _ ->
                    scale = (scale * zoomChange).coerceAtLeast(1f)
                    offset += panChange
                }

                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable { viewingImage = null }
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .transformable(state = state)
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = viewingImage,
                            contentDescription = "Full Image",
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.Fit
                        )
                    }
                    IconButton(
                        onClick = { viewingImage = null },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
        }

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
                                                    val newText = "$tag "
                                                    searchQuery = TextFieldValue(newText, TextRange(newText.length))
                                                    tagSuggestions = emptyList()
                                                    performSearch(tag)
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
                            onClick = { performSearch(searchQuery.text) },
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
                                            Text(
                                                text = q["title"].toString(),
                                                style = MaterialTheme.typography.titleMedium,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = q["body"].toString(),
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 4,
                                                overflow = TextOverflow.Ellipsis
                                            )
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

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp)
                                    .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                                    .padding(8.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(question["title"].toString(), style = MaterialTheme.typography.titleLarge)
                            }
                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                                    .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                                    .padding(8.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(question["body"].toString())
                            }
                            Spacer(Modifier.height(16.dp))

                            val imageUrl = question["imageUrl"]?.toString()
                            if (!imageUrl.isNullOrBlank()) {
                                Button(
                                    onClick = { viewingImage = imageUrl },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("View Question Image") }
                                Spacer(Modifier.height(12.dp))
                            }

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
                                                .orderBy("timestamp", Query.Direction.DESCENDING)
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
                        Text(
                            text = "Click an option to view the answer in detail",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .align(Alignment.CenterHorizontally)
                        )

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
                                            .clickable {
                                                selectedAnswer = ans
                                                subView = "answer_detail"
                                            }
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
                                                            tint = Color.Black
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = ans["answer_body"].toString(),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
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

                // ---------------- ANSWER DETAIL ----------------
                "answer_detail" ->  {
                    selectedAnswer?.let { ans ->
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Answer by: " + (ans["answerer_id"]?.toString() ?: "Unknown"),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(16.dp))

                            // 5 line tall scrollable box (~120dp)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                                    .padding(8.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(ans["answer_body"].toString())
                            }

                            Spacer(Modifier.height(16.dp))

                            val imgUrl = ans["imageUrl"]?.toString()
                            if (!imgUrl.isNullOrBlank()) {
                                Button(
                                    onClick = { viewingImage = imgUrl },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("View Answer Image")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
