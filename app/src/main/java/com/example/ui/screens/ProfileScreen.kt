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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.model.MediaType
import com.example.model.Post
import com.example.model.PostType
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.ui.components.Base64Image
import com.example.ui.components.PostCard
import com.example.ui.components.UserAvatar
import com.example.ui.theme.CloseFriendsGreen
import java.io.File

@Composable
fun ProfileScreen(
    viewedProfile: UserProfile,
    myProfile: UserProfile,
    allUsers: List<UserProfile>,
    postsState: UiState<List<Post>>,
    savedPostIds: Set<String>,
    likedPostIds: Set<String>,
    followingIds: Set<String>,
    closeFriendIds: Set<String>,
    videoFiles: Map<String, File>,
    videoLoadingIds: Set<String>,
    isBusy: Boolean,
    showBackButton: Boolean,
    onBack: () -> Unit,
    onUpdateProfile: (displayName: String, username: String, bio: String, avatarUri: Uri?, onDone: () -> Unit) -> Unit,
    onToggleFollow: (UserProfile) -> Unit,
    onToggleCloseFriend: (UserProfile) -> Unit,
    onOpenMessageWithUser: (UserProfile) -> Unit,
    onRequestVideoLoad: (Post) -> Unit,
    onLikePost: (Post) -> Unit,
    onOpenComments: (Post) -> Unit,
    onSharePost: (Post) -> Unit,
    onSavePost: (String) -> Unit,
    onDeletePost: (Post) -> Unit,
    onSignOut: () -> Unit
) {
    val isOwnProfile = viewedProfile.userId == myProfile.userId
    if (showBackButton) {
        BackHandler { onBack() }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showEditSheet by remember { mutableStateOf(false) }
    var showCloseFriendsSheet by remember { mutableStateOf(false) }
    var inspectedPost by remember { mutableStateOf<Post?>(null) }

    val allPosts = (postsState as? UiState.Success)?.data ?: emptyList()
    val userPosts = remember(allPosts, viewedProfile.userId) {
        allPosts.filter { it.authorId == viewedProfile.userId }
    }
    val userReels = remember(userPosts) {
        userPosts.filter { it.postType == PostType.REEL.name || it.mediaType == MediaType.VIDEO.name }
    }
    val savedPosts = remember(allPosts, savedPostIds) {
        allPosts.filter { it.id in savedPostIds }
    }

    val displayedGrid = when (selectedTab) {
        0 -> userPosts
        1 -> userReels
        else -> savedPosts
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBackButton) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
            }
            Text(
                text = viewedProfile.username,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (showBackButton) 4.dp else 6.dp)
            )
            if (isOwnProfile) {
                IconButton(
                    onClick = onSignOut,
                    modifier = Modifier.testTag("sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Cerrar sesión"
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

        // Profile Info Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserAvatar(
                    avatarBase64 = viewedProfile.avatarBase64,
                    displayName = viewedProfile.displayName,
                    size = 82.dp
                )
                Spacer(modifier = Modifier.width(20.dp))
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ProfileStat(count = userPosts.size.toLong(), label = "Publicac.")
                    ProfileStat(count = viewedProfile.followersCount, label = "Seguidores")
                    ProfileStat(count = viewedProfile.followingCount, label = "Siguiendo")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = viewedProfile.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (viewedProfile.bio.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = viewedProfile.bio,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (isOwnProfile) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showEditSheet = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("edit_profile_button")
                    ) {
                        Text("Editar perfil")
                    }
                    OutlinedButton(
                        onClick = { showCloseFriendsSheet = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("close_friends_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = CloseFriendsGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Mejores amigos")
                    }
                }
            } else {
                val isFollowing = viewedProfile.userId in followingIds
                val isCloseFriend = viewedProfile.userId in closeFriendIds
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onToggleFollow(viewedProfile) },
                        shape = RoundedCornerShape(10.dp),
                        colors = if (isFollowing) {
                            ButtonDefaults.outlinedButtonColors()
                        } else {
                            ButtonDefaults.buttonColors()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isFollowing) "Siguiendo" else "Seguir")
                    }
                    OutlinedButton(
                        onClick = { onOpenMessageWithUser(viewedProfile) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Mensaje")
                    }
                    IconButton(onClick = { onToggleCloseFriend(viewedProfile) }) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Mejores amigos",
                            tint = if (isCloseFriend) CloseFriendsGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Tabs
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                icon = { Icon(Icons.Default.GridOn, contentDescription = "Publicaciones") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                icon = { Icon(Icons.Default.Movie, contentDescription = "Reels") }
            )
            if (isOwnProfile) {
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.BookmarkBorder, contentDescription = "Guardados") }
                )
            }
        }

        if (displayedGrid.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (selectedTab) {
                        0 -> "Aún no hay publicaciones aquí."
                        1 -> "Aún no hay Reels publicados."
                        else -> "No has guardado ninguna publicación todavía."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(displayedGrid, key = { it.id }) { post ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clickable { inspectedPost = post }
                    ) {
                        Base64Image(
                            base64 = if (post.mediaType == MediaType.VIDEO.name) post.thumbnailBase64 else post.mediaBase64,
                            contentDescription = "Publicación",
                            modifier = Modifier.fillMaxSize()
                        )
                        if (post.mediaType == MediaType.VIDEO.name) {
                            Icon(
                                imageVector = Icons.Default.PlayCircleOutline,
                                contentDescription = "Video",
                                tint = Color.White,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditSheet) {
        EditProfileBottomSheet(
            profile = myProfile,
            isBusy = isBusy,
            onSave = { name, handle, bio, uri ->
                onUpdateProfile(name, handle, bio, uri) { showEditSheet = false }
            },
            onDismiss = { showEditSheet = false }
        )
    }

    if (showCloseFriendsSheet) {
        CloseFriendsBottomSheet(
            allUsers = allUsers.filter { it.userId != myProfile.userId },
            closeFriendIds = closeFriendIds,
            onToggleCloseFriend = onToggleCloseFriend,
            onDismiss = { showCloseFriendsSheet = false }
        )
    }

    inspectedPost?.let { post ->
        val freshPost = allPosts.firstOrNull { it.id == post.id }
        if (freshPost == null) {
            inspectedPost = null
        } else {
            Dialog(
                onDismissRequest = { inspectedPost = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = { inspectedPost = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar")
                        }
                    }
                    PostCard(
                        post = freshPost,
                        currentUid = myProfile.userId,
                        isLiked = freshPost.id in likedPostIds,
                        isSaved = freshPost.id in savedPostIds,
                        isFollowingAuthor = freshPost.authorId in followingIds,
                        videoFile = videoFiles[freshPost.id],
                        isVideoLoading = freshPost.id in videoLoadingIds,
                        onRequestVideoLoad = { onRequestVideoLoad(freshPost) },
                        onAuthorClick = { inspectedPost = null },
                        onFollowClick = { },
                        onLikeClick = { onLikePost(freshPost) },
                        onCommentClick = { onOpenComments(freshPost) },
                        onShareClick = { onSharePost(freshPost) },
                        onSaveClick = { onSavePost(freshPost.id) },
                        onDeleteClick = {
                            onDeletePost(freshPost)
                            inspectedPost = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileStat(count: Long, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileBottomSheet(
    profile: UserProfile,
    isBusy: Boolean,
    onSave: (String, String, String, Uri?) -> Unit,
    onDismiss: () -> Unit
) {
    var displayName by remember { mutableStateOf(profile.displayName) }
    var username by remember { mutableStateOf(profile.username) }
    var bio by remember { mutableStateOf(profile.bio) }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) avatarUri = uri }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Editar perfil",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .clickable {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                contentAlignment = Alignment.Center
            ) {
                if (avatarUri != null) {
                    AsyncImage(
                        model = avatarUri,
                        contentDescription = "Nueva foto",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    UserAvatar(
                        avatarBase64 = profile.avatarBase64,
                        displayName = profile.displayName,
                        size = 84.dp
                    )
                }
            }
            Text(
                text = "Cambiar foto",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.clickable {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            )

            OutlinedTextField(
                value = displayName,
                onValueChange = { if (it.length <= 50) displayName = it },
                label = { Text("Nombre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = username,
                onValueChange = { if (it.length <= 30) username = it },
                label = { Text("Usuario") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = bio,
                onValueChange = { if (it.length <= 160) bio = it },
                label = { Text("Presentación") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { onSave(displayName, username, bio, avatarUri) },
                enabled = displayName.isNotBlank() && username.trim().length >= 3 && !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Guardar cambios")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CloseFriendsBottomSheet(
    allUsers: List<UserProfile>,
    closeFriendIds: Set<String>,
    onToggleCloseFriend: (UserProfile) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = CloseFriendsGreen
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Mejores amigos",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Solo las personas que elijas podrán ver tus historias o publicaciones marcadas para Mejores amigos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (allUsers.isEmpty()) {
                Text(
                    text = "Aún no hay otras personas registradas en Momentia.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(allUsers, key = { it.userId }) { user ->
                        val checked = user.userId in closeFriendIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleCloseFriend(user) }
                                .padding(vertical = 6.dp),
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
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { onToggleCloseFriend(user) }
                            )
                        }
                    }
                }
            }
        }
    }
}
