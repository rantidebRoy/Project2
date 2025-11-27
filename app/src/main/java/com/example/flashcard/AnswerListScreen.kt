package com.example.flashcard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnswerListScreen(
    questionId: String,
    navController: NavController,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Question Details") },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {

            // 🔥 Show two main actions exactly like AnswerQuestionScreen
            Button(
                onClick = { navController.navigate("submit_answer_screen/$questionId") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Answer the Question")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { navController.navigate("other_answers_screen/$questionId") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Other Answers")
            }


            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Your Answered Questions",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            // If you want to show a list of your answers, place it here
            // (this part depends on how you load your data)
        }
    }
}
