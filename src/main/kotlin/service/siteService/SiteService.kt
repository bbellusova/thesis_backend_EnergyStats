package cvut.fit.energyStats.service.siteService

import cvut.fit.energyStats.domain.dto.SiteDTO
import cvut.fit.energyStats.domain.dto.removePassword
import cvut.fit.energyStats.repository.siteRepository.SiteRepository
import cvut.fit.energyStats.service.forecastService.ProductionForecastCacheService
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toLocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import kotlin.time.Clock

@Service
class SiteService(
    private val siteRepository: SiteRepository,
    private val passwordEncoder: PasswordEncoder,
    private val cacheService: ProductionForecastCacheService,
    private val clock: Clock = Clock.System,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getSiteById(siteId: Int): SiteDTO = siteRepository.getSiteById(siteId).removePassword()

    fun canUseSiteName(name: String): Boolean = !(siteRepository.siteNameExists(name))

    fun authenticateSite(
        name: String,
        password: String,
    ): SiteDTO {
        val site = siteRepository.getSiteByName(name)
        if (!passwordEncoder.matches(password, site.password)) {
            throw BadCredentialsException("incorrect password provided")
        }
        return site.removePassword()
    }

    fun createSite(siteDTO: SiteDTO): Int {
        try {
            require(canUseSiteName(siteDTO.siteName)) {
                "site name already exists, please choose a different name"
            }
            checkRequestConsistency(siteDTO, true)
            val hashedPassword = passwordEncoder.encode(siteDTO.password)
            val createSite =
                SiteDTO(
                    0,
                    siteDTO.siteName,
                    hashedPassword!!,
                    siteDTO.timezone,
                    siteDTO.lat,
                    siteDTO.lon,
                    siteDTO.azm,
                    siteDTO.kwp,
                    siteDTO.dec,
                )
            val siteId = siteRepository.createSite(createSite)
            return siteId
        } catch (e: IllegalArgumentException) {
            log.error("failed to create site: ${e.message}")
            throw IllegalArgumentException("failed to create site: ${e.message}")
        }
    }

    fun updateSiteParameters(
        siteId: Int,
        siteDTO: SiteDTO,
    ): Boolean {
        try {
            val updateSiteDTO = siteDTO.removePassword()
            checkRequestConsistency(updateSiteDTO, false)
            val changed = updateSiteDTO != getSiteById(siteId)
            if (changed) {
                siteRepository.updateSiteParameters(siteId, updateSiteDTO)
                cacheService.removeFromCache(siteId)
            }
            return changed
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("failed to update site: ${e.message}")
        }
    }

    // we only want to persist data from hours that are fully finished,
    // so we round down the current time
    fun getLastFinishedHourAtSite(site: Int): LocalDateTime {
        val timezone = getSiteById(site).timezone
        val currentTimeAtSite = clock.now().toLocalDateTime(timezone)
        return LocalDateTime(currentTimeAtSite.date, LocalTime(currentTimeAtSite.hour, 0, 0, 0))
    }

    private fun checkRequestConsistency(
        siteDTO: SiteDTO,
        shouldHavePassword: Boolean,
    ) {
        if (shouldHavePassword) {
            require(siteDTO.password != null) { "Site password cannot be null" }
            require(siteDTO.password.length >= 8) { "Site password should be at least 8 characters long" }
        }
        require(siteDTO.lat in -90.0..90.0 && siteDTO.lon in -180.0..180.0) { "invalid coordinates" }
        require(siteDTO.azm in -180.0..180.0) { "invalid azimuth" }
        require(siteDTO.dec in 0.0..90.0) { "invalid declination" }
        require(0 < siteDTO.kwp) { "invalid peak power" }
    }
}
