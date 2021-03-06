package apincer.android.mmate.utils;

import java.io.File;
import java.util.List;

import apincer.android.mmate.objectbox.AudioTag;
import apincer.android.utils.FileUtils;

public class MediaTagParser {
    public void parse(AudioTag tag, List<String> patterns) {
       if(patterns.size()<=0) {
            return;
        }

       // AudioTag tag = item.clone();
        String text = tag.getPath();
        //remove extension
        if(text.lastIndexOf(".") > 0) {
            text = text.substring(0, text.lastIndexOf("."));
        }

        // detect valid string
        String firstTag = patterns.get(0);
        // find tagsCount
       // int tagsCount = 0;
        int firstTagIndex = text.length();
        for (String model: patterns) {
            if(firstTag.equals(model)) {
              //  tagsCount++;
                firstTagIndex = text.lastIndexOf(firstTag, firstTagIndex-1);
            }
        }

        text = text.substring(firstTagIndex);

        for(int i=0;i< patterns.size();i++) {
            String curTag = patterns.get(i);
            String nextTagText = "";
            String tagType = curTag;
            int nextTagSeq = i+1;
            if((nextTagSeq) < patterns.size()) {
                // last tag
                nextTagText = patterns.get(nextTagSeq);
            }

           int textLen = text.length();
            if("title".equalsIgnoreCase(tagType)) {
                    int nextTagIndex = getIndexForNextTag(text, nextTagText);
                    tag.setTitle(text.substring(0, nextTagIndex));
                    text = text.substring(nextTagIndex, textLen);
                }else if("artist".equalsIgnoreCase(tagType)) {
                    int nextTagIndex = getIndexForNextTag(text, nextTagText);
                    tag.setArtist(text.substring(0, nextTagIndex));
                    text = text.substring(nextTagIndex, textLen);
                } else if("album".equalsIgnoreCase(tagType)) {
                    int nextTagIndex = getIndexForNextTag(text, nextTagText);
                    tag.setAlbum(text.substring(0, nextTagIndex));
                    text = text.substring(nextTagIndex, textLen);
                }else if("track".equalsIgnoreCase(tagType)) {
                    int nextTagIndex = getIndexForNextTag(text, nextTagText);
                    tag.setTrack(text.substring(0, nextTagIndex));
                    text = text.substring(nextTagIndex, textLen);
                }else if ("sp".equals(tagType)){
                    // eat space(s)
                    text = text.trim();
            }else if("*".equals(tagType)){
                // eat any text until next pattern
                if(patterns.size()>nextTagSeq+1) {
                    String nextToken = patterns.get(nextTagSeq + 1);
                    int nextIndex = 0;
                    for (nextIndex = 0; nextIndex < text.length(); nextIndex++) {
                        if (nextToken.equals(text.charAt(nextIndex))) {
                            break;
                        }
                    }
                    if(nextIndex < text.length()) {
                        text = text.substring(nextIndex, text.length());
                    }else {
                        text = "";
                    }
                }else {
                    text = "";
                }
            }else {
                // eat tag type from text
                if((text.length()-tagType.length()) >0) {
                    text = text.substring(tagType.length());
                }
            }
        }
    }

    private int getIndexForNextTag(String text, String nextTagType) {
        int indx = text.length();
        if("sp".equals(nextTagType)) {
            indx = text.indexOf(" ");
            if(indx <0) {
                indx = text.indexOf("\t");
            }
        } else if(nextTagType.trim().length()>0 && text.contains(nextTagType)){
            indx = text.indexOf(nextTagType);
        }

        return indx;
    }

    public enum READ_MODE {SIMPLE,HIERARCHY,SMART;
         public static READ_MODE PATTERN;
     }

    String titleSep="- ";
    String titleSep2=". ";
    String artistBegin="[";
    String artistEnd="]";

