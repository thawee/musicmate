package apincer.android.mmate.ui;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static apincer.music.core.utils.StringUtils.formatAudioBitsDepth;
import static apincer.music.core.utils.StringUtils.formatAudioSampleRate;
import static apincer.music.core.utils.StringUtils.formatStorageSize;
import static apincer.music.core.utils.StringUtils.trim;
import static apincer.music.core.utils.StringUtils.trimToEmpty;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.palette.graphics.Palette;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import javax.inject.Inject;

import android.view.ViewGroup.MarginLayoutParams;
import android.widget.Toast;
import static apincer.android.mmate.utils.UIUtils.dpToPx;

import apincer.android.mmate.R;
import apincer.android.mmate.coil3.CoverartFetcher;
import apincer.android.mmate.service.MusicMateServiceImpl;
import apincer.android.mmate.ui.compose.DialogInterop;
import apincer.android.mmate.ui.view.VerdictFormatter;
import apincer.android.mmate.utils.BitmapHelper;
import apincer.android.mmate.utils.UIUtils;
import apincer.android.utils.FileUtils;
import apincer.music.core.Constants;
import apincer.music.core.authenticity.AudioAnalysisResult;
import apincer.music.core.authenticity.AudioAuthenticityAnalyzer;
import apincer.music.core.authenticity.SpectrogramGenerator;
import apincer.music.core.model.Track;
import apincer.music.core.playback.spi.PlaybackService;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.utils.ApplicationUtils;
import apincer.music.core.utils.ThaiEncodingUtils;
import apincer.music.core.utils.StringUtils;
import apincer.android.mmate.ui.viewmodel.TagsViewModel;
import apincer.android.mmate.worker.FileOperationTask;
import apincer.android.mmate.utils.TextBuilder;
import coil3.BitmapImage;
import coil3.Image;
import coil3.ImageLoader;
import coil3.SingletonImageLoader;
import coil3.request.CachePolicy;
import coil3.request.ImageRequest;
import coil3.size.Precision;
import coil3.size.Size;
import coil3.target.ImageViewTarget;
import coil3.target.Target;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.widget.TooltipCompat;
import com.google.android.material.button.MaterialButton;
import android.net.Uri;
import android.view.HapticFeedbackConstants;

