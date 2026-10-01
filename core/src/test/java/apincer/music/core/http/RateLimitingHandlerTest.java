package apincer.music.core.http;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class RateLimitingHandlerTest {

    private static NioHttpServer.HttpRequest request(String path) {
        byte[] raw = ("GET " + path + " HTTP/1.1\r\nHost: test\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
        NioHttpServer.HttpRequest request = new NioHttpServer.HttpRequest();
        request.parse(raw, raw.length, "192.168.1.20");
        return request;
    }

    private static final NioHttpServer.Handler OK = r -> new NioHttpServer.HttpResponse();

    @Test
    public void requestsOverTheLimit_get429() {
        // The limiter counts per wall-clock second; retry if a run straddles a second boundary
        for (int attempt = 0; attempt < 5; attempt++) {
            RateLimitingHandler limiter = new RateLimitingHandler(50, OK);
            long second = System.currentTimeMillis() / 1000;
            int limited = 0;
            for (int i = 0; i < 60; i++) {
                if (limiter.handle(request("/music/" + i)).statusCode == 429) limited++;
            }
            if (System.currentTimeMillis() / 1000 == second) {
                assertEquals(10, limited);
                return;
            }
        }
        throw new AssertionError("could not complete a run within one second");
    }

    @Test
    public void exemptPaths_areNotCountedOrLimited() {
        // A WebUI cover grid loads many images at once from one browser
        RateLimitingHandler limiter = new RateLimitingHandler(50, path -> path.startsWith("/coverart/"), OK);
        for (int i = 0; i < 200; i++) {
            assertEquals(200, limiter.handle(request("/coverart/album-" + i)).statusCode);
        }
        assertEquals(200, limiter.handle(request("/music/1")).statusCode); // covers did not use up the budget
    }
}
