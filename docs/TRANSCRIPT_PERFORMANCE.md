# Bound transcript rendering and preserve formatted Markdown height

- Transcript rendering retains at most 5,000 entries/raw lines and a bounded recent sequence history; a notice appears if earlier rendered history was trimmed. The server log is unchanged. Buffered live chunks during catch-up are capped, with HTTP recovery on overflow.

Run `./gradlew :app:testDebugUnitTest` with a complete JDK 17+ and Android SDK. Robolectric tests verify storage and Compose behavior, not physical-device frame rates.

Transcript parsing and snapshots run off Main behind a mutex. First Markdown display renders formatted spans synchronously to avoid a raw-text height swap; later edits keep previous spans while rendering off Main. This trades initial parse cost for stable initial height. The run timer only updates the header. Tests cover concurrent chunks, retention, first formatted display and pending edits.
