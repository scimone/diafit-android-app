package uk.scimone.diafit.journal.presentation.model

import uk.scimone.diafit.core.domain.model.PumpEventEntity
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
    val durationMinutes: Int? = null
) : JournalEntryUi {
    override val kind: JournalEntryKind get() = JournalEntryKind.DEVICE

    /** Profile switches and target-only switches open the detail page instead of the plain dialog. */
    val hasDetailPage: Boolean
        get() = title.equals("Profile Switch", ignoreCase = true) || title.equals("Temporary Target", ignoreCase = true)
}

/** [target] is the temporary target paired with this event when it is a profile switch. */
fun PumpEventEntity.toUi(target: TemporaryTarget? = null): PumpEventUi {
    toProfileSwitch()?.let { sw ->
        return PumpEventUi(
            id, timestampUtc, "Profile Switch", sw.baseName, target,
            percentage = sw.percentage.takeIf { it != 100 },
            timeShiftHours = sw.timeShiftHours.takeIf { it != 0 },
            durationMinutes = sw.durationMinutes.takeIf { it > 0 }
        )
    }
    toTemporaryTarget()?.let { t ->
        return PumpEventUi(id, timestampUtc, "Temporary Target", t.reason.ifEmpty { null }, t, durationMinutes = t.durationMinutes.takeIf { it > 0 })
    }
    return PumpEventUi(id, timestampUtc, eventType, summary(), target)
}
