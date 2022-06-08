/*
 * Entagged Audio Tag library
 * Copyright (c) 2003-2005 Raphaël Slinckx <raphael@slinckx.net>
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *  
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.jaudiotagger.audio.flac;

import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.flac.metadatablock.MetadataBlockDataPicture;
import org.jaudiotagger.audio.flac.metadatablock.MetadataBlockHeader;
import org.jaudiotagger.logging.Hex;
import org.jaudiotagger.tag.FieldDataInvalidException;
import org.jaudiotagger.tag.InvalidFrameException;
import org.jaudiotagger.tag.TagField;
import org.jaudiotagger.tag.TagTextField;
import org.jaudiotagger.tag.flac.FlacTag;
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentReader;
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Read Flac Tag
 */
public class FlacTagReader
{
    // Logger Object
    public static Logger logger = Logger.getLogger("org.jaudiotagger.audio.flac");

    private VorbisCommentReader vorbisCommentReader = new VorbisCommentReader();


    public FlacTag read(Path path) throws CannotReadException, IOException
    {
        try (FileChannel fc = FileChannel.open(path))
        {
            FlacStreamReader flacStream = new FlacStreamReader(fc, path.toString() + " ");
            flacStream.findStream();

            //Hold the metadata
            VorbisCommentTag tag = null;
            List<MetadataBlockDataPicture> images = new ArrayList<MetadataBlockDataPicture>();

            //Seems like we have a valid stream
            boolean isLastBlock = false;
            while (!isLastBlock)
            {
                if (logger.isLoggable(Level.CONFIG))
                {
                    logger.config(path + " Looking for MetaBlockHeader at:" + fc.position());
                }

                //Read the header
                MetadataBlockHeader mbh = MetadataBlockHeader.readHeader(fc);
                if (mbh == null)
                {
                    break;
                }

                if (logger.isLoggable(Level.CONFIG))
                {
                    logger.config(path + " Reading MetadataBlockHeader:" + mbh.toString() + " ending at " + fc.position());
                }

                //Is it one containing some sort of metadata, therefore interested in it?

                //JAUDIOTAGGER-466:CBlocktype can be null
                if (mbh.getBlockType() != null)
                {
                    switch (mbh.getBlockType())
                    {
                        //We got a vorbiscomment comment block, parse it
                        case VORBIS_COMMENT:
                            ByteBuffer commentHeaderRawPacket = ByteBuffer.allocate(mbh.getDataLength());
                            fc.read(commentHeaderRawPacket);
                            tag = vorbisCommentReader.read(commentHeaderRawPacket.array(), false, path);
                            break;

                        case PICTURE:
                            try
                            {
                                MetadataBlockDataPicture mbdp = new MetadataBlockDataPicture(mbh, fc);
                                images.add(mbdp);
                            }
                            catch (IOException ioe)
                            {
                                logger.warning(path + "Unable to read picture metablock, ignoring:" + ioe.getMessage());
                            }
                            catch (InvalidFrameException ive)
                            {
                                logger.warning(path + "Unable to read picture metablock, ignoring" + ive.getMessage());
                            }

                            break;


                        case SEEKTABLE:
                            try
                            {
                                long pos = fc.position();
                               // MetadataBlockDataSeekTable mbdp = new MetadataBlockDataSeekTable(mbh, fc);
                                fc.position(pos + mbh.getDataLength());
                            }
                            catch (IOException ioe)
                            {
                                logger.warning(path + "Unable to readseek metablock, ignoring:" + ioe.getMessage());
                            }
                            break;

                        //This is not a metadata block we are interested in so we skip to next block
                        default:
                            if (logger.isLoggable(Level.CONFIG))
                            {
                                logger.config(path + "Ignoring MetadataBlock:" + mbh.getBlockType());
                            }
                            fc.position(fc.position() + mbh.getDataLength());
                            break;
                    }
                }
                isLastBlock = mbh.isLastBlock();
            }
            logger.config("Audio should start at:"+ Hex.asHex(fc.position()));
            
            // read audio data
            int sample = 48000 *3;
           // ByteBuffer audioData = ByteBuffer.allocateDirect((int) (fc.size() - fc.position()));
            ByteBuffer audioData = ByteBuffer.allocateDirect(sample);
            fc.read(audioData);
            // detect MQA
            //checkMQA(audioData);
            

            //Note there may not be either a tag or any images, no problem this is valid however to make it easier we
            //just initialize Flac with an empty VorbisTag
            if (tag == null)
            {
                tag = VorbisCommentTag.createNewTag();
            }
            FlacTag flacTag = new FlacTag(tag, images);
            updateMQAInfo(flacTag);
			
            return flacTag;
        }
    }


