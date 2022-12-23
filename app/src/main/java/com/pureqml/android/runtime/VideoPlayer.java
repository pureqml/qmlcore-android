package com.pureqml.android.runtime;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.Nullable;

import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Function;
import com.eclipsesource.v8.V8Object;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory;
import com.google.android.exoplayer2.source.BaseMediaSource;
import com.google.android.exoplayer2.source.BehindLiveWindowException;
import com.google.android.exoplayer2.source.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaLoadData;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.hls.DefaultHlsExtractorFactory;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoSize;
import com.pureqml.android.IExecutionEnvironment;
import com.pureqml.android.IResource;
import com.pureqml.android.SafeRunnable;
import com.pureqml.android.TypeConverter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.google.android.exoplayer2.C.TIME_UNSET;


public final class VideoPlayer extends BaseObject implements IResource {

    private static final class DeferredCallback implements SurfaceHolder.Callback {
        final Handler handler;
        final SurfaceHolder.Callback callback;

        DeferredCallback(Handler handler, SurfaceHolder.Callback callback) {
            this.handler = handler;
            this.callback = callback;
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            handler.post(new SafeRunnable() {
                @Override
                protected void doRun() {
                    callback.surfaceCreated(holder);
                }
            });
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            handler.post(new SafeRunnable() {
                @Override
                protected void doRun() {
                    callback.surfaceChanged(holder, format, width, height);
                }
            });
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            handler.post(new SafeRunnable() {
                @Override
                protected void doRun() {
                    callback.surfaceDestroyed(holder);
                }
            });
        }
    }

    private final class DeferredSurfaceHolder implements SurfaceHolder {
        final Handler handler;
        final SurfaceHolder surfaceHolder;
        final Map<Callback, DeferredCallback> callbacks;

        DeferredSurfaceHolder(Handler handler, SurfaceHolder surfaceHolder) {
            this.handler = handler;
            this.surfaceHolder = surfaceHolder;
            this.callbacks = new HashMap<>();
        }

        @Override
        public void addCallback(Callback callback) {
            DeferredCallback deferredCallback = new DeferredCallback(handler, callback);
            synchronized (this) {
                callbacks.put(callback, deferredCallback);
            }
            surfaceHolder.addCallback(deferredCallback);
        }

        @Override
        public void removeCallback(Callback callback) {
            surfaceHolder.removeCallback(callback);
            DeferredCallback deferredCallback;
            synchronized (this) {
                deferredCallback = callbacks.get(callback);
            }
            surfaceHolder.removeCallback(deferredCallback);
        }

        @Override
        public boolean isCreating() {
            return surfaceHolder.isCreating();
        }

        @Deprecated
        @Override
        public void setType(int type) {
            surfaceHolder.setType(type);
        }

        @Override
        public void setFixedSize(int width, int height) {
            surfaceHolder.setFixedSize(width, height);
        }

        @Override
        public void setSizeFromLayout() {
            surfaceHolder.setSizeFromLayout();
        }

        @Override
        public void setFormat(int format) {
            surfaceHolder.setFormat(format);
        }

        @Override
        public void setKeepScreenOn(boolean screenOn) {
            surfaceHolder.setKeepScreenOn(screenOn);
        }

        @Override
        public Canvas lockCanvas() {
            return surfaceHolder.lockCanvas();
        }

        @Override
        public Canvas lockCanvas(Rect dirty) {
            return surfaceHolder.lockCanvas(dirty);
        }

        @Override
        public void unlockCanvasAndPost(Canvas canvas) {
            surfaceHolder.unlockCanvasAndPost(canvas);
        }

        @Override
        public Rect getSurfaceFrame() {
            return surfaceHolder.getSurfaceFrame();
        }

        @Override
        public Surface getSurface() {
            return surfaceHolder.getSurface();
        }
    };


    private static final String TAG = "VideoPlayer";
    private static final int PollingInterval = 500; //ms

    private ExoPlayer                   player;
    private SurfaceView                 surfaceView;
    private final ViewHolder<?>         viewHolder;
    private final Handler               handler;
    private final Timeline.Period       period;

    //this is persistent state
    private Rect                        rect;
    private int                         videoWidth = 0;
    private int                         videoHeight = 0;
    private String                      source;
    private boolean                     playerVisible = true;
    private boolean                     autoplay = false;
    private boolean                     paused = false;
    private Runnable                    pollingTask = null;

