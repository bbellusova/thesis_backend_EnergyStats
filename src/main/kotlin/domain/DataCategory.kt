package cvut.fit.energyStats.domain

enum class DataCategory(
    val description: String,
) {
    ENERGY_CONSUMPTION_FROM_THE_GRID("Energy consumed from the grid"),
    ENERGY_PRODUCTION_TO_THE_GRID("Generated energy fed to the grid"),
    ENERGY_PRODUCTION_TOTAL("Total energy production"),
    ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED("Generated energy consumed immediately"),
    ENERGY_CONSUMPTION_TOTAL("Total energy consumption"),
}
