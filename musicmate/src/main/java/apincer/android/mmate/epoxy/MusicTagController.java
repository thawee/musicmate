package apincer.android.mmate.epoxy;

import static apincer.android.mmate.utils.StringUtils.isEmpty;

import android.content.Context;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.airbnb.epoxy.EpoxyViewHolder;
import com.airbnb.epoxy.OnModelBuildFinishedListener;
import com.airbnb.epoxy.TypedEpoxyController;

import java.util.ArrayList;
import java.util.List;

import apincer.android.mmate.Constants;
import apincer.android.mmate.objectbox.MusicTag;
import apincer.android.mmate.repository.MusicTagRepository;
import apincer.android.mmate.repository.SearchCriteria;
import apincer.android.mmate.utils.StringUtils;
import apincer.android.mmate.work.MusicMateExecutors;
import timber.log.Timber;

public class MusicTagController extends TypedEpoxyController<List<MusicTag>> {
    private SearchCriteria criteria;
    private double totalDuration = 0;
    private long totalSize = 0;
    private final View.OnClickListener clickListener;
    private final View.OnLongClickListener longClickListener;
    private final ArrayList<MusicTag> selections;
    private final ArrayList<MusicTag> lastSelections;
    private OnModelBuildFinishedListener listener;
    public static volatile boolean loading  = false;

    public MusicTagController(View.OnClickListener clickListener, View.OnLongClickListener longClickListener) {
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
        selections = new ArrayList<>();
        lastSelections = new ArrayList<>();
    }

    @Override
    public void addModelBuildListener(OnModelBuildFinishedListener listener) {
        super.addModelBuildListener(listener);
        this.listener = listener;
    }

    @Override
    protected void buildModels(List<MusicTag> audioTags) {
        totalDuration =0;
        totalSize = 0;
        boolean noFilters = isEmpty(criteria.getFilterType()) && isEmpty(criteria.getFilterText());
        if(noFilters) {
            for (MusicTag tag : audioTags) {
                new MusicTagModel_()
                        .id(tag.getId())
                        .tag(tag)
                        .controller(this)
                        .clickListener(clickListener)
                        .longClickListener(longClickListener)
                        .addTo(this);
                totalDuration = totalDuration + tag.getAudioDuration();
                totalSize = totalSize + tag.getFileSize();
            }
        }else {
            for (MusicTag tag : audioTags) {
                if(!isFilterMatched(criteria, tag)) {
                    continue;
                }
                /*
                if (Constants.FILTER_TYPE_ALBUM.equals(criteria.getFilterType())) {
                    if (!StringUtils.equals(tag.getAlbum(), criteria.getFilterText())) {
                        continue;
                    }
                } else if (Constants.FILTER_TYPE_ARTIST.equals(criteria.getFilterType())) {
                    if (!StringUtils.equals(tag.getArtist(), criteria.getFilterText())) {
                        continue;
                    }
                } else if (Constants.FILTER_TYPE_ALBUM_ARTIST.equals(criteria.getFilterType())) {
                    if (!StringUtils.equals(tag.getAlbumArtist(), criteria.getFilterText())) {
                        continue;
                    }
                } else if (Constants.FILTER_TYPE_GENRE.equals(criteria.getFilterType())) {
                    if (!StringUtils.equals(tag.getGenre(), criteria.getFilterText())) {
                        continue;
                    }
                } else if (Constants.FILTER_TYPE_PUBLISHER.equals(criteria.getFilterType())) {
                    if (!StringUtils.equals(tag.getPublisher(), criteria.getFilterText())) {
                        continue;
                    }
                } else if (Constants.FILTER_TYPE_GROUPING.equals(criteria.getFilterType())) {
                    if (!StringUtils.equals(tag.getGrouping(), criteria.getFilterText())) {
                        continue;
                    }
                } else if (Constants.FILTER_TYPE_PATH.equals(criteria.getFilterType())) {
                    if (!tag.getPath().startsWith(criteria.getFilterText())) {
                        continue;
                    }
                } */
                new MusicTagModel_()
                        .id(tag.getId())
                        .tag(tag)
                        .controller(this)
                        .clickListener(clickListener)
                        .longClickListener(longClickListener)
                        .addTo(this);
                totalDuration = totalDuration + tag.getAudioDuration();
                totalSize = totalSize + tag.getFileSize();
            }
        }
    }

