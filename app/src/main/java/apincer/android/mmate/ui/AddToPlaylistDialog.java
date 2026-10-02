package apincer.android.mmate.ui;

import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import apincer.android.mmate.R;
import apincer.music.core.model.PlaylistEntry;
import apincer.music.core.model.Track;
import apincer.music.core.repository.PlaylistRepository;

/**
 * "Add to Playlist" picker shared by the library track menu and the song page: the user's own
 * song playlists, plus "New playlist…" which names one and adds the track to it.
 */
public final class AddToPlaylistDialog {
    private AddToPlaylistDialog() {}

    /** @param onAdded runs after a track was added (to refresh a playlist on screen); may be null */
    public static void show(Context context, Track track, Runnable onAdded) {
        show(context, track == null ? java.util.Collections.emptyList() : java.util.Collections.singletonList(track), onAdded);
    }

    public static void show(Context context, List<Track> tracks, Runnable onAdded) {
        if (context == null || tracks == null || tracks.isEmpty()) return;
        PlaylistRepository.loadPlaylists(context);
        List<PlaylistEntry> playlists = PlaylistRepository.getUserSongPlaylists();
        String[] items = new String[playlists.size() + 1];
        items[0] = context.getString(R.string.playlist_new);
        for (int i = 0; i < playlists.size(); i++) {
            items[i + 1] = playlists.get(i).getName();
        }

        new MaterialAlertDialogBuilder(context, R.style.AlertDialogTheme)
                .setTitle(R.string.playlist_add_title)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        showNewPlaylist(context, tracks, onAdded);
                    } else {
                        add(context, playlists.get(which - 1), tracks, onAdded);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static void showNewPlaylist(Context context, List<Track> tracks, Runnable onAdded) {
        EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setSingleLine(true);
        input.setHint(R.string.playlist_name_hint);
        FrameLayout container = new FrameLayout(context);
        int pad = Math.round(24 * context.getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad / 3, pad, 0);
        container.addView(input);

        new MaterialAlertDialogBuilder(context, R.style.AlertDialogTheme)
                .setTitle(R.string.playlist_new_title)
                .setView(container)
                .setPositiveButton(R.string.playlist_create_and_add, (dialog, which) -> {
                    PlaylistEntry entry = PlaylistRepository.createSongPlaylist(context, input.getText().toString());
                    if (entry == null) {
                        Toast.makeText(context, R.string.playlist_name_unavailable, Toast.LENGTH_SHORT).show();
                    } else {
                        add(context, entry, tracks, onAdded);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
        input.requestFocus();
    }

    private static void add(Context context, PlaylistEntry entry, List<Track> tracks, Runnable onAdded) {
        int added = 0;
        for (Track track : tracks) {
            if (PlaylistRepository.addTrackToPlaylist(context, entry.getUuid(), track)) added++;
        }
        String message;
        if (added == 0) {
            message = context.getString(R.string.playlist_already_in, entry.getName());
        } else if (tracks.size() == 1) {
            message = context.getString(R.string.playlist_added, entry.getName());
        } else {
            message = context.getResources().getQuantityString(R.plurals.playlist_added_songs, added, added, entry.getName());
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        if (added > 0 && onAdded != null) onAdded.run();
    }
}
