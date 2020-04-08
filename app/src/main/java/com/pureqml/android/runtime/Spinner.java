package com.pureqml.android.runtime;

import android.content.Context;
import android.graphics.Rect;
import android.util.Log;
import android.widget.ProgressBar;

import com.pureqml.android.IExecutionEnvironment;

public final class Spinner extends Element {
    public static final String TAG = "Input";

    ProgressBar                     view;
    ViewHolder<ProgressBar>         viewHolder;

    public Spinner(IExecutionEnvironment env) {
        super(env);

        Context context = env.getContext();
        view = new ProgressBar(context);
        viewHolder = new ViewHolder<ProgressBar>(context, view);
    }

    public void discard() {
        super.discard();
        viewHolder.discard(_env.getRootView());
    }

    private void updateVisibility(boolean value) {
        viewHolder.update(_env.getRootView(), value);
    }

    @Override
    protected void onGloballyVisibleChanged(boolean value) {
        Log.d(TAG, "onGloballyVisibleChanged " + value);
        super.onGloballyVisibleChanged(value);
        updateVisibility(value);
    }

    @Override
    public void paint(PaintState state) {
        super.paint(state);
        beginPaint();
        paintChildren(state);

        Rect rect = getRect();

        if (!rect.isEmpty()) {
            rect.offsetTo(state.baseX, state.baseY);
            Log.i(TAG, "input layout " + rect.toString());
            viewHolder.setRect(_env.getRootView(), rect);
        }

        endPaint();
    }

}