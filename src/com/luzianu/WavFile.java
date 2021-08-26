package com.luzianu;

import sun.misc.IOUtils;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;

public class WavFile {
    private File file;
    private byte[] buffer;

    public WavFile(File file) throws UnsupportedAudioFileException, IOException {
        this.file = file;
        buffer = IOUtils.readAllBytes(AudioSystem.getAudioInputStream(file));
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
