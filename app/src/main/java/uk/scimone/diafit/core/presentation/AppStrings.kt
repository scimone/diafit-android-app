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

    /**
     * Text by resource name, for stored messages (patterns) that must be shown in today's language. An argument
     * starting with "@" is itself a string resource name. A plurals resource takes its count from the first
     * argument. An unknown name comes back as it is.
     */
    fun byName(name: String, args: List<String> = emptyList()): String {
        val ctx = app ?: return if (args.isEmpty()) name else "$name$args"
        // Whole numbers become Ints so both %d and %s formats work; everything else stays text.
        val resolved = args.map { a -> if (a.startsWith("@")) byName(a.substring(1)) else (a.toIntOrNull() ?: a) }.toTypedArray<Any>()
        val res = ctx.resources
        val string = res.getIdentifier(name, "string", ctx.packageName)
        if (string != 0) return runCatching { res.getString(string, *resolved) }.getOrDefault(name)
        val plural = res.getIdentifier(name, "plurals", ctx.packageName)
        if (plural != 0) {
            val count = args.firstOrNull()?.toIntOrNull() ?: 0
            return runCatching { res.getQuantityString(plural, count, *resolved) }.getOrDefault(name)
        }
        return name
    }
}
