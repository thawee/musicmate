package apincer.android.mmate.ui;

import static apincer.android.mmate.Constants.SRC_NONE;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.UnderlineSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
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

import apincer.android.mmate.Constants;
import apincer.android.mmate.Preferences;
import apincer.android.mmate.R;
import apincer.android.mmate.broadcast.AudioTagEditEvent;
import apincer.android.mmate.broadcast.BroadcastData;
import apincer.android.mmate.coil.ReflectionTransformation;
import apincer.android.mmate.fs.FileSystem;
import apincer.android.mmate.objectbox.MusicTag;
import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.repository.SearchCriteria;
import apincer.android.mmate.utils.ApplicationUtils;
import apincer.android.mmate.utils.MusicTagUtils;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.mmate.utils.ToastHelper;
import apincer.android.mmate.utils.UIUtils;
import apincer.android.mmate.work.DeleteAudioFileWorker;
import apincer.android.mmate.work.ImportAudioFileWorker;
import cn.iwgang.simplifyspan.SimplifySpanBuild;
import cn.iwgang.simplifyspan.unit.SpecialClickableUnit;
import cn.iwgang.simplifyspan.unit.SpecialTextUnit;
import coil.Coil;
import coil.ImageLoader;
import coil.request.ImageRequest;
import me.zhanghai.android.materialratingbar.MaterialRatingBar;
import sakout.mehdi.StateViews.StateView;
import timber.log.Timber;

public class TagsActivity extends AppCompatActivity {
    private static final String UNKNOWN_GENRE = "Unknown Genre"; //implements Callback {
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
  //  private TextView groupingView;
  //  private TextView genreView;
    private ImageView audiophileView;
    private ImageView hiresView;
   // private ImageView mqaView;
    //private ImageView dsdView;
    private ImageView encResView;
    private MaterialRatingBar ratingView;
   // private TextView qualityView;
    private View coverArtLayout;
    private View panelLabels;
    private CollapsingToolbarLayout toolBarLayout;
    private int toolbar_from_color;
    private int toolbar_to_color;
    private StateView mStateView;
    private TagsEditorFragment  tagsEditorFragment = new TagsEditorFragment();
    private volatile boolean isEditing;
    //private BroadcastHelper broadcastHelper;
    FileRepository repos;// = AudioFileRepository.newInstance(getApplication());

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
    private TextView pathDrive;
    //private ImageView pathIcon;
    private SearchCriteria criteria;

