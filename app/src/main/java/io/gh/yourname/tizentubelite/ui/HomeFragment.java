package io.gh.yourname.tizentubelite.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.widget.*;
import io.gh.yourname.tizentubelite.data.Section;
import io.gh.yourname.tizentubelite.data.Video;
import io.gh.yourname.tizentubelite.data.YoutubeRepository;
import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends BrowseSupportFragment {
    private ArrayObjectAdapter rowsAdapter;

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setTitle("TizenTube Native");
        setHeadersState(HEADERS_ENABLED);
        setHeadersTransitionOnBackEnabled(true);
        rowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        setAdapter(rowsAdapter);
        setOnItemViewClickedListener((itemViewHolder, item, rowViewHolder, row) -> {
            if (item instanceof Video) {
                Video v = (Video) item;
                Intent i = new Intent(getActivity(), PlayerActivity.class);
                i.putExtra("videoId", v.id);
                startActivity(i);
            }
        });
        load();
    }

    private void load() {
        new Thread(() -> {
            try {
                YoutubeRepository repo = new YoutubeRepository();
                List<Section> sections = repo.browse();
                if (sections == null || sections.isEmpty()) {
                    sections = new ArrayList<>();
                    String[] defaults = {"Trending", "Music", "Movies on YouTube", "Gaming"};
                    for (String q : defaults) {
                        List<Video> vids = repo.search(q);
                        if (!vids.isEmpty()) sections.add(new Section(q, vids));
                    }
                }
                if (getActivity() == null) return;
                final List<Section> fs = sections;
                getActivity().runOnUiThread(() -> {
                    for (Section s : fs) {
                        ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(new CardPresenter());
                        for (Video v : s.videos) listRowAdapter.add(v);
                        HeaderItem header = new HeaderItem(s.title);
                        rowsAdapter.add(new ListRow(header, listRowAdapter));
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public static class CardPresenter extends Presenter {
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent) {
            ImageCardView card = new ImageCardView(parent.getContext());
            card.setFocusable(true);
            card.setFocusableInTouchMode(true);
            return new ViewHolder(card);
        }
        @Override
        public void onBindViewHolder(ViewHolder viewHolder, Object item) {
            Video v = (Video) item;
            ImageCardView card = (ImageCardView) viewHolder.view;
            card.setTitleText(v.title);
            card.setContentText(v.id);
            // thumb loading via Glide/Picasso omitted for brevity; use placeholder
            card.setMainImageDimensions(320, 180);
        }
        @Override
        public void onUnbindViewHolder(ViewHolder viewHolder) {}
    }
}
