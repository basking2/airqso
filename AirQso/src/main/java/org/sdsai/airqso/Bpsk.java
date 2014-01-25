package org.sdsai.airqso;

import org.sdsai.dsp.BpskOutputStream;
import org.sdsai.dsp.BpskInputStream;
import org.sdsai.dsp.BpskDetector;
import org.sdsai.dsp.BpskGenerator;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * The interface between Android Hardware and the BPSK classes.
 */
public class Bpsk {

    private final static int SAMPLE_RATE = 44100;
    private OutputStream out;
    private Thread receiveThread;
    private boolean receiveThreadRunning;

    /**
     * @param out The output stream that handles writes from the thread that will be started.
     */
    public Bpsk (final OutputStream out) {
        this.out = out;
        this.receiveThread = null;
        this.receiveThreadRunning = false;
    }

    /**
     * Calls {@link #startReceive(int, double, java.io.OutputStream)} with a default symbol rate.
     *
     * @param hz The audio frequency in hertz. 700hz is a nice audible tone.
     * @param out The output stream to write text to.
     *
     * @return A started {@link ReceiveThread}. The user should call {@link ReceiveThread#stopReceive()}
     */
    public ReceiveThread startReceive(final int hz, final OutputStream out) {
        return startReceive(hz, BpskGenerator.PSK31_SYMBOLS_PER_SECOND, out);
    }

    /**
     * Start a receive thread that receives from an {@link AudioRecord} object and writes to out.
     *
     * @param hz The audio frequency in hertz. 700hz is a nice audible tone.
     * @param symbolRate The number of symbols per second. The normal value is 31.25.
     * @param out The output stream to write text to.
     *
     * @return A started {@link ReceiveThread}. The user should call {@link ReceiveThread#stopReceive()}
     */
    public ReceiveThread startReceive(
            final int hz,
            final double symbolRate, final OutputStream out) {

        final BpskDetector bpskDetector = new BpskDetector(hz, SAMPLE_RATE, symbolRate);
        final ReceiveThread r = new ReceiveThread(bpskDetector, out);

        r.start();

        return r;
    }

    public static class ReceiveThread extends Thread {
        private boolean running;
        final private OutputStream out;
        final BpskDetector bpskDetector;

        public ReceiveThread(BpskDetector bpskDetector, final OutputStream out) {
            this.running = false;
            this.out = out;
            this.bpskDetector = bpskDetector;
        }

        @Override
        public void start() {
            this.running = true;
            super.start();
        }

        public void stopReceive() {
            this.running = false;
        }

        @Override
        public void run() {
            final int bufferLen = 10240; /* FIXME - adjust this to something more sensible. */
            final byte[] buffer = new byte[bufferLen];
            final AudioRecord audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    Bpsk.SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferLen);
            final BpskInputStream bpskInputStream = new BpskInputStream(
                    new InputStream() {
                        @Override
                        public int read(byte[] b, int off, int len) throws IOException {
                            int rc = audioRecord.read(b, off, len);
                            switch(rc) {
                                case AudioRecord.ERROR_INVALID_OPERATION:
                                    throw new IOException("Invalid operation.");
                                case AudioRecord.ERROR_BAD_VALUE:
                                    throw new IOException("Bad value.");
                                default:
                                    return rc;
                            }
                        }

                        @Override
                        public int read(byte[] b) throws IOException {
                            return read(b, 0, b.length);
                        }

                        @Override
                        public int read() throws IOException {
                            byte[] b = new byte[1];
                            int i;

                            do {
                                i = read(b, 0, 1);
                            } while (i == 0);

                            if (i < 0) {
                                return i;
                            }
                            return b[0];
                        }
                    },
                    bpskDetector
            );

            audioRecord.startRecording();
            try {
                while(running) {
                    final byte[] b = new byte[1024];

                    /* Receive from the user. */
                    bpskInputStream.read(b);

                    /* Write. */
                    out.write(b);
                }
            }
            catch (final IOException e){
                /* Nothing we can really do. */
            }
            finally {
                audioRecord.stop();
            }
        }
    }
}
