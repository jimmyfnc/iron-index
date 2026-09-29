package com.ironindex.app

import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import java.time.Instant
import java.time.ZoneId

/**
 * Writes finished sessions to Health Connect (on-device, no account) so Discipline, Samsung Health and
 * other apps can see you trained. Write-only: IronIndex never reads health data.
 *
 * What has been shared is remembered per workout (id → version) in its own preferences file, which is
 * left out of phone backups so a restored phone shares everything afresh. Only new or changed sessions
 * are written, and anything shared that's no longer shareable (deleted, or every one of your sets
 * removed) is deleted — including sessions deleted while sharing was off.
 */
class HealthSync(private val context: Context) {
    val permissions = setOf(HealthPermission.getWritePermission(ExerciseSessionRecord::class))

    enum class Availability { AVAILABLE, NEEDS_UPDATE, UNAVAILABLE }

    /** Built into Android 14+; an app from Play on Android 9–13, which may need installing or updating. */
    val availability: Availability get() = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> Availability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Availability.NEEDS_UPDATE
        else -> Availability.UNAVAILABLE
    }

    private val client by lazy { HealthConnectClient.getOrCreate(context) }
    private val shared = context.getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE)

    suspend fun granted(): Boolean = availability == Availability.AVAILABLE &&
        client.permissionController.getGrantedPermissions().containsAll(permissions)

    /** Brings Health Connect in line with [finishedSessions]. Returns how many sessions are currently shared. */
    suspend fun share(finishedSessions: List<Workout>): Int {
        val done = shared.all.mapNotNull { (id, version) -> (version as? Long)?.let { id to it } }.toMap()
        val plan = healthPlan(finishedSessions, done)
        plan.write.entries.chunked(100).forEach { chunk ->
            client.insertRecords(chunk.map { record(it.value) })
            shared.edit { chunk.forEach { putLong(it.key, it.value.version) } }
        }
        plan.remove.chunked(100).forEach { chunk ->
            client.deleteRecords(ExerciseSessionRecord::class, recordIdsList = emptyList(), clientRecordIdsList = chunk.map(::healthClientId))
            shared.edit { chunk.forEach { remove(it) } }
        }
        return plan.shared
    }

    /** Health Connect's own screen, to allow access again or manage what's been shared. */
    fun settingsIntent(): Intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)

    /** Play Store page to install or update Health Connect (Android 9–13). */
    fun installIntent(): Intent = Intent(Intent.ACTION_VIEW,
        "market://details?id=$PROVIDER&url=healthconnect%3A%2F%2Fonboarding".toUri()).setPackage("com.android.vending")

    private fun record(session: HealthSession): ExerciseSessionRecord {
        val zone = ZoneId.systemDefault().rules
        val start = Instant.ofEpochMilli(session.start)
        val end = Instant.ofEpochMilli(session.end)
        return ExerciseSessionRecord(
            startTime = start, startZoneOffset = zone.getOffset(start),
            endTime = end, endZoneOffset = zone.getOffset(end),
            // Typed in by the lifter as they trained, not recorded by a sensor.
            metadata = Metadata.manualEntry(clientRecordId = session.clientId, clientRecordVersion = session.version),
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            title = session.title,
            notes = session.notes,
        )
    }

    companion object {
        /** Excluded from backups (res/xml): a restored phone's Health Connect starts empty. */
        const val SHARED_PREFS = "health_connect_shared"
        private const val PROVIDER = "com.google.android.apps.healthdata"
    }
}
