# J2ME Infinity Loader

[Español](README.md) | **English**

J2ME Infinity Loader is a J2ME (Java ME games and apps) emulator for Android. It is a fork of [J2ME-Loader](https://github.com/nikita36078/J2ME-Loader), so it keeps everything that makes it good: it supports most 2D and 3D games (including Mascot Capsule 3D ones), has a virtual keyboard, individual settings for each application and screen scaling.

On top of that it adds improvements for playing on a modern phone, taken from other forks and from [JarNix](https://github.com/ZenithUzu/JarNix-Java-ME-Porting-Toolkit).

## What this version adds

All new options live in **each game's settings**, just like the original J2ME Loader ones.

- **Force multitouch** (*Controls → Force multitouch*): every finger reaches the game separately, even if the game does not declare it in its manifest. For example, you can move and jump at the same time with the game's own touch controls. Off by default, because some single-touch games get confused by a second finger.
- **Rotate image 90° / 270°** (*Screen → Rotate image*): for games played sideways. The image is rotated in all three drawing modes (software, hardware and OpenGL), and touches are rotated too so they land in the right place. Combine it with *Landscape* orientation.
- **Supersampling** (*Fonts → Supersampling*): the game is drawn 2× to 10× larger and scaled down for display, so text and graphics look sharp instead of blurry. *Auto* picks the factor from your screen and lowers it if memory runs out. Covers 3D too (M3G and Mascot Capsule). Taken from [J2ME-Loader-HD](https://github.com/fuyijian/J2ME-Loader-HD).
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
