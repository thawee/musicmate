package apincer.android.mmate.musicbrainz.recording;

import com.google.gson.annotations.SerializedName;

public class ArtistCreditItem{

	@SerializedName("artist")
	private Artist artist;

	public void setArtist(Artist artist){
		this.artist = artist;
	}

	public Artist getArtist(){
		return artist;
	}

	@Override
 	public String toString(){
		return 
			"ArtistCreditItem{" + 
			"artist = '" + artist + '\'' + 
			"}";
		}
}