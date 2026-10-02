# Расширенные аварийные приёмы — Stage 0 audit checkpoint

Статус: **STAGE 0 COMPLETE — AUDIT BASELINE READY FOR FINAL CI CONFIRMATION**.

Stage 0 выполнялся как аудит уже сделанной архитектуры режима `EXTENDED_EMERGENCY_KNOWLEDGE` и связанных материалов ЧМЭ3/ЧМЭ3Т/ЧМЭ3Э и ТЭМ2/ТЭМ2У. Новые аварийные способы и новые диагностические сценарии в рамках Stage 0 не добавлялись.

## Что было перепроверено

- opt-in состояние режима и однократное предупреждение;
- пользовательская маркировка расширенного контента;
- app/patch зеркала;
- runtime policy и разделение provenance / action authority;
- ChME3 source matrix, source addendum, field observations и Pass 3 research content;
- отсутствие повышения полевых и устаревших источников до действующего разрешения;
- сохранность стандартной диагностики ЧМЭ3;
- сохранность TEM2 Stage 1 / Stage 2 / Stage 3;
- Android source build, unit tests, lint, assemble и patch hygiene.

## Найденные и исправленные дефекты

1. Исходная policy-модель недостаточно жёстко разделяла происхождение материала и право показывать его как действие. Policy поднята до schema v2 и введены `INFORMATION_ONLY`, `CONDITIONAL_ACTION`, `PROHIBITED`.
2. Для обходов защит, перемычек и иных потенциально опасных вмешательств зафиксирован default `PROHIBITED`; условное действие возможно только с отдельным доказательством применимости и safety-gate.
3. Пользовательский текст режима уточнён так, чтобы включение расширенного режима не воспринималось как разрешение архивных, заводских или полевых способов.
4. По ЧМЭ3 устранён риск overclaim текущей нормативной базы: открытая информация по 996/р сохраняется как secondary/index evidence до проверки точного первичного текста применимого пункта. Исторические, локальные и field-practice материалы не получают текущую authority автоматически.
5. CI режима был self-mutating и мог повторно патчить исходники/делать commit из workflow. Stage 0 перевёл его в read-only validation-only режим.
6. `enablePreviouslyAcknowledged()` мог аварийно завершаться при противоречивом состоянии preferences. Теперь отсутствие acknowledgement даёт fail-closed: режим не включается и приложение не падает.
7. В Stage 0 CI добавлены отдельные regression guards для диагностики ЧМЭ3 и всех трёх завершённых стадий ТЭМ2/ТЭМ2У.

## Зафиксированный runtime/UI контракт

- расширенный режим по умолчанию выключен;
- общее предупреждение требуется только при первом включении;
- предупреждение не повторяется при каждом расширенном сценарии;
- расширенный контент имеет фиксированную бирюзовую рамку и текстовую пометку `Расширенный сценарий`;
- light border: `#0F766E`, dark border: `#5EEAD4`;
- цвет темы/акцента не меняет semantic identity расширенного контента;
- цвет не является единственным носителем статуса;
- опасность/запрет внутри расширенного сценария остаются отдельной красной семантикой;
- standard mode не может войти в expanded branch;
- expanded mode не повышает source authority;
- source, sourceStatus, provenanceClass, profile applicability, riskClass и actionDisposition обязательны для будущих expanded records;
- неизвестное исполнение остаётся `FAIL_CLOSED`;
- adjacent-variant inheritance запрещено без отдельного evidence.

## ChME3 research boundary после аудита

Полевые материалы, локальные рекомендации, старые памятки и технические статьи сохраняются как доказательная база для симптомов, гипотез и различающих проверок. Они не превращаются в выполняемые действия автоматически.

Для материалов, связанных с 996/р, наличие вторичного индекса/учебной страницы не считается проверкой точного действующего первичного пункта. Item-level current authority должна подтверждаться отдельно на следующих этапах.

## ТЭМ2 / ТЭМ2У

Stage 0 не менял завершённое диагностическое содержание Stage 3. Регрессионные проверки сохраняют Stage 1 foundation, Stage 2 Atlas/schemes и Stage 3 diagnostics/assistant semantics. Полевые материалы по-прежнему не дают автоматического action authority.

## QA / CI

Перед checkpoint зелёным был run `37044932894` на SHA `cd398cd3bf252cc6d4245ec19bfe62f6c3fdaadf`.

Stage 0 workflow выполняет:

- `qa/validate_extended_emergency_architecture.py`;
- ChME3 runtime diagnostics validator + diagnostic query tests;
- TEM2 Stage 1 / Stage 2 / Stage 3 validators;
- Kotlin compile source projection;
- `:app:testDebugUnitTest`;
- `:app:lintDebug`;
- `:app:assembleDebug`;
- `git diff --check`.

Этот checkpoint считается окончательно закрытым только после успешного прогона workflow на SHA самого checkpoint-коммита.

## Известное ограничение Stage 0

Полная runtime/release проверка офлайн-ASR на этом этапе невозможна из репозитория: внешний `sherpa-onnx.aar` не хранится в нём. Для проверки Kotlin/UI workflow заменяет только ASR adapter во временном build tree на compile stub. Репозиторные исходники при этом не изменяются. Это не считается полной проверкой реального голосового runtime или подписанной release-сборки и должно быть закрыто на соответствующем интеграционном этапе.

## Что сознательно отложено

Stage 1 и последующие этапы должны отдельно выполнить инвентаризацию реальных expanded-кандидатов, усилить доказательную базу, затем построить содержательные диагностические ветви, runtime-v2/assistant routing и финальный cross-layer QA. Stage 0 новых способов не добавляет.
