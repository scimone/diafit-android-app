package uk.scimone.diafit.core.data.networking

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import uk.scimone.diafit.core.domain.util.networking.NetworkError
import uk.scimone.diafit.core.domain.util.networking.Result
import uk.scimone.diafit.core.data.networking.dto.NightscoutEntryDto
import uk.scimone.diafit.core.data.networking.util.constructUrl
import uk.scimone.diafit.core.data.networking.util.safeCall
import uk.scimone.diafit.settings.domain.usecase.GetNightscoutConfigUseCase
import java.time.Instant

class NightscoutApi(
    private val client: HttpClient,
    private val getNightscoutConfig: GetNightscoutConfigUseCase
) {

    suspend fun getCgmEntries(startDate: String, endDate: String = Instant.now().toString()): Result<List<NightscoutEntryDto>, NetworkError> {
        val config = getNightscoutConfig()
        val url = constructUrl("/api/v1/entries/sgv.json", baseUrl = config.baseUrl, apiKey = config.apiKey)

        return safeCall {
            client.get(url) {
                parameter("find[dateString][\$gte]", startDate)
                parameter("find[dateString][\$lte]", endDate)
                parameter("count", 10000000)
            }
        }
    }
}
