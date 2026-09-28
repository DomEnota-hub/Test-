package ru.railbrake.calculator.core.assistant

/** Only routes to existing first-aid cards; it never supplies treatment instructions. */
internal object AssistantSafetyTopics {
    private data class Cue(val topic: String, val pattern: Regex)

    private val cues = listOf(
        Cue("nosebleed", Regex("кров[ьи] +из +нос|носов[а-я]* +кровотеч|нос +кровит")),
        Cue("cpr", Regex("не +дыш|нет +дыхани|остановк[а-я]* +сердц|\\bслр\\b|реанимац")),
        Cue("unconscious", Regex("без +сознани|потерял[а-я]* +сознани|нет +сознани|не +отвечает|обморок")),
        Cue("bleeding", Regex("кровотеч|кровь +не +останавлив|сильн[а-я]* +кров|кровопотер|\\bжгут\\b")),
        Cue("airway", Regex("подавил|поперхнул|удушь|инородн[а-я]* +тел[а-я]* +в +горл|не +может +дышать +из-за +ед")),
        Cue("chest_abdomen", Regex("ран[а-я]* +груд|ран[а-я]* +живот|проникающ[а-я]* +ран")),
        Cue("trauma", Regex("перелом|вывих|сломал[а-я]* +(рук|ног)|сломан[а-я]* +(рук|ног)")),
        Cue("electric", Regex("электротравм|электроудар|пораж[а-я]* +электрическ[а-я]* +ток")),
        Cue("chemical", Regex("химическ[а-я]* +ожог|кислот[а-я]* +попал|щелоч[а-я]* +попал")),
        Cue("burn", Regex("ожог|обж[ео]г|обожг|кипятк[а-я]* +облил")),
        Cue("heat", Regex("теплов[а-я]* +удар|солнечн[а-я]* +удар|перегрел|перегревани")),
        Cue("poisoning", Regex("отрав|надышал[а-я]* +газ|угарн[а-я]* +газ")),
        Cue("frostbite", Regex("обморож|обмороз|отмороз|отморож|пальц[а-я]* +на +мороз[еа]? +онем|бел[а-я]* +кож[а-я]* +на +мороз")),
        Cue("hypothermia", Regex("переохлаж|замерз|сильно +замерз|промерз|замерза|озноб +на +холод")),
        Cue("bites", Regex("укусил|укус +|ужалил|клещ +укус")),
        Cue("seizure", Regex("судорог|эпилепс|припадок")),
        Cue("stress", Regex("паническ[а-я]* +атак|истерик|сильн[а-я]* +паник")),
        Cue("kit", Regex("аптечк|состав +аптечк"))
    )

    fun resolve(text: String, electricalInjury: Boolean = false): Set<String> {
        val found = cues.filter { it.pattern.containsMatchIn(text) }.mapTo(linkedSetOf()) { it.topic }
        if (electricalInjury) found += "electric"
        if ("chemical" in found) found -= "burn"
        if ("nosebleed" in found) found -= "bleeding"
        if ("hypothermia" in found &&
            Regex("замерз[а-я]* +(реле|контакт|аппарат|труб|клапан)").containsMatchIn(text)) found -= "hypothermia"
        // General exposure to cold may involve both states. Named/localized
        // frostbite and named hypothermia remain separate.
        if ("hypothermia" in found && "frostbite" !in found &&
            ("замерз" in text || "замерза" in text || "промерз" in text)) found += "frostbite"
        return found
    }
}
