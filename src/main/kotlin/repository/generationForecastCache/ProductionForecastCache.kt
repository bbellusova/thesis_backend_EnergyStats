package cvut.fit.energyStats.repository.generationForecastCache

import cvut.fit.energyStats.repository.siteRepository.Site
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.datetime

object ProductionForecastCache : Table(name = "ProductionForecastCache") {
    val timestamp = datetime("timestamp")
    val valuekWh = double("produced_kwh")
    val siteId = integer("site_id").references(Site.siteId)
    override val primaryKey = PrimaryKey(timestamp, siteId)
}
