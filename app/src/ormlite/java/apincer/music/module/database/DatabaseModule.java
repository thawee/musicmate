package apincer.music.module.database;

import android.content.Context;

import javax.inject.Singleton;

import apincer.music.core.repository.OrmLiteHelper;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public class DatabaseModule {
    @Provides
    @Singleton
    public OrmLiteHelper provideOrmLiteHelper(@ApplicationContext Context context) {
        return new OrmLiteHelper(context);
    }
}
