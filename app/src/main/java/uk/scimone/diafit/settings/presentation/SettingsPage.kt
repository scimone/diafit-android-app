package uk.scimone.diafit.settings.presentation

/** The subpages behind the account menu; each is a full-screen page opened from `MenuScreen`. */
enum class SettingsPage(val title: String) {
    CONNECTED_APPS("Connected apps"),
    BACKEND("Backend sync"),
    AI("AI"),
    FOOD_DB("Food database"),
    DISPLAY("Display"),
    NOTIFICATIONS("Notifications"),
    PERMISSIONS("Phone permissions")
}
