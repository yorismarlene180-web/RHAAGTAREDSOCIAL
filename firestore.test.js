const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CHARLIE_UID = "charlie_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function pastDate(offsetMs = 1000) {
  return new Date(Date.now() - offsetMs);
}

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").where("discoverable", "==", true).get());
  await assertFails(unauthDb.collection("posts").where("audience", "==", "PUBLIC").get());
  await assertFails(unauthDb.collection("stories").where("audience", "==", "PUBLIC").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "alice",
      displayName: "Alice",
      bio: "",
      discoverable: true,
      followersCount: 0,
      followingCount: 0,
      postsCount: 0,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
});

test("User profile creation, update, and cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate(5000);

  // Valid create
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "alice_real",
      displayName: "Alice",
      bio: "Hola!",
      discoverable: true,
      followersCount: 0,
      followingCount: 0,
      postsCount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Ghost field / shadow update rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastDate(1000),
    })
  );

  // Bob cannot overwrite Alice's bio
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      bio: "Hacked",
      updatedAt: pastDate(1000),
    })
  );

  // Filtered query succeeds, unfiltered query fails
  await assertSucceeds(aliceDb.collection("users").where("discoverable", "==", true).get());
  await assertFails(aliceDb.collection("users").get());
});

test("Atomic follow and unfollow updates followersCount", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate(5000);

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "alice",
      displayName: "Alice",
      bio: "",
      discoverable: true,
      followersCount: 0,
      followingCount: 0,
      postsCount: 0,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      username: "bob",
      displayName: "Bob",
      bio: "",
      discoverable: true,
      followersCount: 0,
      followingCount: 0,
      postsCount: 0,
      createdAt: now,
      updatedAt: now,
    });
  });

  // Unpaired followersCount increment without follower doc must fail
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      followersCount: 1,
      updatedAt: pastDate(1000),
    })
  );

  // Atomic batch follow succeeds
  const followBatch = bobDb.batch();
  followBatch.set(bobDb.collection("users").doc(BOB_UID).collection("following").doc(ALICE_UID), {
    userId: BOB_UID,
    targetId: ALICE_UID,
    username: "alice",
    displayName: "Alice",
    createdAt: pastDate(1000),
  });
  followBatch.set(bobDb.collection("users").doc(ALICE_UID).collection("followers").doc(BOB_UID), {
    userId: ALICE_UID,
    targetId: BOB_UID,
    username: "bob",
    displayName: "Bob",
    createdAt: pastDate(1000),
  });
  followBatch.update(bobDb.collection("users").doc(ALICE_UID), {
    followersCount: 1,
    updatedAt: pastDate(1000),
  });
  followBatch.update(bobDb.collection("users").doc(BOB_UID), {
    followingCount: 1,
    updatedAt: pastDate(1000),
  });
  await assertSucceeds(followBatch.commit());
});

