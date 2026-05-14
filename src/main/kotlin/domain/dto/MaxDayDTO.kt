package cvut.fit.energyStats.domain.dto

import kotlinx.datetime.LocalDate

data class MaxDayDTO(
    val value: Double,
    val days: Set<LocalDate>,
)
