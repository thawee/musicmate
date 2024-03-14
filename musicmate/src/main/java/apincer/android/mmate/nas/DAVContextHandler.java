package apincer.android.mmate.nas;

import static apincer.android.mmate.utils.MusicTagUtils.getExtension;
import static apincer.android.mmate.utils.StringUtils.isEmpty;

import android.content.Context;

import net.freeutils.httpserver.HTTPServer;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import apincer.android.mmate.MusixMateApp;
import apincer.android.mmate.R;
import apincer.android.mmate.broadcast.AudioTagPlayingEvent;
import apincer.android.mmate.broadcast.MusicPlayerInfo;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.repository.MusicTagRepository;
import apincer.android.mmate.utils.Mime;
import apincer.android.mmate.utils.StringUtils;

public class DAVContextHandler implements HTTPServer.ContextHandler {
  //  private final HttpManager httpManager;

    public static int PAGE_SIZE = 100;

    public static String HTML_HEADER_START ="<html><head><meta charset=\"utf-8\"><meta name=\"google\" value=\"notranslate\"><meta name=\"color-scheme\" content=\"light\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
            "<title id=\"title\">MusicMate FS %s</title>";
    public static String HTML_HEADER_FOOT ="</head>";
    public static String HTML_STYLE_SHEET ="<style>h1 {  border-bottom: 1px solid #c0c0c0;  margin-bottom: 10px;  padding-bottom: 10px;  white-space: nowrap;}\n" +
            "body {  padding-inline: 2%;  margin-top: 20px;}\n" +
            ".nameColumnHeader {  width: 100%;}\n" +
            ".dateColumnHeader {   width: 200px;}\n" +
            ".sizeColumnHeader {  width: 100px;}\n" +
            "table {  width: 100%;  max-width: 1050px;  margin-top: 15px;  table-layout: fixed;  overflow: hidden;}\n" +
            "td:first-child { text-overflow: ellipsis; overflow: hidden;  text-wrap: nowrap;}\n" +
            "td.detailsColumn {  padding-inline-start: 15px;  text-align: end;}\n" +
            "a.icon {  padding-inline-start: 1.5em;  text-decoration: none;  user-select: auto;}\n" +
            "a.icon:hover {  text-decoration: underline;}\n" +
            "a.file {\n" +
            "  background : url(\"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAIAAACQkWg2AAAABnRSTlMAAAAAAABupgeRAAABEElEQVR42nRRx3HDMBC846AHZ7sP54BmWAyrsP588qnwlhqw/k4v5ZwWxM1hzmGRgV1cYqrRarXoH2w2m6qqiqKIR6cPtzc3xMSML2Te7XZZlnW7Pe/91/dX47WRBHuA9oyGmRknzGDjab1ePzw8bLfb6WRalmW4ip9FDVpYSWZgOp12Oh3nXJ7nxoJSGEciteP9y+fH52q1euv38WosqA6T2gGOT44vry7BEQtJkMAMMpa6JagAMcUfWYa4hkkzAc7fFlSjwqCoOUYAF5RjHZPVCFBOtSBGfgUDji3c3jpibeEMQhIMh8NwshqyRsBJgvF4jMs/YlVR5KhgNpuBLzk0OcUiR3CMhcPaOzsZiAAA/AjmaB3WZIkAAAAASUVORK5CYII=\") left top no-repeat;\n" +
            "}\n" +
            "a.dir {\n" +
            "  background : url(\"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAABt0lEQVR42oxStZoWQRCs2cXdHTLcHZ6EjAwnQWIkJyQlRt4Cd3d3d1n5d7q7ju1zv/q+mh6taQsk8fn29kPDRo87SDMQcNAUJgIQkBjdAoRKdXjm2mOH0AqS+PlkP8sfp0h93iu/PDji9s2FzSSJVg5ykZqWgfGRr9rAAAQiDFoB1OfyESZEB7iAI0lHwLREQBcQQKqo8p+gNUCguwCNAAUQAcFOb0NNGjT+BbUC2YsHZpWLhC6/m0chqIoM1LKbQIIBwlTQE1xAo9QDGDPYf6rkTpPc92gCUYVJAZjhyZltJ95f3zuvLYRGWWCUNkDL2333McBh4kaLlxg+aTmyL7c2xTjkN4Bt7oE3DBP/3SRz65R/bkmBRPGzcRNHYuzMjaj+fdnaFoJUEdTSXfaHbe7XNnMPyqryPcmfY+zURaAB7SHk9cXSH4fQ5rojgCAVIuqCNWgRhLYLhJB4k3iZfIPtnQiCpjAzeBIRXMA6emAqoEbQSoDdGxFUrxS1AYcpaNbBgyQBGJEOnYOeENKR/iAd1npusI4C75/c3539+nbUjOgZV5CkAU27df40lH+agUdIuA/EAgDmZnwZlhDc0wAAAABJRU5ErkJggg==\") left top no-repeat;\n" +
            "}\n" +
            "@media (max-width: 980px) {   .dateColumnHeader {      width: 135px;   }}\n" +
            "a.up {\n" +
            "  background : url(\"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAACM0lEQVR42myTA+w1RxRHz+zftmrbdlTbtq04qRGrCmvbDWp9tq3a7tPcub8mj9XZ3eHOGQdJAHw77/LbZuvnWy+c/CIAd+91CMf3bo+bgcBiBAGIZKXb19/zodsAkFT+3px+ssYfyHTQW5tr05dCOf3xN49KaVX9+2zy1dX4XMk+5JflN5MBPL30oVsvnvEyp+18Nt3ZAErQMSFOfelCFvw0HcUloDayljZkX+MmamTAMTe+d+ltZ+1wEaRAX/MAnkJdcujzZyErIiVSzCEvIiq4O83AG7LAkwsfIgAnbncag82jfPPdd9RQyhPkpNJvKJWQBKlYFmQA315n4YPNjwMAZYy0TgAweedLmLzTJSTLIxkWDaVCVfAbbiKjytgmm+EGpMBYW0WwwbZ7lL8anox/UxekaOW544HO0ANAshxuORT/RG5YSrjlwZ3lM955tlQqbtVMlWIhjwzkAVFB8Q9EAAA3AFJ+DR3DO/Pnd3NPi7H117rAzWjpEs8vfIqsGZpaweOfEAAFJKuM0v6kf2iC5pZ9+fmLSZfWBVaKfLLNOXj6lYY0V2lfyVCIsVzmcRV9Y0fx02eTaEwhl2PDrXcjFdYRAohQmS8QEFLCLKGYA0AeEakhCCFDXqxsE0AQACgAQp5w96o0lAXuNASeDKWIvADiHwigfBINpWKtAXJvCEKWgSJNbRvxf4SmrnKDpvZavePu1K/zu/due1X/6Nj90MBd/J2Cic7WjBp/jUdIuA8AUtd65M+PzXIAAAAASUVORK5CYII=\") left top no-repeat;\n" +
            "}</style>";
    public static String HTML_LINK_UP ="<table>\n" +
            "  <thead> <tr class=\"header theader\">  <th class=\"nameColumnHeader detailsColumn\"></th>  <th class=\"sizeColumnHeader detailsColumn\"></th> <th class=\"dateColumnHeader detailsColumn\"></th> </tr></thead>\n" +
            "  <tbody id=\"tbody\">\n" +
            "<tr><td data-value=\"..\"><a class=\"icon up\" draggable=\"true\" href=\"%s\">[...]</a></td><td class=\"detailsColumn\" data-value=\"0\"></td><td class=\"detailsColumn\" data-value=\"1707469629625\"></td></tr>\n" +
            "</tbody></table>\n";
    public static String HTML_ITEM_HEADER = "<table>\n" +
            "  <thead>  <tr class=\"header theader\">  <th class=\"nameColumnHeader detailsColumn\"></th>  <th class=\"sizeColumnHeader detailsColumn\"></th>  <th class=\"dateColumnHeader detailsColumn\"></th>  </tr> </thead> <tbody id=\"tbody\">";
    public static String HTML_ITEM_FOLDER = "<tr>\n" +
            "\t<td data-value=\"%s\"><a class=\"icon dir\" draggable=\"true\" href=\"%s\">%s</a></td>\n" +
            "\t<td class=\"detailsColumn\" data-value=\"0\" style=\"padding-right: 5px;\"></td>\n" +
            "\t<td class=\"detailsColumn\" data-value=\"1701833898167\">%td-%<tb-%<tY %<tR</td>\n" +
            "</tr>";
    public static String HTML_ITEM_FILE = "<tr>\n" +
            "\t<td data-value=\"%s\"><a class=\"icon file\" draggable=\"true\" href=\"%s\">%s</a></td>\n" +
            "\t<td class=\"detailsColumn\" data-value=\"0\" style=\"padding-right: 5px;\">%s</td>\n" +
            "\t<td class=\"detailsColumn\" data-value=\"1701833898167\">%td-%<tb-%<tY %<tR</td>\n" +
            "</tr>";