    public List<MusicTag> getEditItems() {
        return editItems;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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

    /*
    private void doStartFFMpeg( ) {
        mStateView.displayLoadingState();
        final int total =  getEditItems().size();
        final int[] count = {0};

        for(AudioTag tag: getEditItems()) {
            if(Constants.MEDIA_ENC_WAVE.equalsIgnoreCase(tag.getAudioEncoding()))  {
                String wavePath = tag.getPath();
                String filePath = FileUtils.removeExtension(tag.getPath());
                String flacPath = filePath+".flac";
                String cmd = "-i \""+wavePath+"\" \""+flacPath+"\"";
                FFmpegKit.executeAsync(cmd, session -> {
                    repos.scanFileAndSaveTag(new File(flacPath));
                    runOnUiThread(() -> {
                        String message = "Convert '"+tag.getTitle()+"' completed in " + session.getDuration() +" ms.";
                        ToastHelper.showActionMessage(TagsActivity.this, mStateView, Constants.STATUS_SUCCESS, message);
                        if(total == count[0]) {
                            mStateView.hideStates();
                        }
                    });
                    count[0]++;
                });
            }
        }
    } */

    @Subscribe(threadMode = ThreadMode.ASYNC,sticky = true)
    public void onMessageEvent(AudioTagEditEvent event) {
        // call from EventBus
        try {
            criteria = event.getSearchCriteria();
            editItems.clear();
            editItems.addAll(event.getItems());
            displayTag = buildDisplayTag();
            runOnUiThread(() -> {
                updateTitlePanel();
                setUpPageViewer();
            });
        }catch (Exception e) {
            Timber.e(e);
        }
    }

    private void setUpPageViewer() {

        final AppBarLayout appBarLayout = findViewById(R.id.appbar);
        ViewPager2 viewPager = findViewById(R.id.viewpager);

        tabLayout = findViewById(R.id.tabLayout);
        TagsTabLayoutAdapter adapter = new TagsTabLayoutAdapter(getSupportFragmentManager(), getLifecycle());
        adapter.addNewTab(tagsEditorFragment, "Editor");
        adapter.addNewTab(new TagsMusicBrainzFragment(), "MusicBrainz");
        viewPager.setAdapter(adapter);

        TabLayoutMediator tabLayoutMediator = new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> tab.setText(adapter.getPageTitle(position)));
        tabLayoutMediator.attach();

        appBarLayout.addOnOffsetChangedListener(new OffSetChangeListener());

        findViewById(R.id.btnEdit).setOnClickListener(v -> appBarLayout.setExpanded(false, true));
        findViewById(R.id.btnDelete).setOnClickListener(v -> doDeleteMediaItem());
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
        hiresView = findViewById(R.id.icon_hires);
        encResView = findViewById(R.id.icon_loudness);
        ratingView = findViewById(R.id.icon_rating);
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
        artistView.setText(StringUtils.trimToEmpty(displayTag.getArtist())+" ");
       // if(AudioTagUtils.isHiResOrDSD(displayTag) || displayTag.isMQA()) {
            //hiresView.setVisibility(View.VISIBLE);
        //    hiresView.setImageBitmap(AudioTagUtils.getEncodingSamplingRateIcon(getApplicationContext(),displayTag));
        ImageLoader imageLoader = Coil.imageLoader(getApplicationContext());
        ImageRequest request = new ImageRequest.Builder(getApplicationContext())
                .data(MusicTagUtils.getEncResolutionIcon(getApplicationContext(), displayTag))
                .crossfade(false)
                .target(hiresView)
                .build();
        imageLoader.enqueue(request);

        //audiophileView.setVisibility(displayTag.isAudiophile()?View.VISIBLE:View.GONE);
        //if (displayTag.isAudiophile()) {
        if (!StringUtils.isEmpty(displayTag.getSourceQuality())) {
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
            encResView.setVisibility(View.GONE);
        }else {
            encResView.setImageBitmap(MusicTagUtils.createLoudnessIcon(getApplicationContext(), displayTag));
            /*request = new ImageRequest.Builder(getApplicationContext())
                    .data(AudioTagUtils.getCachedLoudnessIcon(getApplicationContext(), displayTag))
                    .crossfade(false)
                    .target(encResView)
                    .build();
            imageLoader.enqueue(request);*/
            encResView.setVisibility(View.VISIBLE);
        }
        ratingView.setRating(displayTag.getRating());
        ratingView.setFocusable(false);
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
        if(StringUtils.isEmpty(displayTag.getAlbum())) {
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
        if(!StringUtils.isEmpty(displayTag.getGenre())) {
            genreView.setText(displayTag.getGenre());
            genreView.setPaintFlags(genreView.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            genreView.setOnClickListener(view -> {
                // filter by genre
                Intent resultIntent = new Intent();
                if (criteria != null) {
                    criteria.setFilterType(Constants.FILTER_TYPE_GENRE);
                    criteria.setFilterText(displayTag.getGenre());
                    ApplicationUtils.setSearchCriteria(resultIntent,criteria); //resultIntent.putExtra(Constants.KEY_SEARCH_CRITERIA, criteria);
                }
                setResult(RESULT_OK, resultIntent);
                finish();
            });
        }else {
            genreView.setText(UNKNOWN_GENRE);
        }

        String matePath = repos.buildCollectionPath(displayTag);
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
        //pathInfo.setText(simplePath + mateInd);

       // int bgColor = getApplication().getColor(R.color.grey600);//Color.TRANSPARENT;
       // int textColor = getApplication().getColor(R.color.grey200);
       // int borderColor = getApplication().getColor(R.color.black_transparent_40);
       /* if(StorageId.PRIMARY.equals(sid)) {
           // Bitmap bpm = AudioTagUtils.createBitmapFromTextSquare(getApplicationContext(),48,24," PH ",textColor,borderColor,bgColor);
           // pathInfo.setCompoundDrawablesWithIntrinsicBounds(BitmapHelper.bitmapToDrawable(getApplication(),bpm),null,null,null);
        //    pathInfo.setCompoundDrawablesWithIntrinsicBounds(ContextCompat.getDrawable(getApplicationContext(),R.drawable.ic_memory_white_24dp),null,null,null);
           // pathInfo.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_round_memory_16,0,0,0);
            pathIcon.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_round_memory_16));
        }else {
           // Bitmap bpm = AudioTagUtils.createBitmapFromTextSquare(getApplicationContext(),48,24," SD ",textColor,borderColor,bgColor);
           // pathInfo.setCompoundDrawablesWithIntrinsicBounds(BitmapHelper.bitmapToDrawable(getApplication(),bpm),null,null,null);
           // pathInfo.setCompoundDrawablesWithIntrinsicBounds(ContextCompat.getDrawable(getApplicationContext(),R.drawable.ic_sd_storage_white_24dp),null,null,null);
           // pathInfo.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_round_sd_card_16,0,0,0);
            pathIcon.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_round_sd_card_16));
        } */

       // pathInfo.setPaintFlags(pathInfo.getPaintFlags()| Paint.UNDERLINE_TEXT_FLAG);

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
                ApplicationUtils.setSearchCriteria(resultIntent, criteria); //resultIntent.putExtra(Constants.KEY_SEARCH_CRITERIA, criteria);
                setResult(RESULT_OK, resultIntent);
                finish();
            }
        });

