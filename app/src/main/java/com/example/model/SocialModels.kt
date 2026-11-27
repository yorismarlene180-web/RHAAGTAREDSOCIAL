package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class MediaType {
    IMAGE, VIDEO;
    companion object {
        fun fromRaw(raw: String?): MediaType =
            entries.firstOrNull { it.name == raw } ?: IMAGE
    }
}

enum class PostType {
    POST, REEL;
    companion object {
        fun fromRaw(raw: String?): PostType =
            entries.firstOrNull { it.name == raw } ?: POST
    }
}

enum class AudienceType {
    PUBLIC, CLOSE_FRIENDS;
    companion object {
        fun fromRaw(raw: String?): AudienceType =
            entries.firstOrNull { it.name == raw } ?: PUBLIC
    }
}

enum class ActivityActionType {
    LIKE, COMMENT, FOLLOW;
    companion object {
        fun fromRaw(raw: String?): ActivityActionType =
            entries.firstOrNull { it.name == raw } ?: LIKE
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    val bio: String = "",
    val avatarBase64: String? = null,
    val discoverable: Boolean = true,
    val followersCount: Long = 0L,
    val followingCount: Long = 0L,
    val postsCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Post(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorUsername: String = "",
    val authorDisplayName: String = "",
    val authorAvatarBase64: String? = null,
    val caption: String = "",
    val mediaType: String = MediaType.IMAGE.name,
    val postType: String = PostType.POST.name,
    val audience: String = AudienceType.PUBLIC.name,
    val visibleToUids: List<String> = emptyList(),
    val mediaBase64: String? = null,
    val thumbnailBase64: String? = null,
    val chunkCount: Long = 0L,
    val likesCount: Long = 0L,
    val commentsCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Comment(
    @DocumentId val id: String = "",
    val postId: String = "",
    val authorId: String = "",
    val authorUsername: String = "",
    val authorAvatarBase64: String? = null,
    val text: String = "",
    val audience: String = AudienceType.PUBLIC.name,
    val visibleToUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Story(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorUsername: String = "",
    val authorDisplayName: String = "",
    val authorAvatarBase64: String? = null,
    val mediaBase64: String = "",
    val caption: String? = null,
    val audience: String = AudienceType.PUBLIC.name,
    val visibleToUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Note(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val avatarBase64: String? = null,
    val text: String = "",
    val discoverable: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class RelationRef(
    @DocumentId val id: String = "",
    val userId: String = "",
    val targetId: String = "",
    val username: String? = null,
    val displayName: String? = null,
    val avatarBase64: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ActivityItem(
    @DocumentId val id: String = "",
    val targetUserId: String = "",
    val actorId: String = "",
    val actorUsername: String = "",
    val actorAvatarBase64: String? = null,
    val actionType: String = ActivityActionType.LIKE.name,
    val postId: String? = null,
    val previewText: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatThread(
    @DocumentId val id: String = "",
    val participantIds: List<String> = emptyList(),
    val lastMessageText: String = "",
    val lastSenderId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val senderUsername: String = "",
    val participantIds: List<String> = emptyList(),
    val text: String = "",
    val sharedPostId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
