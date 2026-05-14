package cvut.fit.energyStats.repository.energyDataRepository

import org.springframework.stereotype.Repository

@Repository
class EnergyProductionToTheGridRepository : EnergyDataRepository(EnergyProductionToTheGridO)
