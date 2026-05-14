package cvut.fit.energyStats.service.forecastService

import com.fasterxml.jackson.annotation.JsonProperty
import cvut.fit.energyStats.domain.dto.SiteDTO
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class OpenMeteoClient(
    @param:Qualifier("openMeteoAPIClient") val apiClient: RestClient,
    val objectMapper: ObjectMapper,
) {
    fun getDailyWeatherForecastFromAPI(siteDTO: SiteDTO): OpenMeteoDailyResponse {
        val dailyReq =
            "?latitude=${siteDTO.lat}&longitude=${siteDTO.lon}" +
                "&daily=temperature_2m_min," +
                "temperature_2m_max," +
                "sunrise," +
                "sunset," +
                "weather_code," +
                "sunshine_duration," +
                "daylight_duration" +
                "&timezone=${siteDTO.timezone}" +
                "&forecast_days=2"
        return callAPI<OpenMeteoDailyResponse>(dailyReq)
    }

    fun getHourlyWeatherForecastFromAPI(siteDTO: SiteDTO): OpenMeteoHourlyResponse {
        val hourlyReq =
            "?latitude=${siteDTO.lat}&longitude=${siteDTO.lon}" +
                "&hourly=global_tilted_irradiance,cloud_cover,weather_code,relative_humidity_2m" +
                "&tilt=${siteDTO.dec}&azimuth=${siteDTO.azm}"
        return callAPI<OpenMeteoHourlyResponse>(hourlyReq)
    }

    private inline fun <reified T : Any> callAPI(request: String): T {
        try {
            val response =
                apiClient
                    .get()
                    .uri(request)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus({ !it.is2xxSuccessful }) { _, response ->
                        val errorBody = objectMapper.readValue(response.body, OpenMeteoError::class.java)
                        throw IllegalArgumentException(errorBody.reason)
                    }.body<T>()
            if (response == null) {
                throw IllegalStateException("failed to collect weather data")
            }
            return response
        } catch (e: Exception) {
            throw IllegalArgumentException("failed to get weather forecast data")
        }
    }

    data class OpenMeteoDailyResponse(
        val daily: DailyData,
        @param:JsonProperty("daily_units") val dailyUnits: Map<String, String>,
    )

    data class DailyData(
        val time: List<LocalDate>,
        @param:JsonProperty("temperature_2m_min") val tempMin: List<Double>,
        @param:JsonProperty("temperature_2m_max") val tempMax: List<Double>,
        val sunrise: List<LocalDateTime>,
        val sunset: List<LocalDateTime>,
        @param:JsonProperty("weather_code") val weatherCode: List<Int>,
        @param:JsonProperty("sunshine_duration") val sunshineDuration: List<Double>,
        @param:JsonProperty("daylight_duration") val daylightDuration: List<Double>,
    )

    data class OpenMeteoError(
        val reason: String,
    )

    data class OpenMeteoHourlyResponse(
        val hourly: HourlyData,
        @param:JsonProperty("hourly_units") val hourlyUnits: Map<String, String>,
    )

    data class HourlyData(
        val time: List<LocalDateTime>,
        @param:JsonProperty("global_tilted_irradiance") val globalTiltedIrradiance: List<Double>,
        @param:JsonProperty("cloud_cover") val cloudCover: List<Double>,
        @param:JsonProperty("weather_code") val weatherCode: List<Int>,
        @param:JsonProperty("relative_humidity_2m") val humidity: List<Int>,
    )
}
