package apincer.android.mmate.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import apincer.android.mmate.R;
import apincer.android.mmate.musicbrainz.MusicBrainz;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.ui.view.LinearDividerItemDecoration;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.mmate.utils.UIUtils;
import apincer.android.utils.FileUtils;
import me.zhanghai.android.fastscroll.FastScrollerBuilder;
import sakout.mehdi.StateViews.StateView;

public class TagsMusicBrainzFragment extends Fragment implements View.OnClickListener {
    private static final String TAG = TagsMusicBrainzFragment.class.getName();
    private static final String NO_RESULTS = "NO_RESULTS";
    private TagsActivity mainActivity;
    private RecyclerView mRecyclerView;
    //private View vEmptyView;
    //private RotateLoading mProgressView;
//    private MusicBrainzController controller;
    private StateView mStateView;

    private String keywordTitle = null;
    private String keywordArtist = null;
    private String keywordAlbum = null;
    private String songTitle = null;
    private String songArtist = null;
    private String songAlbum = null;
    private Chip chipTitle;
    private Chip chipAlbum;
    private Chip chipArtist;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mainActivity = (TagsActivity) getActivity();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_musicbraniz, container, false);
        buildKeywords();
        buildChips(view);
        return view;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

      ///  mAdapter.addListener(this);

        // When true, filtering on big list is very slow!
      /***  mAdapter.setNotifyMoveOfFilteredItems(false)
                .setNotifyChangeOfUnfilteredItems(false)
                .setAnimationInitialDelay(100L)
                .setAnimationOnForwardScrolling(true)
                .setAnimationOnReverseScrolling(false)
                .setOnlyEntryAnimation(true); */

        mRecyclerView = view.findViewById(R.id.recycler_view);
        mRecyclerView.setItemViewCacheSize(0); //Setting ViewCache to 0 (default=2) will animate items better while scrolling down+up with LinearLayout
        //mRecyclerView.setWillNotCacheDrawing(true);
      //  mRecyclerView.setLayoutManager(new SmoothScrollLinearLayoutManager(getActivity()));
     //   mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setHasFixedSize(true); //Size of RV will not change
        // NOTE: Use default item animator 'canReuseUpdatedViewHolder()' will return true if
        // a Payload is provided. FlexibleAdapter is actually sending Payloads onItemChange.
        //mRecyclerView.setItemAnimator(new DefaultItemAnimator());
        RecyclerView.ItemDecoration itemDecoration = new LinearDividerItemDecoration(getActivity(),getActivity().getColor(R.color.item_divider),1);
        //   RecyclerView.ItemDecoration itemDecoration = new DividerItemDecoration(this, R.drawable.shadow_below);

        mRecyclerView.addItemDecoration(itemDecoration);

       // controller = new MusicBrainzController(this);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
       // mRecyclerView.setAdapter(controller.getAdapter());
        new FastScrollerBuilder(mRecyclerView)
                .useMd2Style()
                .setPadding(0,0,8,0)
                .setThumbDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_fastscroll_thumb))
                .build();

        //vEmptyView = view.findViewById(R.id.empty_view);
        //mProgressView = view.findViewById(R.id.progressView);
       // mProgressView.setVisibility(View.GONE);
        mStateView = view.findViewById(R.id.status_page);
        mStateView.addCustomState(NO_RESULTS, "No Results Found",
                "Unfortunately MusicBrainz return no results matching search criteria",
                AppCompatResources.getDrawable(getContext(), R.drawable.musicbrainz), null);

        mStateView.hideStates();

        doSearch();
    }

    private void buildChips(View view) {
        chipTitle = view.findViewById(R.id.filter_title);
        chipArtist = view.findViewById(R.id.filter_artist);
        chipAlbum = view.findViewById(R.id.filter_album);
        if(!StringUtils.isEmpty(songTitle)) {
            chipTitle.setText(songTitle);
            chipTitle.setVisibility(View.VISIBLE);
            chipTitle.setChecked(true);
            chipTitle.setChipIcon(UIUtils.getTintedDrawable(getContext(),R.drawable.ic_context_music_24dp, getActivity().getColor(R.color.selected)));
            chipTitle.setCloseIconVisible(false);
            chipTitle.setTextColor(getActivity().getColor(R.color.selected));
            Drawable icon = UIUtils.getTintedDrawable(getContext(),R.drawable.ic_context_music_24dp, getActivity().getColor(R.color.drawer_header_background));
            chipTitle.setChipIcon(icon);
            chipTitle.setOnCheckedChangeListener((buttonView, isChecked) -> {
              // keywordTitle = isChecked?songTitle:null;s
               if(isChecked) {
                   chipTitle.setChipIcon(UIUtils.getTintedDrawable(getContext(),R.drawable.ic_context_music_24dp, getActivity().getColor(R.color.selected)));
                   chipTitle.setCloseIconVisible(false);
                   chipTitle.setTextColor(getActivity().getColor(R.color.selected));
               }else {
                   chipTitle.setTextColor(getActivity().getColor(R.color.grey400));
                   chipTitle.setChipIcon(UIUtils.getTintedDrawable(getContext(),R.drawable.ic_context_music_24dp, getActivity().getColor(R.color.drawer_header_background)));
               }
               doSearch();
           });
        }else {
            chipTitle.setVisibility(View.GONE);
        }
        if(!StringUtils.isEmpty(songArtist)) {
            chipArtist.setText(songArtist);
            chipArtist.setChecked(true);
            chipArtist.setChipIcon(UIUtils.getTintedDrawable(getContext(),R.drawable.ic_context_artist_24dp, getActivity().getColor(R.color.selected)));
            chipArtist.setCloseIconVisible(false);
            chipArtist.setTextColor(getActivity().getColor(R.color.selected));
            chipArtist.setVisibility(View.VISIBLE);
            Drawable icon = UIUtils.getTintedDrawable(getContext(),R.drawable.ic_context_artist_24dp, getActivity().getColor(R.color.drawer_header_background));
            chipArtist.setChipIcon(icon);
            chipArtist.setOnCheckedChangeListener((buttonView, isChecked) -> {
               // keywordArtist = isChecked?songArtist:null;
                if(isChecked) {
                    chipArtist.setChipIcon(UIUtils.getTintedDrawable(getContext(),R.drawable.ic_context_artist_24dp, getActivity().getColor(R.color.selected)));
                    chipArtist.setCloseIconVisible(false);
                    chipArtist.setTextColor(getActivity().getColor(R.color.selected));
                }else {
                    chipArtist.setTextColor(getActivity().getColor(R.color.grey400));
                    chipArtist.setChipIcon(UIUtils.getTintedDrawable(getContext(),R.drawable.ic_context_artist_24dp, getActivity().getColor(R.color.drawer_header_background)));
                }
                doSearch();
            });
        }else {
            chipArtist.setVisibility(View.GONE);
        }
        if(!StringUtils.isEmpty(songAlbum)) {
            chipAlbum.setText(songAlbum);
            chipAlbum.setChecked(true);
            chipAlbum.setChipIcon(UIUtils.getTintedDrawable(getContext(),R.drawable.ic_context_album_24dp, getActivity().getColor(R.color.selected)));
            chipAlbum.setCloseIconVisible(false);
            chipAlbum.setTextColor(getActivity().getColor(R.color.selected));
            chipAlbum.setVisibility(View.VISIBLE);
            Drawable icon = UIUtils.getTintedDrawable(getContext(),R.drawable.ic_context_album_24dp, getActivity().getColor(R.color.drawer_header_background));
            chipAlbum.setChipIcon(icon);
            chipAlbum.setOnCheckedChangeListener((buttonView, isChecked) -> {
               // keywordAlbum = isChecked?songAlbum:null;
                if(isChecked) {
                    chipAlbum.setChipIcon(UIUtils.getTintedDrawable(getContext(),R.drawable.ic_context_album_24dp, getActivity().getColor(R.color.selected)));
                    chipAlbum.setCloseIconVisible(false);
                    chipAlbum.setTextColor(getActivity().getColor(R.color.selected));
                }else {
                    chipAlbum.setTextColor(getActivity().getColor(R.color.grey400));
                    chipAlbum.setChipIcon(UIUtils.getTintedDrawable(getContext(),R.drawable.ic_context_album_24dp, getActivity().getColor(R.color.drawer_header_background)));
                }

                doSearch();
            });
        }else {
            chipAlbum.setVisibility(View.GONE);
        }
    }

    private void buildKeywords() {
        List<MusicTag> list = mainActivity.getEditItems();
        if(!list.isEmpty()) {
            MusicTag item = list.get(0);
            if(list.size()==1) {
                songTitle = item.getTitle();
                if(StringUtils.isEmpty(songTitle)) {
                    songTitle = FileUtils.removeExtension(item.getPath());
                }
             //   tags.add(new Tag("TITLE", songTitle));
            }else if(StringUtils.isEmpty(item.getAlbum())){
                File file = new File(item.getPath());
                songAlbum = file.getParentFile().getName();
            }

            songArtist = item.getArtist();
            if(!StringUtils.isEmpty(songArtist) && !StringUtils.isEmpty(item.getAlbum())) {
                songAlbum = item.getAlbum();
            }
        }

        keywordTitle = songTitle;
        keywordArtist = songArtist;
        keywordAlbum =songAlbum;

      //  return tags;
    }

    @Override
    public void onResume() {
        super.onResume();
        doSearch();
       // String key = MetadataActivity.getEditItems().get(0).getTitle();
        //String url = "http://www.google.com/search?tbm=isch&source=lnms&sa=X&q=" + key;
        //String url = "https://musicbrainz.org/search?type=recording&limit=20&method=indexed&query="+key;
    }

    public void doSearch() {
        CompletableFuture future = CompletableFuture.supplyAsync(new Supplier<List<MusicTag>>() {
            @Override
            public List<MusicTag> get() {
                mStateView.displayLoadingState();
                keywordTitle = chipTitle.isChecked()?songTitle:null;
                keywordArtist = chipArtist.isChecked()?songArtist:null;
                keywordAlbum = chipAlbum.isChecked()?songAlbum:null;
                return MusicBrainz.findSongInfo(keywordTitle,keywordArtist,keywordAlbum);
            }
        }).thenAccept(new Consumer<List<MusicTag>>() {
            @Override
            public void accept(List<MusicTag> musicTags) {
                if(musicTags.isEmpty()) {
                    // vEmptyView.setVisibility(View.VISIBLE);
                    mRecyclerView.setVisibility(View.GONE);
                }else {
                    // vEmptyView.setVisibility(View.GONE);
                    mRecyclerView.setVisibility(View.VISIBLE);
                  //  controller.setData(musicTags);
                }
                mStateView.hideStates();
            }
        }).exceptionally(new Function<Throwable, Void>() {
            @Override
            public Void apply(Throwable throwable) {
                mStateView.hideStates();
                mStateView.displayState(NO_RESULTS);
                mRecyclerView.setVisibility(View.GONE);
                return null;
            }
        });
/*
        Observable<List<MusicTag>> observable = Observable.fromCallable(() -> {
           // if(mainActivity.getEditItems().size()>0) {
                keywordTitle = chipTitle.isChecked()?songTitle:null;
                keywordArtist = chipArtist.isChecked()?songArtist:null;
                keywordAlbum = chipAlbum.isChecked()?songAlbum:null;
                return MusicBrainz.findSongInfo(keywordTitle,keywordArtist,keywordAlbum);
               // resultsList.clear();
               // resultsList.addAll(songs);
          //  }
           // return true;
        });
        observable.subscribeOn(Schedulers.newThread()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<List<MusicTag>>() {
            @Override
            public void onSubscribe(Disposable d) {
                mStateView.displayLoadingState();
                //mProgressView.start();
               // mProgressView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onNext(List<MusicTag> actionResult) {
                if(actionResult.isEmpty()) {
                   // vEmptyView.setVisibility(View.VISIBLE);
                    mRecyclerView.setVisibility(View.GONE);
                }else {
                   // vEmptyView.setVisibility(View.GONE);
                    mRecyclerView.setVisibility(View.VISIBLE);
                    controller.setData(actionResult);
                }
            }

            @Override
            public void onError(Throwable e) {
                mStateView.hideStates();
                mStateView.displayState(NO_RESULTS);
                mRecyclerView.setVisibility(View.GONE);
            }

            @Override
            public void onComplete() {
                mStateView.hideStates();
            }
        }); */
    }

  //  @Override
  //  public boolean onItemClick(View view, int position) {
        /***
        if (mAdapter == null) {
            return false;
        }
        IFlexible flexibleItem = mAdapter.getItem(position);
        if (flexibleItem instanceof RecordingItem) {
            final RecordingItem recordingItem = (RecordingItem) flexibleItem;

            View cview = getActivity().getLayoutInflater().inflate(R.layout.view_actionview_tags_from_musicbrainz, null);
            TextView tTitle = cview.findViewById(R.id.title);
            tTitle.setText(recordingItem.title);
            TextView tArtist = cview.findViewById(R.id.artist);
            tArtist.setText(recordingItem.artist);
            TextView tAlbum = cview.findViewById(R.id.album);
            tAlbum.setText(recordingItem.album);
            TextView tGenre = cview.findViewById(R.id.genre);
            tGenre.setText(recordingItem.genre);
            TextView tYear = cview.findViewById(R.id.year);
            tYear.setText(recordingItem.year);
            View btnOK = cview.findViewById(R.id.btn_ok);
            View btnCancel = cview.findViewById(R.id.btn_cancel);

            ImageView cover = cview.findViewById(R.id.coverart);
              LoadRequest request = LoadRequest.builder(getContext())
                    .data(MusicbrainzCoverArtProvider.getUriForMediaItem(recordingItem))
                    .target(cover)
                    .crossfade(true)
                    .placeholder(R.drawable.progress)
                    .error(R.drawable.ic_broken_image_black_24dp)
                    .size(640, 640)
                    .build();
            Coil.execute(request);

            AlertDialog alert = new MaterialAlertDialogBuilder(getActivity(), R.style.CustomAlertDialogTheme)
                    .setTitle("")
                    .setView(cview)
                    .setCancelable(true)
                    .create();

            btnOK.setOnClickListener((View.OnClickListener) v -> {
                List<MediaItem> mediaItems = MetadataActivity.getEditItems();
                boolean singleTrack = mediaItems.size()==1;

                // String artworkPath = recordingItem.getFrontCoverCache();
                String title = String.valueOf(tTitle.getText());
                String artist = String.valueOf(tArtist.getText());
                String album = String.valueOf(tAlbum.getText());
                String genre = String.valueOf(tGenre.getText());
                String year = String.valueOf(tYear.getText());

                for(MediaItem item:mediaItems) {
                    buildPendingTags(item, title, artist, album, genre, year, singleTrack);
                }

                String artworkPath = MediaFileRepository.getDownloadPath("MusicMate/" + recordingItem.albumId).getAbsolutePath();
                LoadRequest request1 = LoadRequest.builder(getContext())
                        .data(MusicbrainzCoverArtProvider.getUriForMediaItem(recordingItem))
                        .target(null,null,(Function1<Drawable, Unit>) drawable -> {
                            try {
                                File path = new File(artworkPath);
                                path= path.getParentFile();
                                if(!path.exists()) path.mkdirs();
                                Bitmap bitmap = UIUtils.drawableToBitmap(drawable);
                                writePNG(new File(artworkPath), bitmap);
                                // FileOutputStream out = new FileOutputStream(artworkPath);
                               // IOUtils.copy(UIUtils.bitmapToInputStream(bitmap), out);
                               // out.close();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            return null;
                        })
                        .placeholder(R.drawable.progress)
                        .error(R.drawable.ic_broken_image_black_24dp)
                        .crossfade(true)
                        .size(640, 640)
                        .build();
                Coil.execute(request1);

                MediaItemIntentService.startService(getContext(), Constants.COMMAND_SAVE,mediaItems, artworkPath);

                alert.dismiss();
            });
            btnCancel.setOnClickListener(v -> alert.dismiss());

            alert.requestWindowFeature(Window.FEATURE_NO_TITLE);
            alert.setCanceledOnTouchOutside(true);
            // make popup round corners
            alert.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            alert.show();

        } **/
       // return false;
   // }

    private void buildPendingTags(MusicTag item, String title, String artist, String album, String genre, String year, boolean singleTrack) {
        MusicTag tagUpdate = item;
        if(singleTrack) {
            tagUpdate.setTitle(StringUtils.trimToEmpty(title));
        }
        tagUpdate.setAlbum(StringUtils.trimToEmpty(album));
        tagUpdate.setArtist(StringUtils.trimToEmpty(artist));
        tagUpdate.setGenre(StringUtils.trimToEmpty(genre));
        tagUpdate.setYear(StringUtils.trimToEmpty(year));
    }

    private void writePNG(File file, Bitmap bitmap) {
        FileOutputStream outputStream;
        try {
            outputStream = new FileOutputStream(file); // getContext().openFileOutput(fileName, Context.MODE_PRIVATE);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            outputStream.close();
        } catch (Exception error) {
            error.printStackTrace();
        }
    }

    @Override
    public void onClick(View view) {
        RecyclerView.ViewHolder h = mRecyclerView.getChildViewHolder(view);
        /*
        if(h instanceof EpoxyViewHolder) {
            EpoxyViewHolder holder = (EpoxyViewHolder)h;
            MusicTag tag = controller.getAudioTag(holder); // ((MusicBrainzModel_)holder.getModel()).tag();
            View cview = getActivity().getLayoutInflater().inflate(R.layout.view_actionview_tags_from_musicbrainz, null);
            TextView tTitle = cview.findViewById(R.id.title);
            tTitle.setText(tag.getTitle());
            TextView tArtist = cview.findViewById(R.id.artist);
            tArtist.setText(tag.getArtist());
            TextView tAlbum = cview.findViewById(R.id.album);
            tAlbum.setText(tag.getAlbum());
            TextView tGenre = cview.findViewById(R.id.genre);
            tGenre.setText(tag.getGenre());
            TextView tYear = cview.findViewById(R.id.year);
            tYear.setText(tag.getYear());
           // View btnOK = cview.findViewById(R.id.btn_ok);
           // View btnCancel = cview.findViewById(R.id.btn_cancel);

            ImageView cover = cview.findViewById(R.id.coverart);
            ImageLoader imageLoader = Coil.imageLoader(getContext());
            ImageRequest request = new ImageRequest.Builder(getContext())
                    .data(MusicbrainzCoverArtProvider.getUriForMediaItem(tag))
                    .crossfade(true)
                    .target(cover)
                    .transformations(new RoundedCornersTransformation(12,12,12,12))
                    .build();
            imageLoader.enqueue(request);

            AlertDialog alert = new MaterialAlertDialogBuilder(getActivity(), R.style.AlertDialogTheme)
                    .setTitle("")
                    .setView(cview)
                    .setCancelable(true)
                    .setNegativeButton(R.string.alert_btn_cancel, (dialogInterface, i) -> dialogInterface.dismiss())
                    .setPositiveButton(R.string.alert_btn_set, (dialogInterface, i) -> {
                        List<MusicTag> mediaItems = mainActivity.getEditItems();
                        boolean singleTrack = mediaItems.size()==1;

                        // String artworkPath = recordingItem.getFrontCoverCache();
                        String title = String.valueOf(tTitle.getText());
                        String artist = String.valueOf(tArtist.getText());
                        String album = String.valueOf(tAlbum.getText());
                        String genre = String.valueOf(tGenre.getText());
                        String year = String.valueOf(tYear.getText());

                        for(MusicTag item:mediaItems) {
                            buildPendingTags(item, title, artist, album, genre, year, singleTrack);
                        }

                        String artworkPath = FileSystem.getDownloadPath(getContext(), "MusicMate/" + tag.getAlbum()).getAbsolutePath();

                       // URL retrofit = MusicBrainz.getCoverart(tag);
                        ImageRequest rq = new ImageRequest.Builder(getContext())
                                .data(MusicbrainzCoverArtProvider.getUriForMediaItem(tag))
                               // .data(retrofit)
                                .crossfade(true)
                                .target(new Target() {
                                    @Override
                                    public void onStart(@Nullable Drawable drawable) {

                                    }

                                    @Override
                                    public void onError(@Nullable Drawable drawable) {

                                    }

                                    @Override
                                    public void onSuccess(@NonNull Drawable drawable) {
                                        try {
                                            File path = new File(artworkPath);
                                            path= path.getParentFile();
                                            if(!path.exists()) path.mkdirs();
                                            Bitmap bitmap = UIUtils.drawableToBitmap(drawable);
                                            writePNG(new File(artworkPath), bitmap);
                                        } catch (Exception e) {
                                            Log.e(TAG, "", e);
                                        }
                                    }
                                })
                                .build();
                        imageLoader.enqueue(rq);
                        UpdateAudioFileWorker.startWorker(getContext(),mediaItems, artworkPath);
                        //MediaItemIntentService.startService(getContext(), Constants.COMMAND_SAVE,mediaItems, artworkPath);

                        dialogInterface.dismiss();
                    })
                    .create(); */

            /*
            btnOK.setOnClickListener((View.OnClickListener) v -> {
                List<AudioTag> mediaItems = mainActivity.getEditItems();
                boolean singleTrack = mediaItems.size()==1;

                // String artworkPath = recordingItem.getFrontCoverCache();
                String title = String.valueOf(tTitle.getText());
                String artist = String.valueOf(tArtist.getText());
                String album = String.valueOf(tAlbum.getText());
                String genre = String.valueOf(tGenre.getText());
                String year = String.valueOf(tYear.getText());

                for(AudioTag item:mediaItems) {
                    buildPendingTags(item, title, artist, album, genre, year, singleTrack);
                }

                String artworkPath = MediaFileRepository.getDownloadPath("MusicMate/" + tag.getAlbumId()).getAbsolutePath();

                ImageRequest rq = new ImageRequest.Builder(getContext())
                        .data(EmbedCoverArtProvider.getUriForMediaItem(tag))
                        .crossfade(true)
                        .target(null,null,(Function1<Drawable, Unit>) drawable -> {
                            try {
                                File path = new File(artworkPath);
                                path= path.getParentFile();
                                if(!path.exists()) path.mkdirs();
                                Bitmap bitmap = UIUtils.drawableToBitmap(drawable);
                                writePNG(new File(artworkPath), bitmap);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            return null;
                        })
                        .build();
                imageLoader.enqueue(rq);

                MediaItemIntentService.startService(getContext(), Constants.COMMAND_SAVE,mediaItems, artworkPath);

                alert.dismiss();
            }); */
           // btnCancel.setOnClickListener(v -> alert.dismiss());
/*
            alert.requestWindowFeature(Window.FEATURE_NO_TITLE);
            alert.setCanceledOnTouchOutside(true);
            // make popup round corners
           // alert.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            alert.show();
        } */
    }
}