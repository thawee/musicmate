package apincer.android.mmate.ui;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.transition.Slide;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.ViewPropertyAnimatorListenerAdapter;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.airbnb.epoxy.EpoxyViewHolder;
import com.anggrayudi.storage.file.DocumentFileCompat;
import com.anggrayudi.storage.file.StorageId;
import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.ReturnCode;
import com.google.android.material.bottomappbar.BottomAppBar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener;
import com.mikepenz.aboutlibraries.LibsBuilder;
import com.scwang.smart.refresh.layout.api.RefreshLayout;
import com.skydoves.powerspinner.IconSpinnerAdapter;
import com.skydoves.powerspinner.IconSpinnerItem;
import com.skydoves.powerspinner.PowerSpinnerView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import apincer.android.mmate.Constants;
import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.Preferences;
import apincer.android.mmate.R;
import apincer.android.mmate.broadcast.AudioTagEditEvent;
import apincer.android.mmate.broadcast.AudioTagEditResultEvent;
import apincer.android.mmate.broadcast.BroadcastData;
import apincer.android.mmate.broadcast.MusicPlayerInfo;
import apincer.android.mmate.epoxy.AudioTagController;
import apincer.android.mmate.objectbox.AudioTag;
import apincer.android.mmate.repository.AudioFileRepository;
import apincer.android.mmate.repository.AudioTagRepository;
import apincer.android.mmate.repository.SearchCriteria;
import apincer.android.mmate.ui.view.BottomOffsetDecoration;
import apincer.android.mmate.ui.widget.RatioSegmentedProgressBarDrawable;
import apincer.android.mmate.utils.ApplicationUtils;
import apincer.android.mmate.utils.AudioOutputHelper;
import apincer.android.mmate.utils.AudioTagUtils;
import apincer.android.mmate.utils.PermissionUtils;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.mmate.utils.ToastHelper;
import apincer.android.mmate.utils.UIUtils;
import apincer.android.mmate.work.DeleteAudioFileWorker;
import apincer.android.mmate.work.ImportAudioFileWorker;
import apincer.android.mmate.work.ScanLoudnessWorker;
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
import coil.transform.RoundedCornersTransformation;
import me.zhanghai.android.fastscroll.FastScrollerBuilder;
import sakout.mehdi.StateViews.StateView;
import timber.log.Timber;

/**
 * Created by Administrator on 11/23/17.
 */
public class MediaBrowserActivity extends AppCompatActivity implements View.OnClickListener, View.OnLongClickListener {
    private static final int RECYCLEVIEW_ITEM_POSITION_OFFSET=20; //start scrolling from 5 items
    private static final int RECYCLEVIEW_ITEM_OFFSET= 64*7; // scroll item to offset+1 position on list
    private static final int MENU_ID_QUALITY = 55555555;
    private static final int MENU_ID_QUALITY_PCM = 55550000;
    private static final int MAX_PROGRESS_BLOCK = 10;
    private static final int MAX_PROGRESS = 100;

    ActivityResultLauncher<Intent> editorLauncher;

    AudioFileRepository repos;

    private BottomAppBar bottomAppBar;

    private ResideMenu mResideMenu;

    private AudioTagController epoxyController;

    private Snackbar mExitSnackbar;
    //private View mSearchBar;
    private View mHeaderPanel;
    private SearchView mSearchView;
    //private ImageView mSearchViewSwitch;

    private RefreshLayout refreshLayout;
    private StateView mStateView;
    private View nowPlayingView;
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

    private ActionModeCallback actionModeCallback;
    private ActionMode actionMode;

    //selected song to scroll
    private AudioTag lastPlaying;
    private boolean onSetup = true;

    private SearchCriteria searchCriteria;
    private boolean backFromEditor;

    private void doDeleteMediaItems(List<AudioTag> itemsList) {
        String text = "Delete ";
        if(itemsList.size()>1) {
            text = text + itemsList.size() + " songs?";
        }else {
            text = text + "'"+itemsList.get(0).getTitle()+"' song?";
        }

        new MaterialAlertDialogBuilder(MediaBrowserActivity.this, R.style.AlertDialogTheme)
                .setTitle("Delete Songs")
                .setMessage(text)
                .setPositiveButton("DELETE", (dialogInterface, i) -> {
                    DeleteAudioFileWorker.startWorker(getApplicationContext(), itemsList);
                    dialogInterface.dismiss();
                })
                .setNeutralButton("CANCEL", (dialogInterface, i) -> dialogInterface.dismiss())
                .show();
    }

    private void doMoveMediaItems(List<AudioTag> itemsList) {
        String text = "Import ";
        if(itemsList.size()>1) {
            text = text + itemsList.size() + " songs to Music Directory?";
        }else {
            text = text + "'"+itemsList.get(0).getTitle()+"' song to Music Directory?";
        }

        AlertDialog dlg =new MaterialAlertDialogBuilder(MediaBrowserActivity.this, R.style.AlertDialogTheme)
                .setTitle("Import Songs")
                .setMessage(text)
                .setPositiveButton("Import", (dialogInterface, i) -> {
                    dialogInterface.dismiss();
                    ImportAudioFileWorker.startWorker(getApplicationContext(), itemsList);
                })
                .setNeutralButton("CANCEL", (dialogInterface, i) -> dialogInterface.dismiss()).create();
        dlg.show();
    }
	
