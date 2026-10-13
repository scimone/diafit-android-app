package uk.scimone.diafit.core.presentation

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Text that a ViewModel / domain class wants to show without resolving it itself, so it follows the
 * app language when it is finally drawn (see docs/i18n-plan.md, rule 7).
 */
sealed interface UiText {
    data class Raw(val value: String) : UiText
    class Res(@StringRes val id: Int, vararg val args: Any) : UiText {
        override fun equals(other: Any?) = other is Res && id == other.id && args.contentEquals(other.args)
        override fun hashCode() = 31 * id + args.contentHashCode()
    }
    class Plural(@PluralsRes val id: Int, val count: Int, vararg val args: Any) : UiText {
        override fun equals(other: Any?) =
            other is Plural && id == other.id && count == other.count && args.contentEquals(other.args)
        override fun hashCode() = 31 * (31 * id + count) + args.contentHashCode()
    }

    fun asString(context: Context): String = when (this) {
        is Raw -> value
        is Res -> context.getString(id, *args.resolved(context))
        is Plural -> context.resources.getQuantityString(id, count, *args.resolved(context))
    }

    @Composable
    fun asString(): String = asString(LocalContext.current)

    companion object {
        fun of(@StringRes id: Int, vararg args: Any): UiText = Res(id, *args)
        fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): UiText = Plural(id, count, *args)
    }
}

/** Nested [UiText] arguments are resolved too, so messages can be composed without concatenating. */
private fun Array<out Any>.resolved(context: Context): Array<Any> =
    Array(size) { i -> (this[i] as? UiText)?.asString(context) ?: this[i] }
