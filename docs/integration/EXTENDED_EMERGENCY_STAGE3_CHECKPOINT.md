# Расширенные аварийные приёмы — Stage 3 runtime-v2 / assistant semantics checkpoint

Статус: **STAGE 3 COMPLETE — DETERMINISTIC RUNTIME-V2 PROJECTION + ASSISTANT SEMANTICS GREEN BEFORE FINAL CHECKPOINT RUN**.

Stage 3 выполнялся как построение runtime-v2 представления расширенной диагностики поверх уже завершённого Stage 2 discovery. На этом этапе стандартные диагностические графы не заменялись и Android runtime не переподключался: расширенный слой формируется детерминированной проекцией из канонических Stage 2 TSV и остаётся отдельным evidence-layer до финальной интеграции Stage 4.

## Каноническая runtime-проекция

Создан `qa/build_extended_emergency_stage3_projection.py`.

Он детерминированно формирует:

- **92/92** решения маршрутизации для существующих стандартных диагностических веток;
- **77** расширенных runtime-v2 маршрутов кандидатов;
- **49** маршрутов `INFORMATION_ONLY`;
- **28** маршрутов `PROHIBITED`;
- **0** маршрутов `CONDITIONAL_ACTION`.

Распределение по профилям сохраняется без неявного наследования:

- `chme3-base`: **53**;
- `chme3t-rheostatic`: **1**;
- `chme3e-electronic`: **0**;
- `tem2-base`: **22**;
- `tem2u-improved`: **1**.

Нулевой результат ЧМЭ3Э сохраняется как fail-closed: базовые полевые/аварийные методы ЧМЭ3 не наследуются электронному исполнению автоматически. ЧМЭ3Т получает только доказанный профильный expanded-кандидат `CHME3-DIAG-110`. ТЭМ2У получает только отдельный профильный кандидат `TEM2-DIAG-024`, а не общий пакет ТЭМ2.

## Связь со стандартной диагностикой

Стандартный диагностический сценарий остаётся канонической точкой входа и action-boundary.

Порядок маршрутизации ассистента:

1. распознать запрос как диагностику неисправности;
2. выбрать стандартный диагностический сценарий;
3. определить точный профиль;
4. если расширенный режим включён — прикрепить соответствующий expanded evidence-layer;
5. переходить к Атласу только если диагностическое намерение не найдено.

Поэтому включение расширенного режима не создаёт отдельный конкурирующий intent и не может перенаправить запрос о неисправности в Атлас вместо диагностики.

Режим OFF: `STANDARD_ONLY`.

Режим ON при наличии кандидата: `STANDARD_PLUS_EXTENDED_EVIDENCE`.

Режим ON без доказанного дополнительного метода: `STANDARD_ONLY_NO_EXTENDED_METHOD` без наследования от соседнего исполнения.

## Runtime-v2 expanded routes

Каждый expanded-кандидат имеет отдельный runtime-v2 граф с обязательным policy-gate:

- `extendedEmergencyMode == true`;
- точное совпадение профиля;
- adjacent-variant inheritance = `DENY`.

На текущем Stage 3 расширенные графы сознательно не содержат типов узлов `action`, `check`, `instruction` или `procedure`. Они используют только gate / notice / finding / terminal и тем самым не могут превратить исследовательский материал в команду к выполнению.

Для `INFORMATION_ONLY` приложение может показать дополнительную гипотезу, историческую/заводскую/полевую диагностическую информацию и provenance, после чего возвращает пользователя к безопасной локализации стандартного графа.

Для `PROHIBITED` показывается только факт существования нестандартного способа, его статус, риск и источник. Процедура выполнения намеренно отсутствует.

У всех 77 маршрутов:

- `currentAuthorityVerified = false`;
- `executable = false`;
- `procedureVisible = false`;
- `canBecomeExecutableAtRuntime = false`.

Runtime не может сам повысить authority записи.

## UI / presentation contract

Expanded evidence отображается непосредственно после стандартной локализации, если режим включён и профиль совпадает.

Сохраняется согласованный контракт:

- бейдж **«Расширенный сценарий»**;
- фиксированная бирюзовая рамка: light `#0F766E`, dark `#5EEAD4`;
- цвет не является единственным носителем статуса;
- красный остаётся отдельной семантикой опасности/запрета;
- общее предупреждение не повторяется при каждом открытии сценария.

Источник и его статус должны оставаться видимыми пользователю.

## Assistant semantics

Создан `docs/integration/extended_emergency_stage3_assistant_contract.json`.

Он закрепляет:

- troubleshooting intent имеет приоритет над Atlas для языка неисправностей;
- расширенный режим не создаёт отдельный intent;
- расширенный слой прикрепляется только к уже выбранному стандартному сценарию;
- стандартные opposite-event semantics остаются каноническими и не схлопываются expanded-слоем;
- неизвестное исполнение = `FAIL_CLOSED`;
- raw internal IDs не должны отображаться пользователю;
- `INFORMATION_ONLY` не формулируется как императивное действие;
- `PROHIBITED` не содержит процедуры и не становится исполняемым.

## QA

Создан строгий validator `qa/validate_extended_emergency_stage3_runtime.py`. Он проверяет:

- детерминированность builder-а двумя независимыми построениями;
- точные количества 92 / 77 / 49 / 28 / 0;
- полное соответствие candidate set Stage 2;
- sourceRefs, methodClass, provenance, sourceStatus, riskClass и profiles без дрейфа;
- runtime-v2 graph integrity: уникальные IDs, валидные edges, достижимость всех узлов и наличие terminal;
- отсутствие executable/procedural node types;
- отсутствие procedure-level опасных деталей;
- точные mode/profile gates;
- fail-closed для 16 `NO_ADDITIONAL_METHOD_FOUND` веток;
- профильную изоляцию ЧМЭ3Т / ЧМЭ3Э / ТЭМ2У;
- turquoise presentation contract и независимую красную danger semantics;
- запрет `CONDITIONAL_ACTION` на текущем этапе.

Workflow `Expanded emergency Stage 3 runtime` дополнительно прогоняет:

- Stage 2 discovery validator;
- Stage 1 inventory validator;
- Stage 0 architecture validator;
- ChME3 Android projection regression;
- diagnostic graph / unknown-route regression;
- TEM2 Stage 1 / Stage 2 / Stage 3 regression;
- Stage 3 runtime-only change boundary относительно финального Stage 2 SHA `6a76fc0ada4526e0b5f6415095b9d492ed986c9c`;
- `git diff --check`.

Полностью зелёный прогон до checkpoint: run `37056812643`, SHA `b360cfbebab3da1134ff8685c409ffeefcb5e509`.

Два предыдущих неуспешных прогона (`37056172627`, `37056358216`) выявили только ошибку учёта нулевого профиля `chme3e-electronic` в статистике builder/validator. Данные, candidate set и Stage 2 regression при этом проходили. Счётчик нормализован явно, после чего полный набор проверок стал зелёным.

## Граница Stage 3 / Stage 4

Stage 3 создаёт канонический и проверяемый runtime-v2 expanded projection и контракт ассистента, но **не подключает его окончательно к Android runtime/UI** и не повышает research-записи до выполняемых методов.

Stage 4 должен выполнить финальную cross-layer QA и интеграцию режима в приложение: wiring runtime projection, отображение expanded cards в диагностике/ассистенте, реальное переключение настройки, UI regression, Android tests/lint/assemble и итоговый integration checkpoint.

Этот Stage 3 checkpoint считается окончательно закрытым только после успешного workflow на SHA самого checkpoint-коммита.
