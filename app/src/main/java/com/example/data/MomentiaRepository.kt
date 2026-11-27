package com.example.data

import android.content.Context
import com.example.R
import com.example.model.ActivityActionType
import com.example.model.ActivityItem
import com.example.model.AudienceType
import com.example.model.ChatMessage
import com.example.model.ChatThread
import com.example.model.Comment
import com.example.model.MediaType
import com.example.model.Note
import com.example.model.Post
import com.example.model.PostType
import com.example.model.RelationRef
import com.example.model.Story
import com.example.model.UserProfile
import com.example.util.MediaUtils
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.io.File

class MomentiaRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Debes iniciar sesión con Google antes de acceder a Momentia.")
    }

    private fun <T> wrapFirestoreStream(
        uid: String,
        path: String,
        operationType: OperationType = OperationType.LIST,
        source: Flow<T>
    ): Flow<T> = source.catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
        throw error
    }

    // 1. User Profile & Directory
    fun observeMyProfile(uid: String): Flow<UserProfile?> = flow {
        val activeUid = requireUserId()
        check(activeUid == uid)
        val path = "users/$uid"
        emitAll(
            wrapFirestoreStream(
                uid = uid,
                path = path,
                operationType = OperationType.GET,
                source = db.collection("users").document(uid)
                    .snapshots()
                    .map { snapshot ->
                        if (snapshot.exists()) {
                            snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        } else {
                            null
                        }
                    }
            )
        )
    }

    fun observeDiscoverableUsers(uid: String): Flow<List<UserProfile>> = flow {
        requireUserId()
        val path = "users"
        emitAll(
            wrapFirestoreStream(
                uid = uid,
                path = path,
                source = db.collection("users")
                    .whereEqualTo("discoverable", true)
                    .snapshots()
                    .map { snap ->
                        snap.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            .sortedByDescending { it.followersCount }
                    }
            )
        )
    }

    suspend fun createProfile(
        displayName: String,
        rawUsername: String,
        bio: String,
        avatarBase64: String?
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val cleanDisplay = displayName.trim().take(50)
            require(cleanDisplay.isNotEmpty()) { "Ingresa tu nombre para registrarte." }

            val sanitizedHandle = rawUsername.trim()
                .lowercase()
                .replace(Regex("[^a-z0-9._]"), "_")
                .trim('_')
                .ifBlank {
                    cleanDisplay.lowercase().replace(Regex("[^a-z0-9]"), "_").trim('_')
                }
                .let { base ->
                    when {
                        base.length < 3 -> (base + "_user").take(30)
                        base.length > 30 -> base.take(30)
                        else -> base
                    }
                }
            val cleanBio = bio.trim().take(160)

            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "username" to sanitizedHandle,
                "displayName" to cleanDisplay,
                "bio" to cleanBio,
                "discoverable" to true,
                "followersCount" to 0L,
                "followingCount" to 0L,
                "postsCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!avatarBase64.isNullOrBlank() && avatarBase64.length <= 400_000) {
                payload["avatarBase64"] = avatarBase64
            }
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateProfile(
        displayName: String,
        rawUsername: String,
        bio: String,
        avatarBase64: String?
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val cleanDisplay = displayName.trim().take(50)
            require(cleanDisplay.isNotEmpty()) { "El nombre no puede estar vacío." }
            val cleanUser = rawUsername.trim().lowercase().replace(Regex("[^a-z0-9._]"), "_").take(30)
            require(cleanUser.length >= 3) { "El usuario debe tener al menos 3 caracteres." }
            val cleanBio = bio.trim().take(160)

            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            val updates = mutableMapOf<String, Any>(
                "displayName" to cleanDisplay,
                "username" to cleanUser,
                "bio" to cleanBio,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!avatarBase64.isNullOrBlank() && avatarBase64.length <= 400_000) {
                updates["avatarBase64"] = avatarBase64
            }
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    // 2. Posts & Reels Feed (real users only, aligned with allow list)
    fun observeFeedPosts(uid: String): Flow<List<Post>> = flow {
        requireUserId()
        val publicFlow = db.collection("posts")
            .whereEqualTo("audience", AudienceType.PUBLIC.name)
            .snapshots()
            .map { it.toObjects(Post::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

        val visibleFlow = db.collection("posts")
            .whereArrayContains("visibleToUids", uid)
            .snapshots()
            .map { it.toObjects(Post::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

        val merged = combine(publicFlow, visibleFlow) { pubPosts, privPosts ->
            (pubPosts + privPosts)
                .distinctBy { it.id }
                .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
        }
        emitAll(wrapFirestoreStream(uid = uid, path = "posts", source = merged))
    }

    suspend fun publishImagePost(
        profile: UserProfile,
        caption: String,
        imageBase64: String,
        audience: AudienceType,
        closeFriendUids: List<String>
    ): Result<Unit> {
        var docPath = "posts"
        return try {
            val uid = requireUserId()
            require(imageBase64.isNotBlank() && imageBase64.length <= 750_000) {
                "La imagen supera el tamaño permitido."
            }
            val visibleTo = if (audience == AudienceType.CLOSE_FRIENDS) {
                (listOf(uid) + closeFriendUids).distinct().take(100)
            } else {
                listOf(uid)
            }

            val postRef = db.collection("posts").document()
            docPath = postRef.path
            val batch = db.batch()
            val payload = mutableMapOf<String, Any>(
                "authorId" to uid,
                "authorUsername" to profile.username,
                "authorDisplayName" to profile.displayName,
                "caption" to caption.trim().take(500),
                "mediaType" to MediaType.IMAGE.name,
                "postType" to PostType.POST.name,
                "audience" to audience.name,
                "visibleToUids" to visibleTo,
                "mediaBase64" to imageBase64,
                "chunkCount" to 0L,
                "likesCount" to 0L,
                "commentsCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!profile.avatarBase64.isNullOrBlank()) {
                payload["authorAvatarBase64"] = profile.avatarBase64
            }
            batch.set(postRef, payload)
            batch.update(
                db.collection("users").document(uid),
                mapOf(
                    "postsCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun publishVideoPost(
        profile: UserProfile,
        caption: String,
        preparedVideo: MediaUtils.PreparedVideo,
        postType: PostType,
        audience: AudienceType,
        closeFriendUids: List<String>
    ): Result<Unit> {
        var docPath = "posts"
        return try {
            val uid = requireUserId()
            require(preparedVideo.chunksBase64.isNotEmpty() && preparedVideo.chunksBase64.size <= 20) {
                "El video debe tener entre 1 y 20 fragmentos."
            }
            val visibleTo = if (audience == AudienceType.CLOSE_FRIENDS) {
                (listOf(uid) + closeFriendUids).distinct().take(100)
            } else {
                listOf(uid)
            }

            val postRef = db.collection("posts").document()
            docPath = postRef.path
            val batch = db.batch()
            val payload = mutableMapOf<String, Any>(
                "authorId" to uid,
                "authorUsername" to profile.username,
                "authorDisplayName" to profile.displayName,
                "caption" to caption.trim().take(500),
                "mediaType" to MediaType.VIDEO.name,
                "postType" to postType.name,
                "audience" to audience.name,
                "visibleToUids" to visibleTo,
                "chunkCount" to preparedVideo.chunksBase64.size.toLong(),
                "likesCount" to 0L,
                "commentsCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!profile.avatarBase64.isNullOrBlank()) {
                payload["authorAvatarBase64"] = profile.avatarBase64
            }
            if (!preparedVideo.thumbnailBase64.isNullOrBlank() && preparedVideo.thumbnailBase64.length <= 400_000) {
                payload["thumbnailBase64"] = preparedVideo.thumbnailBase64
            }
            batch.set(postRef, payload)

            preparedVideo.chunksBase64.forEachIndexed { index, chunkB64 ->
                val chunkRef = postRef.collection("chunks").document("chunk_$index")
                batch.set(
                    chunkRef,
                    mapOf(
                        "authorId" to uid,
                        "audience" to audience.name,
                        "visibleToUids" to visibleTo,
                        "chunkIndex" to index.toLong(),
                        "dataBase64" to chunkB64,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.update(
                db.collection("users").document(uid),
                mapOf(
                    "postsCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun downloadVideoToCache(post: Post, cacheDir: File): Result<File> {
        val path = "posts/${post.id}/chunks"
        return try {
            val uid = requireUserId()
            val cached = File(File(cacheDir, "momentia_videos"), "${post.id}.mp4")
            if (cached.exists() && cached.length() > 0L) {
                return Result.success(cached)
            }
            val query = if (post.audience == AudienceType.PUBLIC.name) {
                db.collection("posts").document(post.id).collection("chunks")
                    .whereEqualTo("audience", AudienceType.PUBLIC.name)
            } else {
                db.collection("posts").document(post.id).collection("chunks")
                    .whereArrayContains("visibleToUids", uid)
            }
            val snap = query.get().await()
            val sortedChunks = snap.documents
                .sortedBy { it.getLong("chunkIndex") ?: 0L }
                .mapNotNull { it.getString("dataBase64") }
            require(sortedChunks.isNotEmpty()) { "No se encontraron fragmentos del video." }
            val file = MediaUtils.writeChunksToCacheFile(cacheDir, post.id, sortedChunks)
            Result.success(file)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun deletePost(post: Post, currentPostsCount: Long): Result<Unit> {
        val path = "posts/${post.id}"
        return try {
            val uid = requireUserId()
            check(post.authorId == uid)
            val postRef = db.collection("posts").document(post.id)

            if (post.chunkCount > 0L) {
                val chunks = postRef.collection("chunks").whereArrayContains("visibleToUids", uid).get().await()
                val chunkBatch = db.batch()
                chunks.documents.forEach { chunkBatch.delete(it.reference) }
                chunkBatch.commit().await()
            }

            val batch = db.batch()
            batch.delete(postRef)
            if (currentPostsCount > 0L) {
                batch.update(
                    db.collection("users").document(uid),
                    mapOf(
                        "postsCount" to FieldValue.increment(-1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // 3. Likes, Saves, Comments
    fun observeLikedPostIds(uid: String): Flow<Set<String>> = flow {
        requireUserId()
        val path = "users/$uid/likedPosts"
        emitAll(
            wrapFirestoreStream(
                uid = uid,
                path = path,
                source = db.collection("users").document(uid).collection("likedPosts")
                    .snapshots()
                    .map { snap -> snap.documents.map { it.id }.toSet() }
            )
        )
    }

    fun observeSavedPostIds(uid: String): Flow<Set<String>> = flow {
        requireUserId()
        val path = "users/$uid/saved"
        emitAll(
            wrapFirestoreStream(
                uid = uid,
                path = path,
                source = db.collection("users").document(uid).collection("saved")
                    .snapshots()
                    .map { snap -> snap.documents.map { it.id }.toSet() }
            )
        )
    }

    suspend fun toggleLike(post: Post, profile: UserProfile): Result<Unit> {
        val path = "posts/${post.id}/likes/${profile.userId}"
        return try {
            val uid = requireUserId()
            val likeRef = db.collection("posts").document(post.id).collection("likes").document(uid)
            val userLikedRef = db.collection("users").document(uid).collection("likedPosts").document(post.id)
            val postRef = db.collection("posts").document(post.id)

            val alreadyLiked = likeRef.get().await().exists()
            val batch = db.batch()
            if (alreadyLiked) {
                batch.delete(likeRef)
                batch.delete(userLikedRef)
                batch.update(
                    postRef,
                    mapOf(
                        "likesCount" to FieldValue.increment(-1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.set(
                    likeRef,
                    mapOf(
                        "userId" to uid,
                        "username" to profile.username,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.set(
                    userLikedRef,
                    mapOf(
                        "userId" to uid,
                        "targetId" to post.id,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.update(
                    postRef,
                    mapOf(
                        "likesCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                if (post.authorId != uid) {
                    val actRef = db.collection("users").document(post.authorId).collection("activity").document()
                    val actData = mutableMapOf<String, Any>(
                        "targetUserId" to post.authorId,
                        "actorId" to uid,
                        "actorUsername" to profile.username,
                        "actionType" to ActivityActionType.LIKE.name,
                        "postId" to post.id,
                        "previewText" to post.caption.take(80),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    if (!profile.avatarBase64.isNullOrBlank()) {
                        actData["actorAvatarBase64"] = profile.avatarBase64
                    }
                    batch.set(actRef, actData)
                }
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleSave(postId: String): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/saved/$postId"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("saved").document(postId)
            path = ref.path
            val exists = ref.get().await().exists()
            if (exists) {
                ref.delete().await()
            } else {
                ref.set(
                    mapOf(
                        "userId" to uid,
                        "targetId" to postId,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeComments(post: Post, uid: String): Flow<List<Comment>> = flow {
        requireUserId()
        val path = "posts/${post.id}/comments"
        val query = if (post.audience == AudienceType.PUBLIC.name) {
            db.collection("posts").document(post.id).collection("comments")
                .whereEqualTo("audience", AudienceType.PUBLIC.name)
        } else {
            db.collection("posts").document(post.id).collection("comments")
                .whereArrayContains("visibleToUids", uid)
        }
        emitAll(
            wrapFirestoreStream(
                uid = uid,
                path = path,
                source = query.snapshots().map { snap ->
                    snap.toObjects(Comment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
            )
        )
    }

    suspend fun addComment(post: Post, text: String, profile: UserProfile): Result<Unit> {
        val path = "posts/${post.id}/comments"
        return try {
            val uid = requireUserId()
            val cleanText = text.trim().take(300)
            require(cleanText.isNotEmpty()) { "Escribe un comentario." }

            val commentRef = db.collection("posts").document(post.id).collection("comments").document()
            val postRef = db.collection("posts").document(post.id)
            val batch = db.batch()
            val commentMap = mutableMapOf<String, Any>(
                "postId" to post.id,
                "authorId" to uid,
                "authorUsername" to profile.username,
                "text" to cleanText,
                "audience" to post.audience,
                "visibleToUids" to post.visibleToUids.ifEmpty { listOf(post.authorId) },
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (!profile.avatarBase64.isNullOrBlank()) {
                commentMap["authorAvatarBase64"] = profile.avatarBase64
            }
            batch.set(commentRef, commentMap)
            batch.update(
                postRef,
                mapOf(
                    "commentsCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (post.authorId != uid) {
                val actRef = db.collection("users").document(post.authorId).collection("activity").document()
                val actData = mutableMapOf<String, Any>(
                    "targetUserId" to post.authorId,
                    "actorId" to uid,
                    "actorUsername" to profile.username,
                    "actionType" to ActivityActionType.COMMENT.name,
                    "postId" to post.id,
                    "previewText" to cleanText.take(100),
                    "createdAt" to FieldValue.serverTimestamp()
                )
                if (!profile.avatarBase64.isNullOrBlank()) {
                    actData["actorAvatarBase64"] = profile.avatarBase64
                }
                batch.set(actRef, actData)
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // 4. Following & Close Friends
    fun observeFollowingIds(uid: String): Flow<Set<String>> = flow {
        requireUserId()
        emitAll(
            wrapFirestoreStream(
                uid = uid,
                path = "users/$uid/following",
                source = db.collection("users").document(uid).collection("following")
                    .snapshots()
                    .map { snap -> snap.documents.map { it.id }.toSet() }
            )
        )
    }

    fun observeCloseFriendIds(uid: String): Flow<Set<String>> = flow {
        requireUserId()
        emitAll(
            wrapFirestoreStream(
                uid = uid,
                path = "users/$uid/closeFriends",
                source = db.collection("users").document(uid).collection("closeFriends")
                    .snapshots()
                    .map { snap -> snap.documents.map { it.id }.toSet() }
            )
        )
    }

    suspend fun toggleFollow(targetUser: UserProfile, currentProfile: UserProfile): Result<Unit> {
        val path = "users/${currentProfile.userId}/following/${targetUser.userId}"
        return try {
            val uid = requireUserId()
            require(targetUser.userId != uid)
            val followingRef = db.collection("users").document(uid).collection("following").document(targetUser.userId)
            val followerRef = db.collection("users").document(targetUser.userId).collection("followers").document(uid)
            val myUserRef = db.collection("users").document(uid)
            val targetUserRef = db.collection("users").document(targetUser.userId)

            val isFollowing = followingRef.get().await().exists()
            val batch = db.batch()
            if (isFollowing) {
                batch.delete(followingRef)
                batch.delete(followerRef)
                batch.update(
                    myUserRef,
                    mapOf("followingCount" to FieldValue.increment(-1L), "updatedAt" to FieldValue.serverTimestamp())
                )
                batch.update(
                    targetUserRef,
                    mapOf("followersCount" to FieldValue.increment(-1L), "updatedAt" to FieldValue.serverTimestamp())
                )
            } else {
                val followingMap = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "targetId" to targetUser.userId,
                    "username" to targetUser.username,
                    "displayName" to targetUser.displayName,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                if (!targetUser.avatarBase64.isNullOrBlank()) followingMap["avatarBase64"] = targetUser.avatarBase64

                val followerMap = mutableMapOf<String, Any>(
                    "userId" to targetUser.userId,
                    "targetId" to uid,
                    "username" to currentProfile.username,
                    "displayName" to currentProfile.displayName,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                if (!currentProfile.avatarBase64.isNullOrBlank()) followerMap["avatarBase64"] = currentProfile.avatarBase64

                batch.set(followingRef, followingMap)
                batch.set(followerRef, followerMap)
                batch.update(
                    myUserRef,
                    mapOf("followingCount" to FieldValue.increment(1L), "updatedAt" to FieldValue.serverTimestamp())
                )
                batch.update(
                    targetUserRef,
                    mapOf("followersCount" to FieldValue.increment(1L), "updatedAt" to FieldValue.serverTimestamp())
                )

                val actRef = db.collection("users").document(targetUser.userId).collection("activity").document()
                val actData = mutableMapOf<String, Any>(
                    "targetUserId" to targetUser.userId,
                    "actorId" to uid,
                    "actorUsername" to currentProfile.username,
                    "actionType" to ActivityActionType.FOLLOW.name,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                if (!currentProfile.avatarBase64.isNullOrBlank()) actData["actorAvatarBase64"] = currentProfile.avatarBase64
                batch.set(actRef, actData)
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleCloseFriend(targetUser: UserProfile): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/closeFriends/${targetUser.userId}"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("closeFriends").document(targetUser.userId)
            path = ref.path
            if (ref.get().await().exists()) {
                ref.delete().await()
            } else {
                val data = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "targetId" to targetUser.userId,
                    "username" to targetUser.username,
                    "displayName" to targetUser.displayName,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                if (!targetUser.avatarBase64.isNullOrBlank()) data["avatarBase64"] = targetUser.avatarBase64
                ref.set(data).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // 5. Stories (24h active)
    fun observeStories(uid: String): Flow<List<Story>> = flow {
        requireUserId()
        val publicFlow = db.collection("stories")
            .whereEqualTo("audience", AudienceType.PUBLIC.name)
            .snapshots()
            .map { it.toObjects(Story::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

        val visibleFlow = db.collection("stories")
            .whereArrayContains("visibleToUids", uid)
            .snapshots()
            .map { it.toObjects(Story::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

        val cutoffMs = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        val merged = combine(publicFlow, visibleFlow) { pub, priv ->
            (pub + priv)
                .distinctBy { it.id }
                .filter { story ->
                    val ts = story.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                    ts >= cutoffMs
                }
                .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
        }
        emitAll(wrapFirestoreStream(uid = uid, path = "stories", source = merged))
    }

    suspend fun publishStory(
        profile: UserProfile,
        imageBase64: String,
        caption: String?,
        audience: AudienceType,
        closeFriendUids: List<String>
    ): Result<Unit> {
        var path = "stories"
        return try {
            val uid = requireUserId()
            require(imageBase64.isNotBlank() && imageBase64.length <= 750_000) {
                "La imagen de la historia supera el tamaño permitido."
            }
            val visibleTo = if (audience == AudienceType.CLOSE_FRIENDS) {
                (listOf(uid) + closeFriendUids).distinct().take(100)
            } else {
                listOf(uid)
            }
            val ref = db.collection("stories").document()
            path = ref.path
            val data = mutableMapOf<String, Any>(
                "authorId" to uid,
                "authorUsername" to profile.username,
                "authorDisplayName" to profile.displayName,
                "mediaBase64" to imageBase64,
                "audience" to audience.name,
                "visibleToUids" to visibleTo,
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (!profile.avatarBase64.isNullOrBlank()) data["authorAvatarBase64"] = profile.avatarBase64
            val cleanCap = caption?.trim()?.take(200)
            if (!cleanCap.isNullOrBlank()) data["caption"] = cleanCap
            ref.set(data).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // 6. Notes (Anota lo que estás pensando)
    fun observeNotes(uid: String): Flow<List<Note>> = flow {
        requireUserId()
        emitAll(
            wrapFirestoreStream(
                uid = uid,
                path = "notes",
                source = db.collection("notes")
                    .whereEqualTo("discoverable", true)
                    .snapshots()
                    .map { snap ->
                        snap.toObjects(Note::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            .sortedByDescending { it.updatedAt ?: Timestamp(0, 0) }
                    }
            )
        )
    }

    suspend fun upsertNote(profile: UserProfile, text: String): Result<Unit> {
        val path = "notes/${profile.userId}"
        return try {
            val uid = requireUserId()
            val clean = text.trim().take(60)
            require(clean.isNotEmpty()) { "Escribe una nota breve." }
            val ref = db.collection("notes").document(uid)
            val existing = ref.get().await().exists()
            if (existing) {
                val updates = mutableMapOf<String, Any>(
                    "username" to profile.username,
                    "text" to clean,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!profile.avatarBase64.isNullOrBlank()) updates["avatarBase64"] = profile.avatarBase64
                ref.update(updates).await()
            } else {
                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "username" to profile.username,
                    "text" to clean,
                    "discoverable" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!profile.avatarBase64.isNullOrBlank()) payload["avatarBase64"] = profile.avatarBase64
                ref.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteNote(): Result<Unit> {
        var path = "notes/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val ref = db.collection("notes").document(uid)
            path = ref.path
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // 7. Activity Notifications
    fun observeActivity(uid: String): Flow<List<ActivityItem>> = flow {
        requireUserId()
        emitAll(
            wrapFirestoreStream(
                uid = uid,
                path = "users/$uid/activity",
                source = db.collection("users").document(uid).collection("activity")
                    .snapshots()
                    .map { snap ->
                        snap.toObjects(ActivityItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                    }
            )
        )
    }

    // 8. Direct Messages
    fun chatIdFor(uidA: String, uidB: String): String =
        if (uidA <= uidB) "${uidA}_${uidB}" else "${uidB}_${uidA}"

    fun observeChats(uid: String): Flow<List<ChatThread>> = flow {
        requireUserId()
        emitAll(
            wrapFirestoreStream(
                uid = uid,
                path = "chats",
                source = db.collection("chats")
                    .whereArrayContains("participantIds", uid)
                    .snapshots()
                    .map { snap ->
                        snap.toObjects(ChatThread::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            .sortedByDescending { it.updatedAt ?: Timestamp(0, 0) }
                    }
            )
        )
    }

    fun observeMessages(chatId: String, uid: String): Flow<List<ChatMessage>> = flow {
        requireUserId()
        emitAll(
            wrapFirestoreStream(
                uid = uid,
                path = "chats/$chatId/messages",
                source = db.collection("chats").document(chatId).collection("messages")
                    .whereArrayContains("participantIds", uid)
                    .snapshots()
                    .map { snap ->
                        snap.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            .sortedBy { it.createdAt ?: Timestamp(0, 0) }
                    }
            )
        )
    }

    suspend fun sendMessage(
        otherUserId: String,
        text: String,
        currentProfile: UserProfile,
        sharedPostId: String? = null
    ): Result<Unit> {
        val chatId = chatIdFor(currentProfile.userId, otherUserId)
        val path = "chats/$chatId/messages"
        return try {
            val uid = requireUserId()
            require(otherUserId != uid)
            val cleanText = text.trim().take(500)
            require(cleanText.isNotEmpty()) { "Escribe un mensaje." }

            val participants = listOf(uid, otherUserId).sorted()
            val chatRef = db.collection("chats").document(chatId)
            val msgRef = chatRef.collection("messages").document()
            val chatExists = chatRef.get().await().exists()

            val batch = db.batch()
            if (chatExists) {
                batch.update(
                    chatRef,
                    mapOf(
                        "lastMessageText" to cleanText,
                        "lastSenderId" to uid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.set(
                    chatRef,
                    mapOf(
                        "participantIds" to participants,
                        "lastMessageText" to cleanText,
                        "lastSenderId" to uid,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            val msgMap = mutableMapOf<String, Any>(
                "chatId" to chatId,
                "senderId" to uid,
                "senderUsername" to currentProfile.username,
                "participantIds" to participants,
                "text" to cleanText,
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (!sharedPostId.isNullOrBlank()) {
                msgMap["sharedPostId"] = sharedPostId
            }
            batch.set(msgRef, msgMap)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }
}
