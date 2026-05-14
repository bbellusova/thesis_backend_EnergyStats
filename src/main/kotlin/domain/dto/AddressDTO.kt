package cvut.fit.energyStats.domain.dto

data class AddressDTO(
    val country: String,
    val countryCode: String,
    val city: String,
    val street: String,
    val zipCode: String,
)
