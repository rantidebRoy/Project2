package com.example.flashcard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

// --- Data Models ---
data class Topic(
    val id: String = "",
    val name: String = ""
)

data class Flashcard(
    val id: String = "",
    val question: String = "",
    val answer: String = ""
)

// --- ViewModel ---
class FlashcardViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    var topics by mutableStateOf<List<Topic>>(emptyList())
        private set

    var flashcards by mutableStateOf<List<Flashcard>>(emptyList())
        private set

    var currentTopic by mutableStateOf<Topic?>(null)
        private set

    fun loadTopics() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).collection("topics")
            .get()
            .addOnSuccessListener { result ->
                topics = result.documents.map { doc ->
                    Topic(doc.id, doc.getString("name") ?: "")
                }
            }
    }

    fun addTopic(name: String, onDone: () -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        val topic = hashMapOf("name" to name)
        db.collection("users").document(uid).collection("topics")
            .add(topic)
            .addOnSuccessListener {
                loadTopics()
                onDone()
            }
    }

    fun loadFlashcards(topic: Topic) {
        val uid = auth.currentUser?.uid ?: return
        currentTopic = topic
        db.collection("users").document(uid)
            .collection("topics").document(topic.id)
            .collection("flashcards")
            .get()
            .addOnSuccessListener { result ->
                flashcards = result.documents.map { doc ->
                    Flashcard(
                        id = doc.id,
                        question = doc.getString("question") ?: "",
                        answer = doc.getString("answer") ?: ""
                    )
                }
            }
    }

    fun addFlashcard(question: String, answer: String, onDone: () -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        val topic = currentTopic ?: return
        val flashcard = hashMapOf("question" to question, "answer" to answer)
        db.collection("users").document(uid)
            .collection("topics").document(topic.id)
            .collection("flashcards")
            .add(flashcard)
            .addOnSuccessListener {
                loadFlashcards(topic)
                onDone()
            }
    }
}

// --- Screens ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardScreen(viewModel: FlashcardViewModel, onBack: () -> Unit) {
    var currentView by remember { mutableStateOf("topics") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (currentView == "topics") "Topics" else viewModel.currentTopic?.name ?: "") },
                navigationIcon = {
                    if (currentView != "topics") {
                        IconButton(onClick = { currentView = "topics" }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (currentView) {
                "topics" -> TopicListScreen(
                    viewModel = viewModel,
                    onTopicSelected = {
                        viewModel.loadFlashcards(it)
                        currentView = "flashcards"
                    },
                    onAddTopic = {
                        currentView = "addTopic"
                    }
                )

                "addTopic" -> AddTopicScreen(
                    onAdd = { name ->
                        viewModel.addTopic(name) {
                            currentView = "topics"
                        }
                    },
                    onCancel = { currentView = "topics" }
                )

                "flashcards" -> FlashcardListScreen(
                    viewModel = viewModel,
                    onAddFlashcard = { currentView = "addFlashcard" }
                )

                "addFlashcard" -> AddFlashcardScreen(
                    viewModel = viewModel,
                    onDone = { currentView = "flashcards" },
                    onCancel = { currentView = "flashcards" }
                )
            }
        }
    }
}

// --- Topic List Screen ---
@Composable
fun TopicListScreen(
    viewModel: FlashcardViewModel,
    onTopicSelected: (Topic) -> Unit,
    onAddTopic: () -> Unit
) {
    LaunchedEffect(Unit) {
        viewModel.loadTopics()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Your Topics", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        if (viewModel.topics.isEmpty()) {
            Text("No topics yet.")
            Spacer(Modifier.height(8.dp))
        }

        for (topic in viewModel.topics) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onTopicSelected(topic) },
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Box(Modifier.padding(16.dp)) {
                    Text(topic.name, fontSize = 20.sp)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(onClick = onAddTopic, modifier = Modifier.fillMaxWidth()) {
            Text("Add New Topic")
        }
    }
}

// --- Add Topic Screen ---
@Composable
fun AddTopicScreen(onAdd: (String) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Add New Topic", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Topic Name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onAdd(name) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text("Save Topic")
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}

// --- Flashcard List for a Topic ---
@Composable
fun FlashcardListScreen(viewModel: FlashcardViewModel, onAddFlashcard: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Flashcards in ${viewModel.currentTopic?.name}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        if (viewModel.flashcards.isEmpty()) {
            Text("No flashcards yet.")
        } else {
            for (card in viewModel.flashcards) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Q: ${card.question}", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("A: ${card.answer}")
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(onClick = onAddFlashcard, modifier = Modifier.fillMaxWidth()) {
            Text("Add New Flashcard")
        }
    }
}

// --- Add Flashcard Screen ---
@Composable
fun AddFlashcardScreen(viewModel: FlashcardViewModel, onDone: () -> Unit, onCancel: () -> Unit) {
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Add Flashcard", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            label = { Text("Question") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = answer,
            onValueChange = { answer = it },
            label = { Text("Answer") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                viewModel.addFlashcard(question, answer) { onDone() }
            },
            enabled = question.isNotBlank() && answer.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Flashcard")
        }

        Spacer(Modifier.height(8.dp))
        Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}
