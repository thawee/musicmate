// Enhance the addDlnaHeaders method for better compatibility
private void addDlnaHeaders(Response response, MusicTag tag) {
    // Common DLNA headers
    response.getHeaders().put("transferMode.dlna.org", "Streaming");
    response.getHeaders().put(HttpHeader.CONNECTION, "keep-alive");

    // Set content type with more precise MIME type
    String mimeType = MimeTypeUtils.getMimeTypeFromPath(tag.getPath());
    response.getHeaders().put(HttpHeader.CONTENT_TYPE, mimeType != null ? mimeType : getEnhancedContentType(tag));

    // For hi-res audio, use more compatible DLNA profile
    String dlnaFeatures;
    if (tag.getAudioSampleRate() > 48000 || tag.getAudioBitsDepth() > 16) {
        // Use more compatible profile for hi-res audio
        dlnaFeatures = "DLNA.ORG_PN=LPCM;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000";
    } else {
        dlnaFeatures = getDLNAContentFeatures(tag);
    }
    response.getHeaders().put("contentFeatures.dlna.org", dlnaFeatures);

    // Additional DLNA headers for better compatibility
    response.getHeaders().put("EXT", "");  // Required by some DLNA clients
    response.getHeaders().put("realTimeInfo.dlna.org", "DLNA.ORG_TLAG=*");  // Indicates real-time streaming

    // Add DLNA.ORG_FLAGS header separately for better compatibility
    response.getHeaders().put("DLNA.ORG_FLAGS", "01700000000000000000000000000000");

    // Add content-range header even for non-range requests to improve compatibility
    if (!response.getHeaders().containsKey(HttpHeader.CONTENT_RANGE)) {
        File file = new File(tag.getPath());
        if (file.exists()) {
            response.getHeaders().put("Content-Range", "bytes 0-" + (file.length() - 1) + "/" + file.length());
        }
    }

    // Adjust buffer size based on audio format before preparing content
    adjustBufferSizeForContent(tag);
}