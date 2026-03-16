# TwinMind Recorder — Take-Home Assignment

A production-quality voice recording app with real-time transcription and AI summary generation, built in Kotlin with Jetpack Compose.

---

## Tech Stack

| Layer | Technology |
|---|---|
| UI | Jetpack Compose (100%) |
| Architecture | MVVM · Clean Architecture |
| DI | Hilt |
| Database | Room |
| Networking | Retrofit + OkHttp |
| Async | Coroutines + StateFlow |
| Background | WorkManager (HiltWorker) |
| Transcription | OpenAI Whisper *(or mock fallback)* |
| Summarization | Google Gemini 2.5 Flash streaming *(or mock fallback)* |
| Min SDK | API 24 (Android 7.0) |
| Target SDK | API 36 (Android 16) |

---

## Setup

### 1. Clone & open in Android Studio Ladybug+

```bash
git clone <repo-url>
cd TwinMindRecorder
```

### 2. Add API Keys (optional — mock fallback works without them)

In your **local.properties** (never commit this file):

```properties
OPENAI_API_KEY=sk-...
GEMINI_API_KEY=AIza...
```

- If both are blank, the app uses **mock transcription and summary** so you can demo the full flow instantly.

### 3. Build & run

```bash
./gradlew assembleDebug
# or just press Run in Android Studio
```

---

## Architecture Overview

```
MainActivity
└── AppNavigation (NavHost)
    ├── DashboardScreen  ←→  DashboardViewModel  ←→  MeetingRepository  ←→  Room
    ├── RecordingScreen  ←→  RecordingViewModel  ←→  RecordingService (ForegroundService)
    │                                                       │
    │                                                       ├── AudioRecord (PCM 16kHz)
    │                                                       ├── 30s chunk splitter + 2s overlap
    │                                                       ├── WavUtils (WAV header writer)
    │                                                       ├── TranscriptionWorker (enqueued per chunk)
    │                                                       ├── Phone state (TelephonyCallback / PhoneStateListener)
    │                                                       ├── Audio focus listener
    │                                                       └── Headset BroadcastReceiver
    │
    └── SummaryScreen    ←→  SummaryViewModel    ←→  TranscriptionRepository  ←→  Whisper API
                                                  └── MeetingRepository       ←→  Gemini API (streaming)
```

---

## Features Implemented

### 1. Recording Service
- [x] Foreground service with persistent notification (Stop action)
- [x] 30-second PCM chunks with **2-second overlap** to preserve speech continuity
- [x] WAV header written per chunk (Whisper-compatible 16kHz mono)
- [x] Chunks saved to private internal storage, keyed by meeting ID

### 2. Edge Cases
| Edge Case | Handling |
|---|---|
| **Incoming/outgoing call** | `TelephonyCallback` (API 31+) / `PhoneStateListener` (legacy) → pause + "Paused - Phone call" notification |
| **Audio focus loss** | `AudioManager.OnAudioFocusChangeListener` → pause + "Paused - Audio focus lost" notification with Resume/Stop actions |
| **Bluetooth headset** | `BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED` BroadcastReceiver → continue recording, show notification |
| **Wired headset** | `ACTION_HEADSET_PLUG` BroadcastReceiver → continue recording, show notification |
| **Low storage** | `StatFs` check before start and every 5s of audio → graceful stop + "Recording stopped - Low storage" |
| **Process death recovery** | Session persisted in Room; `WorkManager` workers survive process death; `START_STICKY` service restart |
| **Silent audio** | RMS calculation per PCM frame → warning after 10s silence: "No audio detected - Check microphone" |

### 3. Transcription
- [x] Chunks uploaded to **OpenAI Whisper** as they are finalized (live upload)
- [x] `TranscriptionWorker` (WorkManager) — survives app kill, retry on failure
- [x] **Retry all chunks** if any failure detected (reset to PENDING, re-enqueue)
- [x] Transcript stored in Room as single source of truth, ordered by `sequenceNumber`
- [x] Mock fallback if no API key configured

### 4. Summary Generation
- [x] Full transcript assembled from ordered chunks
- [x] Sent to **Gemini 2.5 Flash** with structured prompt
- [x] **Streamed response** — UI updates as tokens arrive
- [x] 4 sections: Title · Summary · Action Items · Key Points
- [x] `SummaryWorker` (WorkManager) — summary continues even if app is killed
- [x] Retry button on error; specific error messages shown

### 5. UI
- [x] Dashboard with meeting list, status badges, duration display
- [x] Recording screen: animated pulse visualizer, live timer, status chip, info cards
- [x] Summary screen: collapsible sections, streaming indicator, transcript viewer
- [x] Dark theme (navy/blue TwinMind palette) + light theme support
- [x] 100% Jetpack Compose, no XML layouts

---

## Project Structure

```
app/src/main/java/com/twinmind/recorder/
├── TwinMindApp.kt              # Application class (Hilt + WorkManager init)
├── MainActivity.kt             # Single activity, permissions
├── di/
│   └── Modules.kt              # Hilt modules (DB, Network)
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt
│   │   ├── dao/                # MeetingDao, AudioChunkDao, TranscriptDao
│   │   └── entity/             # MeetingEntity, AudioChunkEntity, TranscriptEntity
│   ├── remote/
│   │   └── ApiServices.kt      # WhisperApiService, GeminiApiService
│   └── repository/
│       ├── MeetingRepository.kt
│       └── TranscriptionRepository.kt
├── service/
│   └── RecordingService.kt     # Foreground service — all recording logic
├── worker/
│   ├── TranscriptionWorker.kt  # Background transcription
│   └── SummaryWorker.kt        # Background summary (survives app kill)
├── ui/
│   ├── theme/                  # Theme.kt, Typography.kt
│   ├── navigation/             # AppNavigation.kt
│   ├── dashboard/              # DashboardScreen.kt, DashboardViewModel.kt
│   ├── recording/              # RecordingScreen.kt, RecordingViewModel.kt
│   └── summary/                # SummaryScreen.kt, SummaryViewModel.kt
└── util/
    ├── WavUtils.kt             # PCM → WAV header writer
    ├── StorageUtils.kt         # Free-space check, chunk directory management
    ├── FormatUtils.kt          # Duration/date formatting
    └── Receivers.kt            # PhoneStateReceiver, HeadsetReceiver
```

---

## API Key Notes

- The app checks `BuildConfig.OPENAI_API_KEY` and `BuildConfig.GEMINI_API_KEY` at runtime.
- If either is blank, the relevant feature falls back to a **mock implementation** that simulates realistic delays and outputs.
- You can demo the **complete end-to-end flow** without any API keys.

---

## Android 16 (API 36) Live Updates

The `RecordingService` foreground notification updates every second with:
- Live recording timer
- Current status (Recording / Paused - Phone call / etc.)
- Pause/Stop actions

On Android 16 devices, this surfaces on the lock screen via the persistent foreground service notification, satisfying the Live Updates requirement.
