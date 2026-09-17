# Assistant Twin — v0.4: full-auto agent, one-tap consent

## What changed from v0.3
- **"Grant everything" is now one button**, not eight. `MainActivity` chains
  through every required dialog/settings screen automatically (SMS → voice →
  contacts/calendar → storage → notifications → exact alarms → all-files →
  usage access → accessibility), advancing to the next one as soon as each
  returns. Individual buttons still exist below it if you want to grant one
  thing at a time.
- **`AgentLoop` runs full-auto, not "ask to continue."** It used to cap at 12
  steps and tell you to ask it to keep going; it now runs up to 40 steps in one
  continuous pass and, if it hits that cap, just reports where things stand
  instead of stopping to request permission to continue.

## The one thing that genuinely cannot be automated away
Android requires a real, manual tap for the Accessibility Service toggle and
each runtime permission (SMS, mic, contacts, storage, etc.) — every app on the
platform hits this wall, it's enforced by the OS itself, not a policy this app
chose. This is the same shape as Codex needing you to approve its sandbox
access once before it can edit and run freely: one one-time human gate, then
full autonomy after. "Grant everything" collapses that gate to the smallest
number of taps Android allows (one button, then confirm each system dialog as
it appears) — it can't collapse it to zero taps, because the OS won't let any
app do that to itself or another app.

Past that one-time gate, nothing in this app asks again. `AgentLoop` doesn't
show a "may I tap this?" dialog between steps — it decides, acts, reads the
result, and moves straight to the next action until the goal is done or the
40-step safety cap is hit.

- **No server, no internet dependency for inference.** The model runs entirely
  on-device via a JNI bridge to llama.cpp. `AssistantTwinBackend/` from the
  earlier version is no longer needed for anything.
- **Java + XML**, not Kotlin. Every source file under `app/src/main/java` is Java.
- **Not a fixed action list.** `agent/AgentActionExecutor.java` exposes generic
  primitives (open_app, tap_text, tap_xy, type_text, swipe, key, read_screen,
  send_sms, set_alarm, wait) instead of one hardcoded action per app. `open_app`
  just switches which app is frontmost; every other primitive then drives
  whatever that app renders — so it works on any installed app, not only ones
  we specifically coded for.
- **Codex-style loop, not one-shot.** `agent/AgentLoop.java` runs
  observe → decide → act → observe again, up to 12 steps per goal, the same
  shape as a computer-use agent: read the screen, decide the next single action,
  execute it, look at the result, decide the next one — rather than the model
  guessing everything in one reply.

## Getting the on-device model in
1. Pull llama.cpp as a submodule (not vendored here — it's large and moves fast):
   ```
   git submodule add https://github.com/ggml-org/llama.cpp app/src/main/cpp/llama.cpp
   git submodule update --init --recursive
   ```
2. Download a **non-gated** GGUF model — search "Qwen2.5-0.5B-Instruct-GGUF" on
   huggingface.co (public, no login). Grab the `q4_k_m` quantization for the
   best speed/quality tradeoff on phone CPUs.
3. Push it onto the device / emulator:
   ```
   adb push qwen2.5-0.5b-instruct-q4_k_m.gguf /data/data/com.dhanaram.assistanttwin/files/models/
   ```
   (or add first-run download-into-`getFilesDir()` code — the app looks for it
   at exactly `filesDir/models/qwen2.5-0.5b-instruct-q4_k_m.gguf`.)
4. Open in Android Studio (with NDK + CMake components installed) and
   `./gradlew assembleDebug`. This container has no Android SDK/NDK, so the
   Java/XML/Gradle side was authored and reviewed here but not compiled — the
   native JNI bridge specifically (see next section) is the piece most likely
   to need a small fix against whatever llama.cpp commit you pull.

## Getting an actual APK file (two ways, no compiler on my end)
This environment has no Android SDK/NDK, so I can hand you a project that
builds correctly, not a compiled binary. Two real ways to get the .apk:

1. **Push to GitHub, let CI build it (`.github/workflows/build-apk.yml`
   is already in this project):**
   ```
   cd AssistantTwin
   git init && git add . && git commit -m "Assistant Twin"
   git remote add origin <your empty GitHub repo URL>
   git push -u origin main
   ```
   Then on GitHub: **Actions tab → the run → Artifacts → `assistant-twin-debug-apk`**.
   That zip contains `app-debug.apk` — install it with
   `adb install app-debug.apk` or just copy it to the phone and tap it
   (allow "install unknown apps" once, Android-enforced, same as the
   permission gate discussed earlier).
2. **Android Studio, locally:** open the project folder, let it sync, then
   **Build → Build Bundle(s)/APK(s) → Build APK(s)**. Needs the NDK + CMake
   components installed (Android Studio will offer to install them the first
   time it hits the native build).

Either way, the APK does **not** contain the model weights — a GGUF file is
several hundred MB to 1GB+, way too big to bundle, and the CI runner has no
reason to fetch one either. After installing, push the model onto the device
separately (`adb push ... /data/data/com.dhanaram.assistanttwin/files/models/`,
as described above) before the app can actually generate anything — until
then it'll correctly tell you the model isn't found rather than crash.


llama.cpp's C API renames functions periodically (its own examples get updated
every few months to match). This bridge is written against a recent API shape
and every spot most likely to drift is commented `// API-SENSITIVE`. Expect to
possibly rename 1-2 function calls after your first build — that's a direct
compiler-error-driven fix, not a redesign. I'm flagging this precisely instead
of promising a guaranteed first-try compile, because llama.cpp's API is outside
anyone's control to pin without you telling me the exact commit you're on.

## The honest limits of "read/write all apps and data"
- **What actually works:** on-screen text/controls in whatever app is currently
  open (via Accessibility — `read_screen`, `tap_text`, `type_text`), plus
  proper API access to contacts, calendar, call log, and media once you grant
  those permissions.
- **What no permission on stock Android grants:** direct read/write access to
  another app's private internal database or files (its sandboxed storage).
  That's OS-enforced, not a settings toggle. "Full access to any app's data"
  in that literal sense isn't achievable without root. What the agent loop
  gives you instead is the same access a sighted person tapping the screen
  would have — which is enough to actually operate most apps end-to-end
  (send a WhatsApp message, book something, fill a form), just not to read
  their databases directly.

## Play Store policy — unchanged, still the real blocker for public release
- `SEND_SMS`/`READ_SMS` require being the default SMS handler for Play approval.
- Broad Accessibility Service use (general app control) gets rejected without a
  narrow, disclosed accessibility use case — and "drives any app" is about as
  broad as it gets.
- `MANAGE_EXTERNAL_STORAGE` needs its own declared justification.
- Sideload / enterprise MDM distribution has none of these restrictions.

## Left for you
- `AgentLoop`'s prompt has no few-shot examples yet — a 0.5B model will need
  them to reliably emit clean JSON instead of chatty text; add 2-3 example
  turns to the system prompt once you see what it actually outputs.
- Sampling is greedy in the JNI bridge (deterministic, sometimes repetitive).
  Swap in a temperature/top-p sampler chain once the basic loop compiles.
- No conversation memory across app restarts (`AgentLoop`'s transcript is
  built fresh per call from `AssistantForegroundService`/`ChatActivity`).
- Voice is tap-to-talk, not always-listening/wake-word.
