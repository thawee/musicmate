package apincer.android.mmate.musicbrainz.recording;

import java.util.List;
import com.google.gson.annotations.SerializedName;

public class RecordingsItem {

	@SerializedName("score")
	private String score;

	@SerializedName("length")
	private int length;

	@SerializedName("disambiguation")
	private String disambiguation;

	@SerializedName("artist-credit")
	private List<ArtistCreditItem> artistCredit;

	@SerializedName("id")
	private String id;

	@SerializedName("video")
	private Object video;

	@SerializedName("title")
	private String title;

	@SerializedName("releases")
	private List<ReleasesItem> releases;

	public void setScore(String score){
		this.score = score;
	}

	public String getScore(){
		return score;
	}

	public void setLength(int length){
		this.length = length;
	}

	public int getLength(){
		return length;
	}

	public void setDisambiguation(String disambiguation){
		this.disambiguation = disambiguation;
	}

	public String getDisambiguation(){
		return disambiguation;
	}

	public void setArtistCredit(List<ArtistCreditItem> artistCredit){
		this.artistCredit = artistCredit;
	}

	public List<ArtistCreditItem> getArtistCredit(){
		return artistCredit;
	}

	public void setId(String id){
		this.id = id;
	}

	public String getId(){
		return id;
	}

	public void setVideo(Object video){
		this.video = video;
	}

	public Object getVideo(){
		return video;
	}

	public void setTitle(String title){
		this.title = title;
	}

	public String getTitle(){
		return title;
	}

	public void setReleases(List<ReleasesItem> releases){
		this.releases = releases;
	}

	public List<ReleasesItem> getReleases(){
		return releases;
	}

	@Override
 	public String toString(){
		return 
			"RecordingsItem{" + 
			"score = '" + score + '\'' + 
			",length = '" + length + '\'' + 
			",disambiguation = '" + disambiguation + '\'' + 
			",artist-credit = '" + artistCredit + '\'' + 
			",id = '" + id + '\'' + 
			",video = '" + video + '\'' + 
			",title = '" + title + '\'' + 
			",releases = '" + releases + '\'' + 
			"}";
		}
}