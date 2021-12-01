package apincer.android.mmate.musicbrainz.recording;

import java.util.List;
import com.google.gson.annotations.SerializedName;

public class Artist{

	@SerializedName("aliases")
	private List<AliasesItem> aliases;

	@SerializedName("name")
	private String name;

	@SerializedName("id")
	private String id;

	@SerializedName("sort-name")
	private String sortName;

	public void setAliases(List<AliasesItem> aliases){
		this.aliases = aliases;
	}

	public List<AliasesItem> getAliases(){
		return aliases;
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
			"Artist{" + 
			"aliases = '" + aliases + '\'' + 
			",name = '" + name + '\'' + 
			",id = '" + id + '\'' + 
			",sort-name = '" + sortName + '\'' + 
			"}";
		}
}