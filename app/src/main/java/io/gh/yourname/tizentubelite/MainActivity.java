package io.gh.yourname.tizentubelite;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import io.gh.yourname.tizentubelite.ui.HomeFragment;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, new HomeFragment())
                    .commit();
        }
    }
}