package apincer.android.mmate.ui.glide;

import static apincer.android.mmate.Constants.DEFAULT_COVERART_RES;

import android.content.Context;

import androidx.annotation.NonNull;

import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.data.DataFetcher;

import java.io.InputStream;

import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.utils.ApplicationUtils;

public class CoverartFetcher implements DataFetcher<InputStream> {
    private final MusicTag model;
    private final Context context;

    public CoverartFetcher(Context context, MusicTag model) {
        this.model = model;
        this.context = context;
    }

    @Override
    public void loadData(@NonNull Priority priority, @NonNull DataCallback<? super InputStream> callback) {
        // Implement your custom data loading logic here
        // For example, you could load data from a custom source
        InputStream inputStream = FileRepository.getCoverArt(context, model);
        if(inputStream!=null) {
            callback.onDataReady(inputStream);
        }else {
            callback.onDataReady(ApplicationUtils.getAssetsAsStream(context, DEFAULT_COVERART_RES));
        }
    }

    @Override
    public void cleanup() {
        // Clean up resources if needed
    }

    @Override
    public void cancel() {
        // Handle cancellation if needed
    }

    @NonNull
    @Override
    public Class<InputStream> getDataClass() {
        return InputStream.class;
    }

    @NonNull
    @Override
    public DataSource getDataSource() {
        return DataSource.LOCAL;
    }
}