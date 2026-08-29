package it.faccioio.app

import java.util.Calendar
import java.util.Locale

data class ParsedAppointment(
    val title: String,
    val time: Long?,
    val location: String?,
    val dateOnly: Boolean = false
)

data class PersonalArrivalCommand(
    val placeKey: String,
    val title: String
)

fun parsePersonalArrivalCommand(input: String): PersonalArrivalCommand? {
    val normalized = input.trim().replace(Regex("\\s+"), " ")
    val leadingMatch = Regex(
        """^\s*quando\s+(?:arrivo|torno)\s+(?:a|al|alla|in)\s+(casa|lavoro|ufficio)\b\s*[,;:.!-]*\s*(.*)$""",
        RegexOption.IGNORE_CASE
    ).find(normalized)
    val trailingMatch = Regex(
        """^(.*?)\s*[,;:.!-]*\s*\bquando\s+(?:arrivo|torno)\s+(?:a|al|alla|in)\s+(casa|lavoro|ufficio)\b\s*[,;:.!-]*$""",
        RegexOption.IGNORE_CASE
    ).find(normalized)
    val placeText = leadingMatch?.groupValues?.get(1)
        ?: trailingMatch?.groupValues?.get(2)
        ?: return null
    val titleText = leadingMatch?.groupValues?.get(2)
        ?: trailingMatch!!.groupValues[1]
    val placeKey = when (placeText.lowercase(Locale.ITALIAN)) {
        "casa" -> "home"
        else -> "work"
    }
    val title = titleText
        .replace(
            Regex("^(?:ricordami|ricordare|ricordarsi|promemoria)(?:\\s+di)?\\s*", RegexOption.IGNORE_CASE),
            ""
        )
        .trim(' ', ',', '.', '-', ':', ';')
        .replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.ITALIAN) else it.toString()
        }
    if (title.isBlank()) return null
    return PersonalArrivalCommand(placeKey, title)
}

fun parseAppointment(
    input: String,
    now: Calendar = Calendar.getInstance()
): ParsedAppointment? {
    val original = normalizeSpokenTime(
        input.trim().replace(Regex("\\s+"), " ")
    )
    if (original.isBlank()) return null
    val lower = original.lowercase(Locale.ITALIAN)

    val timeMatch = Regex("(?:alle|ore)?\\s*(\\d{1,2})(?:[.:](\\d{2}))?").findAll(lower)
        .firstOrNull { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: 99
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            hour in 0..23 && minute in 0..59 &&
                (match.value.contains("alle") || match.value.contains("ore") ||
                    match.value.contains('.') || match.value.contains(':'))
        }

    val result = (now.clone() as Calendar).apply {
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val dateMatch = Regex("\\b(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{2,4}))?\\b").find(lower)
    val monthNames = listOf(
        "gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno",
        "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre"
    )
    val textDateMatch = Regex(
        "\\b(?:il\\s+)?(\\d{1,2})\\s+(${monthNames.joinToString("|")})(?:\\s+(\\d{4}))?\\b",
        RegexOption.IGNORE_CASE
    ).find(lower)
    val hasRelativeDate = listOf("oggi", "domani", "dopodomani").any { it in lower }
    val hasRecognizedDate = dateMatch != null || textDateMatch != null || hasRelativeDate
    var absoluteDateWithoutYear = false
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
            absoluteDateWithoutYear = yearText.isBlank()
        }
        textDateMatch != null -> {
            val day = textDateMatch.groupValues[1].toInt()
            val month = monthNames.indexOf(
                textDateMatch.groupValues[2].lowercase(Locale.ITALIAN)
            )
            val yearText = textDateMatch.groupValues[3]
            val year = yearText.toIntOrNull() ?: now.get(Calendar.YEAR)
            result.set(year, month, day)
            absoluteDateWithoutYear = yearText.isBlank()
        }
        "dopodomani" in lower -> result.add(Calendar.DAY_OF_YEAR, 2)
        "domani" in lower -> result.add(Calendar.DAY_OF_YEAR, 1)
        timeMatch != null && "oggi" !in lower -> {
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

    timeMatch?.let {
        result.set(Calendar.HOUR_OF_DAY, it.groupValues[1].toInt())
        result.set(Calendar.MINUTE, it.groupValues[2].toIntOrNull() ?: 0)
        if (absoluteDateWithoutYear && result.timeInMillis <= now.timeInMillis) {
            result.add(Calendar.YEAR, 1)
        }
        if (result.timeInMillis <= now.timeInMillis && "oggi" in lower) return null
    }

    val locationMatch = Regex(
        "\\b(?:presso|in|a)\\s+(.+)$",
        RegexOption.IGNORE_CASE
    ).find(original) ?: Regex(
        "\\b((?:via|viale|piazza|piazzale|corso|largo|vicolo|strada|lungomare|località)\\s+.+)$",
        RegexOption.IGNORE_CASE
    ).find(original)
    val location = locationMatch?.groupValues?.get(1)?.trim(' ', '.', ',')

    var title = original
        .replace(dateMatch?.value ?: "", "", ignoreCase = true)
        .replace(textDateMatch?.value ?: "", "", ignoreCase = true)
        .replace(timeMatch?.value ?: "", "", ignoreCase = true)
        .replace(Regex("\\b(?:oggi|domani|dopodomani|lunedì|martedì|mercoledì|giovedì|venerdì|sabato|domenica)\\b", RegexOption.IGNORE_CASE), "")
    if (locationMatch != null) title = title.replace(locationMatch.value, "", ignoreCase = true)
    title = title
        .replace(Regex("\\b(?:alle|ore)\\b", RegexOption.IGNORE_CASE), "")
        .replace(
            Regex("^(?:ricordami|ricordare|ricordarsi|promemoria)(?:\\s+di)?\\s*", RegexOption.IGNORE_CASE),
            ""
        )
        .trim(' ', ',', '.', '-')
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ITALIAN) else it.toString() }

    if (
        title.isBlank() ||
        (timeMatch == null && location.isNullOrBlank() && !hasRecognizedDate)
    ) return null
    return ParsedAppointment(
        title = title,
        time = if (timeMatch != null || hasRecognizedDate) result.timeInMillis else null,
        location = location,
        dateOnly = hasRecognizedDate && timeMatch == null
    )
}

