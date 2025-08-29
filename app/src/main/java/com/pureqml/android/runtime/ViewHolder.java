package com.pureqml.android.runtime;

import android.graphics.Rect;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.pureqml.android.SafeRunnable;

public final class ViewHolder<ViewType extends View> {
    private static final String TAG = "ViewHolder";

    private final ViewType              view;
    RelativeLayout.LayoutParams         layoutParams;

    public ViewHolder(ViewType view) {
        this.view = view;
    }

    public void discard(final ViewGroup rootView) {
        if (rootView != null) {
            rootView.post(new SafeRunnable() {
                @Override
                public void doRun() {
                    view.setVisibility(View.GONE);
                    rootView.removeView(view);
                }
            });
        } else
            Log.w(TAG, "no root view...");
    }

    void setRect(final ViewGroup rootView, Rect rect) {
        if (rootView == null)
            return;

        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(rect.width(), rect.height());
        lp.leftMargin = rect.left;
        lp.topMargin = rect.top;
        Log.d(TAG, "layout params = " + lp.debug("RelativeLayout.LayoutParams"));

        synchronized (this) {
            if (!lp.equals(layoutParams)) {
                Log.v(TAG, "installing new layout params");
                layoutParams = lp;
            } else
                lp = null;
        }
        if (lp != null)
            update(rootView, true);
    }

    void update(final ViewGroup rootView, boolean visible) {
        final ViewGroup.LayoutParams lp;

        synchronized (this) {
            lp = layoutParams;
        }

        if (visible) {
            rootView.post(new SafeRunnable() {
                @Override
                public void doRun() {
                    if (view.getParent() == null) {
                        Log.d(TAG, "adding view to layout...");
                        view.setVisibility(View.VISIBLE);
                        if (lp != null)
                            rootView.addView(view, lp);
                        else
                            rootView.addView(view);
                    } else {
                        if (lp != null)
                            rootView.updateViewLayout(view, lp);
                    }
                }});
        } else {
            rootView.post(new SafeRunnable() {
                @Override
                public void doRun() {
                    if (view.getParent() != null) {
                        Log.d(TAG, "removing view from layout...");
                        view.setVisibility(View.GONE);
                        rootView.removeView(view);
                    }
                }
            });
        }
    }
}
