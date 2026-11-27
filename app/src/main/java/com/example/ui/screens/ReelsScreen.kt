package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.MediaType
import com.example.model.Post
import com.example.model.PostType
import com.example.model.UiState
import com.example.ui.components.Base64Image
import com.example.ui.components.UserAvatar
import com.example.ui.components.VideoTexturePlayer
import com.example.ui.theme.LikeRed
import java.io.File

@Composable
fun ReelsScreen(
    currentUid: String,
    postsState: UiState<List<Post>>,
    likedPostIds: Set<String>,
    followingIds: Set<String>,
    videoFiles: Map<String, File>,
    videoLoadingIds: Set<String>,
    onRequestVideoLoad: (Post) -> Unit,
    onOpenCreateReel: () -> Unit,
    onOpenAuthor: (String) -> Unit,
    onFollowAuthor: (Post) -> Unit,
    onLikePost: (Post) -> Unit,
    onOpenComments: (Post) -> Unit,
    onSharePost: (Post) -> Unit
) {
    val allPosts = (postsState as? UiState.Success)?.data ?: emptyList()
    val reels = remember(allPosts) {
        allPosts.filter { it.postType == PostType.REEL.name || it.mediaType == MediaType.VIDEO.name }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (postsState) {
            is UiState.Loading -> {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
            }
            is UiState.Error -> {
                Text(
                    text = postsState.message,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                )
            }
            is UiState.Success -> {
                if (reels.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.14f),
                            modifier = Modifier.size(76.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MovieCreation,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.empty_reels_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.empty_reels_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.78f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = onOpenCreateReel,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.testTag("empty_reels_create_button")
                        ) {
                            Text("Subir primer Reel")
                        }
                    }
                } else {
                    val pagerState = rememberPagerState(pageCount = { reels.size })
                    var isMuted by remember { mutableStateOf(false) }

                    LaunchedEffect(pagerState.currentPage, reels.size) {
                        reels.getOrNull(pagerState.currentPage)?.let { onRequestVideoLoad(it) }
                        reels.getOrNull(pagerState.currentPage + 1)?.let { onRequestVideoLoad(it) }
                    }

                    VerticalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val reel = reels[page]
                        val isActive = pagerState.currentPage == page
                        val videoFile = videoFiles[reel.id]
                        val isLoading = reel.id in videoLoadingIds
                        val isLiked = reel.id in likedPostIds

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(reel.id, isLiked) {
                                    detectTapGestures(
                                        onTap = { isMuted = !isMuted },
                                        onDoubleTap = { if (!isLiked) onLikePost(reel) }
                                    )
                                }
                        ) {
                            if (videoFile != null) {
                                VideoTexturePlayer(
                                    videoFile = videoFile,
                                    isActive = isActive,
                                    isMuted = isMuted,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Base64Image(
                                    base64 = reel.thumbnailBase64,
                                    contentDescription = "Reel de ${reel.authorUsername}",
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }
                            }

                            // Bottom readability scrim
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                                        )
                                    )
                            )

                            // Top Bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                                    .align(Alignment.TopCenter),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Reels",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { isMuted = !isMuted }) {
                                    Icon(
                                        imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                        contentDescription = if (isMuted) "Activar sonido" else "Silenciar",
                                        tint = Color.White
                                    )
                                }
                            }

                            // Right side action column
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 12.dp, bottom = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    IconButton(onClick = { onLikePost(reel) }) {
                                        Icon(
                                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = "Me gusta",
                                            tint = if (isLiked) LikeRed else Color.White,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                    Text(
                                        text = "${reel.likesCount}",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    IconButton(onClick = { onOpenComments(reel) }) {
                                        Icon(
                                            imageVector = Icons.Default.ChatBubbleOutline,
                                            contentDescription = "Comentar",
                                            tint = Color.White,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Text(
                                        text = "${reel.commentsCount}",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                                IconButton(onClick = { onSharePost(reel) }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Compartir Reel",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            // Bottom-left Author + Caption
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 16.dp, end = 76.dp, bottom = 24.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { onOpenAuthor(reel.authorId) }
                                ) {
                                    UserAvatar(
                                        avatarBase64 = reel.authorAvatarBase64,
                                        displayName = reel.authorDisplayName,
                                        size = 36.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = reel.authorUsername,
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (reel.authorId != currentUid && reel.authorId !in followingIds) {
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Surface(
                                            color = Color.Transparent,
                                            shape = RoundedCornerShape(8.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
                                            modifier = Modifier.clickable { onFollowAuthor(reel) }
                                        ) {
                                            Text(
                                                text = "Seguir",
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelMedium,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                                if (reel.caption.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = reel.caption,
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 3
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
