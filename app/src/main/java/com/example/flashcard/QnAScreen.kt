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
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QnAScreen(parentNavController: NavHostController) {
    // Local NavController for internal QnA navigation
    val qnaNavController = rememberNavController()

    NavHost(
        navController = qnaNavController,
        startDestination = "qna_main"
    ) {
        // ----------------- QnA Main Page -----------------
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
                        IconButton(onClick = { parentNavController.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                )

                Spacer(Modifier.height(32.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = { qnaNavController.navigate("publish_question") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text("Publish Question")
                    }

                    Button(
                        onClick = { qnaNavController.navigate("answer_question_screen") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text("Find Questions")
                    }

                    Button(
                        onClick = { qnaNavController.navigate("published_questions") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text("Published Questions")
                    }

                    Button(
                        onClick = { qnaNavController.navigate("answered_questions") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text("Answered Questions")
                    }
                }
            }
        }

        // ----------------- Sub Pages -----------------
        composable("publish_question") {
            PublishQuestionScreen(onBack = { qnaNavController.popBackStack() })
        }

        composable("answer_question_screen") {
            AnswerQuestionScreen(
                navController = qnaNavController,
                onBack = { qnaNavController.popBackStack() }
            )
        }

        composable("submit_answer_screen/{questionId}") { backStackEntry ->
            val questionId = backStackEntry.arguments?.getString("questionId") ?: ""
            SubmitAnswerScreen(
                navController = qnaNavController,
                questionId = questionId,
                onBack = { qnaNavController.popBackStack() }
            )
        }

        composable("published_questions") {
            PublishedQuestionsScreen(onBack = { qnaNavController.popBackStack() })
        }

        composable("answered_questions") {
            AnsweredQuestionsScreen(
                onBack = { qnaNavController.popBackStack() },
                navigateToAnswers = { questionId ->
                    qnaNavController.navigate("answer_list/$questionId")
                }
            )
        }

        // <-- IMPORTANT: register this route so navigate("answer_list/$questionId") actually works -->
        composable(
            "answer_list/{questionId}",
            arguments = listOf(navArgument("questionId") { type = androidx.navigation.NavType.StringType })
        ) { backStackEntry ->
            val questionId = backStackEntry.arguments?.getString("questionId") ?: ""
            AnswerListScreen(
                questionId = questionId,
                navController = qnaNavController,             // ✅ FIXED
                onBack = { qnaNavController.popBackStack() }
            )
        }
        // --- SHOW OTHER ANSWERS FROM AnswerListScreen ---
        composable(
            "other_answers_screen/{questionId}",
            arguments = listOf(navArgument("questionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val questionId = backStackEntry.arguments?.getString("questionId") ?: ""
            OtherAnswersListScreen(
                questionId = questionId,
                onBack = { qnaNavController.popBackStack() }
            )
        }
        composable("my_answers_screen/{questionId}") { backStackEntry ->
            val qId = backStackEntry.arguments?.getString("questionId")!!
            MyAnswersScreen(
                questionId = qId,
                //navController = qnaNavController,
                onBack = { qnaNavController.popBackStack() }
            )
        }



    }
}
