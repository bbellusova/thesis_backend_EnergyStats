package cvut.fit.energyStats.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class RestClientConfig {
    @Bean
    fun objectMapper(): ObjectMapper =
        ObjectMapper().apply {
            registerKotlinModule()
            propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }

    @Bean fun restClientBuilder(): RestClient.Builder = RestClient.builder()

    @Bean
    fun forecastSolarAPIClient(builder: RestClient.Builder): RestClient = builder.baseUrl("https://api.forecast.solar").build()

    @Bean
    fun openMeteoAPIClient(builder: RestClient.Builder): RestClient = builder.baseUrl("https://api.open-meteo.com/v1/forecast").build()

    @Bean
    fun solarEdgeAPIClient(builder: RestClient.Builder): RestClient = builder.baseUrl("https://monitoringapi.solaredge.com").build()

    @Bean
    fun openCageAPIClient(builder: RestClient.Builder): RestClient = builder.baseUrl("https://api.opencagedata.com/geocode/v1").build()
}
