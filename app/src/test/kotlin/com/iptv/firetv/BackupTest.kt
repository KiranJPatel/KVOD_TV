package com.iptv.firetv

import com.iptv.firetv.core.common.AppException
import com.iptv.firetv.core.parser.XmltvProgramme
import com.iptv.firetv.core.parser.shifted
import com.iptv.firetv.core.security.SourceSecrets
import com.iptv.firetv.data.BackupCodec
import com.iptv.firetv.data.BackupFile
import com.iptv.firetv.data.BackupProfile
import com.iptv.firetv.data.BackupSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupTest {
    @Test fun roundTripKeepsSettingsProfilesAndSourceDefinitions() {
        val b = BackupFile(createdAt = 5, profiles = listOf(BackupProfile("Kids", kids = true, blockedGroups = "horror")), sources = listOf(BackupSource("Home", "M3U_URL", 12)))
        val d = BackupCodec.decode(BackupCodec.encode(b))
        assertEquals(b, d)
    }

    @Test fun backupFormatHasNoPlaceForSecrets() {
        val text = BackupCodec.encode(BackupFile(sources = listOf(BackupSource("Provider", "XTREAM"))))
        for (word in listOf("password", "username", "token", "pinHash", "secretCipher", "url")) assertFalse("must not contain $word", text.contains("\"$word\""))
    }

    @Test fun rejectsForeignOrNewerOrOversizedFiles() {
        fun code(text: String) = try { BackupCodec.decode(text); null } catch (e: AppException) { e.diagnostic }
        assertEquals("E_INPUT", code("not json")); assertEquals("E_INPUT", code("{\"format\":\"other\"}"))
        assertEquals("E_INPUT", code("{\"format\":\"iptv-firetv-backup\",\"version\":9}"))
        assertEquals("E_SIZE", code("x".repeat(BackupCodec.MAX_BYTES + 1)))
    }

    @Test fun unknownFieldsFromFutureMinorVersionsAreIgnored() {
        assertTrue(BackupCodec.decode("{\"format\":\"iptv-firetv-backup\",\"version\":1,\"somethingNew\":true}").profiles.isEmpty())
    }

    @Test fun guideOffsetShiftsStartAndEnd() {
        val p = XmltvProgramme("c", 1_000_000, 4_600_000, "T", null, null, null, null, null, false)
        assertEquals(p, p.shifted(0))
        val s = p.shifted(-90 * 60_000L); assertEquals(1_000_000 - 5_400_000, s.startMs); assertEquals(3_600_000L, s.endMs - s.startMs)
        assertEquals(30, SourceSecrets(epgOffsetMin = 30).epgOffsetMin)
    }

    @Test fun version2CarriesFavouritesAndHistoryByTitleWithoutIds() {
        val ref = com.iptv.firetv.data.ItemRef("LIVE", "Home list", "Sample News", "News", "sample.news")
        val b = BackupFile(userData = listOf(com.iptv.firetv.data.BackupUserData("Main", listOf(ref),
            listOf(com.iptv.firetv.data.ProgressRef(com.iptv.firetv.data.ItemRef("EPISODE", "Home list", "Show", "Series", season = 1, episode = 2), "Show S1E2", 5_000, 60_000, false, 9)),
            listOf(com.iptv.firetv.data.RecentRef(ref, 7)))))
        val text = BackupCodec.encode(b)
        assertEquals(b, BackupCodec.decode(text)); assertEquals(2, BackupCodec.decode(text).version)
        assertFalse("ids are per installation and must not be exported", text.contains("itemId"))
    }

    @Test fun version1BackupsStillRestore() {
        val v1 = BackupCodec.decode("{\"format\":\"iptv-firetv-backup\",\"version\":1,\"profiles\":[{\"name\":\"Kids\",\"kids\":true}]}")
        assertEquals(1, v1.profiles.size); assertTrue(v1.userData.isEmpty())
    }
}
