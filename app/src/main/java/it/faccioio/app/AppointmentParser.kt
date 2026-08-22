package it.faccioio.app

import java.util.Calendar
import java.util.Locale

data class ParsedAppointment(
    val title: String,
    val time: Long,
    val location: String?
)

fun parseAppointment(
    input: String,
    now: Calendar = Calendar.getInstance()
): ParsedAppointment? {
    val original = input.trim().replace(Regex("\\s+"), " ")
    if (original.isBlank()) return null
    val lower = original.lowercase(Locale.ITALIAN)

    val timeMatch = Regex("(?:alle|ore)?\\s*(\\d{1,2})(?:[.:](\\d{2}))?").findAll(lower)
        .firstOrNull { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: 99
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            hour in 0..23 && minute in 0..59 &&
                (match.value.contains("alle") || match.value.contains("ore") ||
                    match.value.contains('.') || match.value.contains(':'))
        } ?: return null

    val result = (now.clone() as Calendar).apply {
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val dateMatch = Regex("\\b(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{2,4}))?\\b").find(lower)
    when {
        dateMatch != null -> {
            val day = dateMatch.groupValues[1].toInt()
            val month = dateMatch.groupValues[2].toInt() - 1
            val yearText = dateMatch.groupValues[3]
            val year = when {
                yearText.isBlank() -> now.get(Calendar.YEAR)
                yearText.length == 2 -> 2000 + yearText.toInt()
                else -> yearText.toInt()
            }
            result.set(year, month, day)
        }
        "dopodomani" in lower -> result.add(Calendar.DAY_OF_YEAR, 2)
        "domani" in lower -> result.add(Calendar.DAY_OF_YEAR, 1)
        "oggi" !in lower -> {
            val weekdays = listOf(
                "domenica", "lunedì", "martedì", "mercoledì",
                "giovedì", "venerdì", "sabato"
            )
            val weekday = weekdays.indexOfFirst { it in lower }
            if (weekday >= 0) {
                var days = (weekday + 1 - result.get(Calendar.DAY_OF_WEEK) + 7) % 7
                if (days == 0) days = 7
                result.add(Calendar.DAY_OF_YEAR, days)
            } else {
                return null
            }
        }
    }

    result.set(Calendar.HOUR_OF_DAY, timeMatch.groupValues[1].toInt())
    result.set(Calendar.MINUTE, timeMatch.groupValues[2].toIntOrNull() ?: 0)
    if (result.timeInMillis <= now.timeInMillis && "oggi" in lower) return null

    val locationMatch = Regex("\\b(?:presso|in|a)\\s+(.+)$", RegexOption.IGNORE_CASE)
        .find(original)
    val location = locationMatch?.groupValues?.get(1)?.trim(' ', '.', ',')

    var title = original
        .replace(dateMatch?.value ?: "", "", ignoreCase = true)
        .replace(timeMatch.value, "", ignoreCase = true)
        .replace(Regex("\\b(?:oggi|domani|dopodomani|lunedì|martedì|mercoledì|giovedì|venerdì|sabato|domenica)\\b", RegexOption.IGNORE_CASE), "")
    if (locationMatch != null) title = title.replace(locationMatch.value, "", ignoreCase = true)
    title = title
        .replace(Regex("\\b(?:alle|ore)\\b", RegexOption.IGNORE_CASE), "")
        .trim(' ', ',', '.', '-')
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ITALIAN) else it.toString() }

    if (title.isBlank()) return null
    return ParsedAppointment(title, result.timeInMillis, location)
}
