package cvut.fit.energyStats.repository.generationForecastCache

import cvut.fit.energyStats.domain.dto.Entries
import cvut.fit.energyStats.domain.dto.ProductionForecastDataDTO
import cvut.fit.energyStats.repository.generationForecastCache.ProductionForecastCache.siteId
import cvut.fit.energyStats.repository.generationForecastCache.ProductionForecastCache.timestamp
import cvut.fit.energyStats.repository.generationForecastCache.ProductionForecastCache.valuekWh
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.min
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.jdbc.batchUpsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Repository

@Repository
class ProductionForecastCacheRepository {
    fun removeFromCache(site: Int) {
        transaction { ProductionForecastCache.deleteWhere { ProductionForecastCache.siteId eq site } }
    }

    fun storeToCache(
        site: Int,
        productionForecastDataDTO: ProductionForecastDataDTO,
    ) {
        val data =
            productionForecastDataDTO.todayData.plus(productionForecastDataDTO.tomorrowData).toList()
        transaction {
            ProductionForecastCache.batchUpsert(data) {
                this[siteId] = site
                this[timestamp] = it.first
                this[valuekWh] = it.second
            }
        }
    }

    fun getFromCache(site: Int): ProductionForecastDataDTO {
        val todayData: Entries = mutableMapOf()
        val tomorrowData: Entries = mutableMapOf()
        val date = getDateFromCache(site)
        transaction {
            val q = ProductionForecastCache.selectAll().where(siteId eq site)

            q.forEach {
                if (it[timestamp].date == date) {
                    todayData[it[timestamp]] = it[valuekWh]
                } else {
                    tomorrowData[it[timestamp]] = it[valuekWh]
                }
            }
        }
        return ProductionForecastDataDTO(todayData, tomorrowData)
    }

    fun getDateFromCache(site: Int): LocalDate? {
        return transaction {
            val q =
                ProductionForecastCache
                    .select(timestamp.date().min())
                    .where { siteId eq site }
                    .withDistinct(true)
                    .firstOrNull()

            return@transaction q?.get(timestamp.date().min())
        }
    }
}
