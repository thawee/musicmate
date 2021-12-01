package apincer.android.tripmate.model;

public class CityDetailsList {
	private String formatted_address = "";
	private String formatted_phone_number = "";
	private String lat = "";
	private String lng = "";
	private String icon = "";
	private String id = "";
	private String name = "";
	private String open_now = "";
	private String rating = "";
	private String reference = "";
	private String author_name = "";
	private String author_url = "";
	private String text = "";
	private String website = "";
	public String getFormatted_address() {
		return formatted_address;
	}
	public void setFormatted_address(String formattedAddress) {
		formatted_address = formattedAddress;
	}
	public String getFormatted_phone_number() {
		return formatted_phone_number;
	}
	public void setFormatted_phone_number(String formattedPhoneNumber) {
		formatted_phone_number = formattedPhoneNumber;
	}
	public String getLat() {
		return lat;
	}
	public void setLat(String lat) {
		this.lat = lat;
	}
	public String getLng() {
		return lng;
	}
	public void setLng(String lng) {
		this.lng = lng;
	}
	public String getIcon() {
		return icon;
	}
	public void setIcon(String icon) {
		this.icon = icon;
	}
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getOpen_now() {
		return open_now;
	}
	public void setOpen_now(String openNow) {
		open_now = openNow;
	}
	public String getRating() {
		return rating;
	}
	public void setRating(String rating) {
		this.rating = rating;
	}
	public String getReference() {
		return reference;
	}
	public void setReference(String reference) {
		this.reference = reference;
	}
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
	public String getText() {
		return text;
	}
	public void setText(String text) {
		this.text = text;
	}
	public String getWebsite() {
		return website;
	}
	public void setWebsite(String website) {
		this.website = website;
	}
	@Override
	public String toString() {
		return "CityDetailsList [author_name=" + author_name + ", author_url="
				+ author_url + ", formatted_address=" + formatted_address
				+ ", formatted_phone_number=" + formatted_phone_number
				+ ", icon=" + icon + ", id=" + id + ", lat=" + lat + ", lng="
				+ lng + ", name=" + name + ", open_now=" + open_now
				+ ", rating=" + rating + ", reference=" + reference + ", text="
				+ text + ", website=" + website + "]";
	}
	
}