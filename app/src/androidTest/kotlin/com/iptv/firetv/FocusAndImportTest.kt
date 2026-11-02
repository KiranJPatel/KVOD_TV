package com.iptv.firetv

import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.platform.testTag
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iptv.firetv.core.database.AppDatabase
import com.iptv.firetv.core.database.FavoriteEntity
import com.iptv.firetv.core.network.Downloader
import com.iptv.firetv.core.network.XtreamClient
import com.iptv.firetv.core.ui.FocusSurface
import com.iptv.firetv.core.ui.IptvTheme
import com.iptv.firetv.core.model.AccentColor
import com.iptv.firetv.core.model.AppTheme
import com.iptv.firetv.data.EpgRepository
import com.iptv.firetv.data.ImportRepository
import com.iptv.firetv.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Device/emulator tests (Fire TV or Android TV image recommended). */
@RunWith(AndroidJUnit4::class)
class FocusAndImportTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var ctx: Context
    private lateinit var db: AppDatabase
    private lateinit var imports: ImportRepository

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        imports = ImportRepository(ctx, db, XtreamClient(Downloader()), EpgRepository(db, SettingsRepository(ctx)))
    }
    @After fun tearDown() = db.close()

    private fun playlist(vararg names: String) = File.createTempFile("pl", ".m3u").also { f ->
        f.writeText("#EXTM3U\n" + names.joinToString("") { "#EXTINF:-1 tvg-id=\"$it\" group-title=\"G\",$it\nhttps://x.test/$it.m3u8\n" })
    }

    @Test fun importingTwiceDoesNotDuplicateAndKeepsFavoritesWhenOrderChanges() = runBlocking {
        val src = db.sources().insert(com.iptv.firetv.core.database.SourceEntity(name = "t", type = "M3U_FILE"))
        imports.commitM3u(src, playlist("a", "b", "c"), null)
        val before = db.channels().byId(db.channels().byTvgIds(listOf("b")).single().id)!!
        db.user().addFavorite(FavoriteEntity(1, "LIVE", before.id, 0))
        imports.commitM3u(src, playlist("c", "b", "a", "d"), null)   // reordered + one new
        assertEquals(4, db.channels().countBySource(src))
        assertEquals(1, db.user().isFavorite(1, "LIVE", before.id))
        assertEquals(before.id, db.channels().byTvgIds(listOf("b")).single().id)
        imports.commitM3u(src, playlist("a"), null)                   // provider dropped channels
        assertEquals(1, db.channels().countBySource(src))
    }

    @Test fun duplicateEntriesInOneFileAreCountedAndSkipped() = runBlocking {
        val src = db.sources().insert(com.iptv.firetv.core.database.SourceEntity(name = "t", type = "M3U_FILE"))
        val f = File.createTempFile("dup", ".m3u").also { it.writeText("#EXTM3U\n#EXTINF:-1,X\nhttps://x/1.m3u8\n#EXTINF:-1,X\nhttps://x/1.m3u8\n") }
        val s = imports.commitM3u(src, f, null); assertEquals(1, s.channels); assertEquals(1, s.duplicates)
    }

    @Test fun adultItemsAreExcludedByRestrictedQueries() = runBlocking {
        val src = db.sources().insert(com.iptv.firetv.core.database.SourceEntity(name = "t", type = "M3U_FILE"))
        val f = File.createTempFile("ad", ".m3u").also { it.writeText("#EXTM3U\n#EXTINF:-1 group-title=\"Adult 18+\",Night\nhttps://x/1.m3u8\n#EXTINF:-1 group-title=\"News\",Daily\nhttps://x/2.m3u8\n") }
        imports.commitM3u(src, f, null)
        val kids = db.channels().observe(null, "", true, emptyList(), -1, 0, 100).first()
        assertEquals(listOf("Daily"), kids.map { it.name })
        val blocked = db.channels().observe(null, "", false, listOf("news"), -1, 0, 100).first()
        assertEquals(listOf("Night"), blocked.map { it.name })
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun focusMovesWithDpadAndKeepsLayout() {
        compose.setContent { IptvTheme(AppTheme.AURORA, com.iptv.firetv.core.model.ThemeMode.DARK, AccentColor.THEME, 1f) { Row {
            FocusSurface({}, Modifier.testTag("a")) { Text("A") }
            FocusSurface({}, Modifier.testTag("b")) { Text("B") }
        } } }
        compose.onNodeWithTag("a").requestFocus()
        compose.onNodeWithTag("a").assertIsFocused()
        compose.onNodeWithTag("a").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("b").assertIsFocused()
    }
}

