package com.forge.hypertrophy.ui.components

import com.forge.hypertrophy.R
import com.forge.hypertrophy.domain.model.SlotCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

class ExerciseSilhouetteResolverSpotTest {
    @Test
    fun prepPosesAreDistinctAndSpecific() {
        assertEquals(
            R.drawable.sil_wrist_rocks,
            ExerciseSilhouetteResolver.resolve("Wrist Rocks", SlotCategory.PREP),
        )
        assertEquals(
            R.drawable.sil_9090_hip_swap,
            ExerciseSilhouetteResolver.resolve("90/90 Hip Swaps", SlotCategory.PREP),
        )
        assertEquals(
            R.drawable.sil_ankle_dorsiflex,
            ExerciseSilhouetteResolver.resolve("Active Ankle Dorsiflexion", SlotCategory.PREP),
        )
        assertNotEquals(
            ExerciseSilhouetteResolver.resolve("Wrist Rocks", SlotCategory.PREP),
            ExerciseSilhouetteResolver.resolve("Wrist Extensions", SlotCategory.PREP),
        )
        assertNotEquals(
            ExerciseSilhouetteResolver.resolve("90/90 Hip Swaps", SlotCategory.PREP),
            ExerciseSilhouetteResolver.resolve("Butterfly Stretch", SlotCategory.COOLDOWN),
        )
    }

    @Test
    fun coreMovesAreDistinct() {
        val ab = ExerciseSilhouetteResolver.resolve("Ab Rollout", SlotCategory.CORE)
        val hollow = ExerciseSilhouetteResolver.resolve("Hollow Body Hold", SlotCategory.CORE)
        val pallof = ExerciseSilhouetteResolver.resolve("Pallof Press", SlotCategory.CORE)
        val twist = ExerciseSilhouetteResolver.resolve("Weighted Russian Twist", SlotCategory.CORE)
        val wiper = ExerciseSilhouetteResolver.resolve("Windshield Wiper", SlotCategory.CORE)
        assertEquals(R.drawable.sil_ab_wheel, ab)
        assertEquals(R.drawable.sil_hollow, hollow)
        assertEquals(R.drawable.sil_pallof, pallof)
        assertEquals(R.drawable.sil_twist, twist)
        assertEquals(R.drawable.sil_windshield, wiper)
        assertNotEquals(ab, hollow)
        assertNotEquals(ab, pallof)
    }

    @Test
    fun poseFamiliesStayDistinct() {
        assertNotEquals(
            ExerciseSilhouetteResolver.resolve("Dragon Flag"),
            ExerciseSilhouetteResolver.resolve("Plank"),
        )
        assertNotEquals(
            ExerciseSilhouetteResolver.resolve("Dragon Flag"),
            ExerciseSilhouetteResolver.resolve("Hanging Leg Raise"),
        )
        assertNotEquals(
            ExerciseSilhouetteResolver.resolve("Doorway Pec Stretch"),
            ExerciseSilhouetteResolver.resolve("World's Greatest Stretch"),
        )
        assertNotEquals(
            ExerciseSilhouetteResolver.resolve("Pigeon Pose"),
            ExerciseSilhouetteResolver.resolve("World's Greatest Stretch"),
        )
        assertNotEquals(
            ExerciseSilhouetteResolver.resolve("Lat Wall Stretch"),
            ExerciseSilhouetteResolver.resolve("Doorway Pec Stretch"),
        )
        assertEquals(
            R.drawable.sil_pigeon,
            ExerciseSilhouetteResolver.resolve("Pigeon Pose"),
        )
        assertEquals(
            R.drawable.sil_lat_overhead_stretch,
            ExerciseSilhouetteResolver.resolve("Deep Lat Overhead Stretch"),
        )
        assertEquals(
            R.drawable.sil_doorway_stretch,
            ExerciseSilhouetteResolver.resolve("Doorway Pec Stretch"),
        )
        assertEquals(
            R.drawable.sil_dragon_flag,
            ExerciseSilhouetteResolver.resolve("Dragon Flag"),
        )
    }

