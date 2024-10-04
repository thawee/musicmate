package apincer.android.mmate.dlna.content;

public enum TransferStatus {

    IN_PROGRESS("IN_PROGRESS"),
    STOPPED("STOPPED"),
    ERROR("ERROR"),
    COMPLETED("COMPLETED");

    private final String value;

    TransferStatus(String protocolString) {
        this.value = protocolString;
    }

    public static TransferStatus valueOrNullOf(String s) {
        for (TransferStatus value : values()) {
            if (value.toString().equals(s)) {
                return value;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return value;
    }

}