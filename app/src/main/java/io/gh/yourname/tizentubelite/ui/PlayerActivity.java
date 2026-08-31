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
        player.addListener(new com.google.android.exoplayer2.Player.Listener() {
            @Override public void onPlayerError(com.google.android.exoplayer2.PlaybackException error) {
                android.util.Log.e("TizenTubePlayer","player error: "+error.getMessage(), error);
                if (!triedProgressiveFallback && playbackInfo != null && playbackInfo.progressiveUrl != null) {
                    triedProgressiveFallback = true;
                    runOnUiThread(() -> {
                        Toast.makeText(PlayerActivity.this, "Adaptive failed, fallback 360p", Toast.LENGTH_SHORT).show();
                        playWithQuality("progressive");
                    });
                }
            }
        });
        new Thread(() -> {
            try {
                playbackInfo = new YoutubeRepository().getPlaybackInfo(videoId);
                if (playbackInfo == null || (playbackInfo.progressiveUrl == null && !playbackInfo.hasAdaptive())) {
                    runOnUiThread(() -> Toast.makeText(this, "No stream found", Toast.LENGTH_SHORT).show());
                    return;
                }
                triedProgressiveFallback = false;
                runOnUiThread(() -> playWithQuality(autoSelectQuality()));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private String autoSelectQuality() {
        if (playbackInfo == null || playbackInfo.videoStreams.isEmpty()) return "progressive";
        // Estimate bandwidth via ConnectivityManager (simple heuristic for TV box)
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm != null ? cm.getActiveNetworkInfo() : null;
            boolean isWifi = ni != null && ni.getType() == ConnectivityManager.TYPE_WIFI;
            // On ethernet/wifi pick highest, on mobile pick 360p
            if (isWifi) {
                for (YoutubeRepository.Stream s : playbackInfo.videoStreams) if ("720p".equals(s.quality)) return s.quality;
                return playbackInfo.videoStreams.get(0).quality;
            } else {
                // prefer 360p for slower
                for (YoutubeRepository.Stream s : playbackInfo.videoStreams) if ("360p".equals(s.quality)) return s.quality;
                return playbackInfo.videoStreams.get(0).quality;
            }
        } catch (Exception e) { return playbackInfo.videoStreams.get(0).quality; }
    }

    private boolean triedProgressiveFallback = false;
    private void playWithQuality(String quality) {
        if (playbackInfo == null) return;
        triedProgressiveFallback = false;
        DataSource.Factory dsf = new DefaultDataSource.Factory(this);
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
        opts.add("Auto (" + autoSelectQuality() + ")");
        for (YoutubeRepository.Stream s : playbackInfo.videoStreams) opts.add(s.quality + " (" + s.itag + ")");
        opts.add("360p progressive");
        new AlertDialog.Builder(this)
            .setTitle("Select quality")
            .setItems(opts.toArray(new String[0]), (d, which) -> {
                String sel;
                if (which == 0) sel = autoSelectQuality();
                else if (which == opts.size()-1) sel = "progressive";
                else sel = playbackInfo.videoStreams.get(which-1).quality;
                long pos = player.getCurrentPosition();
                boolean play = player.getPlayWhenReady();
                playWithQuality(sel);
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