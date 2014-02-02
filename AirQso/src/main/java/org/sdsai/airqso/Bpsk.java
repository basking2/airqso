package org.sdsai.airqso;

import org.sdsai.dsp.BpskOutputStream;
import org.sdsai.dsp.BpskInputStream;
import org.sdsai.dsp.BpskDetector;
import org.sdsai.dsp.BpskGenerator;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.*;
import android.os.Process;
import android.provider.MediaStore;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * The interface between Android Hardware and the BPSK classes.
 */
public class Bpsk {

    /**
     * We can use almost any sample rate.
     */
    private static int[] SAMPLE_RATES = new int[]{44100, 22050, 11025, 8000};

    /**
     * Currently we require 16 bit audio. That is our only choice.
     */
    private static short[] ENCODINGS = new short[]{AudioFormat.ENCODING_PCM_16BIT};

    /**
     * Currently we require a single channel. That is our only choice.
     */
    private static short[] IN_CHANNELS = new short[]{AudioFormat.CHANNEL_IN_MONO};

    /**
     * Currently we require a single channel. That is our only choice.
     */
    private static short[] OUT_CHANNELS = new short[]{AudioFormat.CHANNEL_OUT_MONO};

    /**
     * Different audio sources to attempt to record from.
     */
    private static short[] SOURCES = new short[] {
        MediaRecorder.AudioSource.CAMCORDER,
        MediaRecorder.AudioSource.MIC,
        MediaRecorder.AudioSource.DEFAULT,
    };

    /**
     * How data is delivered to the user.
     */
    private OutputStream out;

    /**
     * How data is delivered to the transmitter from the user.
     */
    private InputStream in;

    /**
     * @param out The output stream that handles writes from the thread that will be started.
     * @param in The input stream that handles transmitting the user's data.
     */
    public Bpsk(final OutputStream out, final InputStream in) {
        this.out = out;
        this.in = in;
    }

    /**
     * Calls {@link #startTransmit(int, double)} with a default symbol rate.
     *
     * @param hz The audio frequency in hertz. 700hz is a nice audible tone.
     *
     * @return A started {@link TransmitThread}. The user should call {@link TransmitThread#stopTransmit()}.
     */
    public TransmitThread startTransmit(final int hz) {
        return startTransmit(hz, BpskGenerator.PSK31_SYMBOLS_PER_SECOND);
    }

    public TransmitThread startTransmit(final int hz, final double symbolRate) {

        final TransmitThread transmitThread = new TransmitThread(hz, symbolRate, in);

        transmitThread.start();

        return transmitThread;
    }

    /**
     * Calls {@link #startReceive(int, double)} with a default symbol rate.
     *
     * @param hz The audio frequency in hertz. 700hz is a nice audible tone.
     *
     * @return A started {@link ReceiveThread}. The user should call {@link ReceiveThread#stopReceive()}.
     */
    public ReceiveThread startReceive(final int hz) {
        return startReceive(hz, BpskGenerator.PSK31_SYMBOLS_PER_SECOND);
    }

    /**
     * Start a receive thread that receives from an {@link AudioRecord} object and writes to out.
     *
     * @param hz The audio frequency in hertz. 700hz is a nice audible tone.
     * @param symbolRate The number of symbols per second. The normal value is 31.25.
     *
     * @return A started {@link ReceiveThread}. The user should call {@link ReceiveThread#stopReceive()}
     */
    public ReceiveThread startReceive(
            final int hz,
            final double symbolRate) {

        final ReceiveThread r = new ReceiveThread(hz, symbolRate, out);

        r.start();

        return r;
    }

