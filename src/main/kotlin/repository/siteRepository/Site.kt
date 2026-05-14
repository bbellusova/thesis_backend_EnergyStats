package cvut.fit.energyStats.repository.siteRepository

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.datetime

object Site : Table("Site") {
    val siteId = integer("site_id").autoIncrement()
    val siteName = varchar("site_name", 256).uniqueIndex()
    val password = varchar("site_password", 450)
    val timezone = varchar("timezone", 256)
    val latitude = double("latitude")
    val longitude = double("longitude")
    val azimuth = double("azimuth")
    val peakPowerKwp = double("peak_power_kwp")
    val declination = double("declination")
    val productionForecastCacheTimestamp = datetime("production_forecast_cache_timestamp").nullable()
    override val primaryKey = PrimaryKey(siteId)
}
