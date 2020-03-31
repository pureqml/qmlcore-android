package com.pureqml.android.runtime;

import android.content.Context;
import android.graphics.Rect;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.SurfaceView;

import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Function;
import com.eclipsesource.v8.V8Object;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.mp3.Mp3Extractor;
import com.google.android.exoplayer2.extractor.mp4.FragmentedMp4Extractor;
import com.google.android.exoplayer2.extractor.ts.Ac3Extractor;
import com.google.android.exoplayer2.extractor.ts.AdtsExtractor;
import com.google.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory;
import com.google.android.exoplayer2.extractor.ts.TsExtractor;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.hls.HlsExtractorFactory;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.TimestampAdjuster;
import com.google.android.exoplayer2.util.Util;
import com.pureqml.android.IExecutionEnvironment;
import com.pureqml.android.IResource;
import com.pureqml.android.TypeConverter;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;


public final class VideoPlayer extends BaseObject implements IResource {
    private static final String TAG = "VideoPlayer";

    private SimpleExoPlayer             player;
    private SurfaceView                 view;
    private ViewHolder<SurfaceView>     viewHolder;

    //this is persistent state
    private Rect                        rect;
    private String                      source;
    private boolean                     playerVisible = true;
    private boolean                     autoplay = false;
    private boolean                     paused = false;

    public VideoPlayer(IExecutionEnvironment env) {
        super(env);
        Context context = env.getContext();
        view = new SurfaceView(context);
        viewHolder = new ViewHolder<SurfaceView>(context, view);
        this.acquireResource();
        _env.register(this);
    }

