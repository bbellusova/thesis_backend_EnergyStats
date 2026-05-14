package cvut.fit.energyStats.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SwaggerConfig {
    @Bean
    fun customOpenAPI(): OpenAPI {
        val securitySchemeName = "JSESSIONID"
        return OpenAPI()
            .info(Info().title("EnergyStats").version("1.0").description("API for EnergyStats app"))
            .components(
                Components()
                    .addSecuritySchemes(
                        securitySchemeName,
                        SecurityScheme()
                            .type(SecurityScheme.Type.APIKEY)
                            .`in`(SecurityScheme.In.COOKIE)
                            .name(securitySchemeName),
                    ),
            ).path(
                "/auth/logout",
                PathItem()
                    .post(
                        Operation()
                            .tags(listOf("Authentication"))
                            .summary("Logout")
                            .description("Invalidates session and clears cookies")
                            .responses(
                                ApiResponses().addApiResponse("200", ApiResponse().description("OK")),
                            ),
                    ),
            )
    }
}
