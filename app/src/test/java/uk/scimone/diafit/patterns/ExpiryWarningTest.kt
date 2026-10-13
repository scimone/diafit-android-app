package uk.scimone.diafit.patterns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uk.scimone.diafit.core.domain.model.DeviceAge
import uk.scimone.diafit.core.domain.model.DeviceKind
import uk.scimone.diafit.core.domain.model.expiryWarning

class ExpiryWarningTest {
    private val h = 3_600_000L
    private fun age(kind: DeviceKind, leftH: Int, life: Int = 72) = DeviceAge(kind, 0L, (life - leftH) * h, life)

    @Test fun onlyBelowLimits() {
        val w = expiryWarning(listOf(age(DeviceKind.SENSOR, 30, 336), age(DeviceKind.PATCH, 11)))
        assertNull(w.sensorMs); assertNull(w.pumpMs)
    }

    @Test fun soonestPumpPartAndSensor() {
        val w = expiryWarning(listOf(age(DeviceKind.SENSOR, 5, 336), age(DeviceKind.SITE, 9), age(DeviceKind.INSULIN, 4)))
        assertEquals(5 * h, w.sensorMs); assertEquals(4 * h, w.pumpMs)
    }
}
