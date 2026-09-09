package it.faccioio.app

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyMotivationTest {

    @Test
    fun allPhrasesFitTheCompactHeaderBudget() {
        val currentSubtitleLength = "Un passo alla volta.".length

        motivationPhrases.forEach { phrase ->
            assertTrue(
                "Frase troppo lunga per l'intestazione: ${phrase.text}",
                phrase.text.length <= currentSubtitleLength
            )
        }
    }

    @Test
    fun categoriesAlternateWithoutAdjacentRepetitions() {
        motivationPhrases.indices.forEach { index ->
            val next = (index + 1) % motivationPhrases.size
            assertTrue(motivationPhrases[index].category != motivationPhrases[next].category)
        }
    }

    @Test
    fun everyCategoryHasTheSameNumberOfPhrases() {
        val counts = motivationPhrases.groupingBy { it.category }.eachCount()
        MotivationCategory.entries.forEach { category ->
            assertEquals(6, counts[category])
        }
    }

    @Test
    fun phraseIsStableForTheDateAndDoesNotRepeatDuringTheCycle() {
        val firstDay = LocalDate.of(2026, 9, 9)
        val cycle = (0L until motivationPhrases.size.toLong())
            .map { motivationFor(firstDay.plusDays(it)) }

        assertEquals(motivationFor(firstDay), motivationFor(firstDay))
        assertEquals(motivationPhrases.size, cycle.map { it.text }.toSet().size)
        assertEquals(motivationFor(firstDay), motivationFor(firstDay.plusDays(motivationPhrases.size.toLong())))
    }
}
