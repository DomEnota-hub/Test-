package ru.railbrake.calculator.core

/**
 * Профиль отделяет универсальный движок приложения от данных конкретного локомотива.
 * Для последующих серий (например, 2ЭС5К/3ЭС5К) добавляется новый профиль, а не ветвление
 * вида `if (ermak)` по всему UI.
 */
data class LocomotiveProfile(
    val id: String,
    val title: String,
    val shortTitle: String,
    val description: String,
    val variants: List<LocomotiveVariant>,
    val sourcePolicy: String,
    val supportedFeatures: Set<ProfileFeature>
)

data class LocomotiveVariant(
    val id: String,
    val title: String,
    val applicability: String,
    val confidence: InformationConfidence,
    val note: String
)

enum class InformationConfidence(val title: String, val description: String) {
    NORMATIVE("Нормативный источник", "Действующий нормативный документ; всё равно проверяйте редакцию и локальный порядок."),
    MANUFACTURER_OR_MANUAL("Руководство/схема", "Руководство по эксплуатации, принципиальная или монтажная схема конкретного исполнения."),
    TRAINING("Учебный материал", "Учебное пособие или тренажёр: полезно для понимания, но не заменяет локальную документацию."),
    REQUIRES_VARIANT_CHECK("Проверить исполнение", "Данные могут отличаться по номеру, модернизации или фактически установленному оборудованию."),
    LOCAL_PROCEDURE("Местный порядок", "Действие зависит от инструкции депо, ТРА, приказа или распоряжения."),
    REFERENCE("Справочно", "Ориентир для поиска и обучения; не использовать как самостоятельное эксплуатационное основание.")
}

enum class ProfileFeature {
    DIAGNOSTICS,
    SIGNALS,
    EQUIPMENT,
    LAYOUT,
    ELECTRICAL_SCHEMES,
    PNEUMATIC_SCHEMES,
    NORMAL_VALUES,
    TRAINING_SIMULATOR
}

object LocomotiveProfiles {
    const val VL80S_ID = "vl80s"
    const val VL80S_GENERAL = "vl80s-general"
    const val VL80S_937_1260 = "vl80s-937-1260"
    const val VL80S_LATER = "vl80s-later"

    val vl80s = LocomotiveProfile(
        id = VL80S_ID,
        title = "ВЛ80С",
        shortTitle = "ВЛ80С",
        description = "Грузовой электровоз переменного тока. Профиль объединяет диагностику, аппараты, схемы, расположение и учебные материалы.",
        variants = listOf(
            LocomotiveVariant(
                id = VL80S_GENERAL,
                title = "Общий профиль ВЛ80С",
                applicability = "Когда номер, год выпуска или модернизация неизвестны.",
                confidence = InformationConfidence.REQUIRES_VARIANT_CHECK,
                note = "Общий профиль является базовым слоем: его сценарии допустимы как общая логика для всех исполнений, а конкретные обозначения и уставки всё равно сверяются по фактической схеме."
            ),
            LocomotiveVariant(
                id = VL80S_937_1260,
                title = "Учебная опорная схема №937–1260",
                applicability = "Учебное пособие использует этот диапазон как базовый вариант схемы.",
                confidence = InformationConfidence.TRAINING,
                note = "Полезно для привязки панелей и логики цепей; не подменяет документацию конкретной секции."
            ),
            LocomotiveVariant(
                id = VL80S_LATER,
                title = "Поздние и модернизированные секции",
                applicability = "Для локомотивов с изменённой схемой или фактически заменённым оборудованием.",
                confidence = InformationConfidence.REQUIRES_VARIANT_CHECK,
                note = "Перед применением любых конкретных обозначений, уставок и расположения сверить схему и документы депо."
            )
        ),
        sourcePolicy = "Нормативные действия имеют приоритет над учебными материалами. При расхождении с документацией конкретного локомотива действует документация конкретного локомотива и установленный местный порядок.",
        supportedFeatures = ProfileFeature.entries.toSet()
    )

    /**
     * `vl80s-general` — общий базовый слой, а не отдельная физическая модификация.
     * Поэтому сценарий с GENERAL применим к любому выбранному исполнению. Сценарий,
     * помеченный только конкретным variant-id, показывается лишь для этого варианта.
     */
    fun appliesToVariant(selectedVariantId: String, applicableVariantIds: Set<String>): Boolean =
        VL80S_GENERAL in applicableVariantIds || selectedVariantId in applicableVariantIds

    val all: List<LocomotiveProfile> = listOf(vl80s)

    fun profile(id: String): LocomotiveProfile? = all.firstOrNull { it.id == id }
    fun variant(id: String): LocomotiveVariant? = all.flatMap { it.variants }.firstOrNull { it.id == id }
}