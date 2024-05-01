package apincer.android.mmate.ui;

import static apincer.android.mmate.utils.StringUtils.isEmpty;
import static apincer.android.mmate.utils.StringUtils.trim;
import static apincer.android.mmate.utils.StringUtils.trimToEmpty;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import apincer.android.mmate.Constants;
import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.Preferences;
import apincer.android.mmate.R;
import apincer.android.mmate.broadcast.AudioTagEditEvent;
import apincer.android.mmate.broadcast.AudioTagEditResultEvent;
import apincer.android.mmate.broadcast.AudioTagPlayingEvent;
import apincer.android.mmate.coil.ReflectionTransformation;
import apincer.android.mmate.fs.MusicCoverArtProvider;
import apincer.android.mmate.repository.FFMPeg;
import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.repository.MusicTagRepository;
import apincer.android.mmate.repository.SearchCriteria;
import apincer.android.mmate.utils.ApplicationUtils;
import apincer.android.mmate.utils.MusicTagUtils;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.mmate.utils.UIUtils;
import apincer.android.mmate.work.DeleteAudioFileWorker;
import apincer.android.mmate.work.ImportAudioFileWorker;
import apincer.android.mmate.work.MusicMateExecutors;
import cn.iwgang.simplifyspan.SimplifySpanBuild;
import cn.iwgang.simplifyspan.unit.SpecialClickableUnit;
import cn.iwgang.simplifyspan.unit.SpecialTextUnit;
import coil.Coil;
import coil.ImageLoader;
import coil.request.ImageRequest;
import sakout.mehdi.StateViews.StateView;

public class TagsActivity extends AppCompatActivity {
    private static final String TAG = TagsActivity.class.getName();
    private static final ArrayList<MusicTag> editItems = new ArrayList<>();
    private volatile MusicTag displayTag;
    private ImageView coverArtView;
    private TabLayout tabLayout;
    private Toolbar toolbar;
    private TextView titleView;
    private TextView artistView ;
    private TextView albumView ;
    private TextView genreView;
    private TextView encInfo;
    private TextView pathInfo;
    private TextView drView;
    //private TextView drDBView;
    private TextView fileTypeView;
   // private TextView rgView;
   private TextView newView;
    private View newPanelView;
    private View hiresView;
    private ImageView audiophileView;
    private ImageView resolutionView;

    private CollapsingToolbarLayout toolBarLayout;
    private int toolbar_from_color;
    private int toolbar_to_color;
    private StateView mStateView;
    FileRepository repos;
    private Fragment activeFragment;

    private ImageView playerBtn;
    private View playerPanel;
    private boolean refreshOnNewSong;

    private boolean previewState = true;

    private AlertDialog progressDialog;

    @Override
    public void onBackPressed() {
        super.onBackPressed();

        if(criteria!=null) {
            Intent resultIntent = new Intent();
            ApplicationUtils.setSearchCriteria(resultIntent,criteria);
            setResult(RESULT_OK, resultIntent);
        }
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
        stopProgressBar();
    }

    private void doAnalystDRRG() {
        CompletableFuture.runAsync(
                () -> {
                    startProgressBar();
                    for(MusicTag tag:this.getEditItems()) {
                        //calculate track RG
                        FFMPeg.detectQuality(tag);
                        //write RG to file
                        FFMPeg.writeTagQualityToFile(this, tag);
                        // update MusicMate Library
                        MusicTagRepository.saveTag(tag);
                    }

                    // may need go reload from db
                    displayTag = buildDisplayTag();
                    runOnUiThread(this::updateTitlePanel);
                    stopProgressBar();
                }
        );
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopProgressBar();
    }
    @Override
    protected void onResume() {
        super.onResume();
    }

    private TextView tagInfo;
    private SearchCriteria criteria;

