package com.lingyou.voicenotes.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lingyou.voicenotes.features.notes.NoteDetailScreen
import com.lingyou.voicenotes.features.notes.NotesListScreen
import com.lingyou.voicenotes.features.record.RecordScreen
import com.lingyou.voicenotes.features.settings.SettingsScreen
import com.lingyou.voicenotes.features.shared.VoiceNotesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val deps = (application as VoiceNotesApp).deps
        setContent {
            VoiceNotesTheme {
                val nav = rememberNavController()
                var tab by rememberSaveable { mutableStateOf(0) }
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = tab == 0,
                                onClick = {
                                    tab = 0
                                    nav.navigate("record") {
                                        popUpTo(nav.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(Icons.Default.Mic, contentDescription = null) },
                                label = { Text("录音") },
                            )
                            NavigationBarItem(
                                selected = tab == 1,
                                onClick = {
                                    tab = 1
                                    nav.navigate("notes") {
                                        popUpTo(nav.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(Icons.Default.Notes, contentDescription = null) },
                                label = { Text("笔记") },
                            )
                            NavigationBarItem(
                                selected = tab == 2,
                                onClick = {
                                    tab = 2
                                    nav.navigate("settings") {
                                        popUpTo(nav.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                label = { Text("设置") },
                            )
                        }
                    },
                ) { padding ->
                    NavHost(
                        navController = nav,
                        startDestination = "record",
                        modifier = Modifier.padding(padding),
                    ) {
                        composable("record") {
                            RecordScreen(
                                deps = deps,
                                onOpenNote = { id ->
                                    tab = 1
                                    nav.navigate("notes") {
                                        launchSingleTop = true
                                    }
                                    nav.navigate("note/$id")
                                },
                            )
                        }
                        composable("notes") {
                            NotesListScreen(
                                deps = deps,
                                onOpen = { id -> nav.navigate("note/$id") },
                            )
                        }
                        composable("note/{id}") { entry ->
                            val id = entry.arguments?.getString("id") ?: return@composable
                            NoteDetailScreen(
                                noteId = id,
                                deps = deps,
                                onBack = { nav.popBackStack() },
                            )
                        }
                        composable("settings") {
                            SettingsScreen(deps = deps)
                        }
                    }
                }
            }
        }
    }
}
