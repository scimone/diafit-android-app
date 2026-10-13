package uk.scimone.diafit.core.presentation

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * The application context's resources, for the few places that name something from outside a composable
 * (enum display names such as a meal type, a day label). Prefer [UiText] or `stringResource` wherever a
 * composable or a Context is at hand; see docs/i18n-plan.md. Resolve at use time, never cache the result.
 */
object AppStrings {
    @Volatile private var app: Context? = null

    fun init(context: Context) { app = context.applicationContext }

    fun get(@StringRes id: Int, vararg args: Any): String =
        app?.getString(id, *args) ?: "string#$id"

    fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String =
        app?.resources?.getQuantityString(id, count, *args) ?: "plural#$id"
}
