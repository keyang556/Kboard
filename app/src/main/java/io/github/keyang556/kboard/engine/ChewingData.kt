// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.keyang556.kboard.engine

import android.content.Context
import android.util.Log
import io.github.keyang556.kboard.BuildConfig
import java.io.File
import java.io.IOException

/**
 * Where libchewing's files live on the device. Its dictionaries ship as assets, and are
 * copied out on first use and after every update, since libchewing reads them from files.
 * What libchewing learns from the user is kept apart, and out of backups.
 */
internal object ChewingData {
    private const val TAG = "ChewingData"
    private val SYSTEM_FILES = listOf("tsi.dat", "word.dat", "swkb.dat", "symbols.dat")
    private const val STAMP_FILE = ".installed"

    fun systemDir(context: Context) = File(context.noBackupFilesDir, "chewing")

    fun userDir(context: Context) = File(context.noBackupFilesDir, "chewing-user")

    fun userDictionary(context: Context) = File(userDir(context), "chewing.dat")

    /**
     * Makes sure the dictionaries of this version of the app are in [systemDir]. The input
     * method starts this on a background thread, and may need it done on the main thread.
     */
    @Synchronized
    fun install(context: Context): Boolean {
        val dir = systemDir(context)
        val stamp = File(dir, STAMP_FILE)
        // The APK's modification time changes with every update, also of a debug build.
        val apk = File(context.applicationInfo.sourceDir)
        val wanted = "${BuildConfig.VERSION_CODE}:${apk.lastModified()}"
        val installed = SYSTEM_FILES.all { File(dir, it).isFile } &&
            stamp.isFile && stamp.readText() == wanted
        if (installed) return true

        return try {
            dir.mkdirs()
            for (name in SYSTEM_FILES) {
                val partial = File(dir, "$name.partial")
                context.assets.open(name).use { input ->
                    partial.outputStream().use { output -> input.copyTo(output) }
                }
                if (!partial.renameTo(File(dir, name))) throw IOException("Cannot install $name")
            }
            stamp.writeText(wanted)
            true
        } catch (e: IOException) {
            Log.e(TAG, "Cannot install the dictionaries", e)
            false
        }
    }

    /** Throws away everything libchewing has learned. No engine may be open meanwhile. */
    fun deleteUserData(context: Context) {
        userDir(context).deleteRecursively()
    }
}
