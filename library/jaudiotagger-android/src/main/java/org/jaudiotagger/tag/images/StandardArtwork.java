package org.jaudiotagger.tag.images;

import org.jaudiotagger.audio.flac.metadatablock.MetadataBlockDataPicture;

import java.io.File;
import java.io.IOException;

class StandardArtwork implements Artwork {
    public static Artwork createArtworkFromMetadataBlockDataPicture(MetadataBlockDataPicture coverArt) {
        return null;
    }

    public static Artwork createArtworkFromFile(File file) {
        return null;
    }

    public static Artwork createLinkedArtworkFromURL(String link) {
        return null;
    }

    @Override
    public byte[] getBinaryData() {
        return new byte[0];
    }

    @Override
    public void setBinaryData(byte[] binaryData) {

    }

    @Override
    public String getMimeType() {
        return null;
    }

    @Override
    public void setMimeType(String mimeType) {

    }

    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public int getHeight() {
        return 0;
    }

    @Override
    public int getWidth() {
        return 0;
    }

    @Override
    public void setDescription(String description) {

    }

    @Override
    public boolean setImageFromData() {
        return false;
    }

    @Override
    public Object getImage() throws IOException {
        return null;
    }

    @Override
    public boolean isLinked() {
        return false;
    }

    @Override
    public void setLinked(boolean linked) {

    }

    @Override
    public String getImageUrl() {
        return null;
    }

    @Override
    public void setImageUrl(String imageUrl) {

    }

    @Override
    public int getPictureType() {
        return 0;
    }

    @Override
    public void setPictureType(int pictureType) {

    }

    @Override
    public void setFromFile(File file) throws IOException {

    }

    @Override
    public void setFromMetadataBlockDataPicture(MetadataBlockDataPicture coverArt) {

    }

    @Override
    public void setWidth(int width) {

    }

    @Override
    public void setHeight(int height) {

    }
}
