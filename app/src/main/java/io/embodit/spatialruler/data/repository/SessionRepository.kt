package io.embodit.spatialruler.data.repository

import android.content.Context
import io.embodit.spatialruler.data.local.SessionJsonCodec
import io.embodit.spatialruler.domain.model.StoredSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What the app remembers between launches: which anchors are pins (with grade, surface frame and
 * fine-tune offset), which pins are joined by tapes, and the settings. Positions are not stored:
 * they come from the anchors.
 */
interface SessionRepository {
    /** Null when nothing was stored yet or the stored data cannot be read. */
    suspend fun load(): StoredSession?

    suspend fun save(session: StoredSession)
}

/** SharedPreferences-backed store; one JSON document, written synchronously on the IO dispatcher. */
class PrefsSessionRepository(
    context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : SessionRepository {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override suspend fun load(): StoredSession? =
        withContext(io) {
            val text = prefs.getString(KEY, null) ?: return@withContext null
            runCatching { SessionJsonCodec.decode(text) }.getOrNull()
        }

    override suspend fun save(session: StoredSession) {
        withContext(io) { prefs.edit().putString(KEY, SessionJsonCodec.encode(session)).commit() }
    }

    private companion object {
        const val FILE = "spatial_ruler"
        const val KEY = "session_v1"
    }
}
