package apincer.android.mmate.share;

import static apincer.android.mmate.Constants.http_port;

import android.app.Activity;

import net.freeutils.httpserver.HTTPServer;

import apincer.android.mmate.utils.ToastHelper;

public class HTTPStreamingServer {

    HTTPServer server = null;

    public void startServer(Activity activity) {
        try {
            server = new HTTPServer(http_port);
            HTTPServer.VirtualHost host = server.getVirtualHost(null); // default host
            host.setAllowGeneratedIndex(false); // with directory index pages
            // host.addContext("/dsf", new HTTPServer.FileContextHandler(new File("/Users/thawee.p/Workspaces/dsf")));
           // List<File> pathList = ScanAudioFileWorker.pathList(context);
           // for(File file: pathList) {
                //String simpleName = DocumentFileCompat.getBasePath(context, file.getAbsolutePath());
                //String storageId = DocumentFileCompat.getStorageId(context, file.getAbsolutePath());
                //host.addContext("/"+storageId+"/"+simpleName, new HTTPServer.FileContextHandler(file));
          //      host.addContext(file.getAbsolutePath(), new HTTPServer.FileContextHandler(file));
          //  }
            host.addContext("/music", new MusicContextHandler(activity.getApplicationContext()));
            host.addContext("/images", new ImageContextHandler(activity.getApplicationContext()));
            server.start();
            //System.out.println("HTTPServer is listening on port " + http_port);
            activity.runOnUiThread(() -> ToastHelper.showActionMessage(activity, "", "HTTP Streaming Server started, access on http:///music/tagid"));

        } catch (Exception e) {
            System.err.println("error: " + e);
            activity.runOnUiThread(() -> ToastHelper.showActionMessage(activity, "", "HTTP Streaming Server stopped, "+e.getMessage()));

        }
    }

    public void stopServer(Activity activity) {
        if(server!= null) {
            server.stop();
            server = null;
            if(activity!=null) {
                activity.runOnUiThread(() -> ToastHelper.showActionMessage(activity, "", "HTTP Streaming Server stopped."));
            }
        }
    }

    public boolean isRunning() {
        if(server == null) {
            return false;
        }
        return true;
    }
}
