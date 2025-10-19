package apincer.music.module.nio;

import android.content.Context;

import org.jupnp.UpnpServiceConfiguration;
import org.jupnp.transport.spi.StreamServerConfiguration;

import javax.inject.Singleton;

import apincer.music.core.repository.FileRepository;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.server.NioContentServerImpl;
import apincer.music.core.server.spi.ContentServer;
import apincer.music.core.server.spi.MediaServerHub;
import apincer.music.core.server.spi.UpnpServer;
import apincer.music.server.jupnp.MediaServerConfiguration;
import apincer.music.server.jupnp.MediaServerHubImpl;
import apincer.music.server.jupnp.transport.NioUPnpServerImpl;
import apincer.music.server.jupnp.transport.StreamServerConfigurationImpl;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public class NioServerModule {

    @Provides
    public UpnpServiceConfiguration provideUpnpServiceConfiguration(@ApplicationContext Context context, UpnpServer upnpServer, ContentServer contentServer) {
        return new MediaServerConfiguration(context, upnpServer, contentServer);
    }

    @Provides
    public StreamServerConfiguration provideStreamServerConfiguration(UpnpServiceConfiguration configuration) {
        return new StreamServerConfigurationImpl(configuration.createNetworkAddressFactory().getStreamListenPort());
    }

    @Provides
    @Singleton
    public MediaServerHub provideMediaServerHub(@ApplicationContext Context context, UpnpServiceConfiguration cfg, FileRepository fileRepos, TagRepository tagRepos) {
        return new MediaServerHubImpl(context, cfg, fileRepos, tagRepos);
    }

    @Provides
    @Singleton
    public UpnpServer provideUpnpServer(@ApplicationContext Context context) {
        return new NioUPnpServerImpl(context);
    }

    @Provides
    @Singleton
    public ContentServer provideContentServer(@ApplicationContext Context context,FileRepository fileRepos, TagRepository tagRepos) {
        return new NioContentServerImpl(context, fileRepos, tagRepos);
    }
}