    //exoplayer flags
    private int                         hlsExtractorFlags = 0;
    private boolean                     exposeCea608WhenMissingDeclarations = true;

    public VideoPlayer(IExecutionEnvironment env) {
        super(env);

        HandlerThread thread = new HandlerThread(this.toString());
        thread.start();
        handler = new Handler(thread.getLooper());

        Context context = env.getContext();
        surfaceView = new SurfaceView(context);
        viewHolder = new ViewHolder<>(context, surfaceView);

        period = new Timeline.Period();

        _env.register(this);

        acquireResource();
    }

    public void emit(String name, Object ... args) {
        _env.getExecutor().execute(new SafeRunnable() {
            @Override
            public void doRun() {
                VideoPlayer.this.emit(null, name, args);
            }
        });
    }

    private void pollPosition() {
        handler.post(new SafeRunnable() {
            @Override
            public void doRun() {
                ExoPlayer player = VideoPlayer.this.player;
                if (player == null)
                    return;

                long position = player.getCurrentPosition();
                Timeline currentTimeline = player.getCurrentTimeline();
                if (!currentTimeline.isEmpty()) {
                    position -= currentTimeline.getPeriod(player.getCurrentPeriodIndex(), period)
                            .getPositionInWindowMs();
                }
                final long duration = player.getDuration();
                if (duration != TIME_UNSET) {
                    Log.v(TAG, "emitting position " + position + " / " + duration);
                    VideoPlayer.this.emit("timeupdate",position / 1000.0);
                    VideoPlayer.this.emit("durationchange", duration / 1000.0);
                }
            }
        });
    }

    private static boolean isBehindLiveWindow(PlaybackException e) {
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof BehindLiveWindowException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private void acquireResourceImpl() {
        if (player != null)
            return;

        Context context = _env.getContext();
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setPrioritizeTimeOverSizeThresholds(true)
                .setBufferDurationsMs(1000, 50000, 1000, 1000)
                .build();

        DefaultTrackSelector trackSelector = new DefaultTrackSelector(context);
        trackSelector.setParameters(
                trackSelector.buildUponParameters()
                        .setExceedRendererCapabilitiesIfNecessary(true)
                        .setAllowVideoMixedMimeTypeAdaptiveness(true)
                        .setAllowAudioMixedMimeTypeAdaptiveness(true)
                        .setAllowVideoNonSeamlessAdaptiveness(true)
        );

        player = new ExoPlayer.Builder(context)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .setLooper(handler.getLooper())
                .build();

        player.setVideoSurfaceHolder(
                new DeferredSurfaceHolder(
                        new Handler(handler.getLooper()),
                        surfaceView.getHolder()));

        player.addListener(new Player.Listener() {
            @Override
            public void onIsLoadingChanged(boolean isLoading) {
                Log.d(TAG, "onLoadingChanged " + isLoading);
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                Log.d(TAG, "onPlayerStateChanged " + playbackState);
                VideoPlayer.this.emit("stateChanged", playbackState);
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                Log.d(TAG, "onPlayerError " + error);
                VideoPlayer.this.emit("error", error.toString());
                if (isBehindLiveWindow(error)) {
                    Log.i(TAG, "restarting player");
                    releaseResource();
                    acquireResource();
                }
            }

            @Override
            public void onPositionDiscontinuity(Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
                Log.d(TAG, "onPositionDiscontinuity " + reason);
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    Log.d(TAG, "onSeekProcessed");
                    VideoPlayer.this.emit("seeked");
                }
            }

            @Override
            public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
                Log.d(TAG, "onPlaybackParametersChanged " + playbackParameters);
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                Log.v(TAG, "onIsPlayingChanged " + isPlaying);
            }

            @Override
            public void onVideoSizeChanged(VideoSize videoSize) {
                Log.v(TAG, "onVideoSizeChanged " + videoSize.width + "x" + videoSize.height + ", rotation: " + videoSize.unappliedRotationDegrees + ", par: " + videoSize.pixelWidthHeightRatio);
                videoWidth = (int)(videoSize.width * videoSize.pixelWidthHeightRatio);
                videoHeight = videoSize.height;
                handler.post(new SafeRunnable() {
                    @Override
                    public void doRun() {
                        updateGeometry();
                    }});
            }

            @Override
            public void onSurfaceSizeChanged(int width, int height) {
                Log.v(TAG, "onSurfaceSizeChanged " + width + "x" + height);
            }

            @Override
            public void onRenderedFirstFrame() {
                Log.v(TAG, "onRenderedFirstFrame");
            }
        });