private fun normalizeSpokenTime(text: String): String {
    val hourWords = mapOf(
        "zero" to 0, "una" to 1, "uno" to 1, "due" to 2, "tre" to 3,
        "quattro" to 4, "cinque" to 5, "sei" to 6, "sette" to 7,
        "otto" to 8, "nove" to 9, "dieci" to 10, "undici" to 11,
        "dodici" to 12, "tredici" to 13, "quattordici" to 14,
        "quindici" to 15, "sedici" to 16, "diciassette" to 17,
        "diciotto" to 18, "diciannove" to 19, "venti" to 20,
        "ventuno" to 21, "ventidue" to 22, "ventitré" to 23,
        "ventitre" to 23, "mezzogiorno" to 12, "mezzanotte" to 0
    )
    val wordsPattern = hourWords.keys
        .sortedByDescending { it.length }
        .joinToString("|") { Regex.escape(it) }
    val spokenTime = Regex(
        "\\b(ore|alle)\\s+($wordsPattern)(?:\\s+e\\s+(un quarto|mezza|mezzo|[a-zàèéìòù]+))?\\b",
        RegexOption.IGNORE_CASE
    )
    val minuteWords = hourWords + mapOf(
        "venticinque" to 25, "trenta" to 30, "trentacinque" to 35,
        "quaranta" to 40, "quarantacinque" to 45, "cinquanta" to 50,
        "cinquantacinque" to 55
    )

    return spokenTime.replace(text) { match ->
        val hour = hourWords[match.groupValues[2].lowercase(Locale.ITALIAN)]
            ?: return@replace match.value
        val minuteText = match.groupValues[3].lowercase(Locale.ITALIAN)
        val minutes = when (minuteText) {
            "" -> null
            "un quarto" -> 15
            "mezza", "mezzo" -> 30
            else -> minuteWords[minuteText]?.takeIf { it in 0..59 }
        }
        if (minutes == null) {
            "${match.groupValues[1]} $hour"
        } else {
            "${match.groupValues[1]} $hour:${minutes.toString().padStart(2, '0')}"
        }
    }
}