        request = new ImageRequest.Builder(getApplicationContext())
                //.data(EmbedCoverArtProvider.getUriForMediaItem(displayTag))
                .data(MusicTagUtils.getCoverArt(getApplicationContext(), displayTag))
                .size(1024,1024)
                .transformations(new ReflectionTransformation())
                .placeholder(R.drawable.progress)
                .error(R.drawable.ic_broken_image_black_24dp)
                .target(coverArtView)
              /*  .target(new Target() {
                    @Override
                    public void onStart(@Nullable Drawable drawable) {

                    }

                    @Override
                    public void onError(@Nullable Drawable drawable) {
                        coverArtView.setImageDrawable(drawable);
                    }

                    @Override
                    public void onSuccess(@NonNull Drawable drawable) {
                       // coverArtView.setImageDrawable(drawable);
                        try {
                           // Bitmap bmp = null;
                            Bitmap bmp = BitmapHelper.drawableToBitmap(drawable);
                            Bitmap fullBitmap = BitmapHelper.applyReflection(bmp);
                           // if(fullBitmap.getAllocationByteCount() > 1024) {
                            //    fullBitmap = BitmapHelper.scaleBitmap(fullBitmap, 1024, 1024);
                           // }
                            coverArtView.setImageBitmap(fullBitmap);
                            Palette.from(bmp).generate(palette -> {
                                int vibrant = palette.getVibrantColor(0x000000); // <=== color you want
                                int vibrantLight = palette.getLightVibrantColor(0x000000);
                                int vibrantDark = palette.getDarkVibrantColor(0x000000);
                                int muted = palette.getMutedColor(0x000000);
                                int mutedLight = palette.getLightMutedColor(0x000000);
                                int mutedDark = palette.getDarkMutedColor(0x000000);

                                int frameColor = palette.getDominantColor(getColor(R.color.bgColor));
                                int panelColor = palette.getVibrantColor(getColor(R.color.bgColor));
                                toolbar_from_color = ColorUtils.TranslateDark(frameColor,100);
                                toolbar_to_color = ColorUtils.TranslateDark(frameColor,100);; //frameColor;
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        panelLabels.setBackgroundColor(ColorUtils.TranslateLight(panelColor, 400));
                                        toolBarLayout.setContentScrimColor(ColorUtils.TranslateDark(frameColor,100));
                                        coverArtLayout.setBackgroundColor(ColorUtils.TranslateDark(panelColor, 400));
                                    }
                                });
                            });
                        }catch (Exception ex) {
                            Timber.e(ex);
                        }
                    } */
              //  })
                .build();
        imageLoader.enqueue(request);

