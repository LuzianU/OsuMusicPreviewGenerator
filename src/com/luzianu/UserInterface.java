package com.luzianu;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.text.NumberFormatter;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

public class UserInterface {
    private static JFrame frame;
    private static JTextArea inputTextArea;
    private static JTextArea outputTextArea;
    private static JCheckBoxMenuItem skipAlreadyGeneratedMaps;
    private static JCheckBoxMenuItem multiThreaded;
    private static JCheckBoxMenuItem allowOutputToSongs;
    private static JFormattedTextField hitsoundField;
    private static JProgressBar progressBar;
    private static JPanel progressPanel;
    private static JButton buttonGenerate;

    public static void show() {

        if (Main.FFMPEG != null && Main.FFMPEG.exists()) {
            try {
                System.out.println("using ffmpeg at " + Main.FFMPEG.getCanonicalPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            int result = JOptionPane.showConfirmDialog(null, "Could not find FFmpeg. Do you want to download it automatically?", "FFmpeg is missing", JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE);
            if (result == JOptionPane.OK_OPTION) {
                JOptionPane.showConfirmDialog(null, "Download begins after closing this window. It might take a while - don't panic", ":]", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                Main.downloadFfmpeg();
            }
            Main.FFMPEG = new File("ffmpeg.exe");
            if (!Main.FFMPEG.exists()) {
                JOptionPane.showMessageDialog(null, "Could not find FFmpeg. Please manually download it and make sure ffmpeg.exe is in the same folder as this application.", ":[", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }

        SwingUtilities.invokeLater(() -> {
            FlatLightLaf.setup();
            frame = new JFrame();
            frame.setResizable(false);
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

            init();

            frame.pack();
            frame.setMaximumSize(frame.getMinimumSize());
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    private static void init() {
        initIcon();

        JPanel root = new JPanel(new BorderLayout());
        frame.add(root, BorderLayout.CENTER);
        JPanel rootTop = new JPanel(new BorderLayout());
        JPanel rootBottom = new JPanel(new BorderLayout());

        JPanel rootT = new JPanel(new BorderLayout());
        JPanel helpPanel = new JPanel(new BorderLayout());
        rootT.add(rootTop, BorderLayout.CENTER);
        rootT.add(helpPanel, BorderLayout.NORTH);

        {
            JButton helpButton = new JButton("Help");
            helpPanel.add(helpButton, BorderLayout.EAST);
            helpButton.addActionListener(e -> {
                int result = JOptionPane.showConfirmDialog(frame, "Feel free to message me!", "Help", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                if (result == JOptionPane.OK_OPTION) {
                    openWebpage("https://osu.ppy.sh/users/7350956");
                }
            });
        }

        root.add(rootT, BorderLayout.NORTH);
        root.add(rootBottom, BorderLayout.SOUTH);

        JPanel flow = new JPanel();
        flow.setLayout(new BoxLayout(flow, BoxLayout.Y_AXIS));
        rootTop.add(flow, BorderLayout.CENTER);

        {
            JPanel inputPanel = new JPanel(new BorderLayout(3, 0));
            flow.add(inputPanel);

            inputPanel.setBorder(BorderFactory.createTitledBorder("Input: (songs folder, beatmap folder or .osu file)"));
            inputTextArea = new JTextArea();
            JButton inputButton = new JButton("...");
            inputPanel.add(inputTextArea, BorderLayout.CENTER);
            inputPanel.add(inputButton, BorderLayout.EAST);

            inputButton.addActionListener(e -> {
                final JFileChooser fc = new JFileChooser();
                fc.setFileFilter(new FileFilter() {
                    @Override
                    public boolean accept(File f) {
                        return f.isDirectory() || f.getName().endsWith(".osu");
                    }

                    @Override
                    public String getDescription() {
                        return "Folders and .osu files";
                    }
                });

                fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
                fc.setAcceptAllFileFilterUsed(false);
                if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                    try {
                        inputTextArea.setText(fc.getSelectedFile().getCanonicalPath());
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            });
        }
        {
            JPanel outputPanel = new JPanel(new BorderLayout(3, 0));
            flow.add(outputPanel);

            outputPanel.setBorder(BorderFactory.createTitledBorder("Output: (temporary folder like 'C:\\TempSongs'; SSD recommended)"));

            outputTextArea = new JTextArea();
            JButton outputButton = new JButton("...");
            outputPanel.add(outputTextArea, BorderLayout.CENTER);
            outputPanel.add(outputButton, BorderLayout.EAST);

            outputButton.addActionListener(e -> {
                final JFileChooser fc = new JFileChooser();
                fc.setFileFilter(new FileFilter() {
                    @Override
                    public boolean accept(File f) {
                        return f.isDirectory();
                    }

                    @Override
                    public String getDescription() {
                        return "Folders";
                    }
                });

                fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
                fc.setAcceptAllFileFilterUsed(false);
                if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                    try {
                        outputTextArea.setText(fc.getSelectedFile().getCanonicalPath());
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            });
        }
        {
            JPanel flowOptionsPanel = new JPanel();
            flowOptionsPanel.setLayout(new BoxLayout(flowOptionsPanel, BoxLayout.Y_AXIS));
            flowOptionsPanel.setBorder(BorderFactory.createTitledBorder("Options:"));

            skipAlreadyGeneratedMaps = new JCheckBoxMenuItem(
                    "Skip maps already generated by this program (recommended)",
                    true);

            multiThreaded = new JCheckBoxMenuItem(
                    "Use multithreading (recommended; faster but more CPU intensive)",
                    true);

            allowOutputToSongs = new JCheckBoxMenuItem(
                    "Allow output to directly write to input folder (not recommended)",
                    false);

            {
                JPanel hitsoundPanel = new JPanel(new BorderLayout(3, 0));
                flowOptionsPanel.add(hitsoundPanel);

                JLabel hitsoundLabel = new JLabel("Hitsound volume: (0-100, 50 recommended)");

                NumberFormat format = NumberFormat.getInstance();
                NumberFormatter formatter = new NumberFormatter(format);
                formatter.setValueClass(Integer.class);
                formatter.setMinimum(0);
                formatter.setMaximum(100);
                formatter.setAllowsInvalid(false);
                formatter.setCommitsOnValidEdit(true);
                hitsoundField = new JFormattedTextField(formatter);
                hitsoundField.setColumns(3);
                hitsoundField.setHorizontalAlignment(SwingConstants.CENTER);

                hitsoundField.setValue(50);

                hitsoundPanel.add(hitsoundLabel, BorderLayout.CENTER);
                hitsoundPanel.add(hitsoundField, BorderLayout.EAST);
            }

            flowOptionsPanel.add(skipAlreadyGeneratedMaps);
            flowOptionsPanel.add(multiThreaded);
            flowOptionsPanel.add(allowOutputToSongs);
            flow.add(flowOptionsPanel);
        }

        {

            progressPanel = new JPanel(new BorderLayout());
            progressPanel.setBorder(BorderFactory.createEmptyBorder(0, 3, 3, 3));
            rootBottom.add(progressPanel, BorderLayout.CENTER);

            buttonGenerate = new JButton("Generate");

            progressBar = new JProgressBar();
            progressBar.setPreferredSize(buttonGenerate.getPreferredSize());
            progressBar.setString("Generating...");
            progressBar.setFont((new JLabel().getFont()));
            progressBar.setFocusable(false);
            progressBar.setStringPainted(true);
            progressBar.setVisible(false);

            buttonGenerate.addActionListener(e -> buttonGeneratePressed());

            progressPanel.add(buttonGenerate, BorderLayout.CENTER);
        }
    }

    public static void updateProgress(int value, int max) {
        SwingUtilities.invokeLater(() -> {
            if (progressBar != null) {
                progressBar.setMaximum(max);
                progressBar.setValue(value);
                progressBar.setString("Generating... (" + value + "/" + max + ")");
            }
        });
    }

    private static void buttonGeneratePressed() {
        try {
            File input = new File(inputTextArea.getText());
            File output = new File(outputTextArea.getText());
            boolean multiThreaded = UserInterface.multiThreaded.getState();
            boolean skipAlreadyGeneratedMaps = UserInterface.skipAlreadyGeneratedMaps.getState();
            int hitsoundVolume = Integer.parseInt(UserInterface.hitsoundField.getText());

            if (!input.exists() || !output.exists())
                throw new RuntimeException("input does not exist");

            try {
                if (input.getCanonicalPath().equals(output.getCanonicalPath())) {
                    JOptionPane.showMessageDialog(frame, "Tick the '" + allowOutputToSongs.getText() + "' box.\n" +
                                                         "Keep in mind that this way not only let's you play osu while " +
                                                         "the generation process is running since it will constantly do rescans " +
                                                         "but also there is a chance of messing up your whole input folder.", ":|", JOptionPane.WARNING_MESSAGE);
                }

            } catch (IOException e) {
                e.printStackTrace();
            }

            new Thread(() -> {
                JOptionPane.showMessageDialog(frame, "Once this is done copy all folders from your specified output folder " +
                                                     "to your input folder. There should be no need to overwrite anything.\n" +
                                                     "Keep in mind the generation speed may vary depending on how many maps you have " +
                                                     "that need to be converted.\n" +
                                                     "If the program crashes for some reason or you want to pause it but don't want to " +
                                                     "lose the generation progress this far, copy all already generated maps (in your " +
                                                     "output folder) to your input folder. The next time you press this button it " +
                                                     "will just skip them since they were already generated.\n" +
                                                     "Also there will be multiple cmd windows running in the background - do not close them " +
                                                     "while the generation process is running.\n" +
                                                     "You might want to disable Windows Defender's Real-time protection while this is running " +
                                                     "since there are a lot of read-write events(?) it puts the CPU under heavy load. Idk why.", "Wall of text", JOptionPane.INFORMATION_MESSAGE);

                buttonGenerate.setVisible(false);
                progressBar.setVisible(true);
                progressPanel.add(progressBar, BorderLayout.CENTER);
                frame.pack();

                Main.generate(input, output, multiThreaded, skipAlreadyGeneratedMaps, hitsoundVolume);

                JOptionPane.showMessageDialog(frame, "Generation process is done. You can copy all folders " +
                                                     "from your specified output folder to your input folder now. " +
                                                     "There should be no need to overwrite anything.", ":]", JOptionPane.INFORMATION_MESSAGE);

                buttonGenerate.setVisible(true);
                progressBar.setVisible(false);
                progressPanel.add(buttonGenerate, BorderLayout.CENTER);
                frame.pack();
            }).start();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "Failed to start the generation process", ":[", JOptionPane.WARNING_MESSAGE);
        }
    }

    private static void initIcon() {
        int[] sizes = new int[]{ 16, 32, 64, 128 };
        List<BufferedImage> icons = new ArrayList<>();

        // yes, the icons are generated at runtime
        for (Integer size : sizes) {
            BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, (int) (72.0 * size / Toolkit.getDefaultToolkit().getScreenResolution())));
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            String text = "\u266A";
            FontMetrics metrics = g.getFontMetrics(g.getFont());
            int x = (size - metrics.stringWidth(text)) / 2;
            int y = ((size - metrics.getHeight()) / 2) + metrics.getAscent();
            g.setColor(Color.WHITE);
            g.fillOval(0, 0, size, size);
            g.setColor(new Color(255, 102, 170));
            g.fill(new Ellipse2D.Double(size * .05, size * .05, size * .9, size * .9));
            g.setColor(Color.WHITE);
            g.drawString(text, x, y);
            icons.add(img);
            g.dispose();
        }
        frame.setIconImages(icons);
    }

    public static boolean openWebpage(URI uri) {
        Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
        if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
            try {
                desktop.browse(uri);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public static boolean openWebpage(String url) {
        try {
            return openWebpage(new URL(url).toURI());
        } catch (URISyntaxException | MalformedURLException e) {
            e.printStackTrace();
        }
        return false;
    }
}
