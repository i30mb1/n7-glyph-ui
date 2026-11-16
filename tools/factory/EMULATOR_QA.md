# Emulator pass of one n7 Glyph app

The app is built, unit-tested and code-reviewed, but nobody has ever started it. You start it on an Android emulator, check that
everything a player can reach on the phone screen works and looks right, take the screenshot the store kit needs, render the store
pictures, look at them critically, and fix what is wrong. The owner publishes these apps on Google Play for a young audience and
other people check after him, so "it starts" is not the bar.

## Read first
1. The app's `SPEC.md` (what every state must do, the store shots it lists, the listing).
2. `APP_BUILD.md` and `PLATFORM_FACTS.md` next to this file (rules, architecture, what the template gives).
3. The app's `MainActivity.kt`, `game/` engines and their `Tuning` objects, `store/store.json`.
4. `D:\AndroidProject\n7-glyph-ui\store\README.md` (store kit, layouts, where the pictures go).

## Hard rules
- Use ONLY the emulator serial given in your task, and pass it on every adb call: `adb -s <serial> ...`.
  adb is `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe` (not on PATH).
  Never run adb without `-s`. Never touch another serial (other agents use the other emulators; a real phone may be attached and must never get a command).
  Never run `adb kill-server`, never close or restart an emulator, never wipe it.
- Gradle only through `python D:\AndroidProject\n7-glyph-ui\tools\gw.py <project dir> <args>` (10-minute tool timeout; it queues behind other agents).
- Do not edit anything under `D:\AndroidProject\n7-glyph-ui` and do not touch other apps. If the library, template or store kit is at fault, describe it in your report.
- `toy/GlyphToyService.kt` stays byte-for-byte as it is. No new permissions or dependencies. Do not commit, tag or push.
- Write multi-line scripts to a file with the Write tool and run the file. In Git Bash prefix adb calls that carry device paths with `MSYS_NO_PATHCONV=1`.
  Foreground `sleep` is blocked in the shell tools: wait with `adb -s <serial> shell sleep 2` or a Python script instead.

## What the emulator can and cannot show
- It has no Glyph Matrix and no Glyph Toys manager, so the toy services are never bound there. The same engine runs on the app screen
  inside `ToyStage` (the round matrix picture with the hold button under it). That is what you test. "Turn on" / "Glyph Toys" buttons must not
  crash when the manager is missing.
- Screenshot: `adb -s <serial> exec-out screencap -p > file.png` (Bash, not PowerShell), then look at the PNG with the Read tool.
- Tap and hold: `adb -s <serial> shell input tap X Y`, `adb -s <serial> shell input swipe X Y X Y <ms>` (same point = a hold of that length).
  Find coordinates from a screenshot or `adb -s <serial> exec-out uiautomator dump /dev/tty`.
- Sensors: `adb -s <serial> emu sensor set acceleration <x>:<y>:<z>` (m/s², device axes; upright phone is `0:9.81:0`, flat on its back `0:0:9.81`),
  `... emu sensor set gyroscope <x>:<y>:<z>` (rad/s), `... emu sensor status`. The stage shows the matrix as seen from the SCREEN side.
  A sequence of values sent from a script with short pauses gives a motion (a jump, a shake, a turn). adb round trips take 50-150 ms each, so fast
  gestures are coarse: judge behaviour, not exact thresholds, and never change `Tuning` numbers to fit the emulator.
- Russian: `adb -s <serial> shell cmd locale set-app-locales <package> --locales ru-RU` (back with `--locales en-US`), then restart the activity.
- Large text: `adb -s <serial> shell settings put system font_scale 1.3` (set it back to 1.0 when done).
- Crashes: `adb -s <serial> logcat -d -s AndroidRuntime:E` and `adb -s <serial> logcat -d | grep <package>`.

## Do, in this order
1. Build `:app:testDebugUnitTest :app:assembleDebug :app:assembleRelease` (the shared library changed: the "More from n7" catalogue now lists six apps).
2. Install the RELEASE apk (`app\build\outputs\apk\release\app-release.apk`, minified by R8) with `adb -s <serial> install -r` and do the whole pass on it:
   R8 breaks only the minified build. (Debug and release are signed differently: `adb -s <serial> uninstall <package>` before switching.)
3. The screen, top to bottom, English then Russian: headline, the one-line tagline, setup card, the stage, "How it works", the app's own cards,
   "More from n7" (five other apps, none of them this app), the signature. Scroll through everything and screenshot each part.
   Look for: clipped or overflowing text, text that wraps badly, wrong language, untranslated strings, overlapping elements, touch targets that
   do nothing, more than one filled primary button, anything that looks unlike the house style. Repeat the scroll at font scale 1.3.
4. Every interactive thing on the screen: each button, stepper, switch, card. Rotate the activity state: leave with Home and come back,
   kill and restart the app (records and settings must survive), background it while the stage is in the middle of something.
5. The toy on the stage, state by state from the spec: the "First ten seconds", every outcome of a round, every setting that changes the toy.
   Use the hold button and the sensor commands. For each state: screenshot, say what the matrix picture shows and whether it reads as what the spec means.
   A state you cannot reach on the emulator goes into the report as "not reached", with the reason.
6. Crash check in logcat after the pass.
7. Store pictures. If a shot uses the `screen` layout, set the app up as the spec says for that shot, take the screenshot, save it where `store.json`
   points (`store/screens/...`), in English, with a clean status bar
   (`adb -s <serial> shell settings put global sysui_demo_allowed 1`, then
   `adb -s <serial> shell am broadcast -a com.android.systemui.demo -e command enter` and the `clock`, `battery`, `network`, `notifications` demo commands; `-e command exit` afterwards).
   Then `python D:\AndroidProject\n7-glyph-ui\store\render.py <project dir>` (all outputs) and look at EVERY picture it wrote under
   `app\src\main\play\listings\en-US\graphics\` and `ru-RU` if present: 5 phone screenshots, the feature graphic, the icon.
   Judge them as a twelve-year-old scrolling Google Play: is it clear in two seconds what the app does; is every headline readable and not cut;
   is each matrix picture recognisable; do the five shots tell different things; is anything empty, cramped or misaligned.
   Improve `store/store.json` (and the frames in `StoreFramesTest` if a frame is the weak part) until they are good, re-render, look again.
8. Fix every defect you found in the app's own code, with a unit test where the defect is in `game/`. Rebuild all three tasks, reinstall the release apk,
   re-check the fixed things on the emulator. If you changed frames or icon cells, re-render the drawables too and rebuild.
9. Leave the emulator usable: font scale 1.0, app locale reset, demo mode off.

## Evidence
Save the screenshots you base your judgement on into the folder given in your task, with names that say what they show
(`en_top.png`, `ru_cards.png`, `stage_wish.png`, ...). The next person looks at them without the emulator.

## Report
Short and factual, plain text:
- what you checked and saw working (list of states and screens, with the screenshot name for each);
- every defect: what, where, how you reproduced it, what you changed, how you re-checked it;
- what you could not check on the emulator and why;
- problems in the library, template or store kit (you must not fix those);
- decisions the owner has to make;
- final build result (test count) and the paths of the release and debug apk.
Never report something as working that you did not see working.