    public static boolean isFilterMatched(SearchCriteria criteria, MusicTag tag) {
        if(criteria==null || isEmpty(criteria.getFilterType())) {
            return true;
        } else if (Constants.FILTER_TYPE_ALBUM.equals(criteria.getFilterType())) {
            return StringUtils.equals(tag.getAlbum(), criteria.getFilterText());
        } else if (Constants.FILTER_TYPE_ARTIST.equals(criteria.getFilterType())) {
            return StringUtils.equals(tag.getArtist(), criteria.getFilterText());
        } else if (Constants.FILTER_TYPE_ALBUM_ARTIST.equals(criteria.getFilterType())) {
            return StringUtils.equals(tag.getAlbumArtist(), criteria.getFilterText());
        } else if (Constants.FILTER_TYPE_GENRE.equals(criteria.getFilterType())) {
            return StringUtils.equals(tag.getGenre(), criteria.getFilterText());
        } else if (Constants.FILTER_TYPE_PUBLISHER.equals(criteria.getFilterType())) {
            return StringUtils.equals(tag.getPublisher(), criteria.getFilterText());
        } else if (Constants.FILTER_TYPE_GROUPING.equals(criteria.getFilterType())) {
            return StringUtils.equals(tag.getGrouping(), criteria.getFilterText());
        } else if (Constants.FILTER_TYPE_PATH.equals(criteria.getFilterType())) {
            return tag.getPath().startsWith(criteria.getFilterText());
        }
        return false;
    }

    public void loadSource() {
        if(criteria == null) {
            criteria = new SearchCriteria(SearchCriteria.TYPE.MY_SONGS);
        }
        loadSource(criteria);
    }

    public void loadSource(String keyword) {
        if(criteria == null) {
            criteria = new SearchCriteria(SearchCriteria.TYPE.MY_SONGS);
        }
        criteria.setKeyword(keyword);
        loadSource(criteria);
    }

    public void loadSource(@Nullable SearchCriteria criteria) {
        if(criteria == null) {
            if(this.criteria==null) {
                criteria = new SearchCriteria(SearchCriteria.TYPE.MY_SONGS);
            }else {
                criteria = this.criteria;
            }
        }

       // if(loading) return;

       // synchronized (this) {
          //  loading = true;
            this.criteria = criteria;
        SearchCriteria finalCriteria = criteria;
        MusicMateExecutors.main(() -> {
            clearSelections();
            List<MusicTag> actionResult = MusicTagRepository.findMediaTag(finalCriteria);
            setData(actionResult);
            listener.onModelBuildFinished(null);
            loading = false;
        });

        /*
            SearchCriteria finalCriteria = criteria;
            Observable<List> observable = Observable.fromCallable(() ->  MusicTagRepository.findMediaTag(finalCriteria));
            observable.subscribeOn(Schedulers.newThread()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<List>() {
                @Override
                public void onSubscribe(Disposable d) {
                    clearSelections();
                }

                @Override
                public void onNext(List actionResult) {
                    setData(actionResult);
                }

                @Override
                public void onError(Throwable e) {
                    listener.onModelBuildFinished(null);
                    loading = false;
                }

                @Override
                public void onComplete() {
                    listener.onModelBuildFinished(null);
                    loading = false;
                }
            }); */
       // }
    }

