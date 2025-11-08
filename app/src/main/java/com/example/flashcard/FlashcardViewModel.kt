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

// --- Composables ---
//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun FlashcardScreen(viewModel: FlashcardViewModel, onBack: () -> Unit) {
//    var currentView by remember { mutableStateOf("topics") }
//    var shuffleList by remember { mutableStateOf<List<Flashcard>>(emptyList()) }
//    var currentIndex by remember { mutableStateOf(0) }
//    var showAnswer by remember { mutableStateOf(false) }
//
//    Scaffold(
//        topBar = {
//            TopAppBar(
//                title = { Text(if (currentView == "topics") "Topics" else viewModel.currentTopic?.name ?: "") },
//                navigationIcon = {
//                    IconButton(onClick = {
//                        currentView = when (currentView) {
//                            "flashcards", "shuffle", "list" -> "topics"
//                            else -> { onBack(); "topics" }
//                        }
//                    }) {
//                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
//                    }
//                }
//            )
//        }
//    ) { padding ->
//        Box(Modifier.padding(padding)) {
//            when (currentView) {
//
//                "topics" -> TopicListScreen(
//                    viewModel = viewModel,
//                    onTopicSelected = {
//                        viewModel.loadFlashcards(it)
//                        currentView = "flashcards"
//                    },
//                    onAddTopic = { currentView = "addTopic" }
//                )
//
//                "addTopic" -> AddTopicScreen(
//                    onAdd = { name -> viewModel.addTopic(name) { currentView = "topics" } },
//                    onCancel = { currentView = "topics" }
//                )
//
//                "flashcards" -> Column(
//                    modifier = Modifier.fillMaxSize().padding(24.dp),
//                    horizontalAlignment = Alignment.CenterHorizontally
//                ) {
//                    Text("Flashcards in ${viewModel.currentTopic?.name}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
//                    Spacer(Modifier.height(16.dp))
//
//                    Button(onClick = {
//                        shuffleList = viewModel.flashcards.shuffled()
//                        currentIndex = 0
//                        showAnswer = false
//                        currentView = "shuffle"
//                    }, modifier = Modifier.fillMaxWidth()) {
//                        Text("Shuffle All")
//                    }
//
//                    Spacer(Modifier.height(8.dp))
//                    Button(onClick = { currentView = "list" }, modifier = Modifier.fillMaxWidth()) {
//                        Text("View List")
//                    }
//
//                    Spacer(Modifier.height(16.dp))
//                    Button(onClick = { currentView = "addFlashcard" }, modifier = Modifier.fillMaxWidth()) {
//                        Text("Add New Flashcard")
//                    }
//                }
//
//                "shuffle" -> {
//                    if (shuffleList.isNotEmpty() && currentIndex < shuffleList.size) {
//                        val card = shuffleList[currentIndex]
//                        Column(
//                            modifier = Modifier.fillMaxSize().padding(24.dp),
//                            horizontalAlignment = Alignment.CenterHorizontally,
//                            verticalArrangement = Arrangement.Center
//                        ) {
//                            Text(card.question, fontSize = 28.sp, fontWeight = FontWeight.Bold)
//                            Spacer(Modifier.height(16.dp))
//                            if (showAnswer) Text(card.answer, fontSize = 24.sp)
//                            Spacer(Modifier.height(16.dp))
//                            Row {
//                                if (!showAnswer) Button(onClick = { showAnswer = true }) { Text("Show Answer") }
//                                Spacer(Modifier.width(8.dp))
//                                Button(onClick = { currentView = "flashcards" }) { Text("End Session") }
//                            }
//                            Spacer(Modifier.height(16.dp))
//                            if (showAnswer) Button(onClick = { currentIndex++ ; showAnswer = false }) {
//                                Text("Next")
//                            }
//                        }
//                    } else {
//                        currentView = "flashcards"
//                    }
//                }
//
//                "list" -> LazyColumn(modifier = Modifier.fillMaxSize().padding(24.dp)) {
//                    items(viewModel.flashcards.size) { idx ->
//                        val card = viewModel.flashcards[idx]
//                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), elevation = CardDefaults.cardElevation(4.dp)) {
//                            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
//                                Column(modifier = Modifier.weight(1f)) {
//                                    Text("Q: ${card.question}", fontWeight = FontWeight.Bold)
//                                    Text("A: ${card.answer}")
//                                }
//                                Button(onClick = { viewModel.deleteFlashcard(card.id) }) { Text("Delete") }
//                            }
//                        }
//                    }
//                }
//
//                "addFlashcard" -> AddFlashcardScreen(
//                    viewModel = viewModel,
//                    onDone = { currentView = "flashcards" },
//                    onCancel = { currentView = "flashcards" }
//                )
//            }
//        }
//    }
//}

// --- Topic List & Add Topic Screens ---
@Composable
fun TopicListScreen(viewModel: FlashcardViewModel, onTopicSelected: (Topic) -> Unit, onAddTopic: () -> Unit) {
    LaunchedEffect(Unit) { viewModel.loadTopics() }
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Your Topics", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        if (viewModel.topics.isEmpty()) Text("No topics yet.") else viewModel.topics.forEach { topic ->
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

// --- Add Flashcard Screen ---
@Composable
fun AddFlashcardScreen(viewModel: FlashcardViewModel, onDone: () -> Unit, onCancel: () -> Unit) {
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
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