@RunWith(AndroidJUnit4::class)
class CategoryOverrideTest {
    @Test fun userOverrideMovesAGroupToAnotherContentTypeOnReimport() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        val imports = ImportRepository(ctx, db, XtreamClient(Downloader()), EpgRepository(db, SettingsRepository(ctx)))
        val src = db.sources().insert(com.iptv.firetv.core.database.SourceEntity(name = "t", type = "M3U_FILE"))
        val f = File.createTempFile("ov", ".m3u").also { it.writeText("#EXTM3U\n#EXTINF:-1 group-title=\"Cinema Hits\",Some Film\nhttps://x/a.mp4\n") }
        imports.commitM3u(src, f, null)
        assertEquals(1, db.vod().countMovies(src)); assertEquals(0, db.channels().countBySource(src))       // auto: movie
        db.imports().setOverride(com.iptv.firetv.core.database.GroupOverrideEntity(src, "cinemahits", "Cinema Hits", "LIVE"))
        imports.commitM3u(src, f, null)
        assertEquals(1, db.channels().countBySource(src)); assertEquals(0, db.vod().countMovies(src))       // forced: live, stale movie removed
        db.close()
    }
}

@RunWith(AndroidJUnit4::class)
class PagingTest {
    @Test fun pagedChannelQueryLoadsPageByPageAndHonoursRestrictions() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        val src = db.sources().insert(com.iptv.firetv.core.database.SourceEntity(name = "t", type = "M3U_FILE"))
        db.channels().upsertAll((1..250).map { i ->
            com.iptv.firetv.core.database.ChannelEntity("id$i", src, "Chan $i", "chan$i", "G", "g", null, null, if (i <= 5) "dup" else null, "https://x/$i", null, i % 50 == 0, i, 0)
        })
        val pager = db.channels().paged(null, "", true, emptyList(), -1, 0, false)           // hide adult => 245 rows
        val first = pager.load(androidx.paging.PagingSource.LoadParams.Refresh(null, 100, false)) as androidx.paging.PagingSource.LoadResult.Page
        assertEquals(100, first.data.size); assertTrue(first.nextKey != null)
        assertEquals(250, db.channels().ids(null, "", false, emptyList(), -1, 0, false).size)
        assertEquals(245, db.channels().ids(null, "", true, emptyList(), -1, 0, false).size)
        assertEquals(1, db.channels().ids(null, "chan7", false, emptyList(), -1, 0, false).filter { it == "id7" }.size)
        db.close()
    }
}

@RunWith(AndroidJUnit4::class)
class BackupUserDataTest {
    @Test fun favouritesAndProgressSurviveBackupAndAreReattachedAfterTheSourceIsImportedAgain() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        val settings = SettingsRepository(ctx)
        val profiles = com.iptv.firetv.data.ProfileRepository(db, settings); profiles.ensureDefault()
        val backup = com.iptv.firetv.data.BackupRepository(ctx, db, settings, profiles)
        val pid = profiles.activeId()
        val src = db.sources().insert(com.iptv.firetv.core.database.SourceEntity(name = "Home list", type = "M3U_FILE"))
        fun chan(i: Long) = com.iptv.firetv.core.database.ChannelEntity("c$i", i, "Sample News", "samplenews", "News", "news", null, null, "sample.news", "https://x/1", null, false, 0, 0)
        db.channels().upsertAll(listOf(chan(src)))
        db.user().addFavorite(FavoriteEntity(pid, "LIVE", "c$src", 1))
        val file = File.createTempFile("bk", ".json").also { it.writeText(com.iptv.firetv.data.BackupCodec.encode(backup.build())) }
        assertTrue(file.readText().contains("Sample News")); assertTrue(!file.readText().contains("c$src"))

        // Fresh installation: the source exists but is empty (not imported yet) -> reference stays pending.
        db.user().removeFavorite(pid, "LIVE", "c$src"); db.channels().deleteBySource(src)
        backup.restore(android.net.Uri.fromFile(file))
        assertEquals(0, db.user().favoritesOf(pid).size)
        // The playlist is imported again (ids differ from the first run): pending favourites are re-attached.
        db.channels().upsertAll(listOf(chan(src).copy(id = "newid")))
        backup.applyPending()
        assertEquals(listOf("newid"), db.user().favoritesOf(pid).map { it.itemId })
        db.close()
    }
}
