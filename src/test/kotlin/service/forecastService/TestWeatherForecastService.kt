package service.forecastService

import cvut.fit.energyStats.domain.dto.DailyWeatherDTO
import cvut.fit.energyStats.domain.dto.HourlyWeatherDTO
import cvut.fit.energyStats.domain.dto.SiteDTO
import cvut.fit.energyStats.domain.dto.WeatherForecastDTO
import cvut.fit.energyStats.service.forecastService.OpenMeteoClient
import cvut.fit.energyStats.service.forecastService.WeatherForecastService
import cvut.fit.energyStats.service.siteService.SiteService
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.TimeZone
import java.time.LocalDate
import java.time.LocalDateTime.of
import kotlin.test.assertEquals

class TestWeatherForecastService :
    FunSpec({
        val mockSiteService: SiteService = mockk()
        val mockOpenMeteoClient: OpenMeteoClient = mockk()
        val service = WeatherForecastService(mockSiteService, mockOpenMeteoClient)

        val validSiteDTO =
            SiteDTO(
                siteId = 0,
                siteName = "testSite",
                password = "secret",
                timezone = TimeZone.of("UTC"),
                lat = 90.0,
                lon = -180.0,
                azm = 0.0,
                kwp = 0.1,
                dec = 90.0,
            )

        test("getForecastFromAPI correctly maps daily and hourly responses") {
            val expectedTodayHourly =
                (0..23).associateWith { i ->
                    HourlyWeatherDTO(
                        cloudiness = (i * 2.0) % 100,
                        irradiance = if (i in 6..19) 500.0 else 0.0,
                        weatherCode = 0,
                        humidity = (i * 3) % 100,
                    )
                }

            val expectedTomorrowHourly =
                (0..23).associateWith { i ->
                    val totalIndex = i + 24
                    HourlyWeatherDTO(
                        cloudiness = (totalIndex * 2.0) % 100,
                        irradiance = if (i in 6..19) 500.0 else 0.0,
                        weatherCode = 3,
                        humidity = (totalIndex * 3) % 100,
                    )
                }

            val expectedResult =
                WeatherForecastDTO(
                    todayWeather =
                        DailyWeatherDTO(
                            date = kotlinx.datetime.LocalDate(2026, 5, 8),
                            temperatureUnit = "°C",
                            temperatureMin = 10.5,
                            temperatureMax = 22.3,
                            sunrise = kotlinx.datetime.LocalDateTime(2026, 5, 8, 5, 30),
                            sunset = kotlinx.datetime.LocalDateTime(2026, 5, 8, 20, 45),
                            weatherCode = 0,
                            sunshineDuration = 36000.0,
                            daylightDuration = 54000.0,
                        ),
                    todayHourly = expectedTodayHourly,
                    tomorrowWeather =
                        DailyWeatherDTO(
                            date = kotlinx.datetime.LocalDate(2026, 5, 9),
                            temperatureUnit = "°C",
                            temperatureMin = 12.0,
                            temperatureMax = 25.1,
                            sunrise = kotlinx.datetime.LocalDateTime(2026, 5, 9, 5, 30),
                            sunset = kotlinx.datetime.LocalDateTime(2026, 5, 9, 20, 45),
                            weatherCode = 3,
                            sunshineDuration = 18000.0,
                            daylightDuration = 54000.0,
                        ),
                    tomorrowHourly = expectedTomorrowHourly,
                )

            every { mockSiteService.getSiteById(validSiteDTO.siteId) } returns validSiteDTO

            every { mockOpenMeteoClient.getDailyWeatherForecastFromAPI(validSiteDTO) } returns
                createDailyResponse()
            every { mockOpenMeteoClient.getHourlyWeatherForecastFromAPI(validSiteDTO) } returns
                createHourlyResponse()

            val result = service.getForecastFromAPI(validSiteDTO.siteId)

            assertEquals(expectedResult, result)
        }
    })

fun createDailyResponse(): OpenMeteoClient.OpenMeteoDailyResponse {
    val days = 2
    return OpenMeteoClient.OpenMeteoDailyResponse(
        dailyUnits =
            mapOf(
                "temperature_2m_min" to "°C",
                "temperature_2m_max" to "°C",
                "sunshine_duration" to "s",
            ),
        daily =
            OpenMeteoClient.DailyData(
                time = List(days) { LocalDate.of(2026, 5, 8).plusDays(it.toLong()) },
                tempMin = listOf(10.5, 12.0),
                tempMax = listOf(22.3, 25.1),
                sunrise = List(days) { of(2026, 5, 8 + it, 5, 30) },
                sunset = List(days) { of(2026, 5, 8 + it, 20, 45) },
                weatherCode = listOf(0, 3),
                sunshineDuration = listOf(36000.0, 18000.0),
                daylightDuration = listOf(54000.0, 54000.0),
            ),
    )
}

fun createHourlyResponse(): OpenMeteoClient.OpenMeteoHourlyResponse {
    val totalHours = 48
    val baseTime = of(2026, 5, 8, 0, 0)

    return OpenMeteoClient.OpenMeteoHourlyResponse(
        hourlyUnits = mapOf("global_tilted_irradiance" to "W/m²", "cloud_cover" to "%"),
        hourly =
            OpenMeteoClient.HourlyData(
                time = List(totalHours) { baseTime.plusHours(it.toLong()) },
                globalTiltedIrradiance =
                    List(totalHours) { i ->
                        val hour = i % 24
                        if (hour in 6..19) 500.0 else 0.0
                    },
                cloudCover = List(totalHours) { (it * 2.0) % 100 },
                weatherCode = List(totalHours) { i -> if (i < 24) 0 else 3 },
                humidity = List(totalHours) { (it * 3) % 100 },
            ),
    )
}
