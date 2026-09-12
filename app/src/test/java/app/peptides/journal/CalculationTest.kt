package app.peptides.journal

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class CalculationTest {
    @Test fun referenceImageArithmetic() {
        val c = Calculator.calculate("5", "10", "1", "1", "100", "1")
        assertEquals(0, c.concentration.compareTo(BigDecimal("10")))
        assertEquals(0, c.volume.compareTo(BigDecimal("0.5")))
        assertEquals(0, c.units.compareTo(BigDecimal("50")))
        assertEquals(0, c.doses.compareTo(BigDecimal("2")))
        assertTrue(c.aligned)
    }
    @Test fun commaFractionsAndRemainder() {
        val c = Calculator.calculate("0,3", "1", "1", "0,5", "100", "1")
        assertEquals(0, c.volume.compareTo(BigDecimal("0.3")))
        assertEquals(0, c.doses.compareTo(BigDecimal("3")))
        assertEquals(0, c.remainder.compareTo(BigDecimal("0.1")))
    }
    @Test fun exactArithmeticDoesNotInventTickAlignment() {
        val c = Calculator.calculate("1", "3", "1", "1", "100", "1")
        assertFalse(c.aligned)
        assertTrue(Calculator.display(c.volume, false).startsWith("≈ "))
        assertEquals("0,5", Calculator.display(BigDecimal("0.5"), true))
        assertTrue(Calculator.calculate("0.005", "1", "1", "1", "100", "0.5").aligned)
    }
    @Test fun rejectsUnsafeOrAmbiguousInputs() {
        listOf("", "0", "-1", "NaN", "Infinity", "1e3", "1,000.1", "1.2.3", "1000000000", "0.0000000001").forEach {
            assertThrows(InputProblem::class.java) { Calculator.number(it) }
        }
        assertEquals(Problem.DOSE_EXCEEDS_VIAL, assertThrows(InputProblem::class.java) { Calculator.calculate("2", "1", "1", "1", "100", "1") }.problem)
        assertEquals(Problem.CAPACITY, assertThrows(InputProblem::class.java) { Calculator.calculate("1", "1", "1", "0.3", "100", "1") }.problem)
        assertEquals(Problem.SCALE, assertThrows(InputProblem::class.java) { Calculator.calculate("1", "1", "1", "1", "100", "3") }.problem)
    }
    @Test fun capacityBoundaryIsComparedBeforeRounding() {
        assertTrue(Calculator.calculate("0.3", "1", "1", "0.3", "100", "1").aligned)
        assertThrows(InputProblem::class.java) { Calculator.calculate("0.300000001", "1", "1", "0.3", "100", "1") }
    }
}
