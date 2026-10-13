package uk.scimone.diafit.dailyrecap

/**
 * Optional morning recap written by an LLM: at 07:00 it sums up yesterday (what went well, what went wrong, what to
 * do better today) in a notification with a chart, and keeps the last two weeks on its own page. Everything lives in
 * this package; the app touches it at a few hook points, each guarded by [ENABLED]:
 * - `DiafitApp` (Koin module + scheduling), `MainActivity` (the page, the notification link, a debug trigger),
 * - `MenuScreen` (the "Daily recap" row).
 *
 * Besides this switch the user has their own: the recap is off until it is switched on on its page, and it needs an
 * AI connection (Menu → AI). Set [ENABLED] to false to hide it; to remove it, delete this package and those hooks.
 */
object DailyRecapFeature {
    const val ENABLED = true
}
