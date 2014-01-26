package org.sdsai.airqso.util;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

/**
 */
public class EditTextInputStream
    extends PipedInputStream
    implements TextWatcher
{
    private PipedOutputStream pipedOutputStream;

    public EditTextInputStream(final EditText editText)
    {
        try {
            pipedOutputStream = new PipedOutputStream(this);
            editText.addTextChangedListener(this);
        }
        catch (final IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public void onTextChanged(
            final CharSequence charSequence,
            final int start,
            final int before,
            final int count
    )
    {
        if (count > 0) {
            try {
                pipedOutputStream.write(
                        charSequence.subSequence(start, start + count).toString().getBytes());
            }
            catch (final IOException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
    }

    @Override
    public void beforeTextChanged(
            final CharSequence charSequence,
            final int start,
            final int count,
            final int after
    )
    {

    }

    @Override
    public void afterTextChanged(final Editable editable) {

    }
}
