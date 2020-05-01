package com.pureqml.android;

import android.annotation.SuppressLint;
import android.content.ComponentCallbacks2;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

import com.pureqml.android.runtime.Element;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

public final class MainActivity
        extends AppCompatActivity
        implements ComponentCallbacks2
{
    private static final String TAG = "main";
    private boolean                 _executionEnvironmentBound = false;
    private ExecutionEnvironment    _executionEnvironment;
    private SurfaceView             _mainView;
    private Rect                    _surfaceFrame;
    private IRenderer               _uiRenderer;
    boolean                         _keyDownHandled;
    boolean                         _showSoftKeyboard;
    InputMethodManager              _imm;

    private ServiceConnection _executionEnvironmentConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.i(TAG, "connected to execution service...");
            _executionEnvironment = ((ExecutionEnvironment.LocalBinder) service).getService();
            _executionEnvironment.acquireResource();
            synchronized (MainActivity.this) {
                ViewGroup rootView = (ViewGroup)findViewById(android.R.id.content);
                rootView = (ViewGroup)rootView.getChildAt(0);
                _executionEnvironment.setRootView(rootView);
                _executionEnvironment.setSurfaceHolder(_mainView.getHolder());

                if (_surfaceFrame != null)
                    _executionEnvironment.setSurfaceFrame(_surfaceFrame);
                if (_uiRenderer != null) {
                    _executionEnvironment.setRenderer(_uiRenderer);
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG, "execution environment service died...");
            synchronized (MainActivity.this) {
                _executionEnvironment.setRootView(null);
                _executionEnvironment.setSurfaceHolder(null);
                _executionEnvironment = null;
            }
        }
    };

    private class SurfaceHolderCallback implements SurfaceHolder.Callback2 {

        @Override
        public void surfaceCreated(final SurfaceHolder holder) {
            Log.i(TAG, "surface created " + holder.getSurfaceFrame());
            synchronized (MainActivity.this) {
                _surfaceFrame = holder.getSurfaceFrame();

                final SurfaceView view = _mainView;
                _uiRenderer = new IRenderer() {
                    DisplayMetrics _metrics;

                    @Override
                    public DisplayMetrics getDisplayMetrics() {
                        return MainActivity.this.getBaseContext().getResources().getDisplayMetrics();
                    }

                    @Override
                    public void invalidateRect(Rect rect) {
                        if (rect != null) {
                            if (!rect.isEmpty()) {
                                //Log.v(TAG, "invalidateRect " + rect);
                                view.postInvalidate(rect.left, rect.top, rect.right, rect.bottom);
                            }
                        } else {
                            Log.v(TAG, "invalidateAll");
                            view.postInvalidate();
                        }
                    }

                    @Override
                    public void keepScreenOn(final boolean enable) {
                        Log.i(TAG, "keepScreenOn " + enable);
                        final Window window = MainActivity.this.getWindow();
                        final View decorView = window.getDecorView();
                        decorView.post(new Runnable() {
                            @Override
                            public void run() {
                                if (enable)
                                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                                else
                                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                            }
                        });
                    }

                    @Override
                    public void setFullScreen(final boolean enable) {
                        Log.i(TAG, "setFullScreen " + enable);
                        Window window = MainActivity.this.getWindow();
                        final View decorView = window.getDecorView();
                        final int flags = View.SYSTEM_UI_FLAG_IMMERSIVE
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;

                        decorView.post(new Runnable() {
                            @Override
                            public void run() {
                                if (enable)
                                    decorView.setSystemUiVisibility(decorView.getSystemUiVisibility() | flags);
                                else
                                    decorView.setSystemUiVisibility(decorView.getSystemUiVisibility() & ~flags);
                            }
                        });

                        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            window.setStatusBarColor(Color.parseColor("#00000099"));
                        }
                    }

                    @SuppressLint("SourceLockedOrientationActivity")
                    @Override
                    public void lockOrientation(String orientation) {
                        Log.i(TAG, "lockOrientation " + orientation);
                        switch(orientation) {
                            case "landscape":
                                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                                break;
                            case "portrait":
                                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                                break;
                            case "auto":
                                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                                break;
                        }
                    }
                };
                if (_executionEnvironment != null)
                    _executionEnvironment.setRenderer(_uiRenderer);
            }
        }

        private void fullRedraw() {
            _executionEnvironment.update(_executionEnvironment.getRootElement());
            _executionEnvironment.paint();
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.i(TAG, "surface changed " + holder.getSurfaceFrame());
            synchronized (MainActivity.this) {
                _surfaceFrame = holder.getSurfaceFrame();
                if (_executionEnvironment != null) {
                    _executionEnvironment.setSurfaceFrame(_surfaceFrame);
                    fullRedraw();
                }
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.i(TAG, "surface destroyed");
            synchronized (MainActivity.this) {
                if (_executionEnvironment != null)
                    _executionEnvironment.setRenderer(null);
                _surfaceFrame = null;
            }
        }

        @Override
        public void surfaceRedrawNeeded(SurfaceHolder holder) {
            Log.i(TAG, "redraw needed");
            if (_executionEnvironment != null)
                fullRedraw();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getSupportActionBar().hide();
        setContentView(R.layout.activity_main);

        _imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        _mainView = (SurfaceView) findViewById(R.id.contextView);

        SurfaceHolder surfaceHolder = _mainView.getHolder();
        surfaceHolder.setFormat(PixelFormat.TRANSLUCENT);
        _mainView.setZOrderMediaOverlay(true);

        surfaceHolder.addCallback(new SurfaceHolderCallback());

        _mainView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                Log.i(TAG, "motion " + event.toString());
                if (_executionEnvironment == null)
                    return false;
                try {
                    return _executionEnvironment.sendEvent(event).get();
                } catch (Exception e) {
                    Log.e(TAG, "execution exception", e);
                    return false;
                }
            }
        });

        bindService(new Intent(this,
                ExecutionEnvironment.class), _executionEnvironmentConnection, Context.BIND_AUTO_CREATE | Context.BIND_ADJUST_WITH_ACTIVITY);
        _executionEnvironmentBound = true;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.i(TAG, "onConfigurationChanged: keyboard: " + newConfig.keyboard + ", hidden: " + newConfig.keyboardHidden + ", hard keyboard hidden: " + newConfig.hardKeyboardHidden);
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!_showSoftKeyboard && _executionEnvironment.isUiInputBlocked()) {
            Log.v(TAG, "ui input blocked");
            return super.dispatchKeyEvent(event);
        }

        String keyName = GetKeyName(event.getKeyCode());
        if (keyName == null) {
            Log.v(TAG, "unknown key name for code " + event.getKeyCode());
            return super.dispatchKeyEvent(event);
        }

        View focusedView = _executionEnvironment.getFocusedView();
        boolean dpadCenterToInput = (focusedView != null) && (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER);

        switch (event.getAction()) {
            case KeyEvent.ACTION_DOWN: {
                _keyDownHandled = false;
                if (dpadCenterToInput) {
                    Log.d(TAG, "let input open IME...");
                    _imm.showSoftInput(focusedView, InputMethodManager.SHOW_FORCED);
                    _keyDownHandled = true;
                    _showSoftKeyboard = true;
                    _executionEnvironment.blockUiInput(true);
                } else if (_executionEnvironment != null) {
                    try {
                        _keyDownHandled = _executionEnvironment.sendEvent(keyName, event).get();
                    } catch (Exception e) {
                        Log.e(TAG, "execution exception", e);
                    }
                }
                break;
            }
            case KeyEvent.ACTION_UP:
                if (dpadCenterToInput) {
                    Log.d(TAG, "IME activation button ACTION_UP");
                    _showSoftKeyboard = false;
                    return true;
                }
                if (_showSoftKeyboard) {
                    Log.d(TAG, "IME activation button ACTION_UP");
                    return true;
                }
                break;
        }

        if (_keyDownHandled)
            return true;

        Log.v(TAG, "returning key to system");
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        boolean result = false;
        try {
            result = _executionEnvironment.getExecutor().submit(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    Log.d(TAG, "back pressed, calling Context.processKey");
                    Element context = _executionEnvironment.getRootElement();
                    return context.emitUntilTrue(null, "keydown", "Back");
                }
            }).get();
            Log.d(TAG, "key handler finishes with " + result);
        } catch (ExecutionException e) {
            Log.e(TAG, "onBackPressed", e);
        } catch (InterruptedException e) {
            Log.e(TAG, "onBackPressed", e);
        }
        if (!result)
            super.onBackPressed();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, "stopping main activity...");
        if (_executionEnvironment != null)
            _executionEnvironment.releaseResource();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG, "starting main activity...");
        if (_executionEnvironment != null)
            _executionEnvironment.acquireResource();
    }

    @Override
    protected void onDestroy() {
        if (_executionEnvironmentBound)
            unbindService(_executionEnvironmentConnection);
        _mainView = null;
        super.onDestroy();
    }

    @Override
    public void onTrimMemory(int level) {
        switch(level)
        {
            case TRIM_MEMORY_RUNNING_MODERATE:
                Log.i(TAG, "trim memory, TRIM_MEMORY_RUNNING_MODERATE");
                break;
            case TRIM_MEMORY_RUNNING_LOW:
                Log.i(TAG, "trim memory, TRIM_MEMORY_RUNNING_LOW");
                break;
            case TRIM_MEMORY_RUNNING_CRITICAL:
                Log.i(TAG, "trim memory, TRIM_MEMORY_RUNNING_CRITICAL");
                break;
            case TRIM_MEMORY_UI_HIDDEN:
                Log.i(TAG, "trim memory, TRIM_MEMORY_UI_HIDDEN");
                break;
            case TRIM_MEMORY_BACKGROUND:
                Log.i(TAG, "trim memory, TRIM_MEMORY_BACKGROUND");
                break;
            case TRIM_MEMORY_MODERATE:
                Log.i(TAG, "trim memory, TRIM_MEMORY_MODERATE");
                break;
            case TRIM_MEMORY_COMPLETE:
                Log.i(TAG, "trim memory, TRIM_MEMORY_COMPLETE");
                break;
            default:
                Log.i(TAG, "trim memory, level: " + level);
        }
        super.onTrimMemory(level);
    }

    static final String GetKeyName(int keyCode) {
        String keyName = GetKeyNameImpl(keyCode);
        if (keyName == null)
            return null;

        switch(keyName)
        {
            case "DpadUp":
            case "DpadDown":
            case "DpadLeft":
            case "DpadRight":
                return keyName.substring(4);
            case "DpadCenter":
            case "Enter":
                return "Select";
            default:
                return keyName;
        }
    }

    static final String GetKeyNameImpl(int keyCode) {
        switch(keyCode) {
           case KeyEvent.KEYCODE_UNKNOWN: return "Unknown";
            case KeyEvent.KEYCODE_SOFT_LEFT: return "SoftLeft";
            case KeyEvent.KEYCODE_SOFT_RIGHT: return "SoftRight";
            case KeyEvent.KEYCODE_HOME: return "Home";
            case KeyEvent.KEYCODE_BACK: return "Back";
            case KeyEvent.KEYCODE_CALL: return "Call";
            case KeyEvent.KEYCODE_ENDCALL: return "Endcall";
            case KeyEvent.KEYCODE_0: return "0";
            case KeyEvent.KEYCODE_1: return "1";
            case KeyEvent.KEYCODE_2: return "2";
            case KeyEvent.KEYCODE_3: return "3";
            case KeyEvent.KEYCODE_4: return "4";
            case KeyEvent.KEYCODE_5: return "5";
            case KeyEvent.KEYCODE_6: return "6";
            case KeyEvent.KEYCODE_7: return "7";
            case KeyEvent.KEYCODE_8: return "8";
            case KeyEvent.KEYCODE_9: return "9";
            case KeyEvent.KEYCODE_STAR: return "Star";
            case KeyEvent.KEYCODE_POUND: return "Pound";
            case KeyEvent.KEYCODE_DPAD_UP: return "DpadUp";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "DpadDown";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "DpadLeft";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "DpadRight";
            case KeyEvent.KEYCODE_DPAD_CENTER: return "DpadCenter";
            case KeyEvent.KEYCODE_VOLUME_UP: return "VolumeUp";
            case KeyEvent.KEYCODE_VOLUME_DOWN: return "VolumeDown";
            case KeyEvent.KEYCODE_POWER: return "Power";
            case KeyEvent.KEYCODE_CAMERA: return "Camera";
            case KeyEvent.KEYCODE_CLEAR: return "Clear";
            case KeyEvent.KEYCODE_A: return "A";
            case KeyEvent.KEYCODE_B: return "B";
            case KeyEvent.KEYCODE_C: return "C";
            case KeyEvent.KEYCODE_D: return "D";
            case KeyEvent.KEYCODE_E: return "E";
            case KeyEvent.KEYCODE_F: return "F";
            case KeyEvent.KEYCODE_G: return "G";
            case KeyEvent.KEYCODE_H: return "H";
            case KeyEvent.KEYCODE_I: return "I";
            case KeyEvent.KEYCODE_J: return "J";
            case KeyEvent.KEYCODE_K: return "K";
            case KeyEvent.KEYCODE_L: return "L";
            case KeyEvent.KEYCODE_M: return "M";
            case KeyEvent.KEYCODE_N: return "N";
            case KeyEvent.KEYCODE_O: return "O";
            case KeyEvent.KEYCODE_P: return "P";
            case KeyEvent.KEYCODE_Q: return "Q";
            case KeyEvent.KEYCODE_R: return "R";
            case KeyEvent.KEYCODE_S: return "S";
            case KeyEvent.KEYCODE_T: return "T";
            case KeyEvent.KEYCODE_U: return "U";
            case KeyEvent.KEYCODE_V: return "V";
            case KeyEvent.KEYCODE_W: return "W";
            case KeyEvent.KEYCODE_X: return "X";
            case KeyEvent.KEYCODE_Y: return "Y";
            case KeyEvent.KEYCODE_Z: return "Z";
            case KeyEvent.KEYCODE_COMMA: return "Comma";
            case KeyEvent.KEYCODE_PERIOD: return "Period";
            case KeyEvent.KEYCODE_ALT_LEFT: return "AltLeft";
            case KeyEvent.KEYCODE_ALT_RIGHT: return "AltRight";
            case KeyEvent.KEYCODE_SHIFT_LEFT: return "ShiftLeft";
            case KeyEvent.KEYCODE_SHIFT_RIGHT: return "ShiftRight";
            case KeyEvent.KEYCODE_TAB: return "Tab";
            case KeyEvent.KEYCODE_SPACE: return "Space";
            case KeyEvent.KEYCODE_SYM: return "Sym";
            case KeyEvent.KEYCODE_EXPLORER: return "Explorer";
            case KeyEvent.KEYCODE_ENVELOPE: return "Envelope";
            case KeyEvent.KEYCODE_ENTER: return "Enter";
            case KeyEvent.KEYCODE_DEL: return "Del";
            case KeyEvent.KEYCODE_GRAVE: return "Grave";
            case KeyEvent.KEYCODE_MINUS: return "Minus";
            case KeyEvent.KEYCODE_EQUALS: return "Equals";
            case KeyEvent.KEYCODE_LEFT_BRACKET: return "LeftBracket";
            case KeyEvent.KEYCODE_RIGHT_BRACKET: return "RightBracket";
            case KeyEvent.KEYCODE_BACKSLASH: return "Backslash";
            case KeyEvent.KEYCODE_SEMICOLON: return "Semicolon";
            case KeyEvent.KEYCODE_APOSTROPHE: return "Apostrophe";
            case KeyEvent.KEYCODE_SLASH: return "Slash";
            case KeyEvent.KEYCODE_AT: return "At";
            case KeyEvent.KEYCODE_NUM: return "Num";
            case KeyEvent.KEYCODE_HEADSETHOOK: return "Headsethook";
            case KeyEvent.KEYCODE_FOCUS: return "Focus";
            case KeyEvent.KEYCODE_PLUS: return "Plus";
            case KeyEvent.KEYCODE_MENU: return "Menu";
            case KeyEvent.KEYCODE_NOTIFICATION: return "Notification";
            case KeyEvent.KEYCODE_SEARCH: return "Search";
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: return "MediaPlayPause";
            case KeyEvent.KEYCODE_MEDIA_STOP: return "MediaStop";
            case KeyEvent.KEYCODE_MEDIA_NEXT: return "MediaNext";
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS: return "MediaPrevious";
            case KeyEvent.KEYCODE_MEDIA_REWIND: return "MediaRewind";
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: return "MediaFastForward";
            case KeyEvent.KEYCODE_MUTE: return "Mute";
            case KeyEvent.KEYCODE_PAGE_UP: return "PageUp";
            case KeyEvent.KEYCODE_PAGE_DOWN: return "PageDown";
            case KeyEvent.KEYCODE_PICTSYMBOLS: return "Pictsymbols";
            case KeyEvent.KEYCODE_SWITCH_CHARSET: return "SwitchCharset";
            case KeyEvent.KEYCODE_BUTTON_A: return "ButtonA";
            case KeyEvent.KEYCODE_BUTTON_B: return "ButtonB";
            case KeyEvent.KEYCODE_BUTTON_C: return "ButtonC";
            case KeyEvent.KEYCODE_BUTTON_X: return "ButtonX";
            case KeyEvent.KEYCODE_BUTTON_Y: return "ButtonY";
            case KeyEvent.KEYCODE_BUTTON_Z: return "ButtonZ";
            case KeyEvent.KEYCODE_BUTTON_L1: return "ButtonL1";
            case KeyEvent.KEYCODE_BUTTON_R1: return "ButtonR1";
            case KeyEvent.KEYCODE_BUTTON_L2: return "ButtonL2";
            case KeyEvent.KEYCODE_BUTTON_R2: return "ButtonR2";
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return "ButtonThumbl";
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return "ButtonThumbr";
            case KeyEvent.KEYCODE_BUTTON_START: return "ButtonStart";
            case KeyEvent.KEYCODE_BUTTON_SELECT: return "ButtonSelect";
            case KeyEvent.KEYCODE_BUTTON_MODE: return "ButtonMode";
            case KeyEvent.KEYCODE_ESCAPE: return "Escape";
            case KeyEvent.KEYCODE_FORWARD_DEL: return "ForwardDel";
            case KeyEvent.KEYCODE_CTRL_LEFT: return "CtrlLeft";
            case KeyEvent.KEYCODE_CTRL_RIGHT: return "CtrlRight";
            case KeyEvent.KEYCODE_CAPS_LOCK: return "CapsLock";
            case KeyEvent.KEYCODE_SCROLL_LOCK: return "ScrollLock";
            case KeyEvent.KEYCODE_META_LEFT: return "MetaLeft";
            case KeyEvent.KEYCODE_META_RIGHT: return "MetaRight";
            case KeyEvent.KEYCODE_FUNCTION: return "Function";
            case KeyEvent.KEYCODE_SYSRQ: return "Sysrq";
            case KeyEvent.KEYCODE_BREAK: return "Break";
            case KeyEvent.KEYCODE_MOVE_HOME: return "MoveHome";
            case KeyEvent.KEYCODE_MOVE_END: return "MoveEnd";
            case KeyEvent.KEYCODE_INSERT: return "Insert";
            case KeyEvent.KEYCODE_FORWARD: return "Forward";
            case KeyEvent.KEYCODE_MEDIA_PLAY: return "MediaPlay";
            case KeyEvent.KEYCODE_MEDIA_PAUSE: return "MediaPause";
            case KeyEvent.KEYCODE_MEDIA_CLOSE: return "MediaClose";
            case KeyEvent.KEYCODE_MEDIA_EJECT: return "MediaEject";
            case KeyEvent.KEYCODE_MEDIA_RECORD: return "MediaRecord";
            case KeyEvent.KEYCODE_F1: return "F1";
            case KeyEvent.KEYCODE_F2: return "F2";
            case KeyEvent.KEYCODE_F3: return "F3";
            case KeyEvent.KEYCODE_F4: return "F4";
            case KeyEvent.KEYCODE_F5: return "F5";
            case KeyEvent.KEYCODE_F6: return "F6";
            case KeyEvent.KEYCODE_F7: return "F7";
            case KeyEvent.KEYCODE_F8: return "F8";
            case KeyEvent.KEYCODE_F9: return "F9";
            case KeyEvent.KEYCODE_F10: return "F10";
            case KeyEvent.KEYCODE_F11: return "F11";
            case KeyEvent.KEYCODE_F12: return "F12";
            case KeyEvent.KEYCODE_NUM_LOCK: return "NumLock";
            case KeyEvent.KEYCODE_NUMPAD_0: return "Numpad0";
            case KeyEvent.KEYCODE_NUMPAD_1: return "Numpad1";
            case KeyEvent.KEYCODE_NUMPAD_2: return "Numpad2";
            case KeyEvent.KEYCODE_NUMPAD_3: return "Numpad3";
            case KeyEvent.KEYCODE_NUMPAD_4: return "Numpad4";
            case KeyEvent.KEYCODE_NUMPAD_5: return "Numpad5";
            case KeyEvent.KEYCODE_NUMPAD_6: return "Numpad6";
            case KeyEvent.KEYCODE_NUMPAD_7: return "Numpad7";
            case KeyEvent.KEYCODE_NUMPAD_8: return "Numpad8";
            case KeyEvent.KEYCODE_NUMPAD_9: return "Numpad9";
            case KeyEvent.KEYCODE_NUMPAD_DIVIDE: return "NumpadDivide";
            case KeyEvent.KEYCODE_NUMPAD_MULTIPLY: return "NumpadMultiply";
            case KeyEvent.KEYCODE_NUMPAD_SUBTRACT: return "NumpadSubtract";
            case KeyEvent.KEYCODE_NUMPAD_ADD: return "NumpadAdd";
            case KeyEvent.KEYCODE_NUMPAD_DOT: return "NumpadDot";
            case KeyEvent.KEYCODE_NUMPAD_COMMA: return "NumpadComma";
            case KeyEvent.KEYCODE_NUMPAD_ENTER: return "NumpadEnter";
            case KeyEvent.KEYCODE_NUMPAD_EQUALS: return "NumpadEquals";
            case KeyEvent.KEYCODE_NUMPAD_LEFT_PAREN: return "NumpadLeftParen";
            case KeyEvent.KEYCODE_NUMPAD_RIGHT_PAREN: return "NumpadRightParen";
            case KeyEvent.KEYCODE_VOLUME_MUTE: return "VolumeMute";
            case KeyEvent.KEYCODE_INFO: return "Info";
            case KeyEvent.KEYCODE_CHANNEL_UP: return "ChannelUp";
            case KeyEvent.KEYCODE_CHANNEL_DOWN: return "ChannelDown";
            case KeyEvent.KEYCODE_ZOOM_IN: return "ZoomIn";
            case KeyEvent.KEYCODE_ZOOM_OUT: return "ZoomOut";
            case KeyEvent.KEYCODE_TV: return "Tv";
            case KeyEvent.KEYCODE_WINDOW: return "Window";
            case KeyEvent.KEYCODE_GUIDE: return "Guide";
            case KeyEvent.KEYCODE_DVR: return "Dvr";
            case KeyEvent.KEYCODE_BOOKMARK: return "Bookmark";
            case KeyEvent.KEYCODE_CAPTIONS: return "Captions";
            case KeyEvent.KEYCODE_SETTINGS: return "Settings";
            case KeyEvent.KEYCODE_TV_POWER: return "TvPower";
            case KeyEvent.KEYCODE_TV_INPUT: return "TvInput";
            case KeyEvent.KEYCODE_STB_POWER: return "StbPower";
            case KeyEvent.KEYCODE_STB_INPUT: return "StbInput";
            case KeyEvent.KEYCODE_AVR_POWER: return "AvrPower";
            case KeyEvent.KEYCODE_AVR_INPUT: return "AvrInput";
            case KeyEvent.KEYCODE_PROG_RED: return "ProgRed";
            case KeyEvent.KEYCODE_PROG_GREEN: return "ProgGreen";
            case KeyEvent.KEYCODE_PROG_YELLOW: return "ProgYellow";
            case KeyEvent.KEYCODE_PROG_BLUE: return "ProgBlue";
            case KeyEvent.KEYCODE_APP_SWITCH: return "AppSwitch";
            case KeyEvent.KEYCODE_BUTTON_1: return "Button1";
            case KeyEvent.KEYCODE_BUTTON_2: return "Button2";
            case KeyEvent.KEYCODE_BUTTON_3: return "Button3";
            case KeyEvent.KEYCODE_BUTTON_4: return "Button4";
            case KeyEvent.KEYCODE_BUTTON_5: return "Button5";
            case KeyEvent.KEYCODE_BUTTON_6: return "Button6";
            case KeyEvent.KEYCODE_BUTTON_7: return "Button7";
            case KeyEvent.KEYCODE_BUTTON_8: return "Button8";
            case KeyEvent.KEYCODE_BUTTON_9: return "Button9";
            case KeyEvent.KEYCODE_BUTTON_10: return "Button10";
            case KeyEvent.KEYCODE_BUTTON_11: return "Button11";
            case KeyEvent.KEYCODE_BUTTON_12: return "Button12";
            case KeyEvent.KEYCODE_BUTTON_13: return "Button13";
            case KeyEvent.KEYCODE_BUTTON_14: return "Button14";
            case KeyEvent.KEYCODE_BUTTON_15: return "Button15";
            case KeyEvent.KEYCODE_BUTTON_16: return "Button16";
            case KeyEvent.KEYCODE_LANGUAGE_SWITCH: return "LanguageSwitch";
            case KeyEvent.KEYCODE_MANNER_MODE: return "MannerMode";
            case KeyEvent.KEYCODE_3D_MODE: return "3dMode";
            case KeyEvent.KEYCODE_CONTACTS: return "Contacts";
            case KeyEvent.KEYCODE_CALENDAR: return "Calendar";
            case KeyEvent.KEYCODE_MUSIC: return "Music";
            case KeyEvent.KEYCODE_CALCULATOR: return "Calculator";
            case KeyEvent.KEYCODE_ZENKAKU_HANKAKU: return "ZenkakuHankaku";
            case KeyEvent.KEYCODE_EISU: return "Eisu";
            case KeyEvent.KEYCODE_MUHENKAN: return "Muhenkan";
            case KeyEvent.KEYCODE_HENKAN: return "Henkan";
            case KeyEvent.KEYCODE_KATAKANA_HIRAGANA: return "KatakanaHiragana";
            case KeyEvent.KEYCODE_YEN: return "Yen";
            case KeyEvent.KEYCODE_RO: return "Ro";
            case KeyEvent.KEYCODE_KANA: return "Kana";
            case KeyEvent.KEYCODE_ASSIST: return "Assist";
            case KeyEvent.KEYCODE_BRIGHTNESS_DOWN: return "BrightnessDown";
            case KeyEvent.KEYCODE_BRIGHTNESS_UP: return "BrightnessUp";
            case KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK: return "MediaAudioTrack";
            case KeyEvent.KEYCODE_SLEEP: return "Sleep";
            case KeyEvent.KEYCODE_WAKEUP: return "Wakeup";
            case KeyEvent.KEYCODE_PAIRING: return "Pairing";
            case KeyEvent.KEYCODE_MEDIA_TOP_MENU: return "MediaTopMenu";
            case KeyEvent.KEYCODE_11: return "11";
            case KeyEvent.KEYCODE_12: return "12";
            case KeyEvent.KEYCODE_LAST_CHANNEL: return "LastChannel";
            case KeyEvent.KEYCODE_TV_DATA_SERVICE: return "TvDataService";
            case KeyEvent.KEYCODE_VOICE_ASSIST: return "VoiceAssist";
            case KeyEvent.KEYCODE_TV_RADIO_SERVICE: return "TvRadioService";
            case KeyEvent.KEYCODE_TV_TELETEXT: return "TvTeletext";
            case KeyEvent.KEYCODE_TV_NUMBER_ENTRY: return "TvNumberEntry";
            case KeyEvent.KEYCODE_TV_TERRESTRIAL_ANALOG: return "TvTerrestrialAnalog";
            case KeyEvent.KEYCODE_TV_TERRESTRIAL_DIGITAL: return "TvTerrestrialDigital";
            case KeyEvent.KEYCODE_TV_SATELLITE: return "TvSatellite";
            case KeyEvent.KEYCODE_TV_SATELLITE_BS: return "TvSatelliteBs";
            case KeyEvent.KEYCODE_TV_SATELLITE_CS: return "TvSatelliteCs";
            case KeyEvent.KEYCODE_TV_SATELLITE_SERVICE: return "TvSatelliteService";
            case KeyEvent.KEYCODE_TV_NETWORK: return "TvNetwork";
            case KeyEvent.KEYCODE_TV_ANTENNA_CABLE: return "TvAntennaCable";
            case KeyEvent.KEYCODE_TV_INPUT_HDMI_1: return "TvInputHdmi1";
            case KeyEvent.KEYCODE_TV_INPUT_HDMI_2: return "TvInputHdmi2";
            case KeyEvent.KEYCODE_TV_INPUT_HDMI_3: return "TvInputHdmi3";
            case KeyEvent.KEYCODE_TV_INPUT_HDMI_4: return "TvInputHdmi4";
            case KeyEvent.KEYCODE_TV_INPUT_COMPOSITE_1: return "TvInputComposite1";
            case KeyEvent.KEYCODE_TV_INPUT_COMPOSITE_2: return "TvInputComposite2";
            case KeyEvent.KEYCODE_TV_INPUT_COMPONENT_1: return "TvInputComponent1";
            case KeyEvent.KEYCODE_TV_INPUT_COMPONENT_2: return "TvInputComponent2";
            case KeyEvent.KEYCODE_TV_INPUT_VGA_1: return "TvInputVga1";
            case KeyEvent.KEYCODE_TV_AUDIO_DESCRIPTION: return "TvAudioDescription";
            case KeyEvent.KEYCODE_TV_AUDIO_DESCRIPTION_MIX_UP: return "TvAudioDescriptionMixUp";
            case KeyEvent.KEYCODE_TV_AUDIO_DESCRIPTION_MIX_DOWN: return "TvAudioDescriptionMixDown";
            case KeyEvent.KEYCODE_TV_ZOOM_MODE: return "TvZoomMode";
            case KeyEvent.KEYCODE_TV_CONTENTS_MENU: return "TvContentsMenu";
            case KeyEvent.KEYCODE_TV_MEDIA_CONTEXT_MENU: return "TvMediaContextMenu";
            case KeyEvent.KEYCODE_TV_TIMER_PROGRAMMING: return "TvTimerProgramming";
            case KeyEvent.KEYCODE_HELP: return "Help";
            case KeyEvent.KEYCODE_NAVIGATE_PREVIOUS: return "NavigatePrevious";
            case KeyEvent.KEYCODE_NAVIGATE_NEXT: return "NavigateNext";
            case KeyEvent.KEYCODE_NAVIGATE_IN: return "NavigateIn";
            case KeyEvent.KEYCODE_NAVIGATE_OUT: return "NavigateOut";
            case KeyEvent.KEYCODE_STEM_PRIMARY: return "StemPrimary";
            case KeyEvent.KEYCODE_STEM_1: return "Stem1";
            case KeyEvent.KEYCODE_STEM_2: return "Stem2";
            case KeyEvent.KEYCODE_STEM_3: return "Stem3";
            case KeyEvent.KEYCODE_DPAD_UP_LEFT: return "DpadUpLeft";
            case KeyEvent.KEYCODE_DPAD_DOWN_LEFT: return "DpadDownLeft";
            case KeyEvent.KEYCODE_DPAD_UP_RIGHT: return "DpadUpRight";
            case KeyEvent.KEYCODE_DPAD_DOWN_RIGHT: return "DpadDownRight";
            case KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD: return "MediaSkipForward";
            case KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD: return "MediaSkipBackward";
            case KeyEvent.KEYCODE_MEDIA_STEP_FORWARD: return "MediaStepForward";
            case KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD: return "MediaStepBackward";
            case KeyEvent.KEYCODE_SOFT_SLEEP: return "SoftSleep";
            case KeyEvent.KEYCODE_CUT: return "Cut";
            case KeyEvent.KEYCODE_COPY: return "Copy";
            case KeyEvent.KEYCODE_PASTE: return "Paste";
            case KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP: return "SystemNavigationUp";
            case KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN: return "SystemNavigationDown";
            case KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT: return "SystemNavigationLeft";
            case KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT: return "SystemNavigationRight";
            default:
                return null;
        }
    }
}
