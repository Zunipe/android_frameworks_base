package com.android.systemui.gamemode;

import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import javax.inject.Inject;

public class DraggableWindowsHelper {
    private final WindowManager mWindowManager;
    private int mLastFloatWindowX = 150;
    private int mLastFloatWindowY = 150;
    private boolean mIsShowing = false;
    private View mWindowView;

    @Inject
    public DraggableWindowsHelper(WindowManager windowManager) {
        mWindowManager = windowManager;
    }

    public boolean isShowing() {
        return mIsShowing;
    }

    public boolean dismiss() {
        if (!mIsShowing || mWindowView == null) {
            return false;
        }
        mWindowManager.removeView(mWindowView);
        mWindowView = null;
        mIsShowing = false;
        return true;
    }

    public boolean show(WindowManager.LayoutParams params, View view) {
        if (mIsShowing) {
            return false;
        }
        params.x = mLastFloatWindowX;
        params.y = mLastFloatWindowY;
        params.gravity = Gravity.TOP | Gravity.START;
        final DisplayMetrics displayMetrics = new DisplayMetrics();
        mWindowManager.getDefaultDisplay().getMetrics(displayMetrics);

        view.setOnTouchListener(new View.OnTouchListener() {
            private float lastTouchX;
            private float lastTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        lastTouchX = event.getRawX();
                        lastTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - lastTouchX;
                        float deltaY = event.getRawY() - lastTouchY;

                        params.x += (int) deltaX;
                        params.y += (int) deltaY;

                        int viewWidth = v.getWidth() > 0 ? v.getWidth() : 150;
                        int viewHeight = v.getHeight() > 0 ? v.getHeight() : 150;
                        int maxX = displayMetrics.widthPixels - viewWidth;
                        int maxY = displayMetrics.heightPixels - viewHeight;

                        if (params.x < 0) params.x = 0;
                        if (params.y < 0) params.y = 0;
                        if (params.x > maxX) params.x = maxX;
                        if (params.y > maxY) params.y = maxY;

                        mWindowManager.updateViewLayout(view, params);
                        lastTouchX = event.getRawX();
                        lastTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        mLastFloatWindowX = params.x;
                        mLastFloatWindowY = params.y;
                        return true;
                }
                return false;
            }
        });
        mWindowView = view;
        mIsShowing = true;
        mWindowManager.addView(view, params);
        return true;
    }
}
