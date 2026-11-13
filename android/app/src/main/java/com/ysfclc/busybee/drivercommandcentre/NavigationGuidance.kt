package com.ysfclc.busybee.drivercommandcentre

/**
 * One announcement per approach band, then the next manoeuvre only after
 * the current turn has actually been passed. This keeps GPS ticks from
 * advancing through every nearby instruction at once.
 */
internal object NavigationGuidance {
    fun nextAnnouncementStage(distanceMeters: Double, currentStage: Int): Int {
        require(distanceMeters.isFinite())
        require(currentStage in 0..3)
        return when {
            distanceMeters <= 35.0 && currentStage < 3 -> 3
            distanceMeters <= 120.0 && currentStage < 2 -> 2
            distanceMeters <= 450.0 && currentStage < 1 -> 1
            else -> currentStage
        }
    }

    fun announcement(distanceMeters: Double, instruction: String, stage: Int): String {
        require(distanceMeters.isFinite())
        require(stage in 1..3)
        val cleanInstruction = instruction.trim().ifBlank { "Continue along the highlighted route." }
        val spokenInstruction = cleanInstruction.replaceFirstChar { it.lowercase() }
        return if (stage == 3) {
            "Now, $spokenInstruction"
        } else {
            "In ${distanceMeters.toInt().coerceAtLeast(0)} metres, $spokenInstruction"
        }
    }
}
