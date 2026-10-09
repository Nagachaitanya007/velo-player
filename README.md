# Lumen

A quiet Android video player. The screen gets out of the way. The engine is [libVLC](https://code.videolan.org/videolan/vlc-android) 3.7, so it opens the same kinds of files VLC does, including MKV, and uses the phone’s hardware decoder when the device has one.

Files stay on the phone. Nothing is uploaded.

## Install a test build

Open the latest Release and download `Lumen.apk` (or `Lumen-test.apk`). Open the file on an Android phone. Android will warn that the developer is unknown. That is expected for a test install.

The first build is signed for sideloading, not for the Play Store.

## While you watch

- Tap to show the controls. They leave on their own.
- Double-tap the sides to skip 10 seconds. Double-tap the middle to play or pause.
- Hold for 2×. Swipe the left side for screen brightness, the right side for volume, or sideways to scrub.
- Adjust is where picture, sound, subtitles, speed, chapters, A–B loop, and sleep live.

libVLC is LGPL-2.1. This project’s source is published so you can relink against it. See VideoLAN’s license for the library itself.
