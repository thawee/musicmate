package apincer.android.mmate.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.carousel.MaskableFrameLayout;

import java.util.ArrayList;
import java.util.List;

import apincer.android.mmate.R;
import apincer.music.core.Constants;
import apincer.music.core.model.MusicFolder;
import apincer.music.core.repository.PlaylistRepository;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.model.SearchCriteria;

public class MusicFolderAdapter extends RecyclerView.Adapter<MusicFolderAdapter.ViewHolder> {
    private final Context context;
    private ArrayList<MusicFolder> arrayList;
    private final SearchCriteria search;
    private int currentlyActivePosition = RecyclerView.NO_POSITION; // To track the active item
    private final TagRepository tagRepos;
    public MusicFolderAdapter(Context context, TagRepository tagRepos, SearchCriteria search) {
        this.context = context;
        this.search = search;
        this.tagRepos = tagRepos;
        refresh();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.view_list_item_carousel, parent, false);
      //  View view = LayoutInflater.from(context).inflate(R.layout.view_list_item_carousel_reflection, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MusicFolder folder =  arrayList.get(position);
        holder.title.setText(folder.getName());
       // holder.itemView.setOnClickListener(view -> onItemClickListener.onClick(holder.imageView, folder.getName()));

        // Apply visual state based on currentlyActivePosition for full binds
        if (position == currentlyActivePosition) {
            highlightFolder((MaskableFrameLayout) holder.rootView);
        } else {
            unhighlightFolder((MaskableFrameLayout) holder.rootView);
        }
    }

    @Override
    public int getItemCount() {
        return arrayList.size();
    }

    public MusicFolder getItem(int newActivePosition) {
       return arrayList.get(newActivePosition);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        View rootView;
        ImageView imageView;
        TextView title;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            this.rootView = itemView;
            imageView = itemView.findViewById(R.id.carousel_image_view);
            title = itemView.findViewById(R.id.folder_title);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    public void refresh() {
        arrayList = new ArrayList<>();
        if(search.getType() == SearchCriteria.TYPE.LIBRARY) {
            addFolder(Constants.TITLE_ALL_SONGS);
            addFolder(Constants.TITLE_INCOMING_SONGS);
            addFolder(Constants.TITLE_DUPLICATE);
            addFolder(Constants.TITLE_TO_ANALYST_DR);
            //addFolder(Constants.TITLE_BROKEN);
        }else if(search.getType() == SearchCriteria.TYPE.MEDIA_QUALITY) {
            addFolder(Constants.QUALITY_AUDIOPHILE);
            addFolder(Constants.QUALITY_RECOMMENDED);
            addFolder(Constants.QUALITY_FAVORITE);
            addFolder(Constants.QUALITY_BAD);
        }else if(search.getType() == SearchCriteria.TYPE.AUDIO_ENCODINGS) {
            addFolder(Constants.TITLE_HIGH_QUALITY);
            addFolder(Constants.TITLE_HIFI_LOSSLESS);
            addFolder(Constants.TITLE_HIRES);
            addFolder(Constants.TITLE_MASTER_AUDIO);
            addFolder(Constants.TITLE_DSD);
        }else if(search.getType() == SearchCriteria.TYPE.GROUPING) {
            List<String> tabs = tagRepos.getActualGroupingList(context);
            addFolders(tabs);
        }else if(search.getType() == SearchCriteria.TYPE.GENRE) {
            List<String> tabs = tagRepos.getActualGenreList();
            addFolders(tabs);
        }else if(search.getType() == SearchCriteria.TYPE.ARTIST) {
            List<String> tabs = tagRepos.getArtistList();
            addFolders(tabs);
        }else if(search.getType() == SearchCriteria.TYPE.PUBLISHER) {
            List<String> tabs = tagRepos.getPublisherList(context);
            addFolders(tabs);
        }else if(search.getType() == SearchCriteria.TYPE.PLAYLIST) {
            PlaylistRepository.initPlaylist(context);
            addFolders(PlaylistRepository.getPlaylistNames());
        }

        if (!arrayList.isEmpty()) {
            currentlyActivePosition = 0;
        } else {
            currentlyActivePosition = RecyclerView.NO_POSITION;
        }

        notifyDataSetChanged();
    }

    private void addFolders(List<String> tabs) {
        for(String tab : tabs) {
            addFolder(tab);
        }
    }

    private void addFolder(String title) {
        arrayList.add(new MusicFolder(title));
    }


    // Helper methods for highlighting
    void highlightFolder(MaskableFrameLayout itemContainer) {
        if (itemContainer == null) return;
        // Apply one or more highlight effects:
        // Example: Scale and Alpha
        itemContainer.animate().scaleX(1.05f).scaleY(1.05f).alpha(1.0f).setDuration(150).start();

        // Example: Change TextView background
        TextView folderTitle = itemContainer.findViewById(R.id.folder_title);
        if (folderTitle != null) {
            folderTitle.setBackgroundResource(R.drawable.shape_border_back_highlighted); // Create this drawable
            folderTitle.setTextColor(ContextCompat.getColor(context, R.color.white)); // Example color
        }
        // Example: Elevation
        itemContainer.setElevation(context.getResources().getDimensionPixelSize(R.dimen.active_carousel_item_elevation));
    }

    void unhighlightFolder(MaskableFrameLayout itemContainer) {
        if (itemContainer == null) return;
        // Revert the highlight effects:
        // Example: Scale and Alpha
        itemContainer.animate().scaleX(1.0f).scaleY(1.0f).alpha(0.85f).setDuration(150).start(); // Make non-active slightly transparent/smaller

        // Example: Revert TextView background
        TextView folderTitle = itemContainer.findViewById(R.id.folder_title);
        if (folderTitle != null) {
            folderTitle.setBackgroundResource(R.drawable.shape_border_back); // Original drawable
            folderTitle.setTextColor(ContextCompat.getColor(context, R.color.original_text_color_for_title)); // Original color
        }
        // Example: Elevation
        itemContainer.setElevation(context.getResources().getDimensionPixelSize(R.dimen.inactive_carousel_item_elevation));
    }

}