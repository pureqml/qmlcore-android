package com.pureqml.android.runtime;

import android.content.Context;

public final class EditText extends androidx.appcompat.widget.AppCompatEditText {
    static final String TAG = "EditText";
    Element parent;

    EditText(Element parent, Context context) {
        super(context);
        this.parent = parent;
        setFocusable(true);
        setFocusableInTouchMode(true);
        setSingleLine();
    }
}
