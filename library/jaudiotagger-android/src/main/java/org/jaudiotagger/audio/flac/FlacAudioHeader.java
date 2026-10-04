package org.jaudiotagger.audio.flac;

import org.jaudiotagger.audio.generic.GenericAudioHeader;

public class FlacAudioHeader extends GenericAudioHeader
{
    //public static final String MQA_ENCODER="MQAENCODER";
    //public static final String MQA_ORIGINAL_SAMPLE_RATE="ORIGINALSAMPLERATE";
   // public static final String MQA_SAMPLE_RATE="MQASAMPLERATE";

    private String md5;
   // private String mqaEncoder;
   // private String mqaInfo;

    public String getMd5()
    {
        return md5;
    }

    public void setMd5(String md5)
    {
        this.md5 = md5;
    }
/*
    public String getMqaEncoder() {
        return mqaEncoder;
    }

    public void setMqaEncoder(String mqa) {
        this.mqaEncoder = mqa;
    }

    public String getMqaInfo() {
        return mqaInfo;
    }

    public void setMqaInfo(String mqaInfo) {
        this.mqaInfo = mqaInfo;
    } */
}
