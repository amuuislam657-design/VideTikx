package com.example

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ChannelEntity
import com.example.data.local.CommentEntity
import com.example.data.local.LocalMediaStorage
import com.example.data.local.MyTubeDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.VideoEntity
import com.example.ui.theme.MyTubeTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MyTubeOfflineTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: MyTubeDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MyTubeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `offline video insert, like dislike, comment cascade, and delete work end to end`() = runTest {
        val videoDao = db.videoDao()
        val commentDao = db.commentDao()

        val video = VideoEntity(
            id = "vid-1",
            title = "Mountain Sunrise 4K",
            description = "Captured offline in the Alps",
            category = "Cinema",
            isShort = false,
            videoFilePath = "/tmp/fake_video.mp4",
            thumbnailFilePath = "/tmp/fake_thumb.jpg",
            durationMs = 95_000L,
            fileSizeBytes = 12_500_000L,
            width = 1920,
            height = 1080,
            channelId = "self",
            creatorName = "Creator"
        )

        videoDao.insertVideo(video)
        val saved = videoDao.getVideoById("vid-1")
        assertNotNull(saved)
        assertEquals("Mountain Sunrise 4K", saved?.title)

        // Like -> Dislike -> Clear Dislike
        videoDao.toggleLike("vid-1")
        assertEquals(1, videoDao.getVideoById("vid-1")?.likesCount)
        assertEquals(1, videoDao.getVideoById("vid-1")?.userReaction)

        videoDao.toggleDislike("vid-1")
        val disliked = videoDao.getVideoById("vid-1")
        assertEquals(0, disliked?.likesCount)
        assertEquals(1, disliked?.dislikesCount)
        assertEquals(-1, disliked?.userReaction)

        // Local comments
        commentDao.insertComment(
            CommentEntity(
                id = "c-1",
                videoId = "vid-1",
                authorName = "Creator",
                text = "Great lighting!"
            )
        )
        assertEquals(1, commentDao.observeCommentsForVideo("vid-1").first().size)

        // Delete video -> cascades to comments
        videoDao.deleteVideoById("vid-1")
        assertEquals(0, videoDao.observeAllVideos().first().size)
        assertEquals(0, commentDao.observeCommentsForVideo("vid-1").first().size)
    }

    @Test
    fun `channels seed and subscription toggle persist offline`() = runTest {
        val channelDao = db.channelDao()
        MyTubeDatabase.seedInitialChannels(channelDao)

        val channels = channelDao.observeAllChannels().first()
        assertTrue(channels.size >= 3)

        val target = channels.first { !it.isDefaultSelf }
        val initialSub = target.isSubscribed
        channelDao.setSubscribed(target.id, !initialSub)

        val updated = channelDao.getChannelById(target.id)
        assertEquals(!initialSub, updated?.isSubscribed)

        val custom = ChannelEntity(
            id = "ch-custom",
            name = "Drone Footage",
            handle = "@dronefootage",
            bio = "Aerial offline clips",
            accentColorHex = 0xFFFF2E4C,
            isSubscribed = true
        )
        channelDao.insertChannel(custom)
        assertNotNull(channelDao.getChannelById("ch-custom"))
    }

    @Test
    fun `preferences repository defaults to dark mode and saves profile edits`() = runTest {
        val prefs = UserPreferencesRepository(context)
        assertTrue(prefs.isDarkTheme.first())

        prefs.setDarkTheme(false)
        assertFalse(prefs.isDarkTheme.first())

        prefs.updateProfile("Alex Studio", "Offline filmmaker & editor")
        val profile = prefs.userProfile.first()
        assertEquals("Alex Studio", profile.name)
        assertEquals("Offline filmmaker & editor", profile.bio)
    }

    @Test
    fun `local media storage saves video, generates fallback thumbnail, tracks size, and deletes cleanly`() = runTest {
        val storage = LocalMediaStorage(context)
        val sourceFile = File(context.cacheDir, "sample_upload.mp4").apply {
            writeBytes(ByteArray(4096) { 0x2A })
        }

        var latestProgress = 0f
        val (savedVideoPath, savedSize) = storage.saveVideoFile(Uri.fromFile(sourceFile)) { p ->
            latestProgress = p
        }
        assertTrue(File(savedVideoPath).exists())
        assertEquals(4096L, savedSize)
        assertEquals(1f, latestProgress, 0.01f)

        val savedThumbPath = storage.saveThumbnailFromUriOrVideo(null, savedVideoPath)
        assertTrue(File(savedThumbPath).exists())

        val stats = storage.calculateStorageStats(
            referencedVideoPaths = setOf(savedVideoPath),
            referencedThumbnailPaths = setOf(savedThumbPath),
            videoCount = 1,
            shortsCount = 0
        )
        assertTrue(stats.videosBytes >= 4096L)
        assertTrue(stats.thumbnailsBytes > 0L)

        storage.deleteFileIfExists(savedVideoPath)
        storage.deleteFileIfExists(savedThumbPath)
        assertFalse(File(savedVideoPath).exists())
        assertFalse(File(savedThumbPath).exists())
    }

    @Test
    fun `compose ui navigates across tabs and toggles theme`() {
        val app = context as android.app.Application
        val vm = com.example.ui.MainViewModel(app)

        composeRule.setContent {
            MyTubeTheme(darkTheme = true) {
                MyTubeApp(viewModel = vm)
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("MY TUBE").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_shorts").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("No Shorts uploaded yet").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_upload").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Upload to MY TUBE").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_subscriptions").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Local Creator Channels").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_profile").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile_theme_switch").assertIsDisplayed()
        composeRule.onNodeWithTag("edit_profile_button").assertIsDisplayed()
    }
}
