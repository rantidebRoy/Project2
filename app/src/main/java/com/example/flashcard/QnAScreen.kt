package com.example.flashcard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QnAScreen(parentNavController: NavHostController) {
    val qnaNavController = rememberNavController()

    // No Scaffold, no TopAppBar here
    NavHost(
        navController = qnaNavController,
        startDestination = "qna_main",
        modifier = Modifier
            .fillMaxSize()
    ) {
        composable("qna_main") {
            // Your main screen SHOULD include its own TopAppBar
            QnAMainScreen(
                onBack = { parentNavController.popBackStack() },
                onPublishQuestion = { qnaNavController.navigate("publish_question") },
                onFindQuestions = { qnaNavController.navigate("answer_question_screen") },
                onPublishedQuestions = { qnaNavController.navigate("published_questions") },
                onAnsweredQuestions = { qnaNavController.navigate("answered_questions") }
            )
        }

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
            PublishedQuestionsScreen(
                onBack = { qnaNavController.popBackStack() },
                onOpenAnswers = { questionId ->
                    qnaNavController.navigate("other_answers_screen/$questionId")
                }
            )
        }

        composable("answered_questions") {
            AnsweredQuestionsScreen(
                onBack = { qnaNavController.popBackStack() },
                navigateToAnswers = { questionId ->
                    qnaNavController.navigate("answer_list/$questionId")
                }
            )
        }

        composable(
            "answer_list/{questionId}",
            arguments = listOf(navArgument("questionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val questionId = backStackEntry.arguments?.getString("questionId") ?: ""
            AnswerListScreen(
                questionId = questionId,
                navController = qnaNavController,
                onBack = { qnaNavController.popBackStack() }
            )
        }

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
            val qId = backStackEntry.arguments?.getString("questionId") ?: ""
            MyAnswersScreen(
                questionId = qId,
                onBack = { qnaNavController.popBackStack() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QnAMainScreen(
    onBack: () -> Unit,
    onPublishQuestion: () -> Unit,
    onFindQuestions: () -> Unit,
    onPublishedQuestions: () -> Unit,
    onAnsweredQuestions: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QnA") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = onPublishQuestion,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) { Text("Publish Question") }

                Button(
                    onClick = onFindQuestions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) { Text("Find Questions") }

                Button(
                    onClick = onPublishedQuestions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) { Text("Published Questions") }

                Button(
                    onClick = onAnsweredQuestions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) { Text("Answered Questions") }
            }
        }
    }
}
