package cvut.fit.energyStats.service.forecastService

import com.fasterxml.jackson.annotation.JsonProperty
import cvut.fit.energyStats.domain.dto.SiteDTO
import kotlinx.datetime.LocalDateTime
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@Service
class ForecastSolarClient(
    @param:Qualifier("forecastSolarAPIClient") private val apiClient: RestClient,
    private val objectMapper: ObjectMapper,
) {
    fun checkConfigurationValidity(
        lat: Double,
        lon: Double,
    ): Boolean {
        var result = false
        try {
            apiClient
                .get()
                .uri("/check/$lat/$lon")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus({ it.is2xxSuccessful }) { _, _ -> result = true }
                .body<Any>()
        } catch (e: Exception) {
        }
        return result
    }

    fun getForecastFromAPI(siteDTO: SiteDTO): ProductionForecastResponseData {
        try {
            val response =
                apiClient
                    .get()
                    .uri(
                        "/estimate/watthours/period/${siteDTO.lat}/${siteDTO.lon}/${siteDTO.dec}/${siteDTO.azm}/${siteDTO.kwp}",
                    ).accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus({ it.value() == 429 }) { _, response ->
                        val errorBody =
                            objectMapper.readValue(response.body, ErrLimitReachedResponseData::class.java)
                        val cutDateFrom =
                            errorBody.message.ratelimit.retryAt
                                .indexOf('+')
                        val retryDateTime =
                            LocalDateTime.parse(
                                errorBody.message.ratelimit.retryAt
                                    .substring(0, cutDateFrom),
                            )
                        throw IllegalArgumentException("${errorBody.result}. Retry at $retryDateTime")
                    }.onStatus({ !it.is2xxSuccessful }) { _, response ->
                        val errorBody = objectMapper.readValue(response.body, GeneralError::class.java)
                        throw IllegalArgumentException(errorBody.result)
                    }.body<ProductionForecastResponseData>()

            if (response != null) {
                return response
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("failed to collect forecast data: ${e.message}")
        }
        throw IllegalArgumentException("failed to collect forecast data")
    }

    data class ProductionForecastResponseData(
        val result: Map<String, Int>,
        val message: ProductionForecastMessageData,
    )

    data class ProductionForecastMessageData(
        val code: Int,
        val type: String,
        val text: String,
        val pid: String,
        val info: LocationInfo,
        val ratelimit: RateLimit,
    )

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
    data class LocationInfo(
        val latitude: Double,
        val longitude: Double,
        val distance: Double,
        val place: String,
        val timezone: String,
        val time: String,
        val time_utc: String,
    )

    data class RateLimit(
        val zone: String,
        val period: Int,
        val limit: Int,
        val remaining: Int,
    )

    data class ErrLimitReachedResponseData(
        val result: String,
        val message: ErrLimitReachedMessageData,
    )

    data class ErrLimitReachedMessageData(
        val code: Int,
        val type: String,
        val text: String,
        val pid: String,
        val info: List<String>,
        val ratelimit: ErrLimitReachedRateLimit,
    )

    data class ErrLimitReachedRateLimit(
        val zone: String,
        val period: Int,
        val limit: Int,
        @param:JsonProperty("retry-at") val retryAt: String,
    )

    data class GeneralError(
        val result: String,
        val message: Message,
    )

    data class Message(
        val code: Int,
        val type: String,
        val text: String,
    )
}
