package com.pureqml.android.runtime;

import android.content.Context;
import android.graphics.Rect;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Function;
import com.eclipsesource.v8.V8Object;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.mp3.Mp3Extractor;
import com.google.android.exoplayer2.extractor.mp4.FragmentedMp4Extractor;
import com.google.android.exoplayer2.extractor.ts.Ac3Extractor;
import com.google.android.exoplayer2.extractor.ts.AdtsExtractor;
import com.google.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory;
import com.google.android.exoplayer2.extractor.ts.TsExtractor;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.hls.HlsExtractorFactory;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.TimestampAdjuster;
import com.google.android.exoplayer2.util.Util;
import com.pureqml.android.IExecutionEnvironment;

import java.util.Collections;
import java.util.List;


public final class VideoPlayer extends BaseObject {
    private static final String TAG = "VideoPlayer";
    private SimpleExoPlayer             player;
    private PlayerView                  view;
    private ViewHolder<PlayerView>      viewHolder;

    public VideoPlayer(IExecutionEnvironment env) {
        super(env);
        Context context = env.getContext();
        DefaultTrackSelector trackSelector = new DefaultTrackSelector();
        trackSelector.setParameters(
                trackSelector.buildUponParameters()
                .setExceedRendererCapabilitiesIfNecessary(true)
                .setAllowMixedMimeAdaptiveness(true)
                .setAllowNonSeamlessAdaptiveness(true)
        );
        player = ExoPlayerFactory.newSimpleInstance(context, trackSelector);
        view = new PlayerView(_env.getContext());
        view.setUseController(false);
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

        DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(_env.getContext(), Util.getUserAgent(_env.getContext(), "pureqml"));

        if (url.endsWith("m3u8")) {
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
