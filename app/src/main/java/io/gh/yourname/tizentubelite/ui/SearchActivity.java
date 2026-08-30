package io.gh.yourname.tizentubelite.ui;

import android.os.Bundle;
import androidx.fragment.app.FragmentActivity;
import io.gh.yourname.tizentubelite.R;

public class SearchActivity extends FragmentActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, new SearchFragment())
                    .commit();
        }
    }
}