package com.pureqml.android.runtime;

import android.content.Context;
import android.graphics.Rect;
import android.util.Log;
import android.widget.ProgressBar;

import com.pureqml.android.IExecutionEnvironment;

public final class Spinner extends Element {
    public static final String TAG = "Input";

    final ProgressBar                     view;
    final ViewHolder<ProgressBar>         viewHolder;

    public Spinner(IExecutionEnvironment env) {
        super(env);

        Context context = env.getContext();
        view = new ProgressBar(context);
        view.setIndeterminate(true);
        viewHolder = new ViewHolder<>(context, view);
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
        beginPaint(state);
        paintChildren(state);

        Rect rect = getRect();

        if (!rect.isEmpty()) {
            rect.offsetTo(state.baseX, state.baseY);
            Log.v(TAG, "spinner layout " + rect.toString());
            viewHolder.setRect(_env.getRootView(), rect);
        }

        endPaint();
    }

}
