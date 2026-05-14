package cvut.fit.energyStats.domain

enum class DataAccessType(
    val isProviderDataAccessType: Boolean,
) {
    API(true),
    FILE(true),
    CALCULATED(false),
    UNAVAILABLE(false),
}

@JvmInline
value class ProviderDataAccessType(
    val dataAccess: DataAccessType,
) {
    init {
        require(dataAccess.isProviderDataAccessType) {
            "Data access type $dataAccess cannot be set for a provider"
        }
    }
}
