package cvut.fit.energyStats.repository.siteRepository

import cvut.fit.energyStats.domain.dto.SiteDTO
import cvut.fit.energyStats.repository.siteRepository.Site.azimuth
import cvut.fit.energyStats.repository.siteRepository.Site.declination
import cvut.fit.energyStats.repository.siteRepository.Site.latitude
import cvut.fit.energyStats.repository.siteRepository.Site.longitude
import cvut.fit.energyStats.repository.siteRepository.Site.password
import cvut.fit.energyStats.repository.siteRepository.Site.peakPowerKwp
import cvut.fit.energyStats.repository.siteRepository.Site.productionForecastCacheTimestamp
import cvut.fit.energyStats.repository.siteRepository.Site.siteId
import cvut.fit.energyStats.repository.siteRepository.Site.siteName
import cvut.fit.energyStats.repository.siteRepository.Site.timezone
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.stereotype.Repository

@Repository
class SiteRepository {
    fun createSite(siteDTO: SiteDTO): Int {
        val id =
            transaction {
                Site.insert {
                    it[siteName] = siteDTO.siteName
                    it[password] = siteDTO.password!!
                    it[timezone] = siteDTO.timezone.id
                    it[latitude] = siteDTO.lat
                    it[longitude] = siteDTO.lon
                    it[azimuth] = siteDTO.azm
                    it[peakPowerKwp] = siteDTO.kwp
                    it[declination] = siteDTO.dec
                }[siteId]
            }
        return id
    }

    fun getSiteById(id: Int): SiteDTO {
        return transaction {
            return@transaction Site
                .selectAll()
                .where { siteId eq id }
                .singleOrNull()
                ?.toSiteDTO()
                ?: throw IllegalArgumentException("site does not exist")
        }
    }

    fun getSiteByName(name: String): SiteDTO {
        return transaction {
            return@transaction Site
                .selectAll()
                .where { siteName eq name }
                .singleOrNull()
                ?.toSiteDTO()
                ?: throw BadCredentialsException("site does not exist")
        }
    }

    fun siteNameExists(name: String): Boolean {
        return transaction {
            return@transaction !(Site.selectAll().where { siteName eq name }.empty())
        }
    }

    fun updateSiteParameters(
        id: Int,
        siteDTO: SiteDTO,
    ) {
        transaction {
            Site.update({ siteId eq id }) {
                it[timezone] = siteDTO.timezone.id
                it[latitude] = siteDTO.lat
                it[longitude] = siteDTO.lon
                it[azimuth] = siteDTO.azm
                it[peakPowerKwp] = siteDTO.kwp
                it[declination] = siteDTO.dec
            }
        }
    }

    fun getCacheTimestamp(site: Int): LocalDateTime? {
        return transaction {
            return@transaction Site
                .select(productionForecastCacheTimestamp)
                .where { siteId eq site }
                .firstOrNull()
                ?.get(productionForecastCacheTimestamp)
        }
    }

    fun setCacheTimestamp(
        site: Int,
        timestamp: LocalDateTime?,
    ) {
        transaction {
            Site.update({ siteId eq site }) { it[productionForecastCacheTimestamp] = timestamp }
        }
    }

    fun nameMatchesId(
        site: Int,
        name: String,
    ): Boolean =
        transaction {
            !Site.select(siteId).where { (siteId eq site) and (siteName eq name) }.empty()
        }

    private fun ResultRow.toSiteDTO(): SiteDTO =
        SiteDTO(
            this[siteId],
            this[siteName],
            this[password],
            TimeZone.of(this[timezone]),
            this[latitude],
            this[longitude],
            this[azimuth],
            this[peakPowerKwp],
            this[declination],
        )
}
