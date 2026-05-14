package domain

import cvut.fit.energyStats.domain.DataAccessType
import cvut.fit.energyStats.domain.ProviderDataAccessType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TestDataAccessType :
    FunSpec({
        test(
            "ProviderDataAccessType throws exception for category with isProviderDataAccessType set to false",
        ) {
            val error =
                shouldThrow<IllegalArgumentException> {
                    ProviderDataAccessType(DataAccessType.CALCULATED)
                }

            error.message shouldBe "Data access type CALCULATED cannot be set for a provider"
        }
    })
