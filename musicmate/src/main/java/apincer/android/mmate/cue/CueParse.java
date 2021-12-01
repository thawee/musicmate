package apincer.android.mmate.cue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;
import android.media.MediaPlayer;

@SuppressLint("SimpleDateFormat")
public class CueParse {

    public static final int FLAC_PATH_POS = 0;

    private final int ALBUM_NAME_POS = 0;

    private final int CUE_FILE_POS = 0;

    private List<Track> tracks = new ArrayList<Track>();

    private String albumName;

    private String cueString;


    public CueParse() {
    }

    public void parseFolderWithCue(File folder) throws CueParseException {
        if(isFolderValid(folder)){
            parseCueFile(getCueFile(folder).get(CUE_FILE_POS));
        } else
            throw new CueParseException(
                    "There more than one flac or cue file in the folder");;
    }

    public void parseCueFile(File cueFile) throws CueParseException {
        cueString = getStringFromCue(cueFile);
        if (isCueValid(cueFile)) {
            getTracksFromCue(cueFile);
        } else {
            throw new CueParseException(
                    "There more than one flac or cue file in the folder");
        }
    }

    private void getTracksFromCue(File cueFile) {
        cueString = getStringFromCue(cueFile);
        ArrayList<String> titles = getTracksTitles();
        ArrayList<String> performers = getTracksPerformers();
        ArrayList<Integer> startIndexes = getStartIndex();
        String path = getTrackPaths(cueFile).get(FLAC_PATH_POS);
        setAlbumName(titles);
        for (int i = 0; i < startIndexes.size() - 1; i++) {
            Track newTrack = new Track();
            newTrack.setTitle(titles.get(i + 1));
            newTrack.setArtist(performers.get(i));
            newTrack.setStartPosition(startIndexes.get(i));
            newTrack.setFilePath(path);
            newTrack.setAlbum(albumName);
            tracks.add(newTrack);
        }
        setDurations(tracks, path);
    }

    private String getStringFromCue(File cueFile) {
        StringBuilder text = new StringBuilder();
        try {
            BufferedReader br = new BufferedReader(new FileReader(cueFile));
            String line;
            while ((line = br.readLine()) != null) {
                text.append(line);
                text.append('\n');
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return text.toString();
    }

    public boolean isCueValid(File cueFile) {
        return getTrackPaths(cueFile).size() <= 1;
    }

    public static boolean isFolderValid(File folder) {
        ArrayList<File> cueFiles = getCueFile(folder);
        return (cueFiles != null & cueFiles.size() > 0);
    }

    // Cue sheet can have only 1 flac file, otherwise it won't be parsed
    public ArrayList<String> getTrackPaths(File cueFile) {
        ArrayList<String> paths = new ArrayList<String>();
        Pattern path = Pattern.compile("FILE \".*\"");
        Matcher pathMatcher = path.matcher(cueString);
        while (pathMatcher.find()) {
            paths.add(cueFile.getAbsolutePath().replace(
                    cueFile.getName(),
                    pathMatcher.group().replace("FILE \"", "")
                            .replace("\"", "")));
        }
        return paths;
    }

    private ArrayList<String> getTracksTitles() {
        ArrayList<String> titles = new ArrayList<String>();
        Pattern title = Pattern.compile("TITLE \".*\"");
        Matcher titleMathcer = title.matcher(cueString);
        while (titleMathcer.find()) {
            titles.add(titleMathcer.group().replace("TITLE \"", "")
                    .replace("\"", ""));
        }
        return titles;
    }

    private ArrayList<String> getTracksPerformers() {
        ArrayList<String> performers = new ArrayList<String>();
        Pattern performer = Pattern.compile("PERFORMER \".*\"");
        Matcher performerMatcher = performer.matcher(cueString);
        while (performerMatcher.find()) {
            performers.add(performerMatcher.group().replace("PERFORMER \"", "")
                    .replace("\"", ""));
        }
        return performers;
    }

    @SuppressWarnings("deprecation")
    private ArrayList<Integer> getStartIndex() {
        ArrayList<Integer> startPositions = new ArrayList<Integer>();
        Pattern startIndex = Pattern.compile("INDEX 01.*");
        Matcher startIndexMatcher = startIndex.matcher(cueString);
        while (startIndexMatcher.find()) {
            String test = startIndexMatcher.group().replace("INDEX 01 ", "")
                    .replace("\"", "");
            SimpleDateFormat format = new SimpleDateFormat("mm:ss:SS");
            try {
                Date time = format.parse(test);
                int hours = time.getHours();
                int minutes = time.getMinutes();
                int seconds = time.getSeconds();
                startPositions.add((hours * 3600 + minutes * 60 + seconds));
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
        return startPositions;
    }

    private void setDurations(List<Track> tracks, String flacPath) {
        for (int i = 0; i < tracks.size() - 1; i++) {
            tracks.get(i).setDuration(
                    tracks.get(i + 1).getStartPosition()
                            - tracks.get(i).getStartPosition());
        }
        // Last track duration = total duration - last track starting point
        MediaPlayer mMediaPlayer = new MediaPlayer();
        try {
            mMediaPlayer.setDataSource(flacPath);
            mMediaPlayer.prepare();
            int totalDuration = mMediaPlayer.getDuration() / 1000;
            int lastTrackDurarion = totalDuration
                    - tracks.get(tracks.size() - 1).getStartPosition();
            tracks.get(tracks.size() - 1).setDuration(lastTrackDurarion);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static ArrayList<File> getCueFile(File parentDir) {
        ArrayList<File> cueFiles = new ArrayList<File>();
        File[] files = parentDir.listFiles();
        for (File file : files) {
            if (!file.isDirectory()) {
                if (file.getName().endsWith(".cue")) {
                    cueFiles.add(file);
                }
            }
        }
        return cueFiles;
    }

    public List<Track> getTracks() {
        return tracks;
    }

    public void setAlbumName(ArrayList<String> titles) {
        this.albumName = titles.get(ALBUM_NAME_POS);
    }

    public String getAlbumName() {
        return albumName;
    }
}