    public List<MusicTag> getEditItems() {
        return editItems;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if(Preferences.isOnNightModeOnly(getApplicationContext())) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM); //must place before super.onCreate();
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tags);
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        repos = FileRepository.newInstance(getApplicationContext());

        coverArtView = findViewById(R.id.panel_cover_art);
        toolBarLayout = findViewById(R.id.toolbar_layout);
        int statusBarHeight = getStatusBarHeight();
        int height = UIUtils.getScreenHeight(this); // getWindow().getWindowManager().getDefaultDisplay().getHeight();
        toolBarLayout.getLayoutParams().height = height + statusBarHeight + 70;
        toolbar_from_color = ContextCompat.getColor(getApplicationContext(),R.color.colorPrimary);
        toolbar_to_color = ContextCompat.getColor(getApplicationContext(),R.color.colorPrimary);
        mStateView = findViewById(R.id.status_page);
        mStateView.hideStates();
        setUpTitlePanel();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        setupMenuToolbar();
        return true;
    }

    @Subscribe(threadMode = ThreadMode.MAIN,sticky = true)
    public void onMessageEvent(AudioTagEditEvent event) {
        // call from EventBus on preview/edit selected tags from main screen
        try {
            criteria = event.getSearchCriteria();
            editItems.clear();
            editItems.addAll(event.getItems());
            displayTag = buildDisplayTag();
            if(displayTag != null){
                runOnUiThread(() -> {
                    updateTitlePanel();
                    setUpPageViewer();
                    stopProgressBar();
                });
            }
        }catch (Exception e) {
            Log.e(TAG, "onMessageEvent", e);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN,sticky = true)
    public void onMessageEvent(AudioTagPlayingEvent event) {
        // call from now playing listener

        if(MusixMateApp.getPlayerInfo()!= null) {
            playerBtn.setVisibility(View.VISIBLE);
            if (refreshOnNewSong) {
                MusicTag tag = event.getPlayingSong();
                if(tag != null) {
                    MusicTagRepository.load(tag);
                    updatePreview(tag);
                    stopProgressBar();
                }
            }
        }
    }

    private void updatePreview(MusicTag playingSong) {
        try {
            editItems.clear();
            editItems.add(playingSong);
            displayTag = buildDisplayTag();
            if (displayTag != null) {
                runOnUiThread(() -> {
                    updateTitlePanel();
                    setUpPageViewer();
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "onMessageEvent", e);
        }
    }

    private void setUpPageViewer() {
        final AppBarLayout appBarLayout = findViewById(R.id.appbar);
        ViewPager2 viewPager = findViewById(R.id.viewpager);

        tabLayout = findViewById(R.id.tabLayout);
        TagsTabLayoutAdapter adapter = new TagsTabLayoutAdapter(getSupportFragmentManager(), getLifecycle());

       // adapter.addNewTab(new TagsMusicBrainzFragment(), "MUSICBRAINZ");
        adapter.addNewTab(new TagsEditorFragment(), "Information");
        adapter.addNewTab(new TagsTechnicalFragment(), "NERD!");
        viewPager.setAdapter(adapter);
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                activeFragment = adapter.fragments.get(position);
                setupMenuToolbar();
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                super.onPageScrollStateChanged(state);
            }
        });

        TabLayoutMediator tabLayoutMediator = new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> tab.setText(adapter.getPageTitle(position)));
        tabLayoutMediator.attach();

        appBarLayout.addOnOffsetChangedListener(new OffSetChangeListener());

        findViewById(R.id.btnEdit).setOnClickListener(v -> appBarLayout.setExpanded(false, true));
        findViewById(R.id.btnDelete).setOnClickListener(v -> doDeleteMediaItem());
       // findViewById(R.id.btnSyncHiby).setOnClickListener(v -> doSyncHibyPlayer());
        findViewById(R.id.btnImport).setOnClickListener(v -> doMoveMediaItem());
        findViewById(R.id.btnAspect).setOnClickListener(view -> ApplicationUtils.startAspect(this, displayTag));
        findViewById(R.id.btnWebSearch).setOnClickListener(view -> ApplicationUtils.webSearch(this,displayTag));
        findViewById(R.id.btnExplorer).setOnClickListener(view -> ApplicationUtils.startFileExplorer(this,displayTag));
    }

    private void setUpTitlePanel() {
        titleView = findViewById(R.id.panel_title);
        artistView = findViewById(R.id.panel_artist);
        albumView = findViewById(R.id.panel_album);
        genreView = findViewById(R.id.panel_genre);
        encInfo = findViewById(R.id.panel_enc);
        pathInfo = findViewById(R.id.panel_path);
        tagInfo = findViewById(R.id.panel_tag);
        hiresView = findViewById(R.id.icon_hires);
        audiophileView = findViewById(R.id.icon_audiophile);
        resolutionView = findViewById(R.id.icon_resolution);
        drView = findViewById(R.id.icon_dr);
        fileTypeView = findViewById(R.id.icon_file_type);
        newView = findViewById(R.id.icon_new);
        newPanelView = findViewById(R.id.icon_new_panel);
        playerBtn = findViewById(R.id.music_player);
        playerPanel = findViewById(R.id.music_player_panel);
        playerPanel.setOnClickListener(view -> {
            refreshOnNewSong = true;
            playerPanel.setBackground(ContextCompat.getDrawable(getApplicationContext(), R.drawable.shape_play_next_background_refresh));
/*
            for(int i=0;i<toolbar.getMenu().size();i++) {
                MenuItem item = toolbar.getMenu().getItem(i);
                if(item.getItemId() == R.id.menu_preview_following_listening) {
                    Drawable drawable = item.getIcon();
                    if(drawable==null) return;
                    if(refreshOnNewSong) {
                        UIUtils.getTintedDrawable(drawable, Color.GREEN);
                    }else {
                        UIUtils.getTintedDrawable(drawable, Color.WHITE);
                    }
                }
            } */
            startProgressBar();
            MusixMateApp.playNextSong(getApplicationContext());
        });
        playerPanel.setOnLongClickListener(view -> {
            refreshOnNewSong = !refreshOnNewSong;
            if(refreshOnNewSong) {
                playerPanel.setBackground(ContextCompat.getDrawable(getApplicationContext(), R.drawable.shape_play_next_background_refresh));
            }else {
                playerPanel.setBackground(ContextCompat.getDrawable(getApplicationContext(), R.drawable.shape_play_next_background));
            }
            return true;
        });
    }

    public void updateTitlePanel() {
        if(MusixMateApp.getPlayerInfo() != null) {
            playerBtn.setVisibility(View.VISIBLE);
            playerBtn.setBackground(MusixMateApp.getPlayerInfo().getPlayerIconDrawable());
            if(refreshOnNewSong) {
                playerPanel.setBackground(ContextCompat.getDrawable(getApplicationContext(), R.drawable.shape_play_next_background_refresh));
            }else {
                playerPanel.setBackground(ContextCompat.getDrawable(getApplicationContext(), R.drawable.shape_play_next_background));
            }
        }else {
            playerBtn.setVisibility(View.GONE);
        }
        if(getEditItems().size()>1) {
            String title = getString(R.string.title_many, getEditItems().size());
            titleView.setText(title);
        }else {
            titleView.setText(trim(displayTag.getTitle(), " - "));
        }
        artistView.setText(trim(displayTag.getArtist(), " - "));
        ImageLoader imageLoader = Coil.imageLoader(getApplicationContext());
        ImageRequest request = new ImageRequest.Builder(getApplicationContext())
                .data(MusicTagUtils.getEncResolutionIcon(getApplicationContext(), displayTag))
                .crossfade(false)
                .target(resolutionView)
                .build();
        imageLoader.enqueue(request);

        drView.setText(MusicTagUtils.getTrackDR(displayTag));
       /* if(MusicTagUtils.isDSD(displayTag)) { //] || !MusicTagUtils.isLossless(displayTag)) {
            drView.setVisibility(View.GONE);
        }else {    // Dynamic Range
            drView.setVisibility(View.VISIBLE);
        } */

        if(MusicTagUtils.isDSD(displayTag) || MusicTagUtils.isPCMHiRes(displayTag)) {
            hiresView.setVisibility(View.VISIBLE);
        }else {
            hiresView.setVisibility(View.GONE);
        }

        Drawable resolutionBackground = MusicTagUtils.getFileFormatBackground(getApplicationContext(), displayTag);
        fileTypeView.setBackground(resolutionBackground);
        fileTypeView.setText(trimToEmpty(displayTag.getAudioEncoding()).toUpperCase(Locale.US));

        if (!isEmpty(displayTag.getMediaQuality())) {
            request = new ImageRequest.Builder(getApplicationContext())
                    .data(MusicTagUtils.getSourceQualityIcon(getApplicationContext(), displayTag))
                    .crossfade(false)
                    .target(audiophileView)
                    .build();
            imageLoader.enqueue(request);
            audiophileView.setVisibility(View.VISIBLE);
        } else {
            audiophileView.setVisibility(View.GONE);
        }

        artistView.setPaintFlags(artistView.getPaintFlags()| Paint.UNDERLINE_TEXT_FLAG);
        artistView.setOnClickListener(view -> {
            //filter by artist
            Intent resultIntent = new Intent();
            if(criteria!=null) {
                criteria.setFilterType(Constants.FILTER_TYPE_ARTIST);
                criteria.setFilterText(displayTag.getArtist());
                ApplicationUtils.setSearchCriteria(resultIntent,criteria);
            }
            setResult(RESULT_OK, resultIntent);
            finish();
        });
        if(isEmpty(displayTag.getAlbum())) {
            albumView.setText(String.format("[%s]", MusicTagUtils.getDefaultAlbum(displayTag)));
        }else {
            albumView.setText(displayTag.getAlbum());
            albumView.setPaintFlags(albumView.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            albumView.setOnClickListener(view -> {
                // filter by album
                Intent resultIntent = new Intent();
                if (criteria != null) {
                    criteria.setFilterType(Constants.FILTER_TYPE_ALBUM);
                    criteria.setFilterText(displayTag.getAlbum());
                    ApplicationUtils.setSearchCriteria(resultIntent, criteria); //resultIntent.putExtra(Constants.KEY_SEARCH_CRITERIA, criteria);
                }
                setResult(RESULT_OK, resultIntent);
                finish();
            });
        }
        String mediaTypeAndPublisher;
        if((!isEmpty(displayTag.getAlbumArtist()))) {
            mediaTypeAndPublisher = " " +displayTag.getAlbumArtist()+" ";
        }else {
            mediaTypeAndPublisher = " " +StringUtils.SYMBOL_SEP+" ";
        }

        genreView.setText(mediaTypeAndPublisher);

        String matePath = repos.buildCollectionPath(displayTag, true);
        if(!StringUtils.equals(matePath, displayTag.getPath())) {
            newView.setTextColor(getColor(R.color.grey200));
            newView.setBackgroundColor(getColor(R.color.material_color_red_900));
            newPanelView.setVisibility(View.VISIBLE);
        }else {
            newPanelView.setVisibility(View.GONE);
        }

        request = new ImageRequest.Builder(getApplicationContext())
                //.data(MusicTagUtils.getCoverArt(getApplicationContext(), displayTag))
                .data(MusicCoverArtProvider.getUriForMusicTag(displayTag))
                .size(1024,1024)
                .transformations(new ReflectionTransformation())
                .placeholder(R.drawable.progress)
                //.placeholder(R.drawable.no_image0)
                .error(R.drawable.no_image0)
                .target(coverArtView)
                .build();
        imageLoader.enqueue(request);

        // Tag
       // int labelColor = ContextCompat.getColor(getApplicationContext(), R.color.grey400);
        int linkNorTextColor = ContextCompat.getColor(getApplicationContext(), R.color.white);
        int linkPressBgColor = ContextCompat.getColor(getApplicationContext(), R.color.grey200);
        SimplifySpanBuild tagSpan = new SimplifySpanBuild("");
        tagSpan.append(new SpecialTextUnit(StringUtils.SYMBOL_SEP).setTextSize(14).useTextBold());

        tagSpan.appendMultiClickable(new SpecialClickableUnit(tagInfo, (tv, clickableSpan) -> {
                    if(!isEmpty(displayTag.getGenre())) {
                        Intent resultIntent = new Intent();
                        if (criteria != null) {
                            criteria.setFilterType(Constants.FILTER_TYPE_GENRE);
                            criteria.setFilterText(trimToEmpty(displayTag.getGenre()));
                            ApplicationUtils.setSearchCriteria(resultIntent, criteria);
                        }
                        setResult(RESULT_OK, resultIntent);
                        finish();
                    }
                }).setNormalTextColor(linkNorTextColor).setPressBgColor(linkPressBgColor),
                new SpecialTextUnit(isEmpty(displayTag.getGenre())?Constants.UNKNOWN_GENRE:displayTag.getGenre()).setTextSize(14).useTextBold().showUnderline());

        tagSpan.append(new SpecialTextUnit(StringUtils.SYMBOL_SEP).setTextSize(14).useTextBold());
        tagSpan.appendMultiClickable(new SpecialClickableUnit(tagInfo, (tv, clickableSpan) -> {
                    Intent resultIntent = new Intent();
                    if(criteria!=null) {
                        criteria.setFilterType(Constants.FILTER_TYPE_GROUPING);
                        criteria.setFilterText(trimToEmpty(displayTag.getGrouping()));
                        ApplicationUtils.setSearchCriteria(resultIntent,criteria);
                    }
                    setResult(RESULT_OK, resultIntent);
                    finish();
                }).setNormalTextColor(linkNorTextColor).setPressBgColor(linkPressBgColor),
                new SpecialTextUnit(isEmpty(displayTag.getGrouping())?" - ":displayTag.getGrouping()).setTextSize(14).useTextBold().showUnderline());

        tagSpan.append(new SpecialTextUnit(StringUtils.SYMBOL_SEP).setTextSize(14).useTextBold());
        tagInfo.setText(tagSpan.build());

        // Path Info
        String simplePath = displayTag.getSimpleName();
        if(simplePath.contains("/")) {
            simplePath = simplePath.substring(0, simplePath.lastIndexOf("/"));
        }
        SpannableString content = new SpannableString(simplePath);// + mateInd);
        content.setSpan(new UnderlineSpan(), 0, simplePath.length(), 0);
        pathInfo.setText(content);

        pathInfo.setOnClickListener(view -> {
            if (criteria != null && displayTag.getPath() != null) {
                Intent resultIntent = new Intent();
                criteria.setFilterType(Constants.FILTER_TYPE_PATH);
                File file = new File(displayTag.getPath());
                if (!file.isDirectory()) {
                    file = file.getParentFile();
                }
                String filterPath = file.getAbsolutePath() + File.separator;
                criteria.setFilterText(filterPath);
                ApplicationUtils.setSearchCriteria(resultIntent, criteria);
                setResult(RESULT_OK, resultIntent);
                finish();
            }
        });

        // ENC info
        try {
            int metaInfoTextSize = 12; //10
            int encColor = ContextCompat.getColor(getApplicationContext(), R.color.grey100);
            SimplifySpanBuild spannableEnc = new SimplifySpanBuild("");
            spannableEnc.append(new SpecialTextUnit(StringUtils.SEP_LEFT,encColor));

            // bps
            spannableEnc.append(new SpecialTextUnit(StringUtils.formatAudioBitsDepth(displayTag.getAudioBitsDepth()), encColor).setTextSize(metaInfoTextSize));
            spannableEnc.append(new SpecialTextUnit(StringUtils.SYMBOL_ENC_SEP, encColor));

            if(MusicTagUtils.isLossless(displayTag)) {
                spannableEnc.append(new SpecialTextUnit(MusicTagUtils.getDynamicRangeSAsString(displayTag), encColor).setTextSize(metaInfoTextSize));
                spannableEnc.append(new SpecialTextUnit(StringUtils.SYMBOL_ENC_SEP, encColor));
            }
            spannableEnc.append(new SpecialTextUnit(StringUtils.formatAudioBitRate(displayTag.getAudioBitRate()),encColor).setTextSize(metaInfoTextSize));
            spannableEnc.append(new SpecialTextUnit(StringUtils.SYMBOL_ENC_SEP))
                    .append(new SpecialTextUnit(StringUtils.formatDuration(displayTag.getAudioDuration(), true), encColor).setTextSize(metaInfoTextSize))
                    .append(new SpecialTextUnit(StringUtils.SYMBOL_ENC_SEP))
                    .append(new SpecialTextUnit(StringUtils.formatStorageSize(displayTag.getFileSize()), encColor).setTextSize(metaInfoTextSize))
                    .append(new SpecialTextUnit(StringUtils.SEP_RIGHT, encColor));
            encInfo.setText(spannableEnc.build());
        }catch (Exception ex) {
            Log.e(TAG, "updateTitlePanel", ex);
        }
    }

    protected MusicTag buildDisplayTag() {
        if(editItems.isEmpty()) return null;

        MusicTag displayTag = editItems.get(0);
        if(editItems.size()==1) {
            return displayTag.clone();
        }

        displayTag = displayTag.clone();
        for (int i=1;i<editItems.size();i++) {
            MusicTag item = editItems.get(i);
            if(!StringUtils.equals(displayTag.getTitle(), item.getTitle())) {
                displayTag.setTitle(StringUtils.MULTI_VALUES);
            }
            if(!StringUtils.equals(displayTag.getTrack(), item.getTrack())) {
                displayTag.setTrack(StringUtils.MULTI_VALUES);
            }
            if(!StringUtils.equals(displayTag.getAlbum(), item.getAlbum())) {
                displayTag.setAlbum(StringUtils.MULTI_VALUES);
            }
            if(!StringUtils.equals(displayTag.getArtist(), item.getArtist())) {
                displayTag.setArtist(StringUtils.MULTI_VALUES);
            }
            if(!StringUtils.equals(displayTag.getAlbumArtist(), item.getAlbumArtist())) {
                displayTag.setAlbumArtist(StringUtils.MULTI_VALUES);
            }
            if(!StringUtils.equals(displayTag.getGenre(), item.getGenre())) {
                displayTag.setGenre(StringUtils.MULTI_VALUES);
            }
            if(!StringUtils.equals(displayTag.getYear(), item.getYear())) {
                displayTag.setYear(StringUtils.MULTI_VALUES);
            }
            if(!StringUtils.equals(displayTag.getDisc(), item.getDisc())) {
                displayTag.setDisc(StringUtils.MULTI_VALUES);
            }
            if(!StringUtils.equals(displayTag.getComment(), item.getComment())) {
                displayTag.setComment(StringUtils.MULTI_VALUES);
            }
            if(!StringUtils.equals(displayTag.getComposer(), item.getComposer())) {
                displayTag.setComposer(StringUtils.MULTI_VALUES);
            }
            if(!StringUtils.equals(displayTag.getGrouping(), item.getGrouping())) {
                displayTag.setGrouping(StringUtils.MULTI_VALUES);
            }
            if(!StringUtils.equals(displayTag.getMediaType(), item.getMediaType())) {
                displayTag.setMediaType(StringUtils.MULTI_VALUES);
            }
            if(!StringUtils.equals(displayTag.getPublisher(), item.getPublisher())) {
                displayTag.setPublisher(StringUtils.MULTI_VALUES);
            }
        }
        return displayTag;
    }

    public int getStatusBarHeight() {
        int result = 0;
        @SuppressLint({"DiscouragedApi", "InternalInsetResource"}) int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    public void doDeleteMediaItem() {
        String text = "Delete ";
        if(editItems.size()>1) {
            text = text + editItems.size() + " songs?";
        }else {
            text = text + "'"+editItems.get(0).getTitle()+"' song?";
        }

        MaterialAlertDialogBuilder builder =  new MaterialAlertDialogBuilder(TagsActivity.this, R.style.AlertDialogTheme)
                .setIcon(R.drawable.ic_round_delete_forever_24)
                .setTitle("Delete Songs")
                .setMessage(text)
                .setPositiveButton("DELETE", (dialogInterface, i) -> {
                    if(getEditItems().size()==1) {
                        MusicMateExecutors.update(() -> {
                            try {
                                boolean status = repos.deleteMediaItem(getEditItems().get(0));
                                AudioTagEditResultEvent message = new AudioTagEditResultEvent(AudioTagEditResultEvent.ACTION_DELETE, status?Constants.STATUS_SUCCESS:Constants.STATUS_FAIL, getEditItems().get(0));
                                EventBus.getDefault().postSticky(message);
                            } catch (Exception e) {
                                Log.e(TAG, "doDeleteMediaItem", e);
                            }
                        });
                    }else {
                        DeleteAudioFileWorker.startWorker(getApplicationContext(), editItems);
                        dialogInterface.dismiss();
                        Intent resultIntent = new Intent();
                        if (criteria != null) {
                            ApplicationUtils.setSearchCriteria(resultIntent, criteria); //resultIntent.putExtra(Constants.KEY_SEARCH_CRITERIA, criteria);
                        }
                        setResult(RESULT_OK, resultIntent);
                    }
                    if(MusixMateApp.getPlayerInfo() == null || !refreshOnNewSong) {
                        finish(); // back to prev activity
                    }else {
                        startProgressBar();
                    }
                })
                .setNeutralButton("CANCEL", (dialogInterface, i) -> dialogInterface.dismiss());
        AlertDialog alertDialog = builder.create();
        alertDialog.getWindow().setGravity(Gravity.BOTTOM);
       // alertDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        alertDialog.show();
    }

    public void doMoveMediaItem() {
      /*  String text = "Import ";
        if(editItems.size()>1) {
            text = text + editItems.size() + " songs to Music Directory?";
        }else {
            text = text + "'"+editItems.get(0).getTitle()+"' song to Music Directory?";
        }

        MaterialAlertDialogBuilder builder =  new MaterialAlertDialogBuilder(TagsActivity.this, R.style.AlertDialogTheme)
                .setTitle("Import Songs")
                .setIcon(R.drawable.ic_round_move_to_inbox_24)
                .setMessage(text)
                .setPositiveButton("Import", (dialogInterface, i) -> {
                    ImportAudioFileWorker.startWorker(getApplicationContext(), editItems);
                    dialogInterface.dismiss();
                    Intent resultIntent = new Intent();
                    if(criteria!=null) {
                        ApplicationUtils.setSearchCriteria(resultIntent,criteria); //resultIntent.putExtra(Constants.KEY_SEARCH_CRITERIA, criteria);
                    }
                    setResult(RESULT_OK, resultIntent);
                    if(MusixMateApp.getPlayerInfo() == null || !refreshOnNewSong) {
                        finish(); // back to prev activity
                    }else {
                        startProgressBar();
                    }
                })
                .setNeutralButton("CANCEL", (dialogInterface, i) -> dialogInterface.dismiss());
        AlertDialog alertDialog = builder.create();
        alertDialog.getWindow().setGravity(Gravity.BOTTOM);
        alertDialog.show(); */

        ImportAudioFileWorker.startWorker(getApplicationContext(), editItems);
        Intent resultIntent = new Intent();
        if(criteria!=null) {
            ApplicationUtils.setSearchCriteria(resultIntent,criteria);
        }
        setResult(RESULT_OK, resultIntent);
        if(MusixMateApp.getPlayerInfo() == null || !refreshOnNewSong) {
            finish(); // back to prev activity
        }else {
            startProgressBar();
        }
    }

    public void setupMenuEditor(Toolbar.OnMenuItemClickListener listener) {
            toolbar.getMenu().clear();
            toolbar.inflateMenu(R.menu.menu_tag_editor);
            toolbar.setOnMenuItemClickListener(listener);
    }

    public void setupMenuTechnical(Toolbar.OnMenuItemClickListener listener) {
            toolbar.getMenu().clear();
            toolbar.inflateMenu(R.menu.menu_tag_technical);
            toolbar.setOnMenuItemClickListener(listener);
    }
    public void setupMenuPreview(Toolbar.OnMenuItemClickListener listener) {
        toolbar.getMenu().clear();
        toolbar.inflateMenu(R.menu.menu_tag_preview);
        toolbar.setOnMenuItemClickListener(listener);
       /* for(int i=0;i<toolbar.getMenu().size();i++) {
            MenuItem item = toolbar.getMenu().getItem(i);
            if(item.getItemId() == R.id.menu_preview_following_listening) {
                Drawable drawable = item.getIcon();
                if(drawable==null) return;
                if(refreshOnNewSong) {
                    UIUtils.getTintedDrawable(drawable, Color.GREEN);
                }else {
                     UIUtils.getTintedDrawable(drawable, Color.WHITE);
                }
            }
        } */
    }

    class OffSetChangeListener implements AppBarLayout.OnOffsetChangedListener {
        double prevScrollOffset = -1;

        @Override
        public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
            double vScrollOffset = Math.abs(verticalOffset);
            double scale = (1 - (1.0 / appBarLayout.getTotalScrollRange() * (vScrollOffset) * 0.2));
            coverArtView.setScaleX((float) scale);
            coverArtView.setScaleY((float) scale);
            fadeToolbarTitle((1.0 / appBarLayout.getTotalScrollRange() * (vScrollOffset)));

            if(vScrollOffset == prevScrollOffset) return;
            prevScrollOffset = vScrollOffset;
            if (verticalOffset == 0) {
                // fully EXPANDED, on preview screen
                // FIXME: should setup menu hear
                previewState = true;
                setupMenuToolbar();
                buildDisplayTag();
                updateTitlePanel();
            } else if (Math.abs(verticalOffset) == appBarLayout.getTotalScrollRange()) {
                // fully COLLAPSED, on editing screen
                previewState = false;
                setupMenuToolbar();
            }else if (Math.abs(1.0 / appBarLayout.getTotalScrollRange() * vScrollOffset) >= 0.8) {
                // should display menus
               ObjectAnimator colorFade = ObjectAnimator.ofObject(tabLayout,
                        "backgroundColor", /*view attribute name*/
                        new ArgbEvaluator(),
                        toolbar_from_color, /*from color*/
                        toolbar_to_color /*to color*/);
                colorFade.setDuration(2000);
                colorFade.start();
            } else {
                // preview mode
               ObjectAnimator colorFade = ObjectAnimator.ofObject(tabLayout,
                        "backgroundColor", /*view attribute name*/
                        new ArgbEvaluator(),
                        ContextCompat.getColor(getApplicationContext(),R.color.bgColor), /*from color*/
                        ContextCompat.getColor(getApplicationContext(),R.color.bgColor) /*to color*/);
                colorFade.setDuration(2000);
                colorFade.start();
                buildDisplayTag();
                updateTitlePanel();
            }
        }
    }

    private Toolbar.OnMenuItemClickListener getOnMenuItemClickListener() {
            return item -> {
                if (item.getItemId() == R.id.menu_preview_calculate_dr) {
                    doAnalystDRRG();
               /* } else if (item.getItemId() == R.id.menu_preview_following_listening) {
                    if (refreshOnNewSong) {
                        refreshOnNewSong = false;
                        UIUtils.getTintedDrawable(item.getIcon(), Color.WHITE);
                    } else {
                        refreshOnNewSong = true;
                        UIUtils.getTintedDrawable(item.getIcon(), Color.GREEN);
                        MusicTag tag = MusixMateApp.getPlayingSong();
                        if (tag != null) {
                            MusicTagRepository.load(tag);
                            updatePreview(tag);
                        }
                    }*/
                }
                return false;
            };
    }

    private void setupMenuToolbar() {
        if (previewState) {
            setupMenuPreview(getOnMenuItemClickListener());
        }else if(activeFragment!= null) {
            if (activeFragment instanceof TagsEditorFragment) {
                setupMenuEditor(((TagsEditorFragment) activeFragment).getOnMenuItemClickListener());
            } else if (activeFragment instanceof TagsTechnicalFragment) {
                setupMenuTechnical(((TagsTechnicalFragment) activeFragment).getOnMenuItemClickListener());
            }
        }
    }

    private void fadeToolbarTitle(double scale) {
        if (toolbar != null) {
            for (int i = 0; i < toolbar.getChildCount(); i++) {
                if (toolbar.getChildAt(i) instanceof TextView) {
                    TextView title = (TextView) toolbar.getChildAt(i);

                    //You now have the title textView. Do something with it
                    title.setAlpha((float) scale);
                }
            }
        }
    }

    public void startProgressBar(final String label) {
        runOnUiThread(() -> {
            try {
                AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this, R.style.AlertDialogTheme);
                dialogBuilder.setView(R.layout.progress_dialog_layout);
                dialogBuilder.setCancelable(true);
                progressDialog = dialogBuilder.create();
                progressDialog.setCanceledOnTouchOutside(true);
                progressDialog.show();
            }catch (Exception ex) {
                Log.e(TAG, "startProgressBar",ex);
            }
        });
    }

    public void startProgressBar() {
        runOnUiThread(() -> {
            try {
                AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this, R.style.AlertDialogTheme);
                dialogBuilder.setView(R.layout.progress_dialog_layout);
                dialogBuilder.setCancelable(true);
                progressDialog = dialogBuilder.create();
                progressDialog.setCanceledOnTouchOutside(true);
                progressDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                progressDialog.show();
            }catch (Exception ex) {
                Log.e(TAG, "startProgressBar",ex);
            }
        });
    }

    public void stopProgressBar() {
        runOnUiThread(() -> {
            if(progressDialog!=null) {
                progressDialog.dismiss();
                progressDialog = null;
            }
        });
    }
}