package apincer.android.tripmate.model;

public class ReviewList {
	private String author_name = "";
	private String author_url = "";
	private String author_text = "";
	private String author_rating = "";
	public String getAuthor_name() {
		return author_name;
	}
	public void setAuthor_name(String authorName) {
		author_name = authorName;
	}
	public String getAuthor_url() {
		return author_url;
	}
	public void setAuthor_url(String authorUrl) {
		author_url = authorUrl;
	}
	public String getAuthor_text() {
		return author_text;
	}
	public void setAuthor_text(String authorText) {
		author_text = authorText;
	}
	public String getAuthor_rating() {
		return author_rating;
	}
	public void setAuthor_rating(String authorRating) {
		author_rating = authorRating;
	}
	@Override
	public String toString() {
		return "ReviewList [author_name=" + author_name + ", author_rating="
				+ author_rating + ", author_text=" + author_text
				+ ", author_url=" + author_url + "]";
	}
}