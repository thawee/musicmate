package apincer.android.mmate.ui;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.palette.graphics.Palette;
import androidx.viewpager2.widget.ViewPager2;

import com.arthenica.ffmpegkit.FFmpegKit;
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
import apincer.android.mmate.R;
import apincer.android.mmate.fs.EmbedCoverArtProvider;
import apincer.android.mmate.objectbox.AudioTag;
import apincer.android.mmate.repository.AudioFileRepository;
import apincer.android.mmate.repository.SearchCriteria;
import apincer.android.mmate.service.BroadcastData;
import apincer.android.mmate.service.AudioTagEditEvent;
import apincer.android.mmate.utils.ApplicationUtils;
import apincer.android.mmate.utils.AudioTagUtils;
import apincer.android.mmate.utils.BitmapHelper;
import apincer.android.mmate.utils.ColorUtils;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.mmate.utils.ToastHelper;
import apincer.android.mmate.utils.UIUtils;
import apincer.android.mmate.work.DeleteAudioFileWorker;
import apincer.android.mmate.work.ImportAudioFileWorker;
import apincer.android.utils.FileUtils;
import cn.iwgang.simplifyspan.SimplifySpanBuild;
import cn.iwgang.simplifyspan.customspan.CustomClickableSpan;
import cn.iwgang.simplifyspan.other.OnClickableSpanListener;
import cn.iwgang.simplifyspan.other.SpecialGravity;
import cn.iwgang.simplifyspan.unit.SpecialClickableUnit;
import cn.iwgang.simplifyspan.unit.SpecialLabelUnit;
import cn.iwgang.simplifyspan.unit.SpecialTextUnit;
import coil.Coil;
import coil.ImageLoader;
import coil.request.ImageRequest;
import coil.target.Target;
import me.zhanghai.android.materialratingbar.MaterialRatingBar;
import sakout.mehdi.StateViews.StateView;
import timber.log.Timber;

public class TagsActivity extends AppCompatActivity {
    private static ArrayList<AudioTag> editItems = new ArrayList<>();
    private volatile AudioTag displayTag;
    private ImageView coverArtView;
    private TabLayout tabLayout;
    private Toolbar toolbar;
    private TextView titleView;
    private TextView artistView ;
    private TextView albumView ;
    private TextView albumArtistView;
    private TextView encInfo;
    private ImageView audiophileView;
    private ImageView hiresView;
    private ImageView mqaView;
    private MaterialRatingBar ratingView;
    private TextView qualityView;
    private View coverArtLayout;
    private View panelLabels;
    private CollapsingToolbarLayout toolBarLayout;
    private int toolbar_from_color;
    private int toolbar_to_color;
    private StateView mStateView;

    @Override
    public void onBackPressed() {
        super.onBackPressed();
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
    }
    @Override
    protected void onResume() {
        super.onResume();
        // Register for the particular broadcast based on ACTION string
        IntentFilter filter = new IntentFilter(BroadcastData.BROADCAST_ACTION);
        LocalBroadcastManager.getInstance(this).registerReceiver(operationReceiver, filter);
    }

    private TextView tagInfo;
    private TextView pathInfo;
    private SearchCriteria criteria;

