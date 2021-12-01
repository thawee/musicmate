package apincer.android.mmate.musicbrainz.recording;

import com.google.gson.annotations.SerializedName;

public class TrackItem{

	@SerializedName("number")
	private String number;

	@SerializedName("length")
	private int length;

	@SerializedName("id")
	private String id;

	@SerializedName("title")
	private String title;

	public void setNumber(String number){
		this.number = number;
	}

	public String getNumber(){
		return number;
	}

	public void setLength(int length){
		this.length = length;
	}

	public int getLength(){
		return length;
	}

	public void setId(String id){
		this.id = id;
	}

	public String getId(){
		return id;
	}

	public void setTitle(String title){
		this.title = title;
	}

	public String getTitle(){
		return title;
	}

	@Override
 	public String toString(){
		return 
			"TrackItem{" + 
			"number = '" + number + '\'' + 
			",length = '" + length + '\'' + 
			",id = '" + id + '\'' + 
			",title = '" + title + '\'' + 
			"}";
		}
}