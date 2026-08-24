# Changelog

Notable changes per release. Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
versioning follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Each entry here should match the `releaseNotes` field in `app/manifest.json`, which is what the
Karoo shows in its own update flow.

## [Unreleased]

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

[Unreleased]: https://github.com/smartycoder/karoo-bignum/compare/v1.0.1...HEAD
[1.0.1]: https://github.com/smartycoder/karoo-bignum/releases/tag/v1.0.1
[1.0.0]: https://github.com/smartycoder/karoo-bignum/releases/tag/v1.0.0