import apincer.music.core.codec.FFMpegHelper;
import apincer.music.core.repository.FileRepository;
import apincer.music.core.provider.MusicFileProvider;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.regex.Pattern;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class TagsActivity extends AppCompatActivity {
    private static final String TAG = "TagsActivity";

    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private TagsViewModel viewModel;

    private ImageView coverArtView;
    private TabLayout tabLayout;
   // private Toolbar toolbar;
    private AppBarLayout appBarLayout;
    //private BottomAppBar bottomAppBar;

    private TextView titleView;
    private TextView artistView ;
    private TextView encInfo;
    private androidx.compose.ui.platform.ComposeView tagsHeaderBadges;

    private Fragment activeFragment;

    private boolean previewState = true;

    private AlertDialog progressDialog;

    private final AtomicLong lastProgressUpdate = new AtomicLong(0);

    private boolean isSaved = false;
    private boolean resultAlreadySet = false;

    private boolean isDirty = false;
    private int currentEditMode = 0;

    public void setDirty(boolean dirty) {
        this.isDirty = dirty;
    }

    private PlaybackService playbackService;
    private boolean isPlaybackServiceBound = false;

    @Inject
    TagRepository tagRepos;

    @Inject
    FileRepository fileRepos;

    @Inject
    FileOperationTask operationTask;

    private final ActivityResultLauncher<String> coverArtPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    applySelectedCoverArt(uri);
                }
            }
    );

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @SuppressLint("CheckResult")
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // Get the binder from the service and set the mediaServerService instance
            MusicMateServiceImpl.MusicMateServiceImplBinder binder = (MusicMateServiceImpl.MusicMateServiceImplBinder) service;
            playbackService = binder.getPlaybackService();
            isPlaybackServiceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            playbackService = null;
            isPlaybackServiceBound = false;
        }
    };

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        dismissProgressDialog(); // Ensure progress dialog is dismissed
    }

    @Override
    protected void onPause() {
        super.onPause();
        dismissProgressDialog();
        stopProgressBar();
    }
    @Override
    protected void onResume() {
        super.onResume();
    }

    private TextView tagInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        super.onCreate(savedInstanceState);

        // Enable Edge-to-Edge
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        //Enable Dynamic Colors
        DynamicColors.applyToActivitiesIfAvailable(getApplication());

        // set status bar color to black
        Window window = getWindow();
        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(window, window.getDecorView());
        insetsController.setAppearanceLightStatusBars(false);

        setContentView(R.layout.activity_tags);
        
        // Handle Status Bar Insets for Header
        appBarLayout = findViewById(R.id.appbar);
        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), systemBars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        viewModel = new ViewModelProvider(this).get(TagsViewModel.class);

        long[] tagIds = getIntent().getLongArrayExtra("MUSIC_TAG_IDS");
        if (tagIds != null && tagIds.length > 0) {
            loadMusicTagsFromDb(tagIds);
        }

        OnBackPressedCallback onBackPressedCallback = new TagsActivity.BackPressedCallback(true);
        getOnBackPressedDispatcher().addCallback(this, onBackPressedCallback);

        coverArtView = findViewById(R.id.panel_cover_art);
        if (coverArtView != null) {
            coverArtView.setOnClickListener(v -> doShowCoverArtActions());
        }
        CollapsingToolbarLayout toolBarLayout = findViewById(R.id.toolbar_layout);
        
        // Set dynamic height for the collapsing header
        int height = UIUtils.getScreenHeight(this);
        toolBarLayout.getLayoutParams().height = (int) (height * 0.85); // 85% of screen height for a better balance
        
        setupTitlePanelViews();
        setupActionButtons(0);

        // Handle Navigation Bar Insets for Bottom Capsule
        View bottomNav = findViewById(R.id.bottom_navigation_container);
        View bottomPanel = findViewById(R.id.bottom_navigation_panel);
        if (bottomNav != null) {
            ViewCompat.setOnApplyWindowInsetsListener(bottomNav, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                if (v.getLayoutParams() instanceof MarginLayoutParams) {
                    MarginLayoutParams mlp = (MarginLayoutParams) v.getLayoutParams();
                    mlp.bottomMargin = 0;
                    v.setLayoutParams(mlp);
                }
                if (bottomPanel != null) {
                    bottomPanel.setPadding(
                        bottomPanel.getPaddingLeft(),
                        bottomPanel.getPaddingTop(),
                        bottomPanel.getPaddingRight(),
                        systemBars.bottom + (int) dpToPx(this, 8)
                    );
                }
                return insets;
            });
        }

        observeViewModel();
        setupPageViewer();

        Intent intent = new Intent(this, MusicMateServiceImpl.class);
        bindService(intent, serviceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        databaseExecutor.shutdown();
        if(isPlaybackServiceBound) {
            unbindService(serviceConnection);
        }
    }

    private void loadMusicTagsFromDb(long[] ids) {
        if (ids == null || ids.length == 0) {
                         return;
                     }
                 databaseExecutor.execute(() -> {
                     if(viewModel != null) {
                         List<Track> musicTags = tagRepos.findByIds(ids);
                         viewModel.processAudioTagEditEvent(musicTags);
                     }
                 });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        //setupMenuToolbar();
        return true;
    }

    private void setupPageViewer() {
        appBarLayout = findViewById(R.id.appbar);
       // bottomAppBar = findViewById(R.id.bottom_app_bar);
        ViewPager2 viewPager = findViewById(R.id.viewpager);
        ViewCompat.setOnApplyWindowInsetsListener(viewPager, (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), (int)dpToPx(this, 160) + systemBars.bottom);
            return insets;
        });

        tabLayout = findViewById(R.id.tabLayout);
        TagsTabLayoutAdapter adapter = new TagsTabLayoutAdapter(getSupportFragmentManager(), getLifecycle());

        adapter.addNewTab(new TagsEditorFragment(), "Song Info");
        adapter.addNewTab(new TagsTechnicalFragment(), "Tech Info");
        viewPager.setAdapter(adapter);
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                activeFragment = adapter.fragments.get(position);
                if(previewState) {
                    setupActionButtons(0);
                }else {
                    setupActionButtons(1);
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                super.onPageScrollStateChanged(state);
            }
        });

        TabLayoutMediator tabLayoutMediator = new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> tab.setText(adapter.getPageTitle(position)));
        tabLayoutMediator.attach();

        appBarLayout.addOnOffsetChangedListener(new OffSetChangeListener());
    }

    private ImageView mBlurBackground;

    private void setupTitlePanelViews() {
        mBlurBackground = findViewById(R.id.main_background_blur);
        titleView = findViewById(R.id.panel_title);
        artistView = findViewById(R.id.panel_artist);
        tagsHeaderBadges = findViewById(R.id.tags_header_badges);
    }
    private void performHapticClick(View v) {
        if (v != null) {
            apincer.android.mmate.utils.Haptics.INSTANCE.selection(v);
        }
    }

    private void performHapticLongClick(View v) {
        if (v != null) {
            apincer.android.mmate.utils.Haptics.INSTANCE.longPress(v);
        }
    }

    private void setupActionButtons(int mode) {
        currentEditMode = mode;
        android.widget.LinearLayout previewToggleGroup = findViewById(R.id.preview_action_group);
        android.widget.LinearLayout editorToggleGroup = findViewById(R.id.editor_action_group);
        android.widget.LinearLayout techToggleGroup = findViewById(R.id.tech_action_group);

        MaterialButton btnDelete = findViewById(R.id.button_delete);
        MaterialButton btnOrganize = findViewById(R.id.button_organize);
        MaterialButton btnMore = findViewById(R.id.button_more);
        MaterialButton actionEditor = findViewById(R.id.action_editor);

        // Tooltips for accessibility
        TooltipCompat.setTooltipText(btnDelete, "Delete selected file(s)");
        TooltipCompat.setTooltipText(btnOrganize, "Organize / Move media file(s)");
        TooltipCompat.setTooltipText(btnMore, "More actions");
        TooltipCompat.setTooltipText(actionEditor, "Open metadata editor");

        // Batch count dynamic updates
        int itemCount = getEditItems().size();
        if (itemCount > 1) {
            btnDelete.setText(getString(R.string.button_delete) + " (" + itemCount + ")");
            btnOrganize.setText(getString(R.string.button_organize) + " (" + itemCount + ")");
        } else {
            btnDelete.setText(R.string.button_delete);
            btnOrganize.setText(R.string.button_organize);
        }

        btnDelete.setOnClickListener(v -> {
            performHapticClick(v);
            doDeleteMediaItems();
        });
        btnOrganize.setOnClickListener(v -> {
            performHapticClick(v);
            doMoveMediaItems();
        });
        btnMore.setOnClickListener(v -> {
            performHapticClick(v);
            doShowMoreActions(v);
        });

        if(mode == 0) {
            previewToggleGroup.setVisibility(VISIBLE);
            editorToggleGroup.setVisibility(GONE);
            techToggleGroup.setVisibility(GONE);
            if (tabLayout != null) {
                tabLayout.setVisibility(GONE);
            }
            actionEditor.setOnClickListener(v -> {
                performHapticClick(v);
                if (tabLayout != null) {
                    tabLayout.setVisibility(VISIBLE);
                }
                appBarLayout.setExpanded(false, true);
                setupActionButtons(1);
            });

            MaterialButton btnPreviewSave = findViewById(R.id.action_preview_save);
            if (btnPreviewSave != null) {
                TooltipCompat.setTooltipText(btnPreviewSave, "Save changes (Long-press to Save & Exit)");
                if (itemCount > 1) {
                    btnPreviewSave.setText(getString(R.string.btn_save) + " (" + itemCount + ")");
                } else {
                    btnPreviewSave.setText(R.string.btn_save);
                }
                btnPreviewSave.setOnClickListener(v -> {
                    performHapticClick(v);
                    doSaveMediaItemsDirectly();
                });
                btnPreviewSave.setOnLongClickListener(v -> {
                    performHapticLongClick(v);
                    doSaveAndFinishDirectly();
                    return true;
                });
            }
        } else if (mode == 1) {
            if (tabLayout != null) {
                tabLayout.setVisibility(VISIBLE);
            }
            if (activeFragment instanceof TagsEditorFragment fragment) {
                // editor
                previewToggleGroup.setVisibility(GONE);
                editorToggleGroup.setVisibility(VISIBLE);
                techToggleGroup.setVisibility(GONE);

                MaterialButton btnReformat = findViewById(R.id.action_reformat);
                MaterialButton btnReadTag = findViewById(R.id.action_read_tag);
                MaterialButton btnSave = findViewById(R.id.action_save);

                TooltipCompat.setTooltipText(btnReformat, "Format tags (Long-press for Full Clean Pipeline)");
                TooltipCompat.setTooltipText(btnReadTag, "Read tags from file name");
                TooltipCompat.setTooltipText(btnSave, "Save changes (Long-press to Save & Exit)");

                if (itemCount > 1) {
                    btnSave.setText(getString(R.string.btn_save) + " (" + itemCount + ")");
                } else {
                    btnSave.setText(R.string.btn_save);
                }

                btnReformat.setOnClickListener(v -> {
                    performHapticClick(v);
                    fragment.doFormatTags();
                });
                btnReformat.setOnLongClickListener(v -> {
                    performHapticLongClick(v);
                    doFullCleanPipeline();
                    return true;
                });

                btnReadTag.setOnClickListener(v -> {
                    performHapticClick(v);
                    fragment.doShowReadTagsPreview();
                });

                btnSave.setOnClickListener(v -> {
                    performHapticClick(v);
                    fragment.doSaveMediaItem();
                });
                btnSave.setOnLongClickListener(v -> {
                    performHapticLongClick(v);
                    doSaveAndFinish(fragment);
                    return true;
                });

            } else if (activeFragment instanceof TagsTechnicalFragment fragment) {
                previewToggleGroup.setVisibility(GONE);
                editorToggleGroup.setVisibility(GONE);
                techToggleGroup.setVisibility(VISIBLE);

                MaterialButton btnReload = findViewById(R.id.btn_reload_tag);
                MaterialButton btnExtractArt = findViewById(R.id.btn_extract_coverart);
                MaterialButton btnRemoveArt = findViewById(R.id.btn_remove_coverart);

                TooltipCompat.setTooltipText(btnReload, "Reload tags from file");
                TooltipCompat.setTooltipText(btnExtractArt, "Extract embedded cover art to folder");
                TooltipCompat.setTooltipText(btnRemoveArt, "Remove embedded cover art");

                btnReload.setOnClickListener(v -> {
                    performHapticClick(v);
                    fragment.doResetTagFromFile();
                });
                btnExtractArt.setOnClickListener(v -> {
                    performHapticClick(v);
                    fragment.doExtractEmbedCoverart();
                });
                btnRemoveArt.setOnClickListener(v -> {
                    performHapticClick(v);
                    fragment.doRemoveEmbedCoverart();
                });
            }
        }
    }

    private void doShowMoreActions(View anchorView) {
        PopupMenu popup = new PopupMenu(this, anchorView);
        popup.getMenuInflater().inflate(R.menu.tag_more_actions_menu, popup.getMenu());

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            popup.getMenu().setGroupDividerEnabled(true);
        }
        apincer.android.mmate.utils.UIUtils.makePopForceShowIcon(popup);

        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_play_now) {
                doPlaySong();
                return true;
            } else if (itemId == R.id.action_add_to_queue) {
                doAddToQueue();
                return true;
            } else if (itemId == R.id.action_auto_tag) {
                doAutoTag();
                return true;
            } else if (itemId == R.id.action_search_match_tags) {
                doSearchAndMatchTags();
                return true;
            } else if (itemId == R.id.action_smart_clean_format) {
                doFullCleanPipeline();
                return true;
            } else if (itemId == R.id.action_spectrum) {
                doShowSpectrum();
                return true;
            } else if (itemId == R.id.action_open_folder) {
                ApplicationUtils.startFileExplorer(this, viewModel.displayTag.getValue());
                return true;
            } else if (itemId == R.id.action_web_search) {
                ApplicationUtils.webSearch(this, viewModel.displayTag.getValue());
                return true;
            } else if (itemId == R.id.action_share) {
                doShareAudioFile();
                return true;
            }
            return false; // Return false if the item click is not handled
        });

        // Optional: Set a dismiss listener
        popup.setOnDismissListener(menu -> {
            // Actions to perform when the popup is dismissed (optional)
        });

        // 4. Show the PopupMenu
        popup.show();
    }
    
    /**
     * Attempts to automatically fetch tags using MusicBrainz (by text) or AcoustID (by audio fingerprint)
     */
    private void doAutoTag() {
        List<Track> items = getEditItems();
        if (items.isEmpty()) return;
        
        startProgressBar();
        
        CompletableFuture.supplyAsync(() -> {
            apincer.music.core.repository.MusicBrainzClient mbClient = new apincer.music.core.repository.MusicBrainzClient();
            apincer.music.core.repository.AcoustIdClient acoustIdClient = new apincer.music.core.repository.AcoustIdClient();
            int fixed = 0;
            
            for (Track item : items) {
                String mbid = null;
                // If title and artist are completely missing, try AcoustID fingerprinting
                if (apincer.music.core.utils.StringUtils.isEmpty(item.getTitle()) && 
                    apincer.music.core.utils.StringUtils.isEmpty(item.getArtist())) {
                    mbid = acoustIdClient.lookupByFile(item.getPath(), (long) item.getAudioDuration());
                } else {
                    // Otherwise rely on text search using MusicBrainz
                    mbid = mbClient.searchRecording(item.getTitle(), item.getArtist());
                }
                
                if (mbid != null) {
                    apincer.music.core.repository.MusicBrainzClient.MusicBrainzMetadata meta = mbClient.getRecordingMetadata(mbid);
                    if (meta != null) {
                        boolean changed = false;
                        if (meta.title != null && !meta.title.isEmpty() && apincer.music.core.utils.StringUtils.isEmpty(item.getTitle())) {
                            item.setTitle(meta.title); changed = true;
                        }
                        if (meta.artist != null && !meta.artist.isEmpty() && apincer.music.core.utils.StringUtils.isEmpty(item.getArtist())) {
                            item.setArtist(meta.artist); changed = true;
                        }
                        if (meta.album != null && !meta.album.isEmpty() && apincer.music.core.utils.StringUtils.isEmpty(item.getAlbum())) {
                            item.setAlbum(meta.album); changed = true;
                        }
                        if (meta.year != null && !meta.year.isEmpty() && apincer.music.core.utils.StringUtils.isEmpty(item.getYear())) {
                            item.setYear(meta.year); changed = true;
                        }
                        if (meta.genre != null && !meta.genre.isEmpty() && apincer.music.core.utils.StringUtils.isEmpty(item.getGenre())) {
                            item.setGenre(meta.genre); changed = true;
                        }
                        
                        // Download cover art if we have a releaseId and no existing cover art
                        if (meta.releaseId != null && !meta.releaseId.isEmpty() && 
                            (item.getAlbumArtFilename() == null || item.getAlbumArtFilename().isEmpty() || !new java.io.File(item.getAlbumArtFilename()).exists())) {
                            java.io.File parentDir = new java.io.File(item.getPath()).getParentFile();
                            if (parentDir != null && parentDir.exists()) {
                                java.io.File coverFile = new java.io.File(parentDir, "Cover.jpg");
                                if (coverFile.exists()) {
                                    if (item.getAlbumArtFilename() == null || !item.getAlbumArtFilename().equals(coverFile.getAbsolutePath())) {
                                        item.setAlbumArtFilename(coverFile.getAbsolutePath());
                                        changed = true;
                                    }
                                } else if (mbClient.downloadCoverArt(meta.releaseId, coverFile)) {
                                    item.setAlbumArtFilename(coverFile.getAbsolutePath());
                                    changed = true;
                                }
                            }
                        }
                        
                        if (changed) fixed++;
                    }
                }
            }
            return fixed;
        }).thenAccept(fixed -> {
            runOnUiThread(() -> {
                if (isDestroyed() || isFinishing()) return;
                redisplayTag();
                stopProgressBar();
                if (fixed > 0) {
                    Toast.makeText(this, "Auto-tagged " + fixed + " items", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "No new tags found", Toast.LENGTH_SHORT).show();
                }
            });
        }).exceptionally(throwable -> {
            runOnUiThread(() -> {
                if (isDestroyed() || isFinishing()) return;
                redisplayTag();
                stopProgressBar();
                Toast.makeText(this, "Error during auto-tag", Toast.LENGTH_SHORT).show();
            });
            return null;
        });
    }
    
    /**
     * Interactive search and match for a single track's tags.
     */
    private void doSearchAndMatchTags() {
        List<Track> items = getEditItems();
        if (items.isEmpty()) return;
        if (items.size() > 1) {
            Toast.makeText(this, "Please select only one track for Search & Match", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Track item = items.get(0);
        
        // Show a custom glassy input dialog to let the user confirm or refine the title and artist query
        View dialogView = getLayoutInflater().inflate(R.layout.view_action_search_query_dialog, null);
        com.google.android.material.textfield.TextInputEditText titleInput = dialogView.findViewById(R.id.input_search_title);
        com.google.android.material.textfield.TextInputEditText artistInput = dialogView.findViewById(R.id.input_search_artist);
        View btnSearch = dialogView.findViewById(R.id.button_search);
        View btnClose = dialogView.findViewById(R.id.btn_close_search_dialog);

        titleInput.setText(item.getTitle());
        artistInput.setText(item.getArtist());

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnSearch.setOnClickListener(v -> {
            String qTitle = titleInput.getText() != null ? titleInput.getText().toString().trim() : "";
            String qArtist = artistInput.getText() != null ? artistInput.getText().toString().trim() : "";
            dialog.dismiss();
            performSearchAndMatch(item, qTitle, qArtist);
        });

        View btnCancel = dialogView.findViewById(R.id.button_cancel);

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }
        dialog.show();
    }
    
    private void performSearchAndMatch(Track item, String title, String artist) {
        startProgressBar();
        CompletableFuture.supplyAsync(() -> {
            apincer.music.core.repository.MusicBrainzClient mbClient = new apincer.music.core.repository.MusicBrainzClient();
            return mbClient.searchRecordingsList(title, artist, 15);
        }).thenAccept(results -> {
            runOnUiThread(() -> {
                stopProgressBar();
                if (results.isEmpty()) {
                    Toast.makeText(this, "No matches found on MusicBrainz", Toast.LENGTH_SHORT).show();
                    return;
                }
                showSearchResultsDialog(item, results);
            });
        }).exceptionally(throwable -> {
            runOnUiThread(() -> {
                stopProgressBar();
                Toast.makeText(this, "Error searching MusicBrainz", Toast.LENGTH_SHORT).show();
            });
            return null;
        });
    }
    
    private class SearchResultAdapter extends android.widget.ArrayAdapter<apincer.music.core.repository.MusicBrainzClient.MusicBrainzSearchResult> {
        public SearchResultAdapter(android.content.Context context, List<apincer.music.core.repository.MusicBrainzClient.MusicBrainzSearchResult> results) {
            super(context, 0, results);
        }

        @Override
        public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
            if (convertView == null) {
                convertView = android.view.LayoutInflater.from(getContext()).inflate(R.layout.view_list_item_search_result, parent, false);
            }

            apincer.music.core.repository.MusicBrainzClient.MusicBrainzSearchResult result = getItem(position);

            android.widget.TextView titleView = convertView.findViewById(R.id.search_result_title);
            android.widget.TextView artistView = convertView.findViewById(R.id.search_result_artist);
            android.widget.TextView albumView = convertView.findViewById(R.id.search_result_album);
            android.widget.ImageView coverView = convertView.findViewById(R.id.search_result_cover);

            titleView.setText(result.title);
            artistView.setText(result.artist);

            StringBuilder albumText = new StringBuilder();
            if (result.album != null && !result.album.isEmpty()) {
                albumText.append(result.album);
            }
            if (result.year != null && !result.year.isEmpty()) {
                if (albumText.length() > 0) albumText.append(" ");
                albumText.append("(").append(result.year).append(")");
            }
            albumView.setText(albumText.toString());

            if (result.releaseId != null && !result.releaseId.isEmpty()) {
                String coverUrl = "https://coverartarchive.org/release/" + result.releaseId + "/front-250";
                
                coil3.request.ImageRequest imageRequest = new coil3.request.ImageRequest.Builder(getContext())
                        .data(coverUrl)
                        .target(new coil3.target.ImageViewTarget(coverView))
                        .build();
                coil3.SingletonImageLoader.get(getContext()).enqueue(imageRequest);
            } else {
                coverView.setImageDrawable(null);
            }

            return convertView;
        }
    }
    
    private void showSearchResultsDialog(Track item, List<apincer.music.core.repository.MusicBrainzClient.MusicBrainzSearchResult> results) {
        View dialogView = getLayoutInflater().inflate(R.layout.view_action_search_results_dialog, null);
        android.widget.ListView listView = dialogView.findViewById(R.id.search_results_list);
        View btnClose = dialogView.findViewById(R.id.btn_close_results_dialog);

        SearchResultAdapter adapter = new SearchResultAdapter(this, results);
        listView.setAdapter(adapter);

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        listView.setOnItemClickListener((parent, view, position, id) -> {
            apincer.music.core.repository.MusicBrainzClient.MusicBrainzSearchResult selected = results.get(position);
            dialog.dismiss();
            applySelectedSearchResult(item, selected);
        });

        View btnCancel = dialogView.findViewById(R.id.button_cancel);

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }
        dialog.show();
    }
    
    private void applySelectedSearchResult(Track item, apincer.music.core.repository.MusicBrainzClient.MusicBrainzSearchResult selected) {
        startProgressBar();
        CompletableFuture.supplyAsync(() -> {
            apincer.music.core.repository.MusicBrainzClient mbClient = new apincer.music.core.repository.MusicBrainzClient();
            apincer.music.core.repository.MusicBrainzClient.MusicBrainzMetadata meta = mbClient.getRecordingMetadata(selected.recordingId);
            boolean changed = false;
            
            if (meta != null) {
                if (meta.title != null && !meta.title.isEmpty()) {
                    item.setTitle(meta.title); changed = true;
                }
                if (meta.artist != null && !meta.artist.isEmpty()) {
                    item.setArtist(meta.artist); changed = true;
                }
                if (meta.album != null && !meta.album.isEmpty()) {
                    item.setAlbum(meta.album); changed = true;
                }
                if (meta.year != null && !meta.year.isEmpty()) {
                    item.setYear(meta.year); changed = true;
                }
                if (meta.genre != null && !meta.genre.isEmpty()) {
                    item.setGenre(meta.genre); changed = true;
                }
                
                // For manual Search & Match, force download and overwrite the cover art
                if (meta.releaseId != null && !meta.releaseId.isEmpty()) {
                    java.io.File parentDir = new java.io.File(item.getPath()).getParentFile();
                    if (parentDir != null && parentDir.exists()) {
                        java.io.File coverFile = new java.io.File(parentDir, "Cover.jpg");
                        if (mbClient.downloadCoverArt(meta.releaseId, coverFile)) {
                            item.setAlbumArtFilename(coverFile.getAbsolutePath());
                            changed = true;
                        } else if (coverFile.exists()) {
                            if (item.getAlbumArtFilename() == null || !item.getAlbumArtFilename().equals(coverFile.getAbsolutePath())) {
                                item.setAlbumArtFilename(coverFile.getAbsolutePath());
                                changed = true;
                            }
                        }
                    }
                }
            }
            return changed;
        }).thenAccept(changed -> {
            runOnUiThread(() -> {
                if (isDestroyed() || isFinishing()) return;
                redisplayTag();
                stopProgressBar();
                if (changed) {
                    Toast.makeText(this, "Match applied successfully", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "No changes applied", Toast.LENGTH_SHORT).show();
                }
            });
        }).exceptionally(throwable -> {
            runOnUiThread(() -> {
                if (isDestroyed() || isFinishing()) return;
                redisplayTag();
                stopProgressBar();
                Toast.makeText(this, "Error applying selected match", Toast.LENGTH_SHORT).show();
            });
            return null;
        });
    }

    /**
     * Fix Thai encoding for all selected tracks.
     */
    private void doFixThaiEncoding() {
        List<Track> items = getEditItems();
        if (items.isEmpty()) return;
        
        startProgressBar();
        
        CompletableFuture.supplyAsync(() -> {
            int fixed = 0;
            for (Track item : items) {
                if (ThaiEncodingUtils.isGarbledThai(item.getTitle())) {
                    item.setTitle(ThaiEncodingUtils.fixThaiEncoding(item.getTitle()));
                    fixed++;
                }
                if (ThaiEncodingUtils.isGarbledThai(item.getArtist())) {
                    item.setArtist(ThaiEncodingUtils.fixThaiEncoding(item.getArtist()));
                    fixed++;
                }
                if (ThaiEncodingUtils.isGarbledThai(item.getAlbum())) {
                    item.setAlbum(ThaiEncodingUtils.fixThaiEncoding(item.getAlbum()));
                    fixed++;
                }
                if (ThaiEncodingUtils.isGarbledThai(item.getAlbumArtist())) {
                    item.setAlbumArtist(ThaiEncodingUtils.fixThaiEncoding(item.getAlbumArtist()));
                    fixed++;
                }
                if (ThaiEncodingUtils.isGarbledThai(item.getGenre())) {
                    item.setGenre(ThaiEncodingUtils.fixThaiEncoding(item.getGenre()));
                    fixed++;
                }
                if (ThaiEncodingUtils.isGarbledThai(item.getComposer())) {
                    item.setComposer(ThaiEncodingUtils.fixThaiEncoding(item.getComposer()));
                    fixed++;
                }
            }
            return fixed;
        }).thenAccept(fixed -> {
            runOnUiThread(() -> {
                if (isDestroyed() || isFinishing()) return;
                redisplayTag();
                stopProgressBar();
                Toast.makeText(this, "Fixed encoding for " + fixed + " fields", Toast.LENGTH_SHORT).show();
            });
        }).exceptionally(throwable -> {
            runOnUiThread(() -> {
                if (isDestroyed() || isFinishing()) return;
                redisplayTag();
                stopProgressBar();
            });
            return null;
        });
    }

    private void handleQuickFix(String actionId) {
        if ("thai_fix".equals(actionId)) {
            doFullCleanPipeline();
        } else if ("auto_tag".equals(actionId)) {
            doAutoTag();
        } else if ("spectrum".equals(actionId)) {
            doShowSpectrum();
        }
    }

    private void doPlaySong() {
        Track track = viewModel.displayTag.getValue();
        if (track == null && !getEditItems().isEmpty()) {
            track = getEditItems().get(0);
        }
        if (track == null) return;

        if (playbackService != null) {
            playbackService.playSong(track);
            Toast.makeText(this, "Playing: " + track.getTitle(), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Playback service not connected", Toast.LENGTH_SHORT).show();
        }
    }

    private void doPlayTrack(Track track) {
        if (track == null) return;
        if (playbackService != null) {
            playbackService.playSong(track);
            Toast.makeText(this, "Playing: " + track.getTitle(), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Playback service not connected", Toast.LENGTH_SHORT).show();
        }
    }

    private void doPlayAllTracks(List<Track> tracks) {
        if (tracks == null || tracks.isEmpty()) return;
        if (playbackService != null) {
            playbackService.playSong(tracks.get(0));
            if (playbackService.getQueueManager() != null && tracks.size() > 1) {
                for (int i = 1; i < tracks.size(); i++) {
                    playbackService.getQueueManager().addPlayingQueue(tracks.get(i));
                }
            }
            Toast.makeText(this, "Playing " + tracks.size() + " tracks", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Playback service not connected", Toast.LENGTH_SHORT).show();
        }
    }

    private void doQueueAllTracks(List<Track> tracks) {
        if (tracks == null || tracks.isEmpty()) return;
        if (playbackService != null && playbackService.getQueueManager() != null) {
            for (Track t : tracks) {
                playbackService.getQueueManager().addPlayingQueue(t);
            }
            Toast.makeText(this, "Added " + tracks.size() + " track(s) to queue", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Playback service not connected", Toast.LENGTH_SHORT).show();
        }
    }

    private void doAddToQueue() {
        List<Track> items = getEditItems();
        if (items.isEmpty()) return;

        if (playbackService != null && playbackService.getQueueManager() != null) {
            for (Track t : items) {
                playbackService.getQueueManager().addPlayingQueue(t);
            }
            Toast.makeText(this, "Added " + items.size() + " track(s) to queue", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Playback service not connected", Toast.LENGTH_SHORT).show();
        }
    }

    private void doCleanTagNoise() {
        List<Track> items = getEditItems();
        if (items.isEmpty()) return;

        int cleaned = 0;
        Pattern junkPattern = Pattern.compile(
                "(?i)(\\[(flac|320k|320kbps|lossless|hq|hd|m4a|mp3|official|lyrics|video|explicit|remastered[^\\]]*)\\]|\\((official[^\\]\\)]*|lyrics?|video|audio|explicit|remastered[^\\)]*)\\)|https?://\\S+|www\\.\\S+)"
        );

        for (Track item : items) {
            boolean changed = false;
            if (!StringUtils.isEmpty(item.getTitle())) {
                String original = item.getTitle();
                String clean = junkPattern.matcher(original).replaceAll("").trim();
                clean = clean.replaceAll("\\s{2,}", " ").replaceAll("^[-–—\\s]+|[-–—\\s]+$", "");
                if (!clean.isEmpty() && !clean.equals(original)) {
                    item.setTitle(clean);
                    changed = true;
                }
            }
            if (!StringUtils.isEmpty(item.getArtist())) {
                String original = item.getArtist();
                String clean = junkPattern.matcher(original).replaceAll("").trim();
                clean = clean.replaceAll("\\s{2,}", " ").replaceAll("^[-–—\\s]+|[-–—\\s]+$", "");
                if (!clean.isEmpty() && !clean.equals(original)) {
                    item.setArtist(clean);
                    changed = true;
                }
            }
            if (changed) cleaned++;
        }

        redisplayTag();
        isDirty = true;
        Toast.makeText(this, "Cleaned tag noise on " + cleaned + " track(s)", Toast.LENGTH_SHORT).show();
    }

    private void doFormatTitleCase() {
        List<Track> items = getEditItems();
        if (items.isEmpty()) return;

        int formatted = 0;
        for (Track item : items) {
            boolean changed = false;
            if (!StringUtils.isEmpty(item.getTitle())) {
                String titleCase = toTitleCase(item.getTitle());
                if (!titleCase.equals(item.getTitle())) {
                    item.setTitle(titleCase);
                    changed = true;
                }
            }
            if (!StringUtils.isEmpty(item.getArtist())) {
                String titleCase = toTitleCase(item.getArtist());
                if (!titleCase.equals(item.getArtist())) {
                    item.setArtist(titleCase);
                    changed = true;
                }
            }
            if (!StringUtils.isEmpty(item.getAlbum())) {
                String titleCase = toTitleCase(item.getAlbum());
                if (!titleCase.equals(item.getAlbum())) {
                    item.setAlbum(titleCase);
                    changed = true;
                }
            }
            if (changed) formatted++;
        }

        redisplayTag();
        isDirty = true;
        Toast.makeText(this, "Formatted Title Case on " + formatted + " track(s)", Toast.LENGTH_SHORT).show();
    }

    private String toTitleCase(String input) {
        if (input == null || input.isEmpty()) return input;
        StringBuilder sb = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : input.toCharArray()) {
            if (Character.isWhitespace(c) || c == '(' || c == '[' || c == '-' || c == '/' || c == '.') {
                capitalizeNext = true;
                sb.append(c);
            } else if (capitalizeNext) {
                sb.append(Character.toTitleCase(c));
                capitalizeNext = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    private void doSaveAndFinish(TagsEditorFragment fragment) {
        if (fragment != null) {
            Toast.makeText(this, "Saving & closing...", Toast.LENGTH_SHORT).show();
            fragment.doSaveMediaItem(() -> {
                // Called on completion — safe to finish now
                runOnUiThread(this::finish);
            });
        }
    }

    public void doSaveMediaItemsDirectly() {
        doSaveMediaItemsDirectly(null);
    }

    public void doSaveMediaItemsDirectly(@Nullable Runnable onComplete) {
        if (activeFragment instanceof TagsEditorFragment fragment) {
            if (onComplete != null) {
                fragment.doSaveMediaItem(onComplete);
            } else {
                fragment.doSaveMediaItem();
            }
            return;
        }

        List<Track> items = getEditItems();
        if (items.isEmpty()) {
            if (onComplete != null) onComplete.run();
            return;
        }

        startProgressBar();
        int totalItems = items.size();
        CompletableFuture.runAsync(() -> {
            int success = 0;
            int failed = 0;
            int count = 0;
            for (Track tag : items) {
                try {
                    boolean status = fileRepos != null && fileRepos.setMusicTag(tag);
                    if (status) success++; else failed++;
                } catch (Exception e) {
                    failed++;
                    Log.e(TAG, "doSaveMediaItemsDirectly error for " + tag.getPath(), e);
                }
                count++;
                final int current = count;
                runOnUiThread(() -> updateProgressBar(current + "/" + totalItems));
            }
            final int successCount = success;
            final int failedCount = failed;
            runOnUiThread(() -> {
                stopProgressBar();
                isDirty = false;
                redisplayTag();
                if (failedCount == 0) {
                    Toast.makeText(this, "Saved " + successCount + " item(s)", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Saved " + successCount + " item(s), " + failedCount + " failed", Toast.LENGTH_SHORT).show();
                }
                if (onComplete != null) onComplete.run();
            });
        }, Executors.newSingleThreadExecutor());
    }

    private void doSaveAndFinishDirectly() {
        Toast.makeText(this, "Saving & closing...", Toast.LENGTH_SHORT).show();
        doSaveMediaItemsDirectly(this::finish);
    }

    private void doFullCleanPipeline() {
        List<Track> items = getEditItems();
        if (items.isEmpty()) return;

        startProgressBar();
        CompletableFuture.supplyAsync(() -> {
            int noiseCleaned = 0;
            int titleCased = 0;
            int thaiFixed = 0;

            Pattern junkPattern = Pattern.compile(
                    "(?i)(\\[(flac|320k|320kbps|lossless|hq|hd|m4a|mp3|official|lyrics|video|explicit|remastered[^\\]]*)\\]|\\((official[^\\]\\)]*|lyrics?|video|audio|explicit|remastered[^\\)]*)\\)|https?://\\S+|www\\.\\S+)"
            );

            for (Track item : items) {
                // 1. Thai encoding repair
                if (ThaiEncodingUtils.isGarbledThai(item.getTitle())) {
                    item.setTitle(ThaiEncodingUtils.fixThaiEncoding(item.getTitle()));
                    thaiFixed++;
                }
                if (ThaiEncodingUtils.isGarbledThai(item.getArtist())) {
                    item.setArtist(ThaiEncodingUtils.fixThaiEncoding(item.getArtist()));
                    thaiFixed++;
                }
                if (ThaiEncodingUtils.isGarbledThai(item.getAlbum())) {
                    item.setAlbum(ThaiEncodingUtils.fixThaiEncoding(item.getAlbum()));
                }
                if (ThaiEncodingUtils.isGarbledThai(item.getGenre())) {
                    item.setGenre(ThaiEncodingUtils.fixThaiEncoding(item.getGenre()));
                }

                // 2. Junk tag noise cleaning
                if (!StringUtils.isEmpty(item.getTitle())) {
                    String original = item.getTitle();
                    String clean = junkPattern.matcher(original).replaceAll("").trim();
                    clean = clean.replaceAll("\\s{2,}", " ").replaceAll("^[-–—\\s]+|[-–—\\s]+$", "");
                    if (!clean.isEmpty() && !clean.equals(original)) {
                        item.setTitle(clean);
                        noiseCleaned++;
                    }
                }
                if (!StringUtils.isEmpty(item.getArtist())) {
                    String original = item.getArtist();
                    String clean = junkPattern.matcher(original).replaceAll("").trim();
                    clean = clean.replaceAll("\\s{2,}", " ").replaceAll("^[-–—\\s]+|[-–—\\s]+$", "");
                    if (!clean.isEmpty() && !clean.equals(original)) {
                        item.setArtist(clean);
                    }
                }

                // 3. Title case standardisation
                if (!StringUtils.isEmpty(item.getTitle())) {
                    item.setTitle(toTitleCase(item.getTitle()));
                    titleCased++;
                }
                if (!StringUtils.isEmpty(item.getArtist())) {
                    item.setArtist(toTitleCase(item.getArtist()));
                }
                if (!StringUtils.isEmpty(item.getAlbum())) {
                    item.setAlbum(toTitleCase(item.getAlbum()));
                }
            }
            return String.format(Locale.getDefault(), "Full Clean Pipeline applied to %d track(s)", items.size());
        }).thenAccept(msg -> {
            runOnUiThread(() -> {
                if (isDestroyed() || isFinishing()) return;
                redisplayTag();
                isDirty = true;
                stopProgressBar();
                Toast.makeText(this, "⚡ " + msg, Toast.LENGTH_SHORT).show();
            });
        }).exceptionally(ex -> {
            runOnUiThread(() -> {
                if (isDestroyed() || isFinishing()) return;
                stopProgressBar();
                Toast.makeText(this, "Clean failed: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
            });
            return null;
        });
    }

    private void doShareAudioFile() {
        List<Track> items = getEditItems();
        if (items.isEmpty()) return;

        try {
            if (items.size() == 1) {
                Track track = items.get(0);
                Uri fileUri = MusicFileProvider.getUriForFile(track.getPath());
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("audio/*");
                shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(shareIntent, "Share Audio File"));
            } else {
                java.util.ArrayList<Uri> uriList = new java.util.ArrayList<>();
                for (Track t : items) {
                    uriList.add(MusicFileProvider.getUriForFile(t.getPath()));
                }
                Intent shareIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
                shareIntent.setType("audio/*");
                shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriList);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(shareIntent, "Share " + items.size() + " Audio Files"));
            }
        } catch (Exception ex) {
            Log.e(TAG, "doShareAudioFile", ex);
            Toast.makeText(this, "Cannot share audio file: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public void doExtractEmbedCoverart() {
        startProgressBar();
        CompletableFuture.runAsync(() -> {
            for (Track tag : getEditItems()) {
                File pathFile = new File(tag.getPath()).getParentFile();
                if (pathFile != null) {
                    File coverArtFile = new File(pathFile, "Cover.jpg");
                    FFMpegHelper.extractCoverArt(tag.getPath(), coverArtFile, null);
                }
            }
        }).thenAccept(v -> {
            runOnUiThread(() -> {
                stopProgressBar();
                Toast.makeText(this, "Cover art extracted to folder", Toast.LENGTH_SHORT).show();
            });
        }).exceptionally(ex -> {
            runOnUiThread(this::stopProgressBar);
            return null;
        });
    }

    public void doRemoveEmbedCoverart() {
        startProgressBar();
        CompletableFuture.runAsync(() -> {
            for (Track tag : getEditItems()) {
                FFMpegHelper.removeCoverArt(getApplicationContext(), tag);
                if (fileRepos != null) {
                    fileRepos.scanMusicFile(new File(tag.getPath()), false);
                }
            }
        }).thenAccept(v -> {
            runOnUiThread(() -> {
                redisplayTag();
                stopProgressBar();
                Toast.makeText(this, "Cover art removed", Toast.LENGTH_SHORT).show();
            });
        }).exceptionally(ex -> {
            runOnUiThread(this::stopProgressBar);
            return null;
        });
    }

    private void doShowCoverArtActions() {
        String[] options = new String[]{
                getString(R.string.menu_search_cover_art),
                getString(R.string.menu_pick_cover_art),
                getString(R.string.menu_extract_coverart),
                getString(R.string.menu_remove_coverart)
        };

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.cd_album_art)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        doSearchAndMatchTags();
                    } else if (which == 1) {
                        try {
                            coverArtPickerLauncher.launch("image/*");
                        } catch (Exception e) {
                            Toast.makeText(this, "Could not open photo picker", Toast.LENGTH_SHORT).show();
                        }
                    } else if (which == 2) {
                        doExtractEmbedCoverart();
                    } else if (which == 3) {
                        doRemoveEmbedCoverart();
                    }
                })
                .show();
    }

    private void applySelectedCoverArt(android.net.Uri uri) {
        startProgressBar();
        CompletableFuture.runAsync(() -> {
            try {
                Track display = viewModel.displayTag.getValue();
                if (display == null && !getEditItems().isEmpty()) {
                    display = getEditItems().get(0);
                }
                if (display != null) {
                    File parentDir = new File(display.getPath()).getParentFile();
                    if (parentDir != null) {
                        File targetCover = new File(parentDir, "Cover.jpg");
                        try (InputStream in = getContentResolver().openInputStream(uri);
                             OutputStream out = new FileOutputStream(targetCover)) {
                            byte[] buffer = new byte[8192];
                            int read;
                            while ((read = in.read(buffer)) != -1) {
                                out.write(buffer, 0, read);
                            }
                        }
                        for (Track item : getEditItems()) {
                            item.setAlbumArtFilename(targetCover.getAbsolutePath());
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error applying selected cover art", e);
            }
        }).thenAccept(v -> {
            runOnUiThread(() -> {
                isDirty = true;
                redisplayTag();
                Track current = viewModel.displayTag.getValue();
                if (current != null) {
                    loadImages(current);
                }
                stopProgressBar();
                Toast.makeText(this, "Cover art updated", Toast.LENGTH_SHORT).show();
            });
        }).exceptionally(ex -> {
            runOnUiThread(this::stopProgressBar);
            return null;
        });
    }

    @SuppressLint("SetTextI18n")
    private void doShowSpectrum() {
        if (getEditItems().isEmpty()) return;

        Track track = getEditItems().get(0);
        View cview = getLayoutInflater().inflate(R.layout.view_action_spectrum, null);

        TextView formatLabel = cview.findViewById(R.id.format_labels);
        TextView formatText = cview.findViewById(R.id.format_values);
        TextView analyticsLabel = cview.findViewById(R.id.analytics_labels);
        TextView analyticsText = cview.findViewById(R.id.analytics_values);
        TextView filenameText = cview.findViewById(R.id.filename);
        TextView qualityScore = cview.findViewById(R.id.quality_score);
        ImageView spectrumView = cview.findViewById(R.id.spectrum_view);
        ProgressBar spinner = cview.findViewById(R.id.analysis_progress_spinner); // From the new XML

        filenameText.setText(FileUtils.getFileName(track.getPath())+"."+FileUtils.getExtension(track.getPath()));
        TextBuilder formatSpan = new TextBuilder(getApplicationContext());
        TextBuilder analyticsSpan = new TextBuilder(getApplicationContext());

        // format
        final int textSize = 11;
        int labelColor = Color.GRAY;
        int valueColor = Color.WHITE;

        formatSpan.append("FORMAT\n", labelColor, textSize, true);
        formatSpan.append("  Type:\n", labelColor, textSize, true);
        formatSpan.append("  Sample Rate:\n", labelColor, textSize, true);
        formatSpan.append("  Bit Depth:\n", labelColor, textSize, true);
        formatSpan.append("  Size:\n", labelColor, textSize, true);
        formatLabel.setText(formatSpan.build());

        formatSpan = new TextBuilder(getApplicationContext());
        formatSpan.append("\n");

        String textValue = track.getFileType().toUpperCase();
        formatSpan.append(textValue+"\n", valueColor, textSize, false);

        textValue = formatAudioSampleRate(track.getAudioSampleRate(), true);
        formatSpan.append(textValue+"\n", valueColor, textSize, false);

        textValue = formatAudioBitsDepth(track.getAudioBitsDepth());
        formatSpan.append(textValue+"\n", valueColor, textSize, false);

        textValue = formatStorageSize(track.getFileSize());
        formatSpan.append(textValue+"\n", valueColor, textSize, false);

        formatText.setText(formatSpan.build());

        // analytics
        //Signal Analytics, Nyquist, Dynamic Range, Peak Amplitude, RMS Level
        analyticsSpan.append("ANALYTICS\n", labelColor, textSize, true);
        analyticsSpan.append("  Nyquist:\n", labelColor, textSize, true);
        analyticsSpan.append("  Spectral Cutoff:\n", labelColor, textSize, true);
        analyticsSpan.append("  Dynamic Range:\n", labelColor, textSize, true);
        analyticsSpan.append("  True Peak:\n", labelColor, textSize, true);
        analyticsSpan.append("  RMS Level:", labelColor, textSize, true);
        analyticsLabel.setText(analyticsSpan.build());

        qualityScore.setText(R.string.analyzing);

        if (spinner != null) spinner.setVisibility(VISIBLE);
        final boolean[] completedNext = {false};

        AudioAuthenticityAnalyzer.analyze(
                track.getPath(),
                (int) track.getAudioSampleRate(),
                track.getAudioBitsDepth(),
                new AudioAuthenticityAnalyzer.Callback() {

                    @Override
                    public void onResult(AudioAnalysisResult r) {
                    /*
                        Log.d("AudioAnalysis", "Verdict: " + r.verdict);
                        Log.d("AudioAnalysis", "DR: " + r.dynamicRange);
                        Log.d("AudioAnalysis", "Noise floor: " + r.noiseFloor);
                        Log.d("AudioAnalysis", "Peak: " + r.peak);
                        Log.d("AudioAnalysis", "bitDepth: " + r.bitDepth);
                        Log.d("AudioAnalysis", "sampleRate: " + r.sampleRate);
                        Log.d("AudioAnalysis", "highBandRms: " + r.highBandRms);
                        Log.d("AudioAnalysis", "lowBandRms: " + r.lowBandRms);
                        Log.d("AudioAnalysis", "Flatness: " + r.spectralFlatness); */

                        runOnUiThread(() -> {
                            String result = r.verdict;

                            // Apply the formatted spannable text
                            qualityScore.setText(VerdictFormatter.format(getApplicationContext(), result));

                            TextBuilder analyticsSpan = new TextBuilder(getApplicationContext());
                            analyticsSpan.append("\n");

                            String textValue = formatAudioSampleRate(track.getAudioSampleRate() /2, true);
                            analyticsSpan.append(textValue+"\n", valueColor, textSize, false);

                            textValue = formatAudioSampleRate((long) r.rolloff, true);
                            analyticsSpan.append(textValue+"\n", valueColor, textSize, false);

                            textValue = String.format(Locale.ENGLISH,"%.2f dB", r.dynamicRange);
                            analyticsSpan.append(textValue+"\n", valueColor, textSize, false);

                            textValue = String.format(Locale.ENGLISH,"%.2f dB", r.peak);
                            analyticsSpan.append(textValue+"\n", valueColor, textSize, false);

                            textValue = String.format(Locale.ENGLISH,"%.2f dB", r.rms);
                            analyticsSpan.append(textValue+"\n", valueColor, textSize, false);

                            analyticsText.setText(analyticsSpan.build());

                            if(completedNext[0]) {
                                if (spinner != null) spinner.setVisibility(GONE);
                            }
                            completedNext[0] = true;
                        });
                    }

                    @Override
                    public void onError(String error) {
                        Log.e("AudioAnalysis", error);
                    }
                });

        SpectrogramGenerator.generate(getApplicationContext(), track.getPath(), track.getAudioEncoding (), track.getAudioBitsDepth(), (int) track.getAudioSampleRate(),
                new SpectrogramGenerator.Callback() {

                    @Override
                    public void onSuccess(String outputPath) {

                        Log.d("AudioAnalysis", "Spectrogram saved: " + outputPath);

                        runOnUiThread(() -> {
                            ImageRequest imageRequest = new ImageRequest.Builder(getApplicationContext())
                                    .data(new File(outputPath))
                                    .size(Size.ORIGINAL)
                                    // Disables the memory cache (prevents showing the previous track's image)
                                    .memoryCachePolicy(CachePolicy.DISABLED)
                                    // Disables the disk cache (forces a fresh read from your FFmpeg output)
                                    .diskCachePolicy(CachePolicy.DISABLED)
                                    // Highly recommended for spectrograms to ensure sharp detail
                                    .precision(Precision.EXACT)
                                    .target(new ImageViewTarget(spectrumView))
                                    .build();
                            SingletonImageLoader.get(getApplicationContext()).enqueue(imageRequest);
                            if(completedNext[0]) {
                                if (spinner != null) spinner.setVisibility(GONE);
                            }
                            completedNext[0] = true;
                        });
                    }

                    @Override
                    public void onError(String error) {

                        Log.e("AudioAnalysis", error);

                    }
                });

        // Dialog Setup
        AlertDialog alert = new MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
                .setView(cview)
                .setCancelable(true)
                .create();

        // Use Window flags for a true edge-to-edge transparent blur look
        if (alert.getWindow() != null) {
            alert.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            alert.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        }

        View btnClose = cview.findViewById(R.id.btn_close);
        View btnOK = cview.findViewById(R.id.button_ok);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> alert.dismiss());
        }
        if (btnOK != null) {
            btnOK.setOnClickListener(v -> alert.dismiss());
        }
        alert.show();
        // After alert.show(), add these lines:
        if (alert.getWindow() != null) {
            // This allows the window to dim the background
            alert.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

            // Set how dark the dim should be (0.0 to 1.0)
            WindowManager.LayoutParams lp = alert.getWindow().getAttributes();
            lp.dimAmount = 0.75f; // 75% dim for high contrast
            alert.getWindow().setAttributes(lp);
        }
    }

    @SuppressLint("CheckResult")
    private void observeViewModel() {

        viewModel.displayTag.observe(this, musicTag -> {
            // This is your new 'displayTag'
            updateTitlePanel(musicTag); // Pass the new displayTag to the update method
            // Update cover art using Coil based on this new displayTag
            loadImages(musicTag);
            updateViewPagers(musicTag);
            dismissProgressDialog();
        });

      //  viewModel.drMeasurementStatus.observe(this, this::handleOperationStatus);
    }

    private void updateViewPagers(Track musicTag) {
        if (activeFragment instanceof TagsEditorFragment) {
            ((TagsEditorFragment) activeFragment).initEditorInputs();
        } else if (activeFragment instanceof TagsTechnicalFragment) {
            // ((TagsTechnicalFragment) activeFragment).displayTechnicalInfo(musicTag); // Re-renders automatically in Compose
        }
    }

    @SuppressLint("CheckResult")
    protected void updateTitlePanel(Track currentDisplayTag) {
        if (currentDisplayTag == null) {
            if (titleView != null) titleView.setText("");
            if (artistView != null) artistView.setText("");
            return;
        }

        String title = trim(currentDisplayTag.getTitle(), " - ");
        if (titleView != null) {
            titleView.setText(title.isEmpty() ? "Unknown Title" : title);
        }

        String artist = trim(currentDisplayTag.getArtist(), " - ");
        String album = trim(currentDisplayTag.getAlbum(), " - ");
        if (artistView != null) {
            if (artist.isEmpty() && album.isEmpty()) {
                artistView.setText("");
            } else if (album.isEmpty() || album.startsWith("[")) {
                artistView.setText(artist);
            } else if (artist.isEmpty()) {
                artistView.setText(album);
            } else {
                artistView.setText(artist + " • " + album);
            }
        }

        // load coverArt & blur background
        loadImages(currentDisplayTag);

        if (tagsHeaderBadges != null) {
            DialogInterop.setTagsHeaderBadges(
                    tagsHeaderBadges,
                    currentDisplayTag,
                    getEditItems().size(),
                    viewModel,
                    this::doPlayTrack,
                    this::doPlayAllTracks,
                    this::doQueueAllTracks,
                    this::doBackToMainActivity,
                    this::handleQuickFix
            );
        }
    }

    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        progressDialog = null; // Allow it to be recreated
    }

    private void doBackToMainActivity(String filterType, String filterText) {

        Intent resultIntent = new Intent();
        resultIntent.putExtra(Constants.KEY_FILTER_TYPE, filterType);
        resultIntent.putExtra(Constants.KEY_FILTER_KEYWORD, trimToEmpty(filterText));

        // Set the result to RESULT_OK and pass the intent containing the data
        setResult(AppCompatActivity.RESULT_OK, resultIntent);
        resultAlreadySet = true;

        finish();
    }

    private void applyGlassyColor(int color) {
        int alphaColor = ColorUtils.setAlphaComponent(color, 64); // ~25% opacity
        if (appBarLayout != null && appBarLayout.getBackground() != null) {
            appBarLayout.getBackground().setTint(alphaColor);
            appBarLayout.getBackground().setTintMode(PorterDuff.Mode.SRC_ATOP);
        }
        View bottomNav = findViewById(R.id.bottom_navigation_container);
        if (bottomNav != null && bottomNav.getBackground() != null) {
            bottomNav.getBackground().setTint(alphaColor);
            bottomNav.getBackground().setTintMode(PorterDuff.Mode.SRC_ATOP);
        }
    }

    private void loadImages(Track displayTag) {
        if(displayTag ==null) return;

        // Load all images in parallel
        ImageLoader imageLoader = SingletonImageLoader.get(getApplicationContext());

        // Cover art with higher priority
        ImageRequest coverRequest = CoverartFetcher.builder(getApplicationContext(), displayTag)
                .size(Size.ORIGINAL)
                .data(displayTag)
                .target(new Target() {
                    @Override
                    public void onStart(@Nullable Image placeholder) {}

                    @Override
                    public void onSuccess(@NonNull Image image) {
                        if (image instanceof BitmapImage) {
                            Bitmap bitmap = ((BitmapImage) image).getBitmap();
                            coverArtView.setImageBitmap(bitmap);

                            // Apply to full screen background with heavy blur
                            if (mBlurBackground != null) {
                                mBlurBackground.setImageBitmap(bitmap);
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    mBlurBackground.setRenderEffect(
                                            RenderEffect.createBlurEffect(100f, 100f, Shader.TileMode.CLAMP)
                                    );
                                }
                            }

                            Bitmap paletteBitmap = BitmapHelper.ensureSoftwareBitmap(bitmap);
                            if (paletteBitmap != null && !paletteBitmap.isRecycled()) {
                                Palette.from(paletteBitmap).generate(palette -> {
                                    if (palette != null) {
                                        int color = palette.getVibrantColor(palette.getMutedColor(Color.DKGRAY));
                                        applyGlassyColor(color);
                                    }
                                });
                            }
                        }
                    }

                    @Override
                    public void onError(@Nullable Image errorDrawable) {}
                })
                .memoryCachePolicy(CachePolicy.ENABLED)
               // .error(imageRequest -> CoverartFetcher.getDefaultCover(getApplicationContext()))
                .build();

        // Enqueue all requests
        imageLoader.enqueue(coverRequest);
    }

    public int getStatusBarHeight() {
        int result = 0;
        @SuppressLint({"DiscouragedApi", "InternalInsetResource"}) int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    public void doDeleteMediaItems() {
        List<Track> editItems = getEditItems();
        if (editItems.isEmpty()) return;

        String text = editItems.size() > 1
                ? getString(R.string.remove_track_confirm_multiple)
                : getString(R.string.remove_track_confirm_single);

        final BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this); // Or pass 'context'
        View sheetView = LayoutInflater.from(this).inflate(R.layout.view_action_trash_bottom_sheet_dialog, null);
        bottomSheetDialog.setContentView(sheetView);

        View btnClose = sheetView.findViewById(R.id.btn_close_trash_sheet);
        Button moveToTrashButton = sheetView.findViewById(R.id.button_move_to_trash);
        Button btnCancel = sheetView.findViewById(R.id.button_cancel_trash);
        TextView title = sheetView.findViewById(R.id.bottom_sheet_title);

        title.setText(text);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> bottomSheetDialog.dismiss());
        }
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> bottomSheetDialog.dismiss());
        }

        moveToTrashButton.setOnClickListener(v -> {
            startProgressBar();
            operationTask.deleteFiles(getApplicationContext(), getEditItems(), new FileOperationTask.ProgressCallback() {
                @Override
                public void onProgress(Track tag, int progress, String status) {
                    Log.d(TAG, "Removing: " + tag.getSimpleName() + " -> " + status);
                    updateProgressBar(status);
                }

                @Override
                public void onComplete() {
                    stopProgressBar();
                    setSaved(true);
                    finish(); // back to prev activity
                }
            });
            bottomSheetDialog.dismiss();
        });

        bottomSheetDialog.show();
    }

    public void doMoveMediaItems() {
        startProgressBar();

        operationTask.moveFiles(getApplicationContext(), getEditItems(), new FileOperationTask.ProgressCallback() {
            boolean closeScreen = false;
            @Override
            public void onProgress(Track tag, int progress, String status) {
                if(playbackService != null && tag.equals(playbackService.getNowPlayingSong())) {
                    closeScreen = true;
                }
                updateProgressBar(status + ": " + tag.getSimpleName());
            }

            @Override
            public void onComplete() {
                operationTask.measureDR(getApplicationContext(), getEditItems(), new FileOperationTask.ProgressCallback() {
                    @Override
                    public void onProgress(Track tag, int progress, String status) {
                        Log.d(TAG, "Mastering analysis: " + tag.getSimpleName() + " -> " + status);
                        updateProgressBar(status + ": " + tag.getSimpleName());
                    }

                    @Override
                    public void onComplete() {
                        if(closeScreen) {
                            stopProgressBar();
                            setSaved(true);
                            finish(); // back to prev activity
                        }else {
                            stopProgressBar();
                            setSaved(true);
                            viewModel.refreshDisplayTag();
                        }
                    }
                });
            }
        });
    }

    public TagsViewModel getViewModel() {
        return viewModel;
    }

    public List<Track> getEditItems() {
        List<Track> items = viewModel != null ? viewModel.editItems.getValue() : null;
        return items != null ? items : java.util.Collections.emptyList();
    }

    public Track getDisplayTag() {
        return viewModel.displayTag.getValue();
    }

    public void refreshDisplayTag() {
        viewModel.refreshDisplayTag();
    }

    public void redisplayTag() {
        viewModel.redisplayTag(getEditItems());
    }

    public void rebuildDisplayTag(List<Track> items) {
        viewModel.redisplayTag(items);
    }

    private class BackPressedCallback extends OnBackPressedCallback {
        public BackPressedCallback(boolean enabled) {
            super(enabled);
        }

        @Override
        public void handleOnBackPressed() {
            // lose focus all dropdown
            View currentFocus = getCurrentFocus();
            if (currentFocus != null) {
                // Clear focus from the EditText
                currentFocus.clearFocus();
            }

            boolean hasUnsavedEdits = isDirty;
            // Always check the editor fragment for modifications, regardless of which tab is active.
            // Previously this only checked activeFragment, which missed edits when on the Tech Info tab.
            if (!hasUnsavedEdits) {
                for (Fragment f : getSupportFragmentManager().getFragments()) {
                    if (f instanceof TagsEditorFragment && f.isAdded()) {
                        hasUnsavedEdits = ((TagsEditorFragment) f).isModified();
                        break;
                    }
                }
            }

            if (hasUnsavedEdits) {
                new MaterialAlertDialogBuilder(TagsActivity.this)
                        .setTitle("Discard changes?")
                        .setMessage("You have unsaved edits. Discard them?")
                        .setPositiveButton("Discard", (dialog, which) -> finish())
                        .setNegativeButton("Cancel", null)
                        .show();
            } else {
                finish();
            }
        }
    }

    class OffSetChangeListener implements AppBarLayout.OnOffsetChangedListener {
        double prevScrollOffset = -1;
        // Track state to avoid redundant updates
        private boolean wasFullyExpanded = true;
        private boolean wasFullyCollapsed = false;

        @Override
        public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
            double vScrollOffset = Math.abs(verticalOffset);
            // Only continue if there's an actual change
            if(vScrollOffset == prevScrollOffset) return;
            int totalRange = appBarLayout.getTotalScrollRange();
            if (totalRange <= 0) return;
            double scrollRatio = (double) vScrollOffset / totalRange;

            // Scale cover art
            double scale = (1 - (scrollRatio * 0.2));
            coverArtView.setScaleX((float) scale);
            coverArtView.setScaleY((float) scale);

            // Fully expanded state
            boolean isFullyExpanded = verticalOffset == 0;
            if (isFullyExpanded && !wasFullyExpanded) {
                // State change: fully EXPANDED
                wasFullyExpanded = true;
                wasFullyCollapsed = false;
                previewState = true;
                if (currentEditMode == 0) {
                    setupActionButtons(0);
                }
                if (tabLayout != null && currentEditMode == 0) {
                    tabLayout.setVisibility(View.GONE);
                }
                viewModel.refreshDisplayTag();
            }
            // Fully collapsed state
            else if (Math.abs(verticalOffset) == appBarLayout.getTotalScrollRange() && !wasFullyCollapsed) {
                // State change: fully COLLAPSED
                wasFullyCollapsed = true;
                wasFullyExpanded = false;
                previewState = false;
                if (tabLayout != null) {
                    tabLayout.setVisibility(View.VISIBLE);
                }
                setupActionButtons(1);
            } else if (!isFullyExpanded && tabLayout != null && tabLayout.getVisibility() != View.VISIBLE) {
                tabLayout.setVisibility(View.VISIBLE);
            }
        }
    }

    public void setSaved(boolean saved) {
        this.isSaved = saved;
        if (saved) {
            this.isDirty = false;
        }
    }

    @Override
    public void finish() {
        if (isSaved && !resultAlreadySet) {
            setResult(AppCompatActivity.RESULT_OK);
        }
        super.finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // Handle the back button click with proper confirmation if dirty
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Starts the progress bar with animation
     */
    public void startProgressBar() {
        runOnUiThread(() -> {
            try {
                if (progressDialog != null) {
                    progressDialog.dismiss();
                }

                AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this, R.style.AlertDialogTheme);
                View v = getLayoutInflater().inflate(R.layout.animated_progress_dialog_layout, null);

                dialogBuilder.setView(v);
                dialogBuilder.setCancelable(true);
                progressDialog = dialogBuilder.create();
                progressDialog.setCanceledOnTouchOutside(true);

                Window window = progressDialog.getWindow();
                if (window != null) {
                    window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                    window.setWindowAnimations(R.style.DialogAnimation);

                    // Add these lines to dim the background
                    WindowManager.LayoutParams layoutParams = window.getAttributes();
                    layoutParams.dimAmount = 0.8f; // Adjust this value between 0.0f (no dim) and 1.0f (fully black)
                    window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                    window.setAttributes(layoutParams);
                }

                // Show the dialog with animation
                progressDialog.show();
            } catch (Exception ex) {
                Log.e(TAG, "startProgressBar", ex);
            }
        });
    }

    /**
     * Updates the progress bar label text with throttling to prevent UI overload.
     * @param label Text to display in the progress bar dialog
     */
    public void updateProgressBar(final String label) {
        long now = System.currentTimeMillis();
        long lastUpdate = lastProgressUpdate.get();
        long PROGRESS_UPDATE_THROTTLE_MS = 100;
        if (now - lastUpdate < PROGRESS_UPDATE_THROTTLE_MS) {
            return;
        }
        if (!lastProgressUpdate.compareAndSet(lastUpdate, now)) {
            return;
        }
        runOnUiThread(() -> {
            if (progressDialog != null && progressDialog.isShowing()) {
                View dialogView = progressDialog.getWindow() != null
                        ? progressDialog.getWindow().getDecorView() : null;
                if (dialogView != null) {
                    TextView labelView = dialogView.findViewById(R.id.progress_label);
                    if (labelView != null) {
                        labelView.setText(label);
                    }
                }
            }
        });
    }

    /**
     * Stops the progress bar with exit animation
     */
    public void stopProgressBar() {
        if(progressDialog != null) {
            runOnUiThread(() -> {
                try {
                        progressDialog.dismiss();
                        progressDialog = null;
                } catch (Exception ignored) {}
            });
        }
    }
}