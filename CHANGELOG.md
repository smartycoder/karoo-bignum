# Changelog

Notable changes per release. Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
versioning follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Each entry here should match the `releaseNotes` field in `app/manifest.json`, which is what the
Karoo shows in its own update flow.

## [Unreleased]

## [1.2.1] - 2026-08-27

### Fixed

- `minSdk` is 26 rather than 29, so BigNum installs on the Karoo 2. The Karoo 2 runs Android 8.1
  (API 27) and goes no further, and nothing here ever needed API 29 -- `getFont()` and
  `fontVariationSettings`, the newest calls in the renderer, are both API 26. Verified on a
  Karoo 2: the extension connects and the fields render.

## [1.2.0] - 2026-08-26

### Added

- **The lap set**, 14 fields: number, time, distance, speed, max speed, heart rate, cadence,
  max cadence, normalized power, max power, W/kg, VAM, ascent and descent, all for the current
  lap.
- **Navigation**, 3 fields: distance to destination, distance to next turn, and ETA. They need
  a route loaded and sit at `--` without one. ETA is a 24-hour wall clock, drawn whole rather
  than with its minutes raised, so it does not read as a stopwatch.

### Changed

- Durations under an hour lose the leading `0:` and are sized against `mm:ss`, so the number is
  drawn taller wherever width is what limits it.
- A value wider than its width template now shrinks only if it is wider than the tile. Where
  height is what limits the number -- most tiles -- a 4-digit power kept a quarter of its height
  for a template it had already outgrown, with room to spare beside it.
- **Power - Lap Avg** is now **Lap - Avg Power**, so it sits with the rest of the lap fields in
  the picker. The field itself is unchanged and pages holding it are not affected.
- Field labels are drawn in capitals and sized against a capital rather than against their own
  ink, so every header has the same letter height and sits on one line. A label with a descender
  ("TIME lap") used to come out a fifth shorter than one without ("HR").

## [1.1.0] - 2026-08-25

### Added

- **Font setting.** Numbers are now set in **Saira** by default, with a **Width** (50-124%) and
  **Weight** (100-900) to go with it; **Oswald Bold** is still there for anyone who preferred it.
  Saira's digits are all the same width, so a value no longer shifts sideways as its digits
  change; measured against Oswald over same-length values, that shift reaches 35-45px in the
  widest field. Narrow digits also make the number *taller*: most fields run out of width before
  they run out of height, so at the default width the digits gain roughly 14% on a half-width
  field and a third on **Time - Elapsed** and **Climb - VAM**. Field labels stay in Oswald
  whatever the number is set to, since at 11dp a condensed face loses the space between a label's
  words.
- **Climb - Grade** draws the slope as a coloured wedge behind the number. Its direction follows
  the sign -- rising for a climb, falling for a descent -- and its height and colour follow the
  size, over the same seven bands as the Karoo's own Climber scale, with thresholds at
  2 / 5 / 8 / 11 / 14 / 20%. At 20% the wedge runs corner to corner. Because the wedge cuts
  diagonally across the number, the number and label take a thin contrasting outline rather than
  flipping colour wholesale, which no single threshold could get right.
- Turning zone colouring off now turns the grade wedge off too, on the reading that a rider who
  wants no colour means everywhere.
- **Raised decimals.** A value's decimal, or a ride time's seconds, drawn small and raised --
  34⁹ rather than 34.9, 1:34¹⁷ rather than 1:34:17 -- with the point or colon dropped, because the
  raised digits already say what they are and the separator's width is width the number can have
  instead. On by default, and it replaces the old fixed behaviour where elapsed time raised its
  seconds and nothing else could.
- The settings screen opens with a card in the Karoo's own style saying where to add the fields,
  and scrolls now that there is more on it than fits a 480x800 screen.
- A new icon: the app at icon size, a field card carrying one number with a raised decimal, drawn
  from the bundled Saira's own outlines. The old one was a white square on a white list row,
  which left three black bars floating with nothing around them.

