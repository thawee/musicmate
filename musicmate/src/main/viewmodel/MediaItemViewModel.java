package apincer.android.mmate.viewmodel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import apincer.android.mmate.Constants;
import apincer.android.mmate.item.HeaderItem;
import apincer.android.mmate.item.MediaItem;
import apincer.android.mmate.objectbox.AudioTag;
import apincer.android.mmate.repository.AudioTagRepository;
import apincer.android.mmate.repository.SearchCriteria;

public class MediaItemViewModel extends FlexibleViewModel<List<AudioTag>, MediaItem, SearchCriteria> {
    private AudioTagRepository metadataRepository;
    private MediaItemFactory itemFactory;
    private Stack<SearchCriteria> criteriaQueue;
    private SearchCriteria criteria;
    private Map<String,HeaderItem> headers;

    @Override
    public void loadSource(@NonNull SearchCriteria criteria) {

        if(criteria == null) {
            criteria = new SearchCriteria(SearchCriteria.TYPE.ALL);
        }

        if (criteria.getType() == SearchCriteria.TYPE.SEARCH ){
            // clear old search
            SearchCriteria ct = peekCriteria(); //criteriaQueue.peek();
            if(ct!=null && ct.getType() == SearchCriteria.TYPE.SEARCH) {
                criteriaQueue.remove(ct);
            }
            criteriaQueue.add(criteria);
        }else if(!(criteria.getType()== SearchCriteria.TYPE.SEARCH_BY_ALBUM ||
                criteria.getType()== SearchCriteria.TYPE.SEARCH_BY_ARTIST)) {
            // not search, reset all if not
            criteriaQueue.clear();
            criteriaQueue.add(criteria);
        }

        if (criteria.getType()== SearchCriteria.TYPE.SEARCH_BY_ALBUM ||
                criteria.getType()== SearchCriteria.TYPE.SEARCH_BY_ARTIST) {
            SearchCriteria ct = peekCriteria(); //criteriaQueue.peek();
            if(ct!=null && ct.getType()!= SearchCriteria.TYPE.SEARCH) {
                criteriaQueue.remove(ct);
            }
        }

        this.identifier.setValue(criteria);
    }

    public MediaItemViewModel(AudioTagRepository metadataRepository) {
        super(); // super() must be called!
        headers = new HashMap<>();
        this.metadataRepository = metadataRepository;
        this.itemFactory = new MediaItemFactory();
        criteriaQueue = new Stack<SearchCriteria>() ;
    }

    @NonNull
    @Override
    protected LiveData<List<AudioTag>> getSource(@NonNull SearchCriteria newCriteria) {
        criteria = newCriteria;
        headers.clear();
        metadataRepository.findMedia(newCriteria);
        return metadataRepository.getSearchResults();
    }

    @Override
    protected boolean isSourceValid(@Nullable List<AudioTag> mediaMetadata) {
        return mediaMetadata != null && !mediaMetadata.isEmpty();
    }

    @Override
    protected List<MediaItem> map(@NonNull List<AudioTag> mediaMetadata) {
        //mediaCount = 0;
        //mediaPlayDuration = 0;
        headers.clear();
        return FlexibleItemProvider
                .with(itemFactory)
                .from(mediaMetadata);
    }

    public void update(AudioTag note) {
        metadataRepository.updateMedia(note);
    }
    public void delete(AudioTag note) {
        metadataRepository.deleteMedia(note.getMediaPath());
        HeaderItem header = headers.get(note.getResultType().name());
        if(header == null) {
            header.setCount(header.getCount() - 1);
            header.setDuration(header.getDuration() - note.getAudioDuration());
        }
    }

    public String getHeaderTitle(AudioTag media) {
        if(criteria!=null) {
            if(criteria.getType() == SearchCriteria.TYPE.ALL) {
                return Constants.TITLE_ALL_SONGS;
            }else if(criteria.getType() == SearchCriteria.TYPE.DOWNLOAD) {
                return Constants.TITLE_INCOMING_SONGS;
            }else if(criteria.getType() == SearchCriteria.TYPE.SIMILAR_TITLE) {
                return Constants.TITLE_SIMILAR_TITLE;
            }else if(criteria.getType() == SearchCriteria.TYPE.SIMILAR_TITLE_ARTIST) {
                return Constants.TITLE_SIMILAR_SONGS;
            }else if(criteria.getType() == SearchCriteria.TYPE.AUDIO_SQ) {
                if(Constants.AUDIO_SQ_DSD.equals(criteria.getKeyword())) {
                    return Constants.TITLE_DSD_AUDIO;
                }else if(Constants.AUDIO_SQ_PCM_SD.equals(criteria.getKeyword())) {
                    return Constants.TITLE_SD_AUDIO;
                }else if(Constants.AUDIO_SQ_PCM_HD.equals(criteria.getKeyword())) {
                    return Constants.TITLE_HD_AUDIO;
                }else if(Constants.AUDIO_SQ_PCM_MQA.equals(criteria.getKeyword())) {
                    return Constants.TITLE_MQA_AUDIO;
                }else if(Constants.AUDIO_SQ_PCM_LD.equals(criteria.getKeyword())) {
                    return Constants.TITLE_LD_AUDIO;
                }else {
                    return criteria.getKeyword();
                }
            }else if(criteria.getType() == SearchCriteria.TYPE.SEARCH) {
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
                }
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

    public void loadSource(boolean reloaded) {
        criteria = peekCriteria(); //criteriaQueue.peek();
        if(criteria == null) {
            criteria = new SearchCriteria(SearchCriteria.TYPE.ALL);
        }else if(!reloaded){
            criteriaQueue.remove(criteria);
        }
        this.identifier.setValue(criteria);
    }

    public SearchCriteria peekCriteria() {
        if(!criteriaQueue.isEmpty()) {
            return criteriaQueue.peek();
        }
        return null;
    }

    class MediaItemFactory
            implements FlexibleItemProvider.Factory<AudioTag, AudioTag> {
        @NonNull
        @Override
        public AudioTag create(AudioTag media) {
            // Equivalent of: new HolderMessageItem(message);
            AudioTag item = FlexibleFactory.create(AudioTag.class, media);
            HeaderItem header = headers.get(media.getResultType().name());
            if(header == null) {
                header = new HeaderItem(getHeaderTitle(media));
                header.setSearchCriteria(criteria);
                header.setResultType(media.getResultType());
                headers.put(media.getResultType().name(), header);
            }
            //if(criteria.getType() != SearchCriteria.TYPE.SEARCH) {
            //    item.setHeader(header);
            //}
            header.setCount(header.getCount()+1);
            header.setDuration(header.getDuration()+media.getAudioDuration());
            item.setHeader(header);
            //mediaCount++;
            //mediaPlayDuration += media.getAudioDuration();
            return item;
        }
    }
}
