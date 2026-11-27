package com.example.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MomentiaRepository
import com.example.model.ActivityItem
import com.example.model.AudienceType
import com.example.model.ChatMessage
import com.example.model.ChatThread
import com.example.model.Comment
import com.example.model.Note
import com.example.model.Post
import com.example.model.PostType
import com.example.model.Story
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.util.MediaUtils
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class MomentiaViewModel(
    private val repository: MomentiaRepository,
    val currentUserId: String
) : ViewModel() {

    private fun <T> Flow<T>.toUiStateFlow(tagLabel: String): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing $tagLabel", rootError)
                emit(UiState.Error(rootError.message ?: "Error al cargar $tagLabel"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    private fun Flow<Set<String>>.toIdSetStateFlow(tagLabel: String): StateFlow<Set<String>> =
        this.catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing $tagLabel", firestoreError ?: error)
            emit(emptySet())
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptySet()
        )

    val myProfileState: StateFlow<UiState<UserProfile?>> =
        repository.observeMyProfile(currentUserId).toUiStateFlow("perfil")

    val usersState: StateFlow<UiState<List<UserProfile>>> =
        repository.observeDiscoverableUsers(currentUserId).toUiStateFlow("usuarios")

    val postsState: StateFlow<UiState<List<Post>>> =
        repository.observeFeedPosts(currentUserId).toUiStateFlow("publicaciones")

    val storiesState: StateFlow<UiState<List<Story>>> =
        repository.observeStories(currentUserId).toUiStateFlow("historias")

    val notesState: StateFlow<UiState<List<Note>>> =
        repository.observeNotes(currentUserId).toUiStateFlow("notas")

    val activityState: StateFlow<UiState<List<ActivityItem>>> =
        repository.observeActivity(currentUserId).toUiStateFlow("actividad")

    val chatsState: StateFlow<UiState<List<ChatThread>>> =
        repository.observeChats(currentUserId).toUiStateFlow("mensajes")

    val likedPostIds: StateFlow<Set<String>> =
        repository.observeLikedPostIds(currentUserId).toIdSetStateFlow("likes")

    val savedPostIds: StateFlow<Set<String>> =
        repository.observeSavedPostIds(currentUserId).toIdSetStateFlow("guardados")

    val followingIds: StateFlow<Set<String>> =
        repository.observeFollowingIds(currentUserId).toIdSetStateFlow("siguiendo")

    val closeFriendIds: StateFlow<Set<String>> =
        repository.observeCloseFriendIds(currentUserId).toIdSetStateFlow("mejores amigos")

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    private val _videoFiles = MutableStateFlow<Map<String, File>>(emptyMap())
    val videoFiles: StateFlow<Map<String, File>> = _videoFiles.asStateFlow()

    private val _videoLoadingIds = MutableStateFlow<Set<String>>(emptySet())
    val videoLoadingIds: StateFlow<Set<String>> = _videoLoadingIds.asStateFlow()

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    fun showFeedback(msg: String) {
        _feedbackMessage.value = msg
    }

    fun registerProfile(
        context: Context,
        displayName: String,
        username: String,
        bio: String,
        avatarUri: Uri?,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val avatarB64 = if (avatarUri != null) {
                MediaUtils.compressImageToBase64(context, avatarUri, maxDimension = 360, maxBase64Chars = 250_000)
                    .getOrNull()
            } else null

            val result = repository.createProfile(displayName, username, bio, avatarB64)
            _isBusy.value = false
            result.onSuccess {
                onDone()
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "No se pudo crear el perfil."
            }
        }
    }

    fun updateProfile(
        context: Context,
        displayName: String,
        username: String,
        bio: String,
        avatarUri: Uri?,
        existingAvatarBase64: String?,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val avatarB64 = if (avatarUri != null) {
                MediaUtils.compressImageToBase64(context, avatarUri, maxDimension = 360, maxBase64Chars = 250_000)
                    .getOrNull() ?: existingAvatarBase64
            } else existingAvatarBase64

            val result = repository.updateProfile(displayName, username, bio, avatarB64)
            _isBusy.value = false
            result.onSuccess {
                _feedbackMessage.value = "Perfil actualizado"
                onDone()
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "No se pudo actualizar el perfil."
            }
        }
    }

    fun publishImagePost(
        context: Context,
        profile: UserProfile,
        uri: Uri,
        caption: String,
        audience: AudienceType,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val compressed = MediaUtils.compressImageToBase64(context, uri)
            compressed.onFailure { err ->
                _isBusy.value = false
                _feedbackMessage.value = err.localizedMessage ?: "Error al procesar la foto."
                return@launch
            }
            val b64 = compressed.getOrThrow()
            val result = repository.publishImagePost(
                profile = profile,
                caption = caption,
                imageBase64 = b64,
                audience = audience,
                closeFriendUids = closeFriendIds.value.toList()
            )
            _isBusy.value = false
            result.onSuccess {
                _feedbackMessage.value = "Publicación compartida en línea"
                onDone()
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "No se pudo publicar."
            }
        }
    }

    fun publishVideoPost(
        context: Context,
        profile: UserProfile,
        uri: Uri,
        caption: String,
        postType: PostType,
        audience: AudienceType,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val prepared = MediaUtils.prepareVideoChunks(context, uri)
            prepared.onFailure { err ->
                _isBusy.value = false
                _feedbackMessage.value = err.localizedMessage ?: "Error al procesar el video."
                return@launch
            }
            val result = repository.publishVideoPost(
                profile = profile,
                caption = caption,
                preparedVideo = prepared.getOrThrow(),
                postType = postType,
                audience = audience,
                closeFriendUids = closeFriendIds.value.toList()
            )
            _isBusy.value = false
            result.onSuccess {
                _feedbackMessage.value = if (postType == PostType.REEL) "Reel publicado en línea" else "Video publicado en línea"
                onDone()
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "No se pudo subir el video."
            }
        }
    }

    fun publishStory(
        context: Context,
        profile: UserProfile,
        uri: Uri,
        caption: String?,
        audience: AudienceType,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val compressed = MediaUtils.compressImageToBase64(context, uri)
            compressed.onFailure { err ->
                _isBusy.value = false
                _feedbackMessage.value = err.localizedMessage ?: "Error al procesar la historia."
                return@launch
            }
            val result = repository.publishStory(
                profile = profile,
                imageBase64 = compressed.getOrThrow(),
                caption = caption,
                audience = audience,
                closeFriendUids = closeFriendIds.value.toList()
            )
            _isBusy.value = false
            result.onSuccess {
                _feedbackMessage.value = "Historia publicada por 24h"
                onDone()
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "No se pudo subir la historia."
            }
        }
    }

    fun ensureVideoLoaded(post: Post, cacheDir: File) {
        if (post.chunkCount <= 0L) return
        if (_videoFiles.value.containsKey(post.id) || _videoLoadingIds.value.contains(post.id)) return
        viewModelScope.launch {
            _videoLoadingIds.value = _videoLoadingIds.value + post.id
            val result = repository.downloadVideoToCache(post, cacheDir)
            result.onSuccess { file ->
                _videoFiles.value = _videoFiles.value + (post.id to file)
            }.onFailure { err ->
                Log.w(TAG, "Failed downloading video ${post.id}", err)
            }
            _videoLoadingIds.value = _videoLoadingIds.value - post.id
        }
    }

    fun toggleLike(post: Post, profile: UserProfile) {
        viewModelScope.launch {
            repository.toggleLike(post, profile).onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "No se pudo actualizar Me gusta."
            }
        }
    }

    fun toggleSave(postId: String) {
        viewModelScope.launch {
            repository.toggleSave(postId).onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "No se pudo guardar."
            }
        }
    }

    fun deletePost(post: Post, currentPostsCount: Long) {
        viewModelScope.launch {
            repository.deletePost(post, currentPostsCount).onSuccess {
                _feedbackMessage.value = "Publicación eliminada"
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "No se pudo eliminar."
            }
        }
    }

    fun toggleFollow(targetUser: UserProfile, currentProfile: UserProfile) {
        viewModelScope.launch {
            repository.toggleFollow(targetUser, currentProfile).onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "No se pudo actualizar seguimiento."
            }
        }
    }

    fun toggleCloseFriend(targetUser: UserProfile) {
        viewModelScope.launch {
            repository.toggleCloseFriend(targetUser).onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "No se pudo actualizar Mejores amigos."
            }
        }
    }

    fun observeComments(post: Post): Flow<List<Comment>> =
        repository.observeComments(post, currentUserId).catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            emit(emptyList())
        }

    fun addComment(post: Post, text: String, profile: UserProfile) {
        viewModelScope.launch {
            repository.addComment(post, text, profile).onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "No se pudo publicar el comentario."
            }
        }
    }

    fun upsertNote(profile: UserProfile, text: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.upsertNote(profile, text).onSuccess {
                _feedbackMessage.value = "Nota publicada"
                onDone()
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "No se pudo guardar tu nota."
            }
        }
    }

    fun deleteNote(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteNote().onSuccess {
                _feedbackMessage.value = "Nota eliminada"
                onDone()
            }
        }
    }

    fun chatIdFor(otherUid: String): String = repository.chatIdFor(currentUserId, otherUid)

    fun observeMessages(chatId: String): Flow<List<ChatMessage>> =
        repository.observeMessages(chatId, currentUserId).catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            emit(emptyList())
        }

    fun sendMessage(
        otherUserId: String,
        text: String,
        currentProfile: UserProfile,
        sharedPostId: String? = null,
        onSent: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.sendMessage(otherUserId, text, currentProfile, sharedPostId).onSuccess {
                onSent()
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "No se pudo enviar el mensaje."
            }
        }
    }

    private companion object {
        const val TAG = "MomentiaVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
