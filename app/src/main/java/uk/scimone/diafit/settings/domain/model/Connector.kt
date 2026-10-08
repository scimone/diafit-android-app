package uk.scimone.diafit.settings.domain.model

/** A kind of data the app shows. Each one is fed by exactly one selected [Connector] (or none). */
enum class DataType(val label: String) {
    CGM("Glucose (CGM)"),
    FOOD("Food & carbs"),
    BOLUS("Bolus insulin"),
    BASAL("Basal insulin"),
    HEART_RATE("Heart rate"),
    STEPS("Steps"),
    SLEEP("Sleep"),
    EXERCISE("Exercise"),
    /** Profile switches and temporary targets (the insulin profile in force). */
    PROFILE("Profile & targets"),
    /** Pump and sensor events: site / pod / insulin / sensor / battery changes, notes. */
    DEVICE("Device status");

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
    val description: String
) {
    NIGHTSCOUT(
        "Nightscout",
        setOf(DataType.CGM, DataType.BOLUS, DataType.FOOD, DataType.BASAL, DataType.PROFILE, DataType.DEVICE),
        setOf(DataType.CGM, DataType.BOLUS, DataType.FOOD, DataType.BASAL, DataType.PROFILE, DataType.DEVICE),
        "Your Nightscout site: glucose, insulin, carbs, temp basals, profile and sensor / pump changes, live and as history."
    ),
    AAPS(
        "AndroidAPS", setOf(DataType.BOLUS, DataType.FOOD, DataType.BASAL, DataType.PROFILE, DataType.DEVICE),
        emptySet(),
        "Boluses, carbs, temp basals and profile changes broadcast live by AAPS (no history)."
    ),
    XDRIP(
        "xDrip+", setOf(DataType.CGM), emptySet(),
        "Glucose broadcast live by xDrip+ (no history)."
    ),
    JUGGLUCO(
        "Juggluco", setOf(DataType.CGM), emptySet(),
        "Glucose broadcast live by Juggluco (no history)."
    ),
    HEALTH_CONNECT(
        "Health Connect",
        setOf(DataType.CGM, DataType.HEART_RATE, DataType.STEPS, DataType.SLEEP, DataType.EXERCISE),
        setOf(DataType.CGM, DataType.HEART_RATE, DataType.STEPS, DataType.SLEEP, DataType.EXERCISE),
        "Heart rate, steps, sleep, exercise and glucose that other apps wrote to Health Connect."
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
