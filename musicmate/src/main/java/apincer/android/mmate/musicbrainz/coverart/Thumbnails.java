package apincer.android.mmate.musicbrainz.coverart;

import com.google.gson.annotations.SerializedName;

public class Thumbnails{

	@SerializedName("small")
	private String small;

	@SerializedName("large")
	private String large;

	public void setSmall(String small){
		this.small = small;
	}

	public String getSmall(){
		return small;
	}

	public void setLarge(String large){
		this.large = large;
	}

	public String getLarge(){
		return large;
	}

	@Override
 	public String toString(){
		return 
			"Thumbnails{" + 
			"small = '" + small + '\'' + 
			",large = '" + large + '\'' + 
			"}";
		}
}