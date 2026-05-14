package cvut.fit.energyStats.repository.dataAccessMethodRepository

import cvut.fit.energyStats.repository.siteRepository.Site
import org.jetbrains.exposed.v1.core.Table

object DataAccessMethod : Table("DataAccessMethod") {
    val siteId = integer("site_id").references(Site.siteId)
    val dataCategory = varchar("data_category", 64)
    val accessMethod = varchar("access_method", 64)
    val dataProvider = varchar("data_provider", 64).nullable()
    val dataProviderSiteId = integer("provider_site_id").nullable()
    val dataProviderAPIKey = varchar("provider_api_key", 256).nullable()
    override val primaryKey = PrimaryKey(siteId, dataCategory)
}
