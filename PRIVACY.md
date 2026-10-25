# Privacy (MVP)

QuietFlow requires Android Notification Access, which may expose message bodies, senders, and other sensitive contents.

- By default, notifications are classified locally and no notification is removed.
- Notification contents and corrections are stored in the app-private SQLite database, up to 1,000 events. They can be erased in the app. The database is not individually encrypted; Android device protection applies.
- No cloud request is made unless the user supplies a TypeSafe AI API key **and** enables explicit cloud consent.
- With consent, the app sends the app package, notification category, truncated title and body to the TypeSafe AI System One endpoint. It masks some common patterns (digit codes, emails and phone numbers), but redaction is incomplete. Do not enable cloud mode for messages whose contents you do not wish to share.
- System, protected, sensitive and ongoing notifications are excluded from cloud requests by a conservative local filter. This filter is not a perfect detector.
- API keys are stored through Android Keystore encryption. They are not bundled in the repository.
- The application has no telemetry or analytics service. Network requests to TypeSafe depend on the user's opt-in settings.

This is an early prototype, not audited for security or privacy.
