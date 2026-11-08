# JARVIS — Permissions, Digital Assistant & Setup Guide

---

## 1. Full Permissions Reference

All of these permissions have been granted on your **iQOO 12** via ADB:

| Permission | Technical Name | Why Jarvis Needs It |
|---|---|---|
| **Microphone** | `android.permission.RECORD_AUDIO` | Converts speech into text commands on-device. |
| **System Overlay Window** | `android.permission.SYSTEM_ALERT_WINDOW` | Draws the transparent holographic Jarvis HUD and floating Assistive Orb over other apps. |
| **Accessibility Service** | `JarvisAccessibilityService` | Performs system gestures (swipe, scroll, back, home, lock screen) and smart text injection into input fields. |
| **Phone Calls** | `android.permission.CALL_PHONE` | Dials contacts directly by voice command without opening the dialer keypad. |
| **Call Answering** | `android.permission.ANSWER_PHONE_CALLS` | Accepts or declines incoming ringing phone calls programmatically. |
| **Audio Settings** | `android.permission.MODIFY_AUDIO_SETTINGS` | Automatically turns on speakerphone for "call on speaker" commands. |
| **Contacts** | `android.permission.READ_CONTACTS` | Searches contacts by name using multi-tiered fuzzy search (matching nicknames and stripping emojis like *"Teja 🫶🏻"*). |
| **Phone State** | `android.permission.READ_PHONE_STATE` | Detects incoming ringing calls to announce caller names and trigger voice prompts. |
| **Send & Read SMS** | `android.permission.SEND_SMS`, `READ_SMS` | Composes and routes voice-dictated SMS messages. |
| **Package Visibility** | `android.permission.QUERY_ALL_PACKAGES` | Discovers all 135+ installed applications on your phone for fuzzy voice launching. |
| **Do Not Disturb** | `android.permission.ACCESS_NOTIFICATION_POLICY` | Mutes phone ringer and controls system DND states. |
| **Alarms & Timers** | `com.android.alarm.permission.SET_ALARM` | Sets native Android alarms and timers. |
| **Background Service** | `android.permission.FOREGROUND_SERVICE_MICROPHONE` | Runs the continuous wake-word listening loop for *"Hey Jarvis"*. |

---

## 2. Power Button Long-Press Setup (Funtouch OS 14 / iQOO 12)

Jarvis is configured as a native **Device Digital Assistant app** (`android.intent.action.ASSIST`).
We have set Jarvis as the secure default assistant on your phone:
```bash
settings put secure assistant com.example.jarvis/com.example.jarvis.assist.JarvisAssistActivity
```

### To verify or adjust on your phone:
1. Open **Settings → Shortcuts & accessibility → Press and hold power button**.
2. Select **Launch Google Assistant** (or **Digital Assistant**). Because Jarvis is registered as the default assistant, holding the power button will now launch **JARVIS**!
3. Alternatively, check: **Settings → Apps → Default apps → Digital assistant app → Device assistant app** &rarr; Select **Jarvis**.

---

## 3. How the Holographic Transparent HUD Works

When triggered (by saying *"Hey Jarvis"*, holding the Power button, or tapping the floating orb):
1. **Translucent Dark Vignette**: The entire screen dims slightly with a dark transparent gradient, keeping whatever video, game, or app you were using visible underneath.
2. **Arc Reactor Core**: 3 concentric glowing rings with rotating dashed segments, degree ticks, and an inner cyan/teal neon glow.
3. **Soundwave Equalizer**: 9 animated frequency bars bouncing dynamically in real time to your voice volume.
4. **Active Speech Transcription**: Displays what you are saying in crisp monospace typography.
5. **Auto-Dismiss**: Once the command executes and Jarvis confirms with his deep British voice, the HUD automatically fades out and returns you to your active app.
