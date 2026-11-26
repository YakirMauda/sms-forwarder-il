<h1 align="center">SMS Forwarder IL</h1>

<p align="center">
  <img src="https://img.shields.io/badge/API-26%2B-brightgreen" alt="API 26+">
  <img src="https://img.shields.io/badge/Kotlin-2.0-purple" alt="Kotlin">
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-blue" alt="Jetpack Compose">
  <img src="https://img.shields.io/badge/License-AGPL%20v3-blue" alt="License">
</p>

A personal Android app that **forwards selected incoming SMS** (verification codes from banks, health funds,
government sites...) to one or more phone numbers, based on rules you define. Hebrew-first (RTL), English too.

> Based on [ronylicha/SMS-Forwarder](https://github.com/ronylicha/SMS-Forwarder), modified by **Yakir Mauda** (2026-11).
> Licensed under AGPL-3.0, like the original. See [Changes from the original](#changes-from-the-original).

---

## How it works

- **Event-driven:** Android wakes the app for every incoming SMS (`BroadcastReceiver` + `goAsync()`).
  No foreground service and no persistent notification.
- **Rules:** a rule fires when its **sender conditions AND its content conditions** both match.
  - Each group combines its conditions with **AND** or **OR**. An empty group matches everything.
  - Sender operators: equals, contains, starts with, ends with, regex. Phone numbers are normalized
    (`+972 50-123-4567` = `0501234567`), and alphanumeric IDs (`Leumi`, `כללית`) are compared as text.
  - Content operators: contains, does not contain, starts with, ends with, exact match, regex.
    Matching is case-insensitive, and an invalid regex never matches (the editor flags it).
- **Targets:** each rule has one or more targets. If several rules match, every target gets the message once.
  A loop guard never forwards an SMS back to its sender.
- **Verbatim:** the forwarded SMS contains the original text only, with no header.
- **Optional default number:** when switched on in Settings, SMS that match no rule go to it.
- **Reliable sending:**
  - Long messages are split into parts (Hebrew: 70 characters per part).
  - Sent/delivered results are recorded in History with the error code.
  - Failed sends are retried automatically by WorkManager (also after a reboot), or by hand from History.
- **Dual SIM:** send from the default SIM, a specific SIM, or the SIM the SMS arrived on.
- **Optional channels:** Telegram bot and HTTP webhook, per target. Off unless a rule uses them.

## Install (sideload with Obtainium)

1. Install [Obtainium](https://github.com/ImranR98/Obtainium) and add `https://github.com/YakirMauda/sms-forwarder-il`.
2. Install the app, then tap **Grant permissions** (receive + send SMS).
3. If Android blocks the SMS permission (Android 13+ "restricted setting" for sideloaded apps):
   App info → **⋮** (top-left in Hebrew, top-right in English) → **Allow restricted settings** → confirm with your PIN,
   then come back and grant again.
4. Allow the app to ignore battery optimization when asked, so codes arrive instantly.

Updates show up in Obtainium automatically for every new GitHub Release.

## Permissions

`RECEIVE_SMS`, `SEND_SMS`, `READ_PHONE_STATE` (dual SIM), `POST_NOTIFICATIONS`, `INTERNET` (optional channels),
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. `READ_SMS` is **not** requested: the app never reads your inbox.

## Development

- Kotlin, Jetpack Compose (Material 3), MVVM, Hilt, Room, WorkManager.
- The rule engine (`domain/rules/`) is pure Kotlin and unit-tested on the JVM (`./gradlew test`).
- CI (`.github/workflows/ci.yml`): lint, unit tests and a debug build on every pull request.
- Release (`.github/workflows/release.yml`): pushing a `v*` tag builds a signed APK
  (versionCode = 1000 + run number) and publishes it as a GitHub Release.

## Changes from the original

Modified by Yakir Mauda, November 2026:

- Removed the foreground service, the boot receiver, RCS ContentObserver capture, the notification listener
  and app-notification forwarding. Forwarding is now event-driven, and WorkManager handles retries.
- Removed the `READ_SMS` permission.
- New rule engine: sender group AND content group, AND/OR logic, 7 operators, Israeli number normalization,
  multiple targets per rule, a loop guard, and a Room migration (v3/v4).
- SMS is forwarded verbatim. Multipart sending, with sent/delivered status and error codes in History.
- Dual-SIM choice (including "same SIM"), plus an optional Telegram channel next to the webhook.
- New UI (Rules / History & Stats / Settings), Hebrew + RTL as the primary language, English secondary.
  French was removed.
- Onboarding for Android 13+ restricted settings, and a battery-optimization prompt.
- Own application id (`com.yakirmauda.smsforwarder`) and a signed release pipeline for Obtainium.

## License

[GNU AGPL v3](LICENSE). Original work © ronylicha and contributors; modifications © 2026 Yakir Mauda.
