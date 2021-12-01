package apincer.android.mmate.musicbrainz.recording;

import java.util.List;
import com.google.gson.annotations.SerializedName;

public class MediaItem{

	@SerializedName("format")
	private String format;

	@SerializedName("position")
	private int position;

	@SerializedName("track-offset")
	private int trackOffset;

	@SerializedName("track")
	private List<TrackItem> track;

	@SerializedName("track-count")
	private int trackCount;

	public void setFormat(String format){
		this.format = format;
	}

	public String getFormat(){
		return format;
	}

	public void setPosition(int position){
		this.position = position;
	}

	public int getPosition(){
		return position;
	}

	public void setTrackOffset(int trackOffset){
		this.trackOffset = trackOffset;
	}

	public int getTrackOffset(){
		return trackOffset;
	}

	public void setTrack(List<TrackItem> track){
		this.track = track;
	}

	public List<TrackItem> getTrack(){
		return track;
	}

	public void setTrackCount(int trackCount){
		this.trackCount = trackCount;
	}

	public int getTrackCount(){
		return trackCount;
	}

	@Override
 	public String toString(){
		return 
			"MediaItem{" + 
			"format = '" + format + '\'' + 
			",position = '" + position + '\'' + 
			",track-offset = '" + trackOffset + '\'' + 
			",track = '" + track + '\'' + 
			",track-count = '" + trackCount + '\'' + 
			"}";
		}
}