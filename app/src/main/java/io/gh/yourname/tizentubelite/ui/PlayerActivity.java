package io.gh.yourname.tizentubelite.ui;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.ui.PlayerView;
import io.gh.yourname.tizentubelite.R;
import io.gh.yourname.tizentubelite.data.YoutubeRepository;

public class PlayerActivity extends Activity {
    private ExoPlayer player;
    private PlayerView playerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        playerView = findViewById(R.id.player_view);
        String videoId = getIntent().getStringExtra("videoId");
        if (videoId == null) { finish(); return; }
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        new Thread(() -> {
            try {
                String url = new YoutubeRepository().nextStreamUrl(videoId);
                if (url == null) {
                    runOnUiThread(() -> Toast.makeText(this, "No stream found", Toast.LENGTH_SHORT).show());
                    return;
                }
                runOnUiThread(() -> {
                    player.setMediaItem(MediaItem.fromUri(url));
                    player.prepare();
                    player.play();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) { player.release(); player = null; }
    }
}