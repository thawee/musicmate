package apincer.android.mmate.musicbrainz.coverart;

import java.util.List;
import com.google.gson.annotations.SerializedName;

public class Coverart{

	@SerializedName("images")
	private List<ImagesItem> images;

	@SerializedName("release")
	private String release;

	public void setImages(List<ImagesItem> images){
		this.images = images;
	}

	public List<ImagesItem> getImages(){
		return images;
	}

	public void setRelease(String release){
		this.release = release;
	}

	public String getRelease(){
		return release;
	}

	@Override
 	public String toString(){
		return 
			"Coverart{" + 
			"images = '" + images + '\'' + 
			",release = '" + release + '\'' + 
			"}";
		}
}