# Publishing Arya on Google Play

A checklist for the Play Console. I verified the target-API and foreground-service points against Google's pages
in October 2026, but Play policy changes often, so read the current text before you submit.

## Already handled in the app

- **Target API**: `targetSdk = 36` (Android 16). Play requires new apps and updates to target API 36 from 31 Aug 2026;
  API 37 is next. `minSdk = 30` (Android 11+).
- **Android 15/16 behaviour**: edge-to-edge UI with window insets, predictive back enabled, no fixed orientation.
- **16 KB page sizes**: Arya has no native libraries, so nothing to rebuild.
- **No restricted permissions**: no `QUERY_ALL_PACKAGES` (package queries are limited to launcher apps), no
  `SYSTEM_ALERT_WINDOW`, no full-screen intents, no exact-alarm permissions, no call/SMS permissions. Calls only open
  the dialer, alarms and timers go through the clock app.
- **Prominent disclosures** before the microphone permission, before the wake word and before contacts access.
- **Foreground service** only starts from a user action while the app is on screen, never at boot, and always shows a
  notification with a Stop button.
- **Secrets**: the API key is encrypted with the Android Keystore; `allowBackup` is off; nothing is logged.
- **AI-generated content**: in-app "Report a problem" and long-press "Report this reply" (see below).

## You must do

1. **Set the report destination** in `gradle.properties` (`aryaReportEmail` and/or `aryaReportUrl`). Play expects AI apps
   to offer in-app reporting; Arya has no server, so reports open the user's email app.
2. **Host the privacy policy** (`PRIVACY.md`, fill in the placeholders) and set `aryaPrivacyUrl`. Enter the same URL in Play Console.
3. **Foreground service declaration** (App content → Foreground service permissions): declare type **Microphone**
   ("Background audio input"), describe the user-facing feature (voice conversation, optional "Hey Arya" listening with a
   visible notification), explain that it is started by the user, and link a short **demo video** showing: enabling the
   feature in Settings, the disclosure dialog, the notification, and stopping it.
4. **Data safety form** (verify each line yourself):
   - Audio (voice recordings) and text messages: collected, **shared** with Google (Gemini API) to provide the feature;
     not stored by the app; processed ephemerally by Google under its terms; data is encrypted in transit.
   - Contacts: accessed on device only, not collected (names of ambiguous matches are sent to Gemini, mention this).
   - No data sold, no ads, no analytics. Users can delete data by clearing app storage or uninstalling.
5. **App access**: the app needs a Gemini API key. Give reviewers working instructions and a **temporary, quota-limited
   key** (App content → App access), otherwise the review will fail.
6. **Target audience**: choose an adult audience (Gemini API terms restrict use by minors; check the current terms).
7. **Content rating questionnaire**: answer for an AI chat app that can produce user-directed content.
8. **Artwork rights**: make sure you may publish the character image in `res/drawable-nodpi/arya_portrait.webp`
   and the launcher icon. If the art was generated with AI, say so in the listing where required.
9. **Store listing**: the 512×512 icon (`store/icon-512.png`) and a 1024×500 feature graphic (`store/feature-graphic.png`) are included; you still need phone screenshots.
10. **Signing**: enroll in Play App Signing. Create an upload key, store it as GitHub secrets
    (`ARYA_KEYSTORE_BASE64`, `ARYA_KEYSTORE_PASSWORD`, `ARYA_KEY_ALIAS`, `ARYA_KEY_PASSWORD`); CI then builds a signed
    `app-release.aab` to upload.
11. **Test on real phones** (Android 11, 13, 14 and 16 at least) before the closed-testing track, especially the wake word,
    the foreground service and audio echo on the speaker. New personal developer accounts must run a closed test first.

## What can still get an app rejected

- Always-on microphone features get extra review. If the foreground-service declaration is refused, ship with the wake
  word limited to "While the app is open" (remove `FOREGROUND_SERVICE_MICROPHONE` and the service) and keep conversations
  in the app.
- Missing or unclear disclosures, a missing privacy policy URL, or reviewers who cannot sign in (no API key).
- Misleading claims: do not describe Arya as always accurate, and keep the "replies are AI-generated" notice.