    public static String XML_HEAD = "<?xml version=\"1.0\" encoding=\"utf-8\" ?><D:multistatus xmlns:D=\"DAV:\" xmlns:Z=\"urn:schemas-microsoft-com:\">";
    public static String XML_FOOT = "</D:multistatus>";
    private Context context;
    protected final DateFormat dateFormatter;
    public DAVContextHandler(Context applicationContext) {
        this.context = applicationContext;
        dateFormatter = new SimpleDateFormat("E, d MMM yyyy HH:mm:ss 'GMT'", Locale.US);
        dateFormatter.setTimeZone(TimeZone.getTimeZone("GMT"));
        dateFormatter.setLenient(false);
       /* HttpManagerBuilder builder = new HttpManagerBuilder();
        builder.setResourceFactory(new ResourceFactory() {
            @Override
            public Resource getResource(String host, String path) throws NotAuthorizedException, BadRequestException {
                return null;
            }
        });
        builder.setEnableBasicAuth(false);
        httpManager = builder.buildHttpManager(); */
    }

    private boolean isDavRequest(HTTPServer.Request req) {
        if("PROPFIND".equals(req.getMethod())) {
            return true;
       // }else  if("LOCKS".equals(req.getMethod())) {
       //         return true;
        }
        return false;
    }

