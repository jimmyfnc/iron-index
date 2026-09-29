package com.ironindex.app

import org.junit.Assert.*
import org.junit.Test

class SetupTest {
    private val cable = Equipment(id = "cable", type = "Cable machine", brand = "Matrix", settings = listOf("Attachment", "Pulley height"))
    private val alex = "alex"
    private fun entry(setup: List<SetupValue> = emptyList()) =
        WorkoutExercise(exercise = Exercise(id = "pushdown", name = "Triceps pushdown"), equipmentId = cable.id, equipmentSnapshot = cable, setup = setup)

    @Test fun eachPersonKeepsTheirOwnSetupAndBlanksAreDropped() {
        val e = entry().withSetup(null, mapOf("Attachment" to " Rope ", "Pulley height" to "")).withSetup(alex, mapOf("Attachment" to "V-bar"))
        assertEquals(mapOf("Attachment" to "Rope"), e.setupFor(null))
        assertEquals(mapOf("Attachment" to "V-bar"), e.setupFor(alex))
        val cleared = e.withSetup(null, mapOf("Attachment" to ""))
        assertTrue(cleared.setupFor(null).isEmpty())
        assertEquals(mapOf("Attachment" to "V-bar"), cleared.setupFor(alex))
    }

    @Test fun lastSetupIsThisPersonsMostRecentRecordedOne() {
        fun session(start: Long, setup: List<SetupValue>) = Workout(name = "S$start", startedAt = start, finishedAt = start + 1,
            exercises = listOf(entry(setup).copy(sets = listOf(SetEntry(weight = "50", reps = "10", complete = true), SetEntry(weight = "40", reps = "10", complete = true, participantId = alex)))))
        val old = session(1_000, listOf(SetupValue(null, "Attachment", "Straight bar"), SetupValue(alex, "Attachment", "Rope")))
        val newer = session(2_000, listOf(SetupValue(alex, "Attachment", "V-bar"))) // you recorded nothing this time
        val mine = completedHistory(listOf(old, newer), null)
        assertEquals(1_000L to mapOf("Attachment" to "Straight bar"), lastSetup(mine, null))
        assertEquals(2_000L to mapOf("Attachment" to "V-bar"), lastSetup(completedHistory(listOf(old, newer), alex), alex))
        assertNull(lastSetup(emptyList(), null))
    }

    @Test fun settingNamesFollowTheMachineThenAnyRecordedExtras() {
        assertEquals(listOf("Attachment", "Pulley height", "Handle"), settingNames(cable, mapOf("Handle" to "Wide", "attachment" to "Rope")))
        assertEquals(listOf("Seat height"), cleanSettingNames(listOf(" Seat height ", "seat HEIGHT", "", "  ")))
        assertEquals("Attachment Rope · Pulley height 12", setupLabel(listOf("Attachment", "Pulley height"), mapOf("Pulley height" to "12", "Attachment" to "Rope")))
    }

    @Test fun valuesRecordedUnderAnotherSpellingStillShow() {
        val names = settingNames(Equipment(type = "Cable machine", settings = listOf("attachment")), mapOf("Attachment" to "Rope"))
        assertEquals(listOf("attachment"), names)
        assertEquals(mapOf("attachment" to "Rope"), mapOf("Attachment" to "Rope").keyedBy(names))
        assertEquals("attachment Rope", setupLabel(names, mapOf("Attachment" to "Rope")))
    }

    @Test fun resavingTheSameSetupChangesNothing() {
        val e = entry(listOf(SetupValue(null, "Attachment", "Rope"), SetupValue(alex, "Attachment", "V-bar")))
        assertSame(e, e.withSetup(null, mapOf("Attachment" to "Rope", "Pulley height" to "")))
    }

    @Test fun suggestionsFitTheMachineType() {
        assertTrue(suggestedSettings("Smith machine").isEmpty())
        assertTrue(suggestedSettings("T-bar row").isEmpty())
        assertEquals(listOf("Attachment", "Pulley height"), suggestedSettings("Cable machine"))
        assertEquals(listOf("Seat height"), suggestedSettings("Chest press machine"))
        assertEquals(listOf("Back pad", "Foot position"), suggestedSettings("Leg press"))
        assertTrue(suggestedSettings("Dumbbells").isEmpty())
        assertTrue(suggestedSettings("Barbell").isEmpty())
    }

    @Test fun setupIsPerSessionAndLeavesWithADetachedPartner() {
        val w = Workout(name = "Arms", partners = listOf(WorkoutPartner(alex, "Alex", trackSets = true)),
            exercises = listOf(entry(listOf(SetupValue(null, "Attachment", "Rope"), SetupValue(alex, "Attachment", "V-bar")))))
        assertTrue(w.freshCopy(template = true).exercises.single().setup.isEmpty())
        assertEquals(listOf(SetupValue(null, "Attachment", "Rope")), w.detachPartner(alex).exercises.single().setup)
    }

    @Test fun olderSavedDataWithoutSetupStillLoads() {
        val e = storageJson.decodeFromString<WorkoutExercise>("""{"id":"e","exercise":{"id":"x","name":"Row"}}""")
        assertTrue(e.setup.isEmpty())
        assertTrue(storageJson.decodeFromString<Equipment>("""{"id":"m","type":"Cable machine"}""").settings.isEmpty())
        val round = storageJson.decodeFromString<Workout>(storageJson.encodeToString(Workout.serializer(), Workout(name = "W", exercises = listOf(entry(listOf(SetupValue(alex, "Attachment", "Rope")))))))
        assertEquals("Rope", round.exercises.single().setupFor(alex)["Attachment"])
    }
}
