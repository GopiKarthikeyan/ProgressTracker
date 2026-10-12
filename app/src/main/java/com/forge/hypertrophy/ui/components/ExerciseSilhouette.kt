package com.forge.hypertrophy.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.forge.hypertrophy.R
import com.forge.hypertrophy.domain.dayplan.DayPlanSectionKind
import com.forge.hypertrophy.domain.model.SlotCategory

object ExerciseSilhouetteResolver {
    fun resolve(
        exerciseName: String?,
        category: SlotCategory? = null,
        sectionKind: DayPlanSectionKind? = null,
    ): Int {
        val key = exerciseName?.trim()?.lowercase().orEmpty()
        if (key.isNotEmpty()) {
            exactDrawable(key)?.let { return it }
            keywordDrawable(key)?.let { return it }
        }
        val cat = category ?: sectionKind?.toSlotCategory()
        return categoryDrawable(cat)
    }

    /**
     * Exact names for every library exercise/skill and prep/cooldown checklist text.
     * Keys are lowercase trimmed names.
     */
    @DrawableRes
    private fun exactDrawable(key: String): Int? = ExactNames[key]

    private val ExactNames: Map<String, Int> = mapOf(
        "90/90 hip swaps" to R.drawable.sil_9090_hip_swap,
        "ab rollout" to R.drawable.sil_ab_wheel,
        "active ankle dorsiflexion" to R.drawable.sil_ankle_dorsiflex,
        "active scapular shrugs" to R.drawable.sil_shrug,
        "adductor machine" to R.drawable.sil_adductor,
        "anterior delt release" to R.drawable.sil_shoulder_stretch,
        "archer pull-up" to R.drawable.sil_exercise_pull_up,
        "assisted pistol squat" to R.drawable.sil_pistol,
        "australian pull-up" to R.drawable.sil_exercise_pull_up,
        "back lever" to R.drawable.sil_back_lever,
        "band chest press" to R.drawable.sil_db_press,
        "band curl" to R.drawable.sil_curl,
        "band deadlift" to R.drawable.sil_exercise_deadlift,
        "band external rotation" to R.drawable.sil_external_rotation,
        "band external rotations" to R.drawable.sil_external_rotation,
        "band face pull" to R.drawable.sil_face_pull,
        "band good morning" to R.drawable.sil_hinge,
        "band lateral walk" to R.drawable.sil_abductor,
        "band pull-apart" to R.drawable.sil_pull_apart,
        "band pull-aparts" to R.drawable.sil_pull_apart,
        "band row" to R.drawable.sil_exercise_row,
        "band shoulder dislocate" to R.drawable.sil_pull_apart,
        "band squat" to R.drawable.sil_exercise_squat,
        "band tricep pushdown" to R.drawable.sil_pushdown,
        "banded shoulder dislocates" to R.drawable.sil_pull_apart,
        "barbell back squat" to R.drawable.sil_exercise_squat,
        "barbell bench press" to R.drawable.sil_bench,
        "barbell curl" to R.drawable.sil_curl,
        "barbell deadlift" to R.drawable.sil_exercise_deadlift,
        "barbell front squat" to R.drawable.sil_front_squat,
        "barbell good morning" to R.drawable.sil_hinge,
        "barbell hip thrust" to R.drawable.sil_glute_bridge,
        "barbell lunge" to R.drawable.sil_exercise_squat,
        "barbell overhead press" to R.drawable.sil_press,
        "barbell row" to R.drawable.sil_exercise_row,
        "barbell shrug" to R.drawable.sil_shrug,
        "barbell standing calf raise" to R.drawable.sil_calf,
        "belt squat" to R.drawable.sil_exercise_squat,
        "bodyweight dip" to R.drawable.sil_dip,
        "box jump" to R.drawable.sil_exercise_squat,
        "brachioradialis stretch" to R.drawable.sil_wrist,
        "burpee" to R.drawable.sil_pushup,
        "cable crunch" to R.drawable.sil_hollow,
        "cable curl" to R.drawable.sil_curl,
        "cable face pull high" to R.drawable.sil_face_pull,
        "cable lateral raise" to R.drawable.sil_raise,
        "cable pull-through" to R.drawable.sil_hinge,
        "cable reverse fly" to R.drawable.sil_rear_delt,
        "cable tricep kickback" to R.drawable.sil_pushdown,
        "cable tricep pushaway" to R.drawable.sil_pushdown,
        "cable woodchop" to R.drawable.sil_woodchop,
        "chest-supported db row" to R.drawable.sil_exercise_row,
        "child's pose" to R.drawable.sil_childs_pose,
        "chin-up" to R.drawable.sil_exercise_pull_up,
        "close-grip bench press" to R.drawable.sil_bench,
        "concentration curl" to R.drawable.sil_curl,
        "couch stretch" to R.drawable.sil_hip_flexor,
        "crab reaches" to R.drawable.sil_crab,
        "db bulgarian split squat" to R.drawable.sil_exercise_squat,
        "db cross hammer curl" to R.drawable.sil_hammer_curl,
        "db curl" to R.drawable.sil_curl,
        "db floor fly" to R.drawable.sil_fly,
        "db front raise" to R.drawable.sil_raise,
        "db hammer curl" to R.drawable.sil_hammer_curl,
        "db high pull (rear delts)" to R.drawable.sil_upright_row,
        "db lateral raise" to R.drawable.sil_raise,
        "db overhead tricep extension" to R.drawable.sil_oh_tricep,
        "db pullover" to R.drawable.sil_straight_arm,
        "db rear delt fly" to R.drawable.sil_rear_delt,
        "db romanian deadlift" to R.drawable.sil_rdl,
        "db shrug" to R.drawable.sil_shrug,
        "db spider curl" to R.drawable.sil_curl,
        "db standing calf raise" to R.drawable.sil_calf,
        "db step-up" to R.drawable.sil_exercise_squat,
        "db tricep kickback" to R.drawable.sil_pushdown,
        "db walking lunge" to R.drawable.sil_exercise_squat,
        "dead hang" to R.drawable.sil_hang,
        "deep lat overhead stretch" to R.drawable.sil_lat_overhead_stretch,
        "diamond push-up" to R.drawable.sil_pushup,
        "doorway pec stretch" to R.drawable.sil_doorway_stretch,
        "dragon flag" to R.drawable.sil_dragon_flag,
        "dragon flag negative" to R.drawable.sil_dragon_flag,
        "dynamic hamstring sweeps" to R.drawable.sil_hamstring_stretch,
        "elbow lever" to R.drawable.sil_elbow_lever,
        "ez-bar preacher curl" to R.drawable.sil_curl,
        "ez-bar skull crusher" to R.drawable.sil_skull,
        "ez-bar upright row" to R.drawable.sil_upright_row,
        "face pull" to R.drawable.sil_face_pull,
        "false-grip hangs" to R.drawable.sil_active_hang,
        "first-knuckle push-ups" to R.drawable.sil_pushup,
        "flat db press" to R.drawable.sil_db_press,
        "forearm flexor release" to R.drawable.sil_wrist,
        "front lever" to R.drawable.sil_front_lever,
        "glute bridge" to R.drawable.sil_glute_bridge,
        "glute bridges" to R.drawable.sil_glute_bridge,
        "goblet squat" to R.drawable.sil_exercise_squat,
        "hack squat" to R.drawable.sil_exercise_squat,
        "hamstring curl" to R.drawable.sil_leg_curl,
        "handstand" to R.drawable.sil_handstand,
        "handstand practice" to R.drawable.sil_handstand,
        "handstand push-up" to R.drawable.sil_hspu,
        "hanging leg raise" to R.drawable.sil_hanging_raise,
        "hanging toes-to-bar" to R.drawable.sil_hanging_raise,
        "high-to-low cable crossover" to R.drawable.sil_cable_fly,
        "hip abductor machine" to R.drawable.sil_abductor,
        "hollow body hold" to R.drawable.sil_hollow,
        "hollow body rock" to R.drawable.sil_hollow_rock,
        "human flag" to R.drawable.sil_human_flag,
        "incline barbell bench press" to R.drawable.sil_incline_press,
        "incline db curl" to R.drawable.sil_curl,
        "incline db press" to R.drawable.sil_incline_press,
        "inverted row" to R.drawable.sil_exercise_row,
        "jump squat" to R.drawable.sil_exercise_squat,
        "l-sit" to R.drawable.sil_lsit,
        "lat pulldown" to R.drawable.sil_lat_pulldown,
        "lat wall stretch" to R.drawable.sil_lat_overhead_stretch,
        "leg extension" to R.drawable.sil_leg_extension,
        "leg press" to R.drawable.sil_leg_press,
        "light bicep isometrics" to R.drawable.sil_curl,
        "lying db extension" to R.drawable.sil_skull,
        "lying leg curl" to R.drawable.sil_leg_curl,
        "machine chest press" to R.drawable.sil_db_press,
        "machine lat pulldown" to R.drawable.sil_lat_pulldown,
        "machine row" to R.drawable.sil_exercise_row,
        "machine shoulder press" to R.drawable.sil_press,
        "manna" to R.drawable.sil_lsit,
        "muscle-up" to R.drawable.sil_muscle_up,
        "muscle-up negative" to R.drawable.sil_muscle_up,
        "nordic hamstring curl" to R.drawable.sil_nordic,
        "one-arm db row" to R.drawable.sil_exercise_row,
        "one-arm pull-up" to R.drawable.sil_exercise_pull_up,
        "one-arm pull-up practice" to R.drawable.sil_exercise_pull_up,
        "overhead tricep stretch" to R.drawable.sil_oh_tricep,
        "pallof press" to R.drawable.sil_pallof,
        "passive dead hang" to R.drawable.sil_hang,
        "pec deck" to R.drawable.sil_fly,
        "pec minor wall stretch" to R.drawable.sil_doorway_stretch,
        "pigeon pose" to R.drawable.sil_pigeon,
        "pike push-up" to R.drawable.sil_pike_pushup,
        "pistol squat" to R.drawable.sil_pistol,
        "pjr pullover" to R.drawable.sil_straight_arm,
        "planche" to R.drawable.sil_planche,
        "planche leans" to R.drawable.sil_planche,
        "planche leans (3 × 10s)" to R.drawable.sil_planche,
        "plank" to R.drawable.sil_plank,
        "pseudo planche push-up" to R.drawable.sil_pike_pushup,
        "pull-up" to R.drawable.sil_exercise_pull_up,
        "push-up" to R.drawable.sil_pushup,
        "rear delt machine" to R.drawable.sil_rear_delt,
        "reverse ez-bar curl" to R.drawable.sil_curl,
        "ring dip" to R.drawable.sil_dip,
        "ring push-up" to R.drawable.sil_pushup,
        "ring row" to R.drawable.sil_exercise_row,
        "romanian deadlift" to R.drawable.sil_rdl,
        "scapular pull-up" to R.drawable.sil_scap_pull,
        "scapular pull-ups" to R.drawable.sil_scap_pull,
        "scapular push-ups" to R.drawable.sil_scap_push,
        "seated cable row" to R.drawable.sil_exercise_row,
        "seated calf raise" to R.drawable.sil_seated_calf,
        "seated db overhead press" to R.drawable.sil_press,
        "seated pancake straddle" to R.drawable.sil_butterfly,
        "seated pike stretch" to R.drawable.sil_pike_stretch,
        "side plank" to R.drawable.sil_plank,
        "single-leg glute bridge" to R.drawable.sil_glute_bridge,
        "straddle stretch" to R.drawable.sil_butterfly,
        "straight-arm pushdown" to R.drawable.sil_straight_arm,
        "sumo deadlift" to R.drawable.sil_exercise_deadlift,
        "tricep pushdown" to R.drawable.sil_pushdown,
        "v-grip lat pulldown" to R.drawable.sil_lat_pulldown,
        "wall bicep/pec stretch" to R.drawable.sil_doorway_stretch,
        "wall handstand push-up" to R.drawable.sil_hspu,
        "weighted chin-up" to R.drawable.sil_weighted_pull_up,
        "weighted dip" to R.drawable.sil_dip,
        "weighted pull-up" to R.drawable.sil_weighted_pull_up,
        "weighted push-up" to R.drawable.sil_pushup,
        "weighted russian twist" to R.drawable.sil_twist,
        "weighted standing calf raise" to R.drawable.sil_calf,
        "windshield wiper" to R.drawable.sil_windshield,
        "wrist extensions" to R.drawable.sil_wrist,
        "wrist rocks" to R.drawable.sil_wrist_rocks,
    )