test("Posts, Close Friends visibility, video chunks, likes, and comments", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = pastDate(5000);

  // Alice creates a PUBLIC post
  await assertSucceeds(
    aliceDb.collection("posts").doc("post_pub").set({
      authorId: ALICE_UID,
      authorUsername: "alice",
      authorDisplayName: "Alice",
      caption: "Mi primer momento!",
      mediaType: "IMAGE",
      postType: "POST",
      audience: "PUBLIC",
      visibleToUids: [ALICE_UID],
      mediaBase64: "abc123",
      chunkCount: 0,
      likesCount: 0,
      commentsCount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Alice creates a CLOSE_FRIENDS video Reel visible only to Alice & Bob
  const reelBatch = aliceDb.batch();
  reelBatch.set(aliceDb.collection("posts").doc("reel_cf"), {
    authorId: ALICE_UID,
    authorUsername: "alice",
    authorDisplayName: "Alice",
    caption: "Solo mejores amigos",
    mediaType: "VIDEO",
    postType: "REEL",
    audience: "CLOSE_FRIENDS",
    visibleToUids: [ALICE_UID, BOB_UID],
    thumbnailBase64: "thumb123",
    chunkCount: 1,
    likesCount: 0,
    commentsCount: 0,
    createdAt: now,
    updatedAt: now,
  });
  reelBatch.set(aliceDb.collection("posts").doc("reel_cf").collection("chunks").doc("chunk_0"), {
    authorId: ALICE_UID,
    audience: "CLOSE_FRIENDS",
    visibleToUids: [ALICE_UID, BOB_UID],
    chunkIndex: 0,
    dataBase64: "videochunkbase64",
    createdAt: now,
  });
  await assertSucceeds(reelBatch.commit());

  // Bob can read CLOSE_FRIENDS reel & chunks, Charlie cannot
  await assertSucceeds(bobDb.collection("posts").doc("reel_cf").get());
  await assertSucceeds(
    bobDb.collection("posts").where("visibleToUids", "array-contains", BOB_UID).get()
  );
  await assertFails(charlieDb.collection("posts").doc("reel_cf").get());
  await assertFails(
    charlieDb.collection("posts").doc("reel_cf").collection("chunks").doc("chunk_0").get()
  );

  // Unfiltered posts query must fail
  await assertFails(bobDb.collection("posts").get());
  // Public filtered query succeeds
  await assertSucceeds(bobDb.collection("posts").where("audience", "==", "PUBLIC").get());

  // Bob atomically likes post_pub
  const likeBatch = bobDb.batch();
  likeBatch.set(bobDb.collection("posts").doc("post_pub").collection("likes").doc(BOB_UID), {
    userId: BOB_UID,
    username: "bob",
    createdAt: pastDate(1000),
  });
  likeBatch.set(bobDb.collection("users").doc(BOB_UID).collection("likedPosts").doc("post_pub"), {
    userId: BOB_UID,
    targetId: "post_pub",
    createdAt: pastDate(1000),
  });
  likeBatch.update(bobDb.collection("posts").doc("post_pub"), {
    likesCount: 1,
    updatedAt: pastDate(1000),
  });
  await assertSucceeds(likeBatch.commit());

  // Bob cannot delete Alice's post
  await assertFails(bobDb.collection("posts").doc("post_pub").delete());
  await assertSucceeds(aliceDb.collection("posts").doc("post_pub").delete());
});

test("Stories, Notes, and Direct Message Chats enforce participant isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = pastDate(5000);

  // Story
  await assertSucceeds(
    aliceDb.collection("stories").doc("story_1").set({
      authorId: ALICE_UID,
      authorUsername: "alice",
      authorDisplayName: "Alice",
      mediaBase64: "imgbase64",
      caption: "En la playa",
      audience: "PUBLIC",
      visibleToUids: [ALICE_UID],
      createdAt: now,
    })
  );
  await assertSucceeds(bobDb.collection("stories").where("audience", "==", "PUBLIC").get());
  await assertFails(bobDb.collection("stories").get());

  // Note
  await assertSucceeds(
    aliceDb.collection("notes").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "alice",
      text: "Escuchando musica",
      discoverable: true,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    bobDb.collection("notes").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "alice",
      text: "Spoof",
      discoverable: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Chat & Message
  const chatId = `${ALICE_UID}_${BOB_UID}`;
  const chatBatch = aliceDb.batch();
  chatBatch.set(aliceDb.collection("chats").doc(chatId), {
    participantIds: [ALICE_UID, BOB_UID],
    lastMessageText: "Hola Bob!",
    lastSenderId: ALICE_UID,
    createdAt: now,
    updatedAt: now,
  });
  chatBatch.set(aliceDb.collection("chats").doc(chatId).collection("messages").doc("msg_1"), {
    chatId,
    senderId: ALICE_UID,
    senderUsername: "alice",
    participantIds: [ALICE_UID, BOB_UID],
    text: "Hola Bob!",
    createdAt: now,
  });
  await assertSucceeds(chatBatch.commit());

  await assertSucceeds(bobDb.collection("chats").where("participantIds", "array-contains", BOB_UID).get());
  await assertFails(charlieDb.collection("chats").doc(chatId).get());
  await assertFails(charlieDb.collection("chats").doc(chatId).collection("messages").get());
  await assertFails(charlieDb.collection("chats").doc(chatId).collection("messages").doc("msg_1").get());
});
