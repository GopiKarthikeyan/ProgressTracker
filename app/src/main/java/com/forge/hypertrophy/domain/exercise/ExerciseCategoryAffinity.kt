package com.forge.hypertrophy.domain.exercise

import com.forge.hypertrophy.domain.model.SlotCategory

/**
 * Whether an exercise is allowed when picking for a given [SlotCategory].
 * An exercise may match multiple categories (e.g. skill + core).
 */
object ExerciseCategoryAffinity {
    fun matches(
        category: SlotCategory,
        name: String,
        skillId: Long?,
        primaryMuscles: List<String>,
    ): Boolean {
        val key = name.trim().lowercase()
        val muscles = primaryMuscles.map { it.uppercase() }.toSet()
        return when (category) {
            SlotCategory.SKILL -> isSkill(key, skillId)
            SlotCategory.CORE -> isCore(key, muscles) || (isSkill(key, skillId) && muscles.contains("CORE"))
            SlotCategory.PREP -> isPrep(key)
            SlotCategory.MOBILITY -> isMobility(key)
            SlotCategory.COOLDOWN -> isCooldown(key)
            SlotCategory.CARDIO -> isCardio(key)
            SlotCategory.ISOLATION -> isIsolation(key) && !isSkill(key, skillId)
            SlotCategory.COMPOUND -> isCompound(key, skillId, muscles)
        }
    }

    private fun isSkill(key: String, skillId: Long?): Boolean {
        if (skillId != null) return true
        return key.containsAny(
            "handstand", "planche", "front lever", "back lever", "muscle-up", "muscle up",
            "l-sit", "lsit", "one-arm pull", "oap", "human flag", "elbow lever", "manna",
        )
    }

    private fun isCore(key: String, muscles: Set<String>): Boolean {
        if (muscles.contains("CORE")) return true
        return key.containsAny(
            "ab rollout", "ab wheel", "pallof", "hollow", "dragon flag", "russian twist",
            "windshield", "toes-to-bar", "toes to bar", "hanging leg", "leg raise", "plank",
            "crunch", "sit-up", "woodchop",
        )
    }

    private fun isPrep(key: String): Boolean {
        if (isCooldown(key) && !key.containsAny("hang", "scap", "shrug", "bridge", "rock", "push-up", "pull")) {
            // pure stretches lean cooldown; still allow activation-style prep
        }
        return key.containsAny(
            "wrist rock", "wrist extension", "hip swap", "90/90", "dorsiflex", "ankle",
            "false-grip", "scapular", "pull-apart", "pull apart", "planche lean",
            "first-knuckle", "external rotation", "shrug", "isometric", "hamstring sweep",
            "glute bridge", "dislocate", "crab", "hang", "activation", "warmup", "warm-up",
            "band pull", "prep",
        ) || isMobility(key)
    }

    private fun isMobility(key: String): Boolean {
        return key.containsAny(
            "mobility", "dislocate", "hip swap", "90/90", "cat-cow", "cat cow",
            "world's greatest", "worlds greatest", "leg swing", "scapular", "wrist rock",
            "dorsiflex", "ankle", "open book", "caoss",
        )
    }

    private fun isCooldown(key: String): Boolean {
        return key.containsAny(
            "stretch", "pose", "pigeon", "couch", "pancake", "straddle", "pike stretch",
            "child", "release", "dead hang", "doorway", "pec minor", "lat wall",
            "brachioradialis", "cooldown", "cool-down",
        )
    }

    private fun isCardio(key: String): Boolean {
        return key.containsAny(
            "running", "jogging", "rowing", "bike", "cycle", "jump rope", "jump-rope",
            "burpee", "cardio", "elliptical", "assault", "treadmill", "swim", "jog ",
        ) || key == "run" || key == "row" || key == "jog"
    }

    private fun isIsolation(key: String): Boolean {
        return key.containsAny(
            "curl", "raise", "fly", "crossover", "pushdown", "pushaway", "extension",
            "kickback", "shrug", "calf", "adductor", "abductor", "pullover", "pushaway",
            "face pull", "high pull", "lateral", "preacher", "skull", "concentration",
            "hammer", "spider", "rear delt", "leg curl", "hamstring curl",
        )
    }

    private fun isCompound(key: String, skillId: Long?, muscles: Set<String>): Boolean {
        if (isSkill(key, skillId)) return false
        if (isIsolation(key) && !key.containsAny("press", "row", "squat", "deadlift", "dip", "pull-up", "pull up")) {
            return false
        }
        if (isCardio(key)) return false
        if (isCooldown(key) && !key.containsAny("press", "squat", "deadlift", "row", "pull")) return false
        // Default: compounds and general strength movements
        if (key.containsAny(
                "squat", "deadlift", "rdl", "press", "bench", "row", "pull-up", "pull up",
                "chin-up", "dip", "lunge", "thrust", "hack", "pulldown", "clean", "snatch",
                "push-up", "push up", "muscle-up",
            )
        ) {
            return true
        }
        // Leftover non-isolation strength work
        return !isPrep(key) && !isMobility(key) && !isCooldown(key) && !isCore(key, muscles)
    }

    private fun String.containsAny(vararg needles: String): Boolean =
        needles.any { this.contains(it) }
}
