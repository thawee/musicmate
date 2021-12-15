package apincer.android.mmate.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.Uri;
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
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener;
import com.mikepenz.aboutlibraries.LibsBuilder;
import com.scwang.smart.refresh.layout.api.RefreshLayout;
import com.scwang.smart.refresh.layout.listener.OnRefreshListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import apincer.android.mmate.Constants;
import apincer.android.mmate.Preferences;
import apincer.android.mmate.R;
import apincer.android.mmate.epoxy.AudioTagController;
import apincer.android.mmate.fs.EmbedCoverArtProvider;
import apincer.android.mmate.objectbox.AudioTag;
import apincer.android.mmate.repository.AudioFileRepository;
import apincer.android.mmate.repository.SearchCriteria;
import apincer.android.mmate.service.BroadcastData;
import apincer.android.mmate.service.MusicListeningService;
import apincer.android.mmate.ui.view.BottomOffsetDecoration;
import apincer.android.mmate.utils.ApplicationUtils;
import apincer.android.mmate.utils.AudioOutputHelper;
import apincer.android.mmate.utils.AudioTagUtils;
import apincer.android.mmate.utils.PermissionUtils;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.mmate.utils.ToastUtils;
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
import coil.transform.CircleCropTransformation;
import me.zhanghai.android.fastscroll.FastScrollerBuilder;
import sakout.mehdi.StateViews.StateView;
import timber.log.Timber;

/**
 * Created by Administrator on 11/23/17.
 */

public class MediaBrowserActivity extends AppCompatActivity implements View.OnClickListener, View.OnLongClickListener {
    private static final int RECYCLEVIEW_ITEM_POSITION_OFFSET=20; //start scrolling from 5 items
    private static final int RECYCLEVIEW_ITEM_OFFSET= 64*7; // scroll item to offset+1 position on list
    private static final int MENU_ID_FORMAT = 888888888;
    private static final int MENU_ID_GENRE = 777777777;
    private static final int MENU_ID_GROUPING = 66666666;
    private static final int MENU_ID_QUALITY = 55555555;
    private static final int MENU_ID_HIRES = 55555556;
    private static final int MENU_ID_HIFI = 55555557;
    private static final int MENU_ID_SAMPLE_RATE = 444444444;

    ActivityResultLauncher<Intent> editorLauncher;

	private FloatingActionButton fabPlayingAction;
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

    // header panel
    TabLayout headerTab;
    TextView headerSubtitle;

    private ActionModeCallback actionModeCallback;
    private ActionMode actionMode;