        updateGeometry();
        setVisibility(playerVisible);
        player.setPlayWhenReady(autoplay);
        if (source != null)
            setSource(source);

        pollingTask = new SafeRunnable() {
            @Override
            protected void doRun() {
                VideoPlayer.this.pollPosition();
                if (pollingTask != null) {
                    handler.postDelayed(pollingTask, PollingInterval);
                }
            }
        };
        handler.postDelayed(pollingTask, PollingInterval);
    }

    private void releaseResourceImpl() {
        pollingTask = null;
        if (player != null) {
            player.setVideoSurfaceView(null);
            player.release();
            player = null;
            videoWidth = 0;
            videoHeight = 0;
        }
    }

    public void setupDrm(String type, V8Object options, V8Function callback, V8Function error) {
        Log.i(TAG, "Player.SetupDRM " + type);
    }

    public void stop() {
        Log.i(TAG, "Player.stop");
        source = null;
        handler.post(new SafeRunnable() {
            @Override
            public void doRun() {
                if (player != null)
                    player.stop();
            }
        });
    }

    public void setSource(String url) {
        Log.i(TAG, "Player.setSource " + url);
        source = url;
        if (player == null)
            return;

        if (source == null || source.isEmpty()) {
            stop();
            return;
        }

        DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(_env.getContext());

        BaseMediaSource source;
        if (url.contains(".m3u8")) { //FIXME: add proper content type here
            HlsMediaSource.Factory factory = new HlsMediaSource.Factory(dataSourceFactory);
            factory.setExtractorFactory(new DefaultHlsExtractorFactory(hlsExtractorFlags, exposeCea608WhenMissingDeclarations))
                    .setAllowChunklessPreparation(true);
            source = factory.createMediaSource(MediaItem.fromUri(Uri.parse(url)));
        } else {
            ProgressiveMediaSource.Factory factory = new ProgressiveMediaSource.Factory(dataSourceFactory);
            source = factory.createMediaSource(MediaItem.fromUri(Uri.parse(url)));
        }

        source.addEventListener(handler, new MediaSourceEventListener() {
            @Override
            public void onLoadError(int windowIndex, @Nullable MediaSource.MediaPeriodId mediaPeriodId, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData, IOException error, boolean wasCanceled) {
                Log.w(TAG, "onLoadError");
                VideoPlayer.this.emit("error", "Source load error: " + error.getLocalizedMessage());
            }
        });

        handler.post(new SafeRunnable() {
            @Override
            public void doRun() {
                player.setMediaSource(source, true);
                player.prepare();
                Log.i(TAG, "Player.setSource exited");
            }
        });
    }

    public void setLoop(boolean loop) {
        Log.i(TAG, "Player.setLoop " + loop);
    }

    public void setBackgroundColor(String color) {
        Log.i(TAG, "Player.setBackground " + color);
    }

    public void play() {
        Log.i(TAG, "Player.play");
        handler.post(new SafeRunnable() {
            @Override
            public void doRun() {
                if (paused)
                {
                    paused = false;
                    VideoPlayer.this.emit("pause", paused);
                    if (player != null)
                        player.setPlayWhenReady(true);
                }
                else
                    Log.i(TAG, "ignoring play on non-paused stream");
            }
        });
    }

    public void pause() {
        Log.i(TAG, "Player.pause");
        handler.post(new SafeRunnable() {
            @Override
            public void doRun() {
                if (!paused)
                {
                    paused = true;
                    VideoPlayer.this.emit("pause", paused);
                    if (player != null)
                        player.setPlayWhenReady(false);
                }
                else
                    Log.i(TAG, "ignoring pause on paused stream");
            }
        });
    }

    public void seek(int pos) {
        Log.i(TAG, "Player.seek " + pos);
        handler.post(new SafeRunnable() {
            @Override
            public void doRun() {
                //FIXME: save position if resources reacquired
                long newPos = player.getCurrentPosition() + pos * 1000L;
                VideoPlayer.this.emit("timeupdate", newPos / 1000.0);

                if (player != null)
                    player.seekTo(newPos);
            }
        });
    }

    public void seekTo(int pos) {
        Log.i(TAG, "Player.seekTo " + pos);
        handler.post(new SafeRunnable() {
            @Override
            public void doRun() {
                //FIXME: save position if resources reacquired
                VideoPlayer.this.emit("timeupdate", pos);

                if (player != null)
                    player.seekTo(pos * 1000L);
            }
        });
    }

    public void setOption(String name, Object value) {
        Log.i(TAG, "Player.setOption " + name + " : " + value);
        handler.post(new SafeRunnable() {
            @Override
            public void doRun() {
                switch (name) {
                    case "autoplay":
                        autoplay = TypeConverter.toBoolean(value);
                        if (player != null)
                            player.setPlayWhenReady(autoplay);
                        break;
                    case "detectAccessUnits":
                        setHlsExtractorFlag(DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS, TypeConverter.toBoolean(value));
                        break;
                    case "allowNonIdrKeyframes":
                        setHlsExtractorFlag(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES, TypeConverter.toBoolean(value));
                        break;
                    case "enableHdmvDtsAudioStreams":
                        setHlsExtractorFlag(DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS, TypeConverter.toBoolean(value));
                        break;
                    case "ignoreAacStream":
                        setHlsExtractorFlag(DefaultTsPayloadReaderFactory.FLAG_IGNORE_AAC_STREAM, TypeConverter.toBoolean(value));
                        break;
                    case "ignoreH264Stream":
                        setHlsExtractorFlag(DefaultTsPayloadReaderFactory.FLAG_IGNORE_H264_STREAM, TypeConverter.toBoolean(value));
                        break;
                    case "ignoreSpliceInfoStream":
                        setHlsExtractorFlag(DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM, TypeConverter.toBoolean(value));
                        break;
                    case "exposeCea608WhenMissingDeclarations":
                        exposeCea608WhenMissingDeclarations = TypeConverter.toBoolean(value);
                        break;
                    default:
                        Log.w(TAG, "ignoring option " + name);
                        break;
                }
            }
        });
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

    public void setHlsExtractorFlag(int flag, boolean flagSwitcher) {
        hlsExtractorFlags = flagSwitcher ? hlsExtractorFlags | flag : hlsExtractorFlags &~ flag;
    }

    private void updateGeometry() {
        if (rect == null) {
            Log.v(TAG, "updateGeometry skipped, rect is null");
            return;
        }

        Rect surfaceGeometry = _env.getSurfaceGeometry();
        //if surface geometry defined and rectangle less than surface geometry, set Z on top
        boolean onTop = surfaceGeometry != null && !rect.contains(surfaceGeometry); //we use original rect here (no AR)
        surfaceView.setZOrderOnTop(onTop);

        if (videoWidth > 0 && videoHeight > 0) {
            float scaleX = 1.0f * rect.width() / videoWidth;
            float scaleY = 1.0f * rect.height() / videoHeight;
            float scale = Math.min(scaleX, scaleY); //always fit
            Log.v(TAG, "aspect ratio scale: " + scale);
            int newWidth = (int)(scale * videoWidth);
            int newHeight = (int)(scale * videoHeight);
            int x = rect.left + (rect.width() - newWidth) / 2;
            int y = rect.top + (rect.height() - newHeight) / 2;
            Rect videoRect = new Rect(x, y, x + newWidth, y + newHeight);
            Log.v(TAG, "corrected video rect: " + videoRect);
            viewHolder.setRect(_env.getRootView(), videoRect);
        }
        else
            viewHolder.setRect(_env.getRootView(), rect);
    }

    private void setRect(Rect rect) {
        Log.i(TAG, "Player.setRect " + rect);
        this.rect = rect;
        updateGeometry();
    }

    public void setVisibility(boolean visible) {
        playerVisible = visible;
        Log.i(TAG, "Player.setVisibility " + visible);
        viewHolder.update(_env.getRootView(), visible);
    }

    @Override
    public void acquireResource() {
        handler.post(new SafeRunnable() {
            @Override
            public void doRun() {
                VideoPlayer.this.acquireResourceImpl();
            }
        });
    }

    @Override
    public void releaseResource() {
        handler.post(new SafeRunnable() {
            @Override
            public void doRun() {
                VideoPlayer.this.releaseResourceImpl();
            }
        });
    }

    @Override
    public void discard() {
        super.discard();
        viewHolder.discard(_env.getRootView());
        releaseResource();
    }

}
