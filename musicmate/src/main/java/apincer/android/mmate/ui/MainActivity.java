package apincer.android.mmate.ui;

import static apincer.android.mmate.utils.StringUtils.isEmpty;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.transition.Slide;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.ViewPropertyAnimatorListenerAdapter;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.selection.SelectionPredicates;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.anggrayudi.storage.file.DocumentFileCompat;
import com.anggrayudi.storage.file.StorageId;
import com.balsikandar.crashreporter.ui.CrashReporterActivity;
import com.google.android.material.bottomappbar.BottomAppBar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener;
import com.heaven7.android.trapezoid.TrapezoidPartsView;
import com.mikepenz.aboutlibraries.LibsBuilder;
import com.scwang.smart.refresh.layout.api.RefreshLayout;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import apincer.android.mmate.Constants;
import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.Preferences;
import apincer.android.mmate.R;
import apincer.android.mmate.broadcast.AudioTagEditEvent;
import apincer.android.mmate.broadcast.AudioTagEditResultEvent;
import apincer.android.mmate.broadcast.AudioTagPlayingEvent;
import apincer.android.mmate.fs.FileSystem;
import apincer.android.mmate.fs.MusicCoverArtProvider;
import apincer.android.mmate.nas.NASServerService;
import apincer.android.mmate.nas.SyncMusic;
import apincer.android.mmate.repository.FFMPeg;
import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.repository.MusicTagRepository;
import apincer.android.mmate.repository.SearchCriteria;
import apincer.android.mmate.ui.view.BottomOffsetDecoration;
import apincer.android.mmate.ui.widget.RatioSegmentedProgressBarDrawable;
import apincer.android.mmate.utils.ApplicationUtils;
import apincer.android.mmate.utils.AudioOutputHelper;
import apincer.android.mmate.utils.BitmapHelper;
import apincer.android.mmate.utils.ColorUtils;
import apincer.android.mmate.utils.HostInterface;
import apincer.android.mmate.utils.MusicTagUtils;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.mmate.utils.ToastHelper;
import apincer.android.mmate.utils.UIUtils;
import apincer.android.mmate.work.MusicMateExecutors;
import apincer.android.residemenu.ResideMenu;
import apincer.android.utils.FileUtils;
import cn.iwgang.simplifyspan.SimplifySpanBuild;
import cn.iwgang.simplifyspan.other.SpecialGravity;
import cn.iwgang.simplifyspan.unit.SpecialClickableUnit;
import cn.iwgang.simplifyspan.unit.SpecialLabelUnit;
import cn.iwgang.simplifyspan.unit.SpecialTextUnit;
import coil.Coil;
import coil.ImageLoader;
import coil.request.ImageRequest;
import coil.target.Target;
import me.zhanghai.android.fastscroll.FastScrollerBuilder;
import sakout.mehdi.StateViews.StateView;

