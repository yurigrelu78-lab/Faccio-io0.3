package it.faccioio.app

import java.time.LocalDate

internal enum class MotivationCategory {
    CONCRETEZZA,
    CALMA,
    PROGRESSO,
    RIPARTENZA,
    RISPETTO_DEI_LIMITI
}

internal data class MotivationPhrase(
    val text: String,
    val category: MotivationCategory
)

/**
 * Le frasi sono ordinate alternando le categorie. L'ordine fa parte del
 * comportamento: garantisce varietà senza bisogno di preferenze persistenti.
 * Ogni testo è intenzionalmente breve per occupare al massimo una riga nella
 * colonna della data, anche quando il pulsante Aggiungi è alla sua larghezza minima.
 */
internal val motivationPhrases = listOf(
    MotivationPhrase("Un passo alla volta.", MotivationCategory.CONCRETEZZA),
    MotivationPhrase("Calma, un passo.", MotivationCategory.CALMA),
    MotivationPhrase("Anche poco conta.", MotivationCategory.PROGRESSO),
    MotivationPhrase("Puoi ripartire.", MotivationCategory.RIPARTENZA),
    MotivationPhrase("Non devi fare tutto.", MotivationCategory.RISPETTO_DEI_LIMITI),

    MotivationPhrase("Inizia da una cosa.", MotivationCategory.CONCRETEZZA),
    MotivationPhrase("Respira e riparti.", MotivationCategory.CALMA),
    MotivationPhrase("Ogni passo conta.", MotivationCategory.PROGRESSO),
    MotivationPhrase("Oggi ricomincia.", MotivationCategory.RIPARTENZA),
    MotivationPhrase("Fai ciò che puoi.", MotivationCategory.RISPETTO_DEI_LIMITI),

    MotivationPhrase("Avanti, un passo.", MotivationCategory.CONCRETEZZA),
    MotivationPhrase("Prenditi tempo.", MotivationCategory.CALMA),
    MotivationPhrase("Stai avanzando.", MotivationCategory.PROGRESSO),
    MotivationPhrase("Ricomincia da qui.", MotivationCategory.RIPARTENZA),
    MotivationPhrase("Il tuo ritmo conta.", MotivationCategory.RISPETTO_DEI_LIMITI),

    MotivationPhrase("Comincia da qui.", MotivationCategory.CONCRETEZZA),
    MotivationPhrase("Scegli la calma.", MotivationCategory.CALMA),
    MotivationPhrase("Fai strada.", MotivationCategory.PROGRESSO),
    MotivationPhrase("Riparti con calma.", MotivationCategory.RIPARTENZA),
    MotivationPhrase("Non avere fretta.", MotivationCategory.RISPETTO_DEI_LIMITI),

    MotivationPhrase("Una cosa per volta.", MotivationCategory.CONCRETEZZA),
    MotivationPhrase("Semplifica il passo.", MotivationCategory.CALMA),
    MotivationPhrase("Oggi fai un passo.", MotivationCategory.PROGRESSO),
    MotivationPhrase("Ora è il momento.", MotivationCategory.RIPARTENZA),
    MotivationPhrase("Fai una pausa.", MotivationCategory.RISPETTO_DEI_LIMITI),

    MotivationPhrase("Parti da qui.", MotivationCategory.CONCRETEZZA),
    MotivationPhrase("La calma fa spazio.", MotivationCategory.CALMA),
    MotivationPhrase("Nota i tuoi passi.", MotivationCategory.PROGRESSO),
    MotivationPhrase("Riparti da oggi.", MotivationCategory.RIPARTENZA),
    MotivationPhrase("Il possibile basta.", MotivationCategory.RISPETTO_DEI_LIMITI)
)

internal fun motivationFor(date: LocalDate): MotivationPhrase =
    motivationPhrases[Math.floorMod(date.toEpochDay(), motivationPhrases.size.toLong()).toInt()]