        // Tag
        int labelColor = ContextCompat.getColor(getApplicationContext(), R.color.grey400);
        int linkNorTextColor = ContextCompat.getColor(getApplicationContext(), R.color.white);
        int linkPressBgColor = ContextCompat.getColor(getApplicationContext(), R.color.grey200);
        SimplifySpanBuild tagSpan = new SimplifySpanBuild("");
       // tagSpan.append(new SpecialLabelUnit("Grouping:", labelColor, UIUtils.sp2px(getApplication(),10), Color.TRANSPARENT).setPadding(5).setPaddingLeft(10).setPaddingRight(10).setGravity(SpecialGravity.CENTER))
                //.append(new SpecialTextUnit(StringUtils.isEmpty(displayTag.getGrouping())?"N/A":displayTag.getGrouping()).setTextSize(14).useTextBold().setGravity(tagInfo.getPaint(), SpecialGravity.CENTER))
        tagSpan.appendMultiClickable(new SpecialClickableUnit(tagInfo, (tv, clickableSpan) -> {
            Intent resultIntent = new Intent();
            if(criteria!=null) {
                criteria.setFilterType(Constants.FILTER_TYPE_GROUPING);
                criteria.setFilterText(StringUtils.trimToEmpty(displayTag.getGrouping()));
                ApplicationUtils.setSearchCriteria(resultIntent,criteria);
            }
            setResult(RESULT_OK, resultIntent);
            finish();
        }).setNormalTextColor(linkNorTextColor).setPressBgColor(linkPressBgColor),
                new SpecialTextUnit(StringUtils.isEmpty(displayTag.getGrouping())?" - ":displayTag.getGrouping()).setTextSize(14).useTextBold().showUnderline())
               // .append(new SpecialLabelUnit(":Grouping"+StringUtils.ARTIST_SEP+"Genre:", labelColor, UIUtils.sp2px(getApplication(),10), Color.TRANSPARENT).setPadding(5).setPaddingLeft(10).setPaddingRight(10).setGravity(SpecialGravity.BOTTOM))
               //  .append(new SpecialLabelUnit(":Grouping | Genre:", labelColor, UIUtils.sp2px(getApplication(),10), Color.TRANSPARENT).setPadding(5).setPaddingLeft(10).setPaddingRight(10).setGravity(SpecialGravity.BOTTOM))
               // .append(new SpecialTextUnit("   |   ").setTextSize(14).useTextBold())
              //  .append(new SpecialTextUnit("    \u20df    ").setTextSize(14).useTextBold())
                .append(new SpecialTextUnit(StringUtils.SYMBOL_SEP).setTextSize(14).useTextBold())
               // .append(StringUtils.ARTIST_SEP)
               // .append(new SpecialLabelUnit("Genre>", labelColor, UIUtils.sp2px(getApplication(),10), Color.TRANSPARENT).setPadding(5).setPaddingLeft(10).setPaddingRight(10).setGravity(SpecialGravity.CENTER))
                //.append(new SpecialTextUnit(StringUtils.isEmpty(displayTag.getGenre())?"N/A":displayTag.getGenre()).setTextSize(14).useTextBold().setGravity(tagInfo.getPaint(), SpecialGravity.CENTER));
               /* .appendMultiClickable(new SpecialClickableUnit(tagInfo, new OnClickableSpanListener() {
                            @Override
                            public void onClick(TextView tv, CustomClickableSpan clickableSpan) {
                                Intent resultIntent = new Intent();
                                if(criteria!=null) {
                                    criteria.setFilterType(Constants.FILTER_TYPE_GENRE);
                                    criteria.setFilterText(StringUtils.trimToEmpty(displayTag.getGenre()));
                                    ApplicationUtils.setSearchCriteria(resultIntent,criteria);
                                }
                                setResult(RESULT_OK, resultIntent);
                                finish();
                            }
                        }).setNormalTextColor(linkNorTextColor).setPressBgColor(linkPressBgColor),
                        new SpecialTextUnit(StringUtils.isEmpty(displayTag.getGenre())?" - ":displayTag.getGenre()).setTextSize(14).useTextBold().showUnderline()); */
                .appendMultiClickable(new SpecialClickableUnit(tagInfo, (tv, clickableSpan) -> {
                    if(!StringUtils.isEmpty(displayTag.getAlbumArtist())) {
                        Intent resultIntent = new Intent();
                        if (criteria != null) {
                            criteria.setFilterType(Constants.FILTER_TYPE_ALBUM_ARTIST);
                            criteria.setFilterText(StringUtils.trimToEmpty(displayTag.getAlbumArtist()));
                            ApplicationUtils.setSearchCriteria(resultIntent, criteria);
                        }
                        setResult(RESULT_OK, resultIntent);
                        finish();
                    }
                }).setNormalTextColor(linkNorTextColor).setPressBgColor(linkPressBgColor),
                    new SpecialTextUnit(MusicTagUtils.getAlbumArtistOrArtist(displayTag)).setTextSize(14).useTextBold().showUnderline());

