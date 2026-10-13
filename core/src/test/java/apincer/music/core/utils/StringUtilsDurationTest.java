package apincer.music.core.utils;

import org.junit.Assert;
import org.junit.Test;

public class StringUtilsDurationTest {

    @Test
    public void friendlyDuration_oneHourIsSingular() {
        Assert.assertEquals("1 Hr", StringUtils.formatDuration(3600, true));
        Assert.assertEquals("1 Hr, 5 Min", StringUtils.formatDuration(3600 + 5 * 60, true));
    }

    @Test
    public void friendlyDuration_severalHoursArePlural() {
        Assert.assertEquals("27 Hrs, 35 Min", StringUtils.formatDuration(27 * 3600 + 35 * 60, true));
    }

    @Test
    public void friendlyDuration_underOneHour() {
        Assert.assertEquals("16 Min", StringUtils.formatDuration(16 * 60, true));
        Assert.assertEquals("54 Sec", StringUtils.formatDuration(54, true));
    }

    @Test
    public void friendlyDuration_twoDaysOrMoreUsesDays() {
        Assert.assertTrue(StringUtils.formatDuration(54 * 3600, true).endsWith(" Days"));
    }
}
