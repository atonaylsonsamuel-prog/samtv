package com.samdevelop.samtv.data.repository

import com.samdevelop.samtv.core.common.AppError
import com.samdevelop.samtv.core.common.AppResult
import com.samdevelop.samtv.core.m3u.M3uConnector
import com.samdevelop.samtv.core.model.ConnectionProfile
import com.samdevelop.samtv.core.model.SourceSecrets
import com.samdevelop.samtv.core.model.SourceType
import com.samdevelop.samtv.core.net.HttpClient
import com.samdevelop.samtv.core.source.SourceConnector
import com.samdevelop.samtv.core.xtream.XtreamConnector
import com.samdevelop.samtv.data.local.ProfileDao
import com.samdevelop.samtv.data.local.toDomain
import com.samdevelop.samtv.security.CredentialStore
import java.util.concurrent.ConcurrentHashMap

/**
 * Cria (e mantém em memória) o conector de cada fonte. O conector guarda estado útil da sessão
 * (ex.: formatos permitidos pelo servidor Xtream), por isso é reaproveitado enquanto o perfil não muda.
 */
class ConnectorProvider(
    private val profiles: ProfileDao,
    private val credentials: CredentialStore,
    private val http: HttpClient,
) {
    private val cache = ConcurrentHashMap<String, Pair<Long, SourceConnector>>()

    suspend fun forProfile(profileId: String): AppResult<SourceConnector> {
        val profile = profiles.get(profileId)?.toDomain() ?: return AppResult.Failure(AppError.NoActiveSource)
        cache[profileId]?.let { (version, connector) -> if (version == profile.updatedAtMillis) return AppResult.Success(connector) }
        val secrets = credentials.load(profileId) ?: return AppResult.Failure(AppError.Storage("credentials unavailable"))
        val connector = build(profile, secrets)
        if (connector is AppResult.Success) cache[profileId] = profile.updatedAtMillis to connector.value
        return connector
    }

    fun build(profile: ConnectionProfile, secrets: SourceSecrets): AppResult<SourceConnector> = when (profile.type) {
        SourceType.XTREAM -> {
            val user = profile.username
            val pass = secrets.password
            if (user.isNullOrEmpty() || pass.isNullOrEmpty()) AppResult.Failure(AppError.Storage("missing xtream credentials"))
            else AppResult.Success(XtreamConnector(profile.id, profile.address, user, pass, profile.allowInsecureHttp, http))
        }
        SourceType.M3U -> {
            val url = secrets.playlistUrl
            if (url.isNullOrEmpty()) AppResult.Failure(AppError.Storage("missing playlist url"))
            else AppResult.Success(M3uConnector(profile.id, url, profile.allowInsecureHttp, http))
        }
    }

    fun put(profile: ConnectionProfile, connector: SourceConnector) {
        cache[profile.id] = profile.updatedAtMillis to connector
    }

    fun invalidate(profileId: String) {
        cache.remove(profileId)
    }
}
