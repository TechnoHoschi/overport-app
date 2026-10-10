# overport – Galaxy XR fork

Fork of [ovrport/app](https://github.com/ovrport/app) focused on the **Samsung Galaxy XR (Android XR)**,
primarily for VR media players (4XVR, NEX, Skybox, Moon …). Project notes live in
[TechnoHoschi/galaxyxr-questport](https://github.com/TechnoHoschi/galaxyxr-questport).

Branch `galaxyxr` carries the changes, `master` mirrors upstream.

## Changes vs. upstream

| Area | Change |
|---|---|
| `analyze` command | Read-only report: engine, XR libraries, Android XR manifest readiness, Meta platform SDK usage (entitlement, IAP, DLC) |
| `--extra-patches` | Add patches/arguments on top of the recommended set instead of listing every patch |
| Patch validation | Unknown patch names and invalid patch arguments fail with a clear message instead of being ignored |
| Patch order | Patches always run in registration order, independent of the order on the command line |
| `patch_platform_products` | Mark in-app products you own as purchased in overport's platform emulation (premium unlocks) |
| `patch_runtime_config` | Set any `liboverport.config.so` value (texture size, CPU/GPU level, vibration, verbose logging …) |
| `patch_vr_metadata` | Configurable: `boundary`, `start_mode_scope`, `openxr_loader`. Declares `libopenxr.google.so` as `uses-native-library` (Android XR docs) in addition to `uses-library`. Fixed feature name `android.hardware.xr.input.controller` (was `software`). XR properties are now also added to elements without children |
| `patch_remove_uses_library` | Default is now `mode=optional`: libraries are kept but marked `required=false` (upstream removed all of them, breaking e.g. `org.apache.http.legacy`). `mode=remove` restores upstream behaviour |
| Config generation | Frida-config products/assets are merged instead of overwriting the database |
| GUI | Patches that need arguments are hidden (no argument input yet) |

## Usage

```bash
# What does the app need?
java -jar overportcli-*-all.jar analyze --input=player.apk

# Recommended patches + premium SKU you own + verbose runtime logging
java -jar overportcli-*-all.jar patch --input=player.apk \
  --extra-patches="patch_platform_products=<sku>;patch_runtime_config=verbose_logging=1"

# Android XR manifest options
java -jar overportcli-*-all.jar patch --input=player.apk \
  --extra-patches="patch_vr_metadata=boundary=NO_RECOMMENDATION,start_mode_scope=both,openxr_loader=native"
```

Install on the Galaxy XR:

```bash
adb install -r player-output.apk          # first time: uninstall the original first (signature differs; OBB/saves are lost!)
adb shell appops set <package> MANAGE_EXTERNAL_STORAGE allow   # media players: access to local videos
adb shell setprop debug.sys.ovrport.verboselogging 1           # optional, for logcat analysis
```

## Builds

GitHub Actions workflow `Galaxy XR build` (`.github/workflows/galaxyxr.yml`) builds the CLI JAR, the Android
app (debug-signed, installable) and the Windows desktop app on every push to `galaxyxr`. Running it manually
with `release_tag` creates a pre-release.

The overport runtime libraries are closed source and are still downloaded from the upstream release index at
patch time; they are not redistributed here.

## License

GPLv3, like upstream.
