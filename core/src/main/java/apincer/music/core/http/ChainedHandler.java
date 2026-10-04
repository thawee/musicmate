package apincer.music.core.http;
public abstract class ChainedHandler implements NioHttpServer.Handler {
    protected NioHttpServer.Handler next;

    public ChainedHandler(NioHttpServer.Handler next) {
        this.next = next;
    }

    // Helper method to safely pass the request down the line
    protected NioHttpServer.HttpResponse next(NioHttpServer.HttpRequest request) {
        if (next != null) {
            return next.handle(request);
        }
        // Bottom of the chain - return 404 if nothing handled it
        return new NioHttpServer.HttpResponse()
                .setStatus(NioHttpServer.HTTP_NOT_FOUND, "Not Found")
                .setBody("404 End of Chain".getBytes());
    }
}