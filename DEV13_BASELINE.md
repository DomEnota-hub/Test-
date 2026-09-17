# Dev13 baseline freeze

This document freezes the known-good dev13 source before any dev14 code is
integrated. Dev14 work must start from this branch and preserve the package,
signing identity and existing user paths.

## Source identity

| Item | Value |
| --- | --- |
| Dev13 commit | `8b1fffb7f3e28ae489b9b037f46f4b02be1e3253` |
| Dev14 staging parent | `d3a401e339e8887c4b6c56f3829960d22aab3f65` |
| Package | `ru.railbrake.calculator` |
| Version | `1.2.2-dev13` (`versionCode 141`) |
| Launcher | `.MainActivity` |
| Expected release certificate SHA-256 | `ea9fe9341a6ba72b585bd7ff82ce29c8fc5b7b59e76b8b134df203eaab8e2788` |

## Integrity fingerprints

| Artifact | SHA-256 |
| --- | --- |
| `RailBrakeCalculator.zip` | `1b713a42e88e242653f1e44e3b0406ca7c45ee2cbaa458bf272fc648307b9fa6` |
| `app/build.gradle.kts` | `65f3b44f8c6d6a251b1480732efc4d80d255af4d27ea6e8124bbf1a75aa26c93` |
| `patch/app/build.gradle.kts` | `65f3b44f8c6d6a251b1480732efc4d80d255af4d27ea6e8124bbf1a75aa26c93` |
| `patch/app/src/main/AndroidManifest.xml` | `27f2c7ca97bc39c8761071f80504498141f1ed9e89926bd32621126a85064ee3` |
| Git tree entries (`app`, `patch`, archive) | `d327795112bf814528920d774d2adfc0d5ae7879cd095e2ba8f80fb9b570f457` |

## Existing visible navigation

`Главная`, `Диагностика`, `Локомотивы / атлас`, `Справочник`, `Первая помощь`,
`Расчёты`, `По массе`, `ИДП №12`, `История`, `Вопросы и ответы`, `Настройки`.

The hidden exam route is also part of the baseline: code `1000 + 2381` opens
questions; developer easter egg is `2381 + 999`.

## Persisted state that must survive upgrade

- locomotive profile (`locomotive_profile`);
- diagnostic sessions (`diagnostic_sessions`);
- secret access (`secret_access`).

## Guard rails for dev14

1. Keep `app/` and `patch/app/` mirrors synchronized.
2. Do not change visible dev13 screens while building the data layer.
3. Treat a successful build as insufficient: each checkpoint requires data
   audit, Android interaction proof with fresh UI dumps, and dev13 regression.
4. Integrate one subsystem at a time from the rebuild plan.