    private boolean isDavOptions(HTTPServer.Request req) {
        if("OPTIONS".equals(req.getMethod())) {
            return true;
        }
        return false;
    }

    @Override
    public int serve(HTTPServer.Request req, HTTPServer.Response resp) throws IOException {
        // /music/grouping/artist/id_title.ext
        ContextPath path = extractContextPath(req);
        if(isDavOptions(req)) {
            serveDavOptions(path, req, resp);
            return 0;
        }
       // 1 = context path, 2 = grouping, 3 = artist, 4 = filename
        switch (path.mode) {
            case 1:
                serveMusicContent(path, req, resp);
                break;
            case 2:
                serveGroupingContent(path, req, resp);
                break;
            case 3:
                serveArtistContent(path, req, resp);
                break;
            case 4:
                serveFileContent(path, req, resp);
                break;
        }

        return 0;
    }

    private void serveDavOptions(ContextPath path, HTTPServer.Request req, HTTPServer.Response resp) {
        try {
            String date = dateFormatter.format(new Date());
            resp.getHeaders().add("Date", date);
            resp.getHeaders().add("DAV", "1");
            resp.getHeaders().add("MS-Author-Via", "DAV");
            resp.getHeaders().add("Keep-Alive", "timeout=15,max=100");
            resp.getHeaders().add("Cache-Control", "private");
            resp.getHeaders().add("Content-Length", "0");
            resp.getHeaders().add("Content-Type", "text/plain; charset=UTF-8");
           // if(path.mode ==4) {
                resp.getHeaders().add("Allow", "OPTIONS, PROPFIND, GET");
                resp.getHeaders().add("Public", "OPTIONS, PROPFIND, GET");
           // }else {
           //     resp.getHeaders().add("Allow", "OPTIONS, PROPFIND");
           //     resp.getHeaders().add("Public", "OPTIONS, PROPFIND");
           // }
            resp.send(Status.OK.getRequestStatus(),"");
        }catch (Exception ex) {}
    }

