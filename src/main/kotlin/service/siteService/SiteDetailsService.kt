package cvut.fit.energyStats.service.siteService

import cvut.fit.energyStats.repository.siteRepository.SiteRepository
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service("siteDetailsService")
class SiteDetailsService(
    private val siteRepository: SiteRepository,
) : UserDetailsService {
    override fun loadUserByUsername(name: String): UserDetails {
        val sitePassword =
            siteRepository.getSiteByName(name).password
                ?: throw UsernameNotFoundException("Site not found: $name")

        return User
            .builder()
            .username(name)
            .password(sitePassword)
            .roles("SITE")
            .build()
    }

    fun isSiteOwner(
        siteId: Int,
        auth: Authentication,
    ): Boolean {
        if (auth is AnonymousAuthenticationToken) return false
        return siteRepository.nameMatchesId(siteId, auth.name)
    }
}
