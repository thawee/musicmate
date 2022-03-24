package apincer.android.mmate.ui;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.transition.Slide;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
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
import com.google.android.material.bottomappbar.BottomAppBar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener;
import com.scwang.smart.refresh.layout.api.RefreshLayout;
import com.scwang.smart.refresh.layout.listener.OnRefreshListener;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import apincer.android.mmate.Constants;
import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.Preferences;
import apincer.android.mmate.R;
import apincer.android.mmate.broadcast.AudioTagEditEvent;
import apincer.android.mmate.broadcast.BroadcastData;
import apincer.android.mmate.broadcast.MusicPlayerInfo;
import apincer.android.mmate.epoxy.AudioTagController;
import apincer.android.mmate.fs.EmbedCoverArtProvider;
import apincer.android.mmate.objectbox.AudioTag;
import apincer.android.mmate.repository.AudioFileRepository;
import apincer.android.mmate.repository.AudioTagRepository;
import apincer.android.mmate.repository.SearchCriteria;
import apincer.android.mmate.ui.view.BottomOffsetDecoration;
import apincer.android.mmate.utils.ApplicationUtils;
import apincer.android.mmate.utils.AudioOutputHelper;
import apincer.android.mmate.utils.AudioTagUtils;
import apincer.android.mmate.utils.PermissionUtils;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.mmate.utils.ToastHelper;
import apincer.android.mmate.utils.UIUtils;
import apincer.android.mmate.work.DeleteAudioFileWorker;
import apincer.android.mmate.work.ImportAudioFileWorker;
import apincer.android.mmate.work.ScanAudioFileWorker;
import apincer.android.residemenu.ResideMenu;
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

    ActivityResultLauncher<Intent> editorLauncher;

    private BottomAppBar bottomAppBar;

    private ResideMenu mResideMenu;

    private AudioTagController epoxyController;

    private Snackbar mExitSnackbar;
    private View mSearchBar;
    private View mHeaderPanel;
    private SearchView mSearchView;
    private ImageView mSearchViewSwitch;

    private RefreshLayout refreshLayout;
    private StateView mStateView;
    private View nowPlayingView;
    private View nowPlayingPanel;
    private ImageView nowPlayingCoverArt;
    private TextView nowPlayingTitle;
   // private TextView nowPlayingSubtitle;
    private ImageView nowPlayingType;
    private ImageView nowPlayingPlayer;
    private ImageView nowPlayingOutput;
    private TextView nowPlayingOutputName;

    // header panel
    TabLayout headerTab;
    TextView headerSubtitle;

    // open tag timer
    private Timer timer;

    private ActionModeCallback actionModeCallback;
    private ActionMode actionMode;

    //selected song to scroll
    private AudioTag selectedTag;
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
                  //  MediaItemIntentService.startService(getApplicationContext(),Constants.COMMAND_DELETE,itemsList);
                    dialogInterface.dismiss();
                    epoxyController.loadSource();
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
                    epoxyController.loadSource();
                })
                .setNeutralButton("CANCEL", (dialogInterface, i) -> dialogInterface.dismiss()).create();
        dlg.show();
    }
	
	private void doShowNowPlayingSongFAB(AudioTag song) {
        ///  if(nowPlayingView.getVisibility() == View.VISIBLE) {
        //prevent show now playing popup and fab as the same time
        //     return;
        //  }
        if (song == null) {
            song = MusixMateApp.getPlayingSong();
        }

        if (song == null || nowPlayingView == null) {
            doHideNowPlayingSongFAB();
            return;
        }
          /*  ImageLoader fabLoader = Coil.imageLoader(getApplicationContext());
            ImageRequest fabRequest = new ImageRequest.Builder(getApplicationContext())
                    .data(EmbedCoverArtProvider.getUriForMediaItem(song))
                    .crossfade(false)
                    .allowHardware(false)
                    .transformations(new CircleCropTransformation())
                    .target(fabPlayingAction)
                    .build();
            fabLoader.enqueue(fabRequest); */
            ImageLoader fabLoader = Coil.imageLoader(this);
            ImageRequest fabRequest = new ImageRequest.Builder(this)
                    .data(EmbedCoverArtProvider.getUriForMediaItem(song))
                    .size(256,256)
                    .crossfade(false)
                    .allowHardware(false)
                    //.transformations(new CircleCropTransformation())
                    .transformations(new RoundedCornersTransformation(86,86,86,86))
                    .target(nowPlayingCoverArt)
                    .build();
            fabLoader.enqueue(fabRequest);

        //nowPlayingSubtitle.setText(AudioTagUtils.getFormattedSubtitle(song));

            nowPlayingTitle.setText(song.getTitle());
            if(song.isMQA()) {
                nowPlayingType.setImageBitmap(AudioTagUtils.getMQASamplingRateIcon(getApplicationContext(), song));
                nowPlayingType.setVisibility(View.VISIBLE);
            }else if(song.isLossless() || AudioTagUtils.isDSD(song)) {
                nowPlayingType.setImageBitmap(AudioTagUtils.getBitsPerSampleIcon(getApplicationContext(), song));
                nowPlayingType.setVisibility(View.VISIBLE);
            }else {
                nowPlayingType.setImageBitmap(AudioTagUtils.getFileFormatIcon(getBaseContext(), song));
                nowPlayingType.setVisibility(View.VISIBLE);
            }
            //player.setImageDrawable(MusicListeningService.getInstance().getPlayerIconDrawable());
            nowPlayingPlayer.setImageDrawable(MusixMateApp.getPlayerInfo().getPlayerIconDrawable());
            AudioOutputHelper.getOutputDevice(getApplicationContext(), new AudioOutputHelper.Callback() {
                @Override
                public void onReady(AudioOutputHelper.Device device) {
                    nowPlayingOutput.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), device.getResId()));
                    nowPlayingOutputName.setText(device.getName());
                }
            });

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                  /*  ViewCompat.animate(fabPlayingAction)
                            .scaleX(1f).scaleY(1f)
                            .alpha(1f).setDuration(250)
                            .setStartDelay(200L)
                            .setListener(new ViewPropertyAnimatorListenerAdapter() {
                                @Override
                                public void onAnimationStart(View view) {
                                    view.setVisibility(View.VISIBLE);
                                }
                            })
                            .start(); */
                    ViewCompat.animate(nowPlayingView)
                            .scaleX(1f).scaleY(1f)
                            .alpha(1f).setDuration(250)
                            .setStartDelay(200L)
                            .setListener(new ViewPropertyAnimatorListenerAdapter() {
                                @Override
                                public void onAnimationStart(View view) {
                                    view.setVisibility(View.VISIBLE);
                                }
                            })
                            .start();
                }
            });

    }
	
	private void doHideNowPlayingSongFAB() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
              /*  ViewCompat.animate(fabPlayingAction)
                        .scaleX(0f).scaleY(0f)
                        .alpha(0f).setDuration(100)
                        .setStartDelay(10L)
                        .setListener(new ViewPropertyAnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(View view) {
                                view.setVisibility(View.GONE);
                            }
                        })
                        .start(); */
                ViewCompat.animate(nowPlayingView)
                        .scaleX(0f).scaleY(0f)
                        .alpha(0f).setDuration(100)
                        .setStartDelay(10L)
                        .setListener(new ViewPropertyAnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(View view) {
                                view.setVisibility(View.GONE);
                            }
                        })
                        .start();
            }
        });
    }

    private void doStartRefresh(SearchCriteria criteria) {
        if(criteria == null) {
            searchCriteria = epoxyController.getCriteria();
        }else {
            searchCriteria = new SearchCriteria(SearchCriteria.TYPE.MY_SONGS);
        }
        refreshLayout.autoRefresh();
    }

    private void doStartRefresh(SearchCriteria.TYPE type, String keyword) {
        if(type == null) {
            searchCriteria = epoxyController.getCriteria();
        }else {
            searchCriteria = new SearchCriteria(type);
        }
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

        if(mSearchBar.getVisibility() == View.VISIBLE) {
            doHideSearch();
            return;
        }

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
        editorLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
            @Override
            public void onActivityResult(ActivityResult result) {
                for(AudioTag tag: epoxyController.getLastSelections()) {
                    epoxyController.notifyModelChanged(tag);
                }

                SearchCriteria criteria = null;
                if (result.getData() != null) {
                    // if retrun criteria, use it otherwise provide null
                    criteria = ApplicationUtils.getSearchCriteria(result.getData()); //result.getData().getParcelableExtra(Constants.KEY_SEARCH_CRITERIA);
                }
                SearchCriteria finalCriteria = criteria;
                backFromEditor = true;
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                epoxyController.loadSource(finalCriteria);
                            }
                        });
                    }
                }, 200);
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
                    epoxyController.loadSource(tab.getText().toString());
                    doStartRefresh(null, tab.getText().toString());
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
        ScanAudioFileWorker.startScan(getApplicationContext());
        SearchCriteria criteria = null;

        if (startIntent.getExtras() != null) {
            criteria =  ApplicationUtils.getSearchCriteria(startIntent); //startIntent.getParcelableExtra(Constants.KEY_SEARCH_CRITERIA);
           // String showPlaying = startIntent.getStringExtra(MusicListeningService.FLAG_SHOW_LISTENING);
           // if("yes".equalsIgnoreCase(showPlaying)) {
           //     selectedTag = startIntent.getParcelableExtra(Constants.KEY_MEDIA_TAG);
           //     criteria = new SearchCriteria(SearchCriteria.TYPE.MY_SONGS);
           // }
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
        TextView label = bottomAppBar.findViewById(R.id.navigation_collections_label);
        ImageView leftMenu = bottomAppBar.findViewById(R.id.navigation_collections);
        UIUtils.getTintedDrawable(leftMenu.getDrawable(), Color.WHITE);
        ImageView rightMenu = bottomAppBar.findViewById(R.id.navigation_settings);
        ImageView searchMenu = bottomAppBar.findViewById(R.id.navigation_search);

        UIUtils.getTintedDrawable(rightMenu.getDrawable(), Color.WHITE);

        mSearchBar = findViewById(R.id.searchBar);
        mSearchView = findViewById(R.id.searchView);
        mSearchViewSwitch = findViewById(R.id.searchViewSwitch);

        mSearchBar.setVisibility(View.GONE);

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
              // if(newText!= null && newText.length()>=2) {
              //     return mLibraryAdapter.searchItems(newText);
              // }
               return false;
           }
       });

       mSearchView.setOnSearchClickListener(new View.OnClickListener() {
           @Override
           public void onClick(View v) {
               String query = String.valueOf(mSearchView.getQuery());
               SearchCriteria criteria = epoxyController.getCriteria();
               doStartRefresh(criteria);
               //epoxyController.loadSource(criteria);
           }
       });

        mSearchViewSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
               // onBackPressed();
                doHideSearch();
            }
        });

        leftMenu.setOnClickListener(v -> doShowLeftMenus());
        label.setOnClickListener(v -> doShowLeftMenus());
        searchMenu.setOnClickListener(v -> {
            if(mSearchBar.getVisibility()==View.GONE) {
                doShowSearch();
            }else {
                doHideSearch();
            }
        });
        rightMenu.setOnClickListener(v -> doShowRightMenus());
         // FAB
       /// fabPlayingAction = findViewById(R.id.fab_now_playing);
       // UIUtils.getTintedDrawable(fabPlayingAction.getDrawable(), getColor(R.color.now_playing));
      ///  fabPlayingAction.setOnClickListener(view1 -> scrollToListening());
      ///  fabPlayingAction.setOnLongClickListener(view1 -> doPlayNextSong());

        // Now Playing
        nowPlayingView = findViewById(R.id.now_playing_panel);
        nowPlayingPanel = findViewById(R.id.now_playing_block);
        nowPlayingTitle = findViewById(R.id.now_playing_title);
       // nowPlayingSubtitle = findViewById(R.id.now_playing_subtitle);
        nowPlayingType = findViewById(R.id.now_playing_file_type);
        nowPlayingPlayer = findViewById(R.id.now_playing_player);
        nowPlayingOutput = findViewById(R.id.now_playing_output);
        nowPlayingOutputName = findViewById(R.id.now_playing_output_name);
        nowPlayingCoverArt = findViewById(R.id.now_playing_coverart);
        nowPlayingView.setOnClickListener(view1 -> scrollToListening());
        nowPlayingView.setOnLongClickListener(view1 -> doPlayNextSong());
        nowPlayingView.setVisibility(View.GONE);
    }

    private void doHideSearch() {
        if(mSearchBar.getVisibility() == View.VISIBLE) {
           // mActionModeHelper.destroyActionModeIfCan();
           // if (!isBackToSearch()) {
            headerTab.setVisibility(View.VISIBLE);
                mSearchBar.setVisibility(View.GONE);
                mSearchView.setQuery("", false);
           // }
            doStartRefresh(null);
            //epoxyController.loadSource(true);
           // mediaItemViewModel.loadSource(false);

           // mSwipeRefreshLayout.setEnabled(true);
        }
    }
	
	private boolean doPlayNextSong() {
		//if(MusicListeningService.getInstance()==null) return false;
        //MusicListeningService.getInstance().playNextSong();
        MusixMateApp.playNextSong(getApplicationContext());
        return true;
	}

    private void doShowSearch() {
        headerTab.setVisibility(View.GONE);
        mSearchBar.setVisibility(View.VISIBLE);
        mSearchView.requestFocus();
        UIUtils.showKeyboard(getApplicationContext(), mSearchView);
        doStopRefresh();
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
        mResideMenu.addMenuItem(MENU_ID_QUALITY, R.drawable.ic_format_mqa_white, Constants.AUDIO_SQ_PCM_MQA, ResideMenu.DIRECTION_LEFT);
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
                    new ActivityResultCallback<ActivityResult>() {
                        @Override
                        public void onActivityResult(ActivityResult result) {
                            initMediaItemList(getIntent());
                        }
                    });
            permissionResultLauncher.launch(myIntent);
        }
        /*if(!EasyPermissions.hasPermissions(getBaseContext(), MusicListeningService.PERMISSIONS_ALL)) {
            Intent myIntent = new Intent(MediaBrowserActivity.this, PermissionActivity.class);
            // You can do the assignment inside onAttach or onCreate, i.e, before the activity is displayed
            ActivityResultLauncher<Intent> permissionResultLauncher = registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    new ActivityResultCallback<ActivityResult>() {
                        @Override
                        public void onActivityResult(ActivityResult result) {
                            initMediaItemList(getIntent());
                        }
                    });
            permissionResultLauncher.launch(myIntent);
        } */
    }

    private boolean scrollToListening() {
       // if (MusicListeningService.getInstance() == null) return false;
       // AudioTag item = MusicListeningService.getInstance().getPlayingSong();

        return scrollToSong(MusixMateApp.getPlayingSong());
    }
    private boolean scrollToSong(AudioTag tag) {
        if (tag == null) return false;

        try {
            int position = epoxyController.getAudioTagPosition(tag);
            position = scrollToPosition(position, true);
            if (position == RecyclerView.NO_POSITION) return false;
            LinearLayoutManager layoutManager = (LinearLayoutManager) mRecyclerView.getLayoutManager();
            layoutManager.scrollToPositionWithOffset(position, RECYCLEVIEW_ITEM_OFFSET);
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

        //broadcastHelper.onResume(this);

        if(!backFromEditor) {
            initMediaItemList(getIntent());
        }
        backFromEditor = false;

        if(mResideMenu.isOpened()) {
            mResideMenu.closeMenu();
        }

       // if(alert!=null && alert.isShowing()) {
       //     alert.dismiss();
       // }

        // Register for the particular broadcast based on ACTION string
        IntentFilter filter = new IntentFilter(BroadcastData.BROADCAST_ACTION);
        LocalBroadcastManager.getInstance(this).registerReceiver(operationReceiver, filter);

     //   if(MusicListeningService.getInstance()!=null) {
            AudioTag tag = MusixMateApp.getPlayingSong(); //MusicListeningService.getInstance().getPlayingSong();
            if(tag!=null) {
                /*ImageLoader imageLoader = Coil.imageLoader(getApplicationContext());
                ImageRequest request = new ImageRequest.Builder(getApplicationContext())
                        .data(EmbedCoverArtProvider.getUriForMediaItem(tag))
                        .crossfade(false)
                        .allowHardware(false)
                        .transformations(new CircleCropTransformation())
                        .target(fabPlayingAction)
                        .build();
                imageLoader.enqueue(request); */
                doShowNowPlayingSongFAB(null);
                //doHideNowPlayingSong();
            }
     //   }

       // filter = new IntentFilter(MusicListeningService.ACTION);
       // LocalBroadcastManager.getInstance(this).registerReceiver(playingReceiver, filter);
 
    }

    @Override
    protected void onPause() {
        super.onPause();
        //broadcastHelper.onPause(this);
        // Unregister the listener when the application is paused
        LocalBroadcastManager.getInstance(this).unregisterReceiver(operationReceiver);
        //LocalBroadcastManager.getInstance(this).unregisterReceiver(playingReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    //Inflate menu to bottom bar
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //getMenuInflater().inflate(R.menu.menu_bottomappbar, menu);
        return super.onCreateOptionsMenu(menu);
    }
/*
    private void enableSwipeRefresh() {
        if(mSwipeRefreshLayout ==null) return;
        mSwipeRefreshLayout.post(() -> mSwipeRefreshLayout.setRefreshing(true));
    } */

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }else if(item.getItemId() == R.id.menu_all_music) {
            doHideSearch();
            //enableSwipeRefresh();
          //  epoxyController.loadSource(new SearchCriteria(SearchCriteria.TYPE.MY_SONGS));
            doStartRefresh(SearchCriteria.TYPE.MY_SONGS, null);

            return true;
       /* }else if(item.getItemId() == R.id.menu_new_collection) {
            doHideSearch();
            //enableSwipeRefresh();
            doStartRefresh();
            epoxyController.loadSource(new SearchCriteria(SearchCriteria.TYPE.MY_SONGS, Constants.TITLE_INCOMING_SONGS));
            return true; */
      //  } else if(item.getItemId() == MENU_ID_FORMAT) {
       //     doHideSearch();
            //enableSwipeRefresh();

           // epoxyController.loadSource(new SearchCriteria(SearchCriteria.TYPE.AUDIO_FORMAT, (String)item.getTitle()));
       //     doStartRefresh(SearchCriteria.TYPE.AUDIO_FORMAT, (String)item.getTitle());
       //     return true;
        } else if(item.getItemId() == MENU_ID_QUALITY_PCM) {
            doHideSearch();
            //enableSwipeRefresh();

            //epoxyController.loadSource(new SearchCriteria(SearchCriteria.TYPE.AUDIO_HIRES, (String)item.getTitle()));
            //  epoxyController.loadSource(new SearchCriteria(SearchCriteria.TYPE.AUDIO_HIRES, Constants.TITLE_HR_LOSSLESS));
            doStartRefresh(SearchCriteria.TYPE.AUDIO_SQ, Constants.TITLE_HIFI_QUALITY);
            return true;
        } else if(item.getItemId() == MENU_ID_QUALITY) {
            doHideSearch();
            //enableSwipeRefresh();

           // epoxyController.loadSource(new SearchCriteria(SearchCriteria.TYPE.AUDIO_SQ, (String)item.getTitle()));
            doStartRefresh(SearchCriteria.TYPE.AUDIO_SQ, (String)item.getTitle());
            return true;
      /*  } else if(item.getItemId() == MENU_ID_HIRES) {
            doHideSearch();
            //enableSwipeRefresh();

            //epoxyController.loadSource(new SearchCriteria(SearchCriteria.TYPE.AUDIO_HIRES, (String)item.getTitle()));
          //  epoxyController.loadSource(new SearchCriteria(SearchCriteria.TYPE.AUDIO_HIRES, Constants.TITLE_HR_LOSSLESS));
            doStartRefresh(SearchCriteria.TYPE.AUDIO_HIRES, Constants.TITLE_HR_LOSSLESS);
            return true;
        } else if(item.getItemId() == MENU_ID_HIFI) {
            doHideSearch();
            //enableSwipeRefresh();

            //epoxyController.loadSource(new SearchCriteria(SearchCriteria.TYPE.AUDIO_HIFI, (String)item.getTitle()));
           // epoxyController.loadSource(new SearchCriteria(SearchCriteria.TYPE.AUDIO_HIFI, Constants.TITLE_HIFI_LOSSLESS));
            doStartRefresh(SearchCriteria.TYPE.AUDIO_HIFI, Constants.TITLE_HIFI_LOSSLESS);
            return true; */
       // } else if(item.getItemId() == MENU_ID_SAMPLE_RATE) {
        //    doHideSearch();
            //enableSwipeRefresh();

            //epoxyController.loadSource(new SearchCriteria(SearchCriteria.TYPE.AUDIO_SAMPLE_RATE, (String)item.getTitle()));
         //   doStartRefresh(SearchCriteria.TYPE.AUDIO_SAMPLE_RATE, (String)item.getTitle());
         //    return true;
        /*} else if(item.getItemId() == MENU_ID_CODEC) {
            doHideSearch();
            mediaItemViewModel.loadSource(new SearchCriteria(SearchCriteria.TYPE.SAMPLING_RATE, (String) item.getTitle()));
            mSwipeRefreshLayout.setEnabled(true);
            return true; */
       // } else if (item.getItemId() == MENU_ID_GENRE) {
        //    doHideSearch();
            //enableSwipeRefresh();
        //    doStartRefresh(SearchCriteria.TYPE.GENRE, (String) item.getTitle());
          //  epoxyController.loadSource(new SearchCriteria(SearchCriteria.TYPE.GENRE, (String) item.getTitle()));
        //    return true;
       /* }else if(item.getItemId() == R.id.menu_similar_songs) {
            doHideSearch();
            //enableSwipeRefresh();
            doStartRefresh();
           // if(Preferences.isSimilarOnTitleAndArtist(getApplicationContext())) {
                epoxyController.loadSource(new SearchCriteria(SearchCriteria.TYPE.MY_SONGS, Constants.TITLE_DUPLICATE));
           // }else {
           //     epoxyController.loadSource(new SearchCriteria(SearchCriteria.TYPE.SIMILAR_TITLE));
           // }

            return true; */
        }else if(item.getItemId() == R.id.menu_groupings) {
            doHideSearch();
            doStartRefresh(SearchCriteria.TYPE.GROUPING, AudioTagRepository.getInstance().getDefaultGroupingList(getApplicationContext()).get(0));
            return true;
        }else if(item.getItemId() == R.id.menu_tag_genre) {
            doHideSearch();
            doStartRefresh(SearchCriteria.TYPE.GENRE, AudioTagRepository.getInstance().getGenreList(getApplicationContext()).get(0));
            return true;
        /*}else if(item.getItemId() == R.id.menu_audiophile) {
            doHideSearch();
            doStartRefresh(SearchCriteria.TYPE.AUDIOPHILE, null);
            return true; */
        }else if(item.getItemId() == R.id.menu_settings) {
            Intent myIntent = new Intent(MediaBrowserActivity.this, SettingsActivity.class);
            startActivity(myIntent);
            return true;
        } else if(item.getItemId() == R.id.menu_sd_permission) {
            //setUpPermissionSAF();
            Intent myIntent = new Intent(MediaBrowserActivity.this, PermissionActivity.class);
            startActivity(myIntent);
            return true;
        } else if(item.getItemId() == R.id.menu_notification_access) {
            //setUpPermissionSAF();
           // Intent myIntent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
           // startActivity(myIntent);
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            startActivity(intent);
            return true;
            // } else if(item.getItemId() == R.id.menu_full_scan) {
       //     doFullScanMediaItems();
       //     return true;
        }else if(item.getItemId() == R.id.menu_about_music_mate) {
            doShowAboutApp();
            return true;
        }else if(item.getItemId() == R.id.navigation_settings) {
            doShowRightMenus();
            return true;
        } else if(item.getItemId() == R.id.navigation_search) {
            doShowSearch();
            return true;
        } else if(item.getItemId() == R.id.menu_signal_path) {
            doShowSignalPath();
            return true;
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
        TextView sourceLabel = view.findViewById(R.id.panel_source_label);
        TextView sourceText =  view.findViewById(R.id.panel_source_text);
        ImageView playerIcon =  view.findViewById(R.id.panel_player_img);
        TextView playerText =  view.findViewById(R.id.panel_player_text);
        ImageView outputIcon =  view.findViewById(R.id.panel_output_img);
        TextView outputText =  view.findViewById(R.id.panel_output_text);
       // TextView outputName = view.findViewById(R.id.panel_output_name);
        TextView outputlabel = view.findViewById(R.id.panel_output_label);
        //sourceIcon.setText(tag==null?"":tag.getAudioEncoding());
        String quality = "";
        String qualityDetails = "";
        if(tag != null) {
            sourceText.setText(tag.getAudioBitCountAndSampleRate());
            sourceIcon.setImageBitmap(AudioTagUtils.getFileFormatIcon(getApplicationContext(),tag));
            quality = AudioTagUtils.getTrackQuality(tag);
            qualityDetails = AudioTagUtils.getTrackQualityDetails(tag);
            sourceLabel.setText("Source: "+quality);
        }else {
            sourceText.setText("-");
        }
        if(playerInfo != null) {
           // playerIcon.setText("");
            playerText.setText(playerInfo.getPlayerName());
            playerIcon.setImageDrawable(playerInfo.getPlayerIconDrawable());
        }else {
            playerText.setText("-");
        }

        AudioOutputHelper.getOutputDevice(getApplicationContext(), new AudioOutputHelper.Callback() {
            @Override
            public void onReady(AudioOutputHelper.Device device) {
                outputIcon.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), device.getResId()));
                outputText.setText(device.getDescription());
//                outputName.setText(device.getCodec());
                if(StringUtils.isEmpty(device.getCodec())) {
                    outputlabel.setText("Output: " + device.getName());
                }else {
                    outputlabel.setText("Output: " + device.getName() +" ("+device.getCodec()+")");
                }
            }
        });
        if(StringUtils.isEmpty(qualityDetails)) {
            qualityDetails = "Track details is not available, you may listen to streaming service or not currently listen to any song.";
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(MediaBrowserActivity.this) //, R.style.SignalPathDialogTheme)
                .setTitle("Signal Path: "+quality)
                .setMessage(qualityDetails) // + text)
                .setView(view);
                //.setPositiveButton("OK", (dialogInterface, i) -> {
                //    dialogInterface.dismiss();
                //});
        builder.show();
    }

    private void doShowEditActivity(AudioTag mediaItem) {
        if(AudioFileRepository.isMediaFileExist(mediaItem)) {
           /* Intent myIntent = new Intent(MediaBrowserActivity.this, TagsActivity.class);
            ArrayList<AudioTag> tagList = new ArrayList<>();
            tagList.add(mediaItem);
            myIntent.putParcelableArrayListExtra(Constants.KEY_MEDIA_TAG_LIST, tagList);
            ApplicationUtils.setSearchCriteria(myIntent,epoxyController.getCriteria()); //myIntent.putExtra(Constants.KEY_SEARCH_CRITERIA, epoxyController.getCriteria());
            editorLauncher.launch(myIntent); */

            ArrayList<AudioTag> tagList = new ArrayList<>();
            tagList.add(mediaItem);
            AudioTagEditEvent message = new AudioTagEditEvent("edit", epoxyController.getCriteria(), tagList);
            EventBus.getDefault().postSticky(message);
            Intent myIntent = new Intent(MediaBrowserActivity.this, TagsActivity.class);
            editorLauncher.launch(myIntent);
           // startActivity(myIntent);
       // }else {
          /*  new MaterialAlertDialogBuilder(MediaBrowserActivity.this, R.style.AlertDialogTheme)
                    .setTitle("Problem")
                    .setMessage(getString(R.string.alert_invalid_media_file, mediaItem.getTitle()))
                    .setPositiveButton("GOT IT", (dialogInterface, i) -> {
                        AudioFileRepository.getInstance(getApplication()).deleteMediaItem(mediaItem);
                        epoxyController.loadSource();
                       // mLibraryAdapter.removeItem(position);
                        dialogInterface.dismiss();
                    })
                    .show(); */

            //ToastHelper.showActionMessage(this,null, Constants.STATUS_FAIL,getString(R.string.alert_invalid_media_file, mediaItem.getTitle()));
            //AudioFileRepository.getInstance(getApplication()).deleteMediaItem(mediaItem);
            //epoxyController.loadSource();
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
                        AudioFileRepository.getInstance(getApplication()).deleteMediaItem(tag);
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

    private void setUpSwipeToRefresh() {
        refreshLayout = findViewById(R.id.refreshLayout);
        refreshLayout.setOnRefreshListener(new OnRefreshListener() {
            @Override
            public void onRefresh(RefreshLayout refreshlayout) {
               epoxyController.loadSource(searchCriteria);
            }
        });
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
                .setThumbDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_fastscroll_thumb))
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
                    onPlaying(tag);
                }else {
                  //  ToastHelper.showBroadcastData(MediaBrowserActivity.this, fabPlayingAction, broadcastData);
                    ToastHelper.showBroadcastData(MediaBrowserActivity.this, null, broadcastData);
                    if(broadcastData.getAction() != BroadcastData.Action.DELETE) {
                        // refresh tag
                        epoxyController.notifyModelChanged(broadcastData.getTagInfo());
                        //  }
                        // re-load library
                        new Timer().schedule(new TimerTask() {
                            @Override
                            public void run() {
                                // this code will be executed after 1 seconds
                                epoxyController.loadSource();
                            }
                        }, 1000);
                    }else {
                        epoxyController.notifyModelChanged(broadcastData.getTagInfo());
                    }
                }
            }