    private void serveArtistContent(ContextPath path, HTTPServer.Request req, HTTPServer.Response resp) throws IOException {
        if(isDavRequest(req)) {
            String uri =  buildUri(req);
            String content = createDavIndexL3(path.name, path, uri);
            resp.getHeaders().add("Content-Type", "text/xml; charset=UTF-8");
            resp.send(Status.MULTI_STATUS.getRequestStatus(), content);
        }else {
            List<MusicTag> list = MusicTagRepository.getMusicTags(context, path.grouping, path.artist);
            String content = createHtmlIndex(null,list, req.getPath(), true);
            resp.send(Status.OK.getRequestStatus(), content);
        }
    }

    private String createDavIndexL3(String name, ContextPath path, String uri) {
        String content = "";
        //int idRange = StringUtils.toInt(path.artist); // 1100
        String[] idRanges = path.artist.split("_", -1);
        long lowerId = StringUtils.toInt(idRanges[0]); //1000
        long upperId = StringUtils.toInt(idRanges[1]); //1000
        String date = dateFormatter.format(new Date());
        // root
        content = createDavResponse(name, date, uri);
        List<MusicTag> files = MusicTagRepository.getMusicTags(lowerId, upperId); //1000 - 1100
        for(MusicTag file: files) {
            String fileuri = uri + getFilename(file, true);
            content += createDavResponse(file, fileuri);
        }

        return XML_HEAD+ content + XML_FOOT;
    }

    private void serveGroupingContent(ContextPath path, HTTPServer.Request req, HTTPServer.Response resp) throws IOException {
        if(isDavRequest(req)) {
            String uri =  buildUri(req);
            String content = createDavIndexL2(path.name, path, uri);
            resp.getHeaders().add("Content-Type", "text/xml; charset=UTF-8");
            resp.send(Status.MULTI_STATUS.getRequestStatus(), content);
        }else {
            List<String> list = MusicTagRepository.getArtistForGrouping(context, path.grouping);
            String content = createHtmlIndex(list, null, req.getPath(), true);
            resp.send(Status.OK.getRequestStatus(), content);
        }
    }

    private String createDavIndexL2(String name, ContextPath path, String uri) {
        String content = "";
        String[] idRanges = path.grouping.split("_", -1);
        long lowerId = StringUtils.toInt(idRanges[0]); //1000
        long upperId = StringUtils.toInt(idRanges[1]); //1000
        double pageSize = Math.ceil((upperId - lowerId) / 10.0);
       // double pageSize = StringUtils.toInt(path.grouping);
        //int pageCount = 1000 / PAGE_SIZE; //10
        String date = dateFormatter.format(new Date());
        // root
        content = createDavResponse(name, date, uri);
       // for(int i=1; i< pageCount;i++) { // 1-9
        String lowId; // = String.format(Locale.US, "%d",lowerId);
        double upId;
        for(int i=0; i< 10; i++) {
           // String dir = (idRange + (i * PAGE_SIZE))+""; // 1000 + (0 - 900)
            lowId = String.format(Locale.US, "%d",lowerId);
            upId = (lowerId+ pageSize);
            if(upId> upperId) upId = upperId;
            String dir = String.format(Locale.US, "%s_%.0f/", lowId,upId); // (int)((i+1) * pageSize);
           // String dir = String.format(Locale.US, "%.0f", ((i+1) * pageSize)); // (int)((i+1) * pageSize);
            String diruri = uri + dir;
            content += createDavResponse(dir, date, diruri);
            lowerId += pageSize;
        }
        return XML_HEAD+ content + XML_FOOT;
    }

