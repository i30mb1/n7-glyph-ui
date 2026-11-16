export const meta = {
  name: 'n7-glyph-app-build',
  description: 'Build one n7 Glyph Matrix app from its spec, review it from two sides, fix what the reviews prove',
  phases: [
    { title: 'Build', detail: 'one engineer builds the whole app from the spec' },
    { title: 'Review', detail: 'a player-side reviewer and a release-side reviewer, both must run things' },
    { title: 'Fix', detail: 'apply confirmed findings, rebuild, re-render' },
  ],
}

const SP = '<scratch folder>'
const F = SP + '\\factory'
const LIB = 'D:\\AndroidProject\\n7-glyph-ui'
const GW = 'python ' + LIB + '\\tools\\gw.py'
const app = args
const DIR = 'D:\\AndroidProject\\' + app.dir
const SPEC = F + '\\specs\\' + app.slug + '.md'
const A = (prompt, opts) => agent(prompt, Object.assign({ model: 'opus', effort: 'high' }, opts))

const COMMON = [
  'Environment: Windows 11, Git Bash for the Bash tool (POSIX syntax), PowerShell also available, Python 3.14 with Pillow, JDKs under ~/.gradle/jdks, Android SDK at %LOCALAPPDATA%\\Android\\Sdk.',
  'The owner\'s real phone is plugged into this computer. Never send any adb command to it or to an emulator: you do not need a device for this task.',
  'Hard rules: do not commit, tag or push anything. Do not edit anything under ' + LIB + ' and do not touch any other app. Run Gradle ONLY as: ' + GW + ' ' + DIR + ' <gradle args> (it queues builds machine-wide; other engineers are building two sibling apps right now, so waits of a few minutes are normal; give the tool call a 10 minute timeout and simply run it again if it times out while waiting). Never run gradlew directly, never stop Gradle daemons.',
  'Write multi-line scripts with the Write tool into a file and run the file; shell heredocs containing backticks or backslashes break in this environment.',
  'Work economically: read what you need once, batch your builds (compile and test in one Gradle call), and keep going until the job is really done.',
  'Your final message is your report to the orchestrator: plain text, factual, no pleasantries. State what you verified by running it and what you did not verify.',
].join('\n')

phase('Build')
const built = await A([
  'You are building a complete Android app for the Glyph Matrix on the back of the Nothing Phone (3).',
  'Read ' + F + '\\APP_BUILD.md first: it is your working agreement and it lists everything else to read.',
  'Your app: "' + app.title + '", package ' + app.pkg + ', project directory ' + DIR + ' (does not exist yet: create it with the generator, repo name ' + app.dir + ', app name "' + app.name + '"). The design spec: ' + SPEC + '.',
  'The spec is the product decision: build it as written, completely. Where it contradicts the toy kit, the template or the platform facts, those win: adapt as little as possible and list each case in your report. The spec\'s prototypes (Python, paths inside the spec) show the intended look and numbers; open their rendered pictures when a description is unclear.',
  'Every number the spec marks as needing a check on the real phone (thresholds, timings, brightness levels) goes into ONE object per engine named Tuning, as named constants with a one-line comment, so it can be changed after device tests without hunting through the code.',
  'Besides what APP_BUILD.md asks, write ' + DIR + '\\store\\catalog.json for the family catalogue: {"title": ..., "summaryEn": ... (max 60 chars, what the player does), "summaryRu": ..., "icon": [[x, y, level0to1], ...], "signal": [[x, y], ...]} with exactly the icon picture and red element your store.json uses.',
  COMMON,
].join('\n\n'), { label: app.slug + ': build', phase: 'Build' })
if (!built) return { slug: app.slug, failed: 'build agent died' }

const FINDINGS = {
  type: 'object',
  properties: {
    verified: { type: 'array', items: { type: 'string' }, description: 'What you ran or checked that turned out correct' },
    findings: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          title: { type: 'string' },
          severity: { type: 'string', enum: ['blocker', 'major', 'minor'] },
          file: { type: 'string' },
          evidence: { type: 'string', description: 'What you ran or read that proves it, with the observed output' },
          fix: { type: 'string', description: 'The concrete change to make' },
        },
        required: ['title', 'severity', 'file', 'evidence', 'fix'],
      },
    },
  },
  required: ['verified', 'findings'],
}

const REVIEW_RULES = [
  'You are a reviewer and you do not edit the app\'s main sources. You may add test files under ' + DIR + '\\app\\src\\test (keep the ones that pass and add value, delete the rest) and throwaway files under ' + SP + '\\review_' + app.slug + '.',
  'Report only defects you proved: by a failing test, a build, a script, a rendered picture you looked at, or the exact lines plus the input that breaks them. No style opinions and no speculative risks without a concrete failing scenario. What is right goes into "verified". Things that can only be known on the real phone are not findings.',
  COMMON,
].join('\n\n')

