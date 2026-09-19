<p align="center">
  <img src="https://capsule-render.vercel.app/api?type=waving&color=0:050505,50:0ea5e9,100:22c55e&height=260&section=header&text=OBD2AI&fontSize=64&fontColor=ffffff&fontAlignY=35&desc=Android%20OBD-II%20Diagnostics%20%2B%20AI&descAlignY=58&animation=twinkling&stroke=22c55e&strokeWidth=2" alt="OBD2AI banner" width="100%" />
</p>

<div align="center">

**Android car diagnostics with AI-powered fault-code explanations.**

[![GitHub stars](https://img.shields.io/github/stars/catsmoker/OBD2AI?style=flat-square&logo=github)](https://github.com/catsmoker/OBD2AI/stargazers)
[![GitHub forks](https://img.shields.io/github/forks/catsmoker/OBD2AI?style=flat-square&logo=github)](https://github.com/catsmoker/OBD2AI/network/members)
[![Open issues](https://img.shields.io/github/issues/catsmoker/OBD2AI?style=flat-square&logo=github)](https://github.com/catsmoker/OBD2AI/issues)
[![Last commit](https://img.shields.io/github/last-commit/catsmoker/OBD2AI?style=flat-square&logo=git)](https://github.com/catsmoker/OBD2AI/commits/main)
[![Repository size](https://img.shields.io/github/repo-size/catsmoker/OBD2AI?style=flat-square)](https://github.com/catsmoker/OBD2AI)
[![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](https://www.android.com/)
[![Min SDK](https://img.shields.io/badge/min%20SDK-24-546E7A?style=flat-square)](https://developer.android.com/studio/releases/platforms)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4%2B-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Latest release](https://img.shields.io/github/v/release/catsmoker/OBD2AI?style=flat-square&label=release)](https://github.com/catsmoker/OBD2AI/releases)
[![License](https://img.shields.io/badge/license-Apache%202.0-0ea5e9?style=flat-square&logo=apache&logoColor=white)](LICENSE)

[![OBD-II](https://img.shields.io/badge/OBD--II-ELM327-22c55e?style=flat-square&logo=car&logoColor=white)](docs/OBD_CONNECTION.md)
[![AI](https://img.shields.io/badge/AI-OpenAI%20%7C%20Gemini%20%7C%20Anthropic%20%7C%20Ollama-8E44AD?style=flat-square&logo=openai&logoColor=white)](docs/AI_PROVIDERS.md)
[![Gradle](https://img.shields.io/badge/build-Gradle-02303A?style=flat-square&logo=gradle&logoColor=white)](https://gradle.org/)
[![Bluetooth](https://img.shields.io/badge/connect-Bluetooth%20SPP%20%7C%20WiFi%20%7C%20Demo-2E7D32?style=flat-square&logo=bluetooth&logoColor=white)](docs/OBD_CONNECTION.md)
[![Telegram](https://img.shields.io/badge/chat-Telegram-26A5E4?style=flat-square&logo=telegram&logoColor=white)](https://t.me/CATSM0KER)
[![PayPal](https://img.shields.io/badge/support-PayPal-00457C?style=flat-square&logo=paypal&logoColor=white)](https://www.paypal.me/catsmoker)

[![Typing effect](https://readme-typing-svg.demolab.com?font=Fira+Code&size=20&duration=2800&pause=900&color=0EA5E9&center=true&vCenter=true&width=620&lines=Read+your+car.+Understand+every+code.;Live+OBD-II+data+with+AI+insight.;Bluetooth,+WiFi+or+demo+%E2%80%94+your+choice.)](https://github.com/catsmoker/OBD2AI)

[Download](https://github.com/catsmoker/OBD2AI/releases) · [Report an issue](https://github.com/catsmoker/OBD2AI/issues)
</div>

**OBD2AI** connects to your car's OBD-II adapter over Bluetooth or WiFi, reads diagnostic trouble codes (DTCs) and live telemetry (speed, RPM, coolant temperature), and uses AI to turn cryptic fault codes into human-friendly assessments with severity and suggested actions.

---

## 🚀 Key Features

### 🔧 Core Diagnostics
- **DTC Reading**: Read stored, pending and permanent trouble codes (SAE J2012) from the vehicle.
- **Bluetooth & WiFi**: Connect over classic Bluetooth SPP or WiFi TCP to ELM327-compatible adapters.
- **Demo Mode**: Explore the full app with a simulated adapter — no car required.
- and more...

### 🤖 AI Assessments
- **Plain-Language Explanations**: Every code gets severity, title, details, implications and suggested actions as structured JSON.
- **4 Providers**: OpenAI, Google Gemini, Anthropic, or any OpenAI-compatible local server (Ollama, LM Studio, llama.cpp, OpenRouter).
- **Offline Fallback**: No key or no network? You still get a system/generic-origin assessment per code — never an error wall.
- and more...

### 📊 Live Data & Experience
- **Real-Time Gauges**: Speedometer-style gauges for speed, RPM and coolant temperature.
- **AI Voice Insight**: Spoken plain-text driving insight via TTS on the live-data screen.
- **GPS Speed Source**: Use the OBD adapter or the phone's GPS for speed.
- **5 Languages & 3 Themes**: English, Spanish, Arabic, Chinese + system/light/dark themes.
- and more...

## 📋 Table of Contents

- [Features](#-key-features)
- [How It Works](#-how-it-works)
- [Installation Guide](#-installation-guide)
- [Supported Adapters](#-supported-adapters)
- [Device Compatibility](#-device-compatibility)
- [Build From Source](#build-from-source)
- [Star History](#-star-history)
- [License](#-license)
- [Contributing & Support](#-contributing--support)

---

## 🔍 How It Works

OBD2AI bridges the gap between raw OBD-II bytes and something a driver can actually act on.

### Connection (Bluetooth / WiFi / Demo)
- **Bluetooth**: Classic SPP socket (`00001101-0000-1000-8000-00805F9B34FB`) to your ELM327 adapter.
- **WiFi**: Plain TCP to the adapter's host/port — the same stream pipeline as Bluetooth.
- **Demo**: A simulated `DemoObdSource` with sliders driving the live data — perfect for trying the app indoors.

### ELM327 Init
On connect the app sends a fixed init sequence — `ATZ, ATE0, ATL0, ATS0, ATH0, ATSP0, ATAT1`. Spaces off and headers off make responses deterministic across ELM327 clones, which the parsers rely on. See [docs/OBD_CONNECTION.md](docs/OBD_CONNECTION.md).

### DTCs + AI
Stored/pending/permanent codes are read from the vehicle, cached to `dtc_results.json`, and each code is assessed **in parallel** by the provider you picked in Settings. Parsing is deliberately lenient (strips code fences and prose) because local models ramble. Results without AI are marked `offline=true` instead of failing. See [docs/AI_PROVIDERS.md](docs/AI_PROVIDERS.md).

---

## 📥 Installation Guide

### Download OBD2AI: [Releases](https://github.com/catsmoker/OBD2AI/releases)

### Connect your adapter
1. **Plug in** your ELM327 adapter to the car's OBD-II port (usually under the dashboard) with the ignition on.
2. **Pair it** in Android Bluetooth settings (classic adapters often use PIN `1234` or `0000`), or join the adapter's WiFi network.
3. **Open OBD2AI** → grant Bluetooth/location permissions when asked → pick your adapter (or host/port for WiFi).
4. **No adapter?** Use **Demo mode** from the connect screen to explore everything.

### Configure AI (Settings)
The app ships with **no API key in the build**. Open **Settings**, pick a provider, save your key (stored only on-device, excluded from backups). The key is **required** for AI assessments from cloud providers — without one you only get generic offline info:
- **OpenAI** — API key, default `gpt-4o-mini` (cheapest; OpenAI has no free API tier).
- **Google Gemini** — AI Studio key, default `gemini-3.1-flash-lite` (free tier, no card needed).
- **Anthropic** — API key, default `claude-haiku-4-5` (cheapest Claude tier; paid-only API).
- **Custom (OpenAI-compatible)** — base URL + model id, default `llama3.2` (e.g. `http://192.168.1.10:11434/v1`). Leave the key blank for keyless local servers.

> **Permissions note:** Bluetooth discovery needs location permission on older Android versions, and `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` on Android 12+. Emulators generally lack Bluetooth hardware — use a physical device or demo mode.

---

## 🚗 Supported Adapters

OBD2AI works with **ELM327-compatible** OBD-II adapters (genuine or clones), including:
- **Bluetooth SPP** adapters (most common ELM327 clones).
- **WiFi** ELM327 adapters (TCP connection).
- *And any adapter speaking the ELM327 AT command set...*

> [!TIP]
> Having trouble with a specific adapter? Open an [issue](https://github.com/catsmoker/OBD2AI/issues) with the adapter model, connection type (Bluetooth/WiFi), and a logcat snippet of the failure.

---

## 📱 Device Compatibility

- **Android Version**: 7.0 (API 24) and newer (target SDK 36).
- **Tested**: Physical devices with Bluetooth are recommended; emulators generally don't support Bluetooth hardware (use demo mode there).
- **Languages**: English, Spanish, Arabic, Chinese (Simplified) + system default.
- **Themes**: System / light / dark.

---

## Build From Source

Requirements: Android Studio, Java 17, Android SDK (compileSdk 37, build-tools 36.1.0).

```bash
./gradlew assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. Full details (versions, tests, troubleshooting) in [docs/BUILD.md](docs/BUILD.md).

---

## 🛡️ Disclaimer
**Warning**: Never interact with the app while driving — set it up while parked. AI assessments are advisory only and **not** a substitute for a qualified mechanic. Clearing codes without fixing the underlying fault can hide real problems. The developers are not responsible for misdiagnosis, vehicle damage, or traffic incidents.

---

## 📄 License
Licensed under the **Apache License, Version 2.0**. See [LICENSE](LICENSE) for details.

---

## 🤝 Contributing & Support

- **Donate**: Support the project via [PayPal](https://www.paypal.me/catsmoker)
- **Report Bugs**: [GitHub Issues](https://github.com/catsmoker/OBD2AI/issues)
- **Security**: please read [SECURITY.md](SECURITY.md) before reporting vulnerabilities.
- Contributions are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) and open an issue before starting a large change.

---

## 📊 Star History

Track the growth and community adoption of OBD2AI over time. Click the chart to explore detailed analytics on GitHub Star History.

<a href="https://www.star-history.com/?repos=catsmoker%2FOBD2AI&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=catsmoker/OBD2AI&type=date&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=catsmoker/OBD2AI&type=date&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=catsmoker/OBD2AI&type=date&legend=top-left" />
 </picture>
</a>
