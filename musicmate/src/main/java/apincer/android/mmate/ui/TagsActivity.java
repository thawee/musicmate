package apincer.android.mmate.ui;

import static apincer.android.mmate.utils.StringUtils.isEmpty;
import static apincer.android.mmate.utils.StringUtils.trimToEmpty;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
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

import apincer.android.mmate.Constants;
import apincer.android.mmate.Preferences;
import apincer.android.mmate.R;
import apincer.android.mmate.broadcast.AudioTagEditEvent;
import apincer.android.mmate.broadcast.AudioTagEditResultEvent;
import apincer.android.mmate.broadcast.BroadcastData;
import apincer.android.mmate.coil.ReflectionTransformation;
import apincer.android.mmate.fs.FileSystem;
import apincer.android.mmate.fs.MusicCoverArtProvider;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.repository.SearchCriteria;
import apincer.android.mmate.utils.ApplicationUtils;
import apincer.android.mmate.utils.MusicTagUtils;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.mmate.utils.ToastHelper;
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
    private TextView drView;
    private TextView drDBView;
    private TextView rgView;
    private ImageView audiophileView;
    private ImageView resolutionView;
    private View coverArtLayout;
    private View panelLabels;
    private CollapsingToolbarLayout toolBarLayout;
    private int toolbar_from_color;
    private int toolbar_to_color;
    private StateView mStateView;
   // private TagsEditorFragment  tagsEditorFragment = new TagsEditorFragment();
    private volatile boolean isEditing;
    FileRepository repos;
    private Fragment activeFragment;

    @Override
    public void onBackPressed() {
        super.onBackPressed();

        if(criteria!=null) {
            Intent resultIntent = new Intent();
            ApplicationUtils.setSearchCriteria(resultIntent,criteria);
            setResult(RESULT_OK, resultIntent);
        }
        //if(isEditing) {
            // send event notif to list page
        //}
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

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister the listener when the application is paused
        LocalBroadcastManager.getInstance(this).unregisterReceiver(operationReceiver);
        //broadcastHelper.onPause(this);
    }
    @Override
    protected void onResume() {
        super.onResume();
        // Register for the particular broadcast based on ACTION string
        IntentFilter filter = new IntentFilter(BroadcastData.BROADCAST_ACTION);
        LocalBroadcastManager.getInstance(this).registerReceiver(operationReceiver, filter);
        //broadcastHelper.onResume(this);
    }

    private TextView tagInfo;
    private TextView pathInfo;
    private TextView filename;
    private TextView pathDrive;
    //private TextView drView;
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
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        //broadcastHelper = new BroadcastHelper(this);
        repos = FileRepository.newInstance(getApplicationContext());

        coverArtLayout = findViewById(R.id.panel_cover_art_layout);
        panelLabels = findViewById(R.id.panel_labels);
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

    @Subscribe(threadMode = ThreadMode.MAIN,sticky = true)
    public void onMessageEvent(AudioTagEditEvent event) {
        // call from EventBus
        try {
            criteria = event.getSearchCriteria();
            editItems.clear();
            editItems.addAll(event.getItems());
            displayTag = buildDisplayTag();
            if(displayTag != null){
                runOnUiThread(() -> {
                    updateTitlePanel();
                    setUpPageViewer();
                });
            }
        }catch (Exception e) {
            Log.e(TAG, "onMessageEvent", e);
        }
    }

    private void setUpPageViewer() {
        final AppBarLayout appBarLayout = findViewById(R.id.appbar);
        ViewPager2 viewPager = findViewById(R.id.viewpager);

        tabLayout = findViewById(R.id.tabLayout);
        TagsTabLayoutAdapter adapter = new TagsTabLayoutAdapter(getSupportFragmentManager(), getLifecycle());

       // adapter.addNewTab(new TagsMusicBrainzFragment(), "MUSICBRAINZ");
        adapter.addNewTab(new TagsEditorFragment(), "Music information");
        adapter.addNewTab(new TagsTechnicalFragment(), "Technical information");
       // adapter.addNewTab(tagsEditorFragment, "METADATA");
        viewPager.setAdapter(adapter);
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                activeFragment = adapter.fragments.get(position);
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
        tagInfo = findViewById(R.id.panel_tag);
        pathInfo = findViewById(R.id.panel_path);
        pathDrive = findViewById(R.id.panel_path_drive);
        audiophileView = findViewById(R.id.icon_audiophile);
        resolutionView = findViewById(R.id.icon_resolution);
        filename = findViewById(R.id.panel_filename);
        drView = findViewById(R.id.icon_dr);
        drDBView = findViewById(R.id.icon_drDB);
        rgView = findViewById(R.id.icon_replay_gain);
    }

    public void updateTitlePanel() {
        if(getEditItems().size()>1) {
            String title = getString(R.string.title_many, getEditItems().size());
            toolbar.setTitle(title);
            titleView.setText(title);
        }else {
            toolbar.setTitle(MusicTagUtils.getFormattedTitle(getApplicationContext(), displayTag));
            titleView.setText(MusicTagUtils.getFormattedTitle(getApplicationContext(), displayTag));
        }
        artistView.setText(trimToEmpty(displayTag.getArtist())+" ");
       // if(AudioTagUtils.isHiResOrDSD(displayTag) || displayTag.isMQA()) {
            //hiresView.setVisibility(View.VISIBLE);
        //    hiresView.setImageBitmap(AudioTagUtils.getEncodingSamplingRateIcon(getApplicationContext(),displayTag));
        ImageLoader imageLoader = Coil.imageLoader(getApplicationContext());
        ImageRequest request = new ImageRequest.Builder(getApplicationContext())
                .data(MusicTagUtils.getEncResolutionIcon(getApplicationContext(), displayTag))
                .crossfade(false)
                .target(resolutionView)
                .build();
        imageLoader.enqueue(request);

        // Dynamic Range
        Drawable resolutionBackground = MusicTagUtils.getResolutionBackground(getApplicationContext(), displayTag);
        //drView.setText(String.format(Locale.US, "DR%.0f",displayTag.getTrackDR()));
        drView.setText(MusicTagUtils.getTrackDR(displayTag));
        drView.setBackground(resolutionBackground);

        drDBView.setText(MusicTagUtils.getMeasuredDR(displayTag) +" dB");
        drDBView.setBackground(resolutionBackground);

        rgView.setText(MusicTagUtils.getTrackReplayGainString(displayTag));
        rgView.setBackground(resolutionBackground);

        // Track Replay Gain
        //resolutionBackground = MusicTagUtils.getResolutionBackground(getApplicationContext(), displayTag);
        //trackRGView.setText(String.format(Locale.US, "G%.2f",displayTag.getTrackRG()));
        //trackRGView.setBackground(resolutionBackground);

        //audiophileView.setVisibility(displayTag.isAudiophile()?View.VISIBLE:View.GONE);
        //if (displayTag.isAudiophile()) {
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

        if(displayTag.isDSD()) {
            drView.setVisibility(View.GONE);
            rgView.setVisibility(View.GONE);
        }else {
            drView.setVisibility(View.VISIBLE);
            rgView.setVisibility(View.VISIBLE);
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
        mediaTypeAndPublisher = isEmpty(displayTag.getMediaType())?Constants.UNKNOWN_MEDIA_TYPE:displayTag.getMediaType();
        mediaTypeAndPublisher += " "+StringUtils.SYMBOL_SEP+" ";
        mediaTypeAndPublisher += isEmpty(displayTag.getPublisher())?Constants.UNKNOWN_PUBLISHER:displayTag.getPublisher();
        genreView.setText(mediaTypeAndPublisher);

        filename.setText("["+FileSystem.getFilename(displayTag.getPath())+"]");
        String matePath = repos.buildCollectionPath(displayTag, true);
        String sid = displayTag.getStorageId();
       // String mateInd = "";
        if(!StringUtils.equals(matePath, displayTag.getPath())) {
            //mateInd = " "+StringUtils.SYMBOL_ATTENTION;
            pathDrive.setTextColor(getColor(R.color.warningColor));
        }
        String simplePath = displayTag.getSimpleName();
        if(simplePath.contains("/")) {
            simplePath = simplePath.substring(0, simplePath.lastIndexOf("/"));
        }
        SpannableString content = new SpannableString(simplePath);// + mateInd);
        content.setSpan(new UnderlineSpan(), 0, simplePath.length(), 0);
        pathInfo.setText(content);
        pathDrive.setText(FileSystem.getStorageName(sid));

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

        request = new ImageRequest.Builder(getApplicationContext())
                //.data(MusicTagUtils.getCoverArt(getApplicationContext(), displayTag))
                .data(MusicCoverArtProvider.getUriForMusicTag(displayTag))
                .size(1024,1024)
                .transformations(new ReflectionTransformation())
                .placeholder(R.drawable.progress)
                .error(R.drawable.ic_broken_image_black_24dp)
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
            Intent resultIntent = new Intent();
            if(criteria!=null) {
                criteria.setFilterType(Constants.FILTER_TYPE_GROUPING);
                criteria.setFilterText(trimToEmpty(displayTag.getGrouping()));
                ApplicationUtils.setSearchCriteria(resultIntent,criteria);
            }
            setResult(RESULT_OK, resultIntent);
            finish();
        }).setNormalTextColor(linkNorTextColor).setPressBgColor(linkPressBgColor),
                new SpecialTextUnit(isEmpty(displayTag.getGrouping())?" - ":displayTag.getGrouping()).setTextSize(14).useTextBold().showUnderline())
                .append(new SpecialTextUnit(StringUtils.SYMBOL_SEP).setTextSize(14).useTextBold())
                .appendMultiClickable(new SpecialClickableUnit(tagInfo, (tv, clickableSpan) -> {
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

        if(!isEmpty(displayTag.getAlbumArtist())) {
            tagSpan.append(new SpecialTextUnit(StringUtils.SYMBOL_SEP).setTextSize(14).useTextBold())
                    .appendMultiClickable(new SpecialClickableUnit(tagInfo, (tv, clickableSpan) -> {
                        if (!isEmpty(displayTag.getAlbumArtist())) {
                            Intent resultIntent = new Intent();
                            if (criteria != null) {
                                criteria.setFilterType(Constants.FILTER_TYPE_PUBLISHER);
                                criteria.setFilterText(trimToEmpty(displayTag.getAlbumArtist()));
                                ApplicationUtils.setSearchCriteria(resultIntent, criteria);
                            }
                            setResult(RESULT_OK, resultIntent);
                            finish();
                        }
                    }).setNormalTextColor(linkNorTextColor).setPressBgColor(linkPressBgColor),
                    new SpecialTextUnit(isEmpty(displayTag.getAlbumArtist()) ? " - " : displayTag.getAlbumArtist()).setTextSize(14).useTextBold().showUnderline());
        }
        tagSpan.append(new SpecialTextUnit(StringUtils.SYMBOL_SEP).setTextSize(14).useTextBold());
        tagInfo.setText(tagSpan.build());

        // ENC info
        int encColor = ContextCompat.getColor(getApplicationContext(), R.color.grey100);
        SimplifySpanBuild spannableEnc = new SimplifySpanBuild("");
        spannableEnc.append(new SpecialTextUnit(StringUtils.SEP_LEFT,encColor).setTextSize(10));

        spannableEnc.append(new SpecialTextUnit(trimToEmpty(displayTag.getFileFormat()).toUpperCase(Locale.US),encColor).setTextSize(10))
                .append(new SpecialTextUnit(StringUtils.SYMBOL_ENC_SEP,encColor).setTextSize(10));

        try {
            spannableEnc.append(new SpecialTextUnit(StringUtils.formatAudioBitsDepth(displayTag.getAudioBitsDepth()), encColor).setTextSize(10))
                    .append(new SpecialTextUnit(StringUtils.SYMBOL_ENC_SEP).setTextSize(10))
                    .append(new SpecialTextUnit(StringUtils.formatAudioSampleRate(displayTag.getAudioSampleRate(), true), encColor).setTextSize(10))
                    .append(new SpecialTextUnit(StringUtils.SYMBOL_ENC_SEP).setTextSize(10))
                    .append(new SpecialTextUnit(StringUtils.formatAudioBitRate(displayTag.getAudioBitRate()), encColor).setTextSize(10))
                    // .append(new SpecialTextUnit(mqaSampleRate,encColor).setTextSize(10))
                    .append(new SpecialTextUnit(StringUtils.SYMBOL_ENC_SEP).setTextSize(10))
                    .append(new SpecialTextUnit(StringUtils.formatChannels(displayTag.getAudioChannels()),encColor).setTextSize(10))
                    .append(new SpecialTextUnit(StringUtils.SYMBOL_ENC_SEP).setTextSize(10))
                    .append(new SpecialTextUnit(StringUtils.formatDuration(displayTag.getAudioDuration(), true), encColor).setTextSize(10))
                    .append(new SpecialTextUnit(StringUtils.SYMBOL_ENC_SEP).setTextSize(10))
                    .append(new SpecialTextUnit(StringUtils.formatStorageSize(displayTag.getFileSize()), encColor).setTextSize(10))
                    .append(new SpecialTextUnit(StringUtils.SEP_RIGHT, encColor).setTextSize(10));
            encInfo.setText(spannableEnc.build());
        }catch (Exception ex) {
            Log.e(TAG, "updateTitlePanel", ex);
        }
    }

    protected MusicTag buildDisplayTag() {
        if(editItems.isEmpty()) return null;

        MusicTag displayTag = editItems.get(0);
        if(editItems.size()==1) {
           // repos.readAudioTagFromFile(displayTag);
           // audio file size = bit rate * duration of audio in seconds * number of channels
            //bit rate = bit depth * sample rate
           // long calSize = MusicTagUtils.calculateFileSize(displayTag);
           // long size = displayTag.getFileSize();
          //s  boolean valid = MusicTagUtils.isFileSizeValid(displayTag);

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
                        // MediaItemIntentService.startService(getApplicationContext(), Constants.COMMAND_DELETE,editItems);
                        DeleteAudioFileWorker.startWorker(getApplicationContext(), editItems);
                        dialogInterface.dismiss();
                        Intent resultIntent = new Intent();
                        if (criteria != null) {
                            ApplicationUtils.setSearchCriteria(resultIntent, criteria); //resultIntent.putExtra(Constants.KEY_SEARCH_CRITERIA, criteria);
                        }
                        setResult(RESULT_OK, resultIntent);
                    }
                    finish(); // back to prev activity
                })
                .setNeutralButton("CANCEL", (dialogInterface, i) -> dialogInterface.dismiss());
        AlertDialog alertDialog = builder.create();
        alertDialog.getWindow().setGravity(Gravity.BOTTOM);
        alertDialog.show();
    }

    public void doMoveMediaItem() {
        String text = "Import ";
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
                    finish(); // back to prev activity
                })
                .setNeutralButton("CANCEL", (dialogInterface, i) -> dialogInterface.dismiss());
        AlertDialog alertDialog = builder.create();
        alertDialog.getWindow().setGravity(Gravity.BOTTOM);
        alertDialog.show();
    }

    public void onPlaying(MusicTag tag) {
        if((!isEditing) && Preferences.isFollowNowPlaying(getApplicationContext())) {
            try {
                editItems.clear();
                editItems.add(tag);
                displayTag = buildDisplayTag();
                if(displayTag==null) return;
                updateTitlePanel();
                setUpPageViewer();
            } catch (Exception e) {
                Log.e(TAG, "onPlaying", e );
            }
        }
    }

    public void setupEditorManu(Toolbar.OnMenuItemClickListener listener) {
        if(!"Music".equalsIgnoreCase(String.valueOf(toolbar.getTitle()))) {
            toolbar.getMenu().clear();
            toolbar.setTitle("Music");
            toolbar.inflateMenu(R.menu.menu_tag_editor);
            toolbar.setOnMenuItemClickListener(listener);
        }
    }

    public void setupTechnicalManu(Toolbar.OnMenuItemClickListener listener) {
        if(!"Technical".equalsIgnoreCase(String.valueOf(toolbar.getTitle()))) {
            toolbar.getMenu().clear();
            toolbar.setTitle("Technical");
            toolbar.inflateMenu(R.menu.menu_tag_technical);
            toolbar.setOnMenuItemClickListener(listener);
        }
    }

    class OffSetChangeListener implements AppBarLayout.OnOffsetChangedListener {

        @Override
        public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
            double vScrollOffset = Math.abs(verticalOffset);
            double scale = (1 - (1.0 / appBarLayout.getTotalScrollRange() * (vScrollOffset) * 0.2));
            coverArtView.setScaleX((float) scale);
            coverArtView.setScaleY((float) scale);
            fadeToolbarTitle((1.0 / appBarLayout.getTotalScrollRange() * (vScrollOffset)));

            if (verticalOffset == 0) {
                // fully EXPANDED, on preview screen
                toolbar.getMenu().clear();
                buildDisplayTag();
                updateTitlePanel();
            } else if (Math.abs(verticalOffset) == appBarLayout.getTotalScrollRange()) {
                // fully COLLAPSED, on editing screen
                setupToolBarMenu();
            }else if (Math.abs(1.0 / appBarLayout.getTotalScrollRange() * vScrollOffset) >= 0.8) {
                //edit mode
                isEditing = true;
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

    private void setupToolBarMenu() {
        if(activeFragment!= null) {
            if (activeFragment instanceof TagsEditorFragment) {
                setupEditorManu(((TagsEditorFragment) activeFragment).getOnMenuItemClickListener());
            } else if (activeFragment instanceof TagsTechnicalFragment) {
                setupTechnicalManu(((TagsTechnicalFragment) activeFragment).getOnMenuItemClickListener());
            } else {
                clearToolBarMenu();
            }
        }
    }

    private void clearToolBarMenu() {
        toolbar.setTitle("");
        toolbar.getMenu().clear();
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

    private final BroadcastReceiver operationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
                BroadcastData broadcastData = BroadcastData.getBroadcastData(intent);
                if (broadcastData != null) {
                    if (broadcastData.getAction() == BroadcastData.Action.PLAYING) {
                        MusicTag tag = broadcastData.getTagInfo();
                        onPlaying(tag);
                    }else {
                        ToastHelper.showBroadcastData(TagsActivity.this, mStateView, broadcastData);
                    }
                }
        }
    };
}