// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.keyang556.kboard.engine

import android.content.Context
import android.util.Log

/**
 * Holds the one engine of the app, which the input method types with and the settings
 * screen may reset. Must only be used from the main thread.
 */
object ChewingEngineHolder {
    private const val TAG = "ChewingEngineHolder"
    private var engine: ChewingEngine? = null

    /** The engine, opened on first use; null if it cannot be, as without its dictionaries. */
    fun get(context: Context): ChewingEngine? {
        engine?.let { return it }
        val appContext = context.applicationContext
        if (!ChewingData.install(appContext)) return null
        val opened = try {
            ChewingData.userDir(appContext).mkdirs()
            ChewingNative.open(
                ChewingData.systemDir(appContext).absolutePath,
                ChewingData.userDictionary(appContext).absolutePath,
            )
        } catch (e: LinkageError) {
            Log.e(TAG, "The Zhuyin engine is missing from this build", e)
            0L
        }
        if (opened == 0L) return null
        return ChewingEngine(opened).also { engine = it }
    }

    /** Closes the engine, which saves what it has learned; [get] opens it again. */
    fun release() {
        engine?.close()
        engine = null
    }

    /** Throws away the phrases learned from the user. */
    fun resetUserData(context: Context) {
        release()
        ChewingData.deleteUserData(context.applicationContext)
    }

    fun version(): String? = try {
        ChewingEngine.version()
    } catch (e: LinkageError) {
        null
    }
}
