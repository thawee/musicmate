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
    public MusicRoomDatabase provideMusicRoomDatabase(@ApplicationContext Context context) {
        return MusicRoomDatabase.getInstance(context);
    }

    @Provides
    @Singleton
    public DbHelper provideDbHelper(MusicRoomDatabase database, @ApplicationContext Context context) {
        return new RoomDbHelper(database, context);
    }
}
