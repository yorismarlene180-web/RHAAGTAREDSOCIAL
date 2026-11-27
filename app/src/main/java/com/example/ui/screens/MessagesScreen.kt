package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.ChatMessage
import com.example.model.ChatThread
import com.example.model.Note
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.ui.components.UserAvatar
import com.example.util.MediaUtils
import kotlinx.coroutines.flow.Flow

@Composable
fun MessagesScreen(
    myProfile: UserProfile,
    usersState: UiState<List<UserProfile>>,
    notesState: UiState<List<Note>>,
    chatsState: UiState<List<ChatThread>>,
    chatIdFor: (String) -> String,
    observeMessages: (String) -> Flow<List<ChatMessage>>,
    onSendMessage: (otherUserId: String, text: String) -> Unit,
    onUpsertNote: (String, onDone: () -> Unit) -> Unit,
    onDeleteNote: (onDone: () -> Unit) -> Unit,
    onBack: () -> Unit
) {
    var activePartner by remember { mutableStateOf<UserProfile?>(null) }
    var showNoteDialog by remember { mutableStateOf(false) }

    val users = (usersState as? UiState.Success)?.data ?: emptyList()
    val usersById = remember(users) { users.associateBy { it.userId } }
    val notes = (notesState as? UiState.Success)?.data ?: emptyList()
    val myNote = remember(notes, myProfile.userId) { notes.firstOrNull { it.userId == myProfile.userId } }
    val otherNotes = remember(notes, myProfile.userId) { notes.filter { it.userId != myProfile.userId } }
    val chats = (chatsState as? UiState.Success)?.data ?: emptyList()

    if (activePartner != null) {
        BackHandler { activePartner = null }
        val partner = activePartner!!
        val chatId = chatIdFor(partner.userId)
        ChatDetailScreen(
            myProfile = myProfile,
            partner = partner,
            messagesFlow = observeMessages(chatId),
            onSendMessage = { text -> onSendMessage(partner.userId, text) },
            onBack = { activePartner = null }
        )
        return
    }

    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Text(
                text = myProfile.username,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showNoteDialog = true }) {
                Icon(Icons.Default.EditNote, contentDescription = "Escribir nota")
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            // Notes Tray ("Anota lo que estás pensando")
            item(key = "notes_tray") {
                Column(modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)) {
                    Text(
                        text = "Notas — Anota lo que estás pensando",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item(key = "my_note") {
                            NoteBubbleItem(
                                avatarBase64 = myProfile.avatarBase64,
                                displayName = myProfile.displayName,
                                username = "Tu nota",
                                noteText = myNote?.text ?: "¿Qué piensas?",
                                isPlaceholder = myNote == null,
                                onClick = { showNoteDialog = true }
                            )
                        }
                        items(otherNotes, key = { it.userId }) { note ->
                            NoteBubbleItem(
                                avatarBase64 = note.avatarBase64,
                                displayName = note.username,
                                username = note.username,
                                noteText = note.text,
                                isPlaceholder = false,
                                onClick = {
                                    usersById[note.userId]?.let { activePartner = it }
                                }
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            }

            // Active Conversations
            item(key = "chats_header") {
                Text(
                    text = "Mensajes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            if (chats.isEmpty()) {
                item(key = "no_chats") {
                    Text(
                        text = "Aún no tienes conversaciones. Elige a una persona real abajo para iniciar un chat.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            } else {
                items(chats, key = { it.id }) { thread ->
                    val otherId = thread.participantIds.firstOrNull { it != myProfile.userId } ?: return@items
                    val partner = usersById[otherId] ?: UserProfile(
                        userId = otherId,
                        username = "usuario",
                        displayName = "Usuario"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { activePartner = partner }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatar(
                            avatarBase64 = partner.avatarBase64,
                            displayName = partner.displayName,
                            size = 50.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = partner.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = thread.lastMessageText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = MediaUtils.formatRelativeTime(thread.updatedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Directory to start a new chat with real users
            val otherUsers = users.filter { it.userId != myProfile.userId }
            if (otherUsers.isNotEmpty()) {
                item(key = "start_chat_header") {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    )
                    Text(
                        text = "Personas disponibles para chatear",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(otherUsers, key = { "dir_${it.userId}" }) { user ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { activePartner = user }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatar(
                            avatarBase64 = user.avatarBase64,
                            displayName = user.displayName,
                            size = 42.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(user.displayName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "@${user.username}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "Enviar mensaje",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }

    if (showNoteDialog) {
        NoteEditorDialog(
            initialText = myNote?.text.orEmpty(),
            hasExisting = myNote != null,
            onSave = { txt -> onUpsertNote(txt) { showNoteDialog = false } },
            onDelete = { onDeleteNote { showNoteDialog = false } },
            onDismiss = { showNoteDialog = false }
        )
    }
}

@Composable
private fun NoteBubbleItem(
    avatarBase64: String?,
    displayName: String,
    username: String,
    noteText: String,
    isPlaceholder: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(86.dp)
            .clickable(onClick = onClick)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (isPlaceholder) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 2.dp
        ) {
            Text(
                text = noteText,
                style = MaterialTheme.typography.labelSmall,
                color = if (isPlaceholder) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        UserAvatar(
            avatarBase64 = avatarBase64,
            displayName = displayName,
            size = 56.dp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = username,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ChatDetailScreen(
    myProfile: UserProfile,
    partner: UserProfile,
    messagesFlow: Flow<List<ChatMessage>>,
    onSendMessage: (String) -> Unit,
    onBack: () -> Unit
) {
    val messages by messagesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var messageInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver a mensajes")
            }
            UserAvatar(
                avatarBase64 = partner.avatarBase64,
                displayName = partner.displayName,
                size = 36.dp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = partner.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "@${partner.username}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillParentMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Envía el primer mensaje a ${partner.displayName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(messages, key = { it.id }) { msg ->
                    val isMine = msg.senderId == myProfile.userId
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Surface(
                            color = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(
                                topStart = 18.dp,
                                topEnd = 18.dp,
                                bottomStart = if (isMine) 18.dp else 4.dp,
                                bottomEnd = if (isMine) 4.dp else 18.dp
                            ),
                            modifier = Modifier.widthIn(max = 280.dp)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                                Text(
                                    text = msg.text,
                                    color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = messageInput,
                onValueChange = { if (it.length <= 500) messageInput = it },
                placeholder = { Text("Enviar mensaje...") },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_message_input")
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    val clean = messageInput.trim()
                    if (clean.isNotEmpty()) {
                        onSendMessage(clean)
                        messageInput = ""
                    }
                },
                enabled = messageInput.isNotBlank(),
                modifier = Modifier.testTag("chat_send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Enviar",
                    tint = if (messageInput.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun NoteEditorDialog(
    initialText: String,
    hasExisting: Boolean,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tu nota") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Comparte lo que estás pensando (máx. 60 caracteres).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 60) text = it },
                    placeholder = { Text("Ej. Escuchando música...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(text) },
                enabled = text.trim().isNotEmpty()
            ) {
                Text("Compartir")
            }
        },
        dismissButton = {
            Row {
                if (hasExisting) {
                    TextButton(onClick = onDelete) {
                        Text("Borrar", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
            }
        }
    )
}
