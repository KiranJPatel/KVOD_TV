package com.iptv.firetv

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iptv.firetv.core.database.AppDatabase
import com.iptv.firetv.core.database.ChannelEntity
import com.iptv.firetv.core.database.ProfileEntity
import com.iptv.firetv.core.database.SourceEntity
import com.iptv.firetv.core.database.VodItemEntity
import com.iptv.firetv.core.model.AccentColor
import com.iptv.firetv.core.model.AppTheme
import com.iptv.firetv.core.model.ContentKind
import com.iptv.firetv.core.ui.IptvTheme
import com.iptv.firetv.core.ui.StatePanel
import com.iptv.firetv.core.ui.TvButton
import com.iptv.firetv.data.CatalogRepository
import com.iptv.firetv.data.EpgRepository
import com.iptv.firetv.data.PinResult
import com.iptv.firetv.data.ProfileRepository
import com.iptv.firetv.data.SettingsRepository
import com.iptv.firetv.data.UserDataRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserDataAndStatesTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val migrations = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)
    private lateinit var ctx: Context
    private lateinit var db: AppDatabase
    private lateinit var profiles: ProfileRepository
    private lateinit var catalog: CatalogRepository
    private lateinit var user: UserDataRepository
    private var src = 0L

    @Before fun setUp() = runBlocking {
        ctx = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        val settings = SettingsRepository(ctx)
        profiles = ProfileRepository(db, settings)
        catalog = CatalogRepository(db, profiles, settings, EpgRepository(db, settings))
        user = UserDataRepository(db, profiles, catalog)
        profiles.ensureDefault()
        src = db.sources().insert(SourceEntity(name = "t", type = "M3U_FILE"))
    }
    @After fun tearDown() = db.close()

    private suspend fun channel(id: String, name: String, adult: Boolean = false) =
        db.channels().upsertAll(listOf(ChannelEntity(id, src, name, name.lowercase(), "G", "g", null, null, null, "https://x/$id", null, adult, 0, 0)))
    private suspend fun movie(id: String, title: String, adult: Boolean = false) =
        db.vod().upsertMovies(listOf(VodItemEntity(id, src, title, title.lowercase(), "Movies", "movies", null, streamRef = "https://x/$id", adult = adult, addedAt = 0, updatedAt = 0)))

    @Test fun favoritesPersistAndAreIsolatedPerProfile() = runBlocking {
        channel("c1", "One")
        val owner = profiles.activeProfile.first()!!
        assertTrue(user.toggleFavorite(ContentKind.LIVE, "c1"))
        assertEquals(setOf("c1"), user.favoriteIds(ContentKind.LIVE).first())
        val other = profiles.save(ProfileEntity(name = "Other"))
        assertEquals(PinResult.Ok, profiles.select(other, null))
        assertEquals(emptySet<String>(), user.favoriteIds(ContentKind.LIVE).first())       // not visible to another profile
        profiles.select(owner.id, null)
        assertEquals(setOf("c1"), user.favoriteIds(ContentKind.LIVE).first())                // still there after switching back
        assertFalse(user.toggleFavorite(ContentKind.LIVE, "c1"))
    }

    @Test fun watchProgressCompletesNearTheEndAndLeavesContinueWatching() = runBlocking {
        movie("m1", "Film")
        user.saveProgress(ContentKind.MOVIE, "m1", "Film", null, 600_000, 1_200_000)
        assertEquals(600_000L, user.progress("m1")!!.positionMs)
        assertEquals(listOf("m1"), user.continueWatching().first().map { it.progress.itemId })
        user.saveProgress(ContentKind.MOVIE, "m1", "Film", null, 1_190_000, 1_200_000)       // 99 % => completed, position reset
        assertTrue(user.progress("m1")!!.completed); assertEquals(0L, user.progress("m1")!!.positionMs)
        assertTrue(user.continueWatching().first().isEmpty())
    }

    @Test fun restrictedProfilesNeverSeeAdultItemsInAnyList() = runBlocking {
        movie("ok", "Family Film"); movie("bad", "Night Film", adult = true); channel("adultch", "Night Channel", adult = true)
        val kids = profiles.save(ProfileEntity(name = "Kids", kids = true))
        profiles.select(kids, null)
        // Progress row exists for the kids profile itself (e.g. recorded before the item was restricted); it must still be hidden.
        user.saveProgress(ContentKind.MOVIE, "bad", "Night Film", null, 100_000, 1_000_000)
        assertEquals(1, db.user().progress(kids, "bad")?.let { 1 } ?: 0)
        assertNull(catalog.movie("bad")); assertNull(catalog.channel("adultch"))                // direct lookups are blocked
        assertTrue(catalog.search("night", -1, setOf("LIVE", "MOVIE", "SERIES")).isEmpty)       // search is blocked
        assertEquals(listOf("ok"), catalog.movies(null, "", 1).first().map { it.id })            // lists are filtered
        assertTrue(user.continueWatching().first().isEmpty())                                    // Continue Watching is filtered
    }

    @Test fun pinProtectedProfileRequiresThePinToSwitch() = runBlocking {
        val id = profiles.save(ProfileEntity(name = "Locked"), newPin = "2468")
        assertEquals(PinResult.Wrong, profiles.select(id, "0000")); assertEquals(PinResult.Wrong, profiles.select(id, null))
        assertEquals(PinResult.Ok, profiles.select(id, "2468"))
    }

    @Test fun schemaV1CreatesAndOpensWithAllMigrations() {
        migrations.createDatabase("migration-test", 1).close()
        val opened = Room.databaseBuilder(ctx, AppDatabase::class.java, "migration-test").addMigrations(*AppDatabase.MIGRATIONS).build()
        opened.openHelper.writableDatabase; opened.close()
    }

    @Test fun migrationFromV1AddsThePlaylistTables() {
        migrations.createDatabase("migration-test-2", 1).close()
        // Room validates the migrated schema against schemas/2.json (generated by the first build).
        migrations.runMigrationsAndValidate("migration-test-2", 2, true, AppDatabase.MIGRATION_1_2).close()
    }

    @Test fun emptyAndErrorPanelsShowMessageAndAnAction() {
        compose.setContent { IptvTheme(AppTheme.AURORA, com.iptv.firetv.core.model.ThemeMode.LIGHT, AccentColor.THEME, 1.3f) {
            StatePanel("No channels yet", "Add or refresh a playlist.", isError = false) { TvButton("Open sources", {}) }
        } }
        compose.onNodeWithText("No channels yet").assertIsDisplayed(); compose.onNodeWithText("Open sources").assertIsDisplayed()
    }
}

