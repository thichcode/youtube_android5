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
        // Show the search icon in the browse header (Leanback renders it when this listener is set)
        setOnSearchClickedListener(searchOrb -> startActivity(new Intent(getActivity(), SearchActivity.class)));
        setOnItemViewClickedListener((itemViewHolder, item, rowViewHolder, row) -> {
            if (item instanceof Video) {
                Video v = (Video) item;
                if ("SEARCH".equals(v.id)) {
                    startActivity(new Intent(getActivity(), SearchActivity.class));
                    return;
                }
                Intent i = new Intent(getActivity(), PlayerActivity.class);
                i.putExtra("videoId", v.id);
                i.putExtra("videoTitle", v.title);
                startActivity(i);
            }
        });
        load();
    }

    private void load() {
        new Thread(() -> {
            YoutubeRepository repo = new YoutubeRepository();
            List<Section> sections = new ArrayList<>();
            try {
                List<Section> b = null;
                try { b = repo.browse(); } catch (Exception e) { android.util.Log.d("TizenTubeHome","browse failed: "+e); }
                if (b != null && !b.isEmpty()) sections.addAll(b);
            } catch (Exception ignored) {}
            if (sections.isEmpty()) {
                String[] defaults = {"Trending", "Music", "Movies on YouTube", "Gaming"};
                for (String q : defaults) {
                    try {
                        List<Video> vids = repo.search(q);
                        android.util.Log.d("TizenTubeHome","search '"+q+"' got "+(vids==null?0:vids.size()));
                        if (vids != null && !vids.isEmpty()) sections.add(new Section(q, vids));
                    } catch (Exception e) {
                        android.util.Log.d("TizenTubeHome","search '"+q+"' failed: "+e.getMessage());
                    }
                }
            }
            android.util.Log.d("TizenTubeHome","final sections="+sections.size());
            if (getActivity() == null) return;
            final List<Section> fs = sections;
            getActivity().runOnUiThread(() -> {
                rowsAdapter.clear();
                rowsAdapter.add(makeSearchRow());
                if (fs.isEmpty()) {
                    // show empty hint row so user knows to use Search
                    ArrayObjectAdapter hint = new ArrayObjectAdapter(new CardPresenter());
                    hint.add(new Video("SEARCH", "No trending available - press Search", "", 0));
                    rowsAdapter.add(new ListRow(new HeaderItem("Explore"), hint));
                } else {
                    for (Section s : fs) {
                        ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(new CardPresenter());
                        for (Video v : s.videos) listRowAdapter.add(v);
                        HeaderItem header = new HeaderItem(s.title);
                        rowsAdapter.add(new ListRow(header, listRowAdapter));
                    }
                }
            });
        }).start();
    }

    private ListRow makeSearchRow() {
        ArrayObjectAdapter a = new ArrayObjectAdapter(new CardPresenter());
        a.add(new Video("SEARCH", "Search videos", "", 0));
        return new ListRow(new HeaderItem("Search"), a);
    }

    public static class CardPresenter extends Presenter {
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent) {
            ImageCardView card = new ImageCardView(parent.getContext());
            card.setFocusable(true);
            card.setFocusableInTouchMode(true);
            card.setMainImageDimensions(320, 180);
            return new ViewHolder(card);
        }
        @Override
        public void onBindViewHolder(ViewHolder viewHolder, Object item) {
            Video v = (Video) item;
            ImageCardView card = (ImageCardView) viewHolder.view;
            card.setTitleText(v.title);
            card.setContentText(v.id);
            card.setMainImageDimensions(320, 180);
            // Load thumbnail
            if (v.thumb != null && !v.thumb.isEmpty() && !"SEARCH".equals(v.id)) {
                try {
                    com.bumptech.glide.Glide.with(card.getContext())
                        .load(v.thumb)
                        .placeholder(android.R.drawable.sym_def_app_icon)
                        .error(android.R.drawable.sym_def_app_icon)
                        .into(card.getMainImageView());
                } catch (Exception ignored) {}
            } else {
                card.setMainImage(card.getResources().getDrawable(android.R.drawable.sym_def_app_icon));
            }
        }
        @Override
        public void onUnbindViewHolder(ViewHolder viewHolder) {
            try { com.bumptech.glide.Glide.with(viewHolder.view.getContext()).clear(((ImageCardView)viewHolder.view).getMainImageView()); } catch (Exception ignored) {}
        }
    }
}
