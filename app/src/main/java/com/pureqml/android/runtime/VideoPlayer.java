package com.pureqml.android.runtime;

import android.graphics.Color;
import android.util.Log;

import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Function;
import com.eclipsesource.v8.V8Object;
import com.pureqml.android.IExecutionEnvironment;

public class VideoPlayer extends Element {
    private static final String TAG = "VideoPlayer";

    public VideoPlayer(IExecutionEnvironment env) {
        super(env);
    }

    public void setupDrm(String type, V8Object options, V8Function callback, V8Function error) {
        Log.i(TAG, "Player.SetupDRM " + type);
    }

    public void stop() {
        Log.i(TAG, "Player.stop");
    }

    public void setSource(String url) {
        Log.i(TAG, "Player.setSource " + url);
    }

    public void setLoop(boolean loop) {
        Log.i(TAG, "Player.setLoop " + loop);
    }

    public void setBackgroundColor(String color) {
        Log.i(TAG, "Player.setBackground " + color);
    }

    public void play() {
        Log.i(TAG, "Player.play");
    }

    public void seek(int pos) {
        Log.i(TAG, "Player.seek " + pos);
    }

    public void seekTo(int pos) {
        Log.i(TAG, "Player.seekTo " + pos);
    }

    public void setOption(String name, String value) {
        Log.i(TAG, "Player.setOption " + name + " : " + value);
    }

    public Object getVideoTracks() {
        return new V8Array(_env.getRuntime());
    }

    public Object getAudioTracks() {
        return new V8Array(_env.getRuntime());
    }

    public void setAudioTrack(String trackId) {
        Log.i(TAG, "Player.setAudioTrack " + trackId);
    }

    public void setVideoTrack(String trackId) {
        Log.i(TAG, "Player.setVideoTrack " + trackId);
    }

    public void setVolume(int volume) {
        Log.i(TAG, "Player.setVolume " + volume);
    }

    public void setMute(boolean muted) {
        Log.i(TAG, "Player.setMute " + muted);
    }

    public void setRect(int l, int t, int r, int b) {
        Log.i(TAG, "Player.setRect " + l + ", " + t + ", " + r + ", " + b);
    }

    public void setVisibility(boolean visible) {
        Log.i(TAG, "Player.setVisibility " + visible);
    }
}
