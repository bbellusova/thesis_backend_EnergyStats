package cvut.fit.energyStats.domain.dto

import kotlinx.datetime.LocalDateTime

data class MaxHourDTO(
    val value: Double,
    val hours: Set<LocalDateTime>,
)
