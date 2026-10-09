# Velo

A quiet video player for Android, Windows, and Mac. The screen gets out of the way. Playback uses [libVLC](https://code.videolan.org/videolan/vlc-android), so it opens the same kinds of files VLC does, including MKV.

Files stay on the device. Nothing is uploaded.

## Android

Open the latest Release and download `Velo.apk`. Open the file on an Android phone. Android will warn that the developer is unknown. That is expected for a test install.

The build is signed for sideloading, not for the Play Store.

## Windows and Mac

The desktop build is the same player, not the Android app in a wrapper. The download includes VLC's engine.

- [Velo-windows.exe](https://github.com/Nagachaitanya007/velo-player/releases/download/v1.0.0/Velo-windows.exe)
- [Velo-mac.dmg](https://github.com/Nagachaitanya007/velo-player/releases/download/v1.0.0/Velo-mac.dmg) for Apple silicon

An Intel Mac build is not in this release. Apple silicon and Windows are.

On a Mac, if the system blocks the app, right-click Velo and choose Open. The first build is not notarized.

## While you watch

- Tap or click to show the controls. They leave on their own.
- Double-tap or double-click the sides to skip 10 seconds. The middle plays or pauses.
- Hold for 2× on Android. On a computer, use the arrow keys: left and right seek, up and down change volume. F is fullscreen.
- Adjust is where picture, sound, subtitles, speed, and the equalizer live.

The desktop build links [vlcj](https://github.com/caprica/vlcj) (GPL-3.0) and libVLC (LGPL-2.1). This repository is the corresponding source.
