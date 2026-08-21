package it.faccioio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FaccioIoApp()
                }
            }
        }
    }
}

data class TaskItem(
    val title: String,
    val completed: Boolean = false,
    val reminderTime: Long? = null
)

@Composable
fun FaccioIoApp() {
    var newTask by remember { mutableStateOf("") }

    val tasks = remember {
        mutableStateListOf(
            TaskItem("Controllare gli impegni di oggi"),
            TaskItem("Fare una pausa di 10 minuti"),
            TaskItem("Preparare le cose per domani")
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text(
            text = "Faccio io",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Un passo alla volta.",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = newTask,
            onValueChange = { newTask = it },
            label = { Text("Cosa devi fare?") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                val text = newTask.trim()

                if (text.isNotEmpty()) {
                    tasks.add(TaskItem(text))
                    newTask = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Aggiungi attività")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Le tue attività",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tasks) { task ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = task.completed,
                        onCheckedChange = { checked ->
                            val index = tasks.indexOf(task)

                            if (index >= 0) {
                                tasks[index] =
                                    task.copy(completed = checked)
                            }
                        }
                    )

                    Text(
                        text = task.title,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}
