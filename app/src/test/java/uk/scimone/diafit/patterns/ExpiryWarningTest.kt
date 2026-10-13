package uk.scimone.diafit.patterns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.scimone.diafit.core.domain.model.DeviceAge
import uk.scimone.diafit.core.domain.model.DeviceKind
import uk.scimone.diafit.core.domain.model.WarnLevel
import uk.scimone.diafit.core.domain.model.expiryAlerts
import uk.scimone.diafit.core.domain.model.warnItems

class ExpiryWarningTest {
    private val h = 3_600_000L
    private fun age(kind: DeviceKind, leftH: Int, life: Int = 72) = DeviceAge(kind, 0L, (life - leftH) * h, life)

    @Test fun nothingAboveInitialThreshold() {
        assertTrue(warnItems(listOf(age(DeviceKind.SENSOR, 30, 336), age(DeviceKind.PATCH, 11)), null).isEmpty())
    }

    @Test fun yellowThenRed() {
        val w = warnItems(listOf(age(DeviceKind.SENSOR, 5, 336), age(DeviceKind.SITE, 0), age(DeviceKind.BATTERY, 30, 720)), null)
        assertEquals(WarnLevel.INITIAL, w.first { it.kind == DeviceKind.SENSOR }.level)
        assertEquals(WarnLevel.URGENT, w.first { it.kind == DeviceKind.SITE }.level)
        assertEquals(WarnLevel.INITIAL, w.first { it.kind == DeviceKind.BATTERY }.level)
    }

    @Test fun insulinByUnits() {
        val a = listOf(age(DeviceKind.INSULIN, 40))
        assertTrue(warnItems(a, 50.0).isEmpty())
        assertEquals(WarnLevel.INITIAL, warnItems(a, 15.0).single().level)
        assertEquals(WarnLevel.URGENT, warnItems(a, 4.0).single().level)
        assertEquals(listOf("INSULIN-units-0-20"), expiryAlerts(a, reservoirUnits = 15.0).map { it.key })
        assertEquals(listOf("INSULIN-units-0-5"), expiryAlerts(a, reservoirUnits = 4.0).map { it.key })
    }
}