    @Deprecated
    public boolean parse(AudioTag item, READ_MODE mode) {
        File file = new File(item.getPath());
        if(!file.exists()) {
            return false;
        }

        AudioTag tag = item.clone();
        String text = FileUtils.removeExtension(file);
        if(mode==READ_MODE.SIMPLE) {
            // filename
            tag.setTitle(text);
            tag.setAlbum(text);
            tag.setArtist(text);
        }else {
            // <track>.<arist> (<featering>) - <tltle>
            // (<track>) [<arist>] <tltle>
            // track sep can be .,-, <space>
            // title sep is -

            text = parseTrackNumber(tag, text);
            text = parseArtist(tag, text);

            tag.setTitle(parseTitle(text));
            String featuring = parseFeaturing(tag.getArtist());
            if(!StringUtils.isEmpty(featuring)) {
                tag.setArtist(removeFeaturing(tag.getArtist()));
                tag.setTitle(tag.getTitle()+ " " + featuring);
            }

            if(mode==READ_MODE.HIERARCHY && (file!=null)) {
                file = file.getParentFile();
                tag.setAlbum(file.getName());
                if(StringUtils.isEmpty(tag.getArtist())) {
                    file = file.getParentFile();
                    tag.setArtist(file.getName());

                }
            }
        }

        return true;
    }
/*
    @Deprecated
    public void parsePattern(MediaItem item, List<TagModel> patterns) {
        if(patterns.size()<=0) {
            return;
        }

        MediaMetadata tag = item.getPendingMetadataOrCreate();
        String text = item.getPath();
        //remove extension
        if(text.lastIndexOf(".") > 0) {
            text = text.substring(0, text.lastIndexOf("."));
        }

        // detect valid string
        String firstTag = patterns.get(0).getTagText();
        // find tagsCount
        // int tagsCount = 0;
        int firstTagIndex = text.length();
        for (TagModel model: patterns) {
            if(firstTag.equals(model.getTagText())) {
                //  tagsCount++;
                firstTagIndex = text.lastIndexOf(firstTag, firstTagIndex-1);
            }
        }

        text = text.substring(firstTagIndex);

        for(int i=0;i< patterns.size();i++) {
            TagModel curTag = patterns.get(i);
            String nextTagText = "";
            String tagType = curTag.getTagText();
            int nextTagSeq = i+1;
            if((nextTagSeq) < patterns.size()) {
                // last tag
                nextTagText = patterns.get(nextTagSeq).getTagText();
            }

            int textLen = text.length();
            if("title".equalsIgnoreCase(tagType)) {
                int nextTagIndex = getIndexForNextTag(text, nextTagText);
                tag.setTitle(text.substring(0, nextTagIndex));
                text = text.substring(nextTagIndex, textLen);
            }else if("artist".equalsIgnoreCase(tagType)) {
                int nextTagIndex = getIndexForNextTag(text, nextTagText);
                tag.setArtist(text.substring(0, nextTagIndex));
                text = text.substring(nextTagIndex, textLen);
            } else if("album".equalsIgnoreCase(tagType)) {
                int nextTagIndex = getIndexForNextTag(text, nextTagText);
                tag.setAlbum(text.substring(0, nextTagIndex));
                text = text.substring(nextTagIndex, textLen);
            }else if("trackno".equalsIgnoreCase(tagType)) {
                int nextTagIndex = getIndexForNextTag(text, nextTagText);
                tag.setTrack(text.substring(0, nextTagIndex));
                text = text.substring(nextTagIndex, textLen);
            }else if ("sp".equals(tagType)){
                // eat space(s)
                text = text.trim();
            }else {
                // eat tag type from text
                if((text.length()-tagType.length()) >0) {
                    text = text.substring(tagType.length());
                }
            }
        }
    } */

    private String parseArtist(AudioTag tag, String text) {
        if(text.startsWith(artistBegin) && text.indexOf(artistEnd)>1) {
            tag.setArtist(text.substring(text.indexOf(artistBegin)+artistBegin.length(), text.indexOf(artistEnd)));
            text = text.substring(text.indexOf(artistEnd)+artistEnd.length());
        }else {
            int titleIndx = text.indexOf(titleSep);
            if (titleIndx >= 0) {
                tag.setArtist(StringUtils.trimToEmpty(text.substring(0, titleIndx)));
                text = StringUtils.trimToEmpty(text.substring(titleIndx+titleSep.length()));
            }
        }
        return StringUtils.trimToEmpty(text);
    }

    private String parseTrackNumber(AudioTag tag, String text) {String trackNo = "";
        int i =0;
        for(;i<text.length();i++) {
            char ch = text.charAt(i);
            if (Character.isDigit(ch)) {
                trackNo = trackNo + ch;
            }else if('('==ch) {
                continue;
            }else if(')'==ch){
                i++;
                break;
            }else {
                i++; //eat none number i.e space or .
                break;
            }
        }
        if(i > 1 && i < text.length()) {
            text = text.substring(i);
        }
        tag.setTrack(trackNo);
        return StringUtils.trimToEmpty(text);
    }

    private String removeFeaturing(String artist) {
        if(artist.indexOf("(") >0 && artist.indexOf(")") >0) {
            return artist.substring(0, artist.indexOf("("));
        }
        return "";
    }

    private String parseFeaturing(String artist) {
        if(artist.indexOf("(") >0 && artist.indexOf(")") >0) {
            return artist.substring(artist.indexOf("(")+1, artist.indexOf(")"));
        }
        return "";
    }

    private String parseTitle(String text) {
        int titleIndx = text.indexOf(titleSep);
            if(titleIndx>=0) {
                text = StringUtils.trimToEmpty(text.substring(titleIndx+titleSep.length()));
            }
            while(text.contains("_")) {
                String timestamp = StringUtils.trimToEmpty(text.substring(text.indexOf("_")+1));
                if (StringUtils.isDigitOnly(timestamp) && timestamp.length() >4) {
                    text = text.substring(0, text.indexOf("_"));
                }else {
                    text = text.substring(0, text.indexOf("_"))+" "+text.substring(text.indexOf("_")+1);
                }
            }
            return StringUtils.trimToEmpty(text);
    }
}
