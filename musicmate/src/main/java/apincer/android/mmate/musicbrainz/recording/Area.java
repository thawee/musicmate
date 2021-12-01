package apincer.android.mmate.musicbrainz.recording;

import java.util.List;
import com.google.gson.annotations.SerializedName;

public class Area{

	@SerializedName("iso-3166-1-codes")
	private List<String> iso31661Codes;

	@SerializedName("name")
	private String name;

	@SerializedName("id")
	private String id;

	@SerializedName("sort-name")
	private String sortName;

	public void setIso31661Codes(List<String> iso31661Codes){
		this.iso31661Codes = iso31661Codes;
	}

	public List<String> getIso31661Codes(){
		return iso31661Codes;
	}

	public void setName(String name){
		this.name = name;
	}

	public String getName(){
		return name;
	}

	public void setId(String id){
		this.id = id;
	}

	public String getId(){
		return id;
	}

	public void setSortName(String sortName){
		this.sortName = sortName;
	}

	public String getSortName(){
		return sortName;
	}

	@Override
 	public String toString(){
		return 
			"Area{" + 
			"iso-3166-1-codes = '" + iso31661Codes + '\'' + 
			",name = '" + name + '\'' + 
			",id = '" + id + '\'' + 
			",sort-name = '" + sortName + '\'' + 
			"}";
		}
}