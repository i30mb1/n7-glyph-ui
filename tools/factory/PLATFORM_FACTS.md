# Glyph Matrix platform facts (verified 2026-11-14 on a Nothing Phone (3), SDK 2.0)

Everything here is a hard constraint for any toy design or code.

## The display
- Nothing Phone (3) has a round monochrome LED matrix on its BACK: a 25x25 grid clipped to a circle, 489 real LEDs.
  Visible x-range per row y=0..24: 9..15, 7..17, 5..19, 4..20, 3..21, 2..22, 2..22, 1..23, 1..23, then 0..24 for rows 9..15,
  then 1..23, 1..23, 2..22, 2..22, 3..21, 4..20, 5..19, 7..17, 9..15. Centre (12,12). Safe radius for content: 11.
- White LEDs only. Brightness per LED is an int 0..4095 (the hardware quantises to about 255 steps). No colour, no red.
- A frame is `IntArray(625)`, row by row, sent with `GlyphMatrixManager.setMatrixFrame(IntArray)`. 25-30 fps is realistic.
- The picture is defined as seen by someone LOOKING AT THE BACK of the phone: x grows to that viewer's right, y grows down.
- A static frame may go dark after 3-4 idle minutes. Animations keep it alive.

## Input
- There is ONE button on the back: the Glyph Button.
  - Short press: the SYSTEM switches to the next toy. A toy never sees it. Taps are not usable.
  - Long press: the toy receives `GlyphToy.EVENT_CHANGE`.
  - If the player keeps holding after the long press and then lets go, the toy receives `GlyphToy.EVENT_ACTION_UP`.
    So "hold and release" works and its duration is measurable, but a hold always starts with the long press.
- Motion sensors: accelerometer and gyroscope (up to ~480 Hz; without the HIGH_SAMPLING_RATE_SENSORS permission apps get 200 Hz max),
  magnetometer 100 Hz, step counter, proximity (front). No barometer, no heart-rate sensor.
- No touch on the matrix. No microphone, camera or network in this batch of apps.
- Vibration from the toy service is UNVERIFIED on the device. Use it as a bonus only: every piece of feedback must also be visible
  on the matrix (the owner films the matrix for short videos; viewers cannot feel vibration).

## How a toy runs
- A toy is an Android `Service` with intent-filter action `com.nothing.glyph.TOY`. The system app `com.nothing.thirdparty` binds it
  while the toy is the one selected on the back of the phone, and unbinds it when the player switches away.
- The player adds toys in Settings > Glyph Interface > Glyph Toys, then cycles through them with short presses.
- One app may declare several toy services: each appears as its own toy in that list with its own name and preview picture.
- Always-on (AOD) toys: if the toy declares `com.nothing.glyph.toy.aod_support`=1 the owner can pin it as the always-on toy;
  then it gets `GlyphToy.EVENT_AOD` about once a minute and should draw ONE calm frame (no animation loop). Button events can be lost in AOD.
- `setText` of the SDK renders Latin only. Draw text yourself. Prefer pictograms and digits: they need no translation.

## The audience and the owner
- The owner is a Russian-speaking content creator; subscribers are young. They want toys that are bold, fun and make viewers say "wow".
  Utility matters less than the effect on camera. The look must feel like part of the Nothing ecosystem.
- Rejected before as boring or dated: aircraft radar, cartoon aquarium, a cat knocking cups off a shelf.
- UI copy: English short and imperative (TILT. EAT. GROW.). Russian equally short, addressing the player as «ты». No emoji in the UI.

## Google Play rules that touch design
- Never encourage throwing or dropping the phone.
- No "detectors" of impossible things (lies, ghosts), even as a joke. No health or medical claims.
- No extra permissions unless unavoidable. These apps need only `com.nothing.ketchum.permission.ENABLE` and `VIBRATE`.