    /**
     * Keyword fallback for library / custom names. Ordered most-specific first.
     */
    @DrawableRes
    private fun keywordDrawable(key: String): Int? {
        return when {
            key.containsAny("handstand push", "hs push", "hspu") -> R.drawable.sil_hspu
            key.containsAny("handstand") -> R.drawable.sil_handstand
            key.containsAny("l-sit", "lsit", "manna") -> R.drawable.sil_lsit
            key.containsAny("dragon flag") -> R.drawable.sil_dragon_flag
            key.containsAny("human flag") -> R.drawable.sil_human_flag
            key.containsAny("elbow lever") -> R.drawable.sil_elbow_lever
            key.containsAny("ab rollout", "ab wheel", "rollout") -> R.drawable.sil_ab_wheel
            key.containsAny("pallof") -> R.drawable.sil_pallof
            key.containsAny("hollow rock") -> R.drawable.sil_hollow_rock
            key.containsAny("hollow") -> R.drawable.sil_hollow
            key.containsAny("woodchop") -> R.drawable.sil_woodchop
            key.containsAny("russian twist", "twist") -> R.drawable.sil_twist
            key.containsAny("windshield") -> R.drawable.sil_windshield
            key.containsAny(
                "toes-to-bar", "toes to bar", "hanging leg", "hanging knee", "leg raise",
            ) -> R.drawable.sil_hanging_raise
            key.containsAny("back lever") -> R.drawable.sil_back_lever
            key.containsAny("front lever") -> R.drawable.sil_front_lever
            key.containsAny("planche") -> R.drawable.sil_planche
            key.containsAny("pistol") -> R.drawable.sil_pistol
            key.containsAny("muscle-up", "muscle up") -> R.drawable.sil_muscle_up
            key.containsAny("scapular pull") -> R.drawable.sil_scap_pull
            key.containsAny(
                "pull-up", "pull up", "pullup", "chin-up", "chin up",
            ) -> R.drawable.sil_exercise_pull_up
            key.containsAny("wrist rock") -> R.drawable.sil_wrist_rocks
            key.containsAny("90/90", "hip swap") -> R.drawable.sil_9090_hip_swap
            key.containsAny("dorsiflex") -> R.drawable.sil_ankle_dorsiflex
            key.containsAny("active hang", "false-grip hang") -> R.drawable.sil_active_hang
            key.containsAny("dead hang", "hang") &&
                !key.contains("hanging") -> R.drawable.sil_hang
            key.containsAny("nordic") -> R.drawable.sil_nordic
            key.containsAny("romanian", "rdl") -> R.drawable.sil_rdl
            key.containsAny(
                "deadlift", "good morning", "hip thrust", "pull-through", "pull through",
            ) -> R.drawable.sil_exercise_deadlift
            key.containsAny("hamstring curl", "leg curl") -> R.drawable.sil_leg_curl
            key.containsAny("leg extension") -> R.drawable.sil_leg_extension
            key.containsAny("leg press") -> R.drawable.sil_leg_press
            key.containsAny("glute bridge", "hip bridge") -> R.drawable.sil_glute_bridge
            key.containsAny("front squat") -> R.drawable.sil_front_squat
            key.containsAny("squat", "lunge", "step-up", "step up", "hack squat", "box jump") ->
                R.drawable.sil_exercise_squat
            key.containsAny("face pull") -> R.drawable.sil_face_pull
            key.containsAny("lat pulldown", "pulldown") &&
                !key.contains("pushdown") &&
                !key.contains("straight-arm") &&
                !key.contains("straight arm") -> R.drawable.sil_lat_pulldown
            key.containsAny("straight-arm", "straight arm", "pullover") ->
                R.drawable.sil_straight_arm
            key.containsAny("pull-apart", "pull apart", "dislocate") -> R.drawable.sil_pull_apart
            key.containsAny("external rotation") -> R.drawable.sil_external_rotation
            key.containsAny("row") && !key.contains("upright") -> R.drawable.sil_exercise_row
            key.containsAny("bench press") -> R.drawable.sil_bench
            key.containsAny("incline") && key.containsAny("press", "bench") ->
                R.drawable.sil_incline_press
            key.containsAny(
                "overhead press", "ohp", "shoulder press", "military press", "push press",
            ) -> R.drawable.sil_press
            key.containsAny("dip") -> R.drawable.sil_dip
            key.containsAny("pike push") -> R.drawable.sil_pike_pushup
            key.containsAny("scapular push") -> R.drawable.sil_scap_push
            key.containsAny(
                "push-up", "push up", "pushup", "floor press", "chest press",
            ) -> R.drawable.sil_pushup
            key.containsAny("press") && !key.contains("leg press") -> R.drawable.sil_press
            key.containsAny("crossover", "cable fly") -> R.drawable.sil_cable_fly
            key.containsAny("rear delt") -> R.drawable.sil_rear_delt
            key.containsAny("fly") -> R.drawable.sil_fly
            key.containsAny("high pull", "upright row") -> R.drawable.sil_upright_row
            key.containsAny("lateral raise", "side raise", "front raise") -> R.drawable.sil_raise
            key.containsAny("shrug") -> R.drawable.sil_shrug
            key.containsAny("seated calf") -> R.drawable.sil_seated_calf
            key.containsAny("calf") -> R.drawable.sil_calf
            key.containsAny("abductor", "lateral walk") -> R.drawable.sil_abductor
            key.containsAny("adductor", "hip adduction") -> R.drawable.sil_adductor
            key.containsAny("hammer") -> R.drawable.sil_hammer_curl
            key.containsAny("curl") -> R.drawable.sil_curl
            key.containsAny("skull", "lying") && key.containsAny("extension", "tricep") ->
                R.drawable.sil_skull
            key.containsAny("overhead") && key.containsAny("tricep", "extension") ->
                R.drawable.sil_oh_tricep
            key.containsAny(
                "tricep", "pushdown", "kickback", "pushaway",
            ) -> R.drawable.sil_pushdown
            key.containsAny("plank") -> R.drawable.sil_plank
            key.containsAny("wrist") -> R.drawable.sil_wrist
            key.containsAny("crab") -> R.drawable.sil_crab
            key.containsAny("child") -> R.drawable.sil_childs_pose
            key.containsAny("pigeon") -> R.drawable.sil_pigeon
            key.containsAny("world's greatest", "worlds greatest") -> R.drawable.sil_world_stretch
            key.containsAny("couch", "hip flexor") -> R.drawable.sil_hip_flexor
            key.containsAny("pike stretch", "forward fold", "pancake", "straddle", "butterfly") ->
                R.drawable.sil_butterfly
            key.containsAny("lat wall", "lat overhead", "deep lat") ->
                R.drawable.sil_lat_overhead_stretch
            key.containsAny("doorway", "pec stretch", "chest stretch") ->
                R.drawable.sil_doorway_stretch
            key.containsAny("hamstring stretch", "hamstring sweep") ->
                R.drawable.sil_hamstring_stretch
            key.containsAny("ankle", "wall calf") -> R.drawable.sil_wall_calf
            key.containsAny("cat-cow", "cat cow", "mobility") -> R.drawable.sil_cat_cow
            key.containsAny(
                "stretch", "release", "rocks", "walkout", "camel", "pose",
            ) -> R.drawable.sil_stretch
            key.containsAny("raise") -> R.drawable.sil_raise
            else -> null
        }
    }

