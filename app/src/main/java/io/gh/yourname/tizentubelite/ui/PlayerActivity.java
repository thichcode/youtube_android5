package io.gh.yourname.tizentubelite.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.Toast;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MergingMediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import io.gh.yourname.tizentubelite.R;
import io.gh.yourname.tizentubelite.data.YoutubeRepository;

public class PlayerActivity extends Activity {
    private ExoPlayer player;
    private PlayerView playerView;
    private YoutubeRepository.PlaybackInfo playbackInfo;
    private String videoId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        playerView = findViewById(R.id.player_view);
        videoId = getIntent().getStringExtra("videoId");
        if (videoId == null) { finish(); return; }
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        new Thread(() -> {
            try {
                YoutubeRepository repo = new YoutubeRepository();
                playbackInfo = repo.getPlaybackInfo(videoId);
                if (playbackInfo == null || (playbackInfo.progressiveUrl == null && !playbackInfo.hasAdaptive())) {
                    runOnUiThread(() -> Toast.makeText(this, "No stream found", Toast.LENGTH_SHORT).show());
                    return;
                }
                android.util.Log.d("TizenTubePlayer","progressive="+(playbackInfo.progressiveUrl!=null)+" adaptive="+playbackInfo.hasAdaptive()
                    +" hls="+playbackInfo.hlsManifestUrl+" dash="+playbackInfo.dashManifestUrl
                    +" videoCount="+playbackInfo.videoStreams.size()+" audioCount="+playbackInfo.audioStreams.size());
                triedProgressiveFallback = false;
                final String autoQ = autoSelectQuality();
                android.util.Log.d("TizenTubePlayer","autoQ="+autoQ+" fast="+isFastNetwork());
                runOnUiThread(() -> playWithQuality(autoQ));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private String autoSelectQuality() {
        if (playbackInfo == null) return "progressive";
        // no adaptive -> must use progressive
        if (playbackInfo.videoStreams.isEmpty()) return "progressive";
        boolean fast = isFastNetwork();
        // fast network (wifi/ethernet) -> try 720p, then 480p, then best available
        if (fast) {
            for (YoutubeRepository.Stream s : playbackInfo.videoStreams) if ("720p".equals(s.quality)) return "720p";
            for (YoutubeRepository.Stream s : playbackInfo.videoStreams) if ("480p".equals(s.quality)) return "480p";
            return playbackInfo.videoStreams.get(0).quality;
        } else {
            // slow/mobile -> stay 360p progressive for stability
            return "progressive";
        }
    }

    private boolean isFastNetwork() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            NetworkInfo ni = cm.getActiveNetworkInfo();
            if (ni == null || !ni.isConnected()) return false;
            int type = ni.getType();
            // WIFI or ETHERNET (9) are fast; mobile/other are slow
            return type == ConnectivityManager.TYPE_WIFI || type == 9;
        } catch (Exception e) { return false; }
    }

    private boolean triedProgressiveFallback = false;
    private static final String STREAM_UA = "com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip";
    private static final String STREAM_UA_VR = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip";

    private void playHls(String url) {
        DataSource.Factory dsf = createStreamDataSource();
        com.google.android.exoplayer2.source.MediaSource ms =
            new com.google.android.exoplayer2.source.hls.HlsMediaSource.Factory(dsf)
                .createMediaSource(MediaItem.fromUri(url));
        player.setMediaSource(ms);
        player.prepare();
        player.play();
        Toast.makeText(this, "Playing HLS stream", Toast.LENGTH_SHORT).show();
        player.addListener(new com.google.android.exoplayer2.Player.Listener() {
            @Override public void onPlayerError(com.google.android.exoplayer2.PlaybackException e) {
                android.util.Log.e("TizenTubePlayer","hls error: "+e.getMessage(), e);
                if (!triedProgressiveFallback && playbackInfo.progressiveUrl != null) {
                    triedProgressiveFallback = true;
                    runOnUiThread(() -> { Toast.makeText(PlayerActivity.this,"HLS failed, fallback 360p",Toast.LENGTH_SHORT).show(); playWithQuality("progressive"); });
                }
            }
        });
    }

    private DataSource.Factory createStreamDataSource(String ua) {
        return new DataSource.Factory() {
            @Override
            public DataSource createDataSource() {
                return new DefaultHttpDataSource(ua, 15000, 15000);
            }
        };
    }
    private DataSource.Factory createStreamDataSource() { return createStreamDataSource(STREAM_UA); }
    private void playWithQuality(String quality) {
        if (playbackInfo == null) return;
        // adaptive streams need VR UA, progressive needs ANDROID UA
        boolean isAdaptive = !"progressive".equals(quality) && !playbackInfo.videoStreams.isEmpty();
        DataSource.Factory dsf = createStreamDataSource(isAdaptive ? STREAM_UA_VR : STREAM_UA);
        MediaSource ms = null;
        if ("progressive".equals(quality) || playbackInfo.videoStreams.isEmpty()) {
            String url = playbackInfo.progressiveUrl;
            if (url == null) { Toast.makeText(this, "No progressive URL", Toast.LENGTH_SHORT).show(); return; }
            ms = new ProgressiveMediaSource.Factory(dsf).createMediaSource(MediaItem.fromUri(url));
            Toast.makeText(this, "Playing 360p (progressive)", Toast.LENGTH_SHORT).show();
        } else {
            YoutubeRepository.Stream video = null;
            // prefer mp4 avc1 for stability, then vp9, then av01
            java.util.List<YoutubeRepository.Stream> cands = new java.util.ArrayList<>();
            for (YoutubeRepository.Stream s : playbackInfo.videoStreams) if (quality.equals(s.quality)) cands.add(s);
            if (cands.isEmpty()) video = playbackInfo.videoStreams.get(0);
            else {
                YoutubeRepository.Stream avc = null, vp9 = null, av1 = null;
                for (YoutubeRepository.Stream s : cands) {
                    if (s.mime != null && s.mime.contains("avc1")) avc = s;
                    else if (s.mime != null && s.mime.contains("vp9")) vp9 = s;
                    else if (s.mime != null && s.mime.contains("av01")) av1 = s;
                }
                video = avc != null ? avc : (vp9 != null ? vp9 : cands.get(0));
            }
            // pick compatible audio (mp4 for avc, webm/opus for vp9/av01)
            YoutubeRepository.Stream audio = null;
            boolean isMp4 = video.mime != null && video.mime.contains("mp4");
            for (YoutubeRepository.Stream a : playbackInfo.audioStreams) {
                if (isMp4 && a.mime != null && a.mime.contains("mp4a")) { if (audio==null || a.bitrate > audio.bitrate) audio = a; }
                else if (!isMp4 && a.mime != null && a.mime.contains("opus")) { if (audio==null || a.bitrate > audio.bitrate) audio = a; }
            }
            if (audio == null && !playbackInfo.audioStreams.isEmpty()) audio = playbackInfo.audioStreams.get(0);
            MediaSource videoSrc = new ProgressiveMediaSource.Factory(dsf).createMediaSource(MediaItem.fromUri(video.url));
            if (audio != null) {
                MediaSource audioSrc = new ProgressiveMediaSource.Factory(dsf).createMediaSource(MediaItem.fromUri(audio.url));
                ms = new MergingMediaSource(videoSrc, audioSrc);
            } else {
                ms = videoSrc;
            }
            Toast.makeText(this, "Playing " + video.quality + " (" + video.mime + ") + audio", Toast.LENGTH_SHORT).show();
            android.util.Log.d("TizenTubePlayer","play "+video.quality+" itag="+video.itag+" mime="+video.mime+" audio itag="+(audio!=null?audio.itag:0));
        }
        player.setMediaSource(ms);
        player.prepare();
        player.play();
        // If adaptive was requested but failed, fallback to progressive
        player.addListener(new com.google.android.exoplayer2.Player.Listener() {
            @Override public void onPlayerError(com.google.android.exoplayer2.PlaybackException error) {
                android.util.Log.e("TizenTubePlayer","player error: "+error.getMessage(), error);
                if (!triedProgressiveFallback) {
                    triedProgressiveFallback = true;
                    runOnUiThread(() -> {
                        if (playbackInfo.progressiveUrl != null && !"progressive".equals(quality)) {
                            Toast.makeText(PlayerActivity.this, "Adaptive failed, fallback 360p", Toast.LENGTH_SHORT).show();
                            playWithQuality("progressive");
                        } else if (!playbackInfo.hasAdaptive()) {
                            Toast.makeText(PlayerActivity.this, "Playback error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(PlayerActivity.this, "Playback failed", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            if (playbackInfo != null && playbackInfo.hasAdaptive() && player != null && player.isPlaying()) {
                showQualityDialog();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void showQualityDialog() {
        if (playbackInfo == null) return;
        java.util.List<String> opts = new java.util.ArrayList<>();
        java.util.List<String> vals = new java.util.ArrayList<>();
        opts.add("360p progressive");
        vals.add("progressive");
        for (YoutubeRepository.Stream s : playbackInfo.videoStreams) {
            opts.add(s.quality + " adaptive (" + (s.mime != null ? s.mime.substring(s.mime.lastIndexOf("/")+1) : s.itag) + ")");
            vals.add(s.quality);
        }
        new AlertDialog.Builder(this)
            .setTitle("Select quality")
            .setItems(opts.toArray(new String[0]), (d, which) -> {
                long pos = player.getCurrentPosition();
                boolean play = player.getPlayWhenReady();
                playWithQuality(vals.get(which));
                player.seekTo(pos);
                player.setPlayWhenReady(play);
            }).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) { player.release(); player = null; }
    }
}