    private void serveMusicContent(ContextPath path, HTTPServer.Request req, HTTPServer.Response resp) throws IOException {
        if(isDavRequest(req)) {
             // /music/1000/<1000+ PAGE_SIZE>/id.<ext>
           // List<MusicTag> list = MusicTagRepository.getAllMusics();
           // long totalSong = MusicTagRepository.getTotalSongCont();
            long maxId = MusicTagRepository.getMaxId();
            long minId = MusicTagRepository.getMinId();
            String uri =  buildUri(req);
            String content = createDavIndexL1(path.name, minId, maxId, uri);
            resp.getHeaders().add("Content-Type", "text/xml; charset=UTF-8");
            resp.send(Status.MULTI_STATUS.getRequestStatus(), content);
        }else {
            // /music/<Group>/<Artist>/id - <title>.<ext>
            List<String> list = MusicTagRepository.getActualGroupingList(context);
            String content = createHtmlIndex(list, null,req.getPath(),false);
            resp.send(Status.OK.getRequestStatus(), content);
        }

        /* List<String> list = MusicTagRepository.getActualGroupingList(context);
        if(isDavRequest(req)) {
            String uri =  buildUri(req);
            String content = createDavIndex(path.name, list, null, req.getPath(),uri);
            resp.getHeaders().add("Content-Type", "text/xml; charset=UTF-8");
            resp.send(Status.MULTI_STATUS.getRequestStatus(), content);
        }else {
            // get file content
            // send playing event
            String content = createHtmlIndex(list, null,req.getPath(),false);
            resp.send(Status.OK.getRequestStatus(), content);
        } */

    }

    private String createDavIndexL1(String name, long minId, long maxId, String uri) {
        String content = "";
        String date = dateFormatter.format(new Date());
        long pageSize = (long)Math.ceil((maxId-minId) / 10.0);
        // double cnt = 0.0;
        // root
        content = createDavResponse(name, date, uri);
        // while((cnt*1000) < maxId) {
        String lowId; // = String.format(Locale.US, "%d",minId);
        long upId;
        for(int i=0; i< 10; i++) {
            lowId = String.format(Locale.US, "%d",minId);
            upId = (minId+ pageSize);
            if(upId> maxId) upId = maxId;
             String dir = String.format(Locale.US, "%s_%d/", lowId, upId); // (int)((i+1) * pageSize);
             String diruri = uri + dir;
             content += createDavResponse(dir, date, diruri);
             minId += pageSize;
         }
         return XML_HEAD+ content + XML_FOOT;
    }

    private String buildUri(HTTPServer.Request req) {
        String uri = req.getBaseURL()+req.getPath();
        if(!uri.endsWith("/")) {
            uri += "/";
        }
        return uri;
    }

    private String createDavIndex(String name, List<String> dirs,List<MusicTag> files, String path,String uri) {
        String date = dateFormatter.format(new Date());
        String content = "";
        content = createDavResponse(name, date, uri);
        if(dirs != null) {
            for(String dir: dirs) {
                String diruri = uri + dir;
                if(!diruri.endsWith("/")) {
                    diruri += "/";
                }
                content = content+ createDavResponse(dir, date, diruri);
            }
        }
        if(files != null) {
            for(MusicTag file: files) {
                String fileuri = uri + getFilename(file, true);
                content += createDavResponse(file, fileuri);
            }
        }
        return XML_HEAD+ content + XML_FOOT;
    }