    /**
     * Attempt to find a combination of record parameters that works on a particular device.
     *
     * @param symbolRate The PSK symbol rate, used to allocate a suitable buffer.
     *
     * @return A valid {@link AudioRecord}. If none is found, a {@link RuntimeException} is thrown.
     * @throws RuntimeException on no {@link AudioRecord} option found.
     */
    public static AudioRecord findAudioRecord(final double symbolRate) {
        final String TAG = "findAudioRecord";
        for (int rate : SAMPLE_RATES) {
            for (short audioFormat : ENCODINGS) {
                for (short channelConfig : IN_CHANNELS) {
                    for (short audioSource : SOURCES) {
                        try {
                            Log.d(TAG, "Attempting rate " + rate + "Hz, bits: " + audioFormat + ", channel: " + channelConfig);
                            final int bufferSize = AudioRecord.getMinBufferSize(rate, channelConfig, audioFormat);

                            if (bufferSize != AudioRecord.ERROR_BAD_VALUE) {

                                /* This is the only real custom code. Wait for about 10 symbols to
                                 * come by at the typical PSK 32 speed. */
                                final int otherBufferSize = (int)(rate / symbolRate * 10);

                                // check if we can instantiate and have a success
                                final AudioRecord recorder = new AudioRecord(
                                        audioSource,
                                        rate,
                                        channelConfig,
                                        audioFormat,
                                        Math.max(bufferSize, otherBufferSize));

                                if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
                                    Log.d(TAG, "Chose rate " + rate + "Hz, bits: " + audioFormat + ", channel: " + channelConfig);
                                    return recorder;
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, rate + "Exception, keep trying.", e);
                        }
                    }
                }
            }
        }
        throw new RuntimeException("Failed to find recording resource.");
    }

    /**
     * Attempt to find a combination of record parameters that works on a particular device.
     *
     * @param symbolRate The PSK symbol rate, used to allocate a suitable buffer.
     *
     * @return A valid {@link AudioTrack}. If none is found, a {@link RuntimeException} is thrown.
     * @throws RuntimeException on no {@link AudioTrack} option found.
     */
    public static AudioTrack findAudioPlay(final double symbolRate) {
        final String TAG = "findAudioPlay";
        for (int rate : SAMPLE_RATES) {
            for (short audioFormat : ENCODINGS) {
                for (short channelConfig : OUT_CHANNELS) {
                    try {
                        Log.d(TAG, "Attempting rate " + rate + "Hz, bits: " + audioFormat + ", channel: " + channelConfig);
                        final int bufferSize = AudioTrack.getMinBufferSize(rate, channelConfig, audioFormat);

                        if (bufferSize != AudioTrack.ERROR_BAD_VALUE) {

                            /* This is the only real custom code. Wait for about 10 symbols to
                             * come by at the typical PSK 32 speed. */
                            final int otherBufferSize = (int)(rate / symbolRate * 2 * 10);

                            // check if we can instantiate and have a success
                            final AudioTrack play = new AudioTrack(
                                    AudioManager.STREAM_MUSIC,
                                    rate,
                                    channelConfig,
                                    audioFormat,
                                    bufferSize,
                                    AudioTrack.MODE_STREAM);

                            if (play.getState() == AudioTrack.STATE_INITIALIZED) {
                                Log.d(TAG, "Chose rate " + rate + "Hz, bits: " + audioFormat + ", channel: " + channelConfig);
                                return play;
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, rate + "Exception, keep trying.", e);
                    }
                }
            }
        }

        throw new RuntimeException("Failed to find playback resource.");
    }

    public static class TransmitThread extends Thread {
        private BpskGenerator bpskGenerator;
        private InputStream   in;
        private AudioTrack audioTrack;
        private boolean running;

        public TransmitThread(final int hz, final double symbolRate, final InputStream in)
        {
            this.running = false;
            this.audioTrack = findAudioPlay(symbolRate);
            this.bpskGenerator = new BpskGenerator(hz, audioTrack.getSampleRate(), symbolRate);
            this.in = in;
        }

        @Override
        public void run() {
            try {
                final BpskOutputStream bpskOutputStream = new BpskOutputStream(new OutputStream(){
                    @Override
                    public void write(int i) throws IOException {
                        short[] b = new short[1];
                        b[0] = (short) i;
                        audioTrack.write(b, 0, 1);
                    }
                    @Override
                    public void write(byte[] b, int off, int len) throws IOException {
                        short[] s = new short[len/2];
                        for (int i = 0; i < s.length; ++i) {
                            s[i] = (short)(((((short)b[i*2])<<8)&0xff00)|((short)(b[i*2+1])&0xff));
                        }
                        audioTrack.write(s, 0, s.length);
                    }
                    @Override
                    public void write(byte[] b) throws IOException {
                        write(b, 0, b.length);
                    }
                }, bpskGenerator);

                running = true;
                audioTrack.play();
                while (running) {
                    final byte[] buffer = new byte[100];

                    if (in.available() == 0) {
                        bpskOutputStream.preamble(10);
                    }
                    else {
                        final int len = in.read(buffer);

                        bpskOutputStream.write(buffer, 0, len);
                   }
                }

                bpskOutputStream.postamble(10);
            }
            catch (final IOException e) {

            }
            finally {
                cleanup();
            }
        }

        /**
         * Release all audio resources to the OS quickly.
         */
        public synchronized void cleanup() {
            if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                audioTrack.stop();
            }

            if (audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
                audioTrack.release();
            }
        }
        public void stopTransmit() {

            cleanup();

            running = false;

            try {
                this.join();
            }
            catch (final InterruptedException e) {
                Log.e("Bpsk", "Failed to join transmit thread.");
            }
        }

        @Override
        public void finalize() throws Throwable { cleanup(); }
    }

