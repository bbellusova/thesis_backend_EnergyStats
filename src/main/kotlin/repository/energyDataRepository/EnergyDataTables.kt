package cvut.fit.energyStats.repository.energyDataRepository

import cvut.fit.energyStats.repository.siteRepository.Site
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.datetime

sealed class EnergyDataTable(
    name: String,
    value: String,
) : Table(name = name) {
    val timestamp = datetime("timestamp")
    val valuekWh = double(value)
    val siteId = integer("site_id").references(Site.siteId)
    override val primaryKey = PrimaryKey(timestamp, siteId)
}

object EnergyConsumptionFromTheGridO :
    EnergyDataTable("EnergyConsumptionFromTheGrid", "consumed_kWh")

object EnergyProductionTotalO : EnergyDataTable("EnergyProductionTotal", "produced_kWh")

object EnergyProductionToTheGridO : EnergyDataTable("EnergyProductionToTheGrid", "produced_kWh")