    public String getHeaderTitle() {
        if(criteria!=null) {
            if(criteria.getType() == SearchCriteria.TYPE.MY_SONGS) {
                if(Constants.TITLE_INCOMING_SONGS.equals(criteria.getKeyword())) {
                    return Constants.TITLE_INCOMING_SONGS;
                }else if(Constants.TITLE_DUPLICATE.equals(criteria.getKeyword())) {
                    return Constants.TITLE_DUPLICATE;
                }else if(Constants.TITLE_BROKEN.equals(criteria.getKeyword())) {
                    return Constants.TITLE_BROKEN;
                }else {
                    return Constants.TITLE_ALL_SONGS;
                }
            } else if(criteria.getType() == SearchCriteria.TYPE.MEDIA_QUALITY) {
                if (Constants.QUALITY_AUDIOPHILE.equals(criteria.getKeyword())) {
                    return Constants.QUALITY_AUDIOPHILE;
                } else if (Constants.QUALITY_RECOMMENDED.equals(criteria.getKeyword())) {
                    return Constants.QUALITY_RECOMMENDED;
                } else if (!isEmpty(criteria.getKeyword())) {
                    return StringUtils.trimToEmpty(criteria.getKeyword());
                } else {
                    return Constants.QUALITY_NORMAL;
                }
            }else if(criteria.getType() == SearchCriteria.TYPE.PUBLISHER) {
                if(isEmpty(criteria.getKeyword()) || Constants.UNKWON_PUBLISHER.equals(criteria.getKeyword())) {
                    return Constants.UNKWON_PUBLISHER;
                }else {
                    return criteria.getKeyword();
                }
            } else if(criteria.getType() == SearchCriteria.TYPE.AUDIO_SQ) {
                if(Constants.AUDIO_SQ_DSD.equals(criteria.getKeyword())) {
                    return Constants.TITLE_DSD_AUDIO;
               // }else if(Constants.AUDIO_SQ_PCM_MQA.equals(criteria.getKeyword())) {
                    //return Constants.TITLE_MQA_AUDIO;
                // }else {
                //    return criteria.getKeyword();
              //  }
        //    } else if(criteria.getType() == SearchCriteria.TYPE.AUDIO_HIRES) {
              /*  else if(Constants.AUDIO_SQ_HIRES_MASTER.equals(criteria.getKeyword())) {
                    return Constants.TITLE_HR_MASTER;
                }else if(Constants.AUDIO_SQ_HIRES_LOSSLESS.equals(criteria.getKeyword())) {
                    return Constants.TITLE_HR_LOSSLESS; */
               // }else {
                //    return criteria.getKeyword();
             //   }
         //   } else if(criteria.getType() == SearchCriteria.TYPE.AUDIO_HIRES) {
               /* else if(Constants.AUDIO_SQ_HIFI_LOSSLESS.equals(criteria.getKeyword())) {
                    return Constants.TITLE_HIFI_LOSSLESS;
                } else if(Constants.AUDIO_SQ_HIFI_QUALITY.equals(criteria.getKeyword())) {
                    return Constants.TITLE_HIFI_QUALITY; */
                }else {
                    return criteria.getKeyword();
                }
           /* }else if(criteria.getType() == SearchCriteria.TYPE.SEARCH) {
                if(media.getResultType() == SearchCriteria.RESULT_TYPE.TOP_RESULT) {
                    return "Top Result";
                }else if(media.getResultType() == SearchCriteria.RESULT_TYPE.ARTIST) {
                    return "Artists";
                } else if(media.getResultType() == SearchCriteria.RESULT_TYPE.ALBUM) {
                    return "Albums";
                } else if(media.getResultType() == SearchCriteria.RESULT_TYPE.TRACKS) {
                    return "Tracks";
                }else {
                    return "Results";
                } */
            //}else if(criteria.getType() == SearchCriteria.TYPE.SEARCH_BY_ARTIST) {
            //    return criteria.getKeyword();
           // }else if(criteria.getType() == SearchCriteria.TYPE.SEARCH_BY_ALBUM) {
            //    return criteria.getKeyword();
            } else {
                return criteria.getKeyword();
            }
        }
        return Constants.TITLE_ALL_SONGS;
    }

    public double getTotalDuration() {
        return totalDuration;
    }

    public long getTotalSize() {
        return totalSize;
    }

    private MusicTagModel_ buildModel(MusicTag tag) {
        return new MusicTagModel_()
                .id(tag.getId())
                .controller(this)
                .tag(tag);
    }

    public SearchCriteria getCriteria() {
        return criteria;
    }

    public void toggleSelection(MusicTag tag) {
        if(selections.contains(tag)) {
            selections.remove(tag);
        }else {
            selections.add(tag);
        }
    }

    public int getSelectedItemCount() {
        return selections.size();
    }

    public List<MusicTag> getLastSelections() {
        return lastSelections;
    }

    public void clearSelections() {
        lastSelections.clear();
        lastSelections.addAll(selections);
        selections.clear();
        for(MusicTag tag: lastSelections) {
            notifyModelChanged(tag);
        }
    }

    public boolean isSelected(MusicTag tag) {
        return selections.contains(tag);
    }

    public ArrayList<MusicTag> getCurrentSelections() {
        return new ArrayList<>(selections);
    }

    public void clearFilter() {
        if(criteria!=null) {
            criteria.setFilterType(null);
            criteria.setFilterText(null);
            loadSource(criteria);
        }
    }

    public void toggleSelections() {
        if(selections.size() == getAdapter().getItemCount()) {
            selections.clear();
            List<MusicTag> list = new ArrayList<>();
            for(int i=0; i<getAdapter().getItemCount();i++) {
                MusicTagModel model = (MusicTagModel) getAdapter().getModelAtPosition(i);
                list.add(model.tag);
            }
            setData(list);
        }else {
            selections.clear();
            List<MusicTag> list = new ArrayList<>();
            for(int i=0; i<getAdapter().getItemCount();i++) {
                MusicTagModel model = (MusicTagModel) getAdapter().getModelAtPosition(i);
                selections.add(model.tag);
                list.add(model.tag);
            }
            setData(list);
        }
    }

