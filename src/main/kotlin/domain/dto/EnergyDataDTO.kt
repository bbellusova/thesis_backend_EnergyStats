package cvut.fit.energyStats.domain.dto

import kotlinx.datetime.LocalDateTime

typealias Entries = MutableMap<LocalDateTime, Double>

data class EnergyDataDTO(
    val entries: Entries,
    val allDataPresent: Boolean,
    val message: String,
)
