package io.gh.yourname.tizentubelite.ui;

import android.content.Intent;
import android.os.Bundle;
import androidx.leanback.app.SearchSupportFragment;
import androidx.leanback.widget.*;
import io.gh.yourname.tizentubelite.data.Video;
import io.gh.yourname.tizentubelite.data.YoutubeRepository;
import java.util.List;

public class SearchFragment extends SearchSupportFragment implements SearchSupportFragment.SearchResultProvider {
    private ArrayObjectAdapter rowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setSearchResultProvider(this);
        setOnItemViewClickedListener((itemViewHolder, item, rowViewHolder, row) -> {
            if (item instanceof Video) {
                Video v = (Video) item;
                Intent i = new Intent(getActivity(), PlayerActivity.class);
                i.putExtra("videoId", v.id);
                startActivity(i);
            }
        });
    }

    @Override
    public ObjectAdapter getResultsAdapter() { return rowsAdapter; }

    @Override
    public boolean onQueryTextChange(String newQuery) { return false; }

    @Override
    public boolean onQueryTextSubmit(String query) {
        new Thread(() -> {
            try {
                List<Video> vids = new YoutubeRepository().search(query);
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    rowsAdapter.clear();
                    ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(new HomeFragment.CardPresenter());
                    for (Video v : vids) listRowAdapter.add(v);
                    rowsAdapter.add(new ListRow(new HeaderItem("Results for " + query), listRowAdapter));
                });
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
        return true;
    }
}
