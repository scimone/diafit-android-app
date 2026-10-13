package uk.scimone.diafit.settings.presentation

import androidx.annotation.StringRes
import uk.scimone.diafit.R

/** The subpages behind the account menu; each is a full-screen page opened from `MenuScreen`. */
enum class SettingsPage(@StringRes val titleRes: Int) {
    CONNECTED_APPS(R.string.settings_page_connected_apps),
    DATA_SOURCES(R.string.settings_page_data_sources),
    BACKEND(R.string.settings_page_backend),
    AI(R.string.settings_page_ai),
    /** Opened from the AI page; not a menu row of its own. */
    AI_LOG(R.string.settings_page_ai_log),
    MEAL_ESTIMATION(R.string.settings_page_meal_estimation),
    FOOD_DB(R.string.settings_page_food_db),
    DISPLAY(R.string.settings_page_display),
    NOTIFICATIONS(R.string.settings_page_notifications),
    PERMISSIONS(R.string.settings_page_permissions)
}