    //selected song to scroll
    private AudioTag selectedTag;
    private AudioTag lastPlaying;
    private boolean onSetup = true;

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
            // displayListenningSong = true;
            text = text + "'"+itemsList.get(0).getTitle()+"' song to Music Directory?";
        }

        AlertDialog dlg =new MaterialAlertDialogBuilder(MediaBrowserActivity.this, R.style.AlertDialogTheme)
                .setTitle("Import Songs")
                .setMessage(text)
                .setPositiveButton("Import", (dialogInterface, i) -> {
                    dialogInterface.dismiss();
                    ImportAudioFileWorker.startWorker(getApplicationContext(), itemsList);
                   // MediaItemIntentService.startService(getApplicationContext(),Constants.COMMAND_MOVE,itemsList);
                    epoxyController.loadSource();
                })
                .setNeutralButton("CANCEL", (dialogInterface, i) -> dialogInterface.dismiss()).create();
        dlg.show();
    }
	
	private void showPlayingSongFAB(AudioTag tag) {
      //  if(MusicListeningService.getInstance()==null || MusicListeningService.getInstance().getPlayingSong()==null) {
      //      return;
      //  }
        if(tag ==null) return;

        ImageLoader imageLoader = Coil.imageLoader(getApplicationContext());
        ImageRequest request = new ImageRequest.Builder(getApplicationContext())
                .data(EmbedCoverArtProvider.getUriForMediaItem(tag))
                .crossfade(false)
                .allowHardware(false)
                .transformations(new CircleCropTransformation())
                .target(fabPlayingAction)
                .build();
        imageLoader.enqueue(request);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ViewCompat.animate(fabPlayingAction)
                        .scaleX(1f).scaleY(1f)
                        .alpha(1f).setDuration(250)
                        .setStartDelay(700L)
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
	
	private void hidePlayingSongFAB() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ViewCompat.animate(fabPlayingAction)
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

    @Override
    public final void onActivityResult(final int requestCode, final int resultCode, final Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        if (requestCode == MusicListeningService.REQUEST_CODE_EDIT_MEDIA_TAG) {
           /** for (int changedPosition : changedPositions) {
                MediaItem item = (MediaItem) mLibraryAdapter.getItem(changedPosition);
                if (item == null || !AudioFileRepository.isMediaFileExist(item)) {
                    mLibraryAdapter.removeItem(changedPosition);
                    // remove selection
                    if (mLibraryAdapter.isSelected(changedPosition)) {
                        mLibraryAdapter.removeSelection(changedPosition);
                    }
                } else {
                    mLibraryAdapter.notifyItemChanged(changedPosition);
                }
            } */
        } else if (requestCode == MusicListeningService.REQUEST_CODE_STORAGE_PERMISSION) {
            if (resultCode == Activity.RESULT_OK) {
                // Get Uri from Storage Access Framework.
                // Persist access permissions.
                this.getContentResolver().takePersistableUriPermission(resultData.getData(), (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
               // Preferences.setExternalURI(getApplicationContext(),resultData.getData().);
                Uri uri = resultData.getData();
                String rootPath = resultData.getExtras().getString("STORAGE_PATH");
                //String rootPath = MediaFileRepository.getInstance(getApplication()).getExternalRootPath();
                //String rootPath = uri.getPath();
                Preferences.setPersistableUriPermission(this, rootPath, uri);
            }
        } /*else if (requestCode == DocumentFileUtils.OPEN_DOCUMENT_TREE_CODE) {
            if (resultData != null && resultData.getData() != null) {
                Uri uri = resultData.getData();
                String rootPath = uri.getPath();
                Preferences.setPersistableUriPermission(this, rootPath, uri);
            }
        }*/
    }

    private void doStartRefresh() {
        refreshLayout.autoRefresh();

      ///  refreshLayout.post(() -> {
           /* MotionEvent event = MotionEvent.obtain(4000, 4000, MotionEvent.ACTION_MOVE, 400, 2000, 0);
            refreshLayout.onTouchEvent(event);
            event.setAction(MotionEvent.ACTION_UP);
            refreshLayout.onTouchEvent(event); */
           // refreshLayout.
       ///     mStateView.displayLoadingState();
         //   refreshLayout.setRefreshing(true);
      ///  });
    }

    private void doStopRefresh() {
        refreshLayout.finishRefresh();
       /* if (refreshLayout.isShown()) {
            refreshLayout.finishRefreshing();
        } */
       // mStateView.hideStates();
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
            stopService(new Intent(getApplicationContext(),MusicListeningService.class));
            mExitSnackbar.dismiss();
            finish();
        }
    }

    protected RecyclerView mRecyclerView;
   // private SwipeRefreshLayout mSwipeRefreshLayout;

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

        /*
        viewModelFactory = new ViewModelProvider.Factory() {
            @NonNull
            @Override
            public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                if (modelClass.equals(MediaItemViewModel.class)) {
                    return (T) new MediaItemViewModel(AudioTagRepository.getInstance(getApplication()));
                } else {
                    return null;
                }
            }
        }; */
        setUpNowPoayingView();
        setUpPermissionsAndScan();
        setUpHeaderPanel();
        setUpBottomAppBar();
        setUpRecycleView();
        setUpSwipeToRefresh();
        setUpResideMenus();

        initMediaItemList(getIntent());
        // start foreground service
        Intent serviceIntent = new Intent(this, MusicListeningService.class);
        ContextCompat.startForegroundService(this, serviceIntent );

        mExitSnackbar = Snackbar.make(this.mRecyclerView, R.string.alert_back_to_exit, Snackbar.LENGTH_LONG);
        View snackBarView = mExitSnackbar.getView();
        snackBarView.setBackgroundColor(getColor(R.color.warningColor));
    }

    private void setUpNowPoayingView() {
        nowPlayingView = findViewById(R.id.now_playing_panel);
        nowPlayingView.setVisibility(View.GONE);
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
                    criteria = result.getData().getParcelableExtra(Constants.KEY_SEARCH_CRITERIA);
                }
                epoxyController.loadSource(criteria);
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
                    doStartRefresh();
                   // SearchCriteria criteria = epoxyController.getCurrentCriteria();
                   // criteria.setKeyword(tab.getText().toString());
                    epoxyController.loadSource(tab.getText().toString());
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
        doStartRefresh();
        SearchCriteria criteria = null;
        if (startIntent.getExtras() != null) {
            criteria = startIntent.getParcelableExtra(Constants.KEY_SEARCH_CRITERIA);
            String showPlaying = startIntent.getStringExtra(MusicListeningService.FLAG_SHOW_LISTENING);
            if("yes".equalsIgnoreCase(showPlaying)) {
                selectedTag = startIntent.getParcelableExtra(Constants.KEY_MEDIA_TAG);
            }
        }
        epoxyController.loadSource(criteria);
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
               SearchCriteria criteria = epoxyController.getCurrentCriteria();
               criteria.searchFor(query);
               epoxyController.loadSource(criteria);
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
               SearchCriteria criteria = epoxyController.getCurrentCriteria();
               criteria.searchFor(query);
               epoxyController.loadSource(criteria);
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
        fabPlayingAction = findViewById(R.id.fab_playing);
       // UIUtils.getTintedDrawable(fabPlayingAction.getDrawable(), getColor(R.color.now_playing));
        fabPlayingAction.setOnClickListener(view1 -> scrollToListening());
        fabPlayingAction.setOnLongClickListener(view1 -> doPlayNextSong());
    }

    private void doHideSearch() {
        if(mSearchBar.getVisibility() == View.VISIBLE) {
           // mActionModeHelper.destroyActionModeIfCan();
           // if (!isBackToSearch()) {
            headerTab.setVisibility(View.VISIBLE);
                mSearchBar.setVisibility(View.GONE);
                mSearchView.setQuery("", false);
           // }
            doStartRefresh();
            epoxyController.loadSource(true);
           // mediaItemViewModel.loadSource(false);

           // mSwipeRefreshLayout.setEnabled(true);
        }
    }
