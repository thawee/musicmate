package apincer.android.mmate.musicbrainz.recording;

import com.google.gson.annotations.SerializedName;

public class AliasesItem{

	@SerializedName("begin-date")
	private Object beginDate;

	@SerializedName("end-date")
	private Object endDate;

	@SerializedName("name")
	private String name;

	@SerializedName("sort-name")
	private String sortName;

	@SerializedName("locale")
	private String locale;

	@SerializedName("type")
	private String type;

	@SerializedName("primary")
	private Object primary;

	public void setBeginDate(Object beginDate){
		this.beginDate = beginDate;
	}

	public Object getBeginDate(){
		return beginDate;
	}

	public void setEndDate(Object endDate){
		this.endDate = endDate;
	}

	public Object getEndDate(){
		return endDate;
	}

	public void setName(String name){
		this.name = name;
	}

	public String getName(){
		return name;
	}

	public void setSortName(String sortName){
		this.sortName = sortName;
	}

	public String getSortName(){
		return sortName;
	}

	public void setLocale(String locale){
		this.locale = locale;
	}

	public String getLocale(){
		return locale;
	}

	public void setType(String type){
		this.type = type;
	}

	public String getType(){
		return type;
	}

	public void setPrimary(Object primary){
		this.primary = primary;
	}

	public Object getPrimary(){
		return primary;
	}

	@Override
 	public String toString(){
		return 
			"AliasesItem{" + 
			"begin-date = '" + beginDate + '\'' + 
			",end-date = '" + endDate + '\'' + 
			",name = '" + name + '\'' + 
			",sort-name = '" + sortName + '\'' + 
			",locale = '" + locale + '\'' + 
			",type = '" + type + '\'' + 
			",primary = '" + primary + '\'' + 
			"}";
		}
}