    @Override
    public void acquireResource() {
        if (player != null)
            return;

        Context context = _env.getContext();
        DefaultTrackSelector trackSelector = new DefaultTrackSelector();
        trackSelector.setParameters(
                trackSelector.buildUponParameters()
                        .setExceedRendererCapabilitiesIfNecessary(true)
                        .setAllowMixedMimeAdaptiveness(true)
                        .setAllowNonSeamlessAdaptiveness(true)
        );
        player = ExoPlayerFactory.newSimpleInstance(context, trackSelector);
        player.setVideoSurfaceView(view);

        final ExecutorService executor = _env.getExecutor();
        player.addListener(new Player.EventListener() {
            @Override
            public void onTimelineChanged(final Timeline timeline, @Nullable Object manifest, int reason) {
                Log.d(TAG, "onTimelineChanged" + timeline + ", reason: " + reason);
            }

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
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        VideoPlayer.this.emit(null, "error", error.toString());
                    }
                });
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
        });

        if (rect != null)
            setRect(rect);
        setVisibility(playerVisible);
        player.setPlayWhenReady(autoplay);
        if (source != null)
            setSource(source);
    }

    @Override
    public void releaseResource() {
        if (this.player != null) {
            player.setVideoSurfaceView(null);
            this.player.release();
            this.player = null;
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

        if (url.indexOf(".m3u8") >= 0) { //FIXME: add proper content type here
            HlsMediaSource.Factory factory = new HlsMediaSource.Factory(dataSourceFactory);
            factory.setExtractorFactory(new CustomHlsExtractorFactory());
            player.prepare(factory.createMediaSource(Uri.parse(url)));
        } else {
            ExtractorMediaSource.Factory factory = new ExtractorMediaSource.Factory(dataSourceFactory);
            player.prepare(factory.createMediaSource(Uri.parse(url)));
        }
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

    public final class CustomHlsExtractorFactory implements HlsExtractorFactory {

        public static final String AAC_FILE_EXTENSION = ".aac";
        public static final String AC3_FILE_EXTENSION = ".ac3";
        public static final String EC3_FILE_EXTENSION = ".ec3";
        public static final String MP3_FILE_EXTENSION = ".mp3";
        public static final String MP4_FILE_EXTENSION = ".mp4";
        public static final String M4_FILE_EXTENSION_PREFIX = ".m4";
        public static final String MP4_FILE_EXTENSION_PREFIX = ".mp4";
        public static final String VTT_FILE_EXTENSION = ".vtt";
        public static final String WEBVTT_FILE_EXTENSION = ".webvtt";

        @Override
        public Pair<Extractor, Boolean> createExtractor(Extractor previousExtractor, Uri uri,
                                                        Format format, List<Format> muxedCaptionFormats, DrmInitData drmInitData,
                                                        TimestampAdjuster timestampAdjuster) {
            String lastPathSegment = uri.getLastPathSegment();
            if (lastPathSegment == null) {
                lastPathSegment = "";
            }
            boolean isPackedAudioExtractor = false;
            Extractor extractor;
//            if (MimeTypes.TEXT_VTT.equals(format.sampleMimeType)
//                    || lastPathSegment.endsWith(WEBVTT_FILE_EXTENSION)
//                    || lastPathSegment.endsWith(VTT_FILE_EXTENSION)) {
//                extractor = new WebvttExtractor(format.language, timestampAdjuster);
//            } else
            if (lastPathSegment.endsWith(AAC_FILE_EXTENSION)) {
                isPackedAudioExtractor = true;
                extractor = new AdtsExtractor();
            } else if (lastPathSegment.endsWith(AC3_FILE_EXTENSION)
                    || lastPathSegment.endsWith(EC3_FILE_EXTENSION)) {
                isPackedAudioExtractor = true;
                extractor = new Ac3Extractor();
            } else if (lastPathSegment.endsWith(MP3_FILE_EXTENSION)) {
                isPackedAudioExtractor = true;
                extractor = new Mp3Extractor(0, 0);
            } else if (previousExtractor != null) {
                // Only reuse TS and fMP4 extractors.
                extractor = previousExtractor;
            } else if (lastPathSegment.endsWith(MP4_FILE_EXTENSION)
                    || lastPathSegment.startsWith(M4_FILE_EXTENSION_PREFIX, lastPathSegment.length() - 4)
                    || lastPathSegment.startsWith(MP4_FILE_EXTENSION_PREFIX, lastPathSegment.length() - 5)) {
                extractor = new FragmentedMp4Extractor(0, timestampAdjuster, null, drmInitData,
                        muxedCaptionFormats != null ? muxedCaptionFormats : Collections.<Format>emptyList());
            } else {
                // For any other file extension, we assume TS format.
                @DefaultTsPayloadReaderFactory.Flags
                int esReaderFactoryFlags = DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM;

                //this is the only change needed
                esReaderFactoryFlags |= DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS;
                esReaderFactoryFlags |= DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES;

                if (muxedCaptionFormats != null) {
                    // The playlist declares closed caption renditions, we should ignore descriptors.
                    esReaderFactoryFlags |= DefaultTsPayloadReaderFactory.FLAG_OVERRIDE_CAPTION_DESCRIPTORS;
                } else {
                    muxedCaptionFormats = Collections.emptyList();
                }
                String codecs = format.codecs;
                if (!TextUtils.isEmpty(codecs)) {
                    // Sometimes AAC and H264 streams are declared in TS chunks even though they don't really
                    // exist. If we know from the codec attribute that they don't exist, then we can
                    // explicitly ignore them even if they're declared.
                    if (!MimeTypes.AUDIO_AAC.equals(MimeTypes.getAudioMediaMimeType(codecs))) {
                        esReaderFactoryFlags |= DefaultTsPayloadReaderFactory.FLAG_IGNORE_AAC_STREAM;
                    }
                    if (!MimeTypes.VIDEO_H264.equals(MimeTypes.getVideoMediaMimeType(codecs))) {
                        esReaderFactoryFlags |= DefaultTsPayloadReaderFactory.FLAG_IGNORE_H264_STREAM;
                    }
                }
                extractor = new TsExtractor(TsExtractor.MODE_HLS, timestampAdjuster,
                        new DefaultTsPayloadReaderFactory(esReaderFactoryFlags, muxedCaptionFormats));
            }
            return Pair.create(extractor, isPackedAudioExtractor);
        }

    }


}
