package org.sdsai.airqso;

import org.sdsai.airqso.util.EditTextInputStream;
import org.sdsai.airqso.util.SystemUiHider;

import android.annotation.TargetApi;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 *
 * @see SystemUiHider
 */
public class ChatActivity extends Activity {

    /**
     * If set, will toggle the system UI visibility upon interaction. Otherwise,
     * will show the system UI visibility upon interaction.
     */
    private static final boolean TOGGLE_ON_CLICK = true;

    private Bpsk bpsk;

    private Bpsk.ReceiveThread receiveThread;
    private Bpsk.TransmitThread transmitThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.chat_activity);

        final View controlsView = findViewById(R.id.chat_controls);
        final View contentView = findViewById(R.id.chat_layout);

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        final ToggleButton txButton = (ToggleButton) findViewById(R.id.tx_button);
        final ToggleButton rxButton = (ToggleButton) findViewById(R.id.rx_button);
        final Button       clrButton = (Button) findViewById(R.id.clr_button);
        final EditText     hzField  = (EditText) findViewById(R.id.hz_text);
        final EditText     txText   = (EditText) findViewById(R.id.chat_tx);
        final TextView     rxText   = (TextView) findViewById(R.id.chat_rx);

        clrButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                txText.setText("");
                rxText.setText("");
            }
        });

        bpsk = new Bpsk(new OutputStream(){
            @Override
            public void write(final int b) throws IOException {
                ChatActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run(){
                        final TextView chatRx = (TextView) findViewById(R.id.chat_rx);
                        chatRx.append(String.valueOf(b));
                    }
                });
            }
            @Override
            public void write(final byte[] bytes) throws IOError {
                ChatActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run(){
                        final TextView chatRx = (TextView) findViewById(R.id.chat_rx);
                        chatRx.append(new String(bytes));
                    }
                });
            }
            @Override
            public void write(final byte[] bytes, final int off, final int len) throws IOException {
                ChatActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run(){
                        final TextView chatRx = (TextView) findViewById(R.id.chat_rx);
                        chatRx.append(new String(bytes, off, len));
                    }
                });
            }
        },
        new EditTextInputStream(txText));

    }

    public int getHz() {
        final EditText txText = (EditText) findViewById(R.id.hz_text);

        int i ;
        try {
            i = Integer.parseInt(txText.getText().toString());
        }
        catch (final NumberFormatException nfe) {
            Log.i("ChatActivity", "Invalid input. Setting hz=700.");
            i = 700;
        }

        return i;
    }

    public void onTxClicked(final View view) {
        // Is the toggle on?
        boolean on = ((ToggleButton) view).isChecked();

        if (transmitThread != null) {
            transmitThread.stopTransmit();
            transmitThread = null;
        }

        if (on) {
            transmitThread = bpsk.startTransmit(getHz());
        }
    }

    public void onRxClicked(View view) {
        // Is the toggle on?
        boolean on = ((ToggleButton) view).isChecked();

        if (receiveThread != null) {
            receiveThread.stopReceive();
            receiveThread = null;
        }

        if (on) {
            receiveThread = bpsk.startReceive(getHz());
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }

    @Override
    protected void onStop() {
        if (transmitThread != null) {
            transmitThread.stopTransmit();
            transmitThread = null;
        }

        if (receiveThread != null) {
            receiveThread.stopReceive();
            receiveThread = null;
        }
    }
}
