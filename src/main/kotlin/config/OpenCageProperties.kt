package cvut.fit.energyStats.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "opencage")
data class OpenCageProperties(
    val apikey: String,
)
