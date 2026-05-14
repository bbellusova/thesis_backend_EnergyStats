package cvut.fit.energyStats.config

import org.springframework.security.access.prepost.PreAuthorize

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@PreAuthorize("@siteDetailsService.isSiteOwner(#siteId, authentication)")
annotation class IsSiteOwner
