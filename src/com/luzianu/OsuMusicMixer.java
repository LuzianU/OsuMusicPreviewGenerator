package com.luzianu;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class OsuMusicMixer {
    final AudioFormat AUDIO_FORMAT = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                                                     44100,
                                                     16,
                                                     2,
                                                     4,
                                                     44100,
                                                     false);

    int[] tempBytes;
    byte[] finalBytes;
    HashMap<String, WavFile> wavMap;
    HashMap<String, List<Integer>> timingMap;

    private final static String tempFolderName = "OsuMusicMixer";

    private int peak = 0;

    private boolean skipAlreadyGeneratedMaps;

    final static boolean VERBOSE = false;

    public OsuMusicMixer(boolean skipAlreadyGeneratedMaps) {
        this.skipAlreadyGeneratedMaps = skipAlreadyGeneratedMaps;
    }

    public boolean generateAllAudioFromOsuFolder(File osuFolder, File outputFolder) throws UnsupportedAudioFileException, IOException, InterruptedException {
        return generateAllAudioFromOsuFolder(osuFolder, outputFolder, 128, 0);
    }

    public boolean generateAllAudioFromOsuFolder(File osuFolder, File outputFolder, int quality, int volume) throws IOException, UnsupportedAudioFileException, InterruptedException {
        System.out.println("\ngenerating from " + osuFolder.getName());
        wavMap = new HashMap<>();
        timingMap = new HashMap<>();

        List<String> wavFileNames = new ArrayList<>();

        String[] possibleOsuFileNames = osuFolder.list((dir, name) -> name.endsWith(".osu"));
        List<String> osuFileNames = new ArrayList<>();
        if(possibleOsuFileNames != null) {
            for (String osuFileName : possibleOsuFileNames) {
                File osuFile = Paths.get(osuFolder.getCanonicalPath(), osuFileName).toFile();

                if (!osuFile.exists()) {
                    System.err.println("could not find a .osu file in " + osuFolder.getCanonicalPath());
                    continue;
                }

                if (VERBOSE)
                    System.out.println("reading osu file " + osuFile.getName());

                if (skipAlreadyGeneratedMaps) {
                    if (Paths.get(osuFolder.getCanonicalPath(), osuFileName.replaceAll("\\.osu$", ".mp3")).toFile().exists()) {
                        if (VERBOSE)
                            System.out.println("skip due to generated mp3 already existing in this folder");
                        continue;
                    }
                }

                try {
                    for (String name : readOsuFile(osuFile, osuFolder, false)) {
                        if (!wavFileNames.contains(name))
                            wavFileNames.add(name);
                    }

                    osuFileNames.add(osuFileName);
                } catch (RuntimeException e) {
                    if (VERBOSE)
                        System.out.println(osuFileName + ": " + e.getMessage());
                }
            }
        }

        if (wavFileNames.isEmpty()) {
            System.out.println("--> " + osuFolder.getName() + ": converting is not need since all osuFiles have a valid mp3");
            return false;
        }

        generateWavMap(osuFolder, wavFileNames);

        for (String osuFileName : osuFileNames) {
            try {
                File outputFile = Paths.get(outputFolder.getCanonicalPath(), osuFileName.replaceAll("\\.osu$", "") + ".mp3").toFile();

                File osuFile = Paths.get(osuFolder.getCanonicalPath(), osuFileName).toFile();
                if (VERBOSE)
                    System.out.println("doing osu file " + osuFileName);

                timingMap.clear();

                readOsuFile(osuFile, osuFolder, true);

                generateOutput(quality, volume, osuFolder, outputFile);
            } catch (Exception e) {
                System.err.println(e.getMessage());
            }
        }

        System.gc();

        deleteWavConverts(osuFolder);

        System.out.println("successfully generated music files for " + osuFolder.getName());

        return true;
    }

    public boolean generateAudioFromOsuFile(File osuFile, File outputFile) throws UnsupportedAudioFileException, IOException, InterruptedException {
        return generateAudioFromOsuFile(osuFile,
                                        Paths.get(outputFile.getCanonicalPath(), osuFile.getName().replaceAll("\\.osu$", ".mp3")).toFile(),
                                        128, 0);
    }

    public boolean generateAudioFromOsuFile(File osuFile, File outputFile, int quality, int volume) throws IOException, UnsupportedAudioFileException, InterruptedException {
        if (!getFileExtension(osuFile).equals("osu"))
            throw new IOException("specified file is not a .osu file");

        if (!osuFile.exists()) {
            System.err.println("osu file does not exist: " + osuFile.getCanonicalPath());
            return false;
        }

        File osuFolder = osuFile.getParentFile();

        if (VERBOSE)
            System.out.println("\ngenerating " + osuFolder.getName());
        wavMap = new HashMap<>();
        timingMap = new HashMap<>();

        if (VERBOSE)
            System.out.println("reading osu file " + osuFile.getName());
        List<String> wavFileNames;
        try {
            wavFileNames = readOsuFile(osuFile, osuFolder, true);
        } catch (RuntimeException e) {
            System.err.println(e.getMessage());
            return false;
        }
        generateWavMap(osuFolder, wavFileNames);

        generateOutput(quality, volume, osuFolder, outputFile);

        System.gc();

        deleteWavConverts(osuFolder);

        return true;
    }

    private void generateWavMap(File osuFolder, List<String> wavFileNames) throws IOException, InterruptedException {
        if (VERBOSE)
            System.out.println("converting " + wavFileNames.size() + " audio files to .wav");

        convertWavFiles(wavFileNames, osuFolder);

        if (VERBOSE)
            System.out.println("finished converting");

        // adding them to the wav map
        for (String name : wavFileNames) {
            String newFileName = "";
            try {
                newFileName = name.endsWith(".ogg") ? (name + ".wav") : name;
                addWav(Paths.get(System.getProperty("java.io.tmpdir"), tempFolderName, osuFolder.getName(), newFileName).toFile());
            } catch (Exception e) {
                System.err.println("couldn't add wav file " + newFileName);
            }
        }
    }

    private void generateOutput(int quality, int volume, File osuFolder, File outputFile) throws IOException, InterruptedException {
        int maxLength = calculateMaxLength();

        finalBytes = new byte[maxLength];
        tempBytes = new int[maxLength / 2];

        int totalTimings = 0;

        for (String wavName : timingMap.keySet()) {
            List<Integer> list = timingMap.getOrDefault(wavName, new ArrayList<>());
            totalTimings += list.size();
        }

        if (VERBOSE)
            System.out.println("mixing " + timingMap.keySet().size() + " sounds with a total of " + totalTimings + " occurrences");

        for (String wavName : timingMap.keySet()) {
            List<Integer> list = timingMap.getOrDefault(wavName, new ArrayList<>());
            WavFile wavFile = wavMap.get(wavName);

            for (int timing : list) {
                mix(wavFile, timing);
            }
        }

        if (VERBOSE)
            System.out.println("normalizing audio output");

        normalize();

        if (VERBOSE)
            System.out.println("saving raw audio output");

        ByteArrayInputStream bis = new ByteArrayInputStream(finalBytes);
        AudioInputStream ais = new AudioInputStream(bis, AUDIO_FORMAT, finalBytes.length / AUDIO_FORMAT.getFrameSize());
        String extension = getFileExtension(outputFile);

        File tempOutput = Paths.get(System.getProperty("java.io.tmpdir"), tempFolderName, osuFolder.getName(), outputFile.getName().replaceAll(extension + "$", "temp.wav")).toFile();

        AudioSystem.write(ais, AudioFileFormat.Type.WAVE, tempOutput);

        if (VERBOSE)
            System.out.println("converting audio output via ffmpeg");

        if (!outputFile.getParentFile().exists())
            outputFile.getParentFile().mkdirs();

        String[] cmd;
        if (volume != 0) {
            cmd = new String[]{ "\"" + Main.FFMPEG.getCanonicalPath() + "\"",
                                "-y",
                                "-i",
                                "\"" + tempOutput.getName() + "\"",
                                "-b:a",
                                quality + "k",
                                "-af",
                                "\"volume=" + volume + "dB\"",
                                "\"" + outputFile.getCanonicalPath() + "\""
            };
        } else {
            cmd = new String[]{ "\"" + Main.FFMPEG.getCanonicalPath() + "\"",
                                "-y",
                                "-i",
                                "\"" + tempOutput.getName() + "\"",
                                "-b:a",
                                quality + "k",
                                "\"" + outputFile.getCanonicalPath() + "\""
            };
        }

        if (!tempOutput.exists())
            throw new RuntimeException("temp output could not be generated");
        else if (VERBOSE)
            System.out.println("temp output generated at " + tempOutput.getCanonicalPath());

        if (tempOutput.getCanonicalPath().length() >= 256) { // WHHYYYYYY WINDOWS WHY
            File idFile = Paths.get(tempOutput.getParentFile().getCanonicalPath(), Thread.currentThread().getId() + "temp.wav").toFile();
            Files.move(tempOutput.toPath(), idFile.toPath());
            if (idFile.exists()) {
                cmd[3] = idFile.getName();
                Runtime.getRuntime().exec(cmd, null, tempOutput.getParentFile()).waitFor();
                idFile.delete();
            } else
                System.err.println("file path longer than 256 characters :] thanks windows");
        } else {
            Runtime.getRuntime().exec(cmd, null, tempOutput.getParentFile()).waitFor();
        }
        tempOutput.delete();

        if (!outputFile.exists())
            throw new RuntimeException("ffmpeg could not convert the mixed audio file to .mp3");
        else if (VERBOSE)
            System.out.println("final audio output generated at " + outputFile.getCanonicalPath());

    }

    private int calculateMaxLength() {
        int maxLength = 0;
        for (String wavName : wavMap.keySet()) {
            WavFile wavFile = wavMap.get(wavName);

            List<Integer> list = timingMap.getOrDefault(wavName, new ArrayList<>());
            int max = list.stream().mapToInt(v -> v).max().orElse(0);

            maxLength = Math.max(maxLength, max + wavFile.getBuffer().length);
        }
        return maxLength;
    }

    private void deleteWavConverts(File osuFolder) {
        File wavFolder = Paths.get(System.getProperty("java.io.tmpdir"), tempFolderName, osuFolder.getName()).toFile();

        if (wavFolder.exists()) {
            File[] files = wavFolder.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }

            boolean delSuccess = wavFolder.delete();
            if (!delSuccess) {
                files = wavFolder.listFiles();

                // order is important
                wavFolder.deleteOnExit();
                if (files != null) {
                    for (File f : files) {
                        f.deleteOnExit();
                    }
                }
            }
        }
    }

    private void convertWavFiles(List<String> wavFileNames, File osuFolder) throws IOException, InterruptedException {
        List<String> cmds = new ArrayList<>();
        cmds.add("SETLOCAL EnableExtensions");
        cmds.add("for /F \"tokens=4\" %%G in ('chcp') do set \"_chcp=%%G\"");
        cmds.add(">NUL chcp 65001");
        for (String name : wavFileNames) {
            String outputFile = name.endsWith(".ogg") ? (name + ".wav") : name;

            cmds.add("\"" + Main.FFMPEG.getCanonicalPath() +
                     "\" -y -i \"" + osuFolder.getCanonicalPath().replace("%", "%%") + "\\" + name.replace("%", "%%") + "\"" +
                     " -acodec pcm_s16le -ar 44100 -ac 2 "
                     + "\"" + Paths.get(System.getProperty("java.io.tmpdir"), tempFolderName, osuFolder.getName()).toFile().getCanonicalPath().replace("%", "%%") + "\\" + outputFile.replace("%", "%%") + "\"");
        }
        cmds.add("copy /b NUL \"" + Paths.get(System.getProperty("java.io.tmpdir"), tempFolderName, osuFolder.getName()).toFile().getCanonicalPath().replace("%", "%%") + "\\done\"");
        cmds.add(">NUL chcp %_chcp%");
        cmds.add("exit");

        FileWriter writer = new FileWriter("convert" + Thread.currentThread().getId() + ".bat");
        for (String str : cmds) {
            writer.write(str + System.lineSeparator());
        }
        writer.close();

        deleteWavConverts(osuFolder);
        Paths.get(System.getProperty("java.io.tmpdir"), tempFolderName, osuFolder.getName()).toFile().mkdirs();

        File doneFile = Paths.get(System.getProperty("java.io.tmpdir"), tempFolderName, osuFolder.getName(), "done").toFile();
        if (doneFile.exists())
            doneFile.delete();

        Runtime.getRuntime().exec("cmd /c start /min convert" + Thread.currentThread().getId() + ".bat");

        // scuffed, thanks java
        while (!doneFile.exists()) {
            // System.out.println("sleep");
            Thread.sleep(500);
        }

        new File("convert" + Thread.currentThread().getId() + ".bat").deleteOnExit();
        doneFile.delete();
    }

    private List<String> readOsuFile(File osuFile, File osuFolder, boolean addToTiming) throws IOException, RuntimeException {
        if (osuFile.getName().startsWith("- - music - -")) {
            throw new RuntimeException("osu music file was already created by this program");
        }

        List<String> wavFileNames = new ArrayList<>();

        List<String> lines = Files.readAllLines(osuFile.toPath(), StandardCharsets.UTF_8);
        HashMap<String, Integer> errorMap = new HashMap<>();

        boolean isHitObjects = false;
        for (String line : lines) {
            if (line.trim().isEmpty())
                continue;
            try {
                if (line.startsWith("AudioFilename:")) {
                    String audioFilename = line.replaceAll("^AudioFilename:", "").trim();
                    File audioFile = Paths.get(osuFolder.getCanonicalPath(), audioFilename).toFile();

                    if (audioFile.exists())
                        throw new RuntimeException("osu file already has a valid mp3");
                }

                String[] arr = line.split(",");
                if (arr.length >= 5) {
                    String wav = arr[3].replace("\"", "");
                    if (wav.endsWith(".ogg") || wav.endsWith(".wav")) {
                        int timing = Integer.parseInt(arr[1]);
                        // why
                        if (!Paths.get(osuFolder.getCanonicalPath(), wav).toFile().exists()) {
                            String initialWav = wav;
                            if (wav.endsWith(".wav")) {
                                wav = wav.replaceAll("\\.wav$", ".ogg");
                            } else if (wav.endsWith(".ogg")) {
                                wav = wav.replaceAll("\\.ogg$", ".wav");
                            }
                        }

                        if (Paths.get(osuFolder.getCanonicalPath(), wav).toFile().exists()) {
                            if (!wavFileNames.contains(wav))
                                wavFileNames.add(wav);

                            if (wav.endsWith(".ogg"))
                                wav += ".wav";

                            if (addToTiming)
                                addTiming(wav, timing);
                        } else {
                            if (addToTiming)
                                errorMap.put(wav, errorMap.getOrDefault(wav, 0) + 1);

                        }
                    }
                }

                if (line.startsWith("[HitObjects]")) {
                    isHitObjects = true;
                    continue;
                }

                if (isHitObjects) {
                    String[] wavs = line.split(":");
                    String wav = wavs[wavs.length - 1];
                    //String wav = line.replaceAll("^" + line.substring(0, line.lastIndexOf(":") + 1), "").trim();

                    String timingStr = line.replaceAll("^" + line.substring(0, line.indexOf(",") + 1), "");

                    timingStr = timingStr.replaceAll("^" + timingStr.substring(0, timingStr.indexOf(",") + 1), "");
                    timingStr = timingStr.substring(0, timingStr.indexOf(","));

                    if (!wav.isEmpty()) {
                        int timing = Integer.parseInt(timingStr);

                        // why
                        if (!Paths.get(osuFolder.getCanonicalPath(), wav).toFile().exists()) {
                            if (wav.endsWith(".wav")) {
                                wav = wav.replaceAll("\\.wav$", ".ogg");
                            } else if (wav.endsWith(".ogg")) {
                                wav = wav.replaceAll("\\.ogg$", ".wav");
                            }
                        }

                        if (Paths.get(osuFolder.getCanonicalPath(), wav).toFile().exists()) {
                            if (!wavFileNames.contains(wav))
                                wavFileNames.add(wav);

                            if (wav.endsWith(".ogg"))
                                wav += ".wav";

                            if (addToTiming)
                                addTiming(wav, timing);
                        } else {
                            if (addToTiming)
                                errorMap.put(wav, errorMap.getOrDefault(wav, 0) + 1);
                        }

                    }
                }

            } catch (RuntimeException re) {
                for (String wav : errorMap.keySet())
                    System.err.println(osuFile.getName() + ": couldn't find " + wav + " [" + errorMap.get(wav) + "x]");
                throw re;
            } catch (Exception e) {
                if (VERBOSE)
                    System.out.println(line);
                e.printStackTrace();
            }
        }

        for (String wav : errorMap.keySet())
            System.err.println(osuFile.getName() + ": couldn't find " + wav + " [" + errorMap.get(wav) + "x]");

        return wavFileNames;
    }

    private void addWav(File file) throws UnsupportedAudioFileException, IOException {
        WavFile wavFile = new WavFile(file);
        wavMap.put(file.getName(), wavFile);
    }

    private void addTiming(String wavName, int ms) {
        if (timingMap.containsKey(wavName)) {
            List<Integer> list = timingMap.get(wavName);
            if (!list.contains(toByteOffset(ms)))
                list.add(toByteOffset(ms));
        } else {
            List<Integer> list = new ArrayList<>();
            list.add(toByteOffset(ms));
            timingMap.put(wavName, list);
        }
    }

    private int toByteOffset(int ms) {
        try {
            int val = (int) (ms / 4000.0 * 705600);
            if (val % 2 != 0)
                val -= 1;
            return val;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return 0;
    }

    private void mix(WavFile wavFile, int offset) {
        if (wavFile == null)
            return;
        // System.out.println("mixing " + wavFile.getFile().getName() + " at " + offset + " (" + ((4.0 * offset) / 705600) + " s)");

        for (int i = 0; i < wavFile.getBuffer().length; i += 2) { // += 2 since it's the converted data is in 16bit le format
            int a = tempBytes[offset / 2 + i / 2];  // tempBytes is int[] and not byte[] thus only half the size of the final byte array ==> /2

            short buf1B = wavFile.getBuffer()[i + 1]; // wavFile is in little endian format
            short buf2B = wavFile.getBuffer()[i];
            buf1B = (short) ((buf1B & 0xff) << 8); // basically get the integer value of every two byte array entries
            buf2B = (short) (buf2B & 0xff);
            int b = buf1B | buf2B;

            tempBytes[offset / 2 + i / 2] = a + b; // again /2 because every two entries get combined to a single int entry
            peak = Math.max(peak, Math.abs(a + b)); // peak is used to normalize the wav file after the mixing is done so there is no clipping
        }
    }

    private void normalize() {
        if (peak == 0) { // return otherwise dividing by zero
            System.err.println("peak is 0 :(");
            return;
        }

        for (int offset = 0; offset < tempBytes.length; offset++) { // ++ here since we loop over the int array
            int res = tempBytes[offset];

            res = (int) (res / (double) peak * Short.MAX_VALUE); // adjust every value so no clipping happens, basically the highest value
            // of the final array is +-Short.MAX_VALUE
            res = Math.min(Math.max(res, Short.MIN_VALUE), Short.MAX_VALUE); // just in case
            finalBytes[2 * offset] = (byte) res; // put the int value of tempBytes back to the 16 bit le format of the final wav byte data
            finalBytes[2 * offset + 1] = (byte) (res >> 8);
        }
    }

    public String getFileExtension(File file) {
        String extension = "";
        {
            int index = file.getName().lastIndexOf('.');
            if (index > 0) {
                extension = file.getName().substring(index + 1);
            }
        }
        return extension;
    }
}
