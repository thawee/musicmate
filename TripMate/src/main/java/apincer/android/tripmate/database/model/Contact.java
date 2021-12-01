package apincer.android.tripmate.database.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "place_contact")
public class Contact {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "contact_id")
    private int id;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhoneNo() {
        return phoneNo;
    }

    public void setPhoneNo(String phoneNo) {
        this.phoneNo = phoneNo;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getWww() {
        return www;
    }

    public void setWww(String www) {
        this.www = www;
    }

    public String getFacebook() {
        return facebook;
    }

    public void setFacebook(String facebook) {
        this.facebook = facebook;
    }

    public String getLineid() {
        return lineid;
    }

    public void setLineid(String lineid) {
        this.lineid = lineid;
    }

    @ColumnInfo(name = "contact_name")
    private String name;
    @ColumnInfo(name = "contact_phone")
    private String phoneNo;
    @ColumnInfo(name = "contact_email")
    private String email;
    @ColumnInfo(name = "contact_www")
    private String www;
    @ColumnInfo(name = "contact_fb")
    private String facebook;
    @ColumnInfo(name = "contact_lineid")
    private String lineid;
}
