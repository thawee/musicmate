package apincer.android.mmate.hiby;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.repository.FileRepository;
import apincer.android.utils.FileUtils;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class SyncHibyPlayer {
    private String baseDir = "/mnt/sd_0/";
   // private static String url = "http://10.100.1.242:4399/";

    public static void list(String hibyEndpoint, String path) {
        Retrofit ret = createHibyRetrofit(hibyEndpoint);
        API api = ret.create(API.class);
        Map<String, String> map = new HashMap();
        map.put("path", "/mnt/sd_0/"+path+"/");
        Call call =  api.list(map);
        Response response;
        try {
            response = call.execute();
            System.out.println(response.body());

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private static void createDir(String hibyEndpoint,String dir) {
        Retrofit ret = createHibyRetrofit(hibyEndpoint);
        API api = ret.create(API.class);
        try {
            //Map map = new HashMap();
            //map.put("path", "/mnt/sd_0/"+dir+"/");  //?path=/mnt/sd_0/{dir}/
            String path = URLEncoder.encode("/mnt/sd_0/"+dir, "UTF-8");
           // MultipartBody.Part dirPart = MultipartBody.Part.createFormData("path", path);
            RequestBody body = RequestBody.create("path="+path, MediaType.parse("application/x-www-form-urlencoded; charset=UTF-8"));
            Call call =  api.create(body);// api.create("path="+ path);
          //  Call call =  api.create(dirPart);
            Response response;

            response = call.execute();
            System.out.println(response.body());

        } catch (Exception e) {
            Log.e("SyncHibyDAP", e.getMessage());
          //  e.printStackTrace();
        }

    }

    private static void upload(String hibyEndpoint,String dir, String filename, File file) throws IOException {
        Retrofit ret = createHibyRetrofit(hibyEndpoint);
        API api = ret.create(API.class);
     //   try {
            //Map map = new HashMap();
            //map.put("path", "/mnt/sd_0/"+dir+"/");  //?path=/mnt/sd_0/{dir}/
            String path = "/mnt/sd_0/"+dir+"/";
            String ext = FileUtils.getExtension(file);
            ext = ext.toLowerCase();

            MultipartBody.Part dirPart = MultipartBody.Part.createFormData("path", path);
            MultipartBody.Part filePart = MultipartBody.Part.createFormData("files[]", filename, RequestBody.create(MediaType.parse("audio/"+ext), file));

            Call call =  api.upload(dirPart, filePart);
            Response response;

            response = call.execute();
            System.out.println(response.body());

      //  } catch (IOException e) {
     //       e.printStackTrace();
     //   }
    }

    private static Retrofit createHibyRetrofit(String hibyEndpoint) {
        return new Retrofit.Builder()
                .baseUrl(hibyEndpoint)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    public static void sync(Context context, String url, MusicTag tag) throws IOException{
       // String url = "http://10.100.1.96:4399/";  // via Mac
       // String url = "http://10.100.1.242:4399/";
        String targetPath = FileRepository.newInstance(context).buildCollectionPath(tag, false);
        String targetDir = FileUtils.getFolderName(targetPath);
        String targetFilename = FileUtils.getFullFileName(targetPath);
        createDirs(url, targetDir);
        upload(url, targetDir, targetFilename, new File(tag.getPath()));
       // send image file if existed
       File cover = FileRepository.getFolderCoverArt(tag);
       if(cover != null) {
            String ext = FileUtils.getExtension(cover);
            upload(url, targetDir, "cover."+ext, cover);
       }
    }

    private static void createDirs(String url, String targetDir) {
        String[] paths = targetDir.split("/");
        String dir = "";
        for(String path: paths) {
            dir = dir+path+"/";
            createDir(url, dir);
        }
    }
}
