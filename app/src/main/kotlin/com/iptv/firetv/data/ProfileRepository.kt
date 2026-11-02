package com.iptv.firetv.data

import androidx.room.withTransaction
import com.iptv.firetv.core.database.AppDatabase
import com.iptv.firetv.core.database.ProfileEntity
import com.iptv.firetv.core.model.ContentRestrictions
import com.iptv.firetv.core.security.Pin
import com.iptv.firetv.core.security.PinThrottle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PinResult {
    data object Ok : PinResult
    data object Wrong : PinResult
    data class Locked(val seconds: Int) : PinResult
}

fun ProfileEntity.restrictions() = ContentRestrictions(
    hideAdult = hideAdult || kids,
    blockedGroups = blockedGroups.split("|").filter { it.isNotBlank() }.toSet()
)

@Singleton
class ProfileRepository @Inject constructor(private val db: AppDatabase, private val settings: SettingsRepository) {
    private val throttle = PinThrottle()

    val profiles: Flow<List<ProfileEntity>> = db.user().observeProfiles()
    val activeProfile: Flow<ProfileEntity?> = combine(profiles, settings.settings.map { it.activeProfileId }.distinctUntilChanged()) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }.distinctUntilChanged()
    val restrictions: Flow<ContentRestrictions> = activeProfile.map { it?.restrictions() ?: ContentRestrictions(hideAdult = true) }.distinctUntilChanged()

    suspend fun ensureDefault() {
        if (db.user().profileCount() == 0) {
            val id = db.user().insertProfile(ProfileEntity(name = "Main", avatar = 0, isOwner = true, hideAdult = false))
            settings.set(SettingKeys.ACTIVE_PROFILE, id)
        }
    }

    suspend fun activeId(): Long { ensureDefault(); return activeProfile.first()?.id ?: 0L }

    /** Switching to a PIN-protected profile requires its PIN; enforced here, not only in the UI. */
    suspend fun select(id: Long, pin: String?): PinResult {
        val p = db.user().profile(id) ?: return PinResult.Wrong
        if (p.pinHash != null) {
            val r = verify(p, pin ?: ""); if (r != PinResult.Ok) return r
        }
        settings.set(SettingKeys.ACTIVE_PROFILE, id)
        return PinResult.Ok
    }

    suspend fun verifyPin(id: Long, pin: String): PinResult = db.user().profile(id)?.let { verify(it, pin) } ?: PinResult.Wrong

    private fun verify(p: ProfileEntity, pin: String): PinResult {
        val wait = throttle.remainingLockSeconds(); if (wait > 0) return PinResult.Locked(wait)
        return if (Pin.matches(pin, p.pinSalt, p.pinHash)) { throttle.onSuccess(); PinResult.Ok } else { throttle.onFailure(); PinResult.Wrong }
    }

    suspend fun save(p: ProfileEntity, newPin: String? = null, clearPin: Boolean = false): Long {
        var e = p
        if (clearPin) e = e.copy(pinSalt = null, pinHash = null)
        else if (newPin != null && Pin.isValidFormat(newPin)) { val s = Pin.newSalt(); e = e.copy(pinSalt = s, pinHash = Pin.hash(newPin, s)) }
        if (e.kids) e = e.copy(hideAdult = true)
        return if (e.id == 0L) db.user().insertProfile(e) else { db.user().updateProfile(e); e.id }
    }

    /** Deletes a profile and all of its private data. The owner profile cannot be deleted. */
    suspend fun delete(id: Long): Boolean {
        val p = db.user().profile(id) ?: return false
        if (p.isOwner) return false
        db.withTransaction {
            db.user().deleteFavoritesOf(id); db.user().deleteProgressOf(id); db.user().deleteHistoryOf(id); db.user().deleteSearchesOf(id)
            db.playlists().deleteItemsOfProfile(id); db.playlists().deleteOfProfile(id)
            db.user().deleteProfile(id)
        }
        if (settings.current().activeProfileId == id) settings.set(SettingKeys.ACTIVE_PROFILE, 0L)
        return true
    }
}
