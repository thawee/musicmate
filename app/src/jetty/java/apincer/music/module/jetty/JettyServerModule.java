package apincer.music.module.jetty;

import android.content.Context;

import org.jupnp.UpnpServiceConfiguration;
import org.jupnp.transport.spi.StreamServerConfiguration;

import apincer.android.jupnp.transport.jetty.JettyContentServerImpl;
import apincer.android.jupnp.transport.jetty.JettyUPnpServerImpl;
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
public class JettyServerModule {

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
        return new JettyUPnpServerImpl(context);
    }

    @Provides
    public ContentServer provideContentServer(@ApplicationContext Context context, FileRepository fileRepos, TagRepository tagRepos) {
        return new JettyContentServerImpl(context, fileRepos, tagRepos);
    }
}
