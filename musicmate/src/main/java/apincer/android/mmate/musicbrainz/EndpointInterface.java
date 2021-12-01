package apincer.android.mmate.musicbrainz;

import java.util.Map;

import apincer.android.mmate.musicbrainz.coverart.Coverart;
import apincer.android.mmate.musicbrainz.recording.Recording;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.Path;
import retrofit2.http.QueryMap;
import retrofit2.http.Url;

/**
 * Created by Administrator on 12/20/17.
 */

public interface EndpointInterface {
    // Request method and URL specified in the annotation
    // Callback for the parsed response is the last parameter

    @Headers({"User-Agent: Music Mate/1.0-dev (thaweemail@gmail.com)","Cache-Control: max-stale=64000"})
    @GET("/ws/2/recording/")
    Call<Recording> findRecordings(@QueryMap(encoded=true) Map<String, String> options);

    @Headers({"User-Agent: Music Mate/1.0-dev (thaweemail@gmail.com)","Cache-Control: max-stale=64000"})
    @GET("/release/{albumId}")
    Call<Coverart> getCoverart(@Path("albumId") String albumid);

    @GET
    Call<ResponseBody> downloadFile(@Url String url);

}
