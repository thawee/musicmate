package apincer.android.mmate.utils;

import static apincer.android.mmate.utils.StringUtils.isEmpty;
import static apincer.android.mmate.utils.StringUtils.trimToEmpty;

import java.io.File;
import java.util.List;

import apincer.android.mmate.repository.database.MusicTag;
import apincer.android.utils.FileUtils;

public class MusicPathTagParser {
    public void parse(MusicTag tag, List<String> patterns) {
        if (patterns.isEmpty()) {
            return;
        }

        // AudioTag tag = item.clone();
        String text = tag.getPath();
        //remove extension
        if (text.lastIndexOf(".") > 0) {
            text = text.substring(0, text.lastIndexOf("."));
        }

        // detect valid string
        String firstTag = patterns.get(0);
        // find tagsCount
        // int tagsCount = 0;
        int firstTagIndex = text.length();
        for (String model : patterns) {
            if (firstTag.equals(model)) {
                //  tagsCount++;
                firstTagIndex = text.lastIndexOf(firstTag, firstTagIndex - 1);
            }
        }

        text = text.substring(firstTagIndex);
        String tagType;
        for (int i = 0; i < patterns.size(); i++) {
            String curTag = patterns.get(i);
            String nextTagText = "";
            tagType = curTag;
            int nextTagSeq = i + 1;
            if ((nextTagSeq) < patterns.size()) {
                nextTagText = patterns.get(nextTagSeq);
            }

            int textLen = text.length();
            if ("title".equalsIgnoreCase(tagType)) {
                int offset =0;
                while ("?".equals(nextTagText)) {
                    i++;
                    offset++;
                    nextTagText = "";
                    nextTagSeq = i + 1;
                    if ((nextTagSeq) < patterns.size()) {
                        nextTagText = patterns.get(nextTagSeq);
                    }
                }

                int nextTagIndex = getIndexForNextTag(text, nextTagText);
                tag.setTitle(subString(text,0,nextTagIndex,offset));
                text = subString(text,nextTagIndex,textLen);
            } else if ("artist".equalsIgnoreCase(tagType)) {
                int offset =0;
                while ("?".equals(nextTagText)) {
                    i++;
                    offset++;
                    nextTagText = "";
                    nextTagSeq = i + 1;
                    if ((nextTagSeq) < patterns.size()) {
                        nextTagText = patterns.get(nextTagSeq);
                    }
                }

                int nextTagIndex = getIndexForNextTag(text, nextTagText);
                tag.setArtist(subString(text,0,nextTagIndex,offset));
                text = subString(text,nextTagIndex,textLen);
            } else if ("album".equalsIgnoreCase(tagType)) {
                int offset =0;
                while ("?".equals(nextTagText)) {
                    i++;
                    offset++;
                    nextTagText = "";
                    nextTagSeq = i + 1;
                    if ((nextTagSeq) < patterns.size()) {
                        nextTagText = patterns.get(nextTagSeq);
                    }
                }

                int nextTagIndex = getIndexForNextTag(text, nextTagText);
                tag.setAlbum(subString(text,0,nextTagIndex,offset));
                text = subString(text,nextTagIndex,textLen);
            } else if ("track".equalsIgnoreCase(tagType)) {
                int offset =0;
                while ("?".equals(nextTagText)) {
                    i++;
                    offset++;
                    nextTagText = "";
                    nextTagSeq = i + 1;
                    if ((nextTagSeq) < patterns.size()) {
                        nextTagText = patterns.get(nextTagSeq);
                    }
                }

                int nextTagIndex = getIndexForNextTag(text, nextTagText);
                tag.setTrack(subString(text,0,nextTagIndex,offset));
                text = subString(text,nextTagIndex,textLen);
            } else if ("sp".equals(tagType)) {
                // eat space(s)
                text = text.trim();
            } else {
                // eat tag type from text
                if ((text.length() - tagType.length()) > 0) {
                    text = text.substring(tagType.length());
                }
            }
        }
    }

    private String subString(String text, int nextTagIndex, int textLen) {
        if(isEmpty(text)) return "";
        if((textLen)<=text.length()) {
            return text.substring(nextTagIndex, textLen);
        }else {
            return text.substring(nextTagIndex);
        }
    }

    private String subString(String text, int i, int nextTagIndex, int offset) {
        if(isEmpty(text)) return "";
        if((nextTagIndex-offset)<=text.length() && nextTagIndex - offset >0) {
            return trimToEmpty(text.substring(i, nextTagIndex - offset));
        }else {
            return trimToEmpty(text.substring(i));
        }
    }

