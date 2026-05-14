package cvut.fit.energyStats.service.forecastService

import cvut.fit.energyStats.domain.dto.ProductionForecastDataDTO
import cvut.fit.energyStats.repository.generationForecastCache.ProductionForecastCacheRepository
import cvut.fit.energyStats.repository.siteRepository.SiteRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.springframework.stereotype.Service

@Service
class ProductionForecastCacheService(
    private val cacheRepository: ProductionForecastCacheRepository,
    private val siteRepository: SiteRepository,
) {
    fun removeFromCache(siteId: Int) {
        cacheRepository.removeFromCache(siteId)
        siteRepository.setCacheTimestamp(siteId, null)
    }

    fun storeToCache(
        siteId: Int,
        productionForecastDataDTO: ProductionForecastDataDTO,
        currentTimeAtSite: LocalDateTime,
    ) {
        cacheRepository.storeToCache(siteId, productionForecastDataDTO)
        siteRepository.setCacheTimestamp(siteId, currentTimeAtSite)
    }

    fun getFromCache(siteId: Int): ProductionForecastDataDTO = cacheRepository.getFromCache(siteId)

    fun getDateFromCache(siteId: Int): LocalDate? = cacheRepository.getDateFromCache(siteId)

    fun getCacheTimestamp(siteId: Int): LocalDateTime? = siteRepository.getCacheTimestamp(siteId)
}
