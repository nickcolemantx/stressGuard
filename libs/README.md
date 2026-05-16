# Samsung Health SDKs

Drop the two `.aar` files in this folder. They are referenced from
`watch/build.gradle.kts` and `companion/build.gradle.kts` via
`implementation(files(...))` and the build will fail to sync until
they are present.

## Files expected

```
libs/
├── samsung-health-sensor-sdk.aar
└── samsung-health-data-sdk.aar
```

## Where to download (free, no account required for SDK download)

| SDK | URL | Used by |
|---|---|---|
| Samsung Health **Sensor** SDK | https://developer.samsung.com/health/sensor | `:watch` (raw HR / PPG) |
| Samsung Health **Data** SDK | https://developer.samsung.com/health/data | `:watch` + `:companion` (stress score) |

After downloading, rename if necessary so the filenames match exactly
the names listed above (or update the `implementation(files(...))`
lines in the module `build.gradle.kts` files to match).

## Why they aren't in git

The `.aar` files are binary distributions from Samsung; they are
gitignored (see root `.gitignore`). Each developer must download
their own copy.
