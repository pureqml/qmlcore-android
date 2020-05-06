package com.pureqml.android.runtime;

import android.content.Context;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.SurfaceView;

import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Function;
import com.eclipsesource.v8.V8Object;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory;
import com.google.android.exoplayer2.source.BaseMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.hls.DefaultHlsExtractorFactory;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.pureqml.android.IExecutionEnvironment;
import com.pureqml.android.IResource;
import com.pureqml.android.TypeConverter;

import java.io.IOException;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;


public final class VideoPlayer extends BaseObject implements IResource {
    private static final String TAG = "VideoPlayer";
    private static int PollingInterval = 500; //ms

    private SimpleExoPlayer             player;
    private SurfaceView                 view;
    private ViewHolder<SurfaceView>     viewHolder;

    //this is persistent state
    private Rect                        rect;
    private String                      source;
    private boolean                     playerVisible = true;
    private boolean                     autoplay = false;
    private boolean                     paused = false;
    private TimerTask                   pollingTask = null;

    public VideoPlayer(IExecutionEnvironment env) {
        super(env);
        Context context = env.getContext();
        view = new SurfaceView(context);
        viewHolder = new ViewHolder<SurfaceView>(context, view);
        this.acquireResource();
        _env.register(this);
    }

    private void emitError(final String error) {
        _env.getExecutor().execute(new Runnable() {
            @Override
            public void run() {
                VideoPlayer.this.emit(null, "error", error);
            }
        });
    }

    private void pollPosition() {
        _env.getExecutor().execute(new Runnable() {
            @Override
            public void run() {
                SimpleExoPlayer player = VideoPlayer.this.player;
                if (player != null) {
                    double position = player.getCurrentPosition() / 1000.0;
                    double duration = player.getDuration() / 1000.0;
                    if (duration > 0) {
                        Log.v(TAG, "emitting position " + position + " / " + duration);
                        VideoPlayer.this.emit(null, "timeupdate", position);
                        VideoPlayer.this.emit(null, "durationchange", duration);
                    }
                }
            }
        });
    }