    @Test
    fun keywordOrderBugsAreFixed() {
        assertEquals(
            R.drawable.sil_scap_pull,
            ExerciseSilhouetteResolver.resolve("Scapular Pull-up"),
        )
        assertEquals(
            R.drawable.sil_pull_apart,
            ExerciseSilhouetteResolver.resolve("Band Pull-Apart"),
        )
        assertEquals(
            R.drawable.sil_curl,
            ExerciseSilhouetteResolver.resolve("Incline DB Curl"),
        )
        assertEquals(
            R.drawable.sil_leg_press,
            ExerciseSilhouetteResolver.resolve("Leg Press"),
        )
        assertEquals(
            R.drawable.sil_leg_extension,
            ExerciseSilhouetteResolver.resolve("Leg Extension"),
        )
        assertEquals(
            R.drawable.sil_nordic,
            ExerciseSilhouetteResolver.resolve("Nordic Hamstring Curl"),
        )
        assertEquals(
            R.drawable.sil_woodchop,
            ExerciseSilhouetteResolver.resolve("Cable Woodchop"),
        )
        assertEquals(
            R.drawable.sil_windshield,
            ExerciseSilhouetteResolver.resolve("Custom Windshield Move"),
        )
        assertEquals(
            R.drawable.sil_rear_delt,
            ExerciseSilhouetteResolver.resolve("Rear Delt Machine"),
        )
        assertEquals(
            R.drawable.sil_exercise_deadlift,
            ExerciseSilhouetteResolver.resolve("Barbell Deadlift"),
        )
        assertEquals(
            R.drawable.sil_external_rotation,
            ExerciseSilhouetteResolver.resolve("Band External Rotations"),
        )
        assertEquals(
            R.drawable.sil_muscle_up,
            ExerciseSilhouetteResolver.resolve("Muscle-up"),
        )
        assertEquals(
            R.drawable.sil_planche,
            ExerciseSilhouetteResolver.resolve("Planche"),
        )
        assertEquals(
            R.drawable.sil_hspu,
            ExerciseSilhouetteResolver.resolve("Handstand Push-up"),
        )
        assertEquals(
            R.drawable.sil_active_hang,
            ExerciseSilhouetteResolver.resolve("False-Grip Hangs"),
        )
    }

    @Test
    fun fallsBackToCategoryWhenUnknown() {
        assertEquals(
            R.drawable.sil_category_core,
            ExerciseSilhouetteResolver.resolve("Mystery Core Move", SlotCategory.CORE),
        )
    }
}

