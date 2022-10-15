package apincer.android.mmate.epoxy;

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
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class MusicTagController extends TypedEpoxyController<List<MusicTag>> {
    private MusicTagRepository tagRepos;
    private SearchCriteria criteria;
    private long totalDuration = 0;
    private long totalSize = 0;
    private View.OnClickListener clickListener;
    private View.OnLongClickListener longClickListener;
    private ArrayList<MusicTag> selections;
    private ArrayList<MusicTag> lastSelections;
    private OnModelBuildFinishedListener listener;
    public static volatile boolean loading  = false;

    public MusicTagController(View.OnClickListener clickListener, View.OnLongClickListener longClickListener) {
        tagRepos = MusicTagRepository.getInstance(); //new AudioTagRepository();
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
        boolean noFilters = StringUtils.isEmpty(criteria.getFilterType()) && StringUtils.isEmpty(criteria.getFilterText());
        if(noFilters) {
            for (MusicTag tag : audioTags) {
                new MusicTagModel_()
                        .id(tag.getId())
                        .tag(tag)
                        .controller(this)
                        .clickListener(clickListener)
                        .longClickListener(longClickListener)
                        .addTo(this);
                // model.onClickListener(clickListener);
                // model.onLongClickListener(longClickListener);
                // add(model);
                totalDuration = totalDuration + tag.getAudioDuration();
                totalSize = totalSize + tag.getFileSize();
            }
        }else {
            for (MusicTag tag : audioTags) {
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
                } else if (Constants.FILTER_TYPE_GROUPING.equals(criteria.getFilterType())) {
                    if (!StringUtils.equals(tag.getGrouping(), criteria.getFilterText())) {
                        continue;
                    }
                } else if (Constants.FILTER_TYPE_PATH.equals(criteria.getFilterType())) {
                    if (!tag.getPath().startsWith(criteria.getFilterText())) {
                        continue;
                    }
                }
                new MusicTagModel_()
                        .id(tag.getId())
                        .tag(tag)
                        .controller(this)
                        .clickListener(clickListener)
                        .longClickListener(longClickListener)
                        .addTo(this);
                // model.onClickListener(clickListener);
                // model.onLongClickListener(longClickListener);
                // add(model);
                totalDuration = totalDuration + tag.getAudioDuration();
                totalSize = totalSize + tag.getFileSize();
            }
        }
    }

    public void loadSource() {
        if(criteria == null) {
            criteria = new SearchCriteria(SearchCriteria.TYPE.MY_SONGS);
        }
        loadSource(criteria);
    }

    public void loadSource(boolean resetSearch) {
        if(criteria!=null && resetSearch) {
            criteria.resetSearch();
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

        if(loading) return;

       // synchronized (this) {
            loading = true;
            this.criteria = criteria;

            SearchCriteria finalCriteria = criteria;
            Observable<List> observable = Observable.fromCallable(() -> tagRepos.findMediaTag(finalCriteria));
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
            });
       // }
    }

    public String getHeaderTitle() {
        if(criteria!=null) {
            if(criteria.getType() == SearchCriteria.TYPE.MY_SONGS) {
                if(Constants.TITLE_INCOMING_SONGS.equals(criteria.getKeyword())) {
                    return Constants.TITLE_INCOMING_SONGS;
                }else if(Constants.TITLE_DUPLICATE.equals(criteria.getKeyword())) {
                    return Constants.TITLE_DUPLICATE;
                }else {
                    return Constants.TITLE_ALL_SONGS;
                }
            } else if(criteria.getType() == SearchCriteria.TYPE.AUDIOPHILE) {
                return Constants.TITLE_AUDIOPHILE;
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
            }else if(criteria.getType() == SearchCriteria.TYPE.SEARCH_BY_ARTIST) {
                return criteria.getKeyword();
            }else if(criteria.getType() == SearchCriteria.TYPE.SEARCH_BY_ALBUM) {
                return criteria.getKeyword();
            } else {
                return criteria.getKeyword();
            }
        }
        return Constants.TITLE_ALL_SONGS;
    }

    public long getTotalDuration() {
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
        ArrayList<MusicTag> tags = new ArrayList<>();
        for(MusicTag tag: selections) {
            tags.add(tag);
        }
        return tags;
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
        MusicTagModel_ model = buildModel(tag);
        return getAdapter().getModelPosition(model);
    }

    public void notifyModelChanged(MusicTag tag) {
       if(tag!= null) {
           MusicTagModel_ model = buildModel(tag);
            int position = getAdapter().getModelPosition(model);
            if (position != RecyclerView.NO_POSITION) {
                MusicTagModel_ md = (MusicTagModel_) getAdapter().getModelAtPosition(position);
                if (md != null) {
                    tagRepos.populateAudioTag(md.tag());
                }
                notifyModelChanged(position);
            }
        }
    }

    public void notifyModelRemoved(MusicTag tag) {
        if(tag!= null) {
            List<MusicTag> list = getCurrentData();
            for(MusicTag item: list) {
                if(tag.equals(item)) {
                    list.remove(item);
                }
            }
            setData(list);
        }
    }

    public boolean hasFilter() {
        if(criteria==null) return false;
        return !StringUtils.isEmpty(criteria.getFilterType());
    }

    public int getTotalSongs() {
        return getAdapter().getItemCount();
    }

    public List<String> getHeaderTitles(Context context) {
        List<String> titles = new ArrayList<>();
        if(criteria.getType() == SearchCriteria.TYPE.MY_SONGS) {
            titles.add(Constants.TITLE_ALL_SONGS);
           // titles.add(Constants.TITLE_AUDIOPHILE);
            titles.add(Constants.TITLE_INCOMING_SONGS);
            titles.add(Constants.TITLE_DUPLICATE);
       /* }else if(criteria.getType() == SearchCriteria.TYPE.AUDIO_SQ &&
                Constants.AUDIO_SQ_PCM_MQA.equals(criteria.getKeyword())) {
            titles.add(Constants.TITLE_MQA_AUDIO);*/
        }else if(criteria.getType() == SearchCriteria.TYPE.AUDIO_SQ &&
                Constants.AUDIO_SQ_DSD.equals(criteria.getKeyword())) {
            titles.add(Constants.TITLE_DSD_AUDIO);
        }else if(criteria.getType() == SearchCriteria.TYPE.AUDIO_SQ) {// &&
            //    !(Constants.AUDIO_SQ_DSD.equals(criteria.getKeyword()) || Constants.AUDIO_SQ_PCM_MQA.equals(criteria.getKeyword()))) {
            titles.add(Constants.TITLE_HIFI_QUALITY);
            titles.add(Constants.TITLE_HIFI_LOSSLESS);
            titles.add(Constants.TITLE_HIRES);
            titles.add(Constants.AUDIO_SQ_PCM_MQA);
        }else if(criteria.getType() == SearchCriteria.TYPE.GROUPING) {
            List<String> tabs = tagRepos.getGroupingList(context);
            for(String tab: tabs) {
                titles.add(tab);
            }
        }else if(criteria.getType() == SearchCriteria.TYPE.GENRE) {
            List<String> tabs = tagRepos.getGenreList(context);
            for(String tab: tabs) {
                titles.add(tab);
            }
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
}