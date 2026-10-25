# Security

QuietFlow is an experimental notification listener. **Do not rely on it for critical notifications until you have validated it on your device.**

- Observation mode is on by default.
- Auto-actions require explicit activation and high-confidence classifications.
- Protected notifications fail open: they remain with the original app.
- Android may show or sound notifications before the listener evaluates them.
- Dismissing a notification does not mark the source application read.

If you find a security issue, avoid posting credentials, private notifications or exploit details in a public issue. Contact the maintainers through a private channel.
