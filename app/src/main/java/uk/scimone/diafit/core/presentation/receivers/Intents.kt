package uk.scimone.diafit.core.presentation.receivers

interface Intents {

    companion object {
        // juggluco -> diafit
        const val JUGGLUCO_NEW_CGM = "glucodata.Minute"

        // xdrip -> diafit
        const val XDRIP_NEW_CGM = "com.eveningoutpost.dexdrip.BgEstimate"

        // AAPS -> diafit
        const val NSCLIENT_NEW_TREATMENT = "info.nightscout.client.NEW_TREATMENT"
        const val NSCLIENT_NEW_PROFILE = "info.nightscout.client.NEW_PROFILE"
        const val NSCLIENT_NEW_DEVICE_STATUS = "info.nightscout.client.NEW_DEVICESTATUS"
        const val NSCLIENT_NEW_FOOD = "info.nightscout.client.NEW_FOOD"
        const val NSCLIENT_NEW_CGM = "info.nightscout.client.NEW_SGV"

        const val EXTRA_STATUSLINE = "com.eveningoutpost.dexdrip.Extras.Statusline"
        const val ACTION_NEW_EXTERNAL_STATUSLINE = "com.eveningoutpost.dexdrip.ExternalStatusline"
        const val RECEIVER_PERMISSION =
            "com.eveningoutpost.dexdrip.permissions.RECEIVE_EXTERNAL_STATUSLINE"

    }
}