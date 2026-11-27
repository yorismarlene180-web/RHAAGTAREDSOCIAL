package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.data.MomentiaRepository
import com.example.model.Post
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.ui.components.CommentsBottomSheet
import com.example.ui.components.SharePostBottomSheet
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CreateFormat
import com.example.ui.screens.CreateScreen
import com.example.ui.screens.FeedScreen
import com.example.ui.screens.MessagesScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ReelsScreen
import com.example.ui.screens.RegisterNameScreen
import com.example.ui.screens.SearchExploreScreen
import com.example.ui.theme.MomentiaTheme
import com.example.viewmodel.MomentiaViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

object Routes {
    const val FEED = "feed"
    const val SEARCH = "search"
    const val CREATE = "create"
    const val REELS = "reels"
    const val PROFILE = "profile"
    const val MESSAGES = "messages"
    const val USER_PROFILE = "user_profile/{userId}"
    fun userProfile(userId: String) = "user_profile/$userId"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MomentiaTheme {
                AppContent()
            }
        }
    }
}

@Composable
fun AppContent() {
    val currentUser by Firebase.auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedMomentiaRoot(currentUserId = user.uid)
        }
    }
}

@Composable
private fun AuthenticatedMomentiaRoot(currentUserId: String) {
    val viewModel: MomentiaViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                MomentiaViewModel(MomentiaRepository(db), currentUserId)
            }
        }
    )

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val myProfileState by viewModel.myProfileState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val feedbackMessage by viewModel.feedbackMessage.collectAsStateWithLifecycle()

    when (val state = myProfileState) {
        is UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is UiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        is UiState.Success -> {
            val myProfile = state.data
            if (myProfile == null) {
                RegisterNameScreen(
                    isBusy = isBusy,
                    errorMessage = feedbackMessage,
                    onRegister = { displayName, username, bio, avatarUri ->
                        viewModel.clearFeedback()
                        viewModel.registerProfile(context, displayName, username, bio, avatarUri)
                    }
                )
            } else {
                MomentiaAppShell(
                    viewModel = viewModel,
                    myProfile = myProfile,
                    onSignOut = {
                        signOut(credentialManager, scope)
                    }
                )
            }
        }
    }
}

