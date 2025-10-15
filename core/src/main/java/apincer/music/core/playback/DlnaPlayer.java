package apincer.music.core.playback;

import android.content.Context;
import android.graphics.drawable.Drawable;

import apincer.music.core.database.MusicTag;
import apincer.music.core.server.RendererDevice;

public class DlnaPlayer implements Player {
    private final Context context;
    private RendererDevice renderer;
    private String rendererUdn;

    public DlnaPlayer(Context context, RendererDevice renderer) {
        this.context = context;
        this.renderer = renderer;
    }

    public DlnaPlayer(Context context, String rendererUdn) {
        this.context = context;
        this.rendererUdn = rendererUdn;
    }

    @Override
    public String getDisplayName() {
        return renderer.getFriendlyName();
    }

    @Override
    public String getId() {
        return renderer.getUdn();
    }

    @Override
    public Drawable getIcon() {
        return null;
    }

    @Override
    public void play(MusicTag song) {
        // This method is used when playing a single song, not a queue
       /* if (isBound) {
            playbackService.playToRenderer(renderer.getUdn(), song);
          //  rendererController.playSong(renderer.getIdentity().getUdn().getIdentifierString(), song);
        } */
    }

    @Override
    public void next() {

    }

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
        return renderer.getDescription();
    }

   /* public void unbind() {
        if (isBound) {
            context.unbindService(serviceConnection);
            isBound = false;
        }
    } */

    /*
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            PlaybackService.PlaybackServiceBinder binder = (PlaybackService.PlaybackServiceBinder) service;
            playbackService = binder.getService();
            isBound = true;
            if(renderer == null) {
                renderer = playbackService.getRendererByUDN(rendererUdn);
            } */
           /* if(renderer != null) {
                rendererController = playbackService.getRendererController();
                rendererController.setupRender(renderer.getIdentity().getUdn().getIdentifierString());
            } */
      /*  }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    }; */

    public String getLocation() {
        return renderer.getHost();
    }
}