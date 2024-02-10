package org.fourthline.cling.model.message.header;

public class ContentLengthHeader extends UpnpHeader<Long> {

    public ContentLengthHeader() {
    }

    public ContentLengthHeader(Long value) {
        setValue(value);
    }

    public ContentLengthHeader(String s) {
        setString(s);
    }

    public String getString() {
        return "" + getValue();
    }

    public void setString(String s) throws InvalidHeaderException {
        setValue(Long.parseLong(s));
    }
}
