# Project context for Claude (handoff from the planning chat, 2026-11-26)

## Who I'm working with
- Owner: Yakir Mauda (GitHub: YakirMauda). Fresh CS graduate, beginner in real-world tooling.
- **Always reply in Hebrew** (code, file names and technical terms stay in English).
- **Always explain simply** what each tool/step does and why (Git, branches, PRs, Gradle, Android APIs...). Assume no prior knowledge.
- He wants to do as little manually as possible: do the work end-to-end (branch, commit, push, PR, merge, tag) and only stop for things that truly need him.
- He wants credit: commit **author** = him, **committer** = Claude (an environment hook requires committer
  noreply@anthropic.com so GitHub shows commits as Verified; GitHub credits contributions to the author):
  `git config author.name "Yakir Mauda"`, `git config author.email "167648116+YakirMauda@users.noreply.github.com"`
  (GitHub noreply, keeps his real email private), `git config user.name Claude`, `git config user.email noreply@anthropic.com`.
  Merge PRs with **squash** so the commit on main is his.

## Goal
Personal Android app that auto-forwards selected incoming SMS (OTP/verification codes for family bureaucracy:
banks, HMOs, government) to his phone number. Sideloaded, built only in the cloud (no Android Studio locally).

## Base & licensing
- Imported (not forked, so contributions count) from `ronylicha/SMS-Forwarder` — Kotlin, Jetpack Compose,
  Material 3, MVVM + Clean Architecture, Hilt, Room. minSdk 26, targetSdk 35, compileSdk 36.
- License AGPL-3.0: keep LICENSE and original copyright; README must state "Based on ronylicha/SMS-Forwarder,
  modified by Yakir Mauda" with a summary of changes and date. Repo stays public.

## Decisions already made
1. **No foreground service, no persistent notification.** Remove `SmsForwardService` (foreground), boot-restart,
   RCS ContentObserver. Event-driven only: `BroadcastReceiver` (SMS_RECEIVED) -> `goAsync()` -> send.
   Retries via WorkManager. NotificationListener can be dropped (not needed for SMS).
2. **Rule engine:** rule fires iff `(SenderGroup) AND (ContentGroup)`.
   - Each group: operator AND/OR + list of conditions. Empty group = matches everything.
   - Sender ops: equals, contains, starts_with, ends_with, regex. Must handle phone numbers and alphanumeric IDs
     in English and Hebrew. Normalize phones (+972 / 972 / leading 0, strip spaces/dashes) when both sides are numeric.
   - Content ops: contains, not_contains, starts_with, ends_with, exact, regex. Case-insensitive; invalid regex = no match (shown in UI).
   - Loop guard: never forward if sender == one of the targets.
   - **Multiple targets per rule** (him by default; optionally family members). Dedupe targets across matched rules.
3. **Channels:** SMS (main) + keep Telegram/webhook as optional channels, off by default. Per-rule channel choice.
4. **Dual SIM:** optional setting — default SIM / specific SIM / same SIM the message arrived on. Hidden on single-SIM phones.
5. **SOLID architecture** (owner explicitly asked):
   - `ConditionMatcher` interface, one class per operator, registered in a map (Open/Closed).
   - `RuleEvaluator` (pure Kotlin, no Android deps) -> unit-testable on JVM.
   - `MessageSender` interface: `SmsMessageSender`, `TelegramSender`, `WebhookSender`.
   - `SimSelector` interface separate from sending (Interface Segregation).
   - `ForwardingHistoryRepository` for logging; use cases depend on interfaces (Dependency Inversion, Hilt).
