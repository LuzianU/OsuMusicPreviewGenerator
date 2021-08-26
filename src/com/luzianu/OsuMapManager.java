package com.luzianu;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class OsuMapManager {
    public static void manageFolder(File input, File outputFolder, int hitsoundVolume) throws IOException {
        String[] osuFiles;
        if (input.isDirectory())
            osuFiles = input.list((dir, name) -> name.endsWith(".osu") && !name.startsWith("- - music - -"));
        else
            osuFiles = new String[]{ input.getName() };

        int counter = 0;
        if(osuFiles != null) {
            for (String osuFileString : osuFiles) {
                File osuFile;
                if (input.isDirectory())
                    osuFile = Paths.get(input.getCanonicalPath(), osuFileString).toFile();
                else
                    osuFile = Paths.get(input.getCanonicalPath()).toFile();

                try {
                    if (manageFile(osuFile, outputFolder, hitsoundVolume))
                        counter++;
                } catch (RuntimeException e) {
                    System.err.println(osuFileString + ": osu file already has a valid mp3");
                }
            }

            System.out.println(input.getName() + ": " + counter + "/" + osuFiles.length + " maps generated");
        }
    }

    public static boolean manageFile(File osuFile, File outputFolder, int hitsoundVolume) throws IOException, RuntimeException {
        File osuFolder = osuFile.getParentFile();

        List<String> lines = Files.readAllLines(osuFile.toPath());

        boolean isGeneral = false;
        int indexOfGeneral = -1;
        for (String line : lines) {
            if (line.trim().isEmpty())
                continue;

            if (isGeneral && line.startsWith("[")) {
                break;
            }

            if (line.startsWith("AudioFilename:")) {
                String audioFilename = line.replaceAll("^AudioFilename:", "").trim();
                File audioFile = Paths.get(osuFolder.getCanonicalPath(), audioFilename).toFile();

                if (audioFile.exists())
                    throw new RuntimeException("osu file already has a valid mp3");
                else {
                    lines.remove(line);
                    break;
                }
            }

            if (line.startsWith("[General]")) {
                indexOfGeneral = lines.indexOf(line);
                isGeneral = true;
                continue;
            }
        }
        lines.removeIf(s -> s.startsWith("PreviewTime:"));
        lines.removeIf(s -> s.startsWith("AudioFilename:"));

        if (indexOfGeneral == -1)
            throw new RuntimeException("osu file might be corrupt");

        String audioFilename = osuFile.getName().replaceAll("\\.osu$", ".mp3");
        lines.add(indexOfGeneral + 1, "AudioFilename: " + audioFilename);

        File musicOsuFile = Paths.get(outputFolder.getCanonicalPath(), "- - music - -" + osuFile.getName()).toFile();
        if (Paths.get(osuFolder.getCanonicalPath(), audioFilename).toFile().exists() ||
            Paths.get(outputFolder.getCanonicalPath(), audioFilename).toFile().exists())
            saveAsMusic(musicOsuFile, lines, hitsoundVolume);
        else {
            System.err.println("audio file does not exist to save as music osu file");
            return false;
        }
        return true;
    }

    private static void saveAsMusic(File osuFile, List<String> oldLines, int hitsoundVolume) throws IOException {
        boolean isHitObjects = false;
        boolean isTimingPoints = false;
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < oldLines.size(); i++) {
            String line = oldLines.get(i);
            if (line.trim().isEmpty()) {
                lines.add(line);
                continue;
            }
            try {
                if (line.startsWith("[")) {
                    isHitObjects = false;
                    isTimingPoints = false;
                }

                String[] arr = line.split(",");
                if (arr.length >= 5) {
                    String wav = arr[3].replace("\"", "");
                    if (wav.endsWith(".ogg") || wav.endsWith(".wav")) {
                        continue; // don't add to lines
                    }
                }

                if (line.startsWith("[HitObjects]")) {
                    isHitObjects = true;
                    lines.add(line); // add to lines before continuing
                    continue;
                }

                if (line.startsWith("[TimingPoints]")) {
                    isTimingPoints = true;
                    lines.add(line); // add to lines before continuing
                    continue;
                }

                if (isHitObjects) {
                    if (line.contains(":")) {
                        lines.add(line.substring(0, line.indexOf(":")) + ":0:0:0:0");
                        continue;
                    }
                }

                if (isTimingPoints) {
                    arr = line.split(",");
                    if (arr.length >= 8) {
                        arr[5] = "" + hitsoundVolume;
                        StringBuilder str = new StringBuilder();
                        for (int x = 0; x < arr.length - 1; x++)
                            str.append(arr[x] += ",");
                        str.append(arr[arr.length - 1]);
                        lines.add(str.toString());

                        continue;
                    }
                }

                if (line.startsWith("Version:"))
                    lines.add(line + " [music]");
                else
                    lines.add(line);
            } catch (Exception e) {
                lines.add(line);
                System.out.println(line);
                e.printStackTrace();
            }
        }
        writeToFile(osuFile, lines);
    }

    private static void writeToFile(File file, List<String> lines) throws IOException {
        if (!file.getParentFile().exists())
            file.getParentFile().mkdirs();

        FileWriter writer = new FileWriter(file);
        for (String line : lines) {
            writer.write(line + System.lineSeparator());
        }
        writer.close();
    }

}
