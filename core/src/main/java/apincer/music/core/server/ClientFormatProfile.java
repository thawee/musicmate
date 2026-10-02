package apincer.music.core.server;

import apincer.music.core.model.Track;
import apincer.music.core.utils.TagUtils;

/**
 * What a DLNA client (usually a TV) can play, from its User-Agent / X-AV-Client-Info, using the
 * evidence in UMS's renderer profiles. Formats a TV cannot play are converted to PCM (WAV,
 * lossless) or, for DSD, not listed. Other clients (control apps, hi-fi streamers) get every
 * file as it is. (Separate from the unused ProfileManager/ClientProfile buffer profiles.)
 */
public enum ClientFormatProfile {
    /** Plays FLAC, not ALAC or DSD (UMS Sony-Bravia / X-Series profiles). */
    SONY_TV(true, false, true),
    /** FLAC only from 2022 models and the User-Agent does not say the year, so FLAC is converted too. */
    LG_TV(true, true, true),
    /** No UMS profile; DSD is hidden, everything else unchanged until the client log shows more. */
    TOSHIBA_TV(true, false, false),
    DEFAULT(false, false, false);

    private final boolean hidesDsd;
    private final boolean convertsFlac;
    private final boolean convertsAlac;

    ClientFormatProfile(boolean hidesDsd, boolean convertsFlac, boolean convertsAlac) {
        this.hidesDsd = hidesDsd;
        this.convertsFlac = convertsFlac;
        this.convertsAlac = convertsAlac;
    }

    public static ClientFormatProfile of(String userAgent, String avClientInfo) {
        String name = ClientRegistry.describe(userAgent, avClientInfo);
        if (name.startsWith("Sony")) return SONY_TV;
        if (name.equals("LG webOS TV")) return LG_TV;
        if (name.equals("Toshiba TV")) return TOSHIBA_TV;
        return DEFAULT;
    }

    /** True when the track is left out of this client's listings and search results. */
    public boolean hides(Track track) {
        return hidesDsd && TagUtils.isDSDFile(track);
    }

    /** True when this client gets the track as PCM (WAV) instead of the original file. */
    public boolean convertsToPcm(Track track) {
        return (convertsFlac && TagUtils.isFLACFile(track)) || (convertsAlac && TagUtils.isALACFile(track));
    }

    /** True when the profile changes anything at all. */
    public boolean adapts() {
        return this != DEFAULT;
    }
}
