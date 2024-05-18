package apincer.android.mmate.repository;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "mediaserver")
public class MediaServer {
    @DatabaseField(generatedId = true,allowGeneratedIdInsert = true)
    protected long id;

    @DatabaseField
    protected String name = "";

    @DatabaseField
    protected String ip = "";

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    @DatabaseField
    protected int port = 22;

    @DatabaseField
    protected String path = "";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @DatabaseField
    protected String username = "";

    @DatabaseField
    protected String password = "";
}