/*
    private boolean isBackToSearch() {
        SearchCriteria criteria = epoxyController.peekCriteria(); //getPrevSearchCriteria();
        if(criteria == null) return false;

        if(criteria.getType() == SearchCriteria.TYPE.SEARCH ||
                criteria.getType() == SearchCriteria.TYPE.SEARCH_BY_ARTIST||
                criteria.getType() == SearchCriteria.TYPE.SEARCH_BY_ALBUM) {
            return true;
        }
        return false;
    } */
	
	private boolean doPlayNextSong() {
		if(MusicListeningService.getInstance()==null) return false;
        MusicListeningService.getInstance().playNextSong();
        return true;
	}

    private void doShowSearch() {
        headerTab.setVisibility(View.GONE);
        mSearchBar.setVisibility(View.VISIBLE);
        mSearchView.requestFocus();
        UIUtils.showKeyboard(getApplicationContext(), mSearchView);
        /*
        InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        manager.showSoftInput(mSearchView, InputMethodManager.SHOW_IMPLICIT); */
        //mSwipeRefreshLayout.setEnabled(false);
        doStopRefresh();
    }

    private void doShowLeftMenus() {
        if(Preferences.isShowStorageSpace(getApplicationContext())) {
            @SuppressLint("InflateParams") View storageView = getLayoutInflater().inflate(R.layout.view_header_left_menu, null);
            LinearLayout panel = storageView.findViewById(R.id.storage_bar);
            UIUtils.displayStorages(getApplication(),panel);
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
            UIUtils.displayStorages(getApplication(),panel);
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
      //  mResideMenu.setSwipeDirectionDisable(ResideMenu.DIRECTION_RIGHT);
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
        mResideMenu.addMenuItem(MENU_ID_HIRES, R.drawable.ic_format_hires_white, Constants.AUDIO_SQ_HIRES, ResideMenu.DIRECTION_LEFT);
        mResideMenu.addMenuItem(MENU_ID_QUALITY, R.drawable.ic_format_mqa_white, Constants.AUDIO_SQ_PCM_MQA, ResideMenu.DIRECTION_LEFT);
        mResideMenu.addMenuItem(MENU_ID_HIFI, UIUtils.getTintedDrawable(getApplicationContext(), R.drawable.ic_sound_wave, Color.WHITE), Constants.AUDIO_SQ_HIFI, ResideMenu.DIRECTION_LEFT);
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

    private void setUpPermissionsAndScan() {
        if (!PermissionUtils.isPermissionsEnabled(this, MusicListeningService.PERMISSIONS_ALL)) {
           /* PermissionsDialogue.Builder alertPermissions = new PermissionsDialogue.Builder(MediaBrowserActivity.this)
                    .setCancelable(false)
                    .setMessage(getString(R.string.app_name) + " requires the following permissions to manage music")
                    .setIcon(R.drawable.ic_launcher)
                    .setRequireStorage(PermissionsDialogue.REQUIRED)
                    .setOnContinueClicked((view, dialog) -> {
                        dialog.dismiss();
                        setUpPermissionSAF();
                    })
                    .setDecorView(getWindow().getDecorView())
                    .build();
            alertPermissions.show();
            */
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
    }

    private boolean scrollToListening() {
        if (MusicListeningService.getInstance() == null) return false;
        AudioTag item = MusicListeningService.getInstance().getPlayingSong();

        return scrollToSong(item);
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
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // show listening on all songs mode only.
       // displayListenningSong = true;
       // initMediaItemList(intent);
        //mediaItemViewModel.loadSource(new SearchCriteria(SearchCriteria.TYPE.ALL));
    }

    @Override
    protected void onResume() {
        super.onResume();

        initMediaItemList(getIntent());

        if(mResideMenu.isOpened()) {
            mResideMenu.closeMenu();
        }

       // if(alert!=null && alert.isShowing()) {
       //     alert.dismiss();
       // }

        // Register for the particular broadcast based on ACTION string
        IntentFilter filter = new IntentFilter(BroadcastData.BROADCAST_ACTION);
        LocalBroadcastManager.getInstance(this).registerReceiver(operationReceiver, filter);

       // filter = new IntentFilter(MusicListeningService.ACTION);
       // LocalBroadcastManager.getInstance(this).registerReceiver(playingReceiver, filter);
 
    }

    @Override
    protected void onPause() {
        super.onPause();
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
            doStartRefresh();
            epoxyController.loadSource(new SearchCriteria(SearchCriteria.TYPE.MY_SONGS));

            return true;
       /* }else if(item.getItemId() == R.id.menu_new_collection) {
            doHideSearch();
            //enableSwipeRefresh();
            doStartRefresh();
            epoxyController.loadSource(new SearchCriteria(SearchCriteria.TYPE.MY_SONGS, Constants.TITLE_INCOMING_SONGS));
            return true; */
        } else if(item.getItemId() == MENU_ID_GROUPING) {
            doHideSearch();
            //enableSwipeRefresh();
            doStartRefresh();
            epoxyController.loadSource(new SearchCriteria(SearchCriteria.TYPE.GROUPING, (String)item.getTitle()));
            return true;
        } else if(item.getItemId() == MENU_ID_FORMAT) {
            doHideSearch();
            //enableSwipeRefresh();
            doStartRefresh();
            epoxyController.loadSource(new SearchCriteria(SearchCriteria.TYPE.AUDIO_FORMAT, (String)item.getTitle()));
            return true;
        } else if(item.getItemId() == MENU_ID_QUALITY) {
            doHideSearch();
            //enableSwipeRefresh();
            doStartRefresh();
            epoxyController.loadSource(new SearchCriteria(SearchCriteria.TYPE.AUDIO_SQ, (String)item.getTitle()));
            return true;
        } else if(item.getItemId() == MENU_ID_HIRES) {
            doHideSearch();
            //enableSwipeRefresh();
            doStartRefresh();
            //epoxyController.loadSource(new SearchCriteria(SearchCriteria.TYPE.AUDIO_HIRES, (String)item.getTitle()));
            epoxyController.loadSource(new SearchCriteria(SearchCriteria.TYPE.AUDIO_HIRES, Constants.TITLE_HR_LOSSLESS));
            return true;
        } else if(item.getItemId() == MENU_ID_HIFI) {
            doHideSearch();
            //enableSwipeRefresh();
            doStartRefresh();
            //epoxyController.loadSource(new SearchCriteria(SearchCriteria.TYPE.AUDIO_HIFI, (String)item.getTitle()));
            epoxyController.loadSource(new SearchCriteria(SearchCriteria.TYPE.AUDIO_HIFI, Constants.TITLE_HIFI_LOSSLESS));
            return true;
        } else if(item.getItemId() == MENU_ID_SAMPLE_RATE) {
            doHideSearch();
            //enableSwipeRefresh();
            doStartRefresh();
            epoxyController.loadSource(new SearchCriteria(SearchCriteria.TYPE.AUDIO_SAMPLE_RATE, (String)item.getTitle()));
             return true;
        /*} else if(item.getItemId() == MENU_ID_CODEC) {
            doHideSearch();
            mediaItemViewModel.loadSource(new SearchCriteria(SearchCriteria.TYPE.SAMPLING_RATE, (String) item.getTitle()));
            mSwipeRefreshLayout.setEnabled(true);
            return true; */
        } else if (item.getItemId() == MENU_ID_GENRE) {
            doHideSearch();
            //enableSwipeRefresh();
            doStartRefresh();
            epoxyController.loadSource(new SearchCriteria(SearchCriteria.TYPE.GENRE, (String) item.getTitle()));
            return true;
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
        }else if(item.getItemId() == R.id.menu_audiophile) {
            doHideSearch();
            //enableSwipeRefresh();
            doStartRefresh();
           // if(Preferences.isSimilarOnTitleAndArtist(getApplicationContext())) {
                epoxyController.loadSource(new SearchCriteria(SearchCriteria.TYPE.AUDIOPHILE));
           // }else {
           //     epoxyController.loadSource(new SearchCriteria(SearchCriteria.TYPE.SIMILAR_TITLE));
           // }

            return true;
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
        String text = "";
        MusicListeningService service = MusicListeningService.getInstance();
        AudioTag tag = service.getPlayingSong();
      //  text = "Song "+tag.getTitle() +"; BPS: "+tag.getAudioBitsPerSample() +" & SampleRate: "+tag.getAudioSampleRate();
      //  text = text+"\n Player: "+service.getPlayerName();

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
            sourceLabel.setText("Track Quality: "+quality);
        }else {
            sourceText.setText("...");
        }
        if(service.getPlayerName() != null) {
           // playerIcon.setText("");
            playerText.setText(service.getPlayerName());
            playerIcon.setImageDrawable(service.getPlayerIconDrawable());

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
                .setView(view)
                .setPositiveButton("OK", (dialogInterface, i) -> {
                    dialogInterface.dismiss();
                });
        builder.show();
    }

    private void doShowEditActivity(AudioTag mediaItem) {
        if(AudioFileRepository.isMediaFileExist(mediaItem)) {
            //changedPositions.add(position);
            //List<AudioTag> items = Collections.singletonList(mediaItem);
            //MetadataActivity.startActivity(MediaBrowserActivity.this, items);
            Intent myIntent = new Intent(MediaBrowserActivity.this, TagsActivity.class);
           // List<AudioTag> tags = Collections.singletonList(mediaItem);
            ArrayList<AudioTag> tagList = new ArrayList<>();
            tagList.add(mediaItem);
            myIntent.putParcelableArrayListExtra(Constants.KEY_MEDIA_TAG_LIST, tagList);
            myIntent.putExtra(Constants.KEY_SEARCH_CRITERIA, epoxyController.getCriteria());
            editorLauncher.launch(myIntent);
        }else {
            new MaterialAlertDialogBuilder(MediaBrowserActivity.this, R.style.AlertDialogTheme)
                    .setTitle("Problem")
                    .setMessage(getString(R.string.alert_invalid_media_file, mediaItem.getTitle()))
                    .setPositiveButton("GOT IT", (dialogInterface, i) -> {
                        AudioFileRepository.getInstance(getApplication()).deleteMediaItem(mediaItem);
                        epoxyController.loadSource();
                       // mLibraryAdapter.removeItem(position);
                        dialogInterface.dismiss();
                    })
                    .show();
        }
    }

    private void doShowEditActivity(ArrayList<AudioTag> selections) {
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

            Intent myIntent = new Intent(MediaBrowserActivity.this, TagsActivity.class);

            myIntent.putParcelableArrayListExtra(Constants.KEY_MEDIA_TAG_LIST, tagList);
            myIntent.putExtra(Constants.KEY_SEARCH_CRITERIA, epoxyController.getCriteria());
        editorLauncher.launch(myIntent);
    }

    private void doShowAboutApp() {

        new LibsBuilder()
                //provide a style (optional) (LIGHT, DARK, LIGHT_DARK_TOOLBAR)
                //.withActivityStyle(Libs.ActivityStyle.LIGHT_DARK_TOOLBAR)
                //start the activity
                .withAboutAppName("Music Mate")
                .withAboutIconShown(true)
                .withAboutVersionShown(true)
                .withAboutVersionShownCode(true)
                .withAboutVersionShownName(true)
                .withEdgeToEdge(true)
                .withLicenseShown(true)
                .withAboutSpecial1("DEVELOPER")
                .withAboutSpecial1Description("<b>Thawee Prakaipetch</b><br /><br />E-Mail: thaweemail@gmail.com<br />")
                .withAboutSpecial2("DIGITAL MUSIC")
                .withAboutSpecial2Description(ApplicationUtils.getAssetsText(this,"digital_music.html"))
                //.withFields(R.string.class.getFields())
                .withAboutDescription("<b>by Thawee Prakaipetch</b><br /><br />Managing music collections on Android<br /><b>Enjoy Your Music :D</b>")
                .withActivityTitle("About MusicMate")
                .start(this);
    }

    private void setUpSwipeToRefresh() {
       /* refreshLayout = findViewById(R.id.ssPullRefresh);
        refreshLayout.setRepeatMode(SSPullToRefreshLayout.RepeatMode.REPEAT);
        refreshLayout.setRepeatCount(SSPullToRefreshLayout.RepeatCount.INFINITE);
        refreshLayout.setOnRefreshListener(new SSPullToRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                epoxyController.loadSource(null);
            }
        }); */

        refreshLayout = findViewById(R.id.refreshLayout);
        refreshLayout.setOnRefreshListener(new OnRefreshListener() {
            @Override
            public void onRefresh(RefreshLayout refreshlayout) {
                refreshlayout.finishRefresh(2000/*,false*/);//传入false表示刷新失败
            }
        });

       /* refreshLayout.setOnRefreshListener(new LiquidRefreshLayout.OnRefreshListener() {
            @Override
            public void completeRefresh() {

            }

            @Override
            public void refreshing() {
                epoxyController.loadSource(null);
            }
        }); */
       /* mSwipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        mSwipeRefreshLayout.setDistanceToTriggerSync(390);
        mSwipeRefreshLayout.setColorSchemeResources(
                android.R.color.holo_purple, android.R.color.holo_blue_light,
                android.R.color.holo_green_light, android.R.color.holo_orange_light);
        mSwipeRefreshLayout.setOnRefreshListener(() -> {
            // Passing true as parameter we always animate the changes between the old and the new data set
           // doScanMediaItems();
            epoxyController.loadSource(null);
        }); */
    }

    protected void setUpRecycleView() {
        epoxyController = new AudioTagController(this, this, this);
        epoxyController.addModelBuildListener(result -> {
            doStopRefresh();
            updateHeaderPanel();
            scrollToSong(selectedTag);
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
                        if(MusicListeningService.getInstance()!=null) {
                            showPlayingSongFAB(MusicListeningService.getInstance().getPlayingSong());
                        }
                    }else {
						hidePlayingSongFAB();
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
        List<String> titles = epoxyController.getHeaderTitles();
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
        SearchCriteria criteria = epoxyController.getCurrentCriteria();
        if(!StringUtils.isEmpty(criteria.getFilterType())) {
            String filterType = criteria.getFilterType();
            spannable.appendMultiClickable(new SpecialClickableUnit(headerSubtitle, (tv, clickableSpan) -> {
                epoxyController.clearFilter();
            }).setNormalTextColor(getColor(R.color.grey200)), new SpecialTextUnit("x ["+filterType+"] ").setTextSize(10));
        }
        spannable.append(new SpecialTextUnit(StringUtils.formatSongSize (count)).setTextSize(12).useTextBold())
                .append(new SpecialLabelUnit(StringUtils.ARTIST_SEP, ContextCompat.getColor(getApplicationContext(), R.color.grey200), UIUtils.sp2px(getApplication(),10), Color.TRANSPARENT).showBorder(Color.BLACK, 2).setPadding(5).setPaddingLeft(10).setPaddingRight(10).setGravity(SpecialGravity.CENTER))
                .append(new SpecialTextUnit(StringUtils.formatStorageSize(totalSize)).setTextSize(12).useTextBold())
                .append(new SpecialLabelUnit(StringUtils.ARTIST_SEP, ContextCompat.getColor(getApplicationContext(), R.color.grey200), UIUtils.sp2px(getApplication(),10), Color.TRANSPARENT).showBorder(Color.BLACK, 2).setPadding(5).setPaddingLeft(10).setPaddingRight(10).setGravity(SpecialGravity.CENTER))
                .append(new SpecialTextUnit(duration).setTextSize(12).useTextBold());

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
                    if (lastPlaying == null || (lastPlaying != null && !lastPlaying.equals(tag))) {
                        lastPlaying = tag;
                        epoxyController.notifyPlayingStatus();
                        selectedTag = null; //tag; // reset auto scroll to song
                        doShowPlayingSong(tag);
                    }
                   // }
                }else {
                    ToastUtils.showBroadcastData(MediaBrowserActivity.this, broadcastData);
                    if(broadcastData.getAction() != BroadcastData.Action.DELETE) {
                        // refresh tag
                        epoxyController.notifyModelChanged(broadcastData.getTagInfo());
                    }
                    // re-load library
                    epoxyController.loadSource();
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

    private void doShowPlayingSong(AudioTag tag) {
        //ToastUtils.showBroadcastData(MediaBrowserActivity.this, broadcastData);
        if(nowPlayingView==null) return;

        hidePlayingSongFAB();
       // @SuppressLint("InflateParams") View view = getLayoutInflater().inflate(R.layout.dailog_now_playing, null);
        TextView title = nowPlayingView.findViewById(R.id.title);
        title.setText(AudioTagUtils.getFormattedTitle(getApplicationContext(),tag));
        TextView subtitle = nowPlayingView.findViewById(R.id.subtitle);
        subtitle.setText(AudioTagUtils.getFormattedSubtitle(tag));
        ImageView cover = nowPlayingView.findViewById(R.id.coverart);
        ImageLoader imageLoader = Coil.imageLoader(getApplicationContext());
        ImageRequest request = new ImageRequest.Builder(getApplicationContext())
                .data(EmbedCoverArtProvider.getUriForMediaItem(tag))
                .crossfade(false)
                .allowHardware(false)
                .transformations(new CircleCropTransformation())
                .target(cover)
                .build();
        imageLoader.enqueue(request);
        nowPlayingView.setVisibility(View.VISIBLE);
       /* AlertDialog dlg = new MaterialAlertDialogBuilder(MediaBrowserActivity.this, R.style.PlayingDialogTheme)
                //.setIcon(R.drawable.ic_play_arrow_black_24dp)
               // .setTitle("Playing")
                //.setMessage(tag.getTitle()+" by "+tag.getArtist())
                .setView(view)
                .setCancelable(true)
                .setBackground(ContextCompat.getDrawable(getApplicationContext(), R.drawable.bg_transparent))
                .show(); */
        final Timer t = new Timer();
        t.schedule(new TimerTask() {
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        nowPlayingView.setVisibility(View.GONE);
                    }
                });
                showPlayingSongFAB(tag);
               // dlg.dismiss(); // when the task active then close the dialog
                t.cancel(); // also just top the timer thread, otherwise, you may receive a crash report
            }
        }, 2500); // after 2 second (or 2000 miliseconds), the task will be active.
    }

    // Define the callback for what to do when data is received
    /*
    private final BroadcastReceiver playingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int resultCode = intent.getIntExtra(Constants.KEY_RESULT_CODE, RESULT_CANCELED);
            if (resultCode == RESULT_OK && epoxyController!=null) {
                AudioTag tag = intent.getParcelableExtra(Constants.KEY_MEDIA_TAG);
                AudioTag prvTag = intent.getParcelableExtra(Constants.KEY_MEDIA_PRV_TAG);
                epoxyController.notifyModelChanged(tag);
                epoxyController.notifyModelChanged(prvTag);
                if(tag!=null) {
                    selectedTag = null; //tag; // reset auto scroll to song
                    fabPlayingAction.setVisibility(View.VISIBLE);
                    ImageLoader imageLoader = Coil.imageLoader(getApplicationContext());
                    ImageRequest request = new ImageRequest.Builder(getApplicationContext())
                            .data(EmbedCoverArtProvider.getUriForMediaItem(tag))
                            .crossfade(false)
                            .allowHardware(false)
                            .transformations(new CircleCropTransformation())
                            .target(fabPlayingAction)
                            .build();
                    imageLoader.enqueue(request);
                }
            }
        }
    }; */

    @Override
    public void onClick(View view) {
        RecyclerView.ViewHolder h = mRecyclerView.getChildViewHolder(view);
        if(h instanceof EpoxyViewHolder) {
            EpoxyViewHolder holder = (EpoxyViewHolder)h;
            if (epoxyController.getSelectedItemCount() > 0) {
                enableActionMode(epoxyController.getAudioTag(holder));
            } else {
                doShowEditActivity(epoxyController.getAudioTag(holder));
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

    private class ActionModeCallback implements ActionMode.Callback {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
           // Tools.setSystemBarColor(MultiSelect.this, R.color.colorDarkBlue2);
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