phase('Review')
const reviews = await parallel([
  () => A([
    'Review a new Android app for the Glyph Matrix (a 25x25 round LED matrix on the back of the Nothing Phone (3)) as the PLAYER would meet it. The app: ' + DIR + '. Its design spec, which is the contract: ' + SPEC + '. Background: ' + F + '\\PLATFORM_FACTS.md, ' + F + '\\KIT_SPEC.md, toy kit source under ' + LIB + '\\n7-glyph-ui\\src\\main\\kotlin\\n7\\glyph\\ui.',
    'The engineer reported:\n<report>\n' + built + '\n</report>',
    'Attack the engines (pure Kotlin under app/src/main/kotlin/.../game): (1) play the spec\'s "First ten seconds" and every row of its controls table as unit tests with synthetic Motion and button events, including the awkward orders (release without long press, long press twice, events in the same frame, no motion samples at all, huge dt, dt of 0, motion timestamps that jump or repeat); (2) hunt dead ends: any state the toy cannot leave, any timer that never fires, any way to get a result the spec forbids; (3) dump the frames of every state to ASCII and look at them: is each picture what the spec draws, readable, inside the round outline, with the brightness levels the spec gives; (4) check every threshold and timer against the spec\'s numbers; (5) check that saved data survives a new engine instance and that still() changes nothing; (6) check that nothing in the copy or the mechanics rewards throwing or dropping the phone.',
    'Then read MainActivity.kt as a first-time user: can the toy be tried on the screen at once, does every control do what its label says, do the settings reach both the stage and the toy service.',
    REVIEW_RULES,
  ].join('\n\n'), { label: app.slug + ': review play', phase: 'Review', schema: FINDINGS }),
  () => A([
    'Review a new Android app of the n7 Glyph family for RELEASE: house style, store material and build. The app: ' + DIR + '. Its design spec: ' + SPEC + '. The working agreement the engineer had: ' + F + '\\APP_BUILD.md. The template it was generated from: ' + LIB + '\\template. The production reference: D:\\AndroidProject\\glyph-pet. Look rules: ' + LIB + '\\store\\ds\\README.md. Store kit: ' + LIB + '\\store\\README.md.',
    'The engineer reported:\n<report>\n' + built + '\n</report>',
    'Attack it: (1) build :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease yourself and read the warnings; confirm R8 ran and that the release APK contains every toy service and the activity under their manifest names (aapt2 dump badging and xmltree from build-tools; dexdump for classes); (2) manifest: permissions exactly com.nothing.ketchum.permission.ENABLE and android.permission.VIBRATE, one service per toy with name, summary, image, longpress, and aod_support only where the spec says; toy/GlyphToyService.kt byte-identical to the template\'s apart from the package line; (3) the main screen against the template\'s order and the look rules (one primary button, red only as the signature, no emoji, Doto only for Latin); (4) strings: every English string has a Russian one and the reverse, Russian is short and uses «ты», no leftover template or demo text anywhere in the project (grep for Demo, Template, ball, ring where they do not belong); (5) Play listing: title at most 30 characters, short description at most 80, full at most 4000, both languages, honest (no claim the app cannot keep, says it needs a Nothing Phone (3), no microphone/throwing/health claims), release notes present; docs/privacy.html accurate for this app; (6) store: run the store kit for icon, drawables and feature (python ' + LIB + '\\store\\render.py ' + DIR + ' --only icon --only drawables --only feature) after the unit tests, then open the pictures with the Read tool: does the icon read at a glance, exactly one red element, is the feature graphic clean; check that store.json lists the shots the spec asks for and that every frame name exists in app/build/store/frames.json; open app/build/store/frames.txt and judge every store frame; (7) store/catalog.json exists and matches the icon; (8) leftovers: files of the template demo that are no longer used, unused resources, dead code.',
    REVIEW_RULES,
  ].join('\n\n'), { label: app.slug + ': review release', phase: 'Review', schema: FINDINGS }),
])

const all = reviews.filter(Boolean).flatMap((r, i) => (r.findings || []).map(f => Object.assign({ side: ['play', 'release'][i] }, f)))
log(app.slug + ': reviews found ' + all.length + ' issues (' + all.filter(f => f.severity === 'blocker').length + ' blockers, ' + all.filter(f => f.severity === 'major').length + ' major)')

phase('Fix')
let fixed = 'No findings.'
if (all.length) {
  fixed = await A([
    'Two reviewers found problems in the Android app ' + DIR + ' ("' + app.title + '", a Glyph Matrix toy app). Its spec is ' + SPEC + '; the working agreement is ' + F + '\\APP_BUILD.md.',
    'Findings as JSON:\n' + JSON.stringify(all, null, 1),
    'For each finding: reproduce it first. If it is real, fix the root cause and add or adjust a test where a test is possible. If it is not real, say why with evidence. Do not make unrelated changes. Reviewers may have left new test files in app/src/test: keep them passing.',
    'Finish with: ' + GW + ' ' + DIR + ' :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease, then python ' + LIB + '\\store\\render.py ' + DIR + ' --only icon --only drawables --only feature, then rebuild :app:assembleDebug if the drawables changed. Look at the re-rendered icon and feature graphic.',
    COMMON,
    'Report per finding: fixed (how) or rejected (why), then the final build and test results (test count), and the path of the debug APK.',
  ].join('\n\n'), { label: app.slug + ': fix', phase: 'Fix' })
}

return {
  slug: app.slug,
  dir: DIR,
  built,
  reviews: reviews.map(r => r ? { verified: r.verified, findings: r.findings } : null),
  fixed,
}