    public static class ReceiveThread extends Thread {
        private boolean running;
        final private OutputStream out;
        final private AudioRecord audioRecord;
        final private BpskDetector bpskDetector;

        public ReceiveThread(final int hz, final double symbolRate, final OutputStream out) {
            this.running = false;
            this.out = out;
            this.audioRecord = findAudioRecord(symbolRate);
            this.bpskDetector = new BpskDetector(hz, audioRecord.getSampleRate(), symbolRate);
        }

        /**
         * Release all the audio resources back to the OS quickly.
         */
        private synchronized void cleanup() {
            if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord.stop();
            }

            if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                audioRecord.release();
            }
        }

        public void stopReceive() {
            cleanup();

            running = false;

            try {
                this.join();
            }
            catch (final InterruptedException e) {
                Log.e("Bpsk", "Failed to join receive thread.");
            }
        }

        @Override
        public void run() {
            final BpskInputStream bpskInputStream = new BpskInputStream(
                    /**
                     * And input stream that reads from an Android Audio source and returns
                     * the 16 bit, big endian, signed integers as java-compatible
                     * values.
                     */
                    new InputStream() {
                        @Override
                        public int read(byte[] b, int off, int len) throws IOException {
                            short[] s = new short[len/2];
                            int rc = audioRecord.read(s, 0, s.length);
                            switch(rc) {
                                case AudioRecord.ERROR_INVALID_OPERATION:
                                    throw new IOException("Invalid operation.");
                                case AudioRecord.ERROR_BAD_VALUE:
                                    throw new IOException("Bad value.");
                            }

                            /* If rc == -1, just exit. If rc == 0, we received nothing. */
                            if (rc > 0) {
                                for (int i = 0; i < rc; ++i) {
                                    b[off+i*2]   = (byte)((s[i]>>8)&0xff);
                                    b[off+i*2+1] = (byte)((s[i])&0xff);
                                }
                            }

                            return rc*2;
                        }

                        @Override
                        public int read(byte[] b) throws IOException {
                            return read(b, 0, b.length);
                        }

                        @Override
                        public int read() throws IOException {
                            short[] s = new short[1];
                            int rc;

                            do {
                                rc = audioRecord.read(s, 0, 1);
                                switch(rc) {
                                    case AudioRecord.ERROR_INVALID_OPERATION:
                                        throw new IOException("Invalid operation.");
                                    case AudioRecord.ERROR_BAD_VALUE:
                                        throw new IOException("Bad value.");
                                }
                            } while (rc == 0);

                            if (rc < 0) {
                                return rc;
                            }

                            return (int)((((short)s[0]<<8)&0xff00) | ((short)s[0]&0xff));
                        }
                    },
                    bpskDetector
            );

            try {
                out.write("[Receive started]\n".getBytes());
                final byte[] b = new byte[1];
                audioRecord.startRecording();
                running = true;
                while (running) {

                    /* Receive from the user. */
                    final int len = bpskInputStream.read(b);

                    if (len > 0) {
                        out.write(b, 0, len);
                    }
                    else if (len == -1) {
                        break;
                    }
                }
            }
            catch (final IOException e){
                /* Nothing we can really do. */
                Log.i("Bpsk", e.getMessage());
            }
            finally {
                cleanup();
            }
        }

        @Override
        public void finalize() throws Throwable { cleanup(); }

    }
}