    @Override
    public void acquireResource() {
        if (player != null)
            return;

        Context context = _env.getContext();
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setPrioritizeTimeOverSizeThresholds(true)
                .createDefaultLoadControl();

        DefaultTrackSelector trackSelector = new DefaultTrackSelector(context);
        trackSelector.setParameters(
                trackSelector.buildUponParameters()
                        .setExceedRendererCapabilitiesIfNecessary(true)
                        .setAllowVideoMixedMimeTypeAdaptiveness(true)
                        .setAllowAudioMixedMimeTypeAdaptiveness(true)
                        .setAllowVideoNonSeamlessAdaptiveness(true)
        );

        player = new SimpleExoPlayer.Builder(context)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .build();

        player.setVideoSurfaceView(view);

        final ExecutorService executor = _env.getExecutor();
        player.addListener(new Player.EventListener() {
            @Override
            public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
                Log.d(TAG, "onTracksChanged" + trackGroups + " " + trackSelections);
            }

            @Override
            public void onLoadingChanged(boolean isLoading) {
                Log.d(TAG, "onLoadingChanged " + isLoading);
            }

            @Override
            public void onPlayerStateChanged(final boolean playWhenReady, final int playbackState) {
                Log.d(TAG, "onPlayerStateChanged " + playbackState);
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        VideoPlayer.this.emit(null, "stateChanged", playbackState);
                        if (playWhenReady && playbackState == Player.STATE_BUFFERING)
                            VideoPlayer.this.emit(null, "stateChanged", Player.STATE_READY);
                    }
                });
            }

            @Override
            public void onRepeatModeChanged(int repeatMode) {
                Log.d(TAG, "onRepeatModeChanged " + repeatMode);
            }

            @Override
            public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
                Log.d(TAG, "onShuffleModeEnabledChanged " + shuffleModeEnabled);
            }

            @Override
            public void onPlayerError(final ExoPlaybackException error) {
                Log.d(TAG, "onPlayerError " + error);
                emitError(error.toString());
            }

            @Override
            public void onPositionDiscontinuity(int reason) {
                Log.d(TAG, "onPositionDiscontinuity " + reason);
            }

            @Override
            public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
                Log.d(TAG, "onPlaybackParametersChanged " + playbackParameters);
            }

            @Override
            public void onSeekProcessed() {
                Log.d(TAG, "onSeekProcessed");
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        VideoPlayer.this.emit(null, "seeked");
                    }
                });
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                Log.v(TAG, "onIsPlayingChanged " + isPlaying);
            }

            @Override
            public void onTimelineChanged(Timeline timeline, int reason) {
                Log.d(TAG, "onTimelineChanged " + timeline + ", reason: " + reason);
            }
        });

        if (rect != null)
            setRect(rect);
        setVisibility(playerVisible);
        player.setPlayWhenReady(autoplay);
        if (source != null)
            setSource(source);

        pollingTask = new TimerTask() {
            @Override
            public void run() {
                VideoPlayer.this.pollPosition();
            }
        };
        _env.getTimer().schedule(pollingTask, PollingInterval, PollingInterval);
    }

    @Override
    public void releaseResource() {
        if (pollingTask != null) {
            try { pollingTask.cancel(); } catch(Exception ex) { }
            pollingTask = null;
        }

        if (player != null) {
            player.setVideoSurfaceView(null);
            player.release();
            player = null;
        }
    }

    public void setupDrm(String type, V8Object options, V8Function callback, V8Function error) {
        Log.i(TAG, "Player.SetupDRM " + type);
    }

    public void stop() {
        Log.i(TAG, "Player.stop");
        source = null;
        if (player != null)
            player.stop();
    }

    public void setSource(String url) {
        Log.i(TAG, "Player.setSource " + url);
        source = url;
        if (player == null)
            return;

        DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(_env.getContext(), Util.getUserAgent(_env.getContext(), "pureqml"));

        BaseMediaSource source;
        if (url.indexOf(".m3u8") >= 0) { //FIXME: add proper content type here
            HlsMediaSource.Factory factory = new HlsMediaSource.Factory(dataSourceFactory);
            factory.setExtractorFactory(new DefaultHlsExtractorFactory(
                    DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS |
                    DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES,
                    true
            ));
            source = factory.createMediaSource(Uri.parse(url));
        } else {
            ProgressiveMediaSource.Factory factory = new ProgressiveMediaSource.Factory(dataSourceFactory);
            source = factory.createMediaSource(Uri.parse(url));
        }

        source.addEventListener(new Handler(_env.getContext().getMainLooper()), new MediaSourceEventListener() {

            @Override
            public void onMediaPeriodCreated(int windowIndex, MediaSource.MediaPeriodId mediaPeriodId) {

            }

            @Override
            public void onMediaPeriodReleased(int windowIndex, MediaSource.MediaPeriodId mediaPeriodId) {

            }

            @Override
            public void onLoadStarted(int windowIndex, @Nullable MediaSource.MediaPeriodId mediaPeriodId, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {

            }

            @Override
            public void onLoadCompleted(int windowIndex, @Nullable MediaSource.MediaPeriodId mediaPeriodId, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {

            }

            @Override
            public void onLoadCanceled(int windowIndex, @Nullable MediaSource.MediaPeriodId mediaPeriodId, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {

            }

            @Override
            public void onLoadError(int windowIndex, @Nullable MediaSource.MediaPeriodId mediaPeriodId, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData, IOException error, boolean wasCanceled) {
                Log.w(TAG, "onLoadError");
                VideoPlayer.this.emitError("Source load error: " + error.getLocalizedMessage());
            }

            @Override
            public void onReadingStarted(int windowIndex, MediaSource.MediaPeriodId mediaPeriodId) {

            }

            @Override
            public void onUpstreamDiscarded(int windowIndex, MediaSource.MediaPeriodId mediaPeriodId, MediaLoadData mediaLoadData) {

            }

            @Override
            public void onDownstreamFormatChanged(int windowIndex, @Nullable MediaSource.MediaPeriodId mediaPeriodId, MediaLoadData mediaLoadData) {

            }
        });
        player.prepare(source, true, true);
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
        if (paused)
        {
            paused = false;
            VideoPlayer.this.emit(null, "pause", paused);
            player.setPlayWhenReady(true);
        }
        else
            Log.i(TAG, "ignoring play on non-paused stream");
    }

    public void pause() {
        Log.i(TAG, "Player.pause");
        if (!paused)
        {
            paused = true;
            VideoPlayer.this.emit(null, "pause", paused);
            player.setPlayWhenReady(false);
        }
        else
            Log.i(TAG, "ignoring pause on paused stream");
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
        if (name.equals("autoplay")) {
            autoplay = TypeConverter.toBoolean(value);
            player.setPlayWhenReady(autoplay);
        } else
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
        setRect(new Rect(l, t, r, b));
    }

    private void setRect(Rect rect) {
        Log.i(TAG, "Player.setRect " + rect);
        viewHolder.setRect(_env.getRootView(), rect);
        this.rect = rect;
    }

    public void setVisibility(boolean visible) {
        playerVisible = visible;
        Log.i(TAG, "Player.setVisibility " + visible);
        viewHolder.update(_env.getRootView(), visible);
    }

}
