package apincer.android.mmate.musicbrainz.coverart;

import java.util.List;
import com.google.gson.annotations.SerializedName;

public class ImagesItem{

	@SerializedName("image")
	private String image;

	@SerializedName("types")
	private List<String> types;

	@SerializedName("approved")
	private boolean approved;

	@SerializedName("edit")
	private int edit;

	@SerializedName("back")
	private boolean back;

	@SerializedName("comment")
	private String comment;

	@SerializedName("id")
	private String id;

	@SerializedName("front")
	private boolean front;

	@SerializedName("thumbnails")
	private Thumbnails thumbnails;

	public void setImage(String image){
		this.image = image;
	}

	public String getImage(){
		return image;
	}

	public void setTypes(List<String> types){
		this.types = types;
	}

	public List<String> getTypes(){
		return types;
	}

	public void setApproved(boolean approved){
		this.approved = approved;
	}

	public boolean isApproved(){
		return approved;
	}

	public void setEdit(int edit){
		this.edit = edit;
	}

	public int getEdit(){
		return edit;
	}

	public void setBack(boolean back){
		this.back = back;
	}

	public boolean isBack(){
		return back;
	}

	public void setComment(String comment){
		this.comment = comment;
	}

	public String getComment(){
		return comment;
	}

	public void setId(String id){
		this.id = id;
	}

	public String getId(){
		return id;
	}

	public void setFront(boolean front){
		this.front = front;
	}

	public boolean isFront(){
		return front;
	}

	public void setThumbnails(Thumbnails thumbnails){
		this.thumbnails = thumbnails;
	}

	public Thumbnails getThumbnails(){
		return thumbnails;
	}

	@Override
 	public String toString(){
		return 
			"ImagesItem{" + 
			"image = '" + image + '\'' + 
			",types = '" + types + '\'' + 
			",approved = '" + approved + '\'' + 
			",edit = '" + edit + '\'' + 
			",back = '" + back + '\'' + 
			",comment = '" + comment + '\'' + 
			",id = '" + id + '\'' + 
			",front = '" + front + '\'' + 
			",thumbnails = '" + thumbnails + '\'' + 
			"}";
		}
}