### Changed

- **Time - Elapsed** reads h:mm:ss throughout, so the first hour of a ride shows 0:34:56 rather
  than 34:56 and a clock that has not started reads 0:00:00 rather than a pair of dashes. The
  field is sized against a "0:00:00" template either way, so the leading hour costs no room --
  what it buys is a field that does not change shape the moment the ride passes an hour.
- Each field's bitmap is now rasterised at the size it is displayed at, taken from the view size
  Karoo reports, instead of one fixed size for every tile. Tiles range from 238x142 to 478x288,
  so a single size was drawing about 1.7x more pixels than a half tile can show while stretching
  1.6x on the largest one. Nothing moves on screen; a half-width field allocates and ships 42%
  fewer bytes per update, and the largest tiles stop being upscaled.
- Redundant redraws are dropped. Karoo sends a sample whether or not the value moved, and a
  field whose displayed state is unchanged no longer draws a bitmap or crosses a process
  boundary to say so.

### Notes

- Saira is bundled as the variable `Saira[wdth,wght].ttf` from Google Fonts, subset to Latin so
  the two axes survive without carrying glyphs no field draws. Both it and Oswald are licensed
  under the SIL Open Font License 1.1; see [OFL.txt](OFL.txt).
- A non-finite grade takes the lowest band and the floor wedge height rather than the loudest
  colour and an undefined path.
- The wedge is Grade's alone: every other field takes the same drawing path it always did, and
  the wedge hook defaults to nothing.

## [1.0.1] - 2026-08-24

### Added

- **Power - W/kg 3s** and **Power - W/kg 5s**. karoo-ext has no smoothed power-to-weight type, so
  these divide the smoothed power stream by the rider weight from the Karoo profile. Without a
  usable weight the field shows `--` rather than raw watts labelled as W/kg.

### Fixed

- A field's fallback value is now rendered through the same conversion as a live one, so a derived
  field cannot print watts where it promises W/kg.
- A non-finite rider weight no longer reaches the screen as `NaN`.

## [1.0.0] - 2026-08-24

First public release.

### Added

- 39 data fields under **BigNum** in the field picker, covering speed, distance, heart rate,
  cadence, power, climbing, time and temperature.
- Numbers rendered in bundled Oswald Bold, sized to fill the field rather than sit at the
  default text size. Each field keeps a constant text size regardless of how many digits its
  current value has; wider values shrink to fit.
- Zone coloring for heart rate and power fields, using the colors the Karoo shows on its own
  Heart Rate Zones and Power Zones settings screens — five zones for heart rate, seven for
  power — with the boundaries taken from the rider's Karoo profile. Three modes:
  - **Off** — every value in the normal text color.
  - **Number** — the number takes the zone color.
  - **Field background** — the field is filled with the zone color and the number, label and
    icon switch to black or white, whichever contrasts better with that fill.
- Elapsed time draws the seconds at half size and raised, so the hours and minutes stay large.
- Metric/imperial follows the Karoo profile for speed, distance, elevation and temperature;
  power-to-weight and TSS use the rider weight and FTP from the same profile.
- Power smoothing offered as separate fields — 3s, 5s, 10s, 30s, 20m, 1hr, lap average and
  normalized power — because karoo-ext gives an extension no per-field settings of its own.
- Light and dark themes, picked up from the Karoo's own setting on every redraw.

### Notes

- A field with no zone, a value of zero, or a profile with no zones configured is drawn in the
  normal text color and never filled.
- The rounded card behind each field is drawn by Karoo. On a ride page it does not clip the
  extension's view to that card, so the fill rounds its own corners to match.

[Unreleased]: https://github.com/smartycoder/karoo-bignum/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/smartycoder/karoo-bignum/releases/tag/v1.1.0
[1.0.1]: https://github.com/smartycoder/karoo-bignum/releases/tag/v1.0.1
[1.0.0]: https://github.com/smartycoder/karoo-bignum/releases/tag/v1.0.0
