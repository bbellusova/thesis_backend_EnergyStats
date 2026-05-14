package cvut.fit.energyStats.domain

enum class DataProvider(
    val fullName: String,
) {
    ZSD("Zapadoslovenska distribucna a.s."),
    SolarEdge("SolarEdge"),
    DUMMY("Dummy"),
}
