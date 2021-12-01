package apincer.android.mmate.musicbrainz.recording;

import java.util.List;
import com.google.gson.annotations.SerializedName;

public class ReleaseGroup{

	@SerializedName("primary-type")
	private String primaryType;

	@SerializedName("secondary-types")
	private List<String> secondaryTypes;

	@SerializedName("id")
	private String id;

	public void setPrimaryType(String primaryType){
		this.primaryType = primaryType;
	}

	public String getPrimaryType(){
		return primaryType;
	}

	public void setSecondaryTypes(List<String> secondaryTypes){
		this.secondaryTypes = secondaryTypes;
	}

	public List<String> getSecondaryTypes(){
		return secondaryTypes;
	}

	public void setId(String id){
		this.id = id;
	}

	public String getId(){
		return id;
	}

	@Override
 	public String toString(){
		return 
			"ReleaseGroup{" + 
			"primary-type = '" + primaryType + '\'' + 
			",secondary-types = '" + secondaryTypes + '\'' + 
			",id = '" + id + '\'' + 
			"}";
		}
}