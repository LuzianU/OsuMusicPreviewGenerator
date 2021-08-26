# OsuMusicPreviewGenerator
Generate audio previews for your fully keysounded osu! beatmaps. Primarily useful for BMS converts but also useful for converting them to .mp3

https://user-images.githubusercontent.com/52568586/131014458-53f0ba76-4d01-4612-8221-5d660c742a2b.mp4

# Requirements
- Java version 8 or above
- FFmpeg installed or ffmpeg.exe in the same folder as this application's [latest release](https://github.com/LuzianU/OsuMusicPreviewGenerator/releases/latest)

# How to run/install
If you meet the requirements you should be able to just open the OsuMusicPreviewGenerator.jar

If that does not work you probably don't hava java installed. You can download it [here](https://adoptopenjdk.net/?variant=openjdk8&jvmVariant=hotspot). When running the installer make sure to also select the option `Set JAVA_HOME variable`.

# Generating the music preview maps
- Select your osu! songs folder as input. The program will recursivly check every subfolder for your .osu beatmaps
- Select an empty folder as output, preferably on your fastest drive
- Press the Generate button
- Wait... This might take a while - just let it run in the background
- Once it's done you can copy all folders from your output folder to your osu! songs folder. There should be no need to overwrite any files
- Do a rescan of your osu! maps

# Help
Feel free to let [me](https://osu.ppy.sh/users/7350956) know if you need any help

# Licenses
This project uses the following projects:

[FlatLaf](https://github.com/JFormDesigner/FlatLaf),[ licensed under Apache License 2.0](https://github.com/JFormDesigner/FlatLaf/blob/main/LICENSE)
