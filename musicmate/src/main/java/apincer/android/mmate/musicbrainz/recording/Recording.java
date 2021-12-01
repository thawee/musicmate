package apincer.android.mmate.musicbrainz.recording;

import java.util.List;
import com.google.gson.annotations.SerializedName;

public class Recording{

	@SerializedName("offset")
	private int offset;

	@SerializedName("recordings")
	private List<RecordingsItem> recordings;

	@SerializedName("created")
	private String created;

	@SerializedName("count")
	private int count;

	public void setOffset(int offset){
		this.offset = offset;
	}

	public int getOffset(){
		return offset;
	}

	public void setRecordings(List<RecordingsItem> recordings){
		this.recordings = recordings;
	}

	public List<RecordingsItem> getRecordings(){
		return recordings;
	}

	public void setCreated(String created){
		this.created = created;
	}

	public String getCreated(){
		return created;
	}

	public void setCount(int count){
		this.count = count;
	}

	public int getCount(){
		return count;
	}

	@Override
 	public String toString(){
		return 
			"Recording{" + 
			"offset = '" + offset + '\'' + 
			",recordings = '" + recordings + '\'' + 
			",created = '" + created + '\'' + 
			",count = '" + count + '\'' + 
			"}";
		}
}