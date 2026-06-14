![Preview](./docs/img/tui_header_1200x630.svg)
![Badge](https://rama-io.github.io/img/badge_tui.svg)

# Tūī

**Tūī** is a minimal, privacy-first local music player that skips metadata entirely. What you see is exactly what you name, no hidden tags, no surprises.

Built entirely in **native Kotlin**, Tūī runs fully **on-device**, avoids tracking, no internet
access, and no external APIs.

---

## Branding creation

Want to see how Tūī's visual identity came together?

This short session captures part of the process behind designing the app's header. Exploring sketches, composition, and refinement as the direction takes shape.

https://www.youtube.com/watch?v=We8xXVLiVI4

---

## Screenshots

| Playlist | Settings | About |
| - | - | - |
| ![Playlist](./fastlane/metadata/android/en-US/images/phoneScreenshots/1.png) | ![Settings](./fastlane/metadata/android/en-US/images/phoneScreenshots/2.png) | ![About](./fastlane/metadata/android/en-US/images/phoneScreenshots/3.png) |

---

## Permissions

| Permission | Why it's needed |
|---|---|
| **Read Media / Read External Storage** | Required to list and play music files on your device. Without this, the app cannot see any tracks. |
| **Write External Storage / Manage External Storage** | Required to rename and delete songs directly from the app. Only needed if you use those features; playback works without it. |

Permissions can be granted from **Settings → System** inside the app if they were skipped at first launch.

---

## Usage

- Long-press on a track to open **Track Settings**.

---

## Installation

<p>
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/rama-io/tui">
    <img src="https://rama-io.github.io/img/obtainium.png" alt="Get tui From Obtainium">
  </a>
  &nbsp;
  <a href="https://f-droid.org/app/com.rama.tui">
    <img src="https://rama-io.github.io/img/fdroid.png" alt="Get tui From F-Droid">
  </a>
  &nbsp;
  <a href="https://github.com/rama-io/tui/releases/latest">
    <img src="https://rama-io.github.io/img/github.png" alt="Get tui From GitHub">
  </a>
</p>

---

## License

**Tūī** is Free Software. You are free to use, study, share, and improve it under the terms of the
**GNU General Public License v3** or later.

---

## Tested Devices

| Device       | OS         | Year | Status     |
|--------------|------------|------|------------|
| Pixel 8 Pro  | Android 16 | 2026 | ✅ Verified |
| Pixel 6      | GrapheneOS | 2026 | ✅ Verified |
| Samsung On 5 | Android 6  | 2015 | ✅ Verified |

---

## Documents

- [Branding](./docs/branding.md)
- [Attributions](./docs/attributions.md)