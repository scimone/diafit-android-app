package uk.scimone.diafit.backendsync

/**
 * Optional upload of the local data to a Diafit backend. Everything lives in this package; the app touches it at
 * only a few hook points, each guarded by [ENABLED]:
 * - `DiafitApp` (Koin module + periodic sync), `MainActivity` (sync on app open),
 * - `SettingsScreen` (the "Backend sync" section), `AppDatabase.backendSyncDao()` (read-only queries).
 *
 * Set [ENABLED] to false to hide it; to remove it, delete this package and those hook points.
 */
object BackendSyncFeature {
    const val ENABLED = true
}
