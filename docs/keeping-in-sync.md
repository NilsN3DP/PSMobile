# Staying in sync permanently

**The question:** the port made iOS and Android congruent in September 2026.
How does it stay that way?

**The short answer:** not through discipline. Discipline did not hold for a
quarter of a year. It stays that way because divergence breaks the build.

---

## Why discipline is not enough

From July to September there was a rule ("Android should look like iOS"), a
requirements document, a parity matrix with 64 entries and 127 proof
screenshots. The apps still drifted apart – because nobody could *measure*
whether a change widened the gap. Every deviation was only noticed when
someone saw it on a device.

So the rule has to be one a machine can check. That is exactly why the UI
was built the way it was during the port:

- **same file names** – `SimpleModeView.swift` ↔ `SimpleModeView.kt`
- **same public names** – `struct SimpleModeView: View` ↔ `fun SimpleModeView(`
- **same identifiers** – `accessibilityIdentifier("x")` ↔ `testTag("x")`
- **same texts** – `st("English", "Deutsch")` is character-identical on both sides

That is not cosmetics. Those are four invariants that can be compared
without starting the app.

---

## Four layers

### Layer 1 – structure (in place since the port)

Makes divergence *visible*. Without it nothing is comparable. Described in
[twin-conventions.md](twin-conventions.md).

### Layer 2 – the guard

Makes divergence *fail*. `tools/paritaet_pruefen.py` checks:

| Rule | Meaning |
|---|---|
| **ZWILLING FEHLT** (twin missing) | A Swift view without a Kotlin counterpart. |
| **VORLAGE FEHLT** (template missing) | An Android view without an iOS original – a new screen that iOS does not have is the beginning of drift. |
| **KENNUNG FEHLT** (identifier missing) | An `accessibilityIdentifier` without a `testTag`. |
| **TEXT FEHLT** (text missing) | A label that reads differently or is missing on Android. |
| **NAME FEHLT** (name missing) | A public view without a composable of the same name. |
| **ZWILLING STEHT** (twin untouched) | The Swift file was changed, the twin was not. The strictest rule – it catches exactly the case "changed iOS, forgot Android". |

For justified deviations there are three lists at the top of the script
(`OHNE_ZWILLING_SWIFT`, `OHNE_VORLAGE_KOTLIN`, `ANDERSWO_DEKLARIERT`).
Whoever adds an entry writes a sentence next to it. That is the difference
between a deliberate decision and an oversight.

**Where it runs:**

```bash
python tools/paritaet_pruefen.py                            # everything
python tools/paritaet_pruefen.py --geaendert --basis HEAD   # only the commit
```

- **Before every commit** via `tools/hooks/pre-commit`.
  Set up: `git config core.hooksPath tools/hooks`.
  Bypass in a justified single case: `git commit --no-verify`.
- **On every push and pull request** via `.github/workflows/paritaet.yml`.

The hook checks only the files of the commit (fractions of a second), CI
checks everything.

### Layer 3 – duplicate less

Whatever lives in the shared module `android/shared` exists only once and
cannot drift at all. Today it already holds the multi-bed rules, filament
catalogue, adhesion advice, layer profiles, colour mixing, preview ranges,
the settings page structure and window scaling.

**Rule for new code:** any logic without platform reference belongs in the
shared module, not in the view. A calculation, a sort order, a validation, a
state transition – all of that can be shared. Only drawing, gestures and
system access stay per platform.

A warning from the rebuild: the role names of the G-code preview *were* in
the shared module (`PreviewRoles`), but iOS did not use them and had its own
list with different words. Result: the same legend showed "External
perimeter" on Android and "External" on iOS. Shared code only helps if
**both** sides use it. Otherwise it is the third copy.

### Layer 4 – the final stage (open)

Compose Multiplatform would reduce the UI to **one** implementation. Then
there would be nothing left to synchronise.

| | |
|---|---|
| **For** | One UI instead of two. The guard would be superfluous. The existing Compose code would be the base. |
| **Against** | The project deliberately chose native UIs ([decisions.md](decisions.md), E-02/E-13), because **pen pressure and Pencil hover** are the actual purpose – they would be lost with a shared UI or would have to be passed through laboriously. Also: Compose on iOS is still young, and the GLES viewport depends on platform code. |

Assessment: **not yet.** Layers 1–3 solve the problem at low cost. Layer 4
is a project of its own and should only come up once the current state has
run stably for a few months and Compose Multiplatform handles pen input
cleanly on iOS.

---

## How a change works from now on

1. **iOS first.** The Swift file is the reference – that is where it is
   decided how something looks and what it is called.
2. **Twin in the same commit.** The same change in the Kotlin file of the
   same name. Texts and identifiers character-identical.
3. **The hook checks.** If the twin is left untouched, the commit does not
   go through.
4. **Build and look.** Build, install, take screenshots.
5. **CI checks everything again**, including the files this commit did not
   touch.

This does not apply to pure Android infrastructure (service, JNI, network) –
it has no Swift original and is developed normally.

---

## What the guard cannot do

It compares text, not pictures. Not checked:

- **Layout dimensions and spacing.** Whether a button is 44 or 48 dp high,
  only the eye sees.
- **Order and nesting.** Both sides can show the same elements in a
  different arrangement.
- **Behaviour.** Whether a button does the same thing is not in any text
  comparison.

For that there is still the look at both devices. The guard only takes the
mechanical work off it, so the review can concentrate on substance.

**One more step is possible:** a screenshot tour on both platforms, laid side
by side (`tools/rundgang-android.sh` and `ScreenshotTourUITests`). It is more
effort and more fragile than a text comparison, so it is an option, not a
requirement – sensible before a release, not on every commit.
