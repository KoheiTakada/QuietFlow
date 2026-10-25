[![Android debug APK](https://github.com/KoheiTakada/QuietFlow/actions/workflows/android.yml/badge.svg)](https://github.com/KoheiTakada/QuietFlow/actions/workflows/android.yml)

# QuietFlow — Android notification attention manager (MVP)

Android 10+ notification management proof-of-concept, optimized for Pixel / Android 17.

## Shipped implementation
- NotificationListenerService: listen to and record real notifications with app name, title, text, type and timestamp.
- Five classes: P0 action required, P1 ephemeral interrupt, P2 persistent status, P3 review later, P4 hide.
- **Observation mode is the default.** It never cancels original notifications.
- Local conservative safety policy protecting OTP/security/finance, alarm/call/navigation, ongoing/FGS/system notifications.
- Jev System One `choice` classification via official `POST https://api.typesafe.ai/v1/systemone` API with confidence and option probabilities. No fake/mock cloud API.
- Jev connection is opt-in and requires a user-supplied API key; short redaction (digits/phone/email) is applied, but is **not a complete privacy guarantee**.
- API key encrypted through Android Keystore AES-256-GCM; local history limited to 1,000 events, device backup disabled.
- Explicit permission controls, active-mode safety confirmation, recent decisions, one-tap reclassification and exact (app + title + body) learning from corrections.
- High-confidence active-mode actions: P1 replace with a 12-second own notification, P2 replace with low-importance own notification, P3/P4 dismiss original. Never auto-dismiss P0, safety-protected, unknown, low-confidence or API-failed notifications. If posting replacement is unavailable, original stays.
- Sample events button and Java policy test runner.

## Build

Requires Android Studio, Android SDK platform 36, JDK 17, and Gradle 8.13. The project intentionally uses Android platform UI APIs only (no Compose/third-party Android dependencies).

```sh
gradle :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Alternatively, push this project to GitHub: `.github/workflows/android.yml` builds a debug APK artifact on every push and runs the policy tests. The source archive has a Gradle wrapper **properties file only**; the wrapper JAR/script are not included. Use local Gradle or Android Studio.

Run the pure-Java safety tests without Android SDK:

```sh
mkdir -p build/policy-tests
javac -d build/policy-tests app/src/main/java/dev/quietflow/core/*.java tests/PolicyEngineTest.java
java -cp build/policy-tests PolicyEngineTest
```

## On-device smoke test
1. Install app, grant notification listener access (and POST_NOTIFICATIONS for own notifications).
2. Leave auto sorting OFF, receive ordinary app notifications and inspect recent classification.
3. Enter TypeSafe key and explicitly check permission to send selected, redacted notification contents to the cloud. Confirm history shows `jev:` and varying P0–P4.
4. Correct several notifications and verify an identical app + title + body subsequently adopts the correction.
5. Enable auto sorting only after observing the classifier. For P3/P4 use throwaway notifications first; ensure P0/OTP/ongoing notification remains.
6. Verify the original notification is not dismissed when permission is disabled or on API errors.

## Known limitations and security decisions
- Android notification listeners see notifications **after** the OS posts them. A notification may already have sounded before classification. This MVP does **not** promise pre-notification interception or DND-managed re-alerting.
- `cancelNotification` removes a **dismissable** OS notification, **not** a message's unread state in Gmail/LINE/WhatsApp; no `setNotificationsShown` call is made automatically.
- The original sender's notification actions/contentIntent are not transported when reposting P1/P2 (only title + text). Buttons and deep links may be lost. This is an MVP limitation.
- P2 reproduction is a snapshot, not a live status widget; later updates from the source are processed individually. Original status may persist when Android forbids removal.
- Input text can include sensitive material despite best-effort redaction. The safest setting is cloud opt-out and observation-only. No remote requests occur without both opt-in and API key.
- The system's notification badge may remain if another active notification exists or if an app displays its own in-app unread count. This is not a mark-as-read solution.
- Backend unavailability, low confidence or invalid credentials always fail open (keep source notification); no offline Jev or local LLM is bundled.
- UI shows event history to anyone who can unlock device; future version needs privacy lock and encryption at rest beyond device-wide FBE.
- The active-mode implementation must be tested on a real Android device before relying on it. Not production validated.

## Project status and license

Experimental MVP, **not yet validated on a physical Android device**. Build status is tracked by GitHub Actions. Licensed under MIT (see [LICENSE](LICENSE)); see [PRIVACY.md](PRIVACY.md) and [SECURITY.md](SECURITY.md).

## Official references
- Android NotificationListenerService: https://developer.android.com/reference/android/service/notification/NotificationListenerService
- TypeSafe Jev Choice: https://docs.typesafe.ai/primitives/choice
- TypeSafe System One API: https://docs.typesafe.ai/api/system-one
