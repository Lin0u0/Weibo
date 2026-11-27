package com.example.myweibo.data

import kotlinx.coroutines.CancellationException
import org.json.JSONObject

internal enum class ProfileCoverState { Available, Omitted, Cleared, Invalid }

internal data class CurrentProfileCandidate(
    val profile: UserProfile,
    val coverState: ProfileCoverState,
) {
    fun retainOmittedCover(requestedUid: String?, cached: UserProfile?): UserProfile {
        if (requestedUid.isNullOrBlank() || coverState != ProfileCoverState.Omitted ||
            profile.id != requestedUid || cached == null || cached.id != requestedUid
        ) return profile
        return profile.copy(coverUrls = ProfileCoverUrls.normalize(cached.coverUrls))
    }
}

internal suspend fun selectCurrentProfileCandidate(
    requestedUid: String,
    config: JSONObject,
    fetchDesktop: suspend () -> String,
    fetchMobile: suspend () -> String,
): CurrentProfileCandidate {
    fun parse(raw: String) = WeiboJsonParser.parseCurrentProfileCandidate(raw, requestedUid, config)
    val desktop = try {
        parse(fetchDesktop())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        // Invalid responses (including logical API errors) are not profile data.
        return parse(fetchMobile())
    }
    if (desktop.coverState == ProfileCoverState.Available || desktop.coverState == ProfileCoverState.Cleared) {
        return desktop
    }

    val mobile = try {
        parse(fetchMobile())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        return desktop
    }
    // A cover-only fallback must not replace the verified desktop profile's other fields.
    return when (mobile.coverState) {
        ProfileCoverState.Available, ProfileCoverState.Cleared -> desktop.copy(
            profile = desktop.profile.copy(coverUrls = mobile.profile.coverUrls),
            coverState = mobile.coverState,
        )
        else -> desktop
    }
}
