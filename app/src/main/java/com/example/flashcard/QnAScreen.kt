package com.example.flashcard

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QnAScreen(navController: NavHostController) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "qna_main") {

        composable("qna_main") {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TopAppBar(
                    title = { Text("QnA") },
                    navigationIcon = {
                        IconButton(onClick = { /* Handle back from main app */ }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )

                Spacer(Modifier.height(32.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = { navController.navigate("publish_question") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text("Publish Question")
                    }

                    Button(
                        onClick = { navController.navigate("answer_question_screen") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Text("Answer Question")
                    }


                    Button(
                        onClick = { navController.navigate("published_questions") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text("Published Questions")
                    }


                    Button(
                        onClick = { navController.navigate("answered_questions") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text("Answered Questions")
                    }

                }
            }
        }

        composable("publish_question") {
            PublishQuestionScreen(onBack = { navController.popBackStack() })
        }
        composable("answer_question_screen") {
            AnswerQuestionScreen(navController = navController, onBack = { navController.popBackStack() })
        }

        composable("submit_answer_screen/{questionId}") { backStackEntry ->
            val questionId = backStackEntry.arguments?.getString("questionId") ?: ""
            SubmitAnswerScreen(
                navController = navController,
                questionId = questionId,
                onBack = { navController.popBackStack() }
            )
        }
        composable("published_questions") {
            PublishedQuestionsScreen(onBack = { navController.popBackStack() })
        }
        composable("answered_questions") {
            AnsweredQuestionsScreen(onBack = { navController.popBackStack() })
        }





    }
}