/**
 * Created by Administrator on 11/23/17.
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getName();
    private static final int RECYCLEVIEW_ITEM_POSITION_OFFSET=16; //start scrolling from 4 items
    private static final int RECYCLEVIEW_ITEM_OFFSET= 48; // scroll item to offset+1 position on list
    private static final int MENU_ID_QUALITY = 55555555;
    private static final int MENU_ID_QUALITY_PCM = 55550000;
    private static final double MAX_PROGRESS_BLOCK = 10.00;
    private static final double MAX_PROGRESS = 100.00;

    private MusicTag nowPlaying = null;

    ActivityResultLauncher<Intent> editorLauncher;

    FileRepository repos;

    private BottomAppBar bottomAppBar;

    private ResideMenu mResideMenu;

    private OnBackPressedCallback onBackPressedCallback;

    private MusicTagAdapter adapter;
    private SelectionTracker<Long> mTracker;

    List<MusicTag> selections = new ArrayList<>();

    private Snackbar mExitSnackbar;
    //private View mSearchBar;
    private View mHeaderPanel;
    private TrapezoidPartsView mHeaderTPV;
    private SearchView mSearchView;
    //private ImageView mSearchViewSwitch;

    private RefreshLayout refreshLayout;
    private StateView mStateView;
    private View nowPlayingView;
    private View nowPlayingPanel;
    private View nowPlayingIconView;
    private View nowPlayingTitlePanel;
    private ImageView nowPlayingCoverArt;
    private TextView nowPlayingTitle;
    private ImageView nowPlayingType;
    private ImageView nowPlayingPlayer;
    private ImageView nowPlayingOutputDevice;

    // header panel
    TabLayout headerTab;
    TextView headerSubtitle;

    // open tag timer
    private Timer timer;

    private volatile boolean busy;

    private ActionModeCallback actionModeCallback;
    private ActionMode actionMode;

    private boolean onSetup = true;
    private int positionToScroll = -1;

    private void doDeleteMediaItems(List<MusicTag> selections) {
     if(selections.isEmpty()) return;

    View cview = getLayoutInflater().inflate(R.layout.view_action_files, null);

    Map<MusicTag, String> statusList = new HashMap<>();
    ListView itemsView = cview.findViewById(R.id.itemListView);
    TextView titleText = cview.findViewById(R.id.title);
        titleText.setText(R.string.title_removing_music_files);
        itemsView.setAdapter(new BaseAdapter() {
        @Override
        public int getCount() {
            return selections.size();
        }

        @Override
        public Object getItem(int i) {
            return null;
        }

        @Override
        public long getItemId(int i) {
            return 0;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            view = getLayoutInflater().inflate(R.layout.view_action_listview_item, null);
            MusicTag tag = selections.get(i);
            TextView seq = view.findViewById(R.id.seq);
            TextView name = view.findViewById(R.id.name);
            TextView status = view.findViewById(R.id.status);
            seq.setText(String.valueOf(i+1));
            if(statusList.containsKey(tag)) {
                status.setText(statusList.get(tag));
            }else {
                status.setText("-");
            }
            name.setText(FileSystem.getFilename(tag.getPath()));
            return view;
        }
    });

    View btnOK = cview.findViewById(R.id.btn_ok);
    View btnCancel = cview.findViewById(R.id.btn_cancel);

    ProgressBar progressBar = cview.findViewById(R.id.progressBar);

        btnOK.setEnabled(false);

        btnOK.setEnabled(true);

    double block = Math.min(selections.size(), MAX_PROGRESS_BLOCK);
    double sizeInBlock = MAX_PROGRESS/block;
    List<Long> valueList = new ArrayList<>();
        for(int i=0; i< block;i++) {
        valueList.add((long) sizeInBlock);
    }
    final double rate = 100.00/selections.size(); // pcnt per 1 song
    int barColor = getColor(R.color.material_color_green_400);
        progressBar.setProgressDrawable(new RatioSegmentedProgressBarDrawable(barColor, Color.GRAY, valueList, 8f));
        progressBar.setMax((int) MAX_PROGRESS);

    AlertDialog alert = new MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
            .setTitle("")
            .setView(cview)
            .setCancelable(true)
            .create();
        alert.requestWindowFeature(Window.FEATURE_NO_TITLE);
        alert.setCanceledOnTouchOutside(false);
    // make popup round corners
        alert.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        btnOK.setOnClickListener(v -> {
            busy = true;
            progressBar.setProgress(getInitialProgress(selections.size(), rate));
            for(MusicTag tag: selections) {
            MusicMateExecutors.move(() -> {
                try {
                    boolean status = repos.deleteMediaItem(tag);
                    if(status) {
                        AudioTagEditResultEvent message = new AudioTagEditResultEvent(AudioTagEditResultEvent.ACTION_DELETE, status?Constants.STATUS_SUCCESS:Constants.STATUS_FAIL, tag);
                        EventBus.getDefault().postSticky(message);
                        statusList.put(tag, "Deleted");
                    }else {
                        statusList.put(tag, "Fail");
                    }
                    runOnUiThread(() -> {
                        int pct = progressBar.getProgress();
                        progressBar.setProgress((int) (pct + rate));
                        progressBar.invalidate();
                        itemsView.invalidateViews();
                    });
                } catch (Exception e) {
                    Log.e(TAG,"deleteFile",e);
                    statusList.put(tag, "Fail");
                    runOnUiThread(() -> {
                        int pct = progressBar.getProgress();
                        progressBar.setProgress((int) (pct + rate));
                        progressBar.invalidate();
                        itemsView.invalidateViews();
                    });
                }
            });
        }
        btnOK.setEnabled(false);
        btnOK.setVisibility(View.GONE);
    });
        btnCancel.setOnClickListener(v -> {alert.dismiss();busy=false;});
        alert.show();
}

    private void doMoveMediaItems(List<MusicTag> selections) {
        if(selections.isEmpty()) return;

        View cview = getLayoutInflater().inflate(R.layout.view_action_files, null);

        Map<MusicTag, String> statusList = new HashMap<>();
        ListView itemsView = cview.findViewById(R.id.itemListView);
        TextView titleText = cview.findViewById(R.id.title);
        titleText.setText("Import to Music directory");
        itemsView.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return selections.size();
            }

            @Override
            public Object getItem(int i) {
                return null;
            }

            @Override
            public long getItemId(int i) {
                return 0;
            }

            @Override
            public View getView(int i, View view, ViewGroup viewGroup) {
                view = getLayoutInflater().inflate(R.layout.view_action_listview_item, null);
                MusicTag tag = selections.get(i);
                TextView seq = view.findViewById(R.id.seq);
                TextView name = view.findViewById(R.id.name);
                TextView status = view.findViewById(R.id.status);
                seq.setText(String.valueOf(i+1));
                if(statusList.containsKey(tag)) {
                    status.setText(statusList.get(tag));
                }else {
                    status.setText("-");
                }
                name.setText(FileSystem.getFilename(tag.getPath()));
                return view;
            }
        });

        View btnOK = cview.findViewById(R.id.btn_ok);
        View btnCancel = cview.findViewById(R.id.btn_cancel);

        ProgressBar progressBar = cview.findViewById(R.id.progressBar);

       // btnOK.setEnabled(false);

        btnOK.setEnabled(true);

        double block = Math.min(selections.size(), MAX_PROGRESS_BLOCK);
        double sizeInBlock = MAX_PROGRESS/block;
        List<Long> valueList = new ArrayList<>();
        for(int i=0; i< block;i++) {
            valueList.add((long) sizeInBlock);
        }
        final double rate = 100.00/selections.size(); // pcnt per 1 song
        int barColor = getColor(R.color.material_color_green_400);
        progressBar.setProgressDrawable(new RatioSegmentedProgressBarDrawable(barColor, Color.GRAY, valueList, 8f));
        progressBar.setMax((int) MAX_PROGRESS);

        AlertDialog alert = new MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
                .setTitle("")
                .setView(cview)
                .setCancelable(true)
                .create();
        alert.requestWindowFeature(Window.FEATURE_NO_TITLE);
        alert.setCanceledOnTouchOutside(false);
        // make popup round corners
        alert.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        btnOK.setOnClickListener(v -> {
            busy = true;
            progressBar.setProgress(getInitialProgress(selections.size(), rate));
            for(MusicTag tag: selections) {
                MusicMateExecutors.move(() -> {
                    try {
                        statusList.put(tag, "Moving");
                        runOnUiThread(() -> {
                            int pct = progressBar.getProgress();
                            progressBar.setProgress((int) (pct + rate));
                            progressBar.invalidate();
                            itemsView.invalidateViews();
                        });
                        boolean status = repos.importAudioFile(tag);
                        if(status) {
                            AudioTagEditResultEvent message = new AudioTagEditResultEvent(AudioTagEditResultEvent.ACTION_MOVE, status ? Constants.STATUS_SUCCESS : Constants.STATUS_FAIL, tag);
                            EventBus.getDefault().postSticky(message);
                            statusList.put(tag, "Done");
                        }else {
                            statusList.put(tag, "Fail");
                        }
                        runOnUiThread(() -> {
                            int pct = progressBar.getProgress();
                            progressBar.setProgress((int) (pct + rate));
                            progressBar.invalidate();
                            itemsView.invalidateViews();
                        });
                    } catch (Exception e) {
                        Log.e(TAG,"importFile",e);
                        try {
                            runOnUiThread(() -> {
                                statusList.put(tag, "Fail");
                                int pct = progressBar.getProgress();
                                progressBar.setProgress((int) (pct + rate));
                                progressBar.invalidate();
                                itemsView.invalidateViews();
                            });
                        }catch (Exception ex) {}
                    }
                });
            }
            btnOK.setEnabled(false);
            btnOK.setVisibility(View.GONE);
        });
        btnCancel.setOnClickListener(v -> {alert.dismiss();busy=false;});
        alert.show();
    }
	
	private void doShowNowPlayingSongFAB(final MusicTag song) {
        if (song == null) {
            doHideNowPlayingSongFAB();
            return;
        }
        if(nowPlayingView == null) {
            setUpNowPlayingView();
        }
        nowPlaying = song;

        ImageLoader imageLoader = Coil.imageLoader(getApplicationContext());
        ImageRequest request = new ImageRequest.Builder(getApplicationContext())
                //.data(MusicTagUtils.getCoverArt(this, song))
                .data(MusicCoverArtProvider.getUriForMusicTag(song))
                .allowHardware(false)
                //.placeholder(R.drawable.progress)
              //  .error(R.drawable.ic_broken_image_black_24dp)
               // .error(R.drawable.no_image0)
                .target(new Target() {
                    @Override
                    public void onStart(@Nullable Drawable drawable) {
                        nowPlayingCoverArt.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.progress));
                    }

                    @Override
                    public void onError(@Nullable Drawable drawable) {
                        nowPlayingCoverArt.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.no_image0));
                    }

                    @Override
                    public void onSuccess(@NonNull Drawable drawable) {
                        try {
                            nowPlayingCoverArt.setImageDrawable(drawable);
                            MusicMateExecutors.main(() -> {
                                Bitmap bmp = BitmapHelper.drawableToBitmap(drawable);
                                Palette palette = Palette.from(bmp).generate();
                                int mutedColor = palette.getMutedColor(ContextCompat.getColor(getApplicationContext(),R.color.transparent));
                                int dominantColor = palette.getDominantColor(ContextCompat.getColor(getApplicationContext(),R.color.transparent));
                                runOnUiThread(() -> {
                                    try {
                                        nowPlayingPanel.setBackgroundTintList(ColorStateList.valueOf(mutedColor));
                                        nowPlayingTitlePanel.setBackgroundTintList(ColorStateList.valueOf(ColorUtils.TranslateDark(mutedColor, 80)));
                                        nowPlayingIconView.setBackgroundTintList(ColorStateList.valueOf(ColorUtils.TranslateDark(dominantColor, 80)));
                                    }catch (Exception ex){}
                                });
                            });
                        }catch (Exception ex) {
                            Log.e(TAG, "doShowNowPlayingSongFAB", ex);
                        }
                    }
                })
                .build();
        imageLoader.enqueue(request);
        nowPlayingTitle.setText(song.getTitle());

        imageLoader = Coil.imageLoader(getApplicationContext());
        request = new ImageRequest.Builder(getApplicationContext())
                .data(MusicTagUtils.getEncResolutionIcon(getApplicationContext(), song))
                .crossfade(false)
                .target(nowPlayingType)
                .build();
        imageLoader.enqueue(request);
        if(MusixMateApp.getPlayerInfo()!=null) {
            nowPlayingPlayer.setImageDrawable(MusixMateApp.getPlayerInfo().getPlayerIconDrawable());
        }

        MusicMateExecutors.main(() -> {
            AudioOutputHelper.getOutputDevice(getApplicationContext(), device -> nowPlayingOutputDevice.setImageBitmap(AudioOutputHelper.getOutputDeviceIcon(getApplicationContext(),device)));
            runOnUiThread(() -> ViewCompat.animate(nowPlayingView)
                    .scaleX(1f).scaleY(1f)
                    .alpha(1f).setDuration(250)
                    .setStartDelay(10L)
                    .setListener(new ViewPropertyAnimatorListenerAdapter() {
                        @SuppressLint("NotifyDataSetChanged")
                        @Override
                        public void onAnimationStart(@NonNull View view) {
                            view.setVisibility(View.VISIBLE);
                            adapter.notifyMusicTagChanged(song); // .notifyModelChanged(song);
                        }
                    })
                    .start());
        });
    }
	
	private void doHideNowPlayingSongFAB() {
        nowPlaying = null;
        runOnUiThread(() -> ViewCompat.animate(nowPlayingView)
                .scaleX(0f).scaleY(0f)
                .alpha(0f).setDuration(100)
                .setStartDelay(10L)
                .setListener(new ViewPropertyAnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(@NonNull View view) {
                        view.setVisibility(View.GONE);
                    }
                })
                .start());
    }

    private void doStartRefresh(SearchCriteria criteria) {
        if(criteria!= null) {
            doStartRefresh(criteria.getType(), criteria.getKeyword());
        }else {
            refreshLayout.autoRefresh();
        }
    }

    private void doStartRefresh(SearchCriteria.TYPE type, String keyword) {
        adapter.setType(type);
        adapter.setKeyword(keyword);
        refreshLayout.autoRefresh();
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressed() {
        if(mResideMenu.isOpened()) {
            mResideMenu.closeMenu();
            return;
        }

        if(actionMode !=null) {
            actionMode.finish();
            return;
        }

       /* not work as expected
       if(mSearchView.isShown()) {
            mSearchView.setIconified(true);
            adapter.resetSearchString();
            refreshLayout.autoRefresh();
            return;
        } */

        if(adapter!=null && adapter.isSearchMode()) {
            doHideSearch();
            refreshLayout.autoRefresh();
            return;
        }

        if(adapter!=null && adapter.hasFilter()) {
            adapter.resetFilter();
            refreshLayout.autoRefresh();
            return;
        }

        if (!mExitSnackbar.isShown()) {
            mExitSnackbar.show();
        } else {
            mExitSnackbar.dismiss();
            finishAndRemoveTask();
            System.exit(0);
        }
    }

    protected RecyclerView mRecyclerView;

    private void initActivityTransitions() {
            Slide transition = new Slide();
            transition.excludeTarget(android.R.id.statusBarBackground, true);
            getWindow().setEnterTransition(transition);
            getWindow().setReturnTransition(transition);
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // create -> restore state --> resume --> running --> pause --> save state --> destroy
        // if savedInstanceState == null, fresh start
        if(Preferences.isOnNightModeOnly(getApplicationContext())) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM); //must place before super.onCreate();
        }
        super.onCreate(savedInstanceState);
        onBackPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Your business logic to handle the back pressed event
                Log.d(TAG, "onBackPressedCallback: handleOnBackPressed");
                onBackPressed();
            }
        };

        getOnBackPressedDispatcher().addCallback(this, onBackPressedCallback);

        repos = FileRepository.newInstance(getApplicationContext());
        initActivityTransitions();
        setContentView(R.layout.activity_main);
        setUpEditorLauncher();
        setUpPermissions();
        setUpHeaderPanel();
        setUpNowPlayingView();
        setUpBottomAppBar();
        setUpRecycleView();
        setUpSwipeToRefresh();
        setUpResideMenus();

        loadDataSets(savedInstanceState);
        mExitSnackbar = Snackbar.make(this.mRecyclerView, R.string.alert_back_to_exit, Snackbar.LENGTH_LONG);
        View snackBarView = mExitSnackbar.getView();
        snackBarView.setBackgroundColor(getColor(R.color.warningColor));
    }

    private void setUpEditorLauncher() {
        editorLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            // support filter by artist, albnum, path
            if (result.getData() != null) {
                // if retrun criteria, use it otherwise provide null
                SearchCriteria criteria = ApplicationUtils.getSearchCriteria(result.getData());
                adapter.loadDataSets(criteria);
            }
        });
    }

    private void setUpHeaderPanel() {
        mHeaderPanel = findViewById(R.id.header_panel);

        headerTab = findViewById(R.id.header_tab);
        headerSubtitle = findViewById(R.id.header_subtitle);
        headerTab.addOnTabSelectedListener(new OnTabSelectedListener(){
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if(!onSetup) {
                    adapter.resetFilter();
                    adapter.setKeyword(tab.getText().toString());
                    refreshLayout.autoRefresh();
                }
                TextView tv = tab.getCustomView().findViewById(R.id.tabTitle);
                tv.setTextColor(Color.WHITE);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                TextView tv = tab.getCustomView().findViewById(R.id.tabTitle);
                tv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.grey400));
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

    private void loadDataSets(Bundle startIntent) {
        SearchCriteria criteria;
        if(startIntent != null) {
            criteria = startIntent.getParcelable("main_screen_type");
        }else {
            criteria = new SearchCriteria(SearchCriteria.TYPE.MY_SONGS);
        }
        doStartRefresh(criteria);
    }

    /**
     * set up Bottom Bar
     */
    private void setUpBottomAppBar() {
        //find id
        bottomAppBar = findViewById(R.id.bottom_app_bar);

        //set bottom bar to Action bar as it is similar like Toolbar
        setSupportActionBar(bottomAppBar);
        ImageView leftMenu = bottomAppBar.findViewById(R.id.navigation_collections);
        UIUtils.getTintedDrawable(leftMenu.getDrawable(), Color.WHITE);
        ImageView rightMenu = bottomAppBar.findViewById(R.id.navigation_settings);
        mSearchView = bottomAppBar.findViewById(R.id.searchView);

        UIUtils.getTintedDrawable(rightMenu.getDrawable(), Color.WHITE);

       mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
           @Override
           public boolean onQueryTextSubmit(String query) {
               adapter.setSearchString(query);
               refreshLayout.autoRefresh();
               return false;
           }

           @Override
           public boolean onQueryTextChange(String newText) {
               if(isEmpty(newText) || newText.length() >=3) {
                   adapter.setSearchString(newText);
                   refreshLayout.autoRefresh();
               }
               return false;
           }
       });

       mSearchView.setOnSearchClickListener(v -> {
           adapter.setSearchString(String.valueOf(mSearchView.getQuery()));
           refreshLayout.autoRefresh();
       });

        mSearchView.setOnCloseListener(() -> {
            doHideSearch();
            refreshLayout.autoRefresh();
            return false;
        });

        leftMenu.setOnClickListener(v -> doShowLeftMenus());
        rightMenu.setOnClickListener(v -> doShowRightMenus());

    }

    private void setUpNowPlayingView() {
        // Now Playing
        nowPlayingView = findViewById(R.id.now_playing_panel);
        nowPlayingPanel = findViewById(R.id.now_playing_panel_inner);
        nowPlayingIconView = findViewById(R.id.now_playing_icon_panel);
        nowPlayingTitlePanel = findViewById(R.id.now_playing_title_panel);
        nowPlayingTitle = findViewById(R.id.now_playing_title);
        nowPlayingType = findViewById(R.id.now_playing_file_type);
        nowPlayingPlayer = findViewById(R.id.now_playing_player);
        nowPlayingOutputDevice = findViewById(R.id.now_playing_device);
        nowPlayingCoverArt = findViewById(R.id.now_playing_coverart);
        nowPlayingView.setOnClickListener(view1 -> scrollToListening());
        nowPlayingView.setOnLongClickListener(view1 -> doPlayNextSong());
        nowPlayingView.setVisibility(View.GONE);
    }

    private void doHideSearch() {
        adapter.setSearchString("");
        refreshLayout.autoRefresh();
    }
	
	private boolean doPlayNextSong() {
        MusixMateApp.playNextSong(getApplicationContext());
        return true;
	}

    private void doShowLeftMenus() {
        if(Preferences.isShowStorageSpace(getApplicationContext())) {
            @SuppressLint("InflateParams") View storageView = getLayoutInflater().inflate(R.layout.view_header_left_menu, null);
            LinearLayout panel = storageView.findViewById(R.id.storage_bar);
            UIUtils.buildStoragesStatus(getApplication(),panel);
            mResideMenu.setLeftHeader(storageView);
        }
        mResideMenu.openMenu(ResideMenu.DIRECTION_LEFT);
    }

    private void doShowRightMenus() {
        mResideMenu.openMenu(ResideMenu.DIRECTION_RIGHT);
    }

    private void setUpResideMenus() {
        // attach to current activity;
        mResideMenu = new ResideMenu(this);
        mResideMenu.setBackground(R.drawable.bg);
        mResideMenu.attachToActivity(this);
        mResideMenu.setScaleValue(0.54f);
        mResideMenu.setSwipeDirectionDisable(ResideMenu.DIRECTION_LEFT);
        mResideMenu.setSwipeDirectionDisable(ResideMenu.DIRECTION_RIGHT);
        mResideMenu.setOnMenuItemClickListener(item -> {
            onOptionsItemSelected(item);
            mResideMenu.closeMenu();
        });

        // create left menus
        mResideMenu.setMenuRes(R.menu.menu_music_mate,ResideMenu.DIRECTION_RIGHT);
        // create right menus

        mResideMenu.setMenuRes(R.menu.menu_music_collection, ResideMenu.DIRECTION_LEFT);
        mResideMenu.addMenuItem(MENU_ID_QUALITY, R.drawable.ic_format_dsd_white, Constants.AUDIO_SQ_DSD, ResideMenu.DIRECTION_LEFT);
        mResideMenu.addMenuItem(MENU_ID_QUALITY_PCM, UIUtils.getTintedDrawable(getApplicationContext(), R.drawable.ic_sound_wave, Color.WHITE), Constants.AUDIO_SQ_PCM, ResideMenu.DIRECTION_LEFT);
    }

    private void setUpPermissions() {
        if (!Environment.isExternalStorageManager()) {
            //todo when permission is granted      // do not have read/write storage permission
            Intent myIntent = new Intent(MainActivity.this, PermissionActivity.class);
            // You can do the assignment inside onAttach or onCreate, i.e, before the activity is displayed
            ActivityResultLauncher<Intent> permissionResultLauncher = registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> loadDataSets(null));
            permissionResultLauncher.launch(myIntent);
        }
    }

    private void scrollToListening() {
        if(nowPlaying ==null) return;
        positionToScroll = adapter.getMusicTagPosition(nowPlaying);
        scrollToPosition(positionToScroll,true);
    }

    private void scrollToPosition(int position, boolean offset) {
        if(position != RecyclerView.NO_POSITION) {
            if(offset) {
                int positionWithOffset = position - RECYCLEVIEW_ITEM_POSITION_OFFSET;
                if (positionWithOffset < 0) {
                    positionWithOffset = 0;
                }
                mRecyclerView.scrollToPosition(positionWithOffset);
                LinearLayoutManager layoutManager = (LinearLayoutManager) mRecyclerView.getLayoutManager();
                Objects.requireNonNull(layoutManager).scrollToPositionWithOffset(position, RECYCLEVIEW_ITEM_OFFSET);
            }else {
                mRecyclerView.scrollToPosition(position);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if(mResideMenu.isOpened()) {
            mResideMenu.closeMenu();
        }

        doShowNowPlayingSongFAB(MusixMateApp.getPlayingSong());
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Subscribe(threadMode = ThreadMode.MAIN,sticky = true)
    public void onMessageEvent(AudioTagPlayingEvent event) {
        MusicMateExecutors.main(() -> {
            MusicTag tag = event.getPlayingSong();
            onPlaying(tag);
        });
    }

    @Subscribe(threadMode = ThreadMode.MAIN,sticky = true)
    public void onMessageEvent(AudioTagEditResultEvent event) {
        // call from EventBus
        try {
            MusicMateExecutors.main(() -> {
                if(event.getItem()!=null) {
                    int position = adapter.getMusicTagPosition(event.getItem());
                    if(AudioTagEditResultEvent.ACTION_DELETE.equals(event.getAction())) {
                        adapter.removeMusicTag(position, event.getItem());
                    }else if(adapter.isMatchFilter(event.getItem())) {
                        adapter.loadDataSets();
                    }
                }
            });
        }catch (Exception e) {
            Log.e(TAG,"onMessageEvent",e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putParcelable("main_screen_type", adapter.getCriteria());
    }

    @Override
    public void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    public void onStop() {
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    //Inflate menu to bottom bar
    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        return super.onCreateOptionsMenu(menu);
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }else if(item.getItemId() == R.id.menu_all_music) {
            doHideSearch();
            doStartRefresh(SearchCriteria.TYPE.MY_SONGS, null);
            return true;
        } else if(item.getItemId() == MENU_ID_QUALITY_PCM) {
            doHideSearch();
            doStartRefresh(SearchCriteria.TYPE.AUDIO_SQ, Constants.TITLE_HIGH_QUALITY);
            return true;
        } else if(item.getItemId() == MENU_ID_QUALITY) {
            doHideSearch();
            doStartRefresh(SearchCriteria.TYPE.AUDIO_SQ, (String)item.getTitle());
            return true;

        }else if(item.getItemId() == R.id.menu_groupings) {
            doHideSearch();
            doStartRefresh(SearchCriteria.TYPE.GROUPING, MusicTagRepository.getActualGroupingList(getApplicationContext()).get(0));
            return true;
        }else if(item.getItemId() == R.id.menu_tag_genre) {
            doHideSearch();
            doStartRefresh(SearchCriteria.TYPE.GENRE, MusicTagRepository.getActualGenreList(getApplicationContext()).get(0));
            return true;
        }else if(item.getItemId() == R.id.menu_settings) {
            Intent myIntent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(myIntent);
            return true;
        } else if(item.getItemId() == R.id.menu_sd_permission) {
            //setUpPermissionSAF();
            Intent myIntent = new Intent(MainActivity.this, PermissionActivity.class);
            startActivity(myIntent);
            return true;
        } else if(item.getItemId() == R.id.menu_notification_access) {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            startActivity(intent);
            return true;
        }else if(item.getItemId() == R.id.menu_about_libraries) {
            doShowAboutLibraries();
            return true;
        }else if(item.getItemId() == R.id.menu_about_music_mate) {
            doShowAboutApp();
            return true;
        }else if(item.getItemId() == R.id.navigation_settings) {
            doShowRightMenus();
            return true;
        }else if(item.getItemId() == R.id.menu_about_crash) {
            Intent myIntent = new Intent(MainActivity.this, CrashReporterActivity.class);
            startActivity(myIntent);
            return true;
        }else if(item.getItemId() == R.id.menu_media_server) {
            doShowMediaServerControl();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void doShowMediaServerControl() {
        View cview = getLayoutInflater().inflate(R.layout.view_action_media_server, null);
        TextView ip = cview.findViewById(R.id.server_ip_address);
        TextView status = cview.findViewById(R.id.server_status);
        ip.setText(HostInterface.getIPv4Address());
        AlertDialog alert = new MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
                .setTitle("")
                .setView(cview)
                .setCancelable(true)
                .create();
        alert.requestWindowFeature(Window.FEATURE_NO_TITLE);
        alert.setCanceledOnTouchOutside(true);
        Button startButton = cview.findViewById(R.id.manageServer);
        if(MusixMateApp.getInstance().isNASRunning()) {
            status.setText("Status: STARTED");
            startButton.setText("Stop Server");
        }else {
            status.setText("Status: STOPED");
            startButton.setText("Start Server");
        }
        startButton.setOnClickListener(view -> {
            if(MusixMateApp.getInstance().isNASRunning()) {
                stopService(new Intent(getApplicationContext(),NASServerService.class));
            }else {
                startForegroundService(new Intent(getApplicationContext(),NASServerService.class));
            }
            if(MusixMateApp.getInstance().isNASRunning()) {
                status.setText("Status: STARTED");
                startButton.setText("Stop Server");
            }else {
                status.setText("Status: STOPED");
                startButton.setText("Start Server");
            }
        });

        // make popup round corners
        alert.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        alert.show();
    }

    /*
    private void doShowEditActivity(MusicTag mediaItem) {
        if(FileRepository.isMediaFileExist(mediaItem)) {
            ArrayList<MusicTag> tagList = new ArrayList<>();
            tagList.add(mediaItem);
            AudioTagEditEvent message = new AudioTagEditEvent("edit", adapter.getCriteria(), tagList);
            EventBus.getDefault().postSticky(message);
            Intent myIntent = new Intent(MainActivity.this, TagsActivity.class);
            editorLauncher.launch(myIntent);
        }
    }*/

    private void doShowEditActivity(List<MusicTag> selections) {
        ArrayList<MusicTag> tagList = new ArrayList<>();
        for(MusicTag tag: selections) {
            if(FileRepository.isMediaFileExist(tag)) {
                tagList.add(tag);
            }else {
                new MaterialAlertDialogBuilder(MainActivity.this, R.style.AlertDialogTheme)
                    .setTitle("Problem")
                    .setMessage(getString(R.string.alert_invalid_media_file, tag.getTitle()))
                    .setPositiveButton("GOT IT", (dialogInterface, i) -> {
                        repos.deleteMediaItem(tag);
                        adapter.loadDataSets();
                        dialogInterface.dismiss();
                    })
                    .show();
                    }
        }

        if(!tagList.isEmpty()) {
            Intent myIntent = new Intent(MainActivity.this, TagsActivity.class);

            AudioTagEditEvent message = new AudioTagEditEvent("edit", adapter.getCriteria(), tagList);
            EventBus.getDefault().postSticky(message);
            editorLauncher.launch(myIntent);
        }
    }


    private void doSendToStreamingServer(List<MusicTag> selections) {
        if(selections.isEmpty()) return;

        View cview = getLayoutInflater().inflate(R.layout.view_action_transfer_files, null);
        Map<MusicTag, String> doneList = new HashMap<>();
        ListView itemsView = cview.findViewById(R.id.itemListView);
        EditText targetURL = cview.findViewById(R.id.targetURL);
        EditText targetIP = cview.findViewById(R.id.targetIP);
        targetURL.setText("Sync to Streaming Server");
        targetIP.setVisibility(View.GONE);

        itemsView.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return selections.size();
            }

            @Override
            public Object getItem(int i) {
                return null;
            }

            @Override
            public long getItemId(int i) {
                return 0;
            }

            @Override
            public View getView(int i, View view, ViewGroup viewGroup) {
                view = getLayoutInflater().inflate(R.layout.view_action_listview_item, null);
                MusicTag tag = selections.get(i);
                TextView seq = view.findViewById(R.id.seq);
                TextView name = view.findViewById(R.id.name);
                TextView status = view.findViewById(R.id.status);
                seq.setText(String.valueOf(i+1));
                if(doneList.containsKey(tag)) {
                    status.setText(doneList.get(tag));
                }else {
                    status.setText("-");
                }
                name.setText(FileSystem.getFilename(tag.getPath()));
                return view;
            }
        });

        View btnOK = cview.findViewById(R.id.btn_ok);
        View btnCancel = cview.findViewById(R.id.btn_cancel);

        ProgressBar progressBar = cview.findViewById(R.id.progressBar);

        btnOK.setEnabled(true);

        double block = Math.min(selections.size(), MAX_PROGRESS_BLOCK);
        double sizeInBlock = MAX_PROGRESS/block;
        List<Long> valueList = new ArrayList<>();
        for(int i=0; i< block;i++) {
            valueList.add((long) sizeInBlock);
        }
        final float rate = 100/selections.size(); // pcnt per 1 song
        int barColor = getColor(R.color.material_color_green_400);
        progressBar.setProgressDrawable(new RatioSegmentedProgressBarDrawable(barColor, Color.GRAY, valueList, 8f));
        progressBar.setMax((int) MAX_PROGRESS);

        AlertDialog alert = new MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
                .setTitle("")
                .setView(cview)
                .setCancelable(true)
                .create();
        alert.requestWindowFeature(Window.FEATURE_NO_TITLE);
        alert.setCanceledOnTouchOutside(false);
        // make popup round corners
        alert.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        btnOK.setOnClickListener(v -> {
            busy = true;
            progressBar.setProgress(getInitialProgress(selections.size(), rate));
            MusicMateExecutors.move(() -> {
                SyncMusic streamer = new SyncMusic();
                for(MusicTag tag: selections) {
                    try {
                        doneList.put(tag, "Sending");
                        runOnUiThread(() -> itemsView.invalidateViews());
                        streamer.sync(getApplicationContext(), tag);
                        doneList.put(tag, "Done");
                        runOnUiThread(() -> {
                            int pct = progressBar.getProgress();
                            progressBar.setProgress((int) (pct + rate));
                            itemsView.invalidateViews();
                            progressBar.invalidate();
                        });
                    } catch (Exception ex) {
                        try {
                            doneList.put(tag, "Fail");
                            runOnUiThread(() -> {
                                int pct = progressBar.getProgress();
                                progressBar.setProgress((int) (pct + rate));
                                itemsView.invalidateViews();
                                progressBar.invalidate();
                            });
                        } catch (Exception e) {
                        }
                    }
                }
                runOnUiThread(() -> ToastHelper.showActionMessage(MainActivity.this, "", "Selected file are send to raspberry."));

            });
            btnOK.setEnabled(false);
            btnOK.setVisibility(View.GONE);
        });
        btnCancel.setOnClickListener(v -> {alert.dismiss();busy=false;});
        alert.show();
    }

    private void doShowAboutApp() {
        Intent myIntent = new Intent(MainActivity.this, AboutActivity.class);
        startActivity(myIntent);
    }

    private void doShowAboutLibraries() {
       new LibsBuilder()
                .withAboutAppName("Music Mate")
                .withAboutIconShown(true)
                .withAboutVersionShown(true)
                .withAboutVersionShownCode(false)
                .withAboutVersionShownName(true)
                .withEdgeToEdge(false)
                .withLicenseShown(true)
                .withLicenseDialog(false)
                .withSearchEnabled(false)
                .withSortEnabled(true)
                .withActivityTitle("Third-Party Libraries")
                .start(this);
    }

    private void setUpSwipeToRefresh() {
        refreshLayout = findViewById(R.id.refreshLayout);
        refreshLayout.setOnRefreshListener(refreshlayout -> adapter.loadDataSets());
    }

    private void setUpRecycleView( ) {
        adapter = new MusicTagAdapter(new SearchCriteria(SearchCriteria.TYPE.MY_SONGS));
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
                refreshLayout.finishRefresh();
                updateHeaderPanel();
                if(adapter.getItemCount()==0) {
                    mStateView.displayState("search");
                }else {
                    mStateView.hideStates();
                }
            }
        });
        mRecyclerView = findViewById(R.id.recycler_view);
        mRecyclerView.setItemViewCacheSize(0);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.setAdapter(adapter);
        mRecyclerView.setHasFixedSize(true); //Size of RV will not change
        RecyclerView.ItemDecoration itemDecoration = new BottomOffsetDecoration(64);
        mRecyclerView.addItemDecoration(itemDecoration);
        mRecyclerView.setPreserveFocusAfterLayout(true); // preserve original location

        MusicTagAdapter.OnListItemClick onListItemClick = (view, position) -> doShowEditActivity(Collections.singletonList(adapter.getMusicTag(position)));
        adapter.setClickListener(onListItemClick);

        mTracker = new SelectionTracker.Builder<>(
                "selection-id",
                mRecyclerView,
                new MusicTagAdapter.KeyProvider(),
                new MusicTagAdapter.DetailsLookup(mRecyclerView),
                StorageStrategy.createLongStorage())
                .withSelectionPredicate(SelectionPredicates.createSelectAnything())
                .build();
        adapter.injectTracker(mTracker);

        SelectionTracker.SelectionObserver<Long> observer = new SelectionTracker.SelectionObserver<Long>() {
            @Override
            public void onSelectionChanged() {
                int count = mTracker.getSelection().size();
                selections.clear();
                if(count > 0) {
                    mTracker.getSelection().forEach(item -> selections.add(adapter.getMusicTag(item.intValue())));//selections += item);
                    if (actionMode == null) {
                        actionMode = startSupportActionMode(actionModeCallback);
                    }
                    actionMode.setTitle(StringUtils.formatSongSize(count));
                    actionMode.invalidate();
              }else if(actionMode != null){
                    actionMode.setTitle(StringUtils.formatSongSize(count));
                    actionMode.invalidate();
                }
            }
        };
        this.mTracker.addObserver(observer);

        new FastScrollerBuilder(mRecyclerView)
                .useMd2Style()
                .setPadding(0,0,8,0)
                .setThumbDrawable(Objects.requireNonNull(ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_fastscroll_thumb)))
                .build();

        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                    super.onScrollStateChanged(recyclerView, newState);
                    if(newState == RecyclerView.SCROLL_STATE_IDLE) {
                        doShowNowPlayingSongFAB(MusixMateApp.getPlayingSong());
                    }else {
						doHideNowPlayingSongFAB();
                    }
                }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
				//showPlayingSongFAB();
            }
            });

        actionModeCallback = new ActionModeCallback();
        mStateView = findViewById(R.id.status_page);
        mStateView.hideStates();
    }

    public View getTabView(String title) {
        // Given you have a custom layout in `res/layout/custom_tab.xml` with a TextView and ImageView
        View v = LayoutInflater.from(this).inflate(R.layout.view_header_tab_item, null);
        TextView hv = v.findViewById(R.id.tabHeader);
        hv.setText(StringUtils.getAbvByUpperCase(title));
        TextView tv = v.findViewById(R.id.tabTitle);
        tv.setText(title);
        return v;
    }

    /*
    private List<TrapezoidPartsView.TrapezoidPart> createTrapezoidParts() {
        List<TrapezoidPartsView.TrapezoidPart> list = new ArrayList<>();
        List<String> titles = this.adapter.getHeaderTitles(getApplicationContext());
        String headerTitle = this.adapter.getHeaderTitle();
        int i=0;
        for(String title: titles) {
            TrapezoidPartsView.TrapezoidPart part = new TrapezoidPartsView.TrapezoidPart();
            if(i==0) {
                part.setBgIcon(UIUtils.getBitmap(this, R.drawable.tab_start_bg));
            }else if(i == (titles.size()-1)){
                part.setBgIcon(UIUtils.getBitmap(this, R.drawable.tab_end_bg));
            }else {
                part.setBgIcon(UIUtils.getBitmap(this, R.drawable.tab_bg));
            }
            if(title.equals(headerTitle)) {
                part.setSelected(true);
            }

           // part.setIcon(getResources().getDrawable(mIconIds[i]));
            part.setText(title);
            part.setShortText(StringUtils.getAbvByUpperCase(title));
            list.add(part);
            i++;
        }
        return list;
    } */

    private void updateHeaderPanel() {
       // mHeaderTPV.setParts(createTrapezoidParts());

        headerTab.removeAllTabs();
        onSetup = true;
        List<String> titles = adapter.getHeaderTitles(getApplicationContext());
        String headerTitle = adapter.getHeaderTitle();
        int i=0;
        for(String title: titles) {
            TabLayout.Tab firstTab = headerTab.newTab(); // Create a new Tab names
            View tabItemView = getTabView(title);
            firstTab.setCustomView(tabItemView);
            firstTab.setText(title); // set the Text for the first Tab
            if(StringUtils.equals(headerTitle, title)) {
                headerTab.addTab(firstTab, true);
            }else {
                headerTab.addTab(firstTab);
            }
            if(i==0) {
                tabItemView.setBackgroundResource(R.drawable.tab_start_bg);
            }else if(i == (titles.size()-1)){
                tabItemView.setBackgroundResource(R.drawable.tab_end_bg);
            }else {
                tabItemView.setBackgroundResource(R.drawable.tab_bg);
            }
            i++;
        }
        onSetup = false;

        int count = adapter.getTotalSongs();
        long totalSize = adapter.getTotalSize();
        String duration = StringUtils.formatDuration(adapter.getTotalDuration(), true);
        SimplifySpanBuild spannable = new SimplifySpanBuild("");
        if(count >0) {
            SearchCriteria criteria = adapter.getCriteria();
            if (!isEmpty(criteria.getFilterType())) {
                String filterType = criteria.getFilterType();
                spannable.appendMultiClickable(new SpecialClickableUnit(headerSubtitle, (tv, clickableSpan) -> adapter.resetFilter()).setNormalTextColor(getColor(R.color.grey200)), new SpecialTextUnit("[" + filterType + "]  ").setTextSize(10));
            }
            spannable.append(new SpecialTextUnit(StringUtils.formatSongSize(count)).setTextSize(12).useTextBold())
                    .append(new SpecialLabelUnit(StringUtils.SYMBOL_HEADER_SEP, ContextCompat.getColor(getApplicationContext(), R.color.grey100), UIUtils.sp2px(getApplication(), 10), Color.TRANSPARENT).setPadding(5).setPaddingLeft(10).setPaddingRight(10).setGravity(SpecialGravity.CENTER))
                    .append(new SpecialTextUnit(StringUtils.formatStorageSize(totalSize)).setTextSize(12).useTextBold())
                    .append(new SpecialLabelUnit(StringUtils.SYMBOL_HEADER_SEP, ContextCompat.getColor(getApplicationContext(), R.color.grey100), UIUtils.sp2px(getApplication(), 10), Color.TRANSPARENT).setPadding(5).setPaddingLeft(10).setPaddingRight(10).setGravity(SpecialGravity.CENTER))
                    .append(new SpecialTextUnit(duration).setTextSize(12).useTextBold());
        }else {
            spannable.append(new SpecialTextUnit("No Results").setTextSize(12).useTextBold());
        }
        headerSubtitle.setText(spannable.build());

    }

    public void onPlaying(MusicTag song) {
        if(song!=null) {
            runOnUiThread(() -> doShowNowPlayingSongFAB(song));
            if(Preferences.isListFollowNowPlaying(getBaseContext())) {
                    if(timer!=null) {
                            timer.cancel();
                    }
                    timer = new Timer();
                    timer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            if(!busy) {
                                runOnUiThread(() -> scrollToListening());
                            }
                        }
                    }, 1500); // 1.5 seconds
            }
        }
    }

    private class ActionModeCallback implements ActionMode.Callback {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.menu_main_actionmode, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            mHeaderPanel.setVisibility(View.GONE);
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            int id = item.getItemId();
            if (id == R.id.action_delete) {
               doDeleteMediaItems(getSelections());
                mode.finish();
                return true;
            }else if (id == R.id.action_transfer_file) {
                doMoveMediaItems(getSelections());
                mode.finish();
                return true;
            }else if (id == R.id.action_edit_metadata) {
                doShowEditActivity(getSelections());
                mode.finish();
                return true;
            }else if (id == R.id.action_encoding_file) {
                doEncodingAudioFiles(getSelections());
                mode.finish();
                return true;
           /* }else if (id == R.id.action_send_to_hibyos) {
                doSendFilesToHibyDAP(getSelections());
                mode.finish();
                return true;*/
            }else if (id == R.id.action_send_to_streaming) {
                doSendToStreamingServer(getSelections());
                mode.finish();
                return true;
           // }else if (id == R.id.action_send_playlist_to_streaming) {
              //  final List<MusicTag> list = getSelections();
              //  MusicMateExecutors.move(() -> {
              //      doSendRadioStationToStreamer(list);
              //  });
              //  mode.finish();
              //  return true;
            }else if (id == R.id.action_calculate_replay_gain) {
                doAnalystDRRG(getSelections());
                mode.finish();
                return true;
           /* }else if (id == R.id.action_export_playlist) {
                doExportAsPlaylist(getSelections());
                mode.finish();
                return true; */
            }else if (id == R.id.action_select_all) {
                if(mTracker.getSelection().size() == adapter.getItemCount()) {
                    // selected all, reset selection
                    mTracker.clearSelection();
                }else {
                    for (int i = 0; i < adapter.getItemCount(); i++) {
                        mTracker.select((long) i);
                    }
                }
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mTracker.clearSelection();
            actionMode = null;
            mHeaderPanel.setVisibility(View.VISIBLE);
        }

        private List<MusicTag> getSelections() {
            List<MusicTag> list = new ArrayList<>(selections);
            return list;
        }
    }


    private void doAnalystDRRG(List<MusicTag> selections) {
        if(selections.isEmpty()) return;

        View cview = getLayoutInflater().inflate(R.layout.view_action_files, null);

        Map<MusicTag, String> statusList = new HashMap<>();
        ListView itemsView = cview.findViewById(R.id.itemListView);
        TextView titleText = cview.findViewById(R.id.title);
        titleText.setText("Dynamic Range and ReplayGain");
        itemsView.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return selections.size();
            }

            @Override
            public Object getItem(int i) {
                return null;
            }

            @Override
            public long getItemId(int i) {
                return 0;
            }

            @Override
            public View getView(int i, View view, ViewGroup viewGroup) {
                view = getLayoutInflater().inflate(R.layout.view_action_listview_item, null);
                MusicTag tag = selections.get(i);
                TextView seq = view.findViewById(R.id.seq);
                TextView name = view.findViewById(R.id.name);
                TextView status = view.findViewById(R.id.status);
                seq.setText(String.valueOf(i+1));
                if(statusList.containsKey(tag)) {
                    status.setText(statusList.get(tag));
                }else {
                    status.setText("-");
                }
                name.setText(FileSystem.getFilename(tag.getPath()));
                return view;
            }
        });

        View btnOK = cview.findViewById(R.id.btn_ok);
        View btnCancel = cview.findViewById(R.id.btn_cancel);

        ProgressBar progressBar = cview.findViewById(R.id.progressBar);

       // btnOK.setEnabled(false);

        btnOK.setEnabled(true);

        double block = Math.min(selections.size(), MAX_PROGRESS_BLOCK);
        double sizeInBlock = MAX_PROGRESS/block;
        List<Long> valueList = new ArrayList<>();
        for(int i=0; i< block;i++) {
            valueList.add((long) sizeInBlock);
        }
        final double rate = 100.00/selections.size(); // pcnt per 1 song
        int barColor = getColor(R.color.material_color_green_400);
        progressBar.setProgressDrawable(new RatioSegmentedProgressBarDrawable(barColor, Color.GRAY, valueList, 8f));
        progressBar.setMax((int) MAX_PROGRESS);

        AlertDialog alert = new MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
                .setTitle("")
                .setView(cview)
                .setCancelable(true)
                .create();
        alert.requestWindowFeature(Window.FEATURE_NO_TITLE);
        alert.setCanceledOnTouchOutside(false);
        // make popup round corners
        alert.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        btnOK.setOnClickListener(v -> {
            busy = true;
            progressBar.setProgress(getInitialProgress(selections.size(), rate));
            for(MusicTag tag: selections) {
                MusicMateExecutors.move(() -> {
                    try {
                        statusList.put(tag, "Analysing");
                        runOnUiThread(() -> {
                            itemsView.invalidateViews();
                        });
                        //calculate track RG
                        FFMPeg.detectQuality(tag);
                        //write RG to file
                        FFMPeg.writeTagQualityToFile(MainActivity.this, tag);
                        // update MusicMate Library
                        MusicTagRepository.saveTag(tag);

                        AudioTagEditResultEvent message = new AudioTagEditResultEvent(AudioTagEditResultEvent.ACTION_UPDATE, Constants.STATUS_SUCCESS, tag);
                        EventBus.getDefault().postSticky(message);
                        statusList.put(tag, "Done");
                        runOnUiThread(() -> {
                            int pct = progressBar.getProgress();
                            progressBar.setProgress((int) (pct + rate));
                            progressBar.invalidate();
                            itemsView.invalidateViews();
                        });
                    } catch (Exception e) {
                        Log.e(TAG,"calculateRG",e);
                        try {
                            statusList.put(tag, "Fail");
                            runOnUiThread(() -> {
                                int pct = progressBar.getProgress();
                                progressBar.setProgress((int) (pct + rate));
                                progressBar.invalidate();
                                itemsView.invalidateViews();
                            });
                        }catch (Exception ex) {}
                    }
                });
            }
            btnOK.setEnabled(false);
            btnOK.setVisibility(View.GONE);
        });
        btnCancel.setOnClickListener(v -> {alert.dismiss();busy=false; });
        busy = true;
        alert.show();
    }

    private int getInitialProgress(int total, double ratePerItem) {
        // 10 % + diff
        return (int) ((ratePerItem/10.0) + (100-(ratePerItem*total)));
    }

    private void doExportAsPlaylist(List<MusicTag> currentSelections) {
        /*
        #EXTM3U
        #PLAYLIST: The title of the playlist

        #EXTINF:111, Sample artist name - Sample track title
        C:\Music\SampleMusic.mp3

        #EXTINF:222,Example Artist name - Example track title
        C:\Music\ExampleMusic.mp3
         */
        Writer out = null;
        try {

            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd", Locale.US);
            String path = "/Playlist/"+adapter.getCriteria().getKeyword()+"_"+currentSelections.size()+"_"+simpleDateFormat.format(new Date())+".m3u";
            path =DocumentFileCompat.buildAbsolutePath(getApplicationContext(), StorageId.PRIMARY, path);
            File filepath = new File(path);
            File folder = filepath.getParentFile();
            if(!folder.exists()) {
                folder.mkdirs();
            }
            out = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(filepath,true), StandardCharsets.UTF_8));
            out.write("#EXRM3U\n");
            out.write("#PLAYLIST: MusicMate Playlist\n\n");

            for (MusicTag tag:currentSelections) {
                out.write("#EXTINF:"+tag.getAudioDuration()+","+tag.getArtist()+","+tag.getTitle()+"\n");
                out.write(tag.getPath()+"\n\n");
            }
            ToastHelper.showActionMessage(MainActivity.this, "", "Playlist '"+path+"' is created.");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                out.close();
            }catch (Exception ex) {}
        }
    }

    private void doEncodingAudioFiles(List<MusicTag> selections) {
        if(selections.isEmpty()) return;
        // convert WAVE to AIFF, FLAC, ALAC
        // convert AIFF to WAVE, FLAC, ALAC
        // convert FLAC to ALAC
        // convert ALAC to FLAC

        View cview = getLayoutInflater().inflate(R.layout.view_action_encoding_files, null);

        Map<MusicTag, String> statusList = new HashMap<>();
        ListView itemsView = cview.findViewById(R.id.itemListView);
        itemsView.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return selections.size();
            }

            @Override
            public Object getItem(int i) {
                return null;
            }

            @Override
            public long getItemId(int i) {
                return 0;
            }

            @Override
            public View getView(int i, View view, ViewGroup viewGroup) {
                view = getLayoutInflater().inflate(R.layout.view_action_listview_item, null);
                MusicTag tag = selections.get(i);
                TextView seq = view.findViewById(R.id.seq);
                TextView name = view.findViewById(R.id.name);
                TextView status = view.findViewById(R.id.status);
                seq.setText(String.valueOf(i+1));
                if(statusList.containsKey(tag)) {
                    status.setText(statusList.get(tag));
                }else {
                    status.setText("-");
                }
                name.setText(FileSystem.getFilename(tag.getPath()));
                return view;
            }
        });

        View btnOK = cview.findViewById(R.id.btn_ok);
        View btnCancel = cview.findViewById(R.id.btn_cancel);

        ProgressBar progressBar = cview.findViewById(R.id.progressBar);
        MaterialRadioButton btnFlacOptimal = cview.findViewById(R.id.mediaEncodingFLACOPT);
        MaterialRadioButton btnFlac = cview.findViewById(R.id.mediaEncodingFLAC);
       /* if(MusicTagUtils.isALACFile(selections.get(0))) {
            btnFlacOptimal.setEnabled(false);
            btnFlac.setEnabled(true);
           // btnMPeg.setEnabled(true);

            btnFlac.setChecked(true);
           // encoding[0] = "FLAC";
        } else */
       // if(MusicTagUtils.isFLACFile(selections.get(0))) {
               // btnAlac.setEnabled(true);
        //        btnFlac.setEnabled(false);
       //         btnOK.setEnabled(true);
       // }else {
           // btnAlac.setEnabled(true);
            btnFlacOptimal.setEnabled(true);
            btnFlac.setEnabled(true);
            btnFlacOptimal.setChecked(true);
            btnOK.setEnabled(true);
      //  }

        double block = Math.min(selections.size(), MAX_PROGRESS_BLOCK);
        double sizeInBlock = MAX_PROGRESS/block;
        List<Long> valueList = new ArrayList<>();
        for(int i=0; i< block;i++) {
            valueList.add((long) sizeInBlock);
        }
        final double rate = 100.00/selections.size(); // pcnt per 1 song
        int barColor = getColor(R.color.material_color_green_400);
        progressBar.setProgressDrawable(new RatioSegmentedProgressBarDrawable(barColor, Color.GRAY, valueList, 8f));
        progressBar.setMax((int) MAX_PROGRESS);

        AlertDialog alert = new MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
                .setTitle("")
                .setView(cview)
                .setCancelable(true)
                .create();
        alert.requestWindowFeature(Window.FEATURE_NO_TITLE);
        alert.setCanceledOnTouchOutside(false);
        // make popup round corners
        alert.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        btnOK.setOnClickListener(v -> {
            busy = true;
            int cLevel = 0; // for flac 0 un-compressed, 8 most compress
            String targetExt = "FLAC";
            if(btnFlacOptimal.isChecked()) {
               // targetExt = "m4a";
                cLevel = 4;
            }else {
               // compressLevel = 2; // 5 = default, 0 less, 8 most compress
               // targetExt = "FLAC";
                cLevel = -1;
            }

            if(isEmpty(targetExt)) {
                return;
            }

            progressBar.setProgress(getInitialProgress(selections.size(), rate));

            String finalTargetExt = targetExt.toLowerCase();
            int finalCLevel = cLevel;
            MusicMateExecutors.move(() -> {
                for(MusicTag tag: selections) {
                    if(!StringUtils.trimToEmpty(finalTargetExt).equalsIgnoreCase(tag.getFileFormat()))  {
                        String srcPath = tag.getPath();
                        String filePath = FileUtils.removeExtension(tag.getPath());
                        String targetPath = filePath+"."+ finalTargetExt;
                        int bitDepth = tag.getAudioBitsDepth();
                        statusList.put(tag, "Encoding");
                        runOnUiThread(() -> {
                            int pct = progressBar.getProgress();
                            progressBar.setProgress((int) (pct + rate));
                            progressBar.invalidate();
                            itemsView.invalidateViews();
                        });

                        if(FFMPeg.convert(getApplicationContext(),srcPath, targetPath, finalCLevel, bitDepth)) {
                            statusList.put(tag, "Done");
                            repos.scanMusicFile(new File(targetPath),false); // re scan file
                            runOnUiThread(() -> {
                                    int pct = progressBar.getProgress();
                                    progressBar.setProgress((int) (pct+rate));
                                    progressBar.invalidate();
                                    itemsView.invalidateViews();
                                });
                        }else {
                            statusList.put(tag, "Fail");
                            runOnUiThread(() -> {
                                int pct = progressBar.getProgress();
                                progressBar.setProgress((int) (pct+rate));
                                progressBar.invalidate();
                                itemsView.invalidateViews();
                            });
                        }
                    }else {
                        statusList.put(tag, "Skip");
                        runOnUiThread(() -> {
                            int pct = progressBar.getProgress();
                            progressBar.setProgress((int) (pct + rate));
                            progressBar.invalidate();
                            itemsView.invalidateViews();
                        });
                    }
                }

            });
            btnOK.setEnabled(false);
            btnOK.setVisibility(View.GONE);
        });
        btnCancel.setOnClickListener(v -> {alert.dismiss();busy=false;});
        alert.show();
    }
}