6. **Forwarded text = original SMS body verbatim** (owner's request 2026-11-26): no sender/date header.
   **Sending:** `SmsManager.divideMessage` + `sendMultipartTextMessage` (Hebrew = UCS-2, 70 chars/part);
   sent/delivery PendingIntents update the Room log (status + error code). Retry failed from History.
7. **History & Stats tab:** timestamp, sender, snippet, matched rule, target, status, error; stats today/month,
   success rate, failed count; retry button.
8. **Permissions:** RECEIVE_SMS + SEND_SMS (+ READ_PHONE_STATE for dual-SIM). Recommended NOT to request READ_SMS
   (not needed); confirm with owner before adding.
9. **Onboarding / restricted settings (Android 13-15+):**
   - Detect "denied and can't ask again" (request returns denied immediately / shouldShowRequestPermissionRationale false)
     -> show HE/EN explanation: App info -> ⋮ menu -> "Allow restricted settings" (התרת הגדרות מוגבלות) -> confirm with PIN.
     Note: in RTL the ⋮ is on the top-LEFT; text must follow the locale.
   - Big button: `Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))`.
   - Re-check permission state on resume (LifecycleResumeEffect / ON_RESUME) and enable "Grant" immediately.
   - Battery: on launch check `PowerManager.isIgnoringBatteryOptimizations`; banner + dialog -> `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
     (manifest permission REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).
10. **Localization:** Hebrew primary + RTL (`values-iw`, `android:supportsRtl="true"`), English secondary.
    Existing FR strings can be removed. Per-app language via `AppCompatDelegate.setApplicationLocales` + locales_config.
11. UI reference prototype (interactive): https://claude.ai/artifact/S7zrqfj3vgGfaSMw3KandP

## Build / release pipeline (done in PR #1, branch `ci/release-pipeline`)
- `applicationId = com.yakirmauda.smsforwarder` (namespace/package still com.qrcommunication.smsforwarder — fine; may rename later).
- `.github/workflows/release.yml`: on tag `v*` -> signed `assembleRelease`, versionCode = 1000 + run_number,
  versionName from tag, verifies signature, publishes GitHub Release asset `sms-forwarder-il-vX.Y.Z.apk`.
- Secrets already set by owner: KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS (release), KEY_PASSWORD.
  Keystore cert SHA-256: 64:06:90:D8:FB:70:22:6C:D1:46:BF:6F:32:D1:86:13:5C:02:61:4D:30:E4:16:AC:32:E2:66:B6:BE:C5:16:2B
- `ci.yml` (lint, unit tests, debug build) passes; failing tests now surface as PR annotations.
- Phone side: Obtainium tracking https://github.com/YakirMauda/sms-forwarder-il releases.

## Roadmap / status
- [x] Phase 0: import repo, keystore, secrets
- [x] Phase 1: CI/CD — PR #1 merged; v1.5.0 released (tag created by owner in web UI: the sandbox can only push
  branch `ci/release-pipeline`, tag push = 403). APK `sms-forwarder-il-v1.5.0.apk` attached, v2 cert SHA-256 verified.
  Note: v1.5.0 is pre-Phase-2 code (still has the foreground service).
- [x] Phase 2: strip foreground service / boot / RCS; receiver + goAsync + WorkManager retry (PR #2 merged, not released yet).
  `SmsReceiver` -> `ForwardingProcessor` (send timeout 7s) -> on failure `RetryScheduler` -> `ForwardRetryWorker`
  (HiltWorker, delays from user's `RetryPolicy`). READ_SMS, NotificationListener, AppWhitelist screen removed.
  Leftovers for later phases: unused strings of removed screens; `LoopProtection` still normalizes +33 (Phase 3);
  `SmsRetryManager.MAX_RETRIES` also caps manual retry from History after auto-retries (revisit in Phase 4/5).
- [x] Phase 3: new rule engine (PR #4 merged). Pure Kotlin in `domain/rules/` (RuleModels, PhoneNumbers, ConditionMatchers +
  MatcherRegistry, RuleEvaluator, RuleJson). Room v3 JSON columns (null = legacy row, see `data/mapper/ForwardingRuleMapper`).
  Pure domain tests can run locally: kotlinc 2.0.21 + junit/hamcrest/json jars, no Android SDK needed.
  **Owner decision:** app is rule-driven. "Default number" is an optional catch-all (pref `isDefaultNumberEnabled`, off
  by default): unmatched SMS go there only when switched on.
- [~] Phase 4: multipart sending, status tracking, dual-SIM, channels (PR #5). `MessageSender` per channel (Hilt @IntoSet),
  `SmsStatusReceiver` -> `SmsStatusHandler` (FAILED + code -> retry; delivered_at), `SimSelector` (-1 default, -2 same SIM,
  0/1 slot), optional `TelegramSender` (bot token pref). Room v4.
  Workflow note: only `ci/release-pipeline` can be pushed, so phases go one PR at a time; later work waits locally.
- [ ] Phase 5: Compose UI (Rules, History & Stats, Settings), onboarding, Hebrew/RTL
- [ ] Phase 6: install via Obtainium on phone, restricted settings, battery
- [ ] Phase 7: README credits, regular release cycle

## Notes
- Android SDK can't be downloaded in some sandboxes: rely on GitHub Actions for builds; keep domain logic pure Kotlin
  so `./gradlew test` covers it.
- Never commit *.jks / secrets (already in .gitignore).