/*
            int resultCode = intent.getIntExtra(Constants.KEY_RESULT_CODE, RESULT_CANCELED);
            if (resultCode == RESULT_OK) {
                String status = intent.getStringExtra(Constants.KEY_STATUS);
                String message = intent.getStringExtra(Constants.KEY_MESSAGE);
                AudioTag tag = intent.getParcelableExtra(Constants.KEY_MEDIA_TAG);
                String command = intent.getStringExtra(Constants.KEY_COMMAND);
                int successCount = intent.getIntExtra(Constants.KEY_SUCCESS_COUNT, 0);
                int errorCount = intent.getIntExtra(Constants.KEY_ERROR_COUNT, 0);
                int pendingTotal = intent.getIntExtra(Constants.KEY_PENDING_TOTAL, 0);


                if(!Constants.COMMAND_SCAN.equalsIgnoreCase(command)) {
                    // skip media scan broadcast
                    ToastUtils.showActionMessageBrowse(MediaBrowserActivity.this, successCount, errorCount, pendingTotal, status, message);
                    if(Constants.COMMAND_DELETE.equalsIgnoreCase(command)) {
                        epoxyController.loadSource(null); // reload current source
                    }else if(tag!=null) {
                        // int position = epoxyController.getAudioTagPosition(tag);
                        //reload Tag
                        epoxyController.notifyModelChanged(tag);
                        //epoxyController.loadSource(null); // reload current source
                    }
                }*/ /*else if(Constants.STATUS_SUCCESS.equalsIgnoreCase(status)) {
                    if(Constants.COMMAND_DELETE.equalsIgnoreCase(command)) {
                        epoxyController.loadSource(null); // reload current source
                    }else if(tag!=null) {
                       // int position = epoxyController.getAudioTagPosition(tag);
                        //reload Tag
                        epoxyController.notifyModelChanged(tag);
                        //epoxyController.loadSource(null); // reload current source
                    }
                } */
                /*
                if(Constants.STATUS_SUCCESS.equalsIgnoreCase(status)) {
					if(Constants.COMMAND_MOVE.equalsIgnoreCase(command) ||
                            Constants.COMMAND_SAVE.equalsIgnoreCase(command)) {
                        int position = mLibraryAdapter.getItemPosition(mediaPath);
                        if(position>-1) {
                            mLibraryAdapter.notifyItemChanged(position);
                        }
                    }else if(Constants.COMMAND_DELETE.equalsIgnoreCase(command)) {
						int position = mLibraryAdapter.getItemPosition(mediaPath);
                        if(position>-1) {
                            IFlexible item = mLibraryAdapter.getItem(position);
                            if(item instanceof MediaItem) {
                                HeaderItem header = ((MediaItem)item).getHeader();
                                if(header !=null) {
                                    header.setCount(header.getCount()-1);
                                }
                            }
                            mLibraryAdapter.removeItem(position);
                            mLibraryAdapter.notifyDataSetChanged();
                        }
                    }else {
                        mLibraryAdapter.notifyDataSetChanged();
                    }
                }*/
        //    }
        }
    };

    private boolean isOnMyMusicCollection() {
      /*  if(epoxyController.getCriteria()!=null) {
            return (epoxyController.getCriteria().getType() == SearchCriteria.TYPE.MY_SONGS) && (StringUtils.isEmpty(epoxyController.getCriteria().getKeyword()) || Constants.TITLE_ALL_SONGS.equals(epoxyController.getCriteria().getKeyword()));
        }
        return false; */
        return true;
    }

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
        if(song!=null) {
            doShowNowPlayingSongFAB(song);
            if(Preferences.isOpenNowPlaying(getBaseContext())) {
                scrollToSong(song);
                if (lastPlaying == null || (lastPlaying != null && !lastPlaying.equals(song))) {
                    if(timer!=null) {
                        //try {
                            timer.cancel();
                       // }catch (Exception ex) {
                            // IGNORE
                      //  }
                    }
                    timer = new Timer();
                    timer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    doShowEditActivity(song);
                                }
                            });
                        }
                    }, 3500); // 3 seconds
                }
            }
        }
        lastPlaying = song;
        selectedTag = null;
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
}
