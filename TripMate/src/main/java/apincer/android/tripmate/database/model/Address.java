package apincer.android.tripmate.database.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "place_address")
public class Address {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "address_id")
    private int id;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getLine() {
        return line;
    }

    public void setLine(String line) {
        this.line = line;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getPostcode() {
        return postcode;
    }

    public void setPostcode(String postcode) {
        this.postcode = postcode;
    }

    @ColumnInfo(name = "address")
    private String line;
    @ColumnInfo(name = "address_city")
    private String city;
    @ColumnInfo(name = "address_province")
    private String province;
    @ColumnInfo(name = "address_country")
    private String country;
    @ColumnInfo(name = "address_postcode")
    private String postcode;
}