        tagInfo.setText(tagSpan.build());

        // ENC info
        int encColor = ContextCompat.getColor(getApplicationContext(), R.color.grey100);
        SimplifySpanBuild spannableEnc = new SimplifySpanBuild("");
        spannableEnc.append(new SpecialTextUnit(StringUtils.SEP_LEFT,encColor).setTextSize(10));

        spannableEnc.append(new SpecialTextUnit(displayTag.getAudioEncoding(),encColor).setTextSize(10))
                .append(new SpecialTextUnit(StringUtils.SYMBOL_ENC_SEP,encColor).setTextSize(10));

        if((!StringUtils.isEmpty(displayTag.getSource())) && !SRC_NONE.equals(displayTag.getSource())) {
            spannableEnc.append(new SpecialTextUnit(displayTag.getSource(),encColor).setTextSize(10))
                    .append(new SpecialTextUnit(StringUtils.SYMBOL_ENC_SEP,encColor).setTextSize(10));
        }

        spannableEnc.append(new SpecialTextUnit(StringUtils.getFormatedBitsPerSample(displayTag.getAudioBitsPerSample()),encColor).setTextSize(10))
                .append(new SpecialTextUnit(StringUtils.SYMBOL_ENC_SEP).setTextSize(10))
                .append(new SpecialTextUnit(StringUtils.getFormatedAudioSampleRate(displayTag.getAudioSampleRate(),true),encColor).setTextSize(10))
                .append(new SpecialTextUnit(StringUtils.SYMBOL_ENC_SEP).setTextSize(10))
                .append(new SpecialTextUnit(StringUtils.getFormatedAudioBitRate(displayTag.getAudioBitRate()),encColor).setTextSize(10))
               // .append(new SpecialTextUnit(mqaSampleRate,encColor).setTextSize(10))
                //.append(new SpecialTextUnit(StringUtils.SYMBOL_ENC_SEP).setTextSize(10)) //.append(new SpecialLabelUnit(" | ", Color.GRAY, sp2px(10), Color.TRANSPARENT).showBorder(Color.BLACK, 2).setPaddingLeft(10).setPaddingRight(10).setGravity(SpecialGravity.CENTER))
                //.append(new SpecialTextUnit(StringUtils.getFormatedChannels(displayTag.getAudioChannels()),encColor).setTextSize(10))
                .append(new SpecialTextUnit(StringUtils.SYMBOL_ENC_SEP).setTextSize(10)) //.append(new SpecialLabelUnit(" | ", Color.GRAY, sp2px(10), Color.TRANSPARENT).showBorder(Color.BLACK, 2).setPaddingLeft(10).setPaddingRight(10).setGravity(SpecialGravity.CENTER))
                .append(new SpecialTextUnit(StringUtils.formatDuration(displayTag.getAudioDuration(),true),encColor).setTextSize(10))
                .append(new SpecialTextUnit(StringUtils.SYMBOL_ENC_SEP).setTextSize(10)) //.append(new SpecialLabelUnit(" | ", Color.GRAY, sp2px(10), Color.TRANSPARENT).showBorder(Color.BLACK, 2).setPaddingLeft(10).setPaddingRight(10).setGravity(SpecialGravity.CENTER))
                .append(new SpecialTextUnit(StringUtils.formatStorageSize(displayTag.getFileSize()),encColor).setTextSize(10))
                .append(new SpecialTextUnit(StringUtils.SEP_RIGHT,encColor).setTextSize(10));
        encInfo.setText(spannableEnc.build());
    }

    protected MusicTag buildDisplayTag() {
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
            if(!StringUtils.equals(displayTag.getSource(), item.getSource())) {
                displayTag.setSource("Unknown");
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
                   // MediaItemIntentService.startService(getApplicationContext(), Constants.COMMAND_DELETE,editItems);
                    DeleteAudioFileWorker.startWorker(getApplicationContext(),editItems);
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
                updateTitlePanel();
                setUpPageViewer();
            } catch (Exception e) {
                Timber.e(e);
            }
        }
    }