    private String createHtmlIndex(List<String> dirs, List<MusicTag> files, String path, boolean linkToParent) {
            if (!path.endsWith("/")) {
                path += "/";
            }
            long date = new Date().getTime();
            // note: we use apache's format, for consistent user experience
        Formatter fh = new Formatter(Locale.US);
        fh.format(HTML_HEADER_START, path);

            Formatter f = new Formatter(Locale.US);

            f.format("<body><h1 id=\"header\">MusicMate FS</h1><h2 id=\"header\">%s</h2>", path);
            if (linkToParent && path.length() > 1) // add parent link if not root path
                f.format(HTML_LINK_UP, HTTPServer.getParentPath(path));
            f.format(HTML_ITEM_HEADER);
            if(dirs != null) {
                for (String dir : dirs) {
                    try {
                        String name = dir + "/";
                        // properly url-encode the link
                        String link = new URI(null, path + name, null).toASCIIString();
                        f.format(HTML_ITEM_FOLDER, name, link, name, date);
                        //    f.format(" <a href=\"%s\">%s</a>%-" + (w - name.length()) +"s&#8206;%td-%<tb-%<tY %<tR%6s%n",link, name, "", date, size);
                    } catch (URISyntaxException ignore) {}
                }
            }
            if (files != null) {
                for (MusicTag file : files) {
                    try {
                        String name =  getFilename(file,false);  // id_title.ext
                        String size = HTTPServer.toSizeApproxString(file.getFileSize());
                        // properly url-encode the link
                        String link = new URI(null, path + name, null).toASCIIString();
                        f.format(HTML_ITEM_FILE, name,link, name, size, file.getFileLastModified());
                        //f.format(" <a href=\"%s\">%s</a>%-" + (w - name.length()) +"s&#8206;%td-%<tb-%<tY %<tR%6s%n",link, name, "", file.getFileLastModified(), size);
                    } catch (URISyntaxException ignore) {}
                }
            }
            f.format("</tbody></table></body></html>");
            return fh.toString()+HTML_STYLE_SHEET+HTML_HEADER_FOOT+ f.toString();
    }

    private String getFilename(MusicTag tag, boolean idOnly) {
        if(idOnly) {
            return tag.getId()+ "." + getExtension(tag);
        }else {
            return tag.getId() + "_" + tag.getTitle() + "." + getExtension(tag);
        }
    }



    private void serveFileContent(ContextPath path, HTTPServer.Request req, HTTPServer.Response resp) throws IOException {
        MusicTag tag = MusicTagRepository.getMusicTag(path.getId());
        if(tag == null) {
            resp.sendError(Status.NOT_FOUND.requestStatus,"Music not found");
        }
        if("GET".equalsIgnoreCase(req.getMethod())) {
            // get file content
            // send playing event
            MusicPlayerInfo info = new MusicPlayerInfo("http", "Streaming Player", context.getDrawable(R.drawable.ic_play_arrow_black_24dp));
            MusixMateApp.setPlaying(info, tag);
            AudioTagPlayingEvent.publishPlayingSong(tag);
            HTTPServer.serveFileContent(new File(tag.getPath()), req, resp);
        }else {
           String uri =  req.getBaseURL()+req.getPath();
            resp.getHeaders().add("Content-Type", "text/xml; charset=UTF-8");
            resp.send(Status.MULTI_STATUS.getRequestStatus(), XML_HEAD+createDavResponse(tag, uri)+XML_FOOT);
        }
    }

    private String createDavResponse(String name, String date, String uri)   {
        String content = "";
        try {
            String link = new URI(null, uri, null).toASCIIString();
            content = "<D:response>" +
                    "<D:href>" + link + "</D:href>" +
                    "<D:propstat>" +
                    "<D:prop>" +
                    "<D:creationdate>" + date + "</D:creationdate>" +
                    "<D:displayname>" + name + "</D:displayname>" +
                    "<D:getlastmodified>" + date + "</D:getlastmodified>" +
                    "<D:resourcetype><D:collection/></D:resourcetype>" +
                    "</D:prop>" +
                    "<D:status>HTTP/1.1 " + Status.OK.getDescription() + "</D:status>" +
                    "</D:propstat>" +
                    "</D:response>";
        }catch (Exception ex) {}
        return content;
    }

    private String createDavResponse(MusicTag tag, String uri)  {
        String content = "";
        try {
            String date = dateFormatter.format(tag.getFileLastModified());
            String link = new URI(null, uri, null).toASCIIString();
            content = "<D:response>" +
                            "<D:href>" + link + "</D:href>" +
                            "<D:propstat>" +
                            "<D:prop>" +
                            "<D:creationdate>" + date + "</D:creationdate>" +
                            "<D:displayname>" + StringUtils.escapeXml(tag.getTitle()) + "</D:displayname>" +
                            "<D:getcontentlength>" + tag.getAudioDuration() + "</D:getcontentlength>" +
                            "<D:getcontenttype>" + Mime.TYPES.get(tag.getFileFormat()) + "</D:getcontenttype>" +
                            "<D:getlastmodified>" + date + "</D:getlastmodified>" +
                            "<D:resourcetype/>" +
                            "</D:prop>" +
                            "<D:status>HTTP/1.1 " + Status.OK.getDescription() + "</D:status>" +
                            "</D:propstat>" +
                            "</D:response>";
        }catch (Exception ex) {}
        return content;
    }

