package com.pureqml.android.runtime;

import android.content.Context;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;

import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Function;
import com.eclipsesource.v8.V8Object;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.pureqml.android.IExecutionEnvironment;


public final class VideoPlayer extends BaseObject {
    private static final String TAG = "VideoPlayer";
    private SimpleExoPlayer             player;
    private PlayerView                  view;
    private ViewHolder<PlayerView>      viewHolder;

    public VideoPlayer(IExecutionEnvironment env) {
        super(env);
        Context context = env.getContext();
        player = ExoPlayerFactory.newSimpleInstance(context,
                new DefaultTrackSelector()
        );
        view = new PlayerView(_env.getContext());
        view.setPlayer(player);
        viewHolder = new ViewHolder<PlayerView>(context, view);
    }

    public void setupDrm(String type, V8Object options, V8Function callback, V8Function error) {
        Log.i(TAG, "Player.SetupDRM " + type);
    }

    public void stop() {
        Log.i(TAG, "Player.stop");
        player.stop();
    }

    public void setSource(String url) {
        Log.i(TAG, "Player.setSource " + url);
        DataSource.Factory factory = new DefaultDataSourceFactory(_env.getContext(), Util.getUserAgent(_env.getContext(), "pureqml"));
        if (url.endsWith("m3u8"))
            player.prepare(new HlsMediaSource.Factory(factory).createMediaSource(Uri.parse(url)));
        else
            player.prepare(new ExtractorMediaSource.Factory(factory).createMediaSource(Uri.parse(url)));
        Log.i(TAG, "Player.setSource exited");
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
        player.seekTo(pos);
    }

    public void setOption(String name, Object value) {
        Log.i(TAG, "Player.setOption " + name + " : " + value);
        if (name.equals("autoplay"))
            player.setPlayWhenReady(TypeConverter.toBoolean(value));
        else
            Log.w(TAG, "ignoring option " + name);
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
        viewHolder.setRect(_env.getRootView(), new Rect(l, t, r, b));
    }

    public void setVisibility(boolean visible) {
        Log.i(TAG, "Player.setVisibility " + visible);
        viewHolder.update(_env.getRootView(), visible);
    }
}