	private void doShowNowPlayingSongFAB(final AudioTag song) {
        if (song == null || nowPlayingView == null) {
            doHideNowPlayingSongFAB();
            return;
        }

           // ImageLoader fabLoader = Coil.imageLoader(this);
        ImageLoader imageLoader = Coil.imageLoader(getApplicationContext());
            ImageRequest fabRequest = new ImageRequest.Builder(this)
                   // .data(EmbedCoverArtProvider.getUriForMediaItem(song))
                    .data(AudioTagUtils.getCachedCoverArt(getApplicationContext(), song))
                    .size(256,256)
                    .crossfade(false)
                    .allowHardware(false)
                    //.transformations(new CircleCropTransformation())
                   // .transformations(new RoundedCornersTransformation(86,86,86,86))
                    .transformations(new RoundedCornersTransformation(24,24,24,24))
                    .target(nowPlayingCoverArt)
                    .build();
        imageLoader.enqueue(fabRequest);
            nowPlayingTitle.setText(song.getTitle());

            //nowPlayingType.setImageBitmap(AudioTagUtils.getEncodingSamplingRateIcon(getApplicationContext(), song));
       // nowPlayingType.setImageBitmap(AudioTagUtils.getEncodingSamplingRateIcon(getApplicationContext(), song));
        imageLoader = Coil.imageLoader(getApplicationContext());
        ImageRequest request = new ImageRequest.Builder(getApplicationContext())
                .data(AudioTagUtils.getCachedEncResolutionIcon(getApplicationContext(), song))
                .crossfade(false)
                .target(nowPlayingType)
                //.transformations(new RoundedCornersTransformation(4,4,4,4))
                .build();
        imageLoader.enqueue(request);
        nowPlayingPlayer.setImageDrawable(MusixMateApp.getPlayerInfo().getPlayerIconDrawable());
            AudioOutputHelper.getOutputDevice(getApplicationContext(), device -> {
                nowPlayingOutputDevice.setImageBitmap(AudioOutputHelper.getOutputDeviceIcon(getApplicationContext(),device));
            });

            runOnUiThread(() -> ViewCompat.animate(nowPlayingView)
                    .scaleX(1f).scaleY(1f)
                    .alpha(1f).setDuration(250)
                    .setStartDelay(10L)
                    .setListener(new ViewPropertyAnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(@NonNull View view) {
                            view.setVisibility(View.VISIBLE);
                            epoxyController.notifyModelChanged(song);
                        }
                    })
                    .start());
    }
	
	private void doHideNowPlayingSongFAB() {
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
        if(criteria == null) {
            //  searchCriteria = epoxyController.getCriteria();
            //}else
            if (epoxyController.getCriteria() != null) {
                searchCriteria = epoxyController.getCriteria();; //new SearchCriteria(SearchCriteria.TYPE.MY_SONGS);
            } else {
                searchCriteria = new SearchCriteria(SearchCriteria.TYPE.MY_SONGS);
            }
        }else {
            searchCriteria = criteria;
        }
        refreshLayout.autoRefresh();
    }

    private void doStartRefresh(SearchCriteria.TYPE type, String keyword) {
        //if(type == null) {
        ////    searchCriteria = epoxyController.getCriteria();
        //}else {
            searchCriteria = new SearchCriteria(type);
       // }
        searchCriteria.setKeyword(keyword);
        refreshLayout.autoRefresh();
    }

    private void doStopRefresh() {
        refreshLayout.finishRefresh();
    }

    @Override
    public void onBackPressed() {
        if(mResideMenu.isOpened()) {
            mResideMenu.closeMenu();
            return;
        }

        if(actionMode !=null) {
            actionMode.finish();
        }

        if(mSearchView.isShown()) {
            mSearchView.setIconified(true);
        }

       /* if(mSearchBar.getVisibility() == View.VISIBLE) {
            doHideSearch();
            return;
        } */

        if(epoxyController.hasFilter()) {
            epoxyController.clearFilter();
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repos = AudioFileRepository.newInstance(getApplicationContext());
        initActivityTransitions();
        setContentView(R.layout.activity_browser);
        setUpEditorLauncher();
        setUpPermissions();
        setUpHeaderPanel();
        setUpBottomAppBar();
        setUpRecycleView();
        setUpSwipeToRefresh();
        setUpResideMenus();

        initMediaItemList(getIntent());
        mExitSnackbar = Snackbar.make(this.mRecyclerView, R.string.alert_back_to_exit, Snackbar.LENGTH_LONG);
        View snackBarView = mExitSnackbar.getView();
        snackBarView.setBackgroundColor(getColor(R.color.warningColor));
    }

    private void setUpEditorLauncher() {
        editorLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            for(AudioTag tag: epoxyController.getLastSelections()) {
                epoxyController.notifyModelChanged(tag);
            }

            SearchCriteria criteria = null;
            if (result.getData() != null) {
                // if retrun criteria, use it otherwise provide null
                criteria = ApplicationUtils.getSearchCriteria(result.getData());
            }
            SearchCriteria finalCriteria = criteria;
            backFromEditor = true;
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    runOnUiThread(() -> epoxyController.loadSource(finalCriteria));
                }
            }, 200);
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
                    epoxyController.loadSource(Objects.requireNonNull(tab.getText()).toString());
                    SearchCriteria criteria = epoxyController.getCriteria();
                    criteria.setFilterType("");
                    criteria.setFilterText("");
                    criteria.setKeyword(tab.getText().toString());
                    doStartRefresh(criteria);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });
    }

    private void initMediaItemList(Intent startIntent) {
        //ScanAudioFileWorker.startScan(getApplicationContext());
        SearchCriteria criteria = null;

        if (startIntent.getExtras() != null) {
            criteria =  ApplicationUtils.getSearchCriteria(startIntent);
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
       // TextView label = bottomAppBar.findViewById(R.id.navigation_collections_label);
        ImageView leftMenu = bottomAppBar.findViewById(R.id.navigation_collections);
        UIUtils.getTintedDrawable(leftMenu.getDrawable(), Color.WHITE);
        ImageView rightMenu = bottomAppBar.findViewById(R.id.navigation_settings);
        mSearchView = bottomAppBar.findViewById(R.id.searchView);
       // ImageView searchMenu = bottomAppBar.findViewById(R.id.navigation_search);

        UIUtils.getTintedDrawable(rightMenu.getDrawable(), Color.WHITE);

        /*
       // mSearchBar = findViewById(R.id.searchBar);
        mSearchView = findViewById(R.id.searchView);
       // mSearchViewSwitch = findViewById(R.id.searchViewSwitch);

       // mSearchBar.setVisibility(View.GONE);
        */
       mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
           @Override
           public boolean onQueryTextSubmit(String query) {
              //// if(query!= null && query.length()>=2) {
               //    return mLibraryAdapter.searchItems(query);
              // }
               SearchCriteria criteria = epoxyController.getCriteria();
               criteria.searchFor(query);
               doStartRefresh(criteria);
               //epoxyController.loadSource(criteria);
               return false;
           }

           @Override
           public boolean onQueryTextChange(String newText) {
              /* if(newText!= null && newText.length()>2) {
                   SearchCriteria criteria = epoxyController.getCriteria();
                   criteria.searchFor(newText);
                   doStartRefresh(criteria);
               //    return mLibraryAdapter.searchItems(newText);
               }*/
               return false;
           }
       });

       mSearchView.setOnSearchClickListener(v -> {
           SearchCriteria criteria = epoxyController.getCriteria();
           criteria.searchFor(String.valueOf(mSearchView.getQuery()));
           doStartRefresh(criteria);
           //doStartRefresh(criteria);
       });

       /* mSearchView.setOnClickListener(v -> {
           // onBackPressed();
            //doHideSearch();
            doStartRefresh(null);
            mSearchView.setIconified(true);
        }); */
        mSearchView.setOnCloseListener(new SearchView.OnCloseListener() {
            @Override
            public boolean onClose() {
                SearchCriteria criteria = epoxyController.getCriteria();
                criteria.resetSearch();
                doStartRefresh(null);
               // mSearchView.setIconified(true);
                return false;
            }
        });

        leftMenu.setOnClickListener(v -> doShowLeftMenus());
       // label.setOnClickListener(v -> doShowLeftMenus());
       /* searchMenu.setOnClickListener(v -> {
            if(mSearchBar.getVisibility()==View.GONE) {
                doShowSearch();
            }else {
                doHideSearch();
            }
        }); */
        rightMenu.setOnClickListener(v -> doShowRightMenus());
        // Now Playing
        nowPlayingView = findViewById(R.id.now_playing_panel);
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
       /* if(mSearchBar.getVisibility() == View.VISIBLE) {
            headerTab.setVisibility(View.VISIBLE);
                mSearchBar.setVisibility(View.GONE);
                mSearchView.setQuery("", false);
            doStartRefresh(null);
        } */
    }
	
	private boolean doPlayNextSong() {
        MusixMateApp.playNextSong(getApplicationContext());
        return true;
	}

    private void doShowSearch() {
       /* headerTab.setVisibility(View.GONE);
        mSearchBar.setVisibility(View.VISIBLE);
        mSearchView.requestFocus();
        UIUtils.showKeyboard(getApplicationContext(), mSearchView);
        doStopRefresh(); */
    }

    private void doShowLeftMenus() {
        if(Preferences.isShowStorageSpace(getApplicationContext())) {
            @SuppressLint("InflateParams") View storageView = getLayoutInflater().inflate(R.layout.view_header_left_menu, null);
            LinearLayout panel = storageView.findViewById(R.id.storage_bar);
            UIUtils.buildStoragesUsed(getApplication(),panel);
            mResideMenu.setLeftHeader(storageView);
        }
        mResideMenu.openMenu(ResideMenu.DIRECTION_LEFT);
    }

    private void doShowRightMenus() {
        mResideMenu.openMenu(ResideMenu.DIRECTION_RIGHT);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        super.dispatchTouchEvent(ev);

        if(mResideMenu==null) return true;
        if(Preferences.isShowStorageSpace(getApplicationContext())) {
            @SuppressLint("InflateParams") View storageView = getLayoutInflater().inflate(R.layout.view_header_left_menu, null);
            LinearLayout panel = storageView.findViewById(R.id.storage_bar);
            UIUtils.buildStoragesUsed(getApplication(),panel);
            mResideMenu.setLeftHeader(storageView);
        }
        return mResideMenu.dispatchTouchEvent(ev);
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
       // View storageView = getLayoutInflater().inflate(R.layout.view_header_right_menu, null);
       // mResideMenu.setRightHeader(storageView);
        mResideMenu.addMenuItem(MENU_ID_QUALITY, R.drawable.ic_format_dsd_white, Constants.AUDIO_SQ_DSD, ResideMenu.DIRECTION_LEFT);
       // mResideMenu.addMenuItem(MENU_ID_HIRES, R.drawable.ic_format_hires_white, Constants.AUDIO_SQ_PCM_HRMS, ResideMenu.DIRECTION_LEFT);
       // mResideMenu.addMenuItem(MENU_ID_HIRES, R.drawable.ic_format_hires_white, Constants.AUDIO_SQ_HIRES, ResideMenu.DIRECTION_LEFT);
      //  mResideMenu.addMenuItem(MENU_ID_QUALITY, R.drawable.ic_format_mqa_white, Constants.AUDIO_SQ_PCM_MQA, ResideMenu.DIRECTION_LEFT);
        mResideMenu.addMenuItem(MENU_ID_QUALITY_PCM, UIUtils.getTintedDrawable(getApplicationContext(), R.drawable.ic_sound_wave, Color.WHITE), Constants.AUDIO_SQ_PCM, ResideMenu.DIRECTION_LEFT);
        // mResideMenu.addMenuItem(MENU_ID_HIFI, UIUtils.getTintedDrawable(getApplicationContext(), R.drawable.ic_sound_wave, Color.WHITE), Constants.AUDIO_SQ_HIFI, ResideMenu.DIRECTION_LEFT);
       // mResideMenu.addMenuItem(MENU_ID_HIFI, UIUtils.getTintedDrawable(getApplicationContext(), R.drawable.ic_sound_wave_line, Color.WHITE), Constants.AUDIO_SQ_PCM_HQ, ResideMenu.DIRECTION_LEFT);
        // mResideMenu.addMenuItem(MENU_ID_QUALITY, R.drawable.ic_format_mqa_white, Constants.AUDIO_SQ_PCM_LOSSLESS, ResideMenu.DIRECTION_LEFT);
/*
        if(Preferences.isShowPCMAudio(getApplicationContext())) {
           // Bitmap bitmap = MediaItemUtils.createBitmapFromTextSquare(this, 40,40,"HRA",Color.WHITE,Color.TRANSPARENT,Color.TRANSPARENT);
            Bitmap bitmap = AudioTagUtils.createButtonFromText(this, 52,52,"HDA",Color.WHITE,Color.TRANSPARENT,Color.TRANSPARENT);
            mResideMenu.addMenuItem(MENU_ID_QUALITY, new BitmapDrawable(getResources(), bitmap), Constants.AUDIO_SQ_PCM_HD, ResideMenu.DIRECTION_LEFT);
           // bitmap = MediaItemUtils.createBitmapFromTextSquare(this, 56,56,"CRA",Color.WHITE,Color.TRANSPARENT,Color.TRANSPARENT);
            bitmap = AudioTagUtils.createButtonFromText(this, 52,52,"SDA",Color.WHITE,Color.TRANSPARENT,Color.TRANSPARENT);
            mResideMenu.addMenuItem(MENU_ID_QUALITY, new BitmapDrawable(getResources(), bitmap), Constants.AUDIO_SQ_PCM_SD, ResideMenu.DIRECTION_LEFT);
           // bitmap = MediaItemUtils.createBitmapFromTextSquare(this, 40,40,"LRA",Color.WHITE,Color.TRANSPARENT,Color.TRANSPARENT);
            bitmap = AudioTagUtils.createButtonFromText(this, 52,52,"LDA",Color.WHITE,Color.TRANSPARENT,Color.TRANSPARENT);
            mResideMenu.addMenuItem(MENU_ID_QUALITY, new BitmapDrawable(getResources(), bitmap), Constants.AUDIO_SQ_PCM_LD, ResideMenu.DIRECTION_LEFT);
        } */
/*
        if(Preferences.isShowAudioSampleRate(getApplicationContext())) {
            List<String> sampleRateList = AudioTagRepository.getInstance(getApplication()).getSampleRates();
            for (String sampleRate : sampleRateList) {
                mResideMenu.addMenuItem(MENU_ID_SAMPLE_RATE, R.drawable.ic_waves_white, sampleRate, ResideMenu.DIRECTION_LEFT);
            }
        } */
    }

    private void setUpPermissions() {
        if(!PermissionUtils.hasPermissions(getApplicationContext(), PermissionUtils.PERMISSIONS_ALL)) {
            // do not have read/write storage permission
            Intent myIntent = new Intent(MediaBrowserActivity.this, PermissionActivity.class);
            // You can do the assignment inside onAttach or onCreate, i.e, before the activity is displayed
            ActivityResultLauncher<Intent> permissionResultLauncher = registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> initMediaItemList(getIntent()));
            permissionResultLauncher.launch(myIntent);
        }
    }

    private boolean scrollToListening() {
        return scrollToSong(MusixMateApp.getPlayingSong());
    }
    private boolean scrollToSong(AudioTag tag) {
        if (tag == null) return false;

        try {
            int position = epoxyController.getAudioTagPosition(tag);
            position = scrollToPosition(position, true);
            if (position == RecyclerView.NO_POSITION) return false;
            LinearLayoutManager layoutManager = (LinearLayoutManager) mRecyclerView.getLayoutManager();
            Objects.requireNonNull(layoutManager).scrollToPositionWithOffset(position, RECYCLEVIEW_ITEM_OFFSET);
            return true;
        }catch (Exception ex) {
            Timber.e(ex);
        }
        return false;
    }

    private int scrollToPosition(int position, boolean offset) {
        if(position != RecyclerView.NO_POSITION) {
            if(offset) {
                int positionWithOffset = position - RECYCLEVIEW_ITEM_POSITION_OFFSET;
                if (positionWithOffset < 0) {
                    positionWithOffset = 0;
                }
                mRecyclerView.scrollToPosition(positionWithOffset);
            }
        }
        return position;
    }

    @Override
    protected void onResume() {
        super.onResume();

        if(!backFromEditor) {
            initMediaItemList(getIntent());
        }
        backFromEditor = false;

        if(mResideMenu.isOpened()) {
            mResideMenu.closeMenu();
        }

        // Register for the particular broadcast based on ACTION string
        IntentFilter filter = new IntentFilter(BroadcastData.BROADCAST_ACTION);
        LocalBroadcastManager.getInstance(this).registerReceiver(operationReceiver, filter);

            AudioTag tag = MusixMateApp.getPlayingSong();
            if(tag!=null) {
                doShowNowPlayingSongFAB(null);
            }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister the listener when the application is paused
        LocalBroadcastManager.getInstance(this).unregisterReceiver(operationReceiver);
    }

    @Subscribe(threadMode = ThreadMode.MAIN,sticky = true)
    public void onMessageEvent(AudioTagEditResultEvent event) {
        // call from EventBus
        try {
            //if(AudioTagEditResultEvent.ACTION_UPDATE.equals(event.getAction())) {
            //    AudioTag tag = event.getItem();
            //    epoxyController.notifyModelChanged(tag);
            //}else {
                // re-load library
               // new Timer().schedule(new TimerTask() {
                   // @Override
                   // public void run() {
                        // this code will be executed after 1 seconds
                        epoxyController.loadSource();
              //      }
              //  }, 300);
          //  }else {
         //       epoxyController.notifyModelChanged(event.getItem());
           // }
        }catch (Exception e) {
            Timber.e(e);
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
    public void onStop() {
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    //Inflate menu to bottom bar
    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        return super.onCreateOptionsMenu(menu);
        /*super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.menu_main_browser,menu);
        SearchView searchView = (SearchView) menu.findItem( R.id.action_search).getActionView();
        return true; */
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }else if(item.getItemId() == R.id.menu_all_music) {
            doHideSearch();
            doStartRefresh(SearchCriteria.TYPE.MY_SONGS, null);
            return true;
        }else if(item.getItemId() == R.id.menu_audiophile) {
            doHideSearch();
            doStartRefresh(SearchCriteria.TYPE.AUDIOPHILE, null);
            return true;
        } else if(item.getItemId() == MENU_ID_QUALITY_PCM) {
            doHideSearch();
            doStartRefresh(SearchCriteria.TYPE.AUDIO_SQ, Constants.TITLE_HIFI_QUALITY);
            return true;
        } else if(item.getItemId() == MENU_ID_QUALITY) {
            doHideSearch();
            doStartRefresh(SearchCriteria.TYPE.AUDIO_SQ, (String)item.getTitle());
            return true;

        }else if(item.getItemId() == R.id.menu_groupings) {
            doHideSearch();
            doStartRefresh(SearchCriteria.TYPE.GROUPING, AudioTagRepository.getInstance().getDefaultGroupingList(getApplicationContext()).get(0));
            return true;
        }else if(item.getItemId() == R.id.menu_tag_genre) {
            doHideSearch();
            doStartRefresh(SearchCriteria.TYPE.GENRE, AudioTagRepository.getInstance().getGenreList(getApplicationContext()).get(0));
            return true;
        }else if(item.getItemId() == R.id.menu_settings) {
            Intent myIntent = new Intent(MediaBrowserActivity.this, SettingsActivity.class);
            startActivity(myIntent);
            return true;
       /* }else if(item.getItemId() == R.id.menu_statistics) {
            Intent myIntent = new Intent(MediaBrowserActivity.this, StatisticsActivity.class);
            startActivity(myIntent);
            return true; */
        } else if(item.getItemId() == R.id.menu_sd_permission) {
            //setUpPermissionSAF();
            Intent myIntent = new Intent(MediaBrowserActivity.this, PermissionActivity.class);
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
       // } else if(item.getItemId() == R.id.navigation_search) {
       //     doShowSearch();
       //     return true;
        } else if(item.getItemId() == R.id.menu_signal_path) {
            doShowSignalPath();
            return true;
       /* } else if(item.getItemId() == R.id.menu_storage) {
            doDeepScan();
            return true;*/
        }
        return super.onOptionsItemSelected(item);
    }

    private void doShowSignalPath() {
       // String text = "";
       // MusicListeningService service = MusicListeningService.getInstance();
       // AudioTag tag = service.getPlayingSong();
      //  text = "Song "+tag.getTitle() +"; BPS: "+tag.getAudioBitsPerSample() +" & SampleRate: "+tag.getAudioSampleRate();
      //  text = text+"\n Player: "+service.getPlayerName();
        AudioTag tag = MusixMateApp.getPlayingSong();

        MusicPlayerInfo playerInfo = MusixMateApp.getPlayerInfo();

        // file
        // player
        // output
        LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_signal_path, null);
        ImageView sourceIcon =  view.findViewById(R.id.panel_source_img);
        TextView sourceText =  view.findViewById(R.id.panel_source_text);
        ImageView playerIcon =  view.findViewById(R.id.panel_player_img);
        TextView playerText =  view.findViewById(R.id.panel_player_text);
        ImageView outputIcon =  view.findViewById(R.id.panel_output_img);
        TextView outputText =  view.findViewById(R.id.panel_output_text);
        TextView outputlabel = view.findViewById(R.id.panel_output_label);
        String quality = "";
        String qualityDetails = "";
        if(tag != null && !StringUtils.isEmpty(tag.getPath())) {
            ImageLoader imageLoader = Coil.imageLoader(getApplicationContext());
            ImageRequest request = new ImageRequest.Builder(getApplicationContext())
                    .data(AudioTagUtils.getCachedEncResolutionIcon(getApplicationContext(), tag))
                    .crossfade(false)
                    .target(sourceIcon)
                    .build();
            imageLoader.enqueue(request);
            //sourceIcon.setImageBitmap(AudioTagUtils.getEncodingSamplingRateIcon(getApplicationContext(), tag));
           /* if(tag.isMQA()) {
                sourceIcon.setImageBitmap(AudioTagUtils.getMQASamplingRateIcon(getApplicationContext(), tag));
            }else if(tag.isLossless() || AudioTagUtils.isDSD(tag)) {
                sourceIcon.setImageBitmap(AudioTagUtils.getBitsPerSampleIcon(getApplicationContext(), tag));
            }else {
                sourceIcon.setImageBitmap(AudioTagUtils.getFileFormatIcon(getBaseContext(), tag));
            } */
            quality = AudioTagUtils.getTrackQuality(tag);
            qualityDetails = AudioTagUtils.getTrackQualityDetails(tag);
            sourceText.setText(quality);
        }else {
            sourceText.setText("-");
        }
        if(playerInfo != null) {
            playerText.setText(playerInfo.getPlayerName());
            playerIcon.setImageDrawable(playerInfo.getPlayerIconDrawable());
        }else {
            playerText.setText("-");
        }

        AudioOutputHelper.getOutputDevice(getApplicationContext(), device -> {
            outputIcon.setImageBitmap(AudioOutputHelper.getOutputDeviceIcon(getApplicationContext(), device));
          /*  outputText.setText(device.getDescription());
            outputlabel.setText(device.getName());*/
            outputText.setText(device.getName());
            outputlabel.setText(device.getDescription());
        });
        if(StringUtils.isEmpty(qualityDetails)) {
            qualityDetails = "Track details is not available.";
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(MediaBrowserActivity.this, R.style.SignalPathDialogTheme)
                .setTitle("Signal Path \u266A") //+quality)
                .setMessage(qualityDetails) // + text)
                .setView(view)
                .setPositiveButton("Dismiss", (dialogInterface, i) -> dialogInterface.dismiss());
        builder.show();
    }

    private void doShowEditActivity(AudioTag mediaItem) {
        if(AudioFileRepository.isMediaFileExist(mediaItem)) {
            ArrayList<AudioTag> tagList = new ArrayList<>();
            tagList.add(mediaItem);
            AudioTagEditEvent message = new AudioTagEditEvent("edit", epoxyController.getCriteria(), tagList);
            EventBus.getDefault().postSticky(message);
            Intent myIntent = new Intent(MediaBrowserActivity.this, TagsActivity.class);
            editorLauncher.launch(myIntent);
        }
    }

    private void doShowEditActivity(List<AudioTag> selections) {
        ArrayList<AudioTag> tagList = new ArrayList<>();
        for(AudioTag tag: selections) {
            if(AudioFileRepository.isMediaFileExist(tag)) {
                tagList.add(tag);
            }else {
            new MaterialAlertDialogBuilder(MediaBrowserActivity.this, R.style.AlertDialogTheme)
                    .setTitle("Problem")
                    .setMessage(getString(R.string.alert_invalid_media_file, tag.getTitle()))
                    .setPositiveButton("GOT IT", (dialogInterface, i) -> {
                        repos.deleteMediaItem(tag);
                        epoxyController.loadSource();
                        dialogInterface.dismiss();
                    })
                    .show();
                    }
        }

        if(!tagList.isEmpty()) {
            Intent myIntent = new Intent(MediaBrowserActivity.this, TagsActivity.class);

            AudioTagEditEvent message = new AudioTagEditEvent("edit", epoxyController.getCriteria(), tagList);
            EventBus.getDefault().postSticky(message);
            editorLauncher.launch(myIntent);
        }
    }

    private void doShowAboutApp() {
        Intent myIntent = new Intent(MediaBrowserActivity.this, AboutActivity.class);
        startActivity(myIntent);
    }

    private void doShowAboutLibraries() {
       // Intent myIntent = new Intent(MediaBrowserActivity.this, LibrariesActivity.class);
       // startActivity(myIntent);
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
        refreshLayout.setOnRefreshListener(refreshlayout -> epoxyController.loadSource(searchCriteria));
    }

    protected void setUpRecycleView() {
        epoxyController = new AudioTagController(this, this);
        epoxyController.addModelBuildListener(result -> {
            doStopRefresh();
            updateHeaderPanel();
            scrollToSong(MusixMateApp.getPlayingSong());
            if(epoxyController.getAdapter().getItemCount()==0) {
               mStateView.displayState("search");
            }else {
                mStateView.hideStates();
            }
        });
        mRecyclerView = findViewById(R.id.recycler_view);
        mRecyclerView.setItemViewCacheSize(0); //Setting ViewCache to 0 (default=2) will animate items better while scrolling down+up with LinearLayout
       // mRecyclerView.setWillNotCacheDrawing(true);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.setAdapter(epoxyController.getAdapter());
        mRecyclerView.setHasFixedSize(true); //Size of RV will not change
            // NOTE: Use default item animator 'canReuseUpdatedViewHolder()' will return true if
            // a Payload is provided. FlexibleAdapter is actually sending Payloads onItemChange.
            //mRecyclerView.setItemAnimator(new DefaultItemAnimator());
       // RecyclerView.ItemDecoration itemDecoration = new LinearDividerItemDecoration(this, getColor(R.color.item_divider),1);
        RecyclerView.ItemDecoration itemDecoration = new BottomOffsetDecoration(64);
        mRecyclerView.addItemDecoration(itemDecoration);
     //   RecyclerView.ItemDecoration itemDecoration = new DividerItemDecoration(this, R.drawable.shadow_below);
		
      //  mRecyclerView.addItemDecoration(itemDecoration);
		
       // FastScrollRecyclerViewItemDecoration decoration = new FastScrollRecyclerViewItemDecoration(this);
	//	mRecyclerView.addItemDecoration(decoration);
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
                        doShowNowPlayingSongFAB(null);
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

    private void updateHeaderPanel() {
        onSetup = true;
        headerTab.removeAllTabs();
        List<String> titles = epoxyController.getHeaderTitles(getApplicationContext());
        String headerTitle = epoxyController.getHeaderTitle();
        for(String title: titles) {
            TabLayout.Tab firstTab = headerTab.newTab(); // Create a new Tab names
            firstTab.setText(title); // set the Text for the first Tab
            //headerTab.addTab(firstTab);
            if(StringUtils.equals(headerTitle, title)) {
                headerTab.addTab(firstTab, true);
            }else {
                headerTab.addTab(firstTab);
            }
        }
        onSetup = false;

        int count = epoxyController.getTotalSongs();
        long totalSize = epoxyController.getTotalSize();
        String duration = StringUtils.formatDuration(epoxyController.getTotalDuration(), true);
        SimplifySpanBuild spannable = new SimplifySpanBuild("");
        if(count >0) {
            SearchCriteria criteria = epoxyController.getCriteria();
            if (!StringUtils.isEmpty(criteria.getFilterType())) {
                String filterType = criteria.getFilterType();
                spannable.appendMultiClickable(new SpecialClickableUnit(headerSubtitle, (tv, clickableSpan) -> {
                    epoxyController.clearFilter();
                }).setNormalTextColor(getColor(R.color.grey200)), new SpecialTextUnit("[" + filterType + "]  ").setTextSize(10));
            }
            spannable.append(new SpecialTextUnit(StringUtils.formatSongSize(count)).setTextSize(12).useTextBold())
                    .append(new SpecialLabelUnit(StringUtils.SYMBOL_HEADER_SEP, ContextCompat.getColor(getApplicationContext(), R.color.grey200), UIUtils.sp2px(getApplication(), 10), Color.TRANSPARENT).showBorder(Color.BLACK, 2).setPadding(5).setPaddingLeft(10).setPaddingRight(10).setGravity(SpecialGravity.CENTER))
                    .append(new SpecialTextUnit(StringUtils.formatStorageSize(totalSize)).setTextSize(12).useTextBold())
                    .append(new SpecialLabelUnit(StringUtils.SYMBOL_HEADER_SEP, ContextCompat.getColor(getApplicationContext(), R.color.grey200), UIUtils.sp2px(getApplication(), 10), Color.TRANSPARENT).showBorder(Color.BLACK, 2).setPadding(5).setPaddingLeft(10).setPaddingRight(10).setGravity(SpecialGravity.CENTER))
                    .append(new SpecialTextUnit(duration).setTextSize(12).useTextBold());
        }else {
            spannable.append(new SpecialTextUnit("No Results").setTextSize(12).useTextBold());
        }
        headerSubtitle.setText(spannable.build());

    }

    // Define the callback for what to do when data is received
    private final BroadcastReceiver operationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            BroadcastData broadcastData = BroadcastData.getBroadcastData(intent);
            if(broadcastData!=null) {
                if (broadcastData.getAction() == BroadcastData.Action.PLAYING) {
                    AudioTag tag = broadcastData.getTagInfo();
                    ScanLoudnessWorker.startScan(getApplicationContext(), tag);
                    onPlaying(tag);
                }
            }
        }
    };

    @Override
    public void onClick(View view) {
        RecyclerView.ViewHolder h = mRecyclerView.getChildViewHolder(view);
        if(h instanceof EpoxyViewHolder) {
            EpoxyViewHolder holder = (EpoxyViewHolder)h;
            if (epoxyController.getSelectedItemCount() > 0) {
                enableActionMode(epoxyController.getAudioTag(holder));
            } else {
                doShowEditActivity(Arrays.asList(epoxyController.getAudioTag(holder)));
            }
        }
    }

    @Override
    public boolean onLongClick(View view) {
        RecyclerView.ViewHolder h = mRecyclerView.getChildViewHolder(view);
        if(h instanceof EpoxyViewHolder) {
            EpoxyViewHolder holder = (EpoxyViewHolder) h;
            AudioTag tag = epoxyController.getAudioTag(holder);// ((AudioTagModel_)holder.getModel()).tag();
            enableActionMode(tag);
            return true;
        }

        return false;
    }

    private void enableActionMode(AudioTag tag) {
        if (actionMode == null) {
            actionMode = startSupportActionMode(actionModeCallback);
        }
        toggleSelection(tag);
    }

    private void toggleSelection(AudioTag tag) {
        epoxyController.toggleSelection(tag);
        int count = epoxyController.getSelectedItemCount();

        if (count == 0) {
            actionMode.finish();
        } else {
            actionMode.setTitle(StringUtils.formatSongSize(count));
            actionMode.invalidate();
        }
        int position = epoxyController.getAudioTagPosition(tag);
        epoxyController.notifyModelChanged(position);
    }

    public void onPlaying(AudioTag song) {
        if(lastPlaying!=null) {
            epoxyController.notifyModelChanged(lastPlaying);
        }

        if(song!=null) {
            doShowNowPlayingSongFAB(song);
            if((!song.equals(lastPlaying)) && Preferences.isOpenNowPlaying(getBaseContext())) {
                scrollToSong(song);
                    if(timer!=null) {
                            timer.cancel();
                    }
                    timer = new Timer();
                    timer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            runOnUiThread(() -> doShowEditActivity(song));
                        }
                    }, 1500); // 1.5 seconds
            }
        }
        lastPlaying = song;
    }

    private class ActionModeCallback implements ActionMode.Callback {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.menu_context, menu);
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
               doDeleteMediaItems(epoxyController.getCurrentSelections());
                mode.finish();
                return true;
            }else if (id == R.id.action_transfer_file) {
                doMoveMediaItems(epoxyController.getCurrentSelections());
                mode.finish();
                return true;
            }else if (id == R.id.action_edit_metadata) {
                doShowEditActivity(epoxyController.getCurrentSelections());
                mode.finish();
                return true;
            }else if (id == R.id.action_encoding_file) {
                doEncodingAudioFiles(epoxyController.getCurrentSelections());
                mode.finish();
                return true;
            }else if (id == R.id.action_export_playlist) {
                doExportAsPlaylist(epoxyController.getCurrentSelections());
                mode.finish();
                return true;
            }else if (id == R.id.action_select_all) {
                epoxyController.toggleSelections();
                int count = epoxyController.getSelectedItemCount();
                actionMode.setTitle(StringUtils.formatSongSize(count));
                actionMode.invalidate();
                for(int i=0;i<epoxyController.getAdapter().getItemCount();i++) {
                    epoxyController.notifyModelChanged(i);
                }
                return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            epoxyController.clearSelections();
            actionMode = null;
            mHeaderPanel.setVisibility(View.VISIBLE);
           // epoxyController.loadSource(null);
           // Tools.setSystemBarColor(MultiSelect.this, R.color.colorPrimary);
        }
    }

    private void doExportAsPlaylist(List<AudioTag> currentSelections) {
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
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd");
            String path = "/Playlist/"+searchCriteria.getKeyword()+"_"+currentSelections.size()+"_"+simpleDateFormat.format(new Date())+".m3u";
            path =DocumentFileCompat.buildAbsolutePath(getApplicationContext(), StorageId.PRIMARY, path);
            File filepath = new File(path);
            File folder = filepath.getParentFile();
            if(!folder.exists()) {
                folder.mkdirs();
            }
            out = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(filepath,true), "UTF-8"));
            out.write("#EXRM3U\n");
            out.write("#PLAYLIST: MusicMate Playlist\n\n");

            for (AudioTag tag:currentSelections) {
                out.write("#EXTINF:"+tag.getAudioDuration()+","+tag.getArtist()+","+tag.getTitle()+"\n");
                out.write(tag.getPath()+"\n\n");
            }
            ToastHelper.showActionMessage(MediaBrowserActivity.this, "", "Playlist '"+path+"' is created.");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                out.close();
            }catch (Exception ex) {}
        }
    }

    private void doEncodingAudioFiles(ArrayList<AudioTag> selections) {
        // convert WAVE to AIFF, FLAC, ALAC
        // convert AIFF to WAVE, FLAC, ALAC
        // convert FLAC to ALAC
        // convert ALAC to FLAC

        View cview = getLayoutInflater().inflate(R.layout.view_actionview_encoding_audio_files, null);

        TextView filename = cview.findViewById(R.id.full_filename);
        if(selections.size()==1) {
            filename.setText(selections.get(0).getSimpleName());
        }else {
            filename.setText("Convert "+ StringUtils.formatSongSize(selections.size())+" to desired encoding.");
        }

        final String[] encoding = {null};
        View btnOK = cview.findViewById(R.id.btn_ok);
        View btnCancel = cview.findViewById(R.id.btn_cancel);
        PowerSpinnerView mEncodingView = cview.findViewById(R.id.target_encoding);
        ProgressBar progressBar = cview.findViewById(R.id.progressBar);

        btnOK.setEnabled(false);
        IconSpinnerAdapter adapter = new IconSpinnerAdapter(mEncodingView);
        ArrayList encodingItems = new ArrayList<>();
        encodingItems.add(new IconSpinnerItem("AIFF", null));
       // encodingItems.add(new IconSpinnerItem("ALAC", null));
        encodingItems.add(new IconSpinnerItem("FLAC", null));
        encodingItems.add(new IconSpinnerItem("MP3 (320 kbps)", null));
        adapter.setItems(encodingItems);
        adapter.setOnSpinnerItemSelectedListener((i, iconSpinnerItem, i1, t1) -> {
            encoding[0] = String.valueOf(t1.getText());
            if(StringUtils.isEmpty(encoding[0])) {
                btnOK.setEnabled(false);
            }else {
                btnOK.setEnabled(true);
                progressBar.setProgress(0);
            }
        });
        mEncodingView.setSpinnerAdapter(adapter);
        mEncodingView.setText("FLAC");
        encoding[0]="FLAC";
        btnOK.setEnabled(true);

        int block = selections.size()>MAX_PROGRESS_BLOCK?MAX_PROGRESS_BLOCK:selections.size();
        int sizeInBlock = MAX_PROGRESS/block;
        List valueList = new ArrayList();
        for(int i=0; i< block;i++) {
            valueList.add(sizeInBlock);
        }
        final float rate = 100/selections.size(); // pcnt per 1 song
        int barColor = getColor(R.color.material_color_green_400);
        progressBar.setProgressDrawable(new RatioSegmentedProgressBarDrawable(barColor, Color.GRAY, valueList, 8f));
        progressBar.setMax(MAX_PROGRESS);

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
            if(encoding[0] == null) {
                return;
            }
            final int [] cnt = new int[1];
            cnt[0]=1;
            String options = "";
            String targetExt = encoding[0].toLowerCase();
            if(encoding[0].contains("MP3")) {
                options = " -ar 44100 -ab 320k ";
            }
            progressBar.setProgress(0);
            //progressBar.invalidate();
            /*
            String finalOptions = options;
            Observable.fromCallable(() -> {
                        for(AudioTag tag: selections) {
                            // int count = cnt.getAndIncrement();
                            if (!StringUtils.trimToEmpty(encoding[0]).equalsIgnoreCase(tag.getAudioEncoding())) {
                                String srcPath = tag.getPath();
                                String filePath = FileUtils.removeExtension(tag.getPath());
                                String targetPath = filePath + "." + targetExt; //+".flac";
                                String cmd = "-i \"" + srcPath + "\" " + finalOptions + " \"" + targetPath + "\"";
                                FFmpegSession session = FFmpegKit.execute(cmd);

                                if (ReturnCode.isSuccess(session.getReturnCode())) {
                                    repos.writeTags(targetPath, tag);
                                    repos.scanFileAndSaveTag(new File(targetPath));
                                } else {
                                    String msg = String.format("Command failed with state %s and rc %s.%s", session.getState(), session.getReturnCode(), session.getFailStackTrace());
                                    Timber.d(msg);
                                }
                            }
                            runOnUiThread(() -> {
                                int pct = progressBar.getProgress();
                                progressBar.setProgress((int) (pct+rate));
                            });
                        }
                        return true;
                    }).subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new BlockingBaseObserver<Boolean>() {

                        @Override
                        public void onNext(@io.reactivex.rxjava3.annotations.NonNull Boolean aBoolean) {
                           // int pct = progressBar.getProgress();
                            progressBar.setProgress(100);
                            progressBar.invalidate();
                        }

                        @Override
                        public void onError(@io.reactivex.rxjava3.annotations.NonNull Throwable e) {

                        }
                    }); */


            //final int size = selections.size();
            for(AudioTag tag: selections) {
               // int count = cnt.getAndIncrement();
                if(!StringUtils.trimToEmpty(encoding[0]).equalsIgnoreCase(tag.getAudioEncoding()))  {
                    String srcPath = tag.getPath();
                    String filePath = FileUtils.removeExtension(tag.getPath());
                    String targetPath = filePath+"."+targetExt; //+".flac";
                    String cmd = "-i \""+srcPath+"\" "+options+" \""+targetPath+"\"";
                 /*   FFmpegSession session = FFmpegKit.execute(cmd);
                    progressBar.setProgress((count/size)*100);
                    if (ReturnCode.isSuccess(session.getReturnCode())) {
                        repos.writeTags(targetPath, tag);
                        repos.scanFileAndSaveTag(new File(targetPath));
                    }else {
                        String msg = String.format("Command failed with state %s and rc %s.%s", session.getState(), session.getReturnCode(), session.getFailStackTrace());
                        Timber.d(msg);
                    } */
                   /* if(selections.size()==1) {
                        progressBar.setProgress(100);
                    }else {
                        int count = cnt.getAndIncrement();
                        progressBar.setProgress(count/selections.size()*100);
                    } */

                    FFmpegKit.executeAsync(cmd, session -> {
                        if (ReturnCode.isSuccess(session.getReturnCode())) {
                            repos.writeTags(targetPath, tag);
                            repos.scanFileAndSaveTag(new File(targetPath));
                        }else {
                            String msg = String.format("Command failed with state %s and rc %s.%s", session.getState(), session.getReturnCode(), session.getFailStackTrace());
                            Timber.d(msg);
                        }
                        runOnUiThread(() -> {
                            //if(selections.size()==1) {
                            //    progressBar.setProgress(100);
                            //}else {
                           // cnt[0]=cnt[0]+1;
                            //    int count = cnt.incrementAndGet();
                            int pct = progressBar.getProgress();
                            progressBar.setProgress((int) (pct+rate));
                           // progressBar.invalidate();
                           // }
                        });
                    });
                }else {
                    int pct = progressBar.getProgress();
                    progressBar.setProgress((int) (pct+rate));
                   // progressBar.invalidate();
                }
            }
            btnOK.setEnabled(false);
            btnOK.setVisibility(View.GONE);
            //alert.dismiss();
        });
        btnCancel.setOnClickListener(v -> alert.dismiss());
        alert.show();
    }
}
