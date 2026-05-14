package cvut.fit.energyStats.controller.forecastController

import cvut.fit.energyStats.domain.dto.DailyWeatherDTO
import cvut.fit.energyStats.domain.dto.HourlyWeatherDTO
import cvut.fit.energyStats.domain.dto.WeatherForecastDTO
import io.swagger.v3.oas.annotations.media.Schema

data class WeatherForecastResp(
    @field:Schema(description = "Forecast for the current day at the site location")
    val todayWeather: DailyWeatherResp,
    @field:Schema(description = "Forecast for each hour of the current day at the site location")
    val todayHourly: Map<Int, HourlyWeatherResp>,
    @field:Schema(description = "Forecast for each hour of the following day at the site location")
    val tomorrowWeather: DailyWeatherResp,
    val tomorrowHourly: Map<Int, HourlyWeatherResp>,
)

data class DailyWeatherResp(
    @field:Schema(description = "Date of the daily forecast") val date: String,
    @field:Schema(description = "Units of temperature measurements (°C or °F)")
    val temperatureUnit: String,
    @field:Schema(description = "Minimum temperature at 2 meters above the ground")
    val temperatureMin: Double,
    @field:Schema(description = "Maximum temperature at 2 meters above the ground")
    val temperatureMax: Double,
    @field:Schema(description = "Time of sunrise") val sunrise: String,
    @field:Schema(description = "Time of sunset") val sunset: String,
    @field:Schema(
        description =
            "Code of the weather, represents the most common weather condition during the day",
    )
    val weatherCode: Int,
    @field:Schema(description = "Sunshine duration on the day, in seconds")
    val sunshineDuration: Double,
    @field:Schema(description = "Daylight duration on the day, in seconds")
    val daylightDuration: Double,
)

data class HourlyWeatherResp(
    @field:Schema(description = "Cloud cover in %") val cloudiness: Double,
    @field:Schema(
        description =
            "Total radiation received on a tilted pane, in W/m^2, based on the sites azimuth and declination",
    )
    val irradiance: Double,
    @field:Schema(description = "Code of the weather") val weatherCode: Int,
    @field:Schema(description = "Relative humidity at 2 meters above the ground") val humidity: Int,
)

fun WeatherForecastDTO.toWeatherForecastResp(): WeatherForecastResp =
    WeatherForecastResp(
        todayWeather.toDailyWeatherResp(),
        todayHourly.mapValues { (_, value) -> value.toHourlyWeatherResp() },
        tomorrowWeather.toDailyWeatherResp(),
        tomorrowHourly.mapValues { (_, value) -> value.toHourlyWeatherResp() },
    )

fun DailyWeatherDTO.toDailyWeatherResp(): DailyWeatherResp =
    DailyWeatherResp(
        date.toString(),
        temperatureUnit,
        temperatureMin,
        temperatureMax,
        sunrise.toString(),
        sunset.toString(),
        weatherCode,
        sunshineDuration,
        daylightDuration,
    )

fun HourlyWeatherDTO.toHourlyWeatherResp(): HourlyWeatherResp = HourlyWeatherResp(cloudiness, irradiance, weatherCode, humidity)