@RunWith(Parameterized::class)
class LibraryExerciseSilhouetteCoverageTest(
    private val name: String,
    private val expected: Int,
) {
    @Test
    fun resolvesExactDrawable() {
        val resolved = ExerciseSilhouetteResolver.resolve(name, SlotCategory.COMPOUND)
        assertEquals("wrong silhouette for $name", expected, resolved)
        val categoryOnly = ExerciseSilhouetteResolver.resolve(null, SlotCategory.COMPOUND)
        assertNotEquals("library exercise must not be category-only: $name", categoryOnly, resolved)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): Collection<Array<Any>> = listOf(
            arrayOf("Ab Rollout", R.drawable.sil_ab_wheel),
            arrayOf("Adductor Machine", R.drawable.sil_adductor),
            arrayOf("Archer Pull-up", R.drawable.sil_exercise_pull_up),
            arrayOf("Assisted Pistol Squat", R.drawable.sil_pistol),
            arrayOf("Australian Pull-up", R.drawable.sil_exercise_pull_up),
            arrayOf("Back Lever", R.drawable.sil_back_lever),
            arrayOf("Band Chest Press", R.drawable.sil_db_press),
            arrayOf("Band Curl", R.drawable.sil_curl),
            arrayOf("Band Deadlift", R.drawable.sil_exercise_deadlift),
            arrayOf("Band External Rotation", R.drawable.sil_external_rotation),
            arrayOf("Band Face Pull", R.drawable.sil_face_pull),
            arrayOf("Band Good Morning", R.drawable.sil_hinge),
            arrayOf("Band Lateral Walk", R.drawable.sil_abductor),
            arrayOf("Band Pull-Apart", R.drawable.sil_pull_apart),
            arrayOf("Band Row", R.drawable.sil_exercise_row),
            arrayOf("Band Shoulder Dislocate", R.drawable.sil_pull_apart),
            arrayOf("Band Squat", R.drawable.sil_exercise_squat),
            arrayOf("Band Tricep Pushdown", R.drawable.sil_pushdown),
            arrayOf("Barbell Back Squat", R.drawable.sil_exercise_squat),
            arrayOf("Barbell Bench Press", R.drawable.sil_bench),
            arrayOf("Barbell Curl", R.drawable.sil_curl),
            arrayOf("Barbell Deadlift", R.drawable.sil_exercise_deadlift),
            arrayOf("Barbell Front Squat", R.drawable.sil_front_squat),
            arrayOf("Barbell Good Morning", R.drawable.sil_hinge),
            arrayOf("Barbell Hip Thrust", R.drawable.sil_glute_bridge),
            arrayOf("Barbell Lunge", R.drawable.sil_exercise_squat),
            arrayOf("Barbell Overhead Press", R.drawable.sil_press),
            arrayOf("Barbell Row", R.drawable.sil_exercise_row),
            arrayOf("Barbell Shrug", R.drawable.sil_shrug),
            arrayOf("Barbell Standing Calf Raise", R.drawable.sil_calf),
            arrayOf("Belt Squat", R.drawable.sil_exercise_squat),
            arrayOf("Bodyweight Dip", R.drawable.sil_dip),
            arrayOf("Box Jump", R.drawable.sil_exercise_squat),
            arrayOf("Burpee", R.drawable.sil_pushup),
            arrayOf("Cable Crunch", R.drawable.sil_hollow),
            arrayOf("Cable Curl", R.drawable.sil_curl),
            arrayOf("Cable Face Pull High", R.drawable.sil_face_pull),
            arrayOf("Cable Lateral Raise", R.drawable.sil_raise),
            arrayOf("Cable Pull-Through", R.drawable.sil_hinge),
            arrayOf("Cable Reverse Fly", R.drawable.sil_rear_delt),
            arrayOf("Cable Tricep Kickback", R.drawable.sil_pushdown),
            arrayOf("Cable Tricep Pushaway", R.drawable.sil_pushdown),
            arrayOf("Cable Woodchop", R.drawable.sil_woodchop),
            arrayOf("Chest-Supported DB Row", R.drawable.sil_exercise_row),
            arrayOf("Chin-up", R.drawable.sil_exercise_pull_up),
            arrayOf("Close-Grip Bench Press", R.drawable.sil_bench),
            arrayOf("Concentration Curl", R.drawable.sil_curl),
            arrayOf("DB Bulgarian Split Squat", R.drawable.sil_exercise_squat),
            arrayOf("DB Cross Hammer Curl", R.drawable.sil_hammer_curl),
            arrayOf("DB Curl", R.drawable.sil_curl),
            arrayOf("DB Floor Fly", R.drawable.sil_fly),
            arrayOf("DB Front Raise", R.drawable.sil_raise),
            arrayOf("DB Hammer Curl", R.drawable.sil_hammer_curl),
            arrayOf("DB High Pull (Rear Delts)", R.drawable.sil_upright_row),
            arrayOf("DB Lateral Raise", R.drawable.sil_raise),
            arrayOf("DB Overhead Tricep Extension", R.drawable.sil_oh_tricep),
            arrayOf("DB Pullover", R.drawable.sil_straight_arm),
            arrayOf("DB Rear Delt Fly", R.drawable.sil_rear_delt),
            arrayOf("DB Romanian Deadlift", R.drawable.sil_rdl),
            arrayOf("DB Shrug", R.drawable.sil_shrug),
            arrayOf("DB Spider Curl", R.drawable.sil_curl),
            arrayOf("DB Standing Calf Raise", R.drawable.sil_calf),
            arrayOf("DB Step-up", R.drawable.sil_exercise_squat),
            arrayOf("DB Tricep Kickback", R.drawable.sil_pushdown),
            arrayOf("DB Walking Lunge", R.drawable.sil_exercise_squat),
            arrayOf("Dead Hang", R.drawable.sil_hang),
            arrayOf("Diamond Push-up", R.drawable.sil_pushup),
            arrayOf("Dragon Flag", R.drawable.sil_dragon_flag),
            arrayOf("Dragon Flag Negative", R.drawable.sil_dragon_flag),
            arrayOf("EZ-Bar Preacher Curl", R.drawable.sil_curl),
            arrayOf("EZ-Bar Skull Crusher", R.drawable.sil_skull),
            arrayOf("EZ-Bar Upright Row", R.drawable.sil_upright_row),
            arrayOf("Elbow Lever", R.drawable.sil_elbow_lever),
            arrayOf("Face Pull", R.drawable.sil_face_pull),
            arrayOf("Flat DB Press", R.drawable.sil_db_press),
            arrayOf("Front Lever", R.drawable.sil_front_lever),
            arrayOf("Glute Bridge", R.drawable.sil_glute_bridge),
            arrayOf("Goblet Squat", R.drawable.sil_exercise_squat),
            arrayOf("Hack Squat", R.drawable.sil_exercise_squat),
            arrayOf("Hamstring Curl", R.drawable.sil_leg_curl),
            arrayOf("Handstand", R.drawable.sil_handstand),
            arrayOf("Handstand Practice", R.drawable.sil_handstand),
            arrayOf("Handstand Push-up", R.drawable.sil_hspu),
            arrayOf("Hanging Leg Raise", R.drawable.sil_hanging_raise),
            arrayOf("Hanging Toes-to-Bar", R.drawable.sil_hanging_raise),
            arrayOf("High-to-Low Cable Crossover", R.drawable.sil_cable_fly),
            arrayOf("Hip Abductor Machine", R.drawable.sil_abductor),
            arrayOf("Hollow Body Hold", R.drawable.sil_hollow),
            arrayOf("Hollow Body Rock", R.drawable.sil_hollow_rock),
            arrayOf("Human Flag", R.drawable.sil_human_flag),
            arrayOf("Incline Barbell Bench Press", R.drawable.sil_incline_press),
            arrayOf("Incline DB Curl", R.drawable.sil_curl),
            arrayOf("Incline DB Press", R.drawable.sil_incline_press),
            arrayOf("Inverted Row", R.drawable.sil_exercise_row),
            arrayOf("Jump Squat", R.drawable.sil_exercise_squat),
            arrayOf("L-Sit", R.drawable.sil_lsit),
            arrayOf("Lat Pulldown", R.drawable.sil_lat_pulldown),
            arrayOf("Leg Extension", R.drawable.sil_leg_extension),
            arrayOf("Leg Press", R.drawable.sil_leg_press),
            arrayOf("Lying DB Extension", R.drawable.sil_skull),
            arrayOf("Lying Leg Curl", R.drawable.sil_leg_curl),
            arrayOf("Machine Chest Press", R.drawable.sil_db_press),
            arrayOf("Machine Lat Pulldown", R.drawable.sil_lat_pulldown),
            arrayOf("Machine Row", R.drawable.sil_exercise_row),
            arrayOf("Machine Shoulder Press", R.drawable.sil_press),
            arrayOf("Manna", R.drawable.sil_lsit),
            arrayOf("Muscle-up", R.drawable.sil_muscle_up),
            arrayOf("Muscle-up Negative", R.drawable.sil_muscle_up),
            arrayOf("Nordic Hamstring Curl", R.drawable.sil_nordic),
            arrayOf("One-Arm DB Row", R.drawable.sil_exercise_row),
            arrayOf("One-Arm Pull-up", R.drawable.sil_exercise_pull_up),
            arrayOf("One-Arm Pull-up Practice", R.drawable.sil_exercise_pull_up),
            arrayOf("PJR Pullover", R.drawable.sil_straight_arm),
            arrayOf("Pallof Press", R.drawable.sil_pallof),
            arrayOf("Pec Deck", R.drawable.sil_fly),
            arrayOf("Pike Push-up", R.drawable.sil_pike_pushup),
            arrayOf("Pistol Squat", R.drawable.sil_pistol),
            arrayOf("Planche", R.drawable.sil_planche),
            arrayOf("Plank", R.drawable.sil_plank),
            arrayOf("Pseudo Planche Push-up", R.drawable.sil_pike_pushup),
            arrayOf("Pull-up", R.drawable.sil_exercise_pull_up),
            arrayOf("Push-up", R.drawable.sil_pushup),
            arrayOf("Rear Delt Machine", R.drawable.sil_rear_delt),
            arrayOf("Reverse EZ-Bar Curl", R.drawable.sil_curl),
            arrayOf("Ring Dip", R.drawable.sil_dip),
            arrayOf("Ring Push-up", R.drawable.sil_pushup),
            arrayOf("Ring Row", R.drawable.sil_exercise_row),
            arrayOf("Romanian Deadlift", R.drawable.sil_rdl),
            arrayOf("Scapular Pull-up", R.drawable.sil_scap_pull),
            arrayOf("Seated Cable Row", R.drawable.sil_exercise_row),
            arrayOf("Seated Calf Raise", R.drawable.sil_seated_calf),
            arrayOf("Seated DB Overhead Press", R.drawable.sil_press),
            arrayOf("Side Plank", R.drawable.sil_plank),
            arrayOf("Single-Leg Glute Bridge", R.drawable.sil_glute_bridge),
            arrayOf("Straight-Arm Pushdown", R.drawable.sil_straight_arm),
            arrayOf("Sumo Deadlift", R.drawable.sil_exercise_deadlift),
            arrayOf("Tricep Pushdown", R.drawable.sil_pushdown),
            arrayOf("V-Grip Lat Pulldown", R.drawable.sil_lat_pulldown),
            arrayOf("Wall Handstand Push-up", R.drawable.sil_hspu),
            arrayOf("Weighted Chin-up", R.drawable.sil_weighted_pull_up),
            arrayOf("Weighted Dip", R.drawable.sil_dip),
            arrayOf("Weighted Pull-up", R.drawable.sil_weighted_pull_up),
            arrayOf("Weighted Push-up", R.drawable.sil_pushup),
            arrayOf("Weighted Russian Twist", R.drawable.sil_twist),
            arrayOf("Weighted Standing Calf Raise", R.drawable.sil_calf),
            arrayOf("Windshield Wiper", R.drawable.sil_windshield),
        )
    }
}

