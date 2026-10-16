package dev.glinesb.openmacros.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PortionsAndMergeTest {
    @Test fun servingsFromUnits() {
        assertEquals(2.0, Portions.servings(2.0, AmountUnit.SERVING, 30.0), 1e-9)
        assertEquals(1.5, Portions.servings(45.0, AmountUnit.GRAM, 30.0), 1e-9)
        assertEquals(Units.G_PER_OZ / 30.0, Portions.servings(1.0, AmountUnit.OUNCE, 30.0), 1e-9)
        assertEquals(listOf(AmountUnit.SERVING), Portions.unitsFor(null))
    }

    @Test fun unitConversionsRoundTrip() {
        assertEquals(80.0, Units.lbToKg(Units.kgToLb(80.0)), 1e-9)
        val (ft, inch) = Units.cmToFeetInches(Units.feetInchesToCm(5, 11.0))
        assertEquals(5, ft); assertEquals(11.0, inch, 1e-9)
    }

    private fun item(name: String, source: FoodSource, barcode: String? = null, brand: String? = null) =
        FoodItem(source, name + source, name, brand, barcode, "100 g", 100.0, Macros(100.0, 1.0, 1.0, 1.0))

    @Test fun mergeDedupesBarcodesAcrossPadding() {
        val usda = listOf(item("Oat Cereal", FoodSource.USDA, barcode = "0016000275287"))
        val off = listOf(item("Cheerios", FoodSource.OPEN_FOOD_FACTS, barcode = "016000275287"))
        val merged = FoodMerge.merge("cereal", listOf(usda, off))
        assertEquals(1, merged.size)
        assertEquals(FoodSource.USDA, merged[0].source)
    }

    @Test fun mergeRanksByRelevanceThenInterleaves() {
        val usda = listOf(item("Rice, white, cooked", FoodSource.USDA), item("Banana", FoodSource.USDA))
        val off = listOf(item("Brown rice cakes", FoodSource.OPEN_FOOD_FACTS), item("Rice", FoodSource.OPEN_FOOD_FACTS))
        val names = FoodMerge.merge("rice", listOf(usda, off)).map { it.name }
        assertEquals(listOf("Rice", "Rice, white, cooked", "Brown rice cakes", "Banana"), names)
    }

    @Test fun mergeDedupesSameNameAndBrand() {
        val a = listOf(item("Greek Yogurt", FoodSource.USDA, brand = "Fage"))
        val b = listOf(item("greek yogurt", FoodSource.OPEN_FOOD_FACTS, brand = "FAGE"))
        assertEquals(1, FoodMerge.merge("yogurt", listOf(a, b)).size)
    }
}
