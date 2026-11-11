# J2ME Infinity Loader

[Español](README.md) | **English**

J2ME Infinity Loader is a J2ME (Java ME games and apps) emulator for Android. It is a fork of [J2ME-Loader](https://github.com/nikita36078/J2ME-Loader), so it keeps everything that makes it good: it supports most 2D and 3D games (including Mascot Capsule 3D ones), has a virtual keyboard, individual settings for each application and screen scaling.

On top of that it adds improvements for playing on a modern phone, taken from other forks and from [JarNix](https://github.com/ZenithUzu/JarNix-Java-ME-Porting-Toolkit).

## What this version adds

All new options live in **each game's settings**, just like the original J2ME Loader ones.

- **Force multitouch** (*Controls → Force multitouch*): every finger reaches the game separately, even if the game does not declare it in its manifest. For example, you can move and jump at the same time with the game's own touch controls. Off by default, because some single-touch games get confused by a second finger.
- **Rotate image 90° / 270°** (*Screen → Rotate image*): for games played sideways. The image is rotated in all three drawing modes (software, hardware and OpenGL), and touches are rotated too so they land in the right place. Combine it with *Landscape* orientation.
- **Supersampling** (*Fonts → Supersampling*): the game is drawn 2× to 10× larger and scaled down for display, so text and graphics look sharp instead of blurry. *Auto* picks the factor from your screen and lowers it if memory runs out. Covers 3D too (M3G and Mascot Capsule). Taken from [J2ME-Loader-HD](https://github.com/fuyijian/J2ME-Loader-HD).
- **Integer scaling** (*Screen → Scale type*): each game pixel becomes exactly N×N screen pixels; the sharpest image possible. There is also *16:9*, which widens the game toward the screen but never past 16:9 (fills almost everything, distorts little).
- **Anti-flicker** (*Screen*): blends each frame with the previous one. Fixes games that fake transparency with alternating lines or dots, which flicker on a modern screen.
- **Auto fit** (*Screen*): if a game only draws part of the screen (e.g. 176×208 inside 240×320), only that part is shown. It waits about 12 seconds so a logo does not fool it.
- **Game speed 0.25×–16×** (in-game menu → *Game speed*): speeds the game up or slows it down. Games installed before this version must be reinstalled.
- **On-screen gamepad** (in-game menu → keyboard → *Gamepad*): 8-way d-pad, stick, A B X Y, L and R, Select and Start. Move, resize and remap it with the usual editor. It is drawn with a PNG skin; pick your own folder or .zip in *Settings → Gamepad skin*, Citra skins work too.
- **Physical controllers work out of the box**: Xbox, PlayStation and generic pads need no setup (A = OK, B = 7, X = 9, Y = 0, L = \*, R = #, Select/Start = soft keys), and the left stick works like the d-pad. Using a physical controller or keyboard hides the on-screen keys; touching the screen brings them back.
- **Collections** (main menu and each game's menu): group your games and show only one collection. Includes *★ Favorites* and *Recent* (the last 20 you opened). Removing a game from a collection does not uninstall it.
- **Vibration you can feel**: on Android 12+ (HyperOS, One UI) vibration is marked as game use so the system does not mute it.
- **Uses the notch area** by default (can be turned off in Settings).
- **Widescreen** (*Screen → size list → Widescreen*): like JarNix, keeps the game's height and gives it more room on the sides up to 16:9, 18:9, 19.5:9, 20:9, 21:9 or your phone's proportion, without stretching. Works with games that adapt to the screen size; games with a fixed background or interface may show empty areas.
- **Automatic image rotation**: if almost everything a game draws is sideways, the image is turned, the app goes landscape and it is remembered. Default for new games.
- **Memory editor** (in-game menu): find a game number (lives, money…) by value or by how it changed, then change or freeze it. A simple version: results are candidates, try which one is right.
- **Performance overlay**: the *Show FPS* option now also shows memory and CPU.
- **Sharp screenshots**: cut to the auto fit area, turned like the screen and enlarged without blur.
- **Refresh rate that follows the game** (Android 11+): the display runs at 60, 90 or 120 Hz depending on the game's FPS, to save battery.
- **Errors without closing the app**: if a game thread fails, a message is shown instead of closing everything.
- **Ask for the keyboard**: games made for touch ask once whether to show the on-screen keyboard.
- **Export and import a game** (game menu / main menu): one .zip with the game, its settings and saves, to move it to another phone.
- **Change icon** (each game's menu): use any picture as the game's icon in the list, or go back to the original; the game is not modified.
- **Full controller mapping**: L2/R2 (also as analog triggers), L3/R3 and the right stick can be assigned in *Map keys*, and a game key can hold a keyboard key and a controller button at once. Game vibration also shakes the controller.
- **Volume keys**: inside a game they change the game's volume, not the ringer.
- **Check for updates** (main menu): looks at the versions published on GitHub and opens the download. It can also tell you on start (at most once a day; can be turned off in Settings).
- **Bundled screen filters** (OpenGL ES graphics mode → *Filter*): xBR, MMPX, AANN, CRT Lottes, dot matrix (from RetroArch) and JarNix's sharp bilinear, CRT, scanlines, LCD, green LCD, vivid color and amber. For effects, *Suavizar efecto* lowers the strength. Besides PPSSPP shaders (.ini + .fsh), RetroArch .glsl files work too, and can be imported from *Settings → Import screen filters* (folder or .zip).
- **Full Spanish text support (ñ, accents, ¿ ¡)**: each game's text is read in the right encoding with no setup: UTF-8 when it is UTF-8, otherwise Latin-1 (windows-1252). So `ñ` no longer shows as `Ã±` or vanishes, in the game and in the library name. Games installed before must be reinstalled. If a game draws text with its own picture font that has no ñ, the emulator cannot fix it.
- **JarNix-style keypad** (default): d-pad with OK on the left, numbers on the right and soft keys above, dark rounded keys with a white border and bold labels. Move, resize, hide, remap, colors and opacity work as always. Games with a saved layout keep it.
- **Switch between gamepad and keys in game**: the *Gamepad* key (on the keypad) or *123* (on the gamepad) switches instantly, also from the game menu → *Virtual keyboard* → *Gamepad ↔ keys*. Each one remembers its own layout, even when edited.
- **Quick settings** (game menu → *Quick settings*, with the back button): applied at once, without leaving the game, and saved for next time. Show or hide the on-screen keys (or turn the keyboard on if the game had it off), switch gamepad/keys, fit the image to the screen, pick a filter, smooth the image and hide or show the top bar.
- **Centered screen without controls** (like JarNix): with no keys on screen (off, hidden, or hidden while using a physical controller) the game sits in the middle. When the keys come back it moves to the top to make room.
- **Install from ZIP**: pick a .zip and the games inside are installed. When installing several, a game that already exists asks whether to replace or skip it (or all).
- **Library grid** (Settings): icon size, 3:4 cover-like icons and hidden titles.
- **Sound without stutter**: sounds are prepared on their own thread, so the game no longer freezes each time an effect plays.
- **Exact FPS limit**: 30 FPS gives 30, not ~27.
- **Lower touch delay** (Android 11+) and a **black background** by default where the game draws nothing.
- **LG keys**: besides Siemens, Motorola and Nokia E62, LG soft key codes can be used too.
- **Install a folder** (main menu): installs every game in a folder and its subfolders at once.
- **Silent in the background**: music pauses when you leave the game and resumes when you come back.
- **New icon**: an ∞ with JL above and 2ME below, in a gradient from J2ME Loader's indigo to JL-Mod's teal. On Android 13+ it also works as a themed icon (takes your wallpaper color).
- **Installs next to the original**: it has its own app id, so it does not replace J2ME Loader.
- **Installs games built with modern Java**: ports compiled with a recent JDK no longer fail with `Dexing error`.
- **Less invasive permissions**: no "All files access"; storage works like in the original J2ME Loader, and legacy storage and Bluetooth permissions are only declared on the Android versions that use them.

System requirements: the same as the original J2ME Loader.

## Compatibility

The original project's tested game lists are still a good reference:

- [Tested games (touchscreen)](https://github.com/nikita36078/J2ME-Loader/wiki/List-of-Tested-Java-Games-(Touchscreen))
- [Tested games (non touchscreen)](https://github.com/nikita36078/J2ME-Loader/wiki/List-of-Tested-Java-Games-(Non-Touchscreen))
- [Games with bugs](https://github.com/nikita36078/J2ME-Loader/wiki/List-of-Java-Games-with-Bugs)

## Tips

- Enabling filtering can greatly reduce performance in some cases. Disable it if a game is too slow.
- Image flickering can be fixed by enabling the "Immediate processing mode" option.
- If a touch game ignores a second finger, enable *Force multitouch* in its settings.
- Supersampling uses more memory. If a game is slow or closes, lower it or set it to *Off*.

## Download the test APK

Every change pushed to GitHub builds a test APK: open the repository's **Actions** tab, open the latest *Build test APK* run and download **J2ME-Infinity-Loader-debug** under *Artifacts*.

## Building

Open the project in Android Studio and build the variant you want, or use the command line:

```bash
./gradlew assembleOpenDebug     # "open" debug build
```

## Screenshots

<img src="/screenshots/screen.jpg" width="288" height="512"> <img src="/screenshots/screen2.jpg" width="288" height="512">
<img src="/screenshots/screen3.jpg" width="288" height="512"> <img src="/screenshots/screen4.jpg" width="288" height="512">

## Credits

- [nikita36078/J2ME-Loader](https://github.com/nikita36078/J2ME-Loader), the base of this project, itself a fork of [J2meLoader](https://github.com/NaikSoftware/J2meLoader).
- [woesss](https://github.com/woesss), author of [JL-Mod](https://github.com/woesss/JL-Mod), for the open-source Mascot Capsule implementation, soundbank support and many other improvements.
- [fuyijian/J2ME-Loader-HD](https://github.com/fuyijian/J2ME-Loader-HD), for supersampling and the install fix.
- [H3nb/JL-Mod-Plus](https://github.com/H3nb/JL-Mod-Plus), as a reference for controls and library features.
- [JarNix](https://github.com/ZenithUzu/JarNix-Java-ME-Porting-Toolkit), where the multitouch and rotation ideas come from.

## License

> Copyright 2017-2026 Nikita Shakarun and contributors.
> Licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).
> (See the [LICENSE](LICENSE) file for the whole license text.)
