<div align="center">

# AetherNet Browser

**Android browser with a built-in VPN, content blocker and download manager**

</div>

---

## What this is

AetherNet Browser is a work-in-progress Android browser. It is a fork of
[Clint Browser](https://github.com/jhaiian/ClintBrowser) by [@jhaiian](https://github.com/jhaiian),
rebranded and extended with a built-in VPN that runs on the Cruise VPN backend.

**Status: proof of concept.** Not released, not on the Play Store.

## What the fork adds

| Area | Change |
|---|---|
| **Built-in VPN** | WireGuard tunnel carrying only this app's traffic. Menu item, home-page card, and a location picker. |
| **Home page** | Custom start page: animated logo, search box, VPN card and the last 5 sites visited (by domain). The Home button, new incognito tabs, and closing the last tab all land here. |
| **Branding** | AetherNet name, icon, animated startup screen and wordmark. |
| **Incognito** | Everything turns dark while an incognito tab is active — app UI, system bars, web pages and the home page. |

Everything else — the Quiver Guard content blocker, website blocker, download manager, user scripts,
bookmarks, history, themes — comes from Clint Browser upstream.

## Building

```bash
./gradlew assembleGithubDebug     # debug APK per ABI, in app/build/outputs/apk/github/debug/
./gradlew assembleGithubRelease   # release build (unsigned unless signing is configured)
```

Requirements:

- **JDK 17 or 21** (not 25 — Gradle's Android plugin does not support it yet)
- **Android SDK platform 37** and build-tools 37
- `local.properties` with `sdk.dir=/path/to/Android/Sdk`

Release signing is optional. Add `signingConfig.storeFile`, `signingConfig.storePassword`,
`signingConfig.keyAlias` and `signingConfig.keyPassword` to `local.properties` to enable it; without
them the release build is simply unsigned.

There are two product flavors, `github` and `fdroid`, and native code in
[`native/`](native/) whose compiled libraries are committed under `app/src/main/jniLibs`. Gradle
packages those prebuilt binaries — a normal build needs neither the NDK nor Rust. The GitHub Actions
workflows in [`.github/workflows/`](.github/workflows) rebuild them when the sources change.

## VPN notes

- The tunnel carries **only this app's traffic**; other apps keep their normal connection.
- Access is enforced by the backend: it refuses expired or out-of-credit accounts and meters the
  session itself, so the client carries no ad or quota logic.
- The app signs in as a guest through Play Integrity. The backend must know this app's package name
  (`com.aethernet.browser`) and signing certificate, otherwise sign-in fails with
  `ATTESTATION_FAILED_PACKAGE_NAME`.
- The backend host is set per build type in [`app/build.gradle.kts`](app/build.gradle.kts).

## License

The source code is licensed under the [GNU General Public License v3.0](LICENSE), inherited from
Clint Browser.

The names "Clint" and "Clint Browser", the Clint logo and its screenshots are trademarks or
proprietary assets of the upstream author and are **not** covered by the GPL; this fork does not use
them. The AetherNet name and logo are assets of this project.

Third-party libraries keep their own licenses — see [Attribution.md](Attribution.md).
