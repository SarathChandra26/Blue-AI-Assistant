# JARVIS — Permissions & Android Limitations Guide

## 1. Permission Overview

| Permission | API | Required for | Requested at |
|---|---|---|---|
| `RECORD_AUDIO` | All | Speech recognition | Runtime (first launch) |
| `READ_CONTACTS` | All | Call by name | Runtime |
| `CALL_PHONE` | All | Direct call initiation | Runtime |
| `READ_PHONE_STATE` | All | Call state detection | Runtime |
| `ANSWER_PHONE_CALLS` | API 26+ | Answer/decline calls | Runtime |
| `ACCESS_NOTIFICATION_POLICY` | API 23+ | Ringer / DND control | Separate settings page |
| `INTERNET` | All | Cloud speech fallback | Install-time (normal) |
| `VIBRATE` | All | Haptic confirmation | Install-time (normal) |
| `POST_NOTIFICATIONS` | API 33+ | Foreground service notification | Runtime |
| `FOREGROUND_SERVICE_MICROPHONE` | API 34+ | Future background listening | Install-time (normal) |

---

## 2. AccessibilityService

### What it enables
- Global navigation: back, home, recent apps
- Gesture-based scrolling and swiping
- Text insertion into focused fields
- Screen lock (`GLOBAL_ACTION_LOCK_SCREEN`)

### What it does NOT do
- Transmit screen content to any server
- Collect or store user data
- Operate when Jarvis is not actively executing a command

### How to grant it (required manual setup)
1. Open **Settings → Accessibility**
2. Scroll to **Installed Services** or **Downloaded Apps**
3. Find **Jarvis Voice Control**
4. Tap the toggle to **ON**
5. Read and confirm the Android warning

### Why Android requires this
Android's AccessibilityService API requires explicit user consent because it can observe and interact with other apps' UI. This is correct security behaviour. Jarvis cannot bypass this — it is by design.

---

## 3. Do Not Disturb (DND) Access

Required for: `silence ringer`, `do not disturb`, `disable dnd`

### How to grant
1. **Settings → Sound & Vibration → Do Not Disturb**
2. Tap **Allow apps to override DND** or **App access**
3. Find Jarvis and enable access

---

## 4. Android API Restrictions

### Screen wakeup
Standard Android applications **cannot turn on the display** programmatically from the background. `PowerManager.FULL_WAKE_LOCK` was deprecated in API 17. Jarvis does not claim to wake the screen.

### Power button
Ordinary apps **cannot intercept the physical Power button**. The `ACTION_CLOSE_SYSTEM_DIALOGS` broadcast was restricted in API 31. Jarvis does not attempt this.

### Phone shutdown/restart
Requires the `REBOOT` permission which is reserved for system apps signed with the platform key. Jarvis cannot shut down the phone.

### Answer/Decline calls
- `TelecomManager.acceptRingingCall()` requires `ANSWER_PHONE_CALLS` permission.
- On stock Android 14 (iQOO 12), this permission can be granted by the user via Settings → Apps → Jarvis → Permissions → Phone → Allow.
- On some OEM builds, the system dialer may intercept call events before Jarvis.
- If the permission is not granted, Jarvis opens the dialer for the user to confirm.

### Background microphone recording
Android 14 restricts foreground services with type `microphone` to apps that are active in the foreground. Jarvis complies — it does not attempt continuous background listening in the MVP.

---

## 5. iQOO 12 Specific Notes

The iQOO 12 runs **Funtouch OS 14** based on Android 14.

| Feature | Notes |
|---|---|
| AccessibilityService | Available — enable in Settings → Accessibility |
| Gesture navigation | iQOO uses Android gesture nav — `swipe left/right` from edges may conflict. Jarvis uses center-screen gestures to avoid the system nav zones. |
| Screen lock | `GLOBAL_ACTION_LOCK_SCREEN` should work on Funtouch OS |
| DND | Supported via standard NotificationManager API |
| On-device speech | Available with Google Play Services pre-installed |
| Power button long-press | Funtouch OS shows a power menu — not interceptable by Jarvis |
| Custom buttons | iQOO may have a Side Key that can be remapped. Check: Settings → Special features → Side key |

---

## 6. Google Play Compliance (for future public release)

If you intend to publish on Google Play:

- **AccessibilityService**: Google Play policy requires that accessibility features are used exclusively for their declared purpose (assisting users with disabilities or voice control). Jarvis's accessibility usage is legitimate — it performs user-requested actions via voice.
- **Declare usage clearly** in the Play Store listing.
- **Do not use** accessibility to collect user data or perform actions in other apps beyond what the user explicitly commanded.
- **Sensitive permissions** (CALL_PHONE, READ_CONTACTS): Require a brief explanatory note in the Play Console.

---

## 7. Security Model

- All command parsing happens **on-device** (local rule engine).
- No data is sent to Jarvis's servers — there are no servers.
- Speech recognition: Google's on-device model processes audio locally when downloaded. Cloud fallback sends audio to Google Speech API — same as Google Assistant.
- Contacts: accessed read-only, only when the user issues a call command.
- Jarvis does **not** log voice commands persistently.
