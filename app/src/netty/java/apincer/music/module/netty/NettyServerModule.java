package apincer.music.module.netty;

import android.content.Context;

import org.jupnp.UpnpServiceConfiguration;
import org.jupnp.transport.spi.StreamServerConfiguration;

import apincer.android.jupnp.transport.netty.NettyContentServerImpl;
import apincer.android.jupnp.transport.netty.NettyUPnpServerImpl;
import apincer.music.core.repository.FileRepository;
import apincer.music.core.repository.TagRepository;
import apincer.music.core.server.spi.ContentServer;
import apincer.music.core.server.spi.MediaServerHub;
import apincer.music.core.server.spi.UpnpServer;
import apincer.music.server.jupnp.MediaServerConfiguration;
import apincer.music.server.jupnp.MediaServerHubImpl;
import apincer.music.server.jupnp.transport.StreamServerConfigurationImpl;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public class NettyServerModule {
    @Provides
    public UpnpServiceConfiguration provideUpnpServiceConfiguration(@ApplicationContext Context context, UpnpServer upnpServer, ContentServer contentServer) {
        return new MediaServerConfiguration(context, upnpServer, contentServer);
    }

    @Provides
    public StreamServerConfiguration provideStreamServerConfiguration(UpnpServiceConfiguration configuration) {
        return new StreamServerConfigurationImpl(configuration.createNetworkAddressFactory().getStreamListenPort());
    }

    @Provides
    public MediaServerHub provideMediaServerHub(@ApplicationContext Context context, UpnpServiceConfiguration cfg, FileRepository fileRepos, TagRepository tagRepos) {
        return new MediaServerHubImpl(context, cfg, fileRepos, tagRepos);
    }

    @Provides
    public UpnpServer provideUpnpServer(@ApplicationContext Context context) {
        return new NettyUPnpServerImpl(context);
    }

    @Provides
    public ContentServer provideContentServer(@ApplicationContext Context context, FileRepository fileRepos, TagRepository tagRepos) {
        return new NettyContentServerImpl(context, fileRepos, tagRepos);
    }
}
