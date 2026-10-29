# Contributing to Arya

Thanks for helping! Arya is a small project, so the process is light.

## Ground rules

- Be kind. Assume good intent.
- Keep **privacy** first: no analytics, no ads, no new network destinations, no new sensitive permissions without a clear discussion.
  Changes that add permissions must also update `PRIVACY.md` and `docs/PLAY_STORE.md`.
- Keep changes focused. One feature or fix per pull request.

## Setup

JDK 17, Android SDK 36, Gradle 8.14+. See the README for build steps. `./gradlew :app:assembleDebug` must pass before you open a PR.

## Code style

- Kotlin official style (`kotlin.code.style=official`), 4-space indent, no wildcard imports in new code.
- Compose: stateless composables take state and callbacks; long-lived state lives in `AryaEngine` or the stores in `data/`.
- Threading: `AryaEngine` state is touched only on its single engine thread. Do not block it, and do not call Android UI APIs from it.

## Reporting bugs

Use the bug template. Include phone model, Android version, whether the wake word or the foreground service was involved, and
`adb logcat -s AryaWake` output if you can. **Never paste your API key.**

## Security

Please report vulnerabilities privately through GitHub's "Report a vulnerability" instead of a public issue.