	private void updateMQAInfo(FlacTag tag) {
		try { 
		
			if(tag.hasField(MQA_ENCODER) || tag.hasField(MQA_ORIGINAL_SAMPLING_FREQUENCY)) {
	        	tag.addField( new MQATagField("MQA", "YES"));
				if(!tag.hasField(MQA_ORIGINAL_SAMPLING_FREQUENCY)) {
		       // 	tag.addField( new MQATagField("MQA", tag.getFirst(MQA_ORIGINAL_SAMPLING_FREQUENCY)));
		       // }else
		        	if(tag.hasField(MQA_ORIGINAL_SAMPLE_RATE)) {
		        		tag.addField( new MQATagField(MQA_ORIGINAL_SAMPLING_FREQUENCY, tag.getFirst(MQA_ORIGINAL_SAMPLE_RATE)));
		        	}else if(tag.hasField(MQA_SAMPLE_RATE)) {
		        		tag.addField( new MQATagField(MQA_ORIGINAL_SAMPLING_FREQUENCY, tag.getFirst(MQA_SAMPLE_RATE)));
		       // }else {
		        	}
		        }
			}
		} catch (FieldDataInvalidException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
	}


	private void checkMQA(ByteBuffer audioData) {
		// TODO Auto-generated method stub
		audioData.flip();
		  BigInteger CONST_ONE = new BigInteger("1", 10);
	        BigInteger MQA = new BigInteger("be0498c88", 16);
	        BigInteger MASK = new BigInteger("FFFFFFFFF", 16);
	        System.out.println(MQA.toString(16));
	        System.out.println(MQA.toString(2));
	        
	        BigInteger buff = new BigInteger("0",10);
	        BigInteger buff1 = new BigInteger("0",10);
	        try {
	        	int bitsPerSample= 16;
				int pos = bitsPerSample - 16; //(this->decoder.bps - 16u); // aim for 16th bit
	            for (int i = 0; i < audioData.capacity(); i += 2) {
	            	//byte[] b = new byte[2];
	            	//buffer.get(b, i, 2);
	            	//System.out.println(Long.toBinaryString(buff));	
	               // buff |= ((buffer[i] ^ buffer[i+1]) >> pos) & 1L;
	            	byte [] byte0 = new byte[1]; 
	            	byte [] byte1 = new byte[1]; 
	            	byte0[0] = audioData.get(i);
	            	byte1[0] = audioData.get(i+1);
	                BigInteger b = new BigInteger(byte0);
	                BigInteger b1 = new BigInteger(byte1);
	                b = b.xor(b1).shiftRight(pos).and(CONST_ONE);
	                buff = buff.or(b);
	               // System.out.println(Long.toBinaryString(buff.longValue()));
	               // System.out.println(Long.toHexString(MQA));	
	               // System.out.println(Long.toHexString(MASK));	
	               // System.out.println(Long.toHexString(buff));	
	               // System.out.println(buff.toString(16));	
	               // System.out.println(buff.toString(2));
	               // System.out.println(Long.toBinaryString(buff));	
	              /*  if(!buff1.equals(buff)) {
	                	System.out.println("");
	                    System.out.println("check MQA: "+MQA.toString(16)+" :: "+buff.toString(16));
	                } */
	                //if (buff == MQA) { // <== MQA magic word
	                if (MQA.equals(buff)) { // <== MQA magic word
	                   // result.MQADetection = p;
	                   // return result;
	                	System.out.println("Found MQA");	
	                	return;
	                } else {
	                   // buff = (buff << 1L) & MASK;
	                	if(!buff1.equals(buff)) {
	                	 System.out.println("<= "+buff.toString(2));
	                	}
	                	buff = buff.shiftLeft(1).and(MASK);
	                	if(!buff1.equals(buff)) {
	                	 System.out.println("=> "+buff.toString(2));
	                	}
	                }
	            }
	        	}catch(Exception ex) {
	        		ex.printStackTrace();
	        	}
	       // }
	        
	        System.out.println("NOT MQA");	
	}

    private static final String MQA_ENCODER="MQAENCODER";
    private static final String MQA_ORIGINAL_SAMPLE_RATE="ORIGINALSAMPLERATE";
    private static final String MQA_ORIGINAL_SAMPLING_FREQUENCY = "ORFS";
    private static final String MQA_SAMPLE_RATE="MQASAMPLERATE";
    
	private static final byte[] EMPTY_BYTE_ARRAY = new byte[]{};
	
	/**
     * Implementations of {@link TagTextField} for use with
     * &quot;ISO-8859-1&quot; strings.
     *
     * @author Raphaël Slinckx
     */
    protected class MQATagField implements TagTextField
    {

        /**
         * Stores the string.
         */
        private String content;

        /**
         * Stores the identifier.
         */
        private final String id;

        /**
         * Creates an instance.
         *
         * @param fieldId        The identifier.
         * @param initialContent The string.
         */
        public MQATagField(final String fieldId, final String initialContent)
        {
            this.id = fieldId;
            this.content = initialContent;
        }

        @Override
        public void copyContent(final TagField field)
        {
            if (field instanceof TagTextField)
            {
                this.content = ((TagTextField) field).getContent();
            }
        }

        @Override
        public String getContent()
        {
            return this.content;
        }

        @Override
        public Charset getEncoding()
        {
            return StandardCharsets.ISO_8859_1;
        }

        @Override
        public String getId()
        {
            return id;
        }

        @Override
        public byte[] getRawContent()
        {
            return this.content == null ? EMPTY_BYTE_ARRAY : this.content.getBytes(getEncoding());
        }

        @Override
        public boolean isBinary()
        {
            return false;
        }

        @Override
        public void isBinary(boolean b)
        {
            /* not supported */
        }

        @Override
        public boolean isCommon()
        {
            return true;
        }

        @Override
        public boolean isEmpty()
        {
            return "".equals(this.content);
        }

        @Override
        public void setContent(final String s)
        {
            this.content = s;
        }

        @Override
        public void setEncoding(final Charset s)
        {
            /* Not allowed */
        }

        @Override
        public String toString()
        {
            return getContent();
        }
    }
}

