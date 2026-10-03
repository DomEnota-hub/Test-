# ТЭМ2 / ТЭМ2У — Stage 4: Acceptance + cross-layer final QA

Статус: **KNOWLEDGE FOUNDATION COMPLETE / READY FOR APP INTEGRATION**.

Ветка: `feature/tem2-stage4`.
База Stage 4: `44d7456267191b7efb3be92b7dea7e39b455fdb0` — финальный checkpoint расширенного диагностического режима, поэтому завершённая работа по expanded runtime и глобальному Profile Context не теряется.

## Что завершено

### Acceptance contract

Создан `docs/locomotives/diesel/tem2-family/common/stage4_acceptance.json`.

Сессия приёмки поддерживает состояния:

- `NOT_CHECKED`;
- `OK`;
- `NOTE`;
- `NOT_APPLICABLE`.

Есть два равноправных входа:

1. **Начать снаружи**: `OUTSIDE → ENGINE_ROOM → CAB → BRAKE_PNEUMATIC`.
2. **Начать из кабины**: `CAB → BRAKE_PNEUMATIC → ENGINE_ROOM → OUTSIDE`.

Состояние проверки общее для маршрутов, заметка относится к физическому пункту, переключение маршрута не сбрасывает сессию, предусмотрено продолжение незавершённой приёмки.

### Содержание

Авторский core-набор:

- **14 общих проверок** для ТЭМ2/ТЭМ2У;
- **2 профильные проверки ТЭМ2У** — управление одним лицом и система многих единиц, только при фактическом наличии соответствующего оборудования.

Дополнительно действует детерминированная расширенная проекция Acceptance из Stage-2 Atlas:

- ТЭМ2: **33/33** применимых единицы оборудования;
- ТЭМ2У: **35/35** применимых единиц оборудования.

Эта проекция обеспечивает Atlas ↔ Acceptance покрытие без утверждения, что каждый сгенерированный пункт является обязательной сетевой операцией ТО-1.

## Граница обязательности

Stage 4 намеренно не объявляет весь универсальный набор обязательным для каждого депо/оператора.

- Локальный утверждённый процесс может расширять или уточнять объём приёмки.
- Заводские руководства ТЭМ2/ТЭМ2У сохраняют роль исторического заводского baseline и источника по исполнению, но не являются сами по себе текущим разрешением на действие.
- Для тормозного слоя приоритет имеет действующий нормативный контекст с **01.07.2026**.
- Фактически установленное оборудование имеет приоритет над предположением по серии.
- Неизвестное или противоречивое исполнение — `FAIL_CLOSED`.

## Cross-layer QA

Stage 4 проверяет единым контрактом:

**Foundation ↔ Atlas/схемы ↔ Acceptance ↔ Diagnostics ↔ Assistant semantics ↔ Sources ↔ Profile Context**.

Подтверждается:

- Stage 2: 36 сущностей оборудования, 9 систем, 9 функциональных схем; профильная проекция 33 ТЭМ2 / 35 ТЭМ2У;
- Stage 3: **24/24** runtime-v2 диагностических сценария сохранены;
- **22** сценария общие для ТЭМ2/ТЭМ2У, **2** — строго ТЭМ2У;
- каждый диагностический сценарий имеет профиль-совместимый мост в Acceptance через Stage-2 equipment;
- все авторские Acceptance-пункты имеют разрешимые source refs;
- тормозные Acceptance-пункты используют текущий источник `TEM2-SRC-BRAKES-2026` и текущий safety-source;
- ТЭМ2У-специфичное оборудование не попадает в ТЭМ2;
- ТЭМ2Т, ТЭМ2УМ, ТЭМ2А и поздние TEM-family исполнения остаются за fail-closed границей до появления отдельного профиля и доказательств;
- глобальный `LocomotiveProfileRegistry` по-прежнему разрешает ТЭМ2/ТЭМ2У без nullable fallback;
- expanded-emergency runtime/profile boundary не повреждён.

## QA

Новый строгий validator: `app/qa/validate_tem2_stage4.py`.

Workflow: `.github/workflows/tem2-stage4-acceptance.yml`.

Он последовательно выполняет:

1. Stage 1 foundation regression;
2. Stage 2 Atlas/schemes/interactive regression;
3. Stage 3 diagnostics/assistant regression;
4. Stage 4 Acceptance/cross-layer validator;
5. global non-null locomotive profile registry validator;
6. expanded-emergency Stage 4 integration regression;
7. knowledge-only change-boundary;
8. `git diff --check`.

Предварительный полный Stage-4 run перед checkpoint: **37098952604 — SUCCESS** на `34f5a3dc094d744ebe42ebf80aac5d96dd6ac749`.

Финальный критерий закрытия Stage 4: этот checkpoint-коммит также должен пройти тот же workflow на своём точном SHA.

## Integration boundary

Family/global manifests теперь имеют состояние:

`KNOWLEDGE_FOUNDATION_COMPLETE_READY_FOR_APP_INTEGRATION`.

Это означает готовность **базы знаний** ТЭМ2/ТЭМ2У к следующему отдельному этапу интеграции приложения.

Это **не** означает, что Android UI/runtime уже полностью подключил ТЭМ2/ТЭМ2У. Намеренно остаются отдельным следующим этапом:

- Android assets/repositories/screens/navigation;
- production AssistantEngine / AssistantIndex wiring;
- пользовательский выбор рабочего ТЭМ2/ТЭМ2У и end-to-end UI/runtime проверка;
- APK/emulator validation после реальной интеграции.

Глобальный Profile Context уже зарегистрировал `tem2-base` и `tem2u-improved`, поэтому будущая интеграция не должна возвращать `null`: неизвестное исполнение разрешается явным `UNKNOWN_FAIL_CLOSED`.

**Stage 4 заканчивается здесь. Следующий этап — отдельная интеграция ТЭМ2/ТЭМ2У в приложение после отдельного сигнала пользователя.**
