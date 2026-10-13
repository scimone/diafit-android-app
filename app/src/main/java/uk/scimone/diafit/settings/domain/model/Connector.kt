package uk.scimone.diafit.settings.domain.model

import androidx.annotation.StringRes
import uk.scimone.diafit.R

/** A kind of data the app shows. Each one is fed by exactly one selected [Connector] (or none). */
enum class DataType(@StringRes val labelRes: Int) {
    CGM(R.string.datatype_cgm),
    FOOD(R.string.datatype_food),
    BOLUS(R.string.datatype_bolus),
    BASAL(R.string.datatype_basal),
    HEART_RATE(R.string.datatype_heart_rate),
    STEPS(R.string.datatype_steps),
    SLEEP(R.string.datatype_sleep),
    EXERCISE(R.string.datatype_exercise),
    /** Profile switches and temporary targets (the insulin profile in force). */
    PROFILE(R.string.datatype_profile),
    /** Pump and sensor events: site / pod / insulin / sensor / battery changes, notes. */
    DEVICE(R.string.datatype_device);

    val isActivity: Boolean get() = this in ACTIVITY

    companion object {
        val ACTIVITY = setOf(HEART_RATE, STEPS, SLEEP, EXERCISE)
    }
}

/**
 * A place data comes from. The user can connect any number of them; [provides] is what the connector
 * delivers going forward, [canBackfill] what it can also hand over for a past time range.
 */
enum class Connector(
    val displayName: String,
    val provides: Set<DataType>,
    val canBackfill: Set<DataType>,
    @StringRes val descriptionRes: Int
) {
    NIGHTSCOUT(
        "Nightscout",
        setOf(DataType.CGM, DataType.BOLUS, DataType.FOOD, DataType.BASAL, DataType.PROFILE, DataType.DEVICE),
        setOf(DataType.CGM, DataType.BOLUS, DataType.FOOD, DataType.BASAL, DataType.PROFILE, DataType.DEVICE),
        R.string.connector_nightscout_desc
    ),
    AAPS(
        "AndroidAPS", setOf(DataType.BOLUS, DataType.FOOD, DataType.BASAL, DataType.PROFILE, DataType.DEVICE),
        emptySet(),
        R.string.connector_aaps_desc
    ),
    XDRIP(
        "xDrip+", setOf(DataType.CGM), emptySet(),
        R.string.connector_xdrip_desc
    ),
    JUGGLUCO(
        "Juggluco", setOf(DataType.CGM), emptySet(),
        R.string.connector_juggluco_desc
    ),
    HEALTH_CONNECT(
        "Health Connect",
        setOf(DataType.CGM, DataType.HEART_RATE, DataType.STEPS, DataType.SLEEP, DataType.EXERCISE),
        setOf(DataType.CGM, DataType.HEART_RATE, DataType.STEPS, DataType.SLEEP, DataType.EXERCISE),
        R.string.connector_health_connect_desc
    );

    fun toCgmSource(): CgmSource? = when (this) {
        NIGHTSCOUT -> CgmSource.NIGHTSCOUT
        XDRIP -> CgmSource.XDRIP
        JUGGLUCO -> CgmSource.JUGGLUCO
        HEALTH_CONNECT -> CgmSource.HEALTH_CONNECT
        AAPS -> null
    }
}

fun connectorsProviding(type: DataType): List<Connector> = Connector.values().filter { type in it.provides }
fun connectorsBackfilling(type: DataType): List<Connector> = Connector.values().filter { type in it.canBackfill }
