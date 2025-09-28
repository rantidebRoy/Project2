package com.example.flashcard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.tooling.preview.Preview
import com.example.flashcard.ui.theme.FlashcardTheme
import kotlin.random.Random

// 1. Data Class for a Flashcard
// This data class will represent a single flashcard.
data class Flashcard(val question: String, val answer: String)

// 2. Class to manage flashcard data and review sessions.
// This is the core logic, adapted for use with Compose.
class FlashcardViewModel : ViewModel() {

    // A list to store our flashcards.
    private val _flashcards = mutableStateOf(getSampleFlashcards().toMutableList())
    var flashcards: List<Flashcard> by mutableStateOf(emptyList())

    // State to hold our current list of cards and the current card index.
    var currentCardIndex by mutableStateOf(0)

    // State to toggle the visibility of the answer.
    var isAnswerVisible by mutableStateOf(false)

    // State to track if we are adding a new card
    var isAddingNewCard by mutableStateOf(false)

    init {
        flashcards = _flashcards.value.shuffled(Random)
    }

    // --- FIX: Computed property to resolve 'currentCard' reference in MainActivity.kt ---
    val currentCard: Flashcard?
        get() = flashcards.getOrNull(currentCardIndex)
    // ---------------------------------------------------------------------------------

    // Adds a new flashcard to the list.
    fun addFlashcard(question: String, answer: String) {
        val newFlashcard = Flashcard(question, answer)
        _flashcards.value.add(newFlashcard)
        flashcards = _flashcards.value.shuffled(Random)
        currentCardIndex = 0 // Restart session with the new card
    }

    // Returns a shuffled list of all flashcards for a review session.
    fun getShuffledFlashcards(): List<Flashcard> {
        return flashcards.shuffled(Random)
    }

    fun toggleAnswerVisibility() {
        isAnswerVisible = !isAnswerVisible
    }

    fun nextCard() {
        isAnswerVisible = false // Hide answer for the new card
        currentCardIndex = (currentCardIndex + 1) % flashcards.size // Cycle through cards
    }

    // Toggles the view between flashcard review and adding a new card.
    fun toggleAddCardView() {
        isAddingNewCard = !isAddingNewCard
    }

    private fun getSampleFlashcards(): List<Flashcard> {
        return listOf(
            Flashcard("What is the capital of France?", "Paris"),
            Flashcard("What is the main function of the heart?", "To pump blood throughout the body."),
            Flashcard("What does 'val' mean in Kotlin?", "It declares a read-only (immutable) variable."),
            Flashcard("What is the largest planet in our solar system?", "Jupiter")
        )
    }
}

// 4. Composable function to display the flashcard UI.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardScreen(viewModel: FlashcardViewModel, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Flashcards") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            if (viewModel.isAddingNewCard) {
                SetFlashcardScreen(viewModel)
            } else {
                if (viewModel.flashcards.isEmpty()) {
                    Text("No flashcards to review. Please add some.")
                } else {
                    FlashcardReviewScreen(viewModel)
                }
            }
        }
    }
}

// Composable for the screen where the user reviews flashcards.
@Composable
fun FlashcardReviewScreen(viewModel: FlashcardViewModel) {
    // We now use the computed property 'currentCard'
    val currentCard = viewModel.currentCard ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = currentCard.question,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (viewModel.isAnswerVisible) {
            Text(
                text = currentCard.answer,
                fontSize = 20.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        } else {
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = { viewModel.toggleAnswerVisibility() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (viewModel.isAnswerVisible) "Hide Answer" else "Show Answer")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            // Check if flashcards is not empty before cycling
            onClick = {
                if (viewModel.flashcards.isNotEmpty()) {
                    viewModel.nextCard()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Next Card")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { viewModel.toggleAddCardView() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add New Card")
        }
    }
}

// Composable for the screen where the user adds a new flashcard.
@Composable
fun SetFlashcardScreen(viewModel: FlashcardViewModel) {
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Add New Flashcard", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            label = { Text("Question") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = answer,
            onValueChange = { answer = it },
            label = { Text("Answer") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                viewModel.addFlashcard(question, answer)
                question = ""
                answer = ""
                viewModel.toggleAddCardView()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = question.isNotBlank() && answer.isNotBlank()
        ) {
            Text("Save Card")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { viewModel.toggleAddCardView() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel")
        }
    }
}

@Preview(showBackground = true, name = "Flashcard Review Screen Preview")
@Composable
fun FlashcardReviewScreenPreview() {
    FlashcardTheme {
        val viewModel = remember { FlashcardViewModel() }
        FlashcardReviewScreen(viewModel = viewModel)
    }
}

@Preview(showBackground = true, name = "Set Flashcard Screen Preview")
@Composable
fun SetFlashcardScreenPreview() {
    FlashcardTheme {
        val viewModel = remember { FlashcardViewModel() }
        SetFlashcardScreen(viewModel = viewModel)
    }
}
