package com.arthou.smputilities.ttsbridge;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * MP3 playback through Java Sound, used on Windows and macOS. JLayer decodes
 * the MP3 to PCM and the samples go to a SourceDataLine opened on the mixer
 * the user picked, which is how the audio can be sent to the cable (VB-Cable,
 * BlackHole) and to the speakers at the same time.
 */
final class JavaSoundPlayer {
    private JavaSoundPlayer() {
    }

    /** Output devices that can actually play audio. */
    static List<Audio.Device> outputs() {
        List<Audio.Device> devices = new ArrayList<>();
        Line.Info wanted = new Line.Info(SourceDataLine.class);
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            try {
                if (AudioSystem.getMixer(info).getSourceLineInfo(wanted).length > 0) {
                    devices.add(new Audio.Device(info.getName(), info.getName()));
                }
            } catch (Exception ignored) {
                // skip mixers that cannot be opened
            }
        }
        return devices;
    }

    private static Mixer.Info findMixer(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            if (info.getName().equals(name)) {
                return info;
            }
        }
        return null;
    }

    /** Decodes and plays the MP3. A blank device means the system default. */
    static void play(byte[] mp3, String deviceName) throws IOException {
        Bitstream bitstream = new Bitstream(new ByteArrayInputStream(mp3));
        Decoder decoder = new Decoder();
        SourceDataLine line = null;
        byte[] transfer = null;

        try {
            Header header;
            while ((header = bitstream.readFrame()) != null) {
                SampleBuffer samples = (SampleBuffer) decoder.decodeFrame(header, bitstream);

                if (line == null) {
                    AudioFormat format = new AudioFormat(
                        samples.getSampleFrequency(),
                        16,
                        samples.getChannelCount(),
                        true,
                        false);
                    line = openLine(format, deviceName);
                    line.start();
                }

                short[] pcm = samples.getBuffer();
                int count = samples.getBufferLength();
                int bytes = count * 2;
                if (transfer == null || transfer.length < bytes) {
                    transfer = new byte[bytes];
                }
                for (int i = 0; i < count; i++) {
                    transfer[i * 2] = (byte) (pcm[i] & 0xFF);
                    transfer[i * 2 + 1] = (byte) ((pcm[i] >> 8) & 0xFF);
                }
                line.write(transfer, 0, bytes);
                bitstream.closeFrame();
            }
        } catch (Exception exception) {
            throw new IOException("MP3 playback failed: " + exception.getMessage(), exception);
        } finally {
            if (line != null) {
                line.drain();
                line.close();
            }
            try {
                bitstream.close();
            } catch (Exception ignored) {
                // nothing useful to do while closing
            }
        }
    }

    private static SourceDataLine openLine(AudioFormat format, String deviceName) throws Exception {
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        Mixer.Info mixer = findMixer(deviceName);

        if (mixer != null) {
            try {
                SourceDataLine line = (SourceDataLine) AudioSystem.getMixer(mixer).getLine(info);
                line.open(format);
                return line;
            } catch (Exception ignored) {
                // fall through to the default device rather than failing outright
            }
        }

        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(format);
        return line;
    }
}
