package com.alexis.hifiplayer.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alexis.hifiplayer.ui.icons.AppIcons
import com.alexis.hifiplayer.ui.theme.HiFiPlayerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onOpenLibrary: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "HiFiPlayer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors()
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { }) { Icon(painter = painterResource(AppIcons.PlayArrow), contentDescription = "Reproducir") }
        }
    ) { innerPadding ->
        LazyColumn(modifier = modifier.fillMaxSize().padding(innerPadding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text(text = "Reproductor Hi-Fi para reproducción lossless", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                Card(modifier = Modifier.fillMaxWidth().clickable { onOpenLibrary() }) {
                    ListItem(headlineContent = { Text("Biblioteca", style = MaterialTheme.typography.titleMedium) }, supportingContent = { Text("Explora tu música local") }, leadingContent = { Icon(painter = painterResource(AppIcons.MenuBook), contentDescription = "Biblioteca", modifier = Modifier.size(24.dp)) })
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    ListItem(headlineContent = { Text("Salida de audio", style = MaterialTheme.typography.titleMedium) }, supportingContent = { Text("Salida por defecto del sistema") }, leadingContent = { Icon(painter = painterResource(AppIcons.Speaker), contentDescription = "Salida de audio", modifier = Modifier.size(24.dp)) })
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "Estado de reproducción", style = MaterialTheme.typography.titleMedium)
                        Text(text = "No hay reproducción actualmente", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(painter = painterResource(AppIcons.PlayArrow), contentDescription = "Sin reproducción", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = "Listo para reproducir", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }
}@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun HomeScreenPreview() {
    HiFiPlayerTheme { HomeScreen() }
}

@Preview(showBackground = true, showSystemUi = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeScreenPreviewDark() {
    HiFiPlayerTheme(darkTheme = true) { HomeScreen() }
}