@Composable
private fun MomentiaAppShell(
    viewModel: MomentiaViewModel,
    myProfile: UserProfile,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val usersState by viewModel.usersState.collectAsStateWithLifecycle()
    val postsState by viewModel.postsState.collectAsStateWithLifecycle()
    val storiesState by viewModel.storiesState.collectAsStateWithLifecycle()
    val notesState by viewModel.notesState.collectAsStateWithLifecycle()
    val activityState by viewModel.activityState.collectAsStateWithLifecycle()
    val chatsState by viewModel.chatsState.collectAsStateWithLifecycle()
    val likedPostIds by viewModel.likedPostIds.collectAsStateWithLifecycle()
    val savedPostIds by viewModel.savedPostIds.collectAsStateWithLifecycle()
    val followingIds by viewModel.followingIds.collectAsStateWithLifecycle()
    val closeFriendIds by viewModel.closeFriendIds.collectAsStateWithLifecycle()
    val videoFiles by viewModel.videoFiles.collectAsStateWithLifecycle()
    val videoLoadingIds by viewModel.videoLoadingIds.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val feedbackMessage by viewModel.feedbackMessage.collectAsStateWithLifecycle()

    var commentsPost by remember { mutableStateOf<Post?>(null) }
    var sharePost by remember { mutableStateOf<Post?>(null) }
    var preferredCreateFormat by remember { mutableStateOf(CreateFormat.POST) }

    LaunchedEffect(feedbackMessage) {
        val msg = feedbackMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearFeedback()
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomOrRail = currentRoute in setOf(
        Routes.FEED, Routes.SEARCH, Routes.CREATE, Routes.REELS, Routes.PROFILE
    )

    val allUsers = (usersState as? UiState.Success)?.data ?: emptyList()

    val navItems = listOf(
        Triple(Routes.FEED, stringResource(R.string.tab_home), Pair(Icons.Filled.Home, Icons.Outlined.Home)),
        Triple(Routes.SEARCH, stringResource(R.string.tab_search), Pair(Icons.Filled.Search, Icons.Outlined.Search)),
        Triple(Routes.CREATE, stringResource(R.string.tab_create), Pair(Icons.Filled.AddBox, Icons.Outlined.AddBox)),
        Triple(Routes.REELS, stringResource(R.string.tab_reels), Pair(Icons.Filled.Movie, Icons.Outlined.Movie)),
        Triple(Routes.PROFILE, stringResource(R.string.tab_profile), Pair(Icons.Filled.Person, Icons.Outlined.Person))
    )

    fun navigateTopTab(route: String) {
        navController.navigate(route) {
            popUpTo(Routes.FEED) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (showBottomOrRail && !useRail) {
                    NavigationBar {
                        navItems.forEach { (route, label, icons) ->
                            val selected = currentRoute == route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (route == Routes.CREATE) preferredCreateFormat = CreateFormat.POST
                                    navigateTopTab(route)
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) icons.first else icons.second,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("nav_$route")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showBottomOrRail && useRail) {
                    NavigationRail {
                        navItems.forEach { (route, label, icons) ->
                            val selected = currentRoute == route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    if (route == Routes.CREATE) preferredCreateFormat = CreateFormat.POST
                                    navigateTopTab(route)
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) icons.first else icons.second,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = Routes.FEED,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(Routes.FEED) {
                        FeedScreen(
                            myProfile = myProfile,
                            postsState = postsState,
                            storiesState = storiesState,
                            activityState = activityState,
                            likedPostIds = likedPostIds,
                            savedPostIds = savedPostIds,
                            followingIds = followingIds,
                            videoFiles = videoFiles,
                            videoLoadingIds = videoLoadingIds,
                            isBusy = isBusy,
                            onRequestVideoLoad = { post -> viewModel.ensureVideoLoaded(post, context.cacheDir) },
                            onOpenMessages = { navController.navigate(Routes.MESSAGES) },
                            onOpenCreate = {
                                preferredCreateFormat = CreateFormat.POST
                                navigateTopTab(Routes.CREATE)
                            },
                            onOpenUserProfile = { uid ->
                                if (uid == myProfile.userId) {
                                    navigateTopTab(Routes.PROFILE)
                                } else {
                                    navController.navigate(Routes.userProfile(uid))
                                }
                            },
                            onFollowAuthor = { post ->
                                val target = allUsers.firstOrNull { it.userId == post.authorId }
                                    ?: UserProfile(
                                        userId = post.authorId,
                                        username = post.authorUsername,
                                        displayName = post.authorDisplayName,
                                        avatarBase64 = post.authorAvatarBase64
                                    )
                                viewModel.toggleFollow(target, myProfile)
                            },
                            onLikePost = { post -> viewModel.toggleLike(post, myProfile) },
                            onOpenComments = { post -> commentsPost = post },
                            onSharePost = { post -> sharePost = post },
                            onSavePost = { postId -> viewModel.toggleSave(postId) },
                            onDeletePost = { post -> viewModel.deletePost(post, myProfile.postsCount) },
                            onPublishStory = { uri, cap, aud, onDone ->
                                viewModel.publishStory(context, myProfile, uri, cap, aud, onDone)
                            }
                        )
                    }

                    composable(Routes.SEARCH) {
                        SearchExploreScreen(
                            currentUid = myProfile.userId,
                            usersState = usersState,
                            postsState = postsState,
                            onUserClick = { uid ->
                                if (uid == myProfile.userId) {
                                    navigateTopTab(Routes.PROFILE)
                                } else {
                                    navController.navigate(Routes.userProfile(uid))
                                }
                            },
                            onPostClick = { post -> commentsPost = post }
                        )
                    }

                    composable(Routes.CREATE) {
                        CreateScreen(
                            initialFormat = preferredCreateFormat,
                            isBusy = isBusy,
                            onPublishImagePost = { uri, caption, audience ->
                                viewModel.publishImagePost(context, myProfile, uri, caption, audience) {
                                    navigateTopTab(Routes.FEED)
                                }
                            },
                            onPublishVideoPost = { uri, caption, postType, audience ->
                                viewModel.publishVideoPost(context, myProfile, uri, caption, postType, audience) {
                                    if (postType == com.example.model.PostType.REEL) {
                                        navigateTopTab(Routes.REELS)
                                    } else {
                                        navigateTopTab(Routes.FEED)
                                    }
                                }
                            },
                            onPublishStory = { uri, caption, audience ->
                                viewModel.publishStory(context, myProfile, uri, caption, audience) {
                                    navigateTopTab(Routes.FEED)
                                }
                            }
                        )
                    }

                    composable(Routes.REELS) {
                        ReelsScreen(
                            currentUid = myProfile.userId,
                            postsState = postsState,
                            likedPostIds = likedPostIds,
                            followingIds = followingIds,
                            videoFiles = videoFiles,
                            videoLoadingIds = videoLoadingIds,
                            onRequestVideoLoad = { post -> viewModel.ensureVideoLoaded(post, context.cacheDir) },
                            onOpenCreateReel = {
                                preferredCreateFormat = CreateFormat.REEL
                                navigateTopTab(Routes.CREATE)
                            },
                            onOpenAuthor = { uid ->
                                if (uid == myProfile.userId) {
                                    navigateTopTab(Routes.PROFILE)
                                } else {
                                    navController.navigate(Routes.userProfile(uid))
                                }
                            },
                            onFollowAuthor = { post ->
                                val target = allUsers.firstOrNull { it.userId == post.authorId }
                                    ?: UserProfile(
                                        userId = post.authorId,
                                        username = post.authorUsername,
                                        displayName = post.authorDisplayName,
                                        avatarBase64 = post.authorAvatarBase64
                                    )
                                viewModel.toggleFollow(target, myProfile)
                            },
                            onLikePost = { post -> viewModel.toggleLike(post, myProfile) },
                            onOpenComments = { post -> commentsPost = post },
                            onSharePost = { post -> sharePost = post }
                        )
                    }

                    composable(Routes.PROFILE) {
                        ProfileScreen(
                            viewedProfile = myProfile,
                            myProfile = myProfile,
                            allUsers = allUsers,
                            postsState = postsState,
                            savedPostIds = savedPostIds,
                            likedPostIds = likedPostIds,
                            followingIds = followingIds,
                            closeFriendIds = closeFriendIds,
                            videoFiles = videoFiles,
                            videoLoadingIds = videoLoadingIds,
                            isBusy = isBusy,
                            showBackButton = false,
                            onBack = {},
                            onUpdateProfile = { name, handle, bio, uri, onDone ->
                                viewModel.updateProfile(
                                    context = context,
                                    displayName = name,
                                    username = handle,
                                    bio = bio,
                                    avatarUri = uri,
                                    existingAvatarBase64 = myProfile.avatarBase64,
                                    onDone = onDone
                                )
                            },
                            onToggleFollow = {},
                            onToggleCloseFriend = { target -> viewModel.toggleCloseFriend(target) },
                            onOpenMessageWithUser = { navController.navigate(Routes.MESSAGES) },
                            onRequestVideoLoad = { post -> viewModel.ensureVideoLoaded(post, context.cacheDir) },
                            onLikePost = { post -> viewModel.toggleLike(post, myProfile) },
                            onOpenComments = { post -> commentsPost = post },
                            onSharePost = { post -> sharePost = post },
                            onSavePost = { postId -> viewModel.toggleSave(postId) },
                            onDeletePost = { post -> viewModel.deletePost(post, myProfile.postsCount) },
                            onSignOut = onSignOut
                        )
                    }

                    composable(
                        route = Routes.USER_PROFILE,
                        arguments = listOf(navArgument("userId") { type = NavType.StringType })
                    ) { entry ->
                        val targetUid = entry.arguments?.getString("userId") ?: myProfile.userId
                        val targetUser = allUsers.firstOrNull { it.userId == targetUid } ?: myProfile
                        ProfileScreen(
                            viewedProfile = targetUser,
                            myProfile = myProfile,
                            allUsers = allUsers,
                            postsState = postsState,
                            savedPostIds = savedPostIds,
                            likedPostIds = likedPostIds,
                            followingIds = followingIds,
                            closeFriendIds = closeFriendIds,
                            videoFiles = videoFiles,
                            videoLoadingIds = videoLoadingIds,
                            isBusy = isBusy,
                            showBackButton = true,
                            onBack = { navController.popBackStack() },
                            onUpdateProfile = { _, _, _, _, _ -> },
                            onToggleFollow = { target -> viewModel.toggleFollow(target, myProfile) },
                            onToggleCloseFriend = { target -> viewModel.toggleCloseFriend(target) },
                            onOpenMessageWithUser = { navController.navigate(Routes.MESSAGES) },
                            onRequestVideoLoad = { post -> viewModel.ensureVideoLoaded(post, context.cacheDir) },
                            onLikePost = { post -> viewModel.toggleLike(post, myProfile) },
                            onOpenComments = { post -> commentsPost = post },
                            onSharePost = { post -> sharePost = post },
                            onSavePost = { postId -> viewModel.toggleSave(postId) },
                            onDeletePost = { },
                            onSignOut = {}
                        )
                    }

                    composable(Routes.MESSAGES) {
                        MessagesScreen(
                            myProfile = myProfile,
                            usersState = usersState,
                            notesState = notesState,
                            chatsState = chatsState,
                            chatIdFor = { otherId -> viewModel.chatIdFor(otherId) },
                            observeMessages = { chatId -> viewModel.observeMessages(chatId) },
                            onSendMessage = { otherId, txt ->
                                viewModel.sendMessage(otherId, txt, myProfile)
                            },
                            onUpsertNote = { txt, onDone -> viewModel.upsertNote(myProfile, txt, onDone) },
                            onDeleteNote = { onDone -> viewModel.deleteNote(onDone) },
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    commentsPost?.let { post ->
        CommentsBottomSheet(
            post = post,
            commentsFlow = viewModel.observeComments(post),
            onSendComment = { text -> viewModel.addComment(post, text, myProfile) },
            onDismiss = { commentsPost = null }
        )
    }

    sharePost?.let { post ->
        SharePostBottomSheet(
            post = post,
            users = allUsers,
            currentUid = myProfile.userId,
            onSendToUser = { recipient ->
                val preview = "Te compartí una publicación de @${post.authorUsername}: ${post.caption.take(60)}"
                viewModel.sendMessage(
                    otherUserId = recipient.userId,
                    text = preview,
                    currentProfile = myProfile,
                    sharedPostId = post.id,
                    onSent = {
                        sharePost = null
                        viewModel.showFeedback("Enviado a ${recipient.displayName}")
                    }
                )
            },
            onDismiss = { sharePost = null }
        )
    }
}
