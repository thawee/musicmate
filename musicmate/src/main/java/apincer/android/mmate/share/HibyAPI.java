package apincer.android.mmate.share;

import java.util.Map;

import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.QueryMap;

import java.util.ArrayList;

import okhttp3.MultipartBody;
import retrofit2.http.Body;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface HibyAPI {
    @Headers({"User-Agent: Music Mate (thaweemail@gmail.com)","Cache-Control: max-stale=64000"})
    @GET("/list")
    Call<ArrayList<Song>> list(@QueryMap Map<String, String> options);

    @Headers({"User-Agent: Music Mate (thaweemail@gmail.com)","Cache-Control: max-stale=64000"})
    @GET("/download")
    Call<Song> download(@QueryMap Map<String, String> options);

    @Headers({"User-Agent: Music Mate (thaweemail@gmail.com)","Content-Type: application/x-www-form-urlencoded; charset=UTF-8", "Sec-Fetch-Dest: empty", "Sec-Fetch-Mode: cors", "Sec-Fetch-Site: same-origin"})
    @POST("/create")
    Call<Song> create(@Body RequestBody options);

    @Headers({"User-Agent: Music Mate (thaweemail@gmail.com)", "Sec-Fetch-Dest: empty", "Sec-Fetch-Mode: cors", "Sec-Fetch-Site: same-origin"})
    @Multipart
    @POST("/upload")
    Call<Song> upload(@Part MultipartBody.Part dirPart,@Part MultipartBody.Part filePart);

}

