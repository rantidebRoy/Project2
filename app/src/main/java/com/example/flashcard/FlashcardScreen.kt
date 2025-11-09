package com.example.flashcard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.random.Random

// --- Data Models ---
data class Topic(
    val id: String = "",
    val name: String = ""
)

//data class Flashcard(
//    val id: String = "",
//    val question: String = "",
//    val answer: String = ""
//)

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

    fun deleteFlashcard(flashcardId: String) {
        val uid = auth.currentUser?.uid ?: return
        val topic = currentTopic ?: return
        db.collection("users").document(uid)
            .collection("topics").document(topic.id)
            .collection("flashcards")
            .document(flashcardId)
            .delete()
            .addOnSuccessListener {
                loadFlashcards(topic)
            }
    }
}

// --- FlashcardScreen Composable ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardScreen(viewModel: FlashcardViewModel, onBack: () -> Unit) {
    var currentView by remember { mutableStateOf("topics") }
    var topicViewMode by remember { mutableStateOf("options") }
    var shuffledFlashcards by remember { mutableStateOf<List<Flashcard>>(emptyList()) }
    var currentShuffleIndex by remember { mutableStateOf(0) }
    var showAnswer by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (currentView) {
                            "topics" -> "Topics"
                            "addTopic" -> "Add Topic"
                            "topicOptions" -> viewModel.currentTopic?.name ?: ""
                            "shuffle" -> viewModel.currentTopic?.name ?: ""
                            "list" -> viewModel.currentTopic?.name ?: ""
                            "addFlashcard" -> "Add Flashcard"
                            else -> ""
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when (currentView) {
                            "topics" -> onBack()
                            "addTopic" -> currentView = "topics"
                            "topicOptions" -> currentView = "topics"
                            "shuffle", "list" -> currentView = "topicOptions"
                            "addFlashcard" -> currentView = "list"
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (currentView) {
                // --- Topic List ---
                "topics" -> TopicListScreen(
                    viewModel = viewModel,
                    onTopicSelected = {
                        viewModel.loadFlashcards(it)
                        currentView = "topicOptions"
                    },
                    onAddTopic = { currentView = "addTopic" }
                )

                // --- Add Topic ---
                "addTopic" -> AddTopicScreen(
                    onAdd = { name -> viewModel.addTopic(name) { currentView = "topics" } },
                    onCancel = { currentView = "topics" }
                )

                // --- Topic Options ---
                "topicOptions" -> TopicOptionsScreen(
                    onSelectShuffle = {
                        shuffledFlashcards = viewModel.flashcards.shuffled(Random(System.currentTimeMillis()))
                        currentShuffleIndex = 0
                        showAnswer = false
                        currentView = "shuffle"
                    },
                    onSelectList = { currentView = "list" },
                    onBack = { currentView = "topics" }
                )

                // --- Shuffle Flashcards ---
                "shuffle" -> {
                    if (shuffledFlashcards.isEmpty()) {
                        Text("No flashcards in this topic.", Modifier.padding(24.dp))
                    } else {
                        val currentCard = shuffledFlashcards.getOrNull(currentShuffleIndex)
                        currentCard?.let { card ->
                            ShuffleFlashcardScreen(
                                flashcard = card,
                                showAnswer = showAnswer,
                                onShowAnswer = { showAnswer = true },
                                onNext = {
                                    currentShuffleIndex++
                                    showAnswer = false
                                    if (currentShuffleIndex >= shuffledFlashcards.size) {
                                        currentView = "topicOptions"
                                    }
                                },
                                onEndSession = { currentView = "topicOptions" }
                            )
                        }
                    }
                }

                // --- List View ---
                "list" -> FlashcardListWithDeleteScreen(
                    viewModel = viewModel,
                    onBack = { currentView = "topicOptions" },
                    onAddFlashcard = { currentView = "addFlashcard" }
                )

                // --- Add Flashcard ---
                "addFlashcard" -> AddFlashcardScreen(
                    viewModel = viewModel,
                    onDone = { currentView = "list" },
                    onCancel = { currentView = "list" }
                )
            }
        }
    }
}

// --- Topic List & Add Topic Screens ---
@Composable
fun TopicListScreen(viewModel: FlashcardViewModel, onTopicSelected: (Topic) -> Unit, onAddTopic: () -> Unit) {
    LaunchedEffect(Unit) { viewModel.loadTopics() }
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Your Topics", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        if (viewModel.topics.isEmpty()) Text("No topics yet.")
        else viewModel.topics.forEach { topic ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onTopicSelected(topic) },
                elevation = CardDefaults.cardElevation(4.dp)) {
                Box(Modifier.padding(16.dp)) { Text(topic.name, fontSize = 20.sp) }
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAddTopic, modifier = Modifier.fillMaxWidth()) { Text("Add New Topic") }
    }
}

@Composable
fun AddTopicScreen(onAdd: (String) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Add New Topic", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Topic Name") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onAdd(name) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Save Topic") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

// --- Topic Options ---
@Composable
fun TopicOptionsScreen(onSelectShuffle: () -> Unit, onSelectList: () -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Button(onClick = onSelectShuffle, modifier = Modifier.fillMaxWidth()) { Text("Shuffle All") }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onSelectList, modifier = Modifier.fillMaxWidth()) { Text("List View") }
        Spacer(Modifier.height(12.dp))
        //Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to Topics") }
    }
}

// --- Shuffle Flashcard Screen ---
@Composable
fun ShuffleFlashcardScreen(
    flashcard: Flashcard,
    showAnswer: Boolean,
    onShowAnswer: () -> Unit,
    onNext: () -> Unit,
    onEndSession: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text("Q: ${flashcard.question}", style = MaterialTheme.typography.headlineSmall)
        if (showAnswer) {
            Spacer(Modifier.height(16.dp))
            Text("A: ${flashcard.answer}", style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(24.dp))
        if (!showAnswer) Button(onClick = onShowAnswer, modifier = Modifier.fillMaxWidth()) { Text("Show Answer") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Next") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onEndSession, modifier = Modifier.fillMaxWidth()) { Text("End Session") }
    }
}

// --- List View with Delete ---
@Composable
fun FlashcardListWithDeleteScreen(viewModel: FlashcardViewModel, onBack: () -> Unit, onAddFlashcard: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Flashcards in ${viewModel.currentTopic?.name}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(viewModel.flashcards.size) { index ->
                val card = viewModel.flashcards[index]
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Q: ${card.question}", fontWeight = FontWeight.Bold)
                            Text("A: ${card.answer}")
                        }
                        Button(onClick = { viewModel.deleteFlashcard(card.id) }) { Text("Delete") }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = onAddFlashcard, modifier = Modifier.fillMaxWidth()) { Text("Add New Flashcard") }
        Spacer(Modifier.height(8.dp))
       // Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

// --- Add Flashcard Screen ---
@Composable
fun AddFlashcardScreen(viewModel: FlashcardViewModel, onDone: () -> Unit, onCancel: () -> Unit) {
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text("Add Flashcard", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = question, onValueChange = { question = it }, label = { Text("Question") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = answer, onValueChange = { answer = it }, label = { Text("Answer") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Button(onClick = { viewModel.addFlashcard(question, answer) { onDone() } }, enabled = question.isNotBlank() && answer.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Save Flashcard") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}
