package apincer.android.mmate.playback;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.Drawable;
import android.os.IBinder;

import org.jupnp.model.meta.RemoteDevice;

import apincer.android.mmate.dlna.RendererController;
import apincer.android.mmate.repository.database.MusicTag;

public class DlnaPlayer implements Player {
    private final Context context;
    private  RemoteDevice renderer;
    private String rendererUdn;
    private RendererController rendererController;
    private PlaybackService playbackService;
    private boolean isBound = false;

    public DlnaPlayer(Context context, RemoteDevice renderer) {
        this.context = context;
        this.renderer = renderer;
        Intent intent = new Intent(context, PlaybackService.class);
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    public DlnaPlayer(Context context, String rendererUdn) {
        this.context = context;
        this.rendererUdn = rendererUdn;
        Intent intent = new Intent(context, PlaybackService.class);
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public String getDisplayName() {
        return renderer.getDetails().getFriendlyName();
    }

    @Override
    public String getId() {
        return renderer.getIdentity().getUdn().getIdentifierString();
    }

    @Override
    public Drawable getIcon() {
        return null;
    }

    @Override
    public void play(MusicTag song) {
        // This method is used when playing a single song, not a queue
        if (isBound) {
            rendererController.playSong(renderer.getIdentity().getUdn().getIdentifierString(), song);
        }
    }

    @Override
    public void next() {

    }

    /*
    public void play(List<MusicTag> queue, int position) {
        if (isBound) {
            currentQueue.clear();
            currentQueue.addAll(queue);
            currentPlayingIndex = position;
            MusicTag song = currentQueue.get(currentPlayingIndex);
            rendererController.playSong(renderer.getIdentity().getUdn().getIdentifierString(), song);
        }
    } */

    @Override
    public void pause() {
        // Not implemented yet for DLNA, requires specific UPnP action
    }

    @Override
    public void resume() {
        // Not implemented yet for DLNA, requires specific UPnP action
    }

    @Override
    public void stop() {
        // Not implemented yet for DLNA, requires specific UPnP action
    }

    @Override
    public String getDetails() {
        return renderer.getDetails().getModelDetails().getModelDescription();
    }

    public void unbind() {
        if (isBound) {
            context.unbindService(serviceConnection);
            isBound = false;
        }
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            PlaybackService.PlaybackServiceBinder binder = (PlaybackService.PlaybackServiceBinder) service;
            playbackService = binder.getService();
            isBound = true;
            if(renderer == null) {
                renderer = playbackService.getRendererByUDN(rendererUdn);
            }
            if(renderer != null) {
                rendererController = playbackService.getRendererController();
                rendererController.setupRender(renderer.getIdentity().getUdn().getIdentifierString());
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    public String getLocation() {
        return renderer.getIdentity().getDescriptorURL().getHost();
    }
}