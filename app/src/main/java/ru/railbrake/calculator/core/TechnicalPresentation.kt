package ru.railbrake.calculator.core

private val hiddenMetadata = setOf(
    "confirmed_general",
    "base_confirmed",
    "evolution_confirmed",
    "training_source_confirmed",
    "mixed_source_confirmed",
    "secondary_source_requires_drawing",
    "historical_technical_source",
    "open_exact_numbers",
    "final",
    "pass"
)

private val exactLabels = mapOf(
    "local_procedure" to "Действовать по местной инструкции и установленному технологическому процессу",
    "stop_and_report" to "Остановить проверку и доложить установленным порядком",
    "authorized_only" to "Только при установленном допуске и безопасной подготовке",
    "visual_safe" to "Только безопасный визуальный осмотр",
    "cab_only" to "Проверка выполняется из кабины в установленном порядке",
    "external_inspection_complete" to "Наружный осмотр завершён",
    "brake_area_clear" to "Зона тормозной передачи свободна от людей",
    "brakes_confirmed" to "Работоспособность тормозов подтверждена установленным порядком",
    "all_base_variants_verify_actual_section" to "Для всех базовых исполнений с обязательной проверкой фактической секции",
    "actual_section_scheme_required" to "Требуется схема фактической секции",
    "section-aware applicability" to "Применимость определяется по фактическому исполнению секции",
    "unknown" to "Не определено",
    "head" to "Головная секция",
    "booster" to "Бустерная секция",
    "2es5k" to "2ЭС5К",
    "3es5k" to "3ЭС5К",
    "group_base" to "Групповое регулирование",
    "axle_control_modern" to "Современное поосное регулирование",
    "3es5k_434_experimental" to "3ЭС5К №434, опытное исполнение",
    "3es5k_896plus" to "3ЭС5К №896 и позднее",
    "3es5k_axle_control_pre896" to "3ЭС5К с поосным регулированием, до №896",
    "3es5k_base_early" to "3ЭС5К, базовое раннее исполнение",
    "3es5k_rolling_bearing" to "3ЭС5К с подшипниками качения",
    "2es5k_base_early" to "2ЭС5К, базовое раннее исполнение",
    "2es5k_rolling_bearing" to "2ЭС5К с подшипниками качения",
    "2es5k_axle_control_modern" to "2ЭС5К с современным поосным регулированием",
    "klub-u_saut-tskbm" to "КЛУБ-У / САУТ / ТСКБМ",
    "blok_2es5k" to "БЛОК (2ЭС5К)",
    "plain" to "Подшипник скольжения",
    "rolling" to "Подшипник качения",
    "vl80s_unknown" to "Исполнение не определено",
    "vl80s_pre697" to "До №697",
    "vl80s_697_1260" to "№697–1260",
    "vl80s_1261_1405" to "№1261–1405",
    "vl80s_1406_2318" to "№1406–2318",
    "vl80s_2319_2348" to "№2319–2348",
    "vl80s_2349_2653" to "№2349–2653",
    "vl80s_2654plus" to "№2654 и позднее",
    "vl80s_modified_or_mixed" to "Модернизированная или рекомплектованная секция",
    "early_sme" to "Раннее исполнение СМЕ",
    "sme_three_section_capable" to "Допускается трёхсекционная СМЕ",
    "third_section_rheostatic_brake_unavailable" to "Реостатное торможение третьей секции не используется",
    "pr_isolation_on_working_positions" to "Изоляция ПР на рабочих позициях",
    "ekg_sme_sync_updated" to "Изменённая синхронизация ЭКГ при СМЕ",
    "burt16" to "БУРТ-16",
    "vu_protection_rp21_22" to "Защита ВУ с РП21/РП22",
    "aux_compressor_pvu7" to "Вспомогательный компрессор / ПВУ7",
    "fr_late_scheme" to "Поздняя схема фазорасщепителя",
    "fire_signalization_present" to "Установлена пожарная сигнализация",
    "no_exact_scheme_assumption" to "Точную схему нельзя определять без подтверждения исполнения",
    "actual_section_drawing_has_priority" to "Приоритет имеет фактическая схема конкретной секции",
    "serial_number_not_sufficient" to "Номера локомотива недостаточно для определения комплектации",
    "do_not_merge_vl80sk_into_base_vl80s" to "ВЛ80СК не объединяется с базовым ВЛ80С",
    "do_not_assume_same_variant_for_recombined_sections" to "Для рекомплектованных секций исполнение определяется отдельно",
    "do_not_use_training_scheme_as_universal_mounting_scheme" to "Учебная схема не считается универсальной монтажной схемой",
    "exact_wire_apparatus_setting_requires_section_specific_source" to "Точные провода и аппараты требуют источника по конкретной секции"
)

