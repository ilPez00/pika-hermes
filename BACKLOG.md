# PokeClaw Backlog

Items go in, get prioritized, get done, get crossed out. Simple.

Priority: `P0` = blocks users, fix now. `P1` = next up. `P2` = when we get to it. `P3` = nice to have.

---

## Bugs

- [ ] **P1** Historical upgrade gap: users on the older public debug signing path still need a one-time uninstall + reinstall because the original public signing key is already lost
- [ ] **P2** K3-a: Auto-return fires on every service connect, not just user-initiated permission enable
- [ ] **P2** B2-a: No auto-return to PokeClaw after task completes in another app (e.g., stuck in YouTube)
- [ ] **P1** Investigate MediaTek/Samsung local-engine bring-up failures that still report OpenCL/LiteRT engine creation errors on some devices even after GPU→CPU fallback
- [ ] **P2** Settings screen: active model row breaks layout when the model name is long; keep the label/value aligned and truncate or wrap cleanly without shoving the left label into a narrow column

## Features

- [ ] **P0** Missed-call auto follow-up: when a call is missed, let PokeClaw detect the missed-call event, send a follow-up message to that caller automatically, and keep the status visible in the same chatroom instead of as a hidden background-only action. Prefer SMS / true Android API paths first; do not make accessibility-driven WhatsApp automation the default design.
- [x] ~~**P0** Production external automation intent: promote the debug-only task/chat broadcast into a user-enabled production API for Tasker, MacroDroid, Locale, and ADB-style callers. It should accept explicit package/component broadcasts with `task` / `chat` / base64 extras, preserve harness safety rules, and optionally return a result callback intent.~~ — implemented 2026-04-30; callback contract exists, Tasker/MacroDroid callback E2E remains a QA gap
- [ ] **P1** Persistent global instructions: add a user-editable local instructions layer that applies to new tasks/conversations without becoming a prompt dump. It must be short, inspectable, removable, local-first, and separate from hard safety/tool rules.
- [ ] **P1** Scoped app/channel rules: support rules scoped to apps or channels such as WhatsApp, Telegram, Gmail, Browser, and Phone so the harness loads only relevant guidance instead of stuffing every rule into every local-model context.
- [ ] **P1** Explicit user-approved memory: add manual "remember this" style memory first, then optional suggested memories only after user approval. Memory must be deletable/exportable and must not store secrets, bot tokens, API keys, or recovery codes.
- [ ] **P1** Telegram remote-control channel hardening: treat the Telegram bot token path as a first-class remote-control channel with clear setup, token validation, polling status, and E2E QA gates. Current QA can configure a bot token, but live send-to-bot E2E is blocked on the QA Telegram account being frozen/read-only.
- [ ] **P2** Voice input: add a prompt microphone button as an input method, preferably using an available cloud transcription path when the user has a cloud API key and a local/on-device option later. Wake-word/background listening is a separate higher-risk permission/battery design, not the MVP.
- [ ] **P1** Local model import UX: keep shared-storage `.litertlm` import easy and explain clearly why other apps' `Android/data/...` sandboxes (for example Edge Gallery) are not directly readable
- [ ] **P1** More small local model options: add 1B / 1.5B-class local models so lower-RAM phones can still run a useful on-device agent
- [ ] **P1** Custom local model sources: let users point PokeClaw at user-defined model URLs / hosted downloads instead of only the built-in catalog
- [ ] **P2** Google AI Core integration research: evaluate Android's official on-device AI / system model APIs as an optional local runtime path
- [ ] **P1** Structured monitor identifiers: let monitor setup keep a user-facing nickname while using a more stable identifier where possible (phone number / app-stable id / aliases) so WhatsApp/Telegram display-name drift stops breaking setup
- [ ] **P2** Chat keyboard dismissal polish: tapping non-button chatroom space should reliably clear focus and hide IME in both empty and non-empty conversations
- [ ] **P1** Structure-first UI matching: remove remaining language-specific text heuristics where the platform exposes a stable structural hook first (dialog positive buttons, send affordances, standard action widgets)
- [ ] **P1** Tinder automation: auto swipe + monitor matches + auto-reply using same monitor architecture as WhatsApp
- [x] ~~**P1** NLP Playbooks (Layer 2): 5 playbooks in system prompt (Search in App, Navigate Settings, Compose Email, Read Screen, Read Notifications)~~ — done 2026-04-08
- [x] ~~**P1** In-chat task auto-return~~ — done 2026-04-08
- [x] ~~**P2** Monitor stays in app~~ — done 2026-04-08, removed GLOBAL_ACTION_HOME
- [ ] **P2** Unified task registry: monitor + agent tasks tracked in same system (top bar, floating button, etc.)
- [ ] **P3** Rename chat session (H6): pencil icon in sidebar → InputDialog → update title in DB + markdown
- [ ] **P3** Floating button: use PokeClaw icon instead of "AI" text
- [ ] **P3** ChatViewModel extraction: move business logic out of ComposeChatActivity god class
- [ ] **P1** Full i18n refactor for ChatScreen.kt — 75+ user-facing hardcoded English strings (quick task templates, intro text, monitor labels, send/cancel labels) need extracting to strings.xml + zh + ja translation. Surfaced by realme RMX3823 user (#50) wanting "thorough Chinese version". Voice input strings (4 strings) already i18n'd in v0.7.0 as a first slice.

## QA Gaps

- [ ] **P0** Missed-call follow-up E2E: missed-call notification / phone-state trigger reaches PokeClaw, follow-up message is sent to the caller, and the result/status is visible in the same chatroom
- [ ] **P0** Production intent E2E: Tasker/MacroDroid-style explicit broadcast reaches PokeClaw in a release build, starts the requested task/chat, and never bypasses safety/global rules
- [ ] **P1** Production intent callback E2E: when an external automation request includes `request_id` and `return_action`, PokeClaw broadcasts a completion/failure result that Tasker/MacroDroid can consume
- [ ] **P1** Telegram bot channel E2E: token configured -> polling connected -> user sends `/start` and a task to the bot -> PokeClaw receives the update -> returns a visible bot reply. Current QA is blocked by the handset Telegram account being frozen/read-only.
- [ ] **P1** C2: Auto-reply trigger E2E — needs 2nd device to send WhatsApp message to Girlfriend
- [ ] **P1** Release QA: verify locally signed `0.5.1+` public APK can upgrade in-place over the next signed public build once the stable key is installed in GitHub Actions
- [x] ~~**P1** M1-M12 QA: Cloud LLM complex tasks~~ — done 2026-04-08, 10/12 PASS
- [ ] **P2** K6: Verify each Settings permission row leads to correct system settings page
- [ ] **P2** Settings layout QA: verify long local/cloud model names render cleanly on the Settings screen across Pixel/Samsung widths
- [ ] **P2** Download free space check — done 2026-04-08 (StatFs before download)
- [ ] **P1** Local vague-task UX: in Local Task mode, prompt-only behavior is correct, but vague requests like `Copy that token to the clipboard` currently hang instead of failing fast with a clear request for the missing content/details

## Ideas / Research

- Monetization: two-tier (dev=free open source, consumer=China APK + premium features)
- YC application showcase
- Layer 2 NLP Playbooks as "App Cards" like DroidRun
- On-device LLM as competitive moat (first to ship with Gemma 4)
- Positioning: cloud/desktop-driven mobile-agent frameworks already exist; PokeClaw should own the phone-resident, local-first, model-slot harness that can run on a user's own Android device without a PC/cloud phone fleet.

---

## Done

_Move completed items here with date._

- [x] ~~2026-04-30: Production External Automation API~~ — user-enabled `RUN_TASK` / `RUN_CHAT` broadcasts, base64 extras, safety opt-in, and task terminal callback contract
- [x] ~~2026-04-08: Fix "Accessibility starting..." on every chat (A1-b)~~
- [x] ~~2026-04-08: Floating button IDLE→RUNNING in other apps (F3-b)~~
- [x] ~~2026-04-08: LiteRT-LM session conflict + GPU→CPU fallback (D1-a, D1-b)~~
- [x] ~~2026-04-08: Monitor permission check + auto-return after grant~~
- [x] ~~2026-04-08: Settings page: Notification Access row~~
- [x] ~~2026-04-08: Full QA pass 49/50 cases~~
- [x] ~~2026-04-08: Download free space check (StatFs before download)~~
- [x] ~~2026-04-08: Task detection keywords fix (check, compose, find, screen, notification, read my)~~
- [x] ~~2026-04-08: Compound task routing fix (skip Tier 1 for "and"/"then"/"after")~~
- [x] ~~2026-04-08: M1-M12 QA: 10/12 PASS, 2 PARTIAL (M9 camera, M12 system dialog)~~
- [x] ~~2026-04-08: NLP Playbooks Layer 2: 5 playbooks (Search, Settings, Email, Screen, Notifications)~~
- [x] ~~2026-04-08: Tinder research: UI structure documented, workflow designed, needs login~~
- [x] ~~2026-04-10: Chat bubble timestamps~~ — IG-style per-message footer landed for user + assistant bubbles, with hidden timestamp metadata persisted in markdown history so relaunch/reload keeps stable times
- [x] ~~2026-04-28: Release publishing stable signing path~~ — `v0.6.9` tag workflow produced a signed release APK and `SHA256SUMS.txt` through GitHub Actions
- [x] ~~2026-05-26: Voice Input (#44)~~ — system RecognizerIntent wired into chat composer between TextField and Send FAB. V1/V2/V5/V10 ADB-verified PASS on Pixel 8 Pro v0.7.0; V3/V4/V7/V8/V9 need human voice verify, code path structurally verified
