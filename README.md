# Paperclip for Android

A native Android client (Kotlin + Jetpack Compose) for a self-hosted Paperclip instance, built for running an AI agent team from your phone.

It talks to the same HTTP API and live-events WebSocket as the Paperclip web UI. It's built for the board operator's daily loop: answering agents' questions, approving things, watching runs and steering work.

## Features

- **Inbox**: the "needs me" attention feed (agent questions, confirmations, approvals, failed runs). Swipe to dismiss, or long-press to snooze. Also has Unread and Mine tabs.
- **Tasks**: search and filter issues, and create tasks and sub-tasks. The issue page has the full thread and editable status, priority, assignee and project. It also has documents, files, work products, activity and the live run banner.
- **Agent requests answered natively**: questions (single/multi/free text), confirmations (including plan and tool-action approvals), checkbox confirmations, task suggestions and item verdicts.
- **Live run transcripts** for Claude, Codex, ACP and Antigravity agents: assistant messages, collapsible tool calls, results and costs, streamed over the WebSocket.
- **Agents**: status, live activity, pause/resume, run now, reset session, runs, tasks, and viewing or editing instruction files.
- **Dashboard, approvals, costs and quotas, projects, goals, routines, activity and search.**
- **Notifications**: a background check (about every 15 minutes) for new items that need you.
- Light and dark themes, pull-to-refresh, and updates pushed live from the server.

## Signing in

On first launch you enter your server URL. The app then opens the web UI's own sign-in page in an embedded browser, and reuses that browser's cookies for all API and WebSocket traffic. Anything in front of Paperclip that works in a browser works here too, including Cloudflare Access, SSO and email codes.

You can also paste a **board API key** (`pcp_board_…`) on the setup screen to skip the browser. The live WebSocket only accepts cookie sessions, so in that mode the app polls for updates instead.

The server must accept the hostname you use (`paperclipai allowed-hostname <host>`).

## Building

Requirements: JDK 17+ and the Android SDK (platform 35, build-tools 35).

```bash
echo "sdk.dir=/path/to/android/sdk" > local.properties
./gradlew assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

Minimum Android version is 8.0 (API 26).

### Tests

`FixtureDecodeTest` decodes captured API responses with the app's models and runs captured run logs through the transcript parser. Fixtures contain private data, so they aren't committed. Point `PAPERCLIP_FIXTURES` at a directory of captured JSON to run it:

```bash
PAPERCLIP_FIXTURES=/path/to/fixtures ./gradlew testDebugUnitTest
```

## Notes

- This is an unofficial client and isn't affiliated with the Paperclip project.
- Debug builds accept `setupUrl` / `setupKey` intent extras to preconfigure a server (useful on an emulator).