/*
    public void doPreviewCoverArt(File coverArtFile) {
        ImageLoader imageLoader = Coil.imageLoader(getApplicationContext());
        ImageRequest request = new ImageRequest.Builder(getApplicationContext())
                .data(coverArtFile)
                .placeholder(R.drawable.progress)
                .error(R.drawable.ic_broken_image_black_24dp)
                .target(coverArtView)
                .build();
        imageLoader.enqueue(request);
    }

    public void doPreviewCoverArt(Uri coverArtUri) {
        ImageLoader imageLoader = Coil.imageLoader(getApplicationContext());
        ImageRequest request = new ImageRequest.Builder(getApplicationContext())
                .data(coverArtUri)
                .placeholder(R.drawable.progress)
                .error(R.drawable.ic_broken_image_black_24dp)
                .target(coverArtView)
                .build();
        imageLoader.enqueue(request);
    } */

    class OffSetChangeListener implements AppBarLayout.OnOffsetChangedListener {

        @Override
        public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {

            double vScrollOffset = Math.abs(verticalOffset);
            double scale = (1 - (1.0 / appBarLayout.getTotalScrollRange() * (vScrollOffset) * 0.2));
            coverArtView.setScaleX((float) scale);
            coverArtView.setScaleY((float) scale);
            fadeToolbarTitle((1.0 / appBarLayout.getTotalScrollRange() * (vScrollOffset)));

            if (Math.abs(1.0 / appBarLayout.getTotalScrollRange() * vScrollOffset) >= 0.8) {
                //edit mode
                isEditing = true;
//                tabLayout.setBackgroundColor(getResources().getColor(R.color.colorPrimary));
                ObjectAnimator colorFade = ObjectAnimator.ofObject(tabLayout,
                        "backgroundColor" /*view attribute name*/, new ArgbEvaluator(),
                        toolbar_from_color
                        /*from color*/, toolbar_to_color/*to color*/);
                colorFade.setDuration(2000);
                colorFade.start();
            } else {
                // view mode
                //buildDisplayTag(false);
                //updateTitlePanel();
//                tabLayout.setBackgroundColor(getResources().getColor(R.color.bgColor));
                ObjectAnimator colorFade = ObjectAnimator.ofObject(tabLayout,
                        "backgroundColor" /*view attribute name*/, new ArgbEvaluator(),
                        ContextCompat.getColor(getApplicationContext(),R.color.bgColor)
                        /*from color*/, ContextCompat.getColor(getApplicationContext(),R.color.bgColor)/*to color*/);
                colorFade.setDuration(2000);
                colorFade.start();
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

    private final BroadcastReceiver operationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
                BroadcastData broadcastData = BroadcastData.getBroadcastData(intent);
                if (broadcastData != null) {
                    if (broadcastData.getAction() == BroadcastData.Action.PLAYING) {
                        MusicTag tag = broadcastData.getTagInfo();
                        onPlaying(tag);
                    /*    if((!isEditing) && Preferences.isFollowNowPlaying(getApplicationContext())) {
                            AudioTag tag = broadcastData.getTagInfo();
                            try {
                                editItems.clear();
                                editItems.add(tag);
                                displayTag = buildDisplayTag(true);
                                updateTitlePanel();
                                setUpPageViewer();
                            } catch (Exception e) {
                                Timber.e(e);
                            }
                        } */
                    }else {
                        ToastHelper.showBroadcastData(TagsActivity.this, mStateView, broadcastData);
                    }
                }
        }
    };
}