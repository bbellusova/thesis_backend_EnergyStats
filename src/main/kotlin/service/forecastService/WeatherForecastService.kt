package cvut.fit.energyStats.service.forecastService

import cvut.fit.energyStats.domain.dto.DailyWeatherDTO
import cvut.fit.energyStats.domain.dto.HourlyWeatherDTO
import cvut.fit.energyStats.domain.dto.WeatherForecastDTO
import cvut.fit.energyStats.service.siteService.SiteService
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinLocalDateTime
import org.springframework.stereotype.Service

@Service
class WeatherForecastService(
    private val siteService: SiteService,
    private val openMeteoClient: OpenMeteoClient,
) {
    fun getForecastFromAPI(siteId: Int): WeatherForecastDTO {
        val siteDTO = siteService.getSiteById(siteId)

        val dailyResponse = openMeteoClient.getDailyWeatherForecastFromAPI(siteDTO)
        val hourlyResponse = openMeteoClient.getHourlyWeatherForecastFromAPI(siteDTO)

        val todayHourly = getHourlyWeatherFromResponse(hourlyResponse, 0)
        val tomorrowHourly = getHourlyWeatherFromResponse(hourlyResponse, 1)

        return WeatherForecastDTO(
            getDailyWeatherFromResponse(dailyResponse, 0, todayHourly),
            todayHourly,
            getDailyWeatherFromResponse(dailyResponse, 1, tomorrowHourly),
            tomorrowHourly,
        )
    }

    // getDailyWeatherCode returns the most common weather code during the day (from sunrise to
    // sunset)
    private fun getDailyWeatherCode(
        hourly: Map<Int, HourlyWeatherDTO>,
        sunriseHour: Int,
        sunsetHour: Int,
    ): Int? {
        if (hourly.isEmpty()) {
            return null
        }
        val weatherCodesOccurrences: MutableMap<Int, Int> = mutableMapOf()
        hourly.entries
            .filter { it.key in sunriseHour..sunsetHour }
            .forEach {
                weatherCodesOccurrences[it.value.weatherCode] =
                    (weatherCodesOccurrences[it.value.weatherCode]?.plus(1)) ?: 1
            }
        val maxOccurrences = weatherCodesOccurrences.values.max()
        return weatherCodesOccurrences.keys
            .filter { weatherCodesOccurrences[it] == maxOccurrences }
            .maxOf { it } // maxOf causes more severe weather code to be picked in case results are tied
    }

    private fun getDailyWeatherFromResponse(
        response: OpenMeteoClient.OpenMeteoDailyResponse,
        idx: Int,
        hourly: Map<Int, HourlyWeatherDTO>,
    ): DailyWeatherDTO {
        val sunrise = response.daily.sunrise[idx].toKotlinLocalDateTime()
        val sunset = response.daily.sunset[idx].toKotlinLocalDateTime()
        return DailyWeatherDTO(
            response.daily.time[idx].toKotlinLocalDate(),
            response.dailyUnits["temperature_2m_min"]
                ?: response.dailyUnits["temperature_2m_min"]
                ?: "°",
            response.daily.tempMin[idx],
            response.daily.tempMax[idx],
            sunrise,
            sunset,
            getDailyWeatherCode(hourly, sunrise.hour, sunset.hour) ?: response.daily.weatherCode[idx],
            response.daily.sunshineDuration[idx],
            response.daily.daylightDuration[idx],
        )
    }

    private fun getHourlyWeatherFromResponse(
        response: OpenMeteoClient.OpenMeteoHourlyResponse,
        idx: Int,
    ): Map<Int, HourlyWeatherDTO> {
        val result = mutableMapOf<Int, HourlyWeatherDTO>()
        for (i in (24 * idx)..(24 * idx + 23)) {
            result[i % 24] =
                HourlyWeatherDTO(
                    response.hourly.cloudCover[i],
                    response.hourly.globalTiltedIrradiance[i],
                    response.hourly.weatherCode[i],
                    response.hourly.humidity[i],
                )
        }
        return result
    }
}
