<!-- Replace OWNER/REPO below with your GitHub path once, e.g.:  sed -i 's#OWNER/REPO#yourname/arya#g' README.md -->

<div align="center">

<img src="docs/banner.png" alt="Arya — your voice AI companion" width="100%">

<br>

# Arya

**An open-source voice AI companion for Android.**
Native Kotlin and Jetpack Compose, talking to Gemini Live straight from your phone.

<br>

[![Build](https://img.shields.io/github/actions/workflow/status/spidertox/Arya/android.yml?branch=main&style=for-the-badge&logo=githubactions&logoColor=white&label=build)](https://github.com/OWNER/REPO/actions/workflows/android.yml)
[![Release](https://img.shields.io/github/v/release/OWNER/REPO?include_prereleases&style=for-the-badge&logo=github&label=release)](https://github.com/OWNER/REPO/releases)
[![License](https://img.shields.io/github/license/OWNER/REPO?style=for-the-badge&label=license&color=blue)](LICENSE)
[![Stars](https://img.shields.io/github/stars/OWNER/REPO?style=for-the-badge&logo=github)](https://github.com/OWNER/REPO/stargazers)

![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Android 11+](https://img.shields.io/badge/Android-11%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Target SDK 36](https://img.shields.io/badge/target%20SDK-36-34A853?style=for-the-badge&logo=android&logoColor=white)
![Gemini Live](https://img.shields.io/badge/Gemini%20Live-API-8E75B2?style=for-the-badge&logo=googlegemini&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-8.14-02303A?style=for-the-badge&logo=gradle&logoColor=white)

![No ads](https://img.shields.io/badge/ads-none-success?style=for-the-badge)
![No analytics](https://img.shields.io/badge/analytics-none-success?style=for-the-badge)
![No server](https://img.shields.io/badge/server-none-success?style=for-the-badge)
![PRs welcome](https://img.shields.io/badge/PRs-welcome-brightgreen?style=for-the-badge)
![Status](https://img.shields.io/badge/status-early%20v0.2-orange?style=for-the-badge)

[Features](#-features) ·
[Quick start](#-quick-start) ·
[Using Arya](#-using-arya) ·
[Architecture](#-architecture) ·
[Privacy](#-privacy--security) ·
[Contributing](#-contributing)

</div>

---

> [!WARNING]
> **Early software.** This native version has been written and reviewed but **never compiled or run on a device** by its
> author: the first CI build will probably need small fixes, and the wake word, audio echo handling and foreground service
> need testing on real phones. Please open an issue with your device and Android version if something misbehaves.

## ✨ Features

| | |
| --- | --- |
| 🎙️ **Real-time voice** | Talk naturally in Hindi, English or Hinglish. Interrupt Arya mid-sentence, like a phone call. |
| ⌨️ **Type or talk** | Typed messages are answered out loud too. Long-press any reply to copy or report it. |
| 👋 **"Hey Arya"** | Optional wake word: off, only while the app is open, or always (a visible notification stays while she listens). |
| 📱 **Phone commands** | Opens apps, sets alarms and timers, opens the dialer, searches the web, flashlight, volume. Calls only open the dialer. |
| 🧠 **Local memory** | Ask her to remember things. Memories live on your phone and you can delete any of them. |
| 🎨 **A living interface** | Animated aurora background, voice-reactive equalizer, themes that glide when Arya (or you) change them. |
| 🔒 **Private by design** | Your own Gemini key, encrypted with the Android Keystore. No Arya server, no ads, no analytics. |
| 🧩 **Play-ready** | Prominent disclosures, in-app report flow, no restricted permissions. See [docs/PLAY_STORE.md](docs/PLAY_STORE.md). |

## 📸 Screenshots

Screenshots will be added after the first build on real devices. Drop PNGs into `docs/screenshots/` and reference them here.

## 🚀 Quick start

1. **Get a Gemini API key** (free): <https://aistudio.google.com/apikey>
2. **Install Arya**
   - From GitHub: open **Actions → Build Arya**, download `Arya-debug-apk` from the latest run, and install it
     (allow "install unknown apps" for your browser or file manager), **or**
   - build it yourself, see below.
3. Open Arya, paste the key, tap the microphone, and say hello.

## 🛠️ Build from source

**Requirements:** JDK 17, Android SDK 36 (Android Studio recommended), Gradle 8.14+.

```bash
git clone https://github.com/OWNER/REPO.git && cd REPO
gradle wrapper --gradle-version 8.14.3     # once, no wrapper is committed
./gradlew :app:assembleDebug               # → app/build/outputs/apk/debug/app-debug.apk
```

Or open the folder in Android Studio and press **Run**.

<details>
<summary><b>Build on GitHub Actions (no local setup)</b></summary>

Push to `main`: **Actions → Build Arya** produces a debug APK artifact on every run.

For a signed release (APK + AAB for Google Play) add these repository secrets and push a tag such as `v0.2.0`:

| Secret | Meaning |
| --- | --- |
| `ARYA_KEYSTORE_BASE64` | your upload keystore, base64-encoded |
| `ARYA_KEYSTORE_PASSWORD` | keystore password |
| `ARYA_KEY_ALIAS` | key alias |
| `ARYA_KEY_PASSWORD` | key password |

</details>

<details>
<summary><b>Configuration</b></summary>

`gradle.properties` holds three optional values: `aryaReportEmail`, `aryaReportUrl` (where in-app reports go) and `aryaPrivacyUrl`.
In the app, **Settings** controls voice, theme, name, wake word, contacts access and (under *Advanced*) the API key and Live model id
(default `gemini-3.1-flash-live-preview`).

</details>

## 🎙️ Using Arya

Tap the microphone and talk. A few things to try:

| Say | What happens |
| --- | --- |
| "Open WhatsApp" / "YouTube kholo" | Opens the app |
| "Set an alarm for 6:30" | Sets it in your clock app |
| "Timer for 10 minutes" | Starts a timer |
| "Call Mom" | Looks up the contact and opens the dialer (you press call) |
| "Search the weather in Delhi" | Opens a web search |
| "Turn on the flashlight" · "Volume 40" | Flashlight and media volume |
| "Change the theme to emerald" | The whole UI changes colour |
| "Remember that I like green tea" | Saved to local memory |

When the app is closed, Android does not let apps launch other apps, so Arya posts a **"tap to…"** notification instead.
A conversation ends after 90 seconds of silence to release the microphone.

## 🏗️ Architecture

```mermaid
flowchart LR
    subgraph Phone["📱 Your phone"]
        Mic["🎙️ Microphone"] --> Engine["AryaEngine<br/>state machine"]
        Engine --> Speaker["🔊 Speaker"]
        Wake["“Hey Arya”<br/>SpeechRecognizer"] --> Service["Foreground service<br/>(microphone)"]
        Service --> Engine
        Engine --> Tools["Phone commands<br/>apps · alarms · dialer"]
        UI["Jetpack Compose UI"] <--> Engine
    end
    Engine <-->|"WebSocket<br/>PCM audio + tool calls"| Gemini[("Gemini Live API<br/>your API key")]
```

<details>
<summary><b>Project structure</b></summary>

```
app/src/main/java/com/arya/companion/
├── live/
│   ├── AryaEngine.kt        conversation state machine, mic gate, barge-in, tools, idle timeout
│   ├── LiveClient.kt        OkHttp WebSocket to BidiGenerateContent
│   ├── Audio.kt             AudioRecord / AudioTrack, echo cancellation, speaker and headset routing
│   ├── Persona.kt           Arya's system instruction
│   └── Tools.kt             function declarations and runner
├── device/DeviceActions.kt  phone commands through standard Android intents
├── service/
│   ├── AryaVoiceService.kt  foreground service hosting a conversation and the wake word
│   └── WakeWordListener.kt  "Hey Arya" on Android's SpeechRecognizer
├── data/                    settings (API key in the Keystore) and memories
└── ui/                      theme, components, screens, dialogs
```

</details>

## 🔐 Privacy & security

- **Your key, your phone.** The Gemini API key is stored encrypted with the Android Keystore and sent only to Google.
- **Audio** goes to Gemini only while a conversation is active. With the wake word on, Android's speech recognizer listens for the name
  first (on-device with an offline pack on Android 13+, otherwise Google's speech service). Nothing reaches Gemini before "Hey Arya".
- **No server, no ads, no analytics, no tracking.** Memories stay on the phone; `allowBackup` is off.
- **Permissions are minimal**: microphone, notifications, foreground service, alarm, optional contacts. No overlay, no call or SMS, no all-apps query.

Full text: [PRIVACY.md](PRIVACY.md).

## 📱 Compatibility

| | |
| --- | --- |
| Minimum | Android 11 (API 30) |
| Target / compile | Android 16 (API 36), the level Google Play requires from 31 Aug 2026 |
| Devices tested | none yet |
| Known quirks | some phones beep when the wake-word recognizer restarts; aggressive battery savers may stop the background service |

## 🗺️ Roadmap

- [ ] First verified build and device testing (Android 11, 13, 14, 16)
- [ ] Dedicated wake-word engine (Porcupine or openWakeWord) instead of speech-to-text matching
- [ ] Optional animated character (video loops or Live2D) with lip-sync
- [ ] Localised UI (Hindi first)
- [ ] Home-screen widget and quick-settings tile
- [ ] Unit tests for the engine and tool runner

## 🤝 Contributing

Issues and pull requests are welcome, see [CONTRIBUTING.md](CONTRIBUTING.md). Bug reports are most useful with the phone model,
Android version and what you said or tapped just before it happened.

## ❓ FAQ

<details>
<summary><b>Does it cost anything?</b></summary>
Arya is free. Gemini has a free tier for API keys; usage beyond it is billed by Google to your own account.
</details>

<details>
<summary><b>Why does Arya say she opened a notification instead of the app?</b></summary>
Android blocks apps from launching other apps while they are in the background. Tap the notification to finish the action.
</details>

<details>
<summary><b>Can I change her look?</b></summary>
Yes: replace <code>app/src/main/res/drawable-nodpi/arya_portrait.webp</code> (transparent WebP) and the "YOUR LOOK" paragraph in <code>Persona.kt</code>.
Only use artwork you have the rights to distribute.
</details>

<details>
<summary><b>The model name stopped working</b></summary>
Live model names change. Set a current one under Settings → Advanced → Live model.
</details>

## 📄 License & credits

Apache License 2.0, see [LICENSE](LICENSE) and [NOTICE](NOTICE). Arya began as a native Android rewrite of *MYRAA* (a Windows desktop companion).
Built with [Kotlin](https://kotlinlang.org), [Jetpack Compose](https://developer.android.com/compose), [OkHttp](https://square.github.io/okhttp/)
and the [Gemini Live API](https://ai.google.dev/gemini-api/docs/live). The banner is typeset in Poppins (SIL Open Font License).
The character artwork is separate from the source code: use only art you have the rights to distribute.

<div align="center">

<br>

![Issues](https://img.shields.io/github/issues/OWNER/REPO?style=flat-square)
![Last commit](https://img.shields.io/github/last-commit/OWNER/REPO?style=flat-square)
![Downloads](https://img.shields.io/github/downloads/OWNER/REPO/total?style=flat-square)
![Top language](https://img.shields.io/github/languages/top/OWNER/REPO?style=flat-square&logo=kotlin)
![Repo size](https://img.shields.io/github/repo-size/OWNER/REPO?style=flat-square)

<sub>If Arya is useful to you, a ⭐ on GitHub helps others find it.</sub>

</div>
