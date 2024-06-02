package apincer.android.mmate.server;

import org.jupnp.model.types.UDN;

import java.util.UUID;

public class DLNASystemId {
    private final UUID id;

    public DLNASystemId() {
        this.id = generate();
    }

    public UDN getUsi() {
        return new UDN(this.id);
    }

    private static UUID generate()  {
       // final UDN udn = UDN.uniqueSystemIdentifier("MusicMateMediaServer");
       // final UDN udn = new UDN();
       // final UUID i = UUID.fromString("MusicMateMediaServer");
        //return i;
        return UUID.randomUUID();
    }
}
