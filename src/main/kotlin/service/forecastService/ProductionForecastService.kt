package cvut.fit.energyStats.service.forecastService

import cvut.fit.energyStats.domain.dto.ProductionForecastDataDTO
import cvut.fit.energyStats.service.forecastService.ForecastSolarClient.ProductionForecastResponseData
import cvut.fit.energyStats.service.siteService.SiteService
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.temporal.ChronoUnit
import kotlin.time.Clock

@Service
class ProductionForecastService(
    private val siteService: SiteService,
    private val cacheService: ProductionForecastCacheService,
    private val forecastSolarClient: ForecastSolarClient,
    private val clock: Clock = Clock.System,
) {
    private val refetchAfterMinutes = 60
    private val log = LoggerFactory.getLogger(javaClass)

    fun checkConfigurationValidity(
        lat: Double,
        lon: Double,
    ): Boolean = forecastSolarClient.checkConfigurationValidity(lat, lon)

    fun getForecast(siteId: Int): ProductionForecastDataDTO {
        val siteDTO = siteService.getSiteById(siteId)
        val currentDateTimeAtSite = clock.now().toLocalDateTime(siteDTO.timezone)
        if (shouldTryUpdate(siteId, currentDateTimeAtSite)) {
            try {
                log.info("trying to get from API for $siteId")
                val siteDTO = siteService.getSiteById(siteId)
                val data = processData(forecastSolarClient.getForecastFromAPI(siteDTO))
                cacheService.removeFromCache(siteId)
                cacheService.storeToCache(siteId, data, currentDateTimeAtSite)
                return data
            } catch (e: IllegalArgumentException) {
                if (!isUpToDate(siteId, currentDateTimeAtSite)) {
                    throw IllegalArgumentException("error fetching production forecast data: ${e.message}")
                }
            }
        }
        log.info("getting from cache for $siteId")
        return cacheService.getFromCache(siteId)
    }

    private fun isUpToDate(
        siteId: Int,
        currentTime: LocalDateTime,
    ): Boolean {
        val cachedDate = cacheService.getDateFromCache(siteId)
        return (cachedDate != null && cachedDate == currentTime.date)
    }

    private fun shouldTryUpdate(
        siteId: Int,
        currentDateTime: LocalDateTime,
    ): Boolean {
        val cachedTimestamp = cacheService.getCacheTimestamp(siteId) ?: return true
        val minutesBetween =
            ChronoUnit.MINUTES.between(
                cachedTimestamp.toJavaLocalDateTime(),
                currentDateTime.toJavaLocalDateTime(),
            )
        return !isUpToDate(siteId, cachedTimestamp) || minutesBetween >= refetchAfterMinutes
    }

    // data are in Wh
    private fun processData(data: ProductionForecastResponseData): ProductionForecastDataDTO {
        val cutDateFrom =
            data.message.info.time
                .indexOf('+')
        val todayDate =
            LocalDateTime
                .parse(
                    data.message.info.time
                        .substring(0, cutDateFrom),
                ).date
        val tomorrowDate = todayDate.plus(DatePeriod(days = 1))
        val todayData =
            buildMap {
                for (i: Int in 0..23) {
                    put(todayDate.atTime(i, 0), 0.0)
                }
            }.toMutableMap()
        val tomorrowData =
            buildMap {
                for (i: Int in 0..23) {
                    put(tomorrowDate.atTime(i, 0), 0.0)
                }
            }.toMutableMap()
        val todayDay = todayDate.day
        data.result.forEach {
            val dateTime = LocalDateTime.parse(it.key.replace(' ', 'T'))
            val key = dateTime.date.atTime(dateTime.hour, 0)
            val day = dateTime.day
            if (day == todayDay) {
                todayData[key] = it.value / 1000.0
            } else {
                tomorrowData[key] = it.value / 1000.0
            }
        }
        return ProductionForecastDataDTO(todayData, tomorrowData)
    }
}
