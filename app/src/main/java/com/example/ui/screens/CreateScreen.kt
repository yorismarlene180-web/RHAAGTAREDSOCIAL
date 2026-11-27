package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.AudienceType
import com.example.model.PostType
import com.example.ui.theme.CloseFriendsGreen

enum class CreateFormat(val label: String) {
    POST("Publicación"),
    REEL("Reel (Video)"),
    STORY("Historia (24h)")
}

@Composable
fun CreateScreen(
    initialFormat: CreateFormat = CreateFormat.POST,
    isBusy: Boolean,
    onPublishImagePost: (Uri, String, AudienceType) -> Unit,
    onPublishVideoPost: (Uri, String, PostType, AudienceType) -> Unit,
    onPublishStory: (Uri, String, AudienceType) -> Unit
) {
    val context = LocalContext.current
    var format by remember(initialFormat) { mutableStateOf(initialFormat) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var isVideoUri by remember { mutableStateOf(false) }
    var caption by remember { mutableStateOf("") }
    var audience by remember { mutableStateOf(AudienceType.PUBLIC) }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            val mime = context.contentResolver.getType(uri).orEmpty()
            isVideoUri = mime.startsWith("video/")
            if (isVideoUri && format == CreateFormat.STORY) {
                format = CreateFormat.REEL
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Crear nuevo contenido",
            style = MaterialTheme.typography.headlineMedium
        )

        // Format Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CreateFormat.entries.forEach { item ->
                FilterChip(
                    selected = format == item,
                    onClick = {
                        format = item
                        if (item == CreateFormat.REEL && !isVideoUri) {
                            selectedUri = null
                        } else if (item == CreateFormat.STORY && isVideoUri) {
                            selectedUri = null
                            isVideoUri = false
                        }
                    },
                    label = { Text(item.label) },
                    modifier = Modifier.testTag("format_chip_${item.name}")
                )
            }
        }

        // Media Preview / Picker Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(18.dp)
                )
                .clickable {
                    val req = when (format) {
                        CreateFormat.REEL -> PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        CreateFormat.STORY -> PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        CreateFormat.POST -> PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    }
                    mediaPicker.launch(req)
                }
                .testTag("create_media_picker"),
            contentAlignment = Alignment.Center
        ) {
            if (selectedUri != null) {
                if (isVideoUri) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Video seleccionado",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Video listo para subir en línea",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Máximo 6 MB para reproducción rápida en la comunidad. Toca para cambiar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    AsyncImage(
                        model = selectedUri,
                        contentDescription = "Vista previa seleccionada",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = if (format == CreateFormat.REEL) Icons.Default.Movie else Icons.Default.AddPhotoAlternate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = when (format) {
                            CreateFormat.REEL -> "Toca para elegir un video de tu galería"
                            CreateFormat.STORY -> "Toca para elegir una foto para tu historia"
                            CreateFormat.POST -> "Toca para elegir una foto o video"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        if (selectedUri != null) {
            OutlinedButton(
                onClick = {
                    val req = when (format) {
                        CreateFormat.REEL -> PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        CreateFormat.STORY -> PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        CreateFormat.POST -> PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    }
                    mediaPicker.launch(req)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cambiar archivo")
            }
        }

        OutlinedTextField(
            value = caption,
            onValueChange = { if (it.length <= 500) caption = it },
            label = { Text(if (format == CreateFormat.STORY) "Texto sobre la historia (opcional)" else "Descripción") },
            placeholder = { Text("Escribe algo sobre este momento...") },
            maxLines = 4,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("create_caption_input")
        )

        // Audience Selector
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "¿Quién puede verlo?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = audience == AudienceType.PUBLIC,
                    onClick = { audience = AudienceType.PUBLIC },
                    label = { Text("Público") },
                    leadingIcon = {
                        Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
                FilterChip(
                    selected = audience == AudienceType.CLOSE_FRIENDS,
                    onClick = { audience = AudienceType.CLOSE_FRIENDS },
                    label = { Text("Mejores amigos") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = CloseFriendsGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }

        Button(
            onClick = {
                val uri = selectedUri ?: return@Button
                when (format) {
                    CreateFormat.STORY -> onPublishStory(uri, caption, audience)
                    CreateFormat.REEL -> onPublishVideoPost(uri, caption, PostType.REEL, audience)
                    CreateFormat.POST -> {
                        if (isVideoUri) {
                            onPublishVideoPost(uri, caption, PostType.POST, audience)
                        } else {
                            onPublishImagePost(uri, caption, audience)
                        }
                    }
                }
            },
            enabled = selectedUri != null && !isBusy,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("publish_button")
        ) {
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Subiendo en línea...")
            } else {
                Text("Publicar", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