@RunWith(AndroidJUnit4::class)
class PlaylistTest {
    private lateinit var db: AppDatabase
    private lateinit var profiles: ProfileRepository
    private lateinit var playlists: com.iptv.firetv.data.PlaylistRepository
    private var src = 0L

    @Before fun setUp() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        profiles = ProfileRepository(db, SettingsRepository(ctx))
        profiles.ensureDefault()
        playlists = com.iptv.firetv.data.PlaylistRepository(db, profiles)
        src = db.sources().insert(SourceEntity(name = "t", type = "M3U_FILE"))
        db.channels().upsertAll(listOf("c1", "c2", "c3").map { ChannelEntity(it, src, it, it, "G", "g", null, null, null, "https://x/$it", null, false, 0, 0) })
        Unit
    }
    @After fun tearDown() = db.close()

    @Test fun playlistsHoldItemsInTheOrderTheyWereAddedAndCanBeEdited() = runBlocking {
        val a = playlists.create("  Watch later  ")!!
        assertEquals("Watch later", playlists.nameOf(a))
        assertTrue(playlists.toggle(a, ContentKind.LIVE, "c2")); assertTrue(playlists.toggle(a, ContentKind.LIVE, "c1"))
        assertEquals(listOf("c2", "c1"), playlists.channelIds(a))
        assertEquals(2, playlists.playlists.first().single().itemCount)
        assertFalse("toggling again removes", playlists.toggle(a, ContentKind.LIVE, "c2"))
        assertEquals(listOf("c1"), playlists.channelIds(a))
        playlists.rename(a, "Later"); assertEquals("Later", playlists.nameOf(a))
        playlists.delete(a); assertNull(playlists.nameOf(a)); assertTrue(playlists.playlists.first().isEmpty())
    }

    @Test fun anItemCanBeInSeveralPlaylistsAndBlankNamesAreRejected() = runBlocking {
        val a = playlists.create("A")!!; val b = playlists.create("B")!!
        playlists.toggle(a, ContentKind.LIVE, "c1"); playlists.toggle(b, ContentKind.LIVE, "c1")
        assertEquals(setOf(a, b), playlists.playlistIdsContaining(ContentKind.LIVE, "c1").first())
        assertEquals(emptySet<Long>(), playlists.playlistIdsContaining(ContentKind.LIVE, "c3").first())
        assertNull(playlists.create("   "))
    }

    @Test fun thereIsNoLimitOnTheNumberOfPlaylists() = runBlocking {
        repeat(60) { playlists.create("List $it") }
        assertEquals(60, playlists.playlists.first().size)
    }

    @Test fun deletingAProfileRemovesItsPlaylists() = runBlocking {
        val other = profiles.save(ProfileEntity(name = "Other"))
        val owner = profiles.activeId()
        val a = playlists.create("Mine")!!; playlists.toggle(a, ContentKind.LIVE, "c1")
        profiles.select(other, null)
        assertTrue("another profile sees none of them", playlists.playlists.first().isEmpty())
        assertNull(playlists.nameOf(a)); assertFalse(playlists.toggle(a, ContentKind.LIVE, "c2"))
        profiles.select(owner, null)
        assertEquals(listOf("c1"), playlists.channelIds(a))
    }
}
