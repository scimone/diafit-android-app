package uk.scimone.diafit.settings.presentation

/** The subpages behind the account menu; each is a full-screen page opened from `MenuScreen`. */
enum class SettingsPage(val title: String) {
    CONNECTED_APPS("Connected apps"),
    DATA_SOURCES("Data sources"),
    BACKEND("Backend sync"),
    AI("AI"),
    /** Opened from the AI page; not a menu row of its own. */
    AI_LOG("LLM request log"),
    MEAL_ESTIMATION("Meal estimation"),
    FOOD_DB("Food database"),
    DISPLAY("Display"),
    NOTIFICATIONS("Notifications"),
    PERMISSIONS("Phone permissions")
}