    @DrawableRes
    private fun categoryDrawable(category: SlotCategory?): Int = when (category) {
        SlotCategory.PREP -> R.drawable.sil_category_prep
        SlotCategory.MOBILITY -> R.drawable.sil_category_mobility
        SlotCategory.SKILL -> R.drawable.sil_category_skill
        SlotCategory.COMPOUND -> R.drawable.sil_category_compound
        SlotCategory.ISOLATION -> R.drawable.sil_category_isolation
        SlotCategory.CORE -> R.drawable.sil_category_core
        SlotCategory.CARDIO -> R.drawable.sil_category_cardio
        SlotCategory.COOLDOWN -> R.drawable.sil_category_cooldown
        null -> R.drawable.sil_category_compound
    }

    private fun String.containsAny(vararg needles: String): Boolean =
        needles.any { this.contains(it) }

    private fun DayPlanSectionKind.toSlotCategory(): SlotCategory? = when (this) {
        DayPlanSectionKind.PREP -> SlotCategory.PREP
        DayPlanSectionKind.MOBILITY -> SlotCategory.MOBILITY
        DayPlanSectionKind.SKILL -> SlotCategory.SKILL
        DayPlanSectionKind.COMPOUND -> SlotCategory.COMPOUND
        DayPlanSectionKind.ISOLATION -> SlotCategory.ISOLATION
        DayPlanSectionKind.CORE -> SlotCategory.CORE
        DayPlanSectionKind.CARDIO -> SlotCategory.CARDIO
        DayPlanSectionKind.COOLDOWN -> SlotCategory.COOLDOWN
        DayPlanSectionKind.OTHER -> null
    }
}

@Composable
fun ExerciseSilhouette(
    @DrawableRes drawableRes: Int,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val tint = MaterialTheme.colorScheme.onSurface
    val background = MaterialTheme.colorScheme.surfaceVariant
    val inset = (size * 0.08f).coerceIn(3.dp, 10.dp)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape((size * 0.16f).coerceIn(8.dp, 16.dp)))
            .background(background)
            .padding(inset),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(drawableRes),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier.size(size - inset * 2),
        )
    }
}

@Composable
fun ExerciseSilhouette(
    exerciseName: String?,
    category: SlotCategory? = null,
    sectionKind: DayPlanSectionKind? = null,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    ExerciseSilhouette(
        drawableRes = ExerciseSilhouetteResolver.resolve(exerciseName, category, sectionKind),
        modifier = modifier,
        size = size,
    )
}