@RunWith(Parameterized::class)
class ChecklistSilhouetteCoverageTest(
    private val text: String,
    private val expected: Int,
) {
    @Test
    fun resolvesExactDrawable() {
        val resolved = ExerciseSilhouetteResolver.resolve(text, SlotCategory.PREP)
        assertEquals("wrong silhouette for checklist '$text'", expected, resolved)
        assertNotEquals(
            ExerciseSilhouetteResolver.resolve(null, SlotCategory.PREP),
            resolved,
        )
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): Collection<Array<Any>> = listOf(
            arrayOf("Wrist Rocks", R.drawable.sil_wrist_rocks),
            arrayOf("Wrist Extensions", R.drawable.sil_wrist),
            arrayOf("90/90 Hip Swaps", R.drawable.sil_9090_hip_swap),
            arrayOf("Active Ankle Dorsiflexion", R.drawable.sil_ankle_dorsiflex),
            arrayOf("False-Grip Hangs", R.drawable.sil_active_hang),
            arrayOf("Scapular Pull-ups", R.drawable.sil_scap_pull),
            arrayOf("Band Pull-aparts", R.drawable.sil_pull_apart),
            arrayOf("Planche Leans (3 × 10s)", R.drawable.sil_planche),
            arrayOf("First-Knuckle Push-ups", R.drawable.sil_pushup),
            arrayOf("Band External Rotations", R.drawable.sil_external_rotation),
            arrayOf("Active Scapular Shrugs", R.drawable.sil_shrug),
            arrayOf("Light Bicep Isometrics", R.drawable.sil_curl),
            arrayOf("Dynamic Hamstring Sweeps", R.drawable.sil_hamstring_stretch),
            arrayOf("Glute Bridges", R.drawable.sil_glute_bridge),
            arrayOf("Banded Shoulder Dislocates", R.drawable.sil_pull_apart),
            arrayOf("Crab Reaches", R.drawable.sil_crab),
            arrayOf("Scapular Push-ups", R.drawable.sil_scap_push),
            arrayOf("Couch Stretch", R.drawable.sil_hip_flexor),
            arrayOf("Pigeon Pose", R.drawable.sil_pigeon),
            arrayOf("Passive Dead Hang", R.drawable.sil_hang),
            arrayOf("Lat Wall Stretch", R.drawable.sil_lat_overhead_stretch),
            arrayOf("Wall Bicep/Pec Stretch", R.drawable.sil_doorway_stretch),
            arrayOf("Forearm Flexor Release", R.drawable.sil_wrist),
            arrayOf("Doorway Pec Stretch", R.drawable.sil_doorway_stretch),
            arrayOf("Anterior Delt Release", R.drawable.sil_shoulder_stretch),
            arrayOf("Seated Pancake Straddle", R.drawable.sil_butterfly),
            arrayOf("Brachioradialis Stretch", R.drawable.sil_wrist),
            arrayOf("Deep Lat Overhead Stretch", R.drawable.sil_lat_overhead_stretch),
            arrayOf("Seated Pike Stretch", R.drawable.sil_pike_stretch),
            arrayOf("Child's Pose", R.drawable.sil_childs_pose),
            arrayOf("Straddle Stretch", R.drawable.sil_butterfly),
            arrayOf("Pec Minor Wall Stretch", R.drawable.sil_doorway_stretch),
            arrayOf("Overhead Tricep Stretch", R.drawable.sil_oh_tricep),
        )
    }
}
