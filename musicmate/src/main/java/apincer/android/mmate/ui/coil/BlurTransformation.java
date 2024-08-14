package apincer.android.mmate.ui.coil;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import coil.size.Size;
import coil.transform.Transformation;
import kotlin.coroutines.Continuation;

public class BlurTransformation implements Transformation {
    @NonNull
    @Override
    public String getCacheKey() {
        return "";
    }

    @Nullable
    @Override
    public Object transform(@NonNull Bitmap bitmap, @NonNull Size size, @NonNull Continuation<? super Bitmap> continuation) {
        return null;
    }
}
