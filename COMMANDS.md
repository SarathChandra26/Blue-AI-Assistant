# JARVIS — Supported Commands Reference

All commands are matched case-insensitively after normalisation (punctuation removed, whitespace collapsed).

---

## App Launching

| Say | Action |
|---|---|
| `open <app>` | Launch any installed app |
| `launch <app>` | (same) |
| `start <app>` | (same) |
| `run <app>` | (same) |
| `go to <app>` | (same) |

**Well-known app aliases:**

| Phrase | Resolves to |
|---|---|
| Chrome, browser, Google Chrome | Chrome |
| YouTube, YT | YouTube |
| WhatsApp, Whats App | WhatsApp |
| ChatGPT, Chat GPT, OpenAI | ChatGPT |
| Settings, phone settings | Settings |
| Maps, Google Maps | Google Maps |
| Gmail, email | Gmail |
| Instagram, Telegram, Spotify | Respective apps |
| Contacts, Clock, Calculator, Calendar | System apps |
| Play Store, Google Play | Play Store |
| Files, file manager | Files |

If an app is not installed, Jarvis opens Google Play to search for it.

---

## Browser & Search

| Say | Action |
|---|---|
| `open Chrome` | Launches Chrome |
| `search for <query>` | Google search for query |
| `google <query>` | (same) |
| `look up <query>` | (same) |
| `find <query>` | (same) |
| `open url <url>` | Opens the URL directly |
| `visit <url>` | (same) |
| `open website <url>` | (same) |

---

## Navigation (requires Accessibility Service)

| Say | Action |
|---|---|
| `go back` / `back` / `navigate back` | Global back action |
| `go home` / `home` | Go to home screen |
| `recent apps` / `recents` | Open app switcher |

---

## Gestures (requires Accessibility Service)

| Say | Action |
|---|---|
| `scroll up` / `move up` / `go up` | Scroll active content up |
| `scroll down` / `move down` / `go down` | Scroll active content down |
| `swipe left` / `slide left` | Swipe gesture left |
| `swipe right` / `slide right` | Swipe gesture right |

---

## Volume & Audio

| Say | Action |
|---|---|
| `volume up` / `increase volume` / `louder` / `turn up` | Media volume +1 step |
| `volume down` / `decrease volume` / `quieter` / `turn down` | Media volume -1 step |
| `set volume to <0–15>` | Exact media volume level |
| `set volume to max` / `full` | Maximum volume |
| `mute` / `mute media` | Mute media stream |
| `unmute` / `unmute media` | Unmute media stream |
| `silence` / `silence ringer` / `silent mode` | Set ringer to silent |
| `do not disturb` / `dnd` / `enable dnd` | Enable Do Not Disturb |
| `disable dnd` / `turn off dnd` | Disable Do Not Disturb |

> **Note**: Ringer mode changes require Do Not Disturb access. Grant it in Settings → Sound → Do Not Disturb → Allow apps.

---

## Flashlight

| Say | Action |
|---|---|
| `flashlight on` / `turn on flashlight` / `torch on` | Enable torch |
| `flashlight off` / `turn off flashlight` / `torch off` | Disable torch |
| `toggle flashlight` / `toggle torch` | Toggle torch |

> Fails gracefully if camera is in use by another app.

---

## Screen Lock (requires Accessibility Service)

| Say | Action |
|---|---|
| `lock screen` / `lock phone` / `lock device` / `lock` | Lock the screen immediately |

> Uses the Accessibility `GLOBAL_ACTION_LOCK_SCREEN`. No Device Admin required.

---

## Phone Calls

| Say | Action |
|---|---|
| `call <contact name>` | Initiate call (requires CALL_PHONE permission) |
| `phone <name>` / `dial <name>` / `ring <name>` | (same) |
| `answer` / `answer call` / `pick up` | Answer incoming call |
| `decline` / `decline call` / `reject call` / `hang up` | Decline/end call |

> Direct call requires `CALL_PHONE` permission. Without it, Jarvis opens the dialer pre-filled.
> Answering/declining programmatically requires `ANSWER_PHONE_CALLS` (granted in Settings → Apps → Jarvis → Permissions).

---

## Text Entry (requires Accessibility Service)

| Say | Action |
|---|---|
| `type <text>` | Insert text into the currently focused field |
| `write <text>` | (same) |
| `enter <text>` | (same) |
| `dictate <text>` | (same) |

> The target app's text field **must be focused** (tapped) first. Jarvis inserts text using the Accessibility `ACTION_SET_TEXT`. Not all apps expose this action.

**Example (ChatGPT):**
1. Say `"Open ChatGPT"` — Jarvis launches ChatGPT.
2. Tap the message input field in ChatGPT.
3. Say `"Type: Explain operating systems simply"` — Jarvis inserts the text.

---

