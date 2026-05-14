package cvut.fit.energyStats.domain.dto

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

data class WeatherForecastDTO(
    val todayWeather: DailyWeatherDTO,
    val todayHourly: Map<Int, HourlyWeatherDTO>,
    val tomorrowWeather: DailyWeatherDTO,
    val tomorrowHourly: Map<Int, HourlyWeatherDTO>,
)

data class DailyWeatherDTO(
    val date: LocalDate,
    val temperatureUnit: String,
    val temperatureMin: Double,
    val temperatureMax: Double,
    val sunrise: LocalDateTime,
    val sunset: LocalDateTime,
    val weatherCode: Int,
    val sunshineDuration: Double,
    val daylightDuration: Double,
)

data class HourlyWeatherDTO(
    val cloudiness: Double,
    val irradiance: Double,
    val weatherCode: Int,
    val humidity: Int,
)
