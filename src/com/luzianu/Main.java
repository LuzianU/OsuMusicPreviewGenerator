package com.luzianu;

import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.*;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class Main {
    public static final String GIT_HUB_LATEST_URL = "https://github.com/LuzianU/OsuMusicPreviewGenerator/releases/latest";
    public static final String GIT_HUB_DOWNLOAD_BASE_URL = "https://github.com/LuzianU/OsuMusicPreviewGenerator/releases/download/";
    private static final String VERSION = "v1.4";

    public static File FFMPEG = null;

    public static void generate(File input, File output, boolean multiThreaded,
                                boolean skipAlreadyGeneratedMaps, int hitsoundVolume) {
        try {
            if (!input.exists())
                throw new IllegalArgumentException("File does not exist: " + input.getCanonicalPath());
            if (!output.exists())
                throw new IllegalArgumentException("File does not exist: " + output.getCanonicalPath());

            final File[] folders = input.isDirectory() ? input.listFiles((dir, name) -> dir.isDirectory()) : null;
            int amountOfFiles = 1;
            if (input.isDirectory()) {
                if (folders != null)
                    amountOfFiles = folders.length;
            }

            final int[] current = { 1 };
            int finalAmountOfFiles = amountOfFiles + 1;

            Consumer<File> consumer = (folder -> {
                if (folder.isDirectory()) {
                    UserInterface.updateProgress(current[0], finalAmountOfFiles);

                    System.out.println();
                    System.out.println(current[0] + " / " + finalAmountOfFiles);
                    System.out.println();

                    boolean isRoot = false;
                    try {
                        isRoot = folder.getCanonicalPath().equals(input.getCanonicalPath());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    try {
                        doGen(folder, output, skipAlreadyGeneratedMaps, hitsoundVolume, isRoot);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
                current[0]++;
            });

            if (folders != null && input.isDirectory()) {
                Arrays.stream((new File[]{ input })).forEach(consumer);

                if (multiThreaded) {
                    AtomicInteger atomicInteger = new AtomicInteger(0);
                    int n = Runtime.getRuntime().availableProcessors() - 1;

                    Runnable r = () -> {
                        int index;
                        while ((index = atomicInteger.getAndIncrement()) < folders.length) {
                            File folder = folders[index];
                            if (folder.isDirectory()) {
                                UserInterface.updateProgress(current[0], finalAmountOfFiles);

                                System.out.println();
                                System.out.println("[" + Thread.currentThread().getId() + "]\t" + current[0] + " / " + finalAmountOfFiles);
                                System.out.println();

                                boolean isRoot = false;
                                try {
                                    isRoot = folder.getCanonicalPath().equals(input.getCanonicalPath());
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }

                                try {
                                    doGen(folder, output, skipAlreadyGeneratedMaps, hitsoundVolume, isRoot);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }

                            }
                            current[0]++;
                        }
                    };

                    Thread[] threads = new Thread[n];
                    for (int i = 0; i < n; i++) {
                        threads[i] = new Thread(r);
                        threads[i].start();
                    }
                    for (int i = 0; i < n; i++) {
                        try {
                            threads[i].join();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    //Arrays.stream(folders).parallel().forEach(consumer);
                } else
                    Arrays.stream(folders).forEach(consumer);
            } else {
                try {
                    UserInterface.updateProgress(current[0], finalAmountOfFiles);

                    File outputFolder = Paths.get(output.getCanonicalPath(), input.getName()).toFile();

                    if (new OsuMusicMixer(skipAlreadyGeneratedMaps).generateAudioFromOsuFile(input, outputFolder))
                        OsuMapManager.manageFolder(input, outputFolder, hitsoundVolume);

                    current[0]++;

                } catch (UnsupportedAudioFileException | IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }

            UserInterface.updateProgress(finalAmountOfFiles, finalAmountOfFiles);
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("generation done");
    }

    public static HashMap<Long, Logger> loggerMap = new HashMap<>();

    public static Logger createLogger() {
        new File("logs").mkdirs();
        Logger logger = Logger.getLogger("Thread" + Thread.currentThread().getId());
        FileHandler fh;
        logger.setUseParentHandlers(false);

        try {

            //This block configure the logger with handler and formatter
            fh = new FileHandler("logs\\Thread" + Thread.currentThread().getId() + ".log");
            logger.addHandler(fh);
            SimpleFormatter formatter = new SimpleFormatter();
            fh.setFormatter(formatter);

        } catch (SecurityException | IOException e) {
            e.printStackTrace();
        }

        return logger;
    }

    private static void doGen(File folder, File output, boolean skipAlreadyGeneratedMaps, int hitsoundVolume, boolean isRoot) throws IOException {
        if (!loggerMap.containsKey(Thread.currentThread().getId()))
            loggerMap.put(Thread.currentThread().getId(), createLogger());

        File[] subDirs = null;
        if (!isRoot) {
            subDirs = folder.listFiles((dir, name) -> {
                try {
                    return Paths.get(dir.getCanonicalPath(), name).toFile().isDirectory();
                } catch (IOException e) {
                    return false;
                }
            });
        }

        try {
            File outputFolder = Paths.get(output.getCanonicalPath(), folder.getName()).toFile();

            if (new OsuMusicMixer(skipAlreadyGeneratedMaps).generateAllAudioFromOsuFolder(folder, outputFolder))
                OsuMapManager.manageFolder(folder, outputFolder, hitsoundVolume);

        } catch (UnsupportedAudioFileException | IOException | InterruptedException e) {
            e.printStackTrace();
        }

        if (subDirs != null)
            for (File dir : subDirs) {
                doGen(dir, Paths.get(output.getCanonicalPath(), folder.getName()).toFile(), skipAlreadyGeneratedMaps, hitsoundVolume, false);
            }
    }

    public static void main(String[] args) {
        if (!System.getProperty("file.encoding").equals("UTF-8")) {
            JOptionPane.showConfirmDialog(null, "using " + System.getProperty("file.encoding") + " and not UTF-8. This might cause some problems...\n" +
                                                "Check the github page if the generation process gets stuck.", ":|", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        }
        System.out.println(System.getProperty("file.encoding"));
        if (!checkForUpdates())
            return;

        FFMPEG = getFfmpegFile();

        UserInterface.show();
    }

    public static void downloadFfmpeg() {
        String dlUrl = GIT_HUB_DOWNLOAD_BASE_URL + "v1.0/ffmpeg.exe";
        try (BufferedInputStream in = new BufferedInputStream(new URL(dlUrl).openStream());
             FileOutputStream fileOutputStream = new FileOutputStream("ffmpeg.exe")) {
            byte[] dataBuffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                fileOutputStream.write(dataBuffer, 0, bytesRead);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean checkForUpdates() {
        try {
            HttpURLConnection con = (HttpURLConnection) (new URL(GIT_HUB_LATEST_URL).openConnection());
            con.setInstanceFollowRedirects(false);
            con.connect();
            String location = con.getHeaderField("Location");
            if (!location.contains(GIT_HUB_LATEST_URL)) { // redirect happened
                String latestVersion = location.replace(GIT_HUB_LATEST_URL.replace("latest", "tag/"), "");

                if (!VERSION.equals(latestVersion)) {
                    int result = JOptionPane.showConfirmDialog(null, "There's a new version available! Please download it, it might have fixed some bugs.", ":]", JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE);
                    if (result == JOptionPane.OK_OPTION) {
                        UserInterface.openWebpage(GIT_HUB_LATEST_URL);
                        return false;
                    }

                    result = JOptionPane.showConfirmDialog(null, "Please.", ":[", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                    if (result == JOptionPane.OK_OPTION) {
                        UserInterface.openWebpage(GIT_HUB_LATEST_URL);
                        return false;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return true;
    }

    public static File getFfmpegFile() {
        try {
            if (new File("ffmpeg.exe").exists())
                return new File("ffmpeg.exe");

            ProcessBuilder pb = new ProcessBuilder("where", "ffmpeg");
            final Process p = pb.start();
            Scanner sc = new Scanner(p.getInputStream());

            p.waitFor();

            return new File(sc.next());
        } catch (IOException | InterruptedException | NoSuchElementException e) {
            System.err.println("ffmpeg could not be found");
        }

        return null;
    }
}
