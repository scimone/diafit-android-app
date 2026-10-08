package uk.scimone.diafit.journal.presentation.model

import uk.scimone.diafit.core.domain.model.PumpEventEntity
import uk.scimone.diafit.core.domain.model.PumpEventNormalizer
import uk.scimone.diafit.core.domain.model.TemporaryTarget
import uk.scimone.diafit.core.domain.model.summary
import uk.scimone.diafit.core.domain.model.toProfileSwitch
import uk.scimone.diafit.core.domain.model.toTemporaryTarget

/** A device or therapy milestone from AAPS (pod/site change, profile switch, note, ...). */
data class PumpEventUi(
    override val id: Int,
    override val timeUtc: Long,
    val title: String,
    val notes: String?,
    /** The temporary target of a profile switch (or the target itself for a target-only switch). */
    val target: TemporaryTarget? = null,
    /** Profile switch values shown on the right of the card; null = not set (100 %, no shift). */
    val percentage: Int? = null,
    val timeShiftHours: Int? = null,
    val durationMinutes: Int? = null,
    /** Every stored event this card stands for (a site change and an insulin change at once are one card). */
    val ids: List<Int> = listOf(id),
    /** Where it was imported from ("AAPS", "Nightscout"), if known. */
    val source: String? = null,
    val icon: PumpEventIcon = PumpEventIcon.GENERIC
) : JournalEntryUi {
    override val kind: JournalEntryKind get() = JournalEntryKind.DEVICE

    /** Profile switches and target-only switches open the detail page instead of the plain dialog. */
    val hasDetailPage: Boolean
        get() = title.equals("Profile Switch", ignoreCase = true) || title.equals("Temporary Target", ignoreCase = true)
}

enum class PumpEventIcon { PUMP, SENSOR, WARNING, GENERIC }

/** Site / insulin / pod changes use the pump icon, sensor changes the sensor, announcements the warning. */
fun pumpEventIcon(eventType: String): PumpEventIcon = when {
    eventType.contains("sensor", true) -> PumpEventIcon.SENSOR
    eventType.contains("announcement", true) -> PumpEventIcon.WARNING
    eventType.contains("site", true) || eventType.contains("insulin change", true) || eventType.contains("pod", true) ||
        eventType.contains("cannula", true) || eventType.contains("reservoir", true) -> PumpEventIcon.PUMP
    else -> PumpEventIcon.GENERIC
}

/** "ALARM_PUMP_EXPIRED" -> "Alarm pump expired". */
private fun prettyNote(note: String): String =
    if (note.any { it.isLowerCase() }) note else note.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }

/** One card for a site change and an insulin change logged together (a new pod / infusion set with fresh insulin). */
fun List<PumpEventEntity>.toMergedChangeUi(): PumpEventUi {
    val first = minBy { it.timestampUtc }
    return PumpEventUi(
        first.id, first.timestampUtc, "Site & insulin change", null,
        ids = map { it.id }, source = first.originOf(), icon = PumpEventIcon.PUMP
    )
}

private fun PumpEventEntity.originOf(): String? = PumpEventNormalizer.sourceOf(this)

/** [target] is the temporary target paired with this event when it is a profile switch. */
fun PumpEventEntity.toUi(target: TemporaryTarget? = null): PumpEventUi {
    toProfileSwitch()?.let { sw ->
        return PumpEventUi(
            id, timestampUtc, "Profile Switch", sw.baseName, target,
            percentage = sw.percentage.takeIf { it != 100 },
            timeShiftHours = sw.timeShiftHours.takeIf { it != 0 },
            durationMinutes = sw.durationMinutes.takeIf { it > 0 }, source = originOf()
        )
    }
    toTemporaryTarget()?.let { t ->
        return PumpEventUi(id, timestampUtc, "Temporary Target", t.reason.ifEmpty { null }, t, durationMinutes = t.durationMinutes.takeIf { it > 0 })
    }
    val warning = eventType.equals("Announcement", ignoreCase = true)
    return PumpEventUi(
        id, timestampUtc, if (warning) "Warning" else eventType,
        summary()?.let { if (warning) prettyNote(it) else it }, target,
        source = originOf(), icon = pumpEventIcon(eventType)
    )
}