    private int getIndexForNextTag(String text, String nextTagType) {
        int indx = text.length();
        if ("sp".equals(nextTagType)) {
            indx = text.indexOf(" ");
            if (indx < 0) {
                indx = text.indexOf("\t");
            }
        } else if (!nextTagType.trim().isEmpty() && text.contains(nextTagType)) {
            indx = text.indexOf(nextTagType);
        }else if ("?".equals(nextTagType)) {
            indx = 1;  // text.indexOf(" ");
        }

        return indx;
    }

    public enum READ_MODE {
        SIMPLE, HIERARCHY, SMART;
        public static READ_MODE PATTERN;
    }

    String titleSep = "- ";
    String titleSep2 = ". ";
    String artistBegin = "[";
    String artistEnd = "]";

    @Deprecated
    public boolean parse(MusicTag item, READ_MODE mode) {
        File file = new File(item.getPath());
        if (!file.exists()) {
            return false;
        }

        MusicTag tag = item.copy();
        String text = FileUtils.removeExtension(file);
        if (mode == READ_MODE.SIMPLE) {
            // filename
            tag.setTitle(text);
            tag.setAlbum(text);
            tag.setArtist(text);
        } else {
            // <track>.<arist> (<featering>) - <tltle>
            // (<track>) [<arist>] <tltle>
            // track sep can be .,-, <space>
            // title sep is -

            text = parseTrackNumber(tag, text);
            text = parseArtist(tag, text);

            tag.setTitle(parseTitle(text));
            String featuring = parseFeaturing(tag.getArtist());
            if (!StringUtils.isEmpty(featuring)) {
                tag.setArtist(removeFeaturing(tag.getArtist()));
                tag.setTitle(tag.getTitle() + " " + featuring);
            }

            if (mode == READ_MODE.HIERARCHY && (file != null)) {
                file = file.getParentFile();
                tag.setAlbum(file.getName());
                if (StringUtils.isEmpty(tag.getArtist())) {
                    file = file.getParentFile();
                    tag.setArtist(file.getName());

                }
            }
        }

        return true;
    }

    private String parseArtist(MusicTag tag, String text) {
        if (text.startsWith(artistBegin) && text.indexOf(artistEnd) > 1) {
            tag.setArtist(text.substring(text.indexOf(artistBegin) + artistBegin.length(), text.indexOf(artistEnd)));
            text = text.substring(text.indexOf(artistEnd) + artistEnd.length());
        } else {
            int titleIndx = text.indexOf(titleSep);
            if (titleIndx >= 0) {
                tag.setArtist(trimToEmpty(text.substring(0, titleIndx)));
                text = trimToEmpty(text.substring(titleIndx + titleSep.length()));
            }
        }
        return trimToEmpty(text);
    }

    private String parseTrackNumber(MusicTag tag, String text) {
        String trackNo = "";
        int i = 0;
        for (; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isDigit(ch)) {
                trackNo = trackNo + ch;
            } else if ('(' == ch) {
                continue;
            } else if (')' == ch) {
                i++;
                break;
            } else {
                i++; //eat none number i.e space or .
                break;
            }
        }
        if (i > 1 && i < text.length()) {
            text = text.substring(i);
        }
        tag.setTrack(trackNo);
        return trimToEmpty(text);
    }

    private String removeFeaturing(String artist) {
        if (artist.indexOf("(") > 0 && artist.indexOf(")") > 0) {
            return artist.substring(0, artist.indexOf("("));
        }
        return "";
    }

    private String parseFeaturing(String artist) {
        if (artist.indexOf("(") > 0 && artist.indexOf(")") > 0) {
            return artist.substring(artist.indexOf("(") + 1, artist.indexOf(")"));
        }
        return "";
    }

    private String parseTitle(String text) {
        int titleIndx = text.indexOf(titleSep);
        if (titleIndx >= 0) {
            text = trimToEmpty(text.substring(titleIndx + titleSep.length()));
        }
        while (text.contains("_")) {
            String timestamp = trimToEmpty(text.substring(text.indexOf("_") + 1));
            if (StringUtils.isDigitOnly(timestamp) && timestamp.length() > 4) {
                text = text.substring(0, text.indexOf("_"));
            } else {
                text = text.substring(0, text.indexOf("_")) + " " + text.substring(text.indexOf("_") + 1);
            }
        }
        return trimToEmpty(text);
    }
}
