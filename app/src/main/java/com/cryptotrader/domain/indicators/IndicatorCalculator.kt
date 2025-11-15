package com.cryptotrader.domain.indicators

/**
 * Base interface for all technical indicator calculators
 */
interface IndicatorCalculator {
    /**
     * Validates that the data list has sufficient length for the given period
     *
     * @param dataSize The size of the input data
     * @param period The calculation period
     * @return True if data is sufficient, false otherwise
     */
    fun hasSufficientData(dataSize: Int, period: Int): Boolean = dataSize >= period

    /**
     * Creates a list filled with null values for indices where calculation is not possible
     *
     * @param size The total size of the result list
     * @param validStartIndex The index from which valid values begin
     * @param values The calculated valid values
     * @return A list with nulls for invalid indices and calculated values for valid indices
     */
    fun fillWithNulls(size: Int, validStartIndex: Int, values: List<Double?>): List<Double?> {
        return List(size) { index ->
            if (index < validStartIndex) null else values.getOrNull(index - validStartIndex)
        }
    }
}
