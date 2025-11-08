package com.example.flashcard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.random.Random

// --- FlashcardScreen.kt ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardScreen(viewModel: FlashcardViewModel, onBack: () -> Unit) {
    var currentView by remember { mutableStateOf("topics") }
    var topicViewMode by remember { mutableStateOf("options") } // "options", "shuffle", "list"
    var shuffledFlashcards by remember { mutableStateOf<List<Flashcard>>(emptyList()) }
    var currentShuffleIndex by remember { mutableStateOf(0) }
    var showAnswer by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
//                    Text(
//                        when (currentView) {
//                            "topics" -> "Topics"
//                            "topicOptions" -> viewModel.currentTopic?.name ?: ""
//                            "shuffle" -> viewModel.currentTopic?.name ?: ""
//                            "list" -> viewModel.currentTopic?.name ?: ""
//                            else -> ""
//                        }
//                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when (currentView) {
                            "topics" -> onBack()
                            "topicOptions" -> currentView = "topics"
                            "shuffle", "list" -> {
                                currentView = "topicOptions"
                                topicViewMode = "options"
                            }
                        }
                    }) {
                        Text("Back")
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
                        currentView = "topicOptions"
                    },
                    onAddTopic = { currentView = "addTopic" }
                )

                "addTopic" -> AddTopicScreen(
                    onAdd = { name -> viewModel.addTopic(name) { currentView = "topics" } },
                    onCancel = { currentView = "topics" }
                )

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

                "list" -> FlashcardListWithDeleteScreen(
                    viewModel = viewModel,
                    onBack = { currentView = "topicOptions" },
                    onAddFlashcard = { currentView = "addFlashcard" }
                )

                "addFlashcard" -> AddFlashcardScreen(
                    viewModel = viewModel,
                    onDone = { currentView = "list" },
                    onCancel = { currentView = "list" }
                )
            }
        }
    }
}

// --- Topic Options Screen ---
@Composable
fun TopicOptionsScreen(
    onSelectShuffle: () -> Unit,
    onSelectList: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(onClick = onSelectShuffle, modifier = Modifier.fillMaxWidth()) { Text("Shuffle All") }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onSelectList, modifier = Modifier.fillMaxWidth()) { Text("List View") }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to Topics") }
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
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Q: ${flashcard.question}", style = MaterialTheme.typography.headlineSmall)
        if (showAnswer) {
            Spacer(Modifier.height(16.dp))
            Text("A: ${flashcard.answer}", style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onShowAnswer, modifier = Modifier.fillMaxWidth()) { Text("Show Answer") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Next") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onEndSession, modifier = Modifier.fillMaxWidth()) { Text("End Session") }
    }
}

// --- List View Screen with Delete ---
@Composable
fun FlashcardListWithDeleteScreen(
    viewModel: FlashcardViewModel,
    onBack: () -> Unit,
    onAddFlashcard: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Flashcards in ${viewModel.currentTopic?.name}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(viewModel.flashcards.size) { index ->
                val card = viewModel.flashcards[index]
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

// --- Add Flashcard Screen (same as before) ---
//@Composable
//fun AddFlashcardScreen(viewModel: FlashcardViewModel, onDone: () -> Unit, onCancel: () -> Unit) {
//    var question by remember { mutableStateOf("") }
//    var answer by remember { mutableStateOf("") }
//
//    Column(
//        modifier = Modifier.fillMaxSize().padding(24.dp),
//        horizontalAlignment = Alignment.CenterHorizontally,
//        verticalArrangement = Arrangement.Center
//    ) {
//        Text("Add Flashcard", fontSize = 26.sp, fontWeight = FontWeight.Bold)
//        Spacer(Modifier.height(16.dp))
//        OutlinedTextField(value = question, onValueChange = { question = it }, label = { Text("Question") }, modifier = Modifier.fillMaxWidth())
//        Spacer(Modifier.height(8.dp))
//        OutlinedTextField(value = answer, onValueChange = { answer = it }, label = { Text("Answer") }, modifier = Modifier.fillMaxWidth())
//        Spacer(Modifier.height(16.dp))
//        Button(
//            onClick = { viewModel.addFlashcard(question, answer) { onDone() } },
//            enabled = question.isNotBlank() && answer.isNotBlank(),
//            modifier = Modifier.fillMaxWidth()
//        ) { Text("Save Flashcard") }
//        Spacer(Modifier.height(8.dp))
//        Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
//    }
//}
