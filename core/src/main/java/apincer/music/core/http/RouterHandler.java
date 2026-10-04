package apincer.music.core.http;

import java.util.HashMap;
import java.util.Map;

public class RouterHandler implements NioHttpServer.Handler {
    private final Map<String, NioHttpServer.Handler> exactRoutes = new HashMap<>();
    private final Map<String, NioHttpServer.Handler> prefixRoutes = new HashMap<>();
    private final NioHttpServer.Handler notFoundHandler;

    public RouterHandler() {
        this.notFoundHandler = request -> new NioHttpServer.HttpResponse()
                .setStatus(404, "Not Found")
                .setBody("Route not found".getBytes());
    }

    public void get(String path, NioHttpServer.Handler handler) {
        if (path.endsWith("/*")) {
            prefixRoutes.put(path.substring(0, path.length() - 2), handler);
        } else {
            exactRoutes.put(path, handler);
        }
    }

    @Override
    public NioHttpServer.HttpResponse handle(NioHttpServer.HttpRequest request) {
        String path = request.getPath();

        // 1. Check exact matches (e.g., "/api/status")
        NioHttpServer.Handler handler = exactRoutes.get(path);
        if (handler != null) return handler.handle(request);

        // 2. Check prefix matches (e.g., "/music/222" matches "/music/*")
        for (Map.Entry<String, NioHttpServer.Handler> entry : prefixRoutes.entrySet()) {
            if (path.startsWith(entry.getKey())) {
                return entry.getValue().handle(request);
            }
        }

        // 3. Fallback to 404
        return notFoundHandler.handle(request);
    }
}