    public int getAudioTagPosition(MusicTag tag) {
        long startTime = System.currentTimeMillis();
        MusicTagModel_ model = buildModel(tag);
        int position = getAdapter().getModelPosition(model);
        long endTime = System.currentTimeMillis();
        double MethodeDuration = (endTime - startTime)/1000.0;
        Timber.i("getAudioTagPosition(): "+MethodeDuration +" seconds");
        return position;
    }

    public void notifyModelChanged(MusicTag tag) {
       if(tag!= null) {
           try {
               if (isFilterMatched(criteria, tag)) {
                   MusicTagModel_ model = buildModel(tag);
                   int position = getAdapter().getModelPosition(model);
                   if (position != RecyclerView.NO_POSITION) {
                       MusicTagModel_ md = (MusicTagModel_) getAdapter().getModelAtPosition(position);
                       if (md != null) {
                           MusicTagRepository.populateAudioTag(md.tag());
                       }
                       notifyModelChanged(position);
                   }
               } else {
                   notifyModelRemoved(tag);
               }
           }catch (Exception ex) {
               Timber.e(ex);
           }
       }
    }

    public void notifyModelRemoved(MusicTag tag) {
        List<MusicTag> list = getCurrentData();
        if(tag!= null && (list != null  && list.size()>0)) {
            list.removeIf(tag::equals);
            setData(list);
        }
    }

    public boolean hasFilter() {
        if(criteria==null) return false;
        return !isEmpty(criteria.getFilterType());
    }

    public int getTotalSongs() {
        return getAdapter().getItemCount();
    }

    public List<String> getHeaderTitles(Context context) {
        List<String> titles = new ArrayList<>();
        if(criteria.getType() == SearchCriteria.TYPE.MY_SONGS) {
            titles.add(Constants.TITLE_ALL_SONGS);
            titles.add(Constants.TITLE_INCOMING_SONGS);
            titles.add(Constants.TITLE_DUPLICATE);
            titles.add(Constants.TITLE_BROKEN);
       /* }else if(criteria.getType() == SearchCriteria.TYPE.AUDIO_SQ &&
                Constants.AUDIO_SQ_PCM_MQA.equals(criteria.getKeyword())) {
            titles.add(Constants.TITLE_MQA_AUDIO);*/
        }else if(criteria.getType() == SearchCriteria.TYPE.AUDIO_SQ &&
                Constants.AUDIO_SQ_DSD.equals(criteria.getKeyword())) {
            titles.add(Constants.TITLE_DSD_AUDIO);
        }else if(criteria.getType() == SearchCriteria.TYPE.MEDIA_QUALITY) {
            titles.add(Constants.QUALITY_AUDIOPHILE);
            titles.add(Constants.QUALITY_RECOMMENDED);
            titles.add(Constants.QUALITY_NORMAL);
            titles.add(Constants.QUALITY_POOR);
        }else if(criteria.getType() == SearchCriteria.TYPE.AUDIO_SQ) {
            titles.add(Constants.TITLE_HI_QUALITY);
            titles.add(Constants.TITLE_HIFI_LOSSLESS);
            titles.add(Constants.TITLE_HIRES);
            titles.add(Constants.AUDIO_SQ_PCM_MQA);
        }else if(criteria.getType() == SearchCriteria.TYPE.GROUPING) {
            List<String> tabs = MusicTagRepository.getGroupingList(context);
            titles.addAll(tabs);
        }else if(criteria.getType() == SearchCriteria.TYPE.GENRE) {
            List<String> tabs = MusicTagRepository.getGenreList(context);
            titles.addAll(tabs);
        }else if(criteria.getType() == SearchCriteria.TYPE.PUBLISHER) {
            List<String> tabs = MusicTagRepository.getPublisherList(context);
            titles.addAll(tabs);
        }else {
            titles.add(getHeaderTitle());
        }

        return titles;
    }

    public MusicTag getAudioTag(EpoxyViewHolder holder) {
        return  ((MusicTagModel_)holder.getModel()).tag();
    }

    public void notifyModelMoved(MusicTag item) {
        // if match filter, notify change
        // else, notify remove
        notifyModelChanged(item);
        if(hasFilter()) {
            setData(getCurrentData()); // re-build data models, re-filter
        }
    }

    @Override
    protected void onExceptionSwallowed(RuntimeException exception) {
        // Best practice is to throw in debug so you are aware of any issues that Epoxy notices.
        // Otherwise Epoxy does its best to swallow these exceptions and continue gracefully
        throw exception;
    }
}