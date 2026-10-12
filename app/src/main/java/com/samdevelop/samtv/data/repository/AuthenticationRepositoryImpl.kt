package com.samdevelop.samtv.data.repository

import androidx.room.withTransaction
import com.samdevelop.samtv.core.common.AppError
import com.samdevelop.samtv.core.common.AppResult
import com.samdevelop.samtv.core.model.AuthenticationResult
import com.samdevelop.samtv.core.model.ConnectionProfile
import com.samdevelop.samtv.core.model.SourceSecrets
import com.samdevelop.samtv.core.repository.AuthenticationRepository
import com.samdevelop.samtv.data.local.AppDatabase
import com.samdevelop.samtv.data.local.AppPreferences
import com.samdevelop.samtv.data.local.toDomain
import com.samdevelop.samtv.data.local.toEntity
import com.samdevelop.samtv.security.CredentialStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class AuthenticationRepositoryImpl(
    private val db: AppDatabase,
    private val prefs: AppPreferences,
    private val credentials: CredentialStore,
    private val connectors: ConnectorProvider,
) : AuthenticationRepository {

    override val profiles: Flow<List<ConnectionProfile>> =
        db.profiles().observeAll().map { list -> list.map { it.toDomain() } }

    override val activeProfile: Flow<ConnectionProfile?> =
        combine(prefs.activeProfileId, profiles) { id, list -> list.firstOrNull { it.id == id } ?: list.firstOrNull() }
            .distinctUntilChanged()

    override suspend fun test(profile: ConnectionProfile, secrets: SourceSecrets): AppResult<AuthenticationResult> =
        when (val c = connectors.build(profile, secrets)) {
            is AppResult.Failure -> c
            is AppResult.Success -> c.value.authenticate()
        }

    override suspend fun saveAndActivate(profile: ConnectionProfile, secrets: SourceSecrets): AppResult<AuthenticationResult> {
        val connector = when (val c = connectors.build(profile, secrets)) {
            is AppResult.Failure -> return c
            is AppResult.Success -> c.value
        }
        val result = connector.authenticate()
        if (result is AppResult.Failure) return result
        try {
            credentials.save(profile.id, secrets)
        } catch (e: Exception) {
            return AppResult.Failure(AppError.Storage("keystore"))
        }
        val previous = db.profiles().get(profile.id)
        db.profiles().upsert(profile.toEntity())
        // Endereço ou usuário mudaram: o cache antigo não pertence mais a esta fonte.
        if (previous != null && (previous.address != profile.address || previous.username != profile.username)) {
            clearCatalog(profile.id)
        }
        connectors.put(profile, connector)
        prefs.setActiveProfile(profile.id)
        return result
    }

    override suspend fun checkActive(): AppResult<AuthenticationResult> {
        val id = activeId() ?: return AppResult.Failure(AppError.NoActiveSource)
        return when (val c = connectors.forProfile(id)) {
            is AppResult.Failure -> c
            is AppResult.Success -> c.value.authenticate()
        }
    }

    override suspend fun setActive(profileId: String) = prefs.setActiveProfile(profileId)

    override suspend fun remove(profileId: String) {
        db.withTransaction {
            clearCatalog(profileId)
            db.favorites().deleteSource(profileId)
            db.history().clear(profileId)
            db.profiles().delete(profileId)
        }
        credentials.delete(profileId)
        connectors.invalidate(profileId)
        if (prefs.activeProfileIdNow() == profileId) {
            prefs.setActiveProfile(db.profiles().all().firstOrNull()?.id)
        }
    }

    override suspend fun secretsFor(profileId: String): SourceSecrets? = credentials.load(profileId)

    private suspend fun clearCatalog(profileId: String) {
        db.channels().deleteSource(profileId)
        db.movies().deleteSource(profileId)
        db.series().deleteSource(profileId)
        db.categories().deleteSource(profileId)
        db.syncState().deleteSource(profileId)
    }

    private suspend fun activeId(): String? =
        prefs.activeProfileIdNow()?.takeIf { db.profiles().get(it) != null } ?: db.profiles().all().firstOrNull()?.id
}
