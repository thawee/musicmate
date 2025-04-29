package apincer.android.mmate.web;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import android.content.Context;
import android.util.Log;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.Callback;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import apincer.android.mmate.utils.ApplicationUtils;

public class MusicWebHandler extends ResourceHandler {
    private static final String TAG = "MusicWebHandler";
    private File webRoot;

    public Context getContext() {
        return context;
    }

    Context context;

    public MusicWebHandler(Context context) {
        this.context = context;
        // Create a directory for web resources if it doesn't exist
        webRoot = new File(context.getFilesDir(), "web");
        if (!webRoot.exists()) {
            webRoot.mkdirs();
        }

        // Always extract web resources to ensure we have the latest version
        extractWebResources();
    }

    private void extractWebResources() {
        try {
            // Extract web resources from assets to the web directory
            // You'll need to add these files to your assets folder
            String[] webFiles = {"index.html", "styles.css", "app.js"};
            for (String file : webFiles) {
                InputStream in = ApplicationUtils.getAssetsAsStream(getContext(), "web/" + file);
                if (in != null) {
                    File outFile = new File(webRoot, file);
                    Files.copy(in, outFile.toPath(), REPLACE_EXISTING);
                    in.close();
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to extract web resources", e);
        }
    }

    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception {
        // Set appropriate headers for web content
        response.getHeaders().put(HttpHeader.CACHE_CONTROL, "no-cache");

        // Try to serve the requested file from the web directory
        String path = request.getHttpURI().getPath().substring("/music".length());
        if (path.isEmpty() || path.equals("/")) {
            path = "/index.html";
        }

        File requestedFile = new File(webRoot, path);
        if (requestedFile.exists() && requestedFile.isFile()) {
            // Set content type based on file extension
            if (path.endsWith(".html")) {
                response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/html; charset=utf-8");
            } else if (path.endsWith(".css")) {
                response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/css; charset=utf-8");
            } else if (path.endsWith(".js")) {
                response.getHeaders().put(HttpHeader.CONTENT_TYPE, "application/javascript; charset=utf-8");
            }

            // Serve the file
            Content.Sink.write(response, true, new String(Files.readAllBytes(requestedFile.toPath()), StandardCharsets.UTF_8), callback);

            return true;
        }

        // If file not found, return 404
        response.setStatus(HttpStatus.NOT_FOUND_404);
        Content.Sink.write(response, true, "<h1>File Not Found</h1>", callback);
        return true;
    }
}
