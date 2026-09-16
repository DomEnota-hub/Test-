# Android release signing

Current release signing lineage for **RailBrakeCalculator / «Железнодорожный помощник»**.

## Current key

- Alias: `railbrake-release-v2`
- Certificate DN: `CN=Kuznetsov D.S. (DomEnota), OU=RailBrakeCalculator, O=DomEnota, C=RU`
- Algorithm: RSA 4096 / SHA256withRSA
- Certificate SHA-256: `ea9fe9341a6ba72b585bd7ff82ce29c8fc5b7b59e76b8b134df203eaab8e2788`
- Created: 2026-09-16
- Valid through: 2095-02-26

This key is the required signing key for all future release APKs.

The previous certificate SHA-256 `464a94c7db7badb83c7839a1834d09ceb3b9d1c68c189abdb3b0c88b987eff2a` is historical and must not be used for new releases.

Because the signing certificate changed, an APK signed by the new key cannot update an installed APK signed by the old certificate in place. A one-time uninstall/reinstall is required when moving from the old lineage to this one.

## GitHub Actions secrets

The build workflow expects these repository secrets:

- `RAILBRAKE_KEYSTORE_BASE64`
- `RAILBRAKE_STORE_PASSWORD`
- `RAILBRAKE_KEY_ALIAS`
- `RAILBRAKE_KEY_PASSWORD`

The workflow pins the expected certificate SHA-256 and verifies signed release artifacts with `apksigner`. A release signed by a different certificate must fail verification.

Never commit the JKS, passwords, base64 keystore value, or private credentials to this repository.
