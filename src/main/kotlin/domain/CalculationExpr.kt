package cvut.fit.energyStats.domain

enum class Operator {
    PLUS,
    MINUS,
}

data class CalculationExpr(
    val operand1: DataCategory,
    val operand2: DataCategory,
    val operator: Operator,
)
