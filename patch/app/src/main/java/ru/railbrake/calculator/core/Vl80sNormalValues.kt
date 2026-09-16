package ru.railbrake.calculator.core

data class NormalParameter(
    val id: String,
    val title: String,
    val normalValue: String,
    val applicability: String,
    val equipmentIds: Set<String>,
    val confidence: InformationConfidence,
    val source: String
)

/**
 * Небольшая база опорных параметров. Число добавляется только когда его
 * применимость можно объяснить; неизвестная уставка не заменяется «типичным» числом.
 */
object Vl80sNormalValues {
    val all = listOf(
        NormalParameter(
            "control-voltage", "Цепи управления", "Номинально 50 В постоянного тока",
            "ВЛ80С с ТРПШ; фактическое напряжение и допустимые отклонения сверять по руководству конкретного исполнения.",
            setOf("ekg", "bsa", "valve245", "protection"), InformationConfidence.MANUFACTURER_OR_MANUAL,
            "Руководство по эксплуатации ВЛ80С, описание цепей управления и ТРПШ."
        ),
        NormalParameter(
            "contact-network", "Род тока", "Переменный ток промышленной частоты; номинальная сеть 25 кВ",
            "Справочная характеристика серии; не является разрешением приближаться к токоведущим частям.",
            setOf("pantograph", "gv", "transformer", "vvk"), InformationConfidence.REFERENCE,
            "Техническая характеристика ВЛ80С."
        ),
        NormalParameter(
            "gv-state", "Главный выключатель", "Команда, индикация и фактическое состояние должны быть согласованы",
            "Конкретные лампы, реле и условия включения зависят от схемы секции.",
            setOf("gv", "protection"), InformationConfidence.REQUIRES_VARIANT_CHECK,
            "Принципиальная схема и руководство конкретного исполнения."
        ),
        NormalParameter(
            "cooling", "Охлаждение силового оборудования", "До тяговой нагрузки должен быть подтверждён требуемый режим вентиляции",
            "Состав работающих вентиляторов и блокировок зависит от исполнения и режима.",
            setOf("motor-fans", "rectifier", "traction-motors", "burt"), InformationConfidence.REQUIRES_VARIANT_CHECK,
            "Руководство по эксплуатации ВЛ80С, вспомогательные цепи."
        ),
        NormalParameter(
            "pneumatic", "Давления пневмосистемы", "Сверять с манометрами, тормозными правилами и руководством конкретной секции",
            "Приложение сознательно не выдаёт единую уставку для разных приборов и модернизаций.",
            setOf("compressor", "main-reservoirs", "brake-pipe", "tc", "km395", "kvt254"), InformationConfidence.LOCAL_PROCEDURE,
            "Действующие правила тормозов и руководство по пневматическому оборудованию."
        ),
        NormalParameter(
            "temperature", "Нагрев оборудования", "Дым, запах изоляции, изменение цвета и быстрый рост нагрева не являются нормой",
            "Численные пределы применяются только по паспорту конкретного узла и местной технологии контроля.",
            setOf("transformer", "rectifier", "traction-motors", "compressor", "bogie"), InformationConfidence.MANUFACTURER_OR_MANUAL,
            "Руководства по эксплуатации и ремонту соответствующего оборудования."
        )
    )

    fun forEquipment(id: String): List<NormalParameter> = all.filter { id in it.equipmentIds }
}
