package com.luzianu;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.*;

public class WavFile {
    private File file;
    private byte[] buffer;

    public WavFile(File file) throws UnsupportedAudioFileException, IOException {
        this.file = file;
        buffer = getBytesFromInputStream(AudioSystem.getAudioInputStream(file));

    }

    public static byte[] getBytesFromInputStream(InputStream is) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] buffer = new byte[0xFFFF];
        for (int len = is.read(buffer); len != -1; len = is.read(buffer)) {
            os.write(buffer, 0, len);
        }
        return os.toByteArray();
    }

    public File getFile() {
        return file;
    }

    public byte[] getBuffer() {
        return buffer;
    }

    @Override
    public String toString() {
        return "WavFile{" +
               "file=" + file +
               ", bufferSize=" + buffer.length +
               '}';
    }
}
