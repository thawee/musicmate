package apincer.music.room;

import android.content.Context;

import javax.inject.Singleton;

import apincer.music.core.repository.spi.DbHelper;
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
    public DbHelper provideDbHelper(@ApplicationContext Context context) {
        // Temporary placeholder until Room DAO and entity implementation is completed
        return null;
    }
}
