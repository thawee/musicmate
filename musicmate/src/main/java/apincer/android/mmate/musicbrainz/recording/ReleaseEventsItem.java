package apincer.android.mmate.musicbrainz.recording;

import com.google.gson.annotations.SerializedName;

public class ReleaseEventsItem{

	@SerializedName("date")
	private String date;

	@SerializedName("area")
	private Area area;

	public void setDate(String date){
		this.date = date;
	}

	public String getDate(){
		return date;
	}

	public void setArea(Area area){
		this.area = area;
	}

	public Area getArea(){
		return area;
	}

	@Override
 	public String toString(){
		return 
			"ReleaseEventsItem{" + 
			"date = '" + date + '\'' + 
			",area = '" + area + '\'' + 
			"}";
		}
}