## WhatsApp

| Say | Action |
|---|---|
| `open WhatsApp` / `whatsapp` | Opens WhatsApp |
| `whatsapp <name> saying <message>` | Opens WhatsApp share intent with message pre-filled |
| `message <name> the message <text>` | (same) |

> Direct WhatsApp sending without user confirmation is not reliably possible through accessibility automation. Jarvis opens the share intent with the message pre-filled; the user selects the contact and taps Send.

---

## Settings & Help

| Say | Action |
|---|---|
| `settings` / `open settings` | Opens Android Settings |
| `jarvis settings` / `open jarvis settings` | Opens the Jarvis settings panel |
| `help` / `what can you do` / `commands` | Opens the command reference screen |

---

## Blue v5 Intelligence, Vision & Polyglot Features

### 1. Universal Relationship Memory
Teach Blue any family, social, or custom relationship using natural language:
- `Harsha JR is my Bamardhi` (or `teja is my brother`)
- Telugu: `హర్ష జూనియర్ నా బావమరిది` / `harsha jr na bamardhi`
- Hindi: `harsha jr mera bamardhi hai` / `teja mera bhai hai`

**Calling by Relationship:**
- `call bamardhi` -> Automatically dials `Harsha JR`
- Telugu: `బావమరిదికి కాల్ చేయి` / `bamardhi ki call cheyi`
- Hindi: `bamardhi ko call karo`

**Erasing / Forgetting Relationships:**
- Voice: `forget my bamardhi` / `erase bamardhi` / `bamardhi ni marchipo` / `bamardhi ko bhool jao`
- UI: Tap **Settings → RELATIONSHIP MEMORY (v5) → Erase** button on any entry.

---

### 2. Polyglot Live Translation (Lambadi / Telugu / Hindi / English)
- `translate this lambadi to telugu or english: tharo naam kai che`
- `translate this lambadi to telugu`
- `translate hello to lambadi`
- `translate this to hindi`
- Telugu: `దీన్ని తెలుగులోకి అనువదించు` / `లంబాడీ నుండి తెలుగులోకి అనువదించు`
- Hindi: `isko hindi me translate karo`

---

### 3. "Look at My Screen" (Groq Vision & Content AI)
- `look at my screen` / `explain this screen` / `what is on my screen`
- Telugu: `screen choodu` / `స్క్రీన్ లో ఏముంది`
- Hindi: `meri screen dekho` / `screen par kya hai`

Pulls raw on-screen text instantly (< 50ms) using Accessibility node inspection with zero screenshots, summarized in natural spoken sentences via Groq LPU.

---

### 4. Continuous Hands-Free Voice Chat Mode (ChatGPT Voice Style)
- `start chat mode` / `enable chat mode` / `chat mode on`
- `stop chat mode` / `disable chat mode` / `chat mode off`
- Settings UI: Toggle **Continuous Chat Loop** on/off.
Blue automatically re-activates the microphone after each spoken response for continuous conversational flow.

---

### 5. Custom Workflows & Multi-Action Routines
- `run workflow gaming` (sets volume 12, turns off torch)
- `run workflow night routine` (DND on, mute media, lock screen)
- `create workflow [name] steps [action 1] and then [action 2]...`
- Settings UI: View, manage, and delete workflows under **CUSTOM WORKFLOWS & ROUTINES**.

---

### 6. Hindi & Hinglish Commands (हिंदी)
- **Torch:** `torch on karo` / `torch jalao` / `torch off karo` / `torch band karo`
- **Battery:** `battery kitni hai` / `battery check karo`
- **Time/Date:** `samay kya hai` / `time kya hai` / `aaj ki date kya hai`
- **Volume:** `volume badhao` / `volume kam karo` / `mute karo`
- **Reels:** `agli reel` / `pichli reel` / `like karo`
- **Calls:** `call uthao` / `call kaato` / `[name] ko call karo`
- **WhatsApp:** `whatsapp kholo` / `[name] ko whatsapp karo [msg]`
- **Conversation:** `namaste` / `kaise ho` / `aap kaun ho` / `dhanyavaad`

---

### 7. Lambadi / Gorboli / Banjara Commands (లంబాడీ / गोरबोली)
- **Greetings:** `Ram Ram` / `Ram Ram Blue`
- **Identity:** `tharo naam kai che` (replies: *"Maro naam Blue che..."*)
- **Status:** `kasa cha` / `kaso che` (replies: *"Main sokhi chhu..."*)
- **Activity:** `kayi kare che` / `kai kare che` (replies: *"Main thaar saaru taiyyar chhu..."*)
- **Torch:** `light laga` / `batti jala` (on), `light bujha` (off)
- **Call:** `[name] ne phone laga` / `[name] ne call kar`
- **Gratitude:** `dhanvaad` / `dhanyavaad`
