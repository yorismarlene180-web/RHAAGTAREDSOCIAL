package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.R
import com.example.model.ActivityActionType
import com.example.model.ActivityItem
import com.example.model.AudienceType
import com.example.model.Post
import com.example.model.Story
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.ui.components.Base64Image
import com.example.ui.components.PostCard
import com.example.ui.components.UserAvatar
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.CloseFriendsGreen
import com.example.util.MediaUtils
import kotlinx.coroutines.delay
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    myProfile: UserProfile,
    postsState: UiState<List<Post>>,
    storiesState: UiState<List<Story>>,
    activityState: UiState<List<ActivityItem>>,
    likedPostIds: Set<String>,
    savedPostIds: Set<String>,
    followingIds: Set<String>,
    videoFiles: Map<String, File>,
    videoLoadingIds: Set<String>,
    isBusy: Boolean,
    onRequestVideoLoad: (Post) -> Unit,
    onOpenMessages: () -> Unit,
    onOpenCreate: () -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onFollowAuthor: (Post) -> Unit,
    onLikePost: (Post) -> Unit,
    onOpenComments: (Post) -> Unit,
    onSharePost: (Post) -> Unit,
    onSavePost: (String) -> Unit,
    onDeletePost: (Post) -> Unit,
    onPublishStory: (Uri, String?, AudienceType, onDone: () -> Unit) -> Unit
) {
    var activeStoryAuthorGroup by remember { mutableStateOf<List<Story>?>(null) }
    var showStoryCreator by remember { mutableStateOf(false) }
    var showActivitySheet by remember { mutableStateOf(false) }

    val stories = (storiesState as? UiState.Success)?.data ?: emptyList()
    val groupedStories = remember(stories) {
        stories.groupBy { it.authorId }.values.toList()
    }
    val myStories = remember(stories, myProfile.userId) {
        stories.filter { it.authorId == myProfile.userId }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Instagram-style Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { showActivitySheet = true },
                modifier = Modifier.testTag("feed_activity_button")
            ) {
                Icon(
                    imageVector = Icons.Default.FavoriteBorder,
                    contentDescription = "Actividad y notificaciones"
                )
            }
            IconButton(
                onClick = onOpenMessages,
                modifier = Modifier.testTag("feed_messages_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = "Mensajes directos"
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

        when (postsState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = postsState.message,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }
            is UiState.Success -> {
                val posts = postsState.data
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Stories Tray
                    item(key = "stories_tray") {
                        StoriesTray(
                            myProfile = myProfile,
                            myStories = myStories,
                            otherStoryGroups = groupedStories.filter { it.firstOrNull()?.authorId != myProfile.userId },
                            onAddStoryClick = { showStoryCreator = true },
                            onViewStories = { activeStoryAuthorGroup = it }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    }

                    if (posts.isEmpty()) {
                        item(key = "empty_feed") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 28.dp, vertical = 56.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(72.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.AddPhotoAlternate,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(34.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.empty_feed_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.empty_feed_body),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(
                                    onClick = onOpenCreate,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.testTag("empty_feed_create_button")
                                ) {
                                    Text("Publicar foto o video")
                                }
                            }
                        }
                    } else {
                        items(posts, key = { it.id }) { post ->
                            PostCard(
                                post = post,
                                currentUid = myProfile.userId,
                                isLiked = post.id in likedPostIds,
                                isSaved = post.id in savedPostIds,
                                isFollowingAuthor = post.authorId in followingIds,
                                videoFile = videoFiles[post.id],
                                isVideoLoading = post.id in videoLoadingIds,
                                onRequestVideoLoad = { onRequestVideoLoad(post) },
                                onAuthorClick = { onOpenUserProfile(post.authorId) },
                                onFollowClick = { onFollowAuthor(post) },
                                onLikeClick = { onLikePost(post) },
                                onCommentClick = { onOpenComments(post) },
                                onShareClick = { onSharePost(post) },
                                onSaveClick = { onSavePost(post.id) },
                                onDeleteClick = { onDeletePost(post) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (activeStoryAuthorGroup != null) {
        StoryViewerDialog(
            stories = activeStoryAuthorGroup!!,
            onDismiss = { activeStoryAuthorGroup = null }
        )
    }

    if (showStoryCreator) {
        StoryCreatorBottomSheet(
            isBusy = isBusy,
            onPublish = { uri, cap, aud ->
                onPublishStory(uri, cap, aud) { showStoryCreator = false }
            },
            onDismiss = { showStoryCreator = false }
        )
    }

    if (showActivitySheet) {
        ActivityBottomSheet(
            activityState = activityState,
            onUserClick = { uid ->
                showActivitySheet = false
                onOpenUserProfile(uid)
            },
            onDismiss = { showActivitySheet = false }
        )
    }
}

@Composable
private fun StoriesTray(
    myProfile: UserProfile,
    myStories: List<Story>,
    otherStoryGroups: List<List<Story>>,
    onAddStoryClick: () -> Unit,
    onViewStories: (List<Story>) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // My Story Item
        item(key = "my_story") {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(72.dp)
                    .clickable {
                        if (myStories.isNotEmpty()) onViewStories(myStories) else onAddStoryClick()
                    }
                    .testTag("my_story_item")
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    UserAvatar(
                        avatarBase64 = myProfile.avatarBase64,
                        displayName = myProfile.displayName,
                        size = 64.dp,
                        hasStoryRing = myStories.isNotEmpty(),
                        isCloseFriendsStory = myStories.any { it.audience == AudienceType.CLOSE_FRIENDS.name }
                    )
                    Surface(
                        shape = CircleShape,
                        color = BrandBlue,
                        border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .size(22.dp)
                            .clickable(onClick = onAddStoryClick)
                            .testTag("add_story_plus_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Agregar historia",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.your_story),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        items(otherStoryGroups, key = { it.first().authorId }) { group ->
            val first = group.first()
            val hasCloseFriends = group.any { it.audience == AudienceType.CLOSE_FRIENDS.name }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(72.dp)
                    .clickable { onViewStories(group) }
            ) {
                UserAvatar(
                    avatarBase64 = first.authorAvatarBase64,
                    displayName = first.authorDisplayName,
                    size = 64.dp,
                    hasStoryRing = true,
                    isCloseFriendsStory = hasCloseFriends
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = first.authorUsername,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun StoryViewerDialog(
    stories: List<Story>,
    onDismiss: () -> Unit
) {
    var currentIndex by remember { mutableIntStateOf(0) }
    val story = stories.getOrNull(currentIndex) ?: return

    BackHandler { onDismiss() }

    LaunchedEffect(currentIndex, stories.size) {
        delay(5000L)
        if (currentIndex + 1 < stories.size) {
            currentIndex++
        } else {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .safeDrawingPadding()
        ) {
            Base64Image(
                base64 = story.mediaBase64,
                contentDescription = "Historia de ${story.authorUsername}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )

            // Left / Right tap zones
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable {
                            if (currentIndex > 0) currentIndex-- else onDismiss()
                        }
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable {
                            if (currentIndex + 1 < stories.size) currentIndex++ else onDismiss()
                        }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    stories.forEachIndexed { idx, _ ->
                        LinearProgressIndicator(
                            progress = { if (idx <= currentIndex) 1f else 0f },
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.35f),
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UserAvatar(
                        avatarBase64 = story.authorAvatarBase64,
                        displayName = story.authorDisplayName,
                        size = 36.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = story.authorUsername,
                                color = Color.White,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (story.audience == AudienceType.CLOSE_FRIENDS.name) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = CloseFriendsGreen,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Star,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "Mejores amigos",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            text = MediaUtils.formatRelativeTime(story.createdAt),
                            color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar historia",
                            tint = Color.White
                        )
                    }
                }
            }

            if (!story.caption.isNullOrBlank()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 32.dp)
                ) {
                    Text(
                        text = story.caption,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoryCreatorBottomSheet(
    isBusy: Boolean,
    onPublish: (Uri, String?, AudienceType) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var caption by remember { mutableStateOf("") }
    var audience by remember { mutableStateOf(AudienceType.PUBLIC) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) selectedUri = uri }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Compartir historia (24h)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            if (selectedUri != null) {
                AsyncImage(
                    model = selectedUri,
                    contentDescription = "Vista previa de historia",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            }

            OutlinedButton(
                onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (selectedUri == null) "Elegir foto de galería" else "Cambiar foto")
            }

            OutlinedTextField(
                value = caption,
                onValueChange = { if (it.length <= 200) caption = it },
                label = { Text("Texto en la historia (opcional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = audience == AudienceType.PUBLIC,
                    onClick = { audience = AudienceType.PUBLIC },
                    label = { Text("Tu historia (Todos)") }
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

            Button(
                onClick = { selectedUri?.let { onPublish(it, caption, audience) } },
                enabled = selectedUri != null && !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Publicar historia")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityBottomSheet(
    activityState: UiState<List<ActivityItem>>,
    onUserClick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Actividad",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            val items = (activityState as? UiState.Success)?.data ?: emptyList()
            if (items.isEmpty()) {
                Text(
                    text = "Cuando otras personas den Me gusta, comenten o te sigan, lo verás aquí.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items, key = { it.id }) { act ->
                        val desc = when (ActivityActionType.fromRaw(act.actionType)) {
                            ActivityActionType.LIKE -> "le gustó tu publicación"
                            ActivityActionType.COMMENT -> "comentó: ${act.previewText.orEmpty()}"
                            ActivityActionType.FOLLOW -> "comenzó a seguirte"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onUserClick(act.actorId) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(
                                avatarBase64 = act.actorAvatarBase64,
                                displayName = act.actorUsername,
                                size = 40.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${act.actorUsername} $desc",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = MediaUtils.formatRelativeTime(act.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
