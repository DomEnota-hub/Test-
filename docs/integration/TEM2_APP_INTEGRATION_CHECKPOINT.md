# ТЭМ2 / ТЭМ2У — финальный checkpoint интеграции приложения

Статус: **APP INTEGRATION COMPLETE / FINAL SHA VALIDATION REQUIRED**.

Ветка: `integration/tem2-app`.

Исходная база интеграции: `3e717ca3fadfd0bd0d77eec0581da2b54b66a893` — финальный Stage 4 knowledge checkpoint ТЭМ2/ТЭМ2У.

Предфинальный интеграционный SHA: `aa894f7f6a645c986bd69a6cd329750bb5e98f9e`.

## Что интегрировано

- Android assets для ТЭМ2 и ТЭМ2У: Atlas/catalog, diagnostics, interactive scheme layout metadata и stepwise scheme flows.
- `LocomotiveCatalogRegistry`, `TechnicalDataRepository`, `WorkingLocomotive`, `LocomotiveProfileContext`.
- Выбор рабочего профиля ТЭМ2 / ТЭМ2У без nullable fallback.
- Диагностика runtime-v2 и профильный `Tem2DiagnosticRepository`.
- Assistant loader/parser/engine/conversation routing для ТЭМ2 / ТЭМ2У.
- Атлас, интерактивные схемы, диагностика и приёмка подключены к Android UI/runtime.
- Источники представлены через human-readable source presentation layer.
- app/patch зеркала синхронизированы.
- Expanded emergency profile/runtime boundary сохранён.

## Профильная безопасность

- `tem2-base` и `tem2u-improved` разрешаются через единый Profile Context.
- Неизвестное/противоречивое исполнение остаётся `UNKNOWN_FAIL_CLOSED`, а не `null`.
- ТЭМ2Т, ТЭМ2УМ, ТЭМ2А и иные соседние исполнения не наследуются автоматически.
- Глобальный profile registry guard остаётся обязательной регрессией для будущих локомотивов.

## QA перед checkpoint

Workflow: `TEM2 app integration validation`.

Run `37105410237` на `aa894f7f6a645c986bd69a6cd329750bb5e98f9e` завершён **SUCCESS**.

Подтверждено:

1. TEM2 Stage 1–4 regressions — PASS.
2. Global locomotive profile registry — PASS.
3. Expanded emergency Stage 4 integration regression — PASS.
4. TEM2 Android asset/projection QA — PASS.
5. ChME3 Android asset regression — PASS.
6. Реальный pinned `sherpa-onnx-1.13.8.aar` скачан и проверен по SHA-256.
7. `:app:testDebugUnitTest` — PASS.
8. `:app:lintDebug` — PASS.
9. `:app:assembleDebug` — PASS.
10. Android API 34 emulator smoke — PASS для ТЭМ2 и ТЭМ2У.

Emulator smoke проверяет выбор рабочего локомотива, Atlas, интерактивную схему, диагностику, приёмку и сохранение рабочего профиля после force-stop/restart.

## Исправления по ходу emulator QA

Два последних падения были дефектами smoke harness, а не runtime приложения:

- `xml.etree.ElementTree.Element` ошибочно использовался как boolean при поиске Compose node;
- Compose selectable/checkable chip мог быть actionable при `checkable=true`, хотя `clickable=false` в uiautomator tree.

Smoke harness исправлен и после этого полный emulator route прошёл зелёным.

## Integration boundary

Beta и Alpha не изменялись.

Этот checkpoint фиксирует завершённую интеграцию ТЭМ2/ТЭМ2У в Test. После создания checkpoint-коммита тот же полный workflow должен пройти повторно **на точном checkpoint SHA**. Только после этого интеграция считается окончательно закрытой.