    private ContextPath extractContextPath(HTTPServer.Request req) {
        String uri = req.getPath(); //path:  /music/id - arist - title.flac
        if(uri.endsWith("/")) {
            uri = uri.substring(0, uri.length()-1);
        }
        String[] paths = uri.split("/", -1);
        ContextPath path = new ContextPath();

        if(paths!= null && paths.length>=2) {
            path.name = paths[1];
            //0 = empty, 1 = context name, 2 = grouping, 3 = artist, 4 = filename
            if(paths.length >2) {
                path.grouping = paths[2];
                path.mode =2;
            }
            if(paths.length >3) {
                path.artist = paths[3];
                path.mode =3;
            }
            if(paths.length >4) {
                path.filename = paths[4];
                path.mode =4;
            }
        }
        return path;
    }

    private static class ContextPath {
        int mode = 1;
        String grouping;
        String artist;
        String filename;
        String name;

        long getId() {
            if(!isEmpty(filename)) {
                // id_title.ext
                String idStr =filename.substring(0, filename.indexOf("_"));
                return StringUtils.toLong(idStr);
            }
            return -1;
        }

    }
 interface IStatus {

    String getDescription();

    int getRequestStatus();
}

/**
 * Some HTTP response status codes
 */
 enum Status implements IStatus {
    SWITCH_PROTOCOL(101, "Switching Protocols"),

    OK(200, "OK"),
    CREATED(201, "Created"),
    ACCEPTED(202, "Accepted"),
    NO_CONTENT(204, "No Content"),
    PARTIAL_CONTENT(206, "Partial Content"),
    MULTI_STATUS(207, "Multi-Status"),

    REDIRECT(301, "Moved Permanently"),

    REDIRECT_SEE_OTHER(303, "See Other"),
    NOT_MODIFIED(304, "Not Modified"),
    TEMPORARY_REDIRECT(307, "Temporary Redirect"),

    BAD_REQUEST(400, "Bad Request"),
    UNAUTHORIZED(401, "Unauthorized"),
    FORBIDDEN(403, "Forbidden"),
    NOT_FOUND(404, "Not Found"),
    METHOD_NOT_ALLOWED(405, "Method Not Allowed"),
    NOT_ACCEPTABLE(406, "Not Acceptable"),
    REQUEST_TIMEOUT(408, "Request Timeout"),
    CONFLICT(409, "Conflict"),
    GONE(410, "Gone"),
    LENGTH_REQUIRED(411, "Length Required"),
    PRECONDITION_FAILED(412, "Precondition Failed"),
    PAYLOAD_TOO_LARGE(413, "Payload Too Large"),
    UNSUPPORTED_MEDIA_TYPE(415, "Unsupported Media Type"),
    RANGE_NOT_SATISFIABLE(416, "Requested Range Not Satisfiable"),
    EXPECTATION_FAILED(417, "Expectation Failed"),
    TOO_MANY_REQUESTS(429, "Too Many Requests"),

    INTERNAL_ERROR(500, "Internal Server Error"),
    NOT_IMPLEMENTED(501, "Not Implemented"),
    SERVICE_UNAVAILABLE(503, "Service Unavailable"),
    UNSUPPORTED_HTTP_VERSION(505, "HTTP Version Not Supported");

    private final int requestStatus;

    private final String description;

    Status(int requestStatus, String description) {
        this.requestStatus = requestStatus;
        this.description = description;
    }

    @Override
    public String getDescription() {
        return "" + this.requestStatus + " " + this.description;
    }

    @Override
    public int getRequestStatus() {
        return this.requestStatus;
    }
}
}