private val englishRules = mapOf(
    "variant selection happens before detailed scheme rendering. unknown dimensions do not silently default to another execution." to
        "Сначала выбирается исполнение, затем строится подробная схема. Неизвестные параметры не подменяются другим исполнением автоматически.",
    "booster is only valid for 3es5k" to "Бустерная секция применяется только для 3ЭС5К.",
    "3es5k_434_experimental and 3es5k_896plus are separate layouts" to
        "Опытный 3ЭС5К №434 и 3ЭС5К №896 и позднее имеют отдельные компоновки.",
    "brake variants 395 / 130 / 130-2 are mutually exclusive for one selected execution" to
        "Для выбранного исполнения используется один вариант тормозного оборудования: №395, №130 или №130-2.",
    "motor-axle bearing plain / rolling are separate scheme profiles" to
        "Исполнения с моторно-осевыми подшипниками скольжения и качения рассматриваются раздельно.",
    "blok scheme is not generalized to all 2es5k or to 3es5k" to
        "Схема БЛОК не распространяется автоматически на все 2ЭС5К и на 3ЭС5К.",
    "msud-015/axle-control overlays do not replace the base wiring diagram unless a full primary late-execution scheme is available" to
        "Наложения МСУД-015 и поосного регулирования не заменяют базовую электрическую схему без полного первичного источника для позднего исполнения."
)

private val legacyIdentifier = Regex("^[a-z][A-Za-z0-9_-]{1,}$")
private val sourcePoint = Regex("^p\\.(\\d+(?:[.-]\\d+)*)$", RegexOption.IGNORE_CASE)
private val vlProfileSubtitle = Regex("^Профиль секции\\s+vl80s_[A-Za-z0-9_]+$", RegexOption.IGNORE_CASE)
private val rangeTitle = Regex("^(\\d+)-(\\d+)$")

internal fun technicalPresentationLine(value: String): String? {
    val cleaned = userFacingTechnicalText(value).trim()
    if (cleaned.isBlank()) return null
    val parts = cleaned.split(" • ")
        .mapNotNull(::technicalPresentationAtom)
        .distinct()
    return parts.joinToString(" • ").takeIf(String::isNotBlank)
}

private fun technicalPresentationAtom(value: String): String? {
    val raw = value.trim()
    if (raw.isBlank() || isInternalTechnicalReference(raw)) return null
    val key = raw.lowercase()
    if (key in hiddenMetadata) return null
    exactLabels[key]?.let { return it }
    englishRules[key]?.let { return it }
    sourcePoint.matchEntire(raw)?.let { return "п. ${it.groupValues[1]}" }
    if (vlProfileSubtitle.matches(raw)) return "Профиль исполнения секции ВЛ80С"
    if (looksLikeInternalEnglishRule(raw)) return "Ограничение применяется по выбранному исполнению."
    if (legacyIdentifier.matches(raw)) return null
    if (raw.contains('_') && raw.all { it.isLetterOrDigit() || it == '_' || it == '-' || it == ' ' }) return null
    return raw
}

private fun looksLikeInternalEnglishRule(value: String): Boolean {
    val lower = value.lowercase()
    if (!lower.any { it in 'a'..'z' }) return false
    val markers = listOf(" scheme", "variant ", " profile", " layout", " default", " is only ", " are separate", " selection ", " unless ")
    return markers.any(lower::contains)
}

internal fun technicalEntryTitle(entry: TechnicalEntry): String {
    if (entry.family == TechnicalFamily.VL80S && entry.section == TechnicalSection.PROFILES) {
        val raw = entry.title.trim()
        return when {
            raw.equals("unknown", true) -> "Исполнение не определено"
            raw == "<697" -> "До №697"
            raw == ">=2654" -> "№2654 и позднее"
            rangeTitle.matches(raw) -> rangeTitle.matchEntire(raw)!!.let { "№${it.groupValues[1]}–${it.groupValues[2]}" }
            else -> technicalPresentationLine(raw) ?: "Профиль исполнения ВЛ80С"
        }
    }
    return technicalPresentationLine(entry.title) ?: "Материал"
}

internal fun technicalEntrySubtitle(entry: TechnicalEntry): String? {
    if (entry.family == TechnicalFamily.VL80S && entry.section == TechnicalSection.PROFILES) {
        return "Профиль исполнения секции ВЛ80С"
    }
    return technicalPresentationLine(entry.subtitle)
}

internal fun technicalStatusPresentation(status: String): String? = when (status.trim().uppercase()) {
    "INFORMATION" -> "Справочно"
    "ATTENTION" -> "Внимание"
    "RESTRICT_OPERATION" -> "Ограничить эксплуатацию"
    "STOP_AND_REPORT" -> "Остановиться и доложить"
    "REQUIRED" -> "Обязательный параметр"
    "PROFILE_REQUIRED" -> "Требуется выбрать исполнение"
    "CONFLICT" -> "Требует уточнения"
    "FALLBACK" -> "Исполнение не определено"
    "CONFIRMED_GENERAL", "BASE_CONFIRMED", "CONFIRMED" -> "Подтверждено"
    "EVOLUTION_CONFIRMED" -> "Подтверждено для указанного исполнения"
    "TRAINING_SOURCE_CONFIRMED" -> "Подтверждено учебным источником"
    "MIXED_SOURCE_CONFIRMED" -> "Подтверждено несколькими источниками"
    "SECONDARY_SOURCE_REQUIRES_DRAWING" -> "Требует сверки со схемой секции"
    "OPEN", "OPEN_EXACT_NUMBERS" -> "Требует уточнения"
    "FINAL" -> "Проверено"
    else -> null
}
