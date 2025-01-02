package apincer.android.mmate.ui.glide;

import android.content.Context;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.module.AppGlideModule;

import java.io.InputStream;

import apincer.android.mmate.repository.MusicTag;

@GlideModule
public class MusicMateGlideModule extends AppGlideModule {
    @Override
    public void registerComponents(@NonNull Context context, @NonNull Glide glide, Registry registry) {
        registry.append(MusicTag.class, InputStream.class, new CoverartModelLoader.Factory(context));
    }
}
