package cvut.fit.energyStats.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.OncePerRequestFilter

class CsrfCookieFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val csrfToken = request.getAttribute(CsrfToken::class.java.name) as CsrfToken?
        csrfToken?.token
        filterChain.doFilter(request, response)
    }
}

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig {
    @Bean fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { csrf ->
                val repository = CookieCsrfTokenRepository.withHttpOnlyFalse()
                repository.setCookiePath("/")
                csrf
                    .csrfTokenRepository(repository)
                    .ignoringRequestMatchers(
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/sites",
                        "/auth/login",
                    ).csrfTokenRequestHandler(CsrfTokenRequestAttributeHandler())
            }.addFilterAfter(CsrfCookieFilter(), BasicAuthenticationFilter::class.java)
            .exceptionHandling { exceptions ->
                exceptions.authenticationEntryPoint { _, response, _ ->
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")
                }
            }.authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(
                        org.springframework.http.HttpMethod.GET,
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/sites/canUseSiteName/**",
                        "/geocoding/timezoneForCoordinates/**",
                        "/providers", // Allow getting supported providers
                        "/providers/*/availableSitesForAPIKey",
                        "/forecasts/generation/validateCoordinates/**", // Allow validation of coordinates
                    ).permitAll()
                    .requestMatchers(
                        org.springframework.http.HttpMethod.POST,
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/sites", // Allow registration of new site
                        "/auth/login",
                    ).permitAll()
                auth.requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()

                auth.anyRequest().authenticated()
            }.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED) }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { logout ->
                logout
                    .logoutUrl("/auth/logout")
                    .invalidateHttpSession(true)
                    .clearAuthentication(true)
                    .deleteCookies("JSESSIONID")
                    .logoutSuccessHandler { _, response, _ ->
                        response.status = HttpServletResponse.SC_OK
                    }
            }

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()

        configuration.allowedOrigins =
            listOf("http://localhost:3000", "https://energy-stats-bp-bellubea.ksi.fit.cvut.cz")

        configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")

        // for Basic Auth: Allow the Authorization header
        configuration.allowedHeaders = listOf("*")

        // Allow the browser to send credentials (cookies/auth headers)
        configuration.allowCredentials = true

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration) // apply to all endpoints
        return source
    }
}
