package uk.scimone.diafit.mealactivity.domain

import java.security.MessageDigest

/** Stable short hash of canonical text parts; the "input fingerprint" / dataset revision. */
object Fingerprint {
    fun of(parts: Iterable<Any?>): String {
        val md = MessageDigest.getInstance("SHA-256")
        for (p in parts) { md.update((p?.toString() ?: "∅").toByteArray()); md.update(0) }
        return md.digest().take(10).joinToString("") { "%02x".format(it) }
    }
    fun of(vararg parts: Any?): String = of(parts.asList())

    /** Rounded so float noise never changes a fingerprint. */
    fun num(x: Double?): String = if (x == null) "∅" else if (x.isNaN()) "n" else "%.3f".format(java.util.Locale.ROOT, x)
}

/** Fingerprint of the inputs a stored curve was made from, plus everything that changes the result. */
fun curveFingerprint(input: MealInput, engine: MealActivityEngine, modelVersion: Int? = null): String {
    val p = engine.params; val a = engine.adjust
    return Fingerprint.of(
        ActivityVersions.algoKey(engine.grid), p.hashCode(), modelVersion ?: 0, Fingerprint.num(a.timeScale), Fingerprint.num(a.onsetShiftMin), Fingerprint.num(a.compositionGain),
        Fingerprint.num(input.carbsG), Fingerprint.num(input.proteinG), Fingerprint.num(input.fatG), Fingerprint.num(input.fiberG),
        input.speed, input.mealTimeUtc
    )
}
