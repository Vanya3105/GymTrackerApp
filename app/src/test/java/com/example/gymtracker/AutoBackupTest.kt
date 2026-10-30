package com.example.gymtracker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class AutoBackupTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private class FakeFolder(var failing: Boolean = false) : BackupFolder {
        val files = linkedMapOf<String, ByteArray>()
        override fun list() = files.keys.map { BackupFile(it, it) }
        override fun write(name: String, bytes: ByteArray) {
            if (failing) throw IOException("gone")
            files[name] = bytes
        }
        override fun delete(file: BackupFile) { files.remove(file.id) }
    }

    @Before
    fun setUp() {
        LocalStorage.resetForTests()
        context.deleteDatabase("gymtracker.db")
        context.filesDir.listFiles()?.forEach { it.delete() }
        context.getSharedPreferences("gym_tracker_auto_backup", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(DriveBackup.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        LocalStorage.saveUserProfile(context, UserProfile(hasCompletedOnboarding = true, currentWeightKg = 80f))
    }

    @After
    fun tearDown() = LocalStorage.resetForTests()

    private fun at(text: String) = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse(text)!!.time

    @Test
    fun writesTheSameFileAsSaveBackup_andSkipsWhenNothingChanged() {
        val folder = FakeFolder()
        assertTrue(AutoBackup.run(context, folder, force = false, nowMillis = at("2026-10-29 18:30")))
        val name = "gymtracker-backup-2026-10-29-1830.json"
        assertEquals(listOf(name), folder.files.keys.toList())
        val written = JSONObject(String(folder.files.getValue(name)))
        assertTrue(StoreCodec.looksLikeBackup(written))
        assertEquals(JSONObject(LocalStorage.exportJsonBackupString(context)).toString(), written.toString())

        // Nothing changed: no new file.
        assertFalse(AutoBackup.run(context, folder, force = false, nowMillis = at("2026-10-29 19:00")))
        // "Back up now" writes anyway; the same day keeps only the newest.
        assertTrue(AutoBackup.run(context, folder, force = true, nowMillis = at("2026-10-29 19:05")))
        assertEquals(listOf("gymtracker-backup-2026-10-29-1905.json"), folder.files.keys.toList())

        LocalStorage.addWeightLog(context, 79.5f)
        assertTrue(AutoBackup.run(context, folder, force = false, nowMillis = at("2026-10-30 07:00")))
        assertEquals(2, folder.files.size)
    }

    @Test
    fun aFailure_isShown_andClearedByTheNextBackup() {
        val folder = FakeFolder(failing = true)
        assertFalse(AutoBackup.run(context, folder, force = true, nowMillis = at("2026-10-29 18:30")))
        AutoBackup.refreshStatus(context)
        assertNotNull(AutoBackup.status.error)

        folder.failing = false
        assertTrue(AutoBackup.run(context, folder, force = true, nowMillis = at("2026-10-29 18:31")))
        AutoBackup.refreshStatus(context)
        assertNull(AutoBackup.status.error)
        assertEquals(at("2026-10-29 18:31"), AutoBackup.status.lastSuccessMillis)
    }

    @Test
    fun keepsSevenDays_thenOnePerWeekForFourWeeks() {
        fun name(day: String, time: String = "1200") = "gymtracker-backup-$day-$time.json"
        val days = (1..31).map { String.format(Locale.US, "2026-10-%02d", it) } +
            (1..30).map { String.format(Locale.US, "2026-09-%02d", it) }
        val names = days.map { name(it) } + name("2026-10-31", "0800") + "notes.txt" + "gymtracker_backup.json"

        val deleted = AutoBackup.backupsToDelete(names).toSet()
        val kept = names - deleted

        assertTrue("other files are left alone", "notes.txt" in kept && "gymtracker_backup.json" in kept)
        // The newest of each of the last 7 days (an earlier one the same day goes).
        (25..31).forEach { assertTrue(name("2026-10-$it") in kept) }
        assertTrue(name("2026-10-31", "0800") in deleted)
        // Then the newest of each of the 4 weeks before: Oct 24 (Sat, week of Oct 19), Oct 18, Oct 11, Oct 4.
        listOf("2026-10-24", "2026-10-18", "2026-10-11", "2026-10-04").forEach { assertTrue(it, name(it) in kept) }
        assertEquals(7 + 4 + 2, kept.size)
    }

    @Test
    fun driveAndFolderBackups_keepTheirOwnState() {
        val folder = FakeFolder()
        val drive = FakeFolder()
        assertTrue(AutoBackup.run(context, folder, force = false, nowMillis = at("2026-10-30 08:00")))
        // The Drive destination hasn't backed this data up yet, so it isn't skipped as "unchanged".
        assertTrue(AutoBackup.run(context, drive, force = false, nowMillis = at("2026-10-30 08:01"), prefsName = DriveBackup.PREFS))
        drive.failing = true
        assertFalse(AutoBackup.run(context, drive, force = true, nowMillis = at("2026-10-30 08:02"), prefsName = DriveBackup.PREFS) { "drive error" })
        DriveBackup.refreshStatus(context)
        AutoBackup.refreshStatus(context)
        assertEquals("drive error", DriveBackup.status.error)
        assertNull(AutoBackup.status.error)
    }

    @Test
    fun driveDates() {
        val utc = java.util.TimeZone.getTimeZone("UTC")
        val expected = java.util.Calendar.getInstance(utc).apply { clear(); set(2026, 9, 30, 9, 15, 2) }.timeInMillis
        assertEquals(expected, parseRfc3339("2026-10-30T09:15:02.123Z"))
        assertEquals(expected, parseRfc3339("2026-10-30T09:15:02Z"))
        assertEquals(0L, parseRfc3339("soon"))
    }
}
