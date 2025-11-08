package com.example.flashcard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun TopicDetailScreen(navController: NavController, topic: String) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(topic, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { navController.navigate("shuffle/$topic") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Shuffle All")
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { navController.navigate("listView/$topic") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("List View")
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { navController.navigate("addFlashcard/$topic") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add Flashcard")
        }
    }
}