    public List<AudioTag> getEditItems() {
        return editItems;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tags);
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

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
                    AudioFileRepository.getInstance(getApplication()).scanFileAndSaveTag(new File(flacPath));
                    runOnUiThread(() -> {
                        String message = "Convert '"+tag.getTitle()+"' completed in " + session.getDuration() +" ms.";
                        ToastHelper.showActionMessage(getApplicationContext(), Constants.STATUS_SUCCESS, message);
                        if(total == count[0]) {
                            mStateView.hideStates();
                        }
                    });
                    count[0]++;
                });
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN,sticky = true)
    public void onMessageEvent(AudioTagEditEvent event) {
        criteria = event.getSearchCriteria();
        editItems.clear();
        editItems.addAll(event.getItems());
        displayTag = buildDisplayTag(true);

        updateTitlePanel();
        final AppBarLayout appBarLayout = findViewById(R.id.appbar);
        ViewPager2 viewPager = findViewById(R.id.viewpager);

        tabLayout = findViewById(R.id.tabLayout);
        TagsTabLayoutAdapter adapter = new TagsTabLayoutAdapter(getSupportFragmentManager(), getLifecycle());
        adapter.addNewTab(new TagsEditorFragment(), "Editor");
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
        if(Constants.MEDIA_ENC_WAVE.equalsIgnoreCase(displayTag.getAudioEncoding())) {
            //findViewById(R.id.btnFFMPEG).setVisibility(View.VISIBLE);
            findViewById(R.id.btnFFMPEG).setEnabled(true);
            findViewById(R.id.btnFFMPEG).setOnClickListener(view -> doStartFFMpeg());
        }else {
            // findViewById(R.id.btnFFMPEG).setEnabled(false);
            findViewById(R.id.btnFFMPEG).setVisibility(View.GONE);
        }
       // Toast.makeText(getActivity(), event.message, Toast.LENGTH_SHORT).show();
    }

    private void setUpTitlePanel() {
        titleView = findViewById(R.id.panel_title);
        artistView = findViewById(R.id.panel_artist);
        albumView = findViewById(R.id.panel_album);
        albumArtistView = findViewById(R.id.panel_album_artist);
        encInfo = findViewById(R.id.panel_enc);
        tagInfo = findViewById(R.id.panel_tag);
        pathInfo = findViewById(R.id.panel_path);
        audiophileView = findViewById(R.id.icon_audiophile);
        hiresView = findViewById(R.id.icon_hires);
        mqaView = findViewById(R.id.icon_mqa);
        ratingView = findViewById(R.id.icon_rating);
        qualityView = findViewById(R.id.icon_quality_text);
    }

    public void updateTitlePanel() {
        toolbar.setTitle(displayTag.getTitle());
        titleView.setText(displayTag.getTitle());
        artistView.setText(displayTag.getArtist());
        audiophileView.setVisibility(displayTag.isAudiophile()?View.VISIBLE:View.GONE);
        hiresView.setVisibility(AudioTagUtils.isHiResOrDSD(displayTag)?View.VISIBLE:View.GONE);
        mqaView.setVisibility(displayTag.isMQA()?View.VISIBLE:View.GONE);
        ratingView.setRating(displayTag.getRating());
        qualityView.setText(AudioTagUtils.getTrackQuality(displayTag));
        artistView.setPaintFlags(artistView.getPaintFlags()| Paint.UNDERLINE_TEXT_FLAG);
        artistView.setOnClickListener(view -> {
            //filter by artist
            Intent resultIntent = new Intent();
            if(criteria!=null) {
                criteria.setFilterType(Constants.FILTER_TYPE_ARTIST);
                criteria.setFilterText(displayTag.getArtist());
                ApplicationUtils.setSearchCriteria(resultIntent,criteria); //resultIntent.putExtra(Constants.KEY_SEARCH_CRITERIA, criteria);
            }
            setResult(RESULT_OK, resultIntent);
            finish();
        });
        albumView.setText(displayTag.getAlbum());
        albumView.setPaintFlags(albumView.getPaintFlags()| Paint.UNDERLINE_TEXT_FLAG);
        albumView.setOnClickListener(view -> {
            // filter by album
            Intent resultIntent = new Intent();
            if(criteria!=null) {
                criteria.setFilterType(Constants.FILTER_TYPE_ALBUM);
                criteria.setFilterText(displayTag.getAlbum());
                ApplicationUtils.setSearchCriteria(resultIntent,criteria); //resultIntent.putExtra(Constants.KEY_SEARCH_CRITERIA, criteria);
            }
            setResult(RESULT_OK, resultIntent);
            finish();
        });
        if(!StringUtils.isEmpty(displayTag.getAlbumArtist())) {
            albumArtistView.setText(displayTag.getAlbumArtist());
            albumArtistView.setPaintFlags(albumArtistView.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            albumArtistView.setOnClickListener(view -> {
                // filter by album
                Intent resultIntent = new Intent();
                if (criteria != null) {
                    criteria.setFilterType(Constants.FILTER_TYPE_ALBUM_ARTIST);
                    criteria.setFilterText(displayTag.getAlbumArtist());
                    ApplicationUtils.setSearchCriteria(resultIntent,criteria); //resultIntent.putExtra(Constants.KEY_SEARCH_CRITERIA, criteria);
                }
                setResult(RESULT_OK, resultIntent);
                finish();
            });
        }else {
            albumArtistView.setText("N/A");
        }

        String matePath = AudioFileRepository.getInstance(getApplication()).buildCollectionPath(displayTag);
        String mateInd = "";
        if(!StringUtils.equals(matePath, displayTag.getPath())) {
            mateInd = " **";
        }
        String simplePath = displayTag.getSimpleName();
        if(simplePath.contains("/")) {
            simplePath = simplePath.substring(0, simplePath.lastIndexOf("/"));
        }

        pathInfo.setText(displayTag.getStorageId()+"://"+simplePath + mateInd);
        pathInfo.setPaintFlags(pathInfo.getPaintFlags()| Paint.UNDERLINE_TEXT_FLAG);

        pathInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent resultIntent = new Intent();
                if(criteria!=null) {
                    criteria.setFilterType(Constants.FILTER_TYPE_PATH);
                    criteria.setFilterText(displayTag.getPath());
                    ApplicationUtils.setSearchCriteria(resultIntent,criteria); //resultIntent.putExtra(Constants.KEY_SEARCH_CRITERIA, criteria);
                }
                setResult(RESULT_OK, resultIntent);
                finish();
            }
        });

        ImageLoader imageLoader = Coil.imageLoader(getApplicationContext());
        ImageRequest request = new ImageRequest.Builder(getApplicationContext())
                .data(EmbedCoverArtProvider.getUriForMediaItem(displayTag))
                .allowHardware(false)
                .placeholder(R.drawable.progress)
                .error(R.drawable.ic_broken_image_black_24dp)
                .target(new Target() {
                    @Override
                    public void onStart(@Nullable Drawable drawable) {

                    }

                    @Override
                    public void onError(@Nullable Drawable drawable) {
                        coverArtView.setImageDrawable(drawable);
                    }

                    @Override
                    public void onSuccess(@NonNull Drawable drawable) {
                        coverArtView.setImageDrawable(drawable);
                        try {
                            Bitmap bmp = BitmapHelper.drawableToBitmap(drawable);
                          //  Palette palette = Palette.from(bmp).generate();
                           // int frameColor = palette.getDominantColor(getColor(R.color.bgColor));
                           // int panelColor = palette.getVibrantColor(getColor(R.color.bgColor));
                          //  toolbar_from_color = ColorUtils.TranslateDark(frameColor,100);
                          //  toolbar_to_color = ColorUtils.TranslateDark(frameColor,100);; //frameColor;
                            //mainFrame.setBackgroundColor(ColorUtils.TranslateDark(frameColor,100));
                            //Bitmap bmp2 = BitmapHelper.blur(getApplicationContext(), bmp);
                           // coverArtView2.setImageDrawable(BitmapHelper.bitmapToDrawable(getApplicationContext(), bmp2));
                            coverArtView.setImageBitmap(BitmapHelper.applyReflection(bmp));
                         //   panelLabels.setBackgroundColor(ColorUtils.TranslateLight(panelColor, 400));
                          //  toolBarLayout.setContentScrimColor(ColorUtils.TranslateDark(frameColor,100));
                          //  coverArtLayout.setBackgroundColor(ColorUtils.TranslateDark(panelColor, 400));
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
                    }
                })
                .build();
        imageLoader.enqueue(request);

        // Tag
        int labelColor = ContextCompat.getColor(getApplicationContext(), R.color.grey400);
        int linkNorTextColor = ContextCompat.getColor(getApplicationContext(), R.color.white);
        int linkPressBgColor = ContextCompat.getColor(getApplicationContext(), R.color.grey200);
        SimplifySpanBuild tagSpan = new SimplifySpanBuild("");
        tagSpan.append(new SpecialLabelUnit("Grouping:", labelColor, UIUtils.sp2px(getApplication(),10), Color.TRANSPARENT).setPadding(5).setPaddingLeft(10).setPaddingRight(10).setGravity(SpecialGravity.CENTER))
                //.append(new SpecialTextUnit(StringUtils.isEmpty(displayTag.getGrouping())?"N/A":displayTag.getGrouping()).setTextSize(14).useTextBold().setGravity(tagInfo.getPaint(), SpecialGravity.CENTER))
                .appendMultiClickable(new SpecialClickableUnit(tagInfo, new OnClickableSpanListener() {
                            @Override
                            public void onClick(TextView tv, CustomClickableSpan clickableSpan) {
                                Intent resultIntent = new Intent();
                                if(criteria!=null) {
                                    criteria.setFilterType(Constants.FILTER_TYPE_GROUPING);
                                    criteria.setFilterText(StringUtils.trimToEmpty(displayTag.getGrouping()));
                                    ApplicationUtils.setSearchCriteria(resultIntent,criteria);
                                }
                                setResult(RESULT_OK, resultIntent);
                                finish();
                            }
                        }).setNormalTextColor(linkNorTextColor).setPressBgColor(linkPressBgColor),
                new SpecialTextUnit(StringUtils.isEmpty(displayTag.getGrouping())?"N/A":displayTag.getGrouping()).setTextSize(14).useTextBold().showUnderline())

                .append("  ")
                .append(new SpecialLabelUnit("Genre:", labelColor, UIUtils.sp2px(getApplication(),10), Color.TRANSPARENT).setPadding(5).setPaddingLeft(10).setPaddingRight(10).setGravity(SpecialGravity.CENTER))
                //.append(new SpecialTextUnit(StringUtils.isEmpty(displayTag.getGenre())?"N/A":displayTag.getGenre()).setTextSize(14).useTextBold().setGravity(tagInfo.getPaint(), SpecialGravity.CENTER));
                .appendMultiClickable(new SpecialClickableUnit(tagInfo, new OnClickableSpanListener() {
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
                        new SpecialTextUnit(StringUtils.isEmpty(displayTag.getGenre())?"N/A":displayTag.getGenre()).setTextSize(14).useTextBold().showUnderline());
        tagInfo.setText(tagSpan.build());

        // ENC info
        int encColor = ContextCompat.getColor(getApplicationContext(), R.color.grey100);
        SimplifySpanBuild spannableEnc = new SimplifySpanBuild("");
        spannableEnc.append(new SpecialTextUnit("[ ",encColor).setTextSize(10))
                .append(new SpecialTextUnit(displayTag.getAudioEncoding(),encColor).setTextSize(10))
                .append(new SpecialTextUnit(" | ",encColor).setTextSize(10));
        if(displayTag.isMQA()) {
            String mqaLabel = "MQA";
            String mqaRate = displayTag.getMQASampleRate();
            long rate = AudioTagUtils.parseMQASampleRate(mqaRate);
            if (rate >0 && rate != displayTag.getAudioSampleRate()) {
                mqaLabel = mqaLabel + " " + StringUtils.getFormatedAudioSampleRate(rate, true);
            }
            spannableEnc.append(new SpecialTextUnit(mqaLabel,encColor).setTextSize(10))
                    .append(new SpecialTextUnit(" | ",encColor).setTextSize(10));
        }
        spannableEnc.append(new SpecialTextUnit(StringUtils.getFormatedBitsPerSample(displayTag.getAudioBitsPerSample()),encColor).setTextSize(10))
                .append(new SpecialTextUnit(" | ").setTextSize(10))
                .append(new SpecialTextUnit(StringUtils.getFormatedAudioSampleRate(displayTag.getAudioSampleRate(),true),encColor).setTextSize(10))
                .append(new SpecialTextUnit(" | ").setTextSize(10)) //.append(new SpecialLabelUnit(" | ", Color.GRAY, sp2px(10), Color.TRANSPARENT).showBorder(Color.BLACK, 2).setPaddingLeft(10).setPaddingRight(10).setGravity(SpecialGravity.CENTER))
                .append(new SpecialTextUnit(StringUtils.getFormatedChannels(displayTag.getAudioChannels()),encColor).setTextSize(10))
                .append(new SpecialTextUnit(" | ").setTextSize(10)) //.append(new SpecialLabelUnit(" | ", Color.GRAY, sp2px(10), Color.TRANSPARENT).showBorder(Color.BLACK, 2).setPaddingLeft(10).setPaddingRight(10).setGravity(SpecialGravity.CENTER))
                .append(new SpecialTextUnit(StringUtils.formatDuration(displayTag.getAudioDuration(),true),encColor).setTextSize(10))
                .append(new SpecialTextUnit(" | ").setTextSize(10)) //.append(new SpecialLabelUnit(" | ", Color.GRAY, sp2px(10), Color.TRANSPARENT).showBorder(Color.BLACK, 2).setPaddingLeft(10).setPaddingRight(10).setGravity(SpecialGravity.CENTER))
                .append(new SpecialTextUnit(StringUtils.formatStorageSize(displayTag.getFileSize()),encColor).setTextSize(10))
                .append(new SpecialTextUnit(" ]",encColor).setTextSize(10));
        encInfo.setText(spannableEnc.build());
    }

    //
    AudioTag buildDisplayTag(boolean reload) {
        AudioTag baseItem = editItems.get(0);
        AudioTag displayTag = baseItem;
        AudioFileRepository fileRepos = AudioFileRepository.getInstance(getApplication());
        if(reload) {
            fileRepos.reloadMediaItem(displayTag);
        }
        displayTag = displayTag.clone();
        if(editItems.size()==1) {
            return displayTag;
        }

        for (int i=1;i<editItems.size();i++) {
            AudioTag item = editItems.get(i);
            if(reload) {
                fileRepos.reloadMediaItem(item);
            }
            AudioTag displayTag2 = item;
            if(!StringUtils.equals(displayTag.getTitle(), displayTag2.getTitle())) {
                displayTag.setTitle(StringUtils.MULTI_VALUES);
            }
            if(!StringUtils.equals(displayTag.getTrack(), displayTag2.getTrack())) {
                displayTag.setTrack(StringUtils.MULTI_VALUES);
            }
            if(!StringUtils.equals(displayTag.getAlbum(), displayTag2.getAlbum())) {
                displayTag.setAlbum(StringUtils.MULTI_VALUES);
            }
            if(!StringUtils.equals(displayTag.getArtist(), displayTag2.getArtist())) {
                displayTag.setArtist(StringUtils.MULTI_VALUES);
            }
            if(!StringUtils.equals(displayTag.getAlbumArtist(), displayTag2.getAlbumArtist())) {
                displayTag.setAlbumArtist(StringUtils.MULTI_VALUES);
            }
            if(!StringUtils.equals(displayTag.getGenre(), displayTag2.getGenre())) {
                displayTag.setGenre(StringUtils.MULTI_VALUES);
            }
            if(!StringUtils.equals(displayTag.getYear(), displayTag2.getYear())) {
                displayTag.setYear(StringUtils.MULTI_VALUES);
            }
            if(!StringUtils.equals(displayTag.getDisc(), displayTag2.getDisc())) {
                displayTag.setDisc(StringUtils.MULTI_VALUES);
            }
            if(!StringUtils.equals(displayTag.getComment(), displayTag2.getComment())) {
                displayTag.setComment(StringUtils.MULTI_VALUES);
            }
            if(!StringUtils.equals(displayTag.getComposer(), displayTag2.getComposer())) {
                displayTag.setComposer(StringUtils.MULTI_VALUES);
            }
            if(!StringUtils.equals(displayTag.getGrouping(), displayTag2.getGrouping())) {
                displayTag.setGrouping(StringUtils.MULTI_VALUES);
            }
            if(!StringUtils.equals(displayTag.getSource(), displayTag2.getSource())) {
                displayTag.setSource("Unknown");
            }
        }
        return displayTag;
    }

    public int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
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

        new MaterialAlertDialogBuilder(TagsActivity.this, R.style.AlertDialogTheme)
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
                .setNeutralButton("CANCEL", (dialogInterface, i) -> dialogInterface.dismiss())
                .show();
    }

    public void doMoveMediaItem() {
        String text = "Import ";
        if(editItems.size()>1) {
            text = text + editItems.size() + " songs to Music Directory?";
        }else {
            text = text + "'"+editItems.get(0).getTitle()+"' song to Music Directory?";
        }

        new MaterialAlertDialogBuilder(TagsActivity.this, R.style.AlertDialogTheme)
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
                .setNeutralButton("CANCEL", (dialogInterface, i) -> dialogInterface.dismiss())
                .show();
    }

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

    class OffSetChangeListener implements AppBarLayout.OnOffsetChangedListener {

        @Override
        public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
            double vScrollOffset = Math.abs(verticalOffset);
            double scale = (1 - (1.0 / appBarLayout.getTotalScrollRange() * (vScrollOffset) * 0.2));
            coverArtView.setScaleX((float) scale);
            coverArtView.setScaleY((float) scale);
            fadeToolbarTitle((1.0 / appBarLayout.getTotalScrollRange() * (vScrollOffset)));

            if (Math.abs(1.0 / appBarLayout.getTotalScrollRange() * vScrollOffset) >= 0.8) {
//                tabLayout.setBackgroundColor(getResources().getColor(R.color.colorPrimary));
                ObjectAnimator colorFade = ObjectAnimator.ofObject(tabLayout,
                        "backgroundColor" /*view attribute name*/, new ArgbEvaluator(),
                        toolbar_from_color
                        /*from color*/, toolbar_to_color/*to color*/);
                colorFade.setDuration(2000);
                colorFade.start();
            } else {
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
                    ToastHelper.showBroadcastData(TagsActivity.this, broadcastData);
                }
        }
    };
}