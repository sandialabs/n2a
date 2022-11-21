/**
Copyright 2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
**/

package gov.sandia.n2a.backend.c;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferShort;
import java.nio.file.Path;

public class VideoOut extends NativeResource implements Runnable
{
    protected static native long    construct  (String path, String format, String codec);
    protected static native void    writeByte  (long handle, double timestamp, int width, int height, int format, byte [] buffer);
    protected static native void    writeShort (long handle, double timestamp, int width, int height, int format, short[] buffer);
    protected static native void    writeInt   (long handle, double timestamp, int width, int height, int format, int  [] buffer);
    protected static native boolean good       (long handle);
    protected static native String  get        (long handle, String name);
    protected static native void    set        (long handle, String name, String value);

    public VideoOut (Path path, String format, String codec)
    {
        super (construct (path.toString (), format, codec), true);
    }

    public void run ()
    {
        close ();
    }

    public void writeNext (BufferedImage image, double timestamp)
    {
        int        width  = image.getWidth ();
        int        height = image.getHeight ();
        int        format = image.getType ();
        DataBuffer db     = image.getRaster ().getDataBuffer ();

        if (db instanceof DataBufferByte)
        {
            byte[] pixels = ((DataBufferByte) db).getData ();
            writeByte (handle, timestamp, width, height, format, pixels);
        }
        else if (db instanceof DataBufferShort)
        {
            short[] pixels = ((DataBufferShort) db).getData ();
            writeShort (handle, timestamp, width, height, format, pixels);
        }
        else if (db instanceof DataBufferInt)
        {
            int[] pixels = ((DataBufferInt) db).getData ();
            writeInt (handle, timestamp, width, height, format, pixels);
        }
        else throw new RuntimeException ("Unknown buffer type");
    }

    public boolean good ()
    {
        return good (handle);
    }

    public String get (String name)
    {
        return get (handle, name);
    }

    public void set (String name, String value)
    {
        set (handle, name, value);
    }
}
