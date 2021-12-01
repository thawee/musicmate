package apincer.android.tripmate.database.model;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Embedded;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Relation;
import androidx.room.TypeConverters;

import java.util.Date;
import java.util.List;

@Entity (tableName = "place")
public class Place {
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @NonNull
    public String getType() {
        return type;
    }

    public void setType(@NonNull String type) {
        this.type = type;
    }

    public int getPopularity() {
        return popularity;
    }

    public void setPopularity(int popularity) {
        this.popularity = popularity;
    }

    public String getRoadType() {
        return roadType;
    }

    public void setRoadType(String roadType) {
        this.roadType = roadType;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public void setName(@NonNull String name) {
        this.name = name;
    }

    @NonNull
    public String getDescription() {
        return description;
    }

    public void setDescription(@NonNull String description) {
        this.description = description;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public String getFee() {
        return fee;
    }

    public void setFee(String fee) {
        this.fee = fee;
    }

    public String getFeeNote() {
        return feeNote;
    }

    public void setFeeNote(String feeNote) {
        this.feeNote = feeNote;
    }

    public boolean isPublicSite() {
        return publicSite;
    }

    public void setPublicSite(boolean publicSite) {
        this.publicSite = publicSite;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public Contact getContact() {
        return contact;
    }

    public void setContact(Contact contact) {
        this.contact = contact;
    }

    @PrimaryKey(autoGenerate = true)
    private int id;
    @NonNull
    private String type; // national park, private campsite
    private int popularity; // 1 to 10
    private String roadType; // hike, small car, 4x4
    @NonNull
    private String name;
    @NonNull
    private String description;
    private double latitude;
    private double longitude;
    private int rating;
    private String fee;
    private String feeNote;
    private boolean publicSite; // private, public
   // @ColumnInfo(name = "date_updated")
   // @TypeConverters(DateConverter.class)
   // private Date updatedDate;
   // @ColumnInfo(name = "date_Reviewed")
   // @TypeConverters(DateConverter.class)
   // private Date reviewedDate;

    /*
    private boolean hasWifi;
    private boolean hasCellularSignal;
    private boolean hasPublicToilet;
    private boolean hasFlushToilet;
    private boolean hasHotShower;
    private boolean petAllowed;
    private boolean hasWaterFromTab;
    private boolean hasDishWashingSink;
    private boolean mountainView;
    private boolean lakeView;
    */
    @Embedded
    private Address address;
    @Embedded
    private Contact contact;
    //@Relation(parentColumn = "id", entityColumn = "place_id", entity = Comment.class)
    //private List<Comment> comments;
   // private List<Feature> features;
}