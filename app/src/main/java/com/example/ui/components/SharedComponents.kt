package com.example.ui.components

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.AudienceType
import com.example.model.Comment
import com.example.model.MediaType
import com.example.model.Post
import com.example.model.UserProfile
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.BrandMagenta
import com.example.ui.theme.CloseFriendsGreen
import com.example.ui.theme.LikeRed
import com.example.ui.theme.StoryRingBrush
import com.example.util.MediaUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import java.io.File

@Composable
fun UserAvatar(
    avatarBase64: String?,
    displayName: String,
    size: Dp = 40.dp,
    hasStoryRing: Boolean = false,
    isCloseFriendsStory: Boolean = false,
    modifier: Modifier = Modifier
) {
    val imageBitmap = remember(avatarBase64) { MediaUtils.decodeBase64Image(avatarBase64) }
    val ringModifier = when {
        hasStoryRing && isCloseFriendsStory -> Modifier
            .border(2.5.dp, CloseFriendsGreen, CircleShape)
            .padding(3.5.dp)
        hasStoryRing -> Modifier
            .border(2.5.dp, StoryRingBrush, CircleShape)
            .padding(3.5.dp)
        else -> Modifier
    }

    Box(
        modifier = modifier
            .size(size)
            .then(ringModifier)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        BrandMagenta.copy(alpha = 0.25f),
                        BrandCoral.copy(alpha = 0.25f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "Avatar de $displayName",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = displayName.trim().firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun Base64Image(
    base64: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val bitmap = remember(base64) { MediaUtils.decodeBase64Image(base64) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
fun VideoTexturePlayer(
    videoFile: File,
    isActive: Boolean,
    isMuted: Boolean,
    modifier: Modifier = Modifier,
    centerCrop: Boolean = true
) {
    val mediaPlayer = remember(videoFile.absolutePath) { MediaPlayer() }
    var isPrepared by remember(videoFile.absolutePath) { mutableStateOf(false) }

    LaunchedEffect(isMuted, isPrepared) {
        if (isPrepared) {
            val vol = if (isMuted) 0f else 1f
            try {
                mediaPlayer.setVolume(vol, vol)
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(isActive, isPrepared) {
        if (isPrepared) {
            try {
                if (isActive && !mediaPlayer.isPlaying) {
                    mediaPlayer.start()
                } else if (!isActive && mediaPlayer.isPlaying) {
                    mediaPlayer.pause()
                }
            } catch (_: Exception) {
            }
        }
    }

    DisposableEffect(videoFile.absolutePath) {
        onDispose {
            try {
                if (mediaPlayer.isPlaying) mediaPlayer.stop()
                mediaPlayer.release()
            } catch (_: Exception) {
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                        try {
                            mediaPlayer.reset()
                            mediaPlayer.setDataSource(videoFile.absolutePath)
                            mediaPlayer.setSurface(Surface(surfaceTexture))
                            mediaPlayer.isLooping = true
                            mediaPlayer.setOnPreparedListener { mp ->
                                isPrepared = true
                                val vol = if (isMuted) 0f else 1f
                                mp.setVolume(vol, vol)
                                if (centerCrop && width > 0 && height > 0 && mp.videoWidth > 0 && mp.videoHeight > 0) {
                                    val viewRatio = width.toFloat() / height.toFloat()
                                    val videoRatio = mp.videoWidth.toFloat() / mp.videoHeight.toFloat()
                                    val scaleX = if (videoRatio > viewRatio) videoRatio / viewRatio else 1f
                                    val scaleY = if (videoRatio < viewRatio) viewRatio / videoRatio else 1f
                                    val matrix = Matrix()
                                    matrix.setScale(scaleX, scaleY, width / 2f, height / 2f)
                                    setTransform(matrix)
                                }
                                if (isActive) mp.start()
                            }
                            mediaPlayer.prepareAsync()
                        } catch (_: Exception) {
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true
                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                }
            }
        },
        modifier = modifier
    )
}

@Composable
fun PostCard(
    post: Post,
    currentUid: String,
    isLiked: Boolean,
    isSaved: Boolean,
    isFollowingAuthor: Boolean,
    videoFile: File?,
    isVideoLoading: Boolean,
    onRequestVideoLoad: () -> Unit,
    onAuthorClick: () -> Unit,
    onFollowClick: () -> Unit,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onShareClick: () -> Unit,
    onSaveClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDoubleTapHeart by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(true) }
    var isVideoPlaying by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val heartScale by animateFloatAsState(
        targetValue = if (isLiked) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "heartScale"
    )

    LaunchedEffect(showDoubleTapHeart) {
        if (showDoubleTapHeart) {
            delay(700)
            showDoubleTapHeart = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Post Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onAuthorClick),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserAvatar(
                    avatarBase64 = post.authorAvatarBase64,
                    displayName = post.authorDisplayName,
                    size = 38.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = post.authorUsername,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (post.audience == AudienceType.CLOSE_FRIENDS.name) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = CloseFriendsGreen,
                                shape = CircleShape
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Mejores amigos",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .padding(3.dp)
                                        .size(11.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = MediaUtils.formatRelativeTime(post.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (post.authorId != currentUid && !isFollowingAuthor) {
                TextButton(
                    onClick = onFollowClick,
                    modifier = Modifier.testTag("post_follow_btn_${post.id}")
                ) {
                    Text(
                        text = "Seguir",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            if (post.authorId == currentUid) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Opciones de publicación"
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Eliminar publicación", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                showMenu = false
                                onDeleteClick()
                            }
                        )
                    }
                }
            }
        }

        // Media Canvas (1:1 or 4:5)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (post.mediaType == MediaType.VIDEO.name) 0.85f else 1f)
                .background(Color.Black)
                .pointerInput(post.id, isLiked) {
                    detectTapGestures(
                        onDoubleTap = {
                            showDoubleTapHeart = true
                            if (!isLiked) onLikeClick()
                        },
                        onTap = {
                            if (post.mediaType == MediaType.VIDEO.name) {
                                if (videoFile == null) {
                                    onRequestVideoLoad()
                                    isVideoPlaying = true
                                } else {
                                    isVideoPlaying = !isVideoPlaying
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (post.mediaType == MediaType.VIDEO.name) {
                if (videoFile != null && isVideoPlaying) {
                    VideoTexturePlayer(
                        videoFile = videoFile,
                        isActive = true,
                        isMuted = isMuted,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Base64Image(
                        base64 = post.thumbnailBase64,
                        contentDescription = "Miniatura de video de ${post.authorUsername}",
                        modifier = Modifier.fillMaxSize()
                    )
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier.size(60.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isVideoLoading) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Reproducir video",
                                    tint = Color.White,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                    }
                }
                if (videoFile != null && isVideoPlaying) {
                    IconButton(
                        onClick = { isMuted = !isMuted },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .size(36.dp)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = if (isMuted) "Activar sonido" else "Silenciar",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else {
                Base64Image(
                    base64 = post.mediaBase64,
                    contentDescription = "Foto de ${post.authorUsername}: ${post.caption}",
                    modifier = Modifier.fillMaxSize()
                )
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showDoubleTapHeart,
                enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(92.dp)
                )
            }
        }

        // Actions Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onLikeClick,
                modifier = Modifier.testTag("like_btn_${post.id}")
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isLiked) "Quitar Me gusta" else "Me gusta",
                    tint = if (isLiked) LikeRed else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.scale(heartScale)
                )
            }
            IconButton(
                onClick = onCommentClick,
                modifier = Modifier.testTag("comment_btn_${post.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.ChatBubbleOutline,
                    contentDescription = "Comentar"
                )
            }
            IconButton(
                onClick = onShareClick,
                modifier = Modifier.testTag("share_btn_${post.id}")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Enviar por mensaje"
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = onSaveClick,
                modifier = Modifier.testTag("save_btn_${post.id}")
            ) {
                Icon(
                    imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = if (isSaved) "Quitar de guardados" else "Guardar"
                )
            }
        }

        // Likes & Caption
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(bottom = 14.dp)
        ) {
            if (post.likesCount > 0L) {
                Text(
                    text = if (post.likesCount == 1L) "1 Me gusta" else "${post.likesCount} Me gusta",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(3.dp))
            }
            if (post.caption.isNotBlank()) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(post.authorUsername)
                            append(" ")
                        }
                        append(post.caption)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (post.commentsCount > 0L) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (post.commentsCount == 1L) "Ver 1 comentario" else "Ver los ${post.commentsCount} comentarios",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onCommentClick)
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsBottomSheet(
    post: Post,
    commentsFlow: Flow<List<Comment>>,
    onSendComment: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val comments by commentsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var commentText by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Comentarios",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()

            if (comments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sé la primera persona en comentar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(4.dp)) }
                    items(comments, key = { it.id }) { comment ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            UserAvatar(
                                avatarBase64 = comment.authorAvatarBase64,
                                displayName = comment.authorUsername,
                                size = 34.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = comment.authorUsername,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = MediaUtils.formatRelativeTime(comment.createdAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = comment.text,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { if (it.length <= 300) commentText = it },
                    placeholder = { Text("Agrega un comentario...") },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("comment_input")
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val clean = commentText.trim()
                        if (clean.isNotEmpty()) {
                            onSendComment(clean)
                            commentText = ""
                        }
                    },
                    enabled = commentText.isNotBlank(),
                    modifier = Modifier.testTag("send_comment_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Enviar comentario",
                        tint = if (commentText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharePostBottomSheet(
    post: Post,
    users: List<UserProfile>,
    currentUid: String,
    onSendToUser: (UserProfile) -> Unit,
    onDismiss: () -> Unit
) {
    val others = remember(users, currentUid) { users.filter { it.userId != currentUid } }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Enviar publicación por mensaje",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (others.isEmpty()) {
                Text(
                    text = "Aún no hay otras personas registradas para compartirles esta publicación.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(others, key = { it.userId }) { user ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSendToUser(user) }
                                .padding(8.dp),
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
                                text = "Enviar",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }
}
