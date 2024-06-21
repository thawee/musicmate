package apincer.android.mmate.server;

import android.content.Context;

import net.freeutils.httpserver.HTTPServer;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.repository.MusicTagRepository;
import apincer.android.mmate.repository.SearchCriteria;
import apincer.android.mmate.share.PLSBuilder;

public class PlaylistContextHandler implements HTTPServer.ContextHandler {
    public static String HTML_HEADER_START ="<html><head><meta charset=\"utf-8\"><meta name=\"google\" value=\"notranslate\"><meta name=\"color-scheme\" content=\"light\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
            "<title id=\"title\">MusicMate Playlist %s</title>";
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

    private Context context;
    protected final DateFormat dateFormatter;
    public PlaylistContextHandler(Context applicationContext) {
        this.context = applicationContext;
        dateFormatter = new SimpleDateFormat("E, d MMM yyyy HH:mm:ss 'GMT'", Locale.US);
        dateFormatter.setTimeZone(TimeZone.getTimeZone("GMT"));
        dateFormatter.setLenient(false);
    }

    @Override
    public int serve(HTTPServer.Request req, HTTPServer.Response resp) throws IOException {
        String uri = req.getPath();
        if(uri.endsWith(".pls")) {
            String baseuri = buildFileUri(req);
            String [] paths = uri.split("/", -1);
            // /playlist/typ/item.pls -- grouping/thai.pls, gene/pop.pls
            String content = createPlaylist(paths[2], paths[3], baseuri);
            resp.getHeaders().add("Content-Type", "text/plain; charset=UTF-8");
            resp.send(DAVContextHandler.Status.OK.getRequestStatus(), content);
        }else {
            // build index.html content
            List<String> list = new ArrayList<>();
            list.add(String.format(Locale.US, "%s/%s.pls", "Grouping","Thai"));
            list.add(String.format(Locale.US, "%s/%s.pls", "Grouping","English"));
            list.add(String.format(Locale.US, "%s/%s.pls", "Grouping","Lounge"));
            list.add(String.format(Locale.US, "%s/%s.pls", "Grouping","Thai Lounge"));
            String content = buildHtmlIndex(uri, list);
            resp.send(DAVContextHandler.Status.OK.getRequestStatus(), content);
        }

        return 0;
    }

    private String createPlaylist(String typ, String item, String baseuri) {
        if("Grouping".equalsIgnoreCase(typ)) {
            String keyword = item.substring(0, item.indexOf("."));
            SearchCriteria criteria = new SearchCriteria(SearchCriteria.TYPE.GROUPING, keyword);
            List<MusicTag> list = MusicTagRepository.findMediaTag(criteria);
            return PLSBuilder.build(baseuri,list);
        }else if("Genre".equalsIgnoreCase(typ)) {
            String keyword = item.substring(0, item.indexOf("."));
            SearchCriteria criteria = new SearchCriteria(SearchCriteria.TYPE.GENRE, keyword);
            List<MusicTag> list = MusicTagRepository.findMediaTag(criteria);
            return PLSBuilder.build(baseuri,list);
        }
        return "";
    }

    private String buildHtmlIndex(String path, List<String> dirs) {
            if (!path.endsWith("/")) {
                path += "/";
            }
            long date = new Date().getTime();
            // note: we use apache's format, for consistent user experience
        Formatter fh = new Formatter(Locale.US);
        fh.format(HTML_HEADER_START, path);

            Formatter f = new Formatter(Locale.US);

            f.format("<body><h1 id=\"header\">MusicMate Playlist</h1><h2 id=\"header\">%s</h2>", path);

            f.format(HTML_ITEM_HEADER);
            if(dirs != null) {
                for (String dir : dirs) {
                    try {
                        String name = dir;
                        // properly url-encode the link
                        String link = new URI(null, path + name, null).toASCIIString();
                        f.format(HTML_ITEM_FILE, name, link, name, 0.0, date);
                        //name,link, name, size, file.getFileLastModified());
                    } catch (URISyntaxException ignore) {}
                }
            }
            f.format("</tbody></table></body></html>");
            return fh +HTML_STYLE_SHEET+HTML_HEADER_FOOT+ f;
    }

    private String buildFileUri(HTTPServer.Request req) {
        return req.getBaseURL()+"/file";
    }

}
