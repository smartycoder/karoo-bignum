# karoo-bignum

Large, bold numeric data fields for Hammerhead Karoo 3, rendered in **Oswald Bold**.
Optional HR/Power zone coloring driven by your Karoo `UserProfile`.

## Fields

Speed · Heart Rate · Power · Cadence · Distance · Elapsed Time ·
Power 3s/10s/30s avg · HR avg · Elevation · Grade · Temperature · Lap Power.

Speed, Distance, Elevation, and Temperature respect your Karoo profile's metric/imperial preference.
HR and Power fields use a 5-zone color palette (Z1 grey, Z2 blue, Z3 green, Z4 orange, Z5 red).

## Build

```
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Sideload

```
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

In Karoo: Settings → Profile → Data Pages → Add Field → BigNum → choose a field.

## Test

```
./gradlew :app:testDebugUnitTest
```

26 unit tests (formatters and zone-color logic).

## Font license

Oswald is licensed under the SIL Open Font License 1.1.
© Vernon Adams et al. See `OFL.txt` at the project root.

## License

Apache-2.0. See `LICENSE`.
