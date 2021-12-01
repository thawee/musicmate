package apincer.android.mmate.musicbrainz.recording;

import java.util.List;
import com.google.gson.annotations.SerializedName;

public class ReleasesItem{

	@SerializedName("release-group")
	private ReleaseGroup releaseGroup;

	@SerializedName("date")
	private String date;

	@SerializedName("country")
	private String country;

	@SerializedName("release-events")
	private List<ReleaseEventsItem> releaseEvents;

	@SerializedName("id")
	private String id;

	@SerializedName("media")
	private List<MediaItem> media;

	@SerializedName("title")
	private String title;

	@SerializedName("track-count")
	private int trackCount;

	@SerializedName("status")
	private String status;

	public void setReleaseGroup(ReleaseGroup releaseGroup){
		this.releaseGroup = releaseGroup;
	}

	public ReleaseGroup getReleaseGroup(){
		return releaseGroup;
	}

	public void setDate(String date){
		this.date = date;
	}

	public String getDate(){
		return date;
	}

	public void setCountry(String country){
		this.country = country;
	}

	public String getCountry(){
		return country;
	}

	public void setReleaseEvents(List<ReleaseEventsItem> releaseEvents){
		this.releaseEvents = releaseEvents;
	}

	public List<ReleaseEventsItem> getReleaseEvents(){
		return releaseEvents;
	}

	public void setId(String id){
		this.id = id;
	}

	public String getId(){
		return id;
	}

	public void setMedia(List<MediaItem> media){
		this.media = media;
	}

	public List<MediaItem> getMedia(){
		return media;
	}

	public void setTitle(String title){
		this.title = title;
	}

	public String getTitle(){
		return title;
	}

	public void setTrackCount(int trackCount){
		this.trackCount = trackCount;
	}

	public int getTrackCount(){
		return trackCount;
	}

	public void setStatus(String status){
		this.status = status;
	}

	public String getStatus(){
		return status;
	}

	@Override
 	public String toString(){
		return 
			"ReleasesItem{" + 
			"release-group = '" + releaseGroup + '\'' + 
			",date = '" + date + '\'' + 
			",country = '" + country + '\'' + 
			",release-events = '" + releaseEvents + '\'' + 
			",id = '" + id + '\'' + 
			",media = '" + media + '\'' + 
			",title = '" + title + '\'' + 
			",track-count = '" + trackCount + '\'' + 
			",status = '" + status + '\'' + 
			"}";
		}
}