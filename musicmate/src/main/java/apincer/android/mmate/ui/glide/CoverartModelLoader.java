package apincer.android.mmate.ui.glide;

import android.content.Context;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;
import com.bumptech.glide.signature.ObjectKey;

import java.io.InputStream;

import apincer.android.mmate.repository.MusicTag;

public class CoverartModelLoader implements ModelLoader<MusicTag, InputStream> {
    Context context;
    public CoverartModelLoader(Context context) {
        this.context = context;
    }

    @Override
    public ModelLoader.LoadData<InputStream> buildLoadData(@NonNull MusicTag model, int width, int height, @NonNull Options options) {
        return new LoadData<>(new ObjectKey(model), new CoverartFetcher(context, model));
    }

    @Override
    public boolean handles(@NonNull MusicTag model) {
        return true;
    }

    public static class Factory implements ModelLoaderFactory<MusicTag, InputStream> {
        Context context;
        public Factory(Context context) {
            this.context = context;
        }

        @NonNull
        @Override
        public ModelLoader<MusicTag, InputStream> build(@NonNull MultiModelLoaderFactory multiFactory) {
            return new CoverartModelLoader(context);
        }

        @Override
        public void teardown() {
            // Do nothing
        }
    }
}