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
        new Thread(() -> {
            try {
                playbackInfo = new YoutubeRepository().getPlaybackInfo(videoId);
                if (playbackInfo == null || (playbackInfo.progressiveUrl == null && !playbackInfo.hasAdaptive())) {
                    runOnUiThread(() -> Toast.makeText(this, "No stream found", Toast.LENGTH_SHORT).show());
                    return;
                }
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

    private void playWithQuality(String quality) {
        if (playbackInfo == null) return;
        DataSource.Factory dsf = new DefaultDataSource.Factory(this);
        MediaSource ms = null;
        if ("progressive".equals(quality) || playbackInfo.videoStreams.isEmpty()) {
            String url = playbackInfo.progressiveUrl;
            if (url == null) { Toast.makeText(this, "No progressive URL", Toast.LENGTH_SHORT).show(); return; }
            ms = new ProgressiveMediaSource.Factory(dsf).createMediaSource(MediaItem.fromUri(url));
            Toast.makeText(this, "Playing 360p (progressive)", Toast.LENGTH_SHORT).show();
        } else {
            YoutubeRepository.Stream video = null;
            for (YoutubeRepository.Stream s : playbackInfo.videoStreams) if (quality.equals(s.quality)) { video = s; break; }
            if (video == null) video = playbackInfo.videoStreams.get(0);
            YoutubeRepository.Stream audio = playbackInfo.audioStreams.isEmpty() ? null : playbackInfo.audioStreams.get(0);
            // pick best audio (highest bitrate mp4a)
            for (YoutubeRepository.Stream a : playbackInfo.audioStreams) if (a.itag == 140) { audio = a; break; }
            MediaSource videoSrc = new ProgressiveMediaSource.Factory(dsf).createMediaSource(MediaItem.fromUri(video.url));
            if (audio != null) {
                MediaSource audioSrc = new ProgressiveMediaSource.Factory(dsf).createMediaSource(MediaItem.fromUri(audio.url));
                ms = new MergingMediaSource(videoSrc, audioSrc);
            } else {
                ms = videoSrc;
            }
            Toast.makeText(this, "Playing " + video.quality + " (" + video.itag + ") + audio", Toast.LENGTH_SHORT).show();
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