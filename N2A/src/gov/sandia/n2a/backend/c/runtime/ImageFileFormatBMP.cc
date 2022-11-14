/*
Author: Fred Rothganger
Created 12/1/2009.

Copyright 2009-2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


#include "math.h"
#include "image.h"
#include "myendian.h"

#include <stdio.h>

#if BYTE_ORDER != LITTLE_ENDIAN
#  warning This code only supports little-endian.  Big-endian would require a small amount of work to add.
#endif


using namespace n2a;
using namespace std;


// class ImageFileDelegateBMP -------------------------------------------------

class ImageFileDelegateBMP : public ImageFileDelegate
{
public:
    ImageFileDelegateBMP (istream * in, ostream * out, bool ownStream = false);
    ~ImageFileDelegateBMP ();

    virtual void read (Image & image, int x = 0, int y = 0, int width = 0, int height = 0);
    virtual void write (const Image & image, int x = 0, int y = 0);

    virtual String get (const String & name) const;
    virtual void   set (const String & name, const String & value);

    istream * in;
    ostream * out;
    bool      ownStream;

    bool topDown;

    uint32_t * palette;
    uint32_t   fileSize;
    uint32_t   pixelsOffset;
    uint32_t   dibSize;
    int32_t    width;
    int32_t    height;
    uint16_t   planes;
    uint16_t   bitdepth;
    uint32_t   compression;
    uint32_t   pixelsSize;
    uint32_t   colors;
    uint32_t   redMask;
    uint32_t   greenMask;
    uint32_t   blueMask;
    uint32_t   alphaMask;
    uint32_t   colorSpace;
    uint32_t   profileOffset;
    uint32_t   profileSize;
    uint32_t   count;  // bytes read so far
    uint32_t   paletteEntrySize;
};

ImageFileDelegateBMP::ImageFileDelegateBMP (istream * in, ostream * out, bool ownStream)
{
    this->in        = in;
    this->out       = out;
    this->ownStream = ownStream;

    topDown = true; // Assumed format for most memory blocks held by this library.
    palette = 0;

    // Extract header information if we are in input mode.
    if (!in) return;

    //   This could be done by defining a struct and reading it in one shot.
    //   However, the approach used here avoids alignment issues.
    fileSize      = 0;
    pixelsOffset  = 0;
    dibSize       = 0;
    width         = 0;
    height        = 0;
    planes        = 0;
    bitdepth      = 0;
    compression   = 0;
    pixelsSize    = 0;
    colors        = 0;
    redMask       = 0;
    greenMask     = 0;
    blueMask      = 0;
    alphaMask     = 0;
    colorSpace    = 0;
    profileOffset = 0;
    profileSize   = 0;

    in->ignore (2);  // magic string
    in->read ((char*) &fileSize, sizeof(fileSize));
    in->ignore (4);  // reserved data
    in->read ((char*) &pixelsOffset, sizeof(pixelsOffset));
    in->read ((char*) &dibSize, sizeof(dibSize));

    if (14 + dibSize > fileSize) cerr << "WARNING: file size and DIB size are inconsistent" << endl;
    if (dibSize < 12) throw "Not enough information to extract image";
    if (dibSize == 12)  // BITMAPCOREHEADER, that is, OS/2 V1 header
    {
        uint16_t temp16;
        in->read ((char*) &temp16, sizeof(temp16));
        width = temp16;
        in->read ((char*) &temp16, sizeof(temp16));
        height = temp16;
        in->read ((char*) &temp16, sizeof(temp16));
        planes = temp16;
        in->read ((char*) &temp16, sizeof(temp16));
        bitdepth = temp16;
    }
    else  // dibSize > 12
    {
        in->read ((char *) &width,    sizeof (width));
        in->read ((char *) &height,   sizeof (height));
        in->read ((char *) &planes,   sizeof (planes));
        in->read ((char *) &bitdepth, sizeof (bitdepth));
        if (dibSize >= 40)  // BITMAPINFOHEADER
        {
            in->read ((char *) &compression, sizeof (compression));
            in->read ((char *) &pixelsSize,  sizeof (pixelsSize));
            in->ignore (8);  // horizontal and vertical resolution
            in->read ((char *) &colors,      sizeof (colors));
            in->ignore (4);  // number of "important" colors
        }
        if (dibSize == 64)  // BITMAPCOREHEADER2
        {
            in->ignore (2);  // ResUnit
            in->ignore (2);  // Reserved
            in->ignore (2);  // Orientation
            in->ignore (2);  // Halftoning
            in->ignore (4);  // HalftoneSize1
            in->ignore (4);  // HalftoneSize2
            in->ignore (4);  // ColorSpace
            in->ignore (4);  // AppData
        }
        if (dibSize >= 108)  // BITMAPV4HEADER
        {
            in->read ((char *) &redMask,    sizeof (redMask));
            in->read ((char *) &greenMask,  sizeof (greenMask));
            in->read ((char *) &blueMask,   sizeof (blueMask));
            in->read ((char *) &alphaMask,  sizeof (alphaMask));
            in->read ((char *) &colorSpace, sizeof (colorSpace));
            in->ignore (36);  // XYZ endpoints
            in->ignore (12);  // R, G, and B gamma
        }
        if (dibSize >= 124)  // BITMAPV5HEADER
        {
            in->ignore (4);  // intent
            in->read ((char *) &profileOffset, sizeof (profileOffset));
            in->read ((char *) &profileSize,   sizeof (profileSize));
            in->ignore (4);  // reserved
        }
        if (dibSize > 124)
        {
            in->ignore (dibSize - 124);
        }
    }
    count = 14 + dibSize;

    // Analyze header info, and adjust appropriate fields
    if (planes != 1) throw "Invalid number of planes";
    if (height < 0)
    {
        height = -height;
        topDown = true;
    }
    else
    {
        topDown = false;
    }
    if (colors == 0 && bitdepth < 16) colors = 0x1 << bitdepth;  // 2^bitdepth
    paletteEntrySize = dibSize == 12 ? 3 : 4;
    if (dibSize == 40 && compression == 3 && colors != 3)
    {
        if (colors != 0) cerr << "WARNING: Illegal size of color palette for BI_BITFIELDS mode: " << colors << endl;
        colors = 3;
    }
}

ImageFileDelegateBMP::~ImageFileDelegateBMP ()
{
    if (ownStream)
    {
        if (in)  delete in;
        if (out) delete out;
    }

    if (palette) free (palette);
}

void
ImageFileDelegateBMP::read (Image & image, int ignorex, int ignorey, int ignorewidth, int ignoreheight)
{
    if (!in) throw "ImageFileDelegateBMP not open for reading";

    // Read palette
    if (colors)
    {
        uint32_t paletteSize = colors * paletteEntrySize;
        if (count + paletteSize > fileSize) cerr << "WARNING: file size and palette size are inconsistent" << endl;
        if (profileOffset && count + paletteSize > profileOffset + 14) throw "profile and palette overlap";

        palette = (uint32_t*) malloc (colors * 4); // Always store 4 byte palette entries, even if actual entry size is smaller.
        if (!palette) throw "Failed to allocate buffer for palette";

        for (int i = 0; i < colors; i++) in->read ((char*) &palette[i], paletteEntrySize);
        count += paletteSize;
    }

    // Consume profile data, if necessary
    if (colorSpace == 3  ||  colorSpace == 4)  // PROFILE_LINKED, PROFILE_EMBEDDED
    {
        profileOffset += 14; // make offset relative to start of file, rather than start of DIB structure
        if (profileOffset < count) throw "Invalid profile offset";
        if (profileOffset > count) in->ignore (profileOffset - count);
        in->ignore (profileSize);
        count = profileOffset + profileSize;
    }

    // Select format and prepare image buffer
    float depth = bitdepth / 8.0f;
    int bytedepth = (int) floor (depth);
    int stride = 4 * (int) ceil (bitdepth * width / 32.0);
    if (compression <= 2)  // BI_RGB, BI_RLE4, BI_RLE8
    {
        switch (bitdepth)
        {
            case 1:
            case 4:
            case 8:
                image.format = new PixelFormatPalette (&((uint8_t*) palette)[2], &((uint8_t*) palette)[1], &((uint8_t*) palette)[0], 4, bitdepth);
                image.buffer = new PixelBufferGroups (stride, height, 8 / bitdepth, 1);
                break;
            case 16:
                image.format = &B5G5R5;
                image.buffer = new PixelBufferPacked (stride, height, 2);
                break;
            case 24:
                image.format = &BGRChar;
                image.buffer = new PixelBufferPacked (stride, height, 3);
                break;
            case 32:
                image.format = &BGRxChar;
                image.buffer = new PixelBufferPacked (stride, height, 4);
                break;
            default:
                throw "Illegal bit depth";
        }
    }
    else if (compression == 3)  // BI_BITFIELDS
    {
        if (depth != bytedepth) throw "Bitfield format must use an integer number of bytes"; // This allows more flexibility than MS documentation on BMP.  Technically, should only be 2 or 4 bytes, not 1 or 3.

        if (dibSize == 40)
        {
            redMask = palette[0];
            greenMask = palette[1];
            blueMask = palette[2];
            alphaMask = 0;
        }
        image.format = new PixelFormatRGBABits (bytedepth, redMask, greenMask, blueMask, alphaMask);
        image.buffer = new PixelBufferPacked (stride, height, bytedepth);
    }
    else if (compression == 4)  // BI_JPEG
    {
        // Assume an embedded JFIF, and pass to JPEG handler
        ImageFileFormat *jpeg;
        ImageFileFormat::find ("jpeg", jpeg);
        if (!jpeg) throw "BMP JPEG compression is unavailable";
        ImageFileDelegate *delegate = jpeg->open (*in);
        delegate->read (image, ignorex, ignorey);
        delete delegate;
        return;
    }
    else if (compression == 5)  // BI_PNG
    {
        // Assume an embedded PNG and pass to handler
        ImageFileFormat *png;
        ImageFileFormat::find ("png", png);
        if (!png) throw "BMP PNG compression is unavailable";
        ImageFileDelegate *delegate = png->open (*in);
        delegate->read (image, ignorex, ignorey);
        delete delegate;
        return;
    }
    else throw "Unimplemented compression format";
    image.width = width;
    image.height = height;

    if (palette)
    {
        free (palette);
        palette = 0;
    }

    // Read data
    if (!in->good ()) throw "Unable to finish reading image: stream bad.";
    if (pixelsOffset < count) throw "Invalid pixel offset";
    if (pixelsOffset > count) in->ignore (pixelsOffset - count);
    uint8_t *buffer;
    if      (PixelBufferPacked *pbp = (PixelBufferPacked*) image.buffer) buffer = (uint8_t*) pbp->base ();
    else if (PixelBufferGroups *pbg = (PixelBufferGroups*) image.buffer) buffer = (uint8_t*) pbg->memory;
    else throw "Unexpected buffer type";
    if (compression == 0  ||  compression == 3)  // BI_RGB, BI_BITFIELDS
    {
        if (pixelsSize && pixelsSize != stride * height) cerr << "WARNING: Pixel block size is inconsistent" << endl;
        if (topDown)
        {
            in->read ((char*) buffer, stride * height);
        }
        else
        {
            uint8_t *start = buffer;
            buffer += stride * (height - 1);
            while (buffer >= start)
            {
                in->read ((char*) buffer, stride);
                buffer -= stride;
            }
        }
    }
    else if (compression == 1)  // BI_RLE8
    {
        uint8_t *start = buffer;
        uint8_t *end = buffer + stride * height;
        if (!topDown)
        {
            buffer += stride * (height - 1);
            stride *= -1;
        }
        uint8_t *row = buffer;
        bool done = false;
        while (!done)
        {
            uint8_t count = in->get ();
            if (count)
            {
                uint8_t value = in->get ();
                if (buffer < start  ||  buffer >= end  ||  in->bad ())
                {
                    done = true;
                    break;
                }
                for (int i = 0; i < count; i++) *buffer++ = value;
            }
            else  // count == 0, indicating an escape sequence
            {
                uint8_t code = in->get ();
                switch (code)
                {
                    case 0:
                        buffer = row += stride;
                        break;
                    case 1:
                        done = true;
                        break;
                    case 2:
                    {
                        uint8_t dx = in->get ();
                        uint8_t dy = in->get ();
                        buffer += dy * stride + dx;
                        row += dy * stride;
                        break;
                    }
                    default:  // all codes >= 3 are absolute counts
                        if (buffer < start  ||  buffer >= end  ||  in->bad ())
                        {
                            done = true;
                            break;
                        }
                        for (int i = 0; i < code; i++) *buffer++ = in->get ();
                        if (code % 2) in->ignore (1);
                }
            }
        }
    }
    else  // compression == 2 == BI_RLE4
    {
        uint8_t mask = 0xF0;  // big-endian order: first pixel is in high nibble
        uint8_t *start = buffer;
        uint8_t *end = buffer + stride * height;
        if (!topDown)
        {
            buffer += stride * (height - 1);
            stride *= -1;
        }
        uint8_t *row = buffer;
        bool done = false;
        while (!done)
        {
            uint8_t count = in->get ();
            if (count)
            {
                uint8_t value = in->get ();
                if (buffer < start  ||  buffer >= end  ||  in->bad ())
                {
                    done = true;
                    break;
                }
                if (mask == 0x0F)
                {
                    value = (value << 4) | (value >> 4);
                    *buffer = (*buffer & 0xF0) | (value & 0x0F);
                    count--;
                    buffer++;
                    mask = 0xF0;
                }
                int odd = count % 2;
                int even = count / 2;
                for (int i = 0; i < even; i++) *buffer++ = value;
                if (odd)
                {
                    *buffer = value; // we don't care about lower nibble; it will be overwritten shortly
                    mask = 0x0F;
                }
            }
            else  // count == 0, indicating an escape sequence
            {
                uint8_t code = in->get ();
                switch (code)
                {
                    case 0:
                        buffer = row += stride;
                        mask = 0xF0;
                        break;
                    case 1:
                        done = true;
                        break;
                    case 2:
                    {
                        uint8_t dx = in->get ();
                        uint8_t dy = in->get ();
                        int odd = dx % 2;
                        int even = dx / 2;
                        if (odd)
                        {
                            if (mask == 0x0F) even++;
                            mask = ~mask;
                        }
                        buffer += dy * stride + even;
                        row += dy * stride;
                        break;
                    }
                    default:  // all codes >= 3 are literal sequences
                    {
                        uint8_t value;
                        if (buffer < start  ||  buffer >= end  ||  in->bad ())
                        {
                            done = true;
                            break;
                        }
                        if (mask == 0xF0)
                        {
                            for (int i = 0; i < code; i++)
                            {
                                if (i % 2 == 0) value = in->get ();
                                *buffer = (*buffer & ~mask) | (value & mask);
                                mask = ~mask;
                                if (i % 2) buffer++;
                            }
                        }
                        else  // mask == 0x0F
                        {
                            for (int i = 0; i < code; i++)
                            {
                                if (i % 2 == 0)
                                {
                                    value = in->get ();
                                    value = (value << 4) | (value >> 4);
                                }
                                *buffer = (*buffer & ~mask) | (value & mask);
                                mask = ~mask;
                                if (i % 2 == 0) buffer++;
                            }
                        }
                        if (code % 4 < 3) in->ignore (1);
                    }
                }
            }
        }
    }

    topDown = true; // If image was bottom-up, all three cases above reordered while reading.
}

void
ImageFileDelegateBMP::write (const Image & image, int ignorex, int ignorey)
{
    if (!out) throw "ImageFileDelegateBMP not open for writing";

    // Ensure an acceptable pixel format, and prepare header values
    uint32_t dibSize     = 40; // BITMAPINFOHEADER
    uint16_t bitdepth;
    uint32_t compression = 0;  // BI_RGB
    uint32_t colors      = 0;
    uint32_t redMask     = 0;
    uint32_t greenMask   = 0;
    uint32_t blueMask    = 0;
    uint32_t alphaMask   = 0;
    char * buffer;
    int stride;
    uint8_t * r;
    uint8_t * g;
    uint8_t * b;
    if (const PixelFormatRGBABits * pf = (const PixelFormatRGBABits *) image.format)
    {
        bitdepth  = (int) roundp (pf->depth * 8);
        redMask   = pf->redMask;
        greenMask = pf->greenMask;
        blueMask  = pf->blueMask;
        alphaMask = pf->alphaMask;
        if (*pf != BGRChar  &&  *pf != BGRxChar  &&  *pf != B5G5R5)
        {
            if (bitdepth == 16  ||  bitdepth == 32)
            {
                compression = 3;  // BI_BITFIELDS
                if (alphaMask) dibSize = 108; // BITMAPV4HEADER, the only way to write an alpha mask
                else           colors = 3;  // Write R, G and B masks to color palette
            }
            else if (bitdepth < 16)
            {
                write (image * B5G5R5, ignorex, ignorey);
                return;
            }
            else  // includes RGBChar
            {
                write (image * BGRChar, ignorex, ignorey);
                return;
            }
        }
        PixelBufferPacked * pbp = (PixelBufferPacked*) image.buffer;
        assert (pbp);
        buffer = (char *) pbp->base ();
        stride = pbp->stride;
    }
    else if (const PixelFormatPalette * pf = (const PixelFormatPalette *) image.format)
    {
        bitdepth = pf->bits;
        colors = 0x1 << bitdepth;
        PixelBufferGroups *pbg = (PixelBufferGroups*) image.buffer;
        assert (pbg);
        buffer = (char*) pbg->memory;
        stride = pbg->stride;
        r = &((uint8_t*) pf->palette)[3];
        g = &((uint8_t*) pf->palette)[2];
        b = &((uint8_t*) pf->palette)[1];
    }
    else if (image.format->hasAlpha)
    {
        // Convert to 32-bit RGBA
        write (image * BGRAChar, ignorex, ignorey);
        return;
    }
    else if (image.format->monochrome)
    {
        // Convert to 256-level gray palette
        unsigned char palette[256];
        for (int i = 0; i < 256; i++) palette[i] = i;
        PixelFormatPalette * pf = new PixelFormatPalette (&palette[0], &palette[0], &palette[0]);
        write (image * *pf, ignorex, ignorey); // resulting converted image should take possession of "pf" and destroy it when going out of scope
        return;
    }
    else
    {
        // Convert to 24-bit RGB
        write (image * BGRChar, ignorex, ignorey);
        return;
    }

    // Prepare remaining header fields
    int32_t  width        = image.width;
    int32_t  height       = topDown ? -image.height : image.height;
    uint32_t rowBytes     = 4 * (int) ceil (image.width * bitdepth / 32.0); // not a header field, but used to compute them
    uint16_t planes       = 1;
    uint32_t pixelsOffset = 14 + dibSize + colors * 4;
    uint32_t pixelsSize   = rowBytes * image.height;
    uint32_t fileSize     = pixelsOffset + pixelsSize;
    uint32_t resolution   = 2835; // pixels / meter; approximately 72 dpi (times 39.37 inches/meter)
    uint32_t colorSpace   = 1; // LCS_sRGB; may need to set this above, if we ever add deeper support for color spaces
    uint16_t temp16       = 0;
    uint32_t temp32       = 0;

    // Write header
    out->put ('B');
    out->put ('M');
    out->write ((char*) &fileSize,     sizeof(fileSize));
    out->write ((char*) &temp16,       sizeof(temp16));
    out->write ((char*) &temp16,       sizeof(temp16));
    out->write ((char*) &pixelsOffset, sizeof(pixelsOffset));
    out->write ((char*) &dibSize,      sizeof(dibSize));
    out->write ((char*) &width,        sizeof(width));
    out->write ((char*) &height,       sizeof(height));
    out->write ((char*) &planes,       sizeof(planes));
    out->write ((char*) &bitdepth,     sizeof(bitdepth));
    out->write ((char*) &compression,  sizeof(compression));
    out->write ((char*) &pixelsSize,   sizeof(pixelsSize));
    out->write ((char*) &resolution,   sizeof(resolution));
    out->write ((char*) &resolution,   sizeof(resolution));
    out->write ((char*) &colors,       sizeof(colors));
    out->write ((char*) &temp32,       sizeof(temp32));
    if (dibSize == 108)
    {
        out->write ((char*) &redMask,    sizeof(redMask));
        out->write ((char*) &greenMask,  sizeof(greenMask));
        out->write ((char*) &blueMask,   sizeof(blueMask));
        out->write ((char*) &alphaMask,  sizeof(alphaMask));
        out->write ((char*) &colorSpace, sizeof(colorSpace));
        for (int i = 0; i < 48; i++) out->put (0);  // color endpoints and gammas
    }

    // Write palette, if needed
    if (dibSize == 40  &&  compression == 3)
    {
        out->write ((char*) &redMask,   sizeof(redMask));
        out->write ((char*) &greenMask, sizeof(greenMask));
        out->write ((char*) &blueMask,  sizeof(blueMask));
    }
    else
    {
        for (int i = 0; i < colors; i++)
        {
            int i4 = 4 * i;
            out->put (b[i4]);
            out->put (g[i4]);
            out->put (r[i4]);
            out->put (0);
        }
    }

    // Write data
    if (stride == rowBytes)
    {
        out->write (buffer, pixelsSize);
    }
    else
    {
        for (int y = 0; y < image.height; y++)
        {
            out->write (buffer, stride);
            for (int i = stride; i < rowBytes; i++) out->put (0);
            buffer += stride;
        }
    }
}

String
ImageFileDelegateBMP::get (const String & name) const
{
    char buffer[32];
    if (name == "topdown")
    {
        if (topDown) return "1";
        return "0";
    }
    else if (name == "width")
    {
        sprintf (buffer, "%i", width);
        return buffer;
    }
    else if (name == "height")
    {
        sprintf (buffer, "%i", height);
        return buffer;
    }
    return "";
}

void
ImageFileDelegateBMP::set (const String & name, const String & value)
{
    if (name == "topdown") topDown = atoi (value.c_str ());
}


// class ImageFileFormatBMP ---------------------------------------------------

void
ImageFileFormatBMP::use ()
{
    vector<ImageFileFormat *>::iterator i;
    for (i = formats.begin (); i < formats.end (); i++)
    {
        if (typeid (**i) == typeid (ImageFileFormatBMP)) return;
    }
    formats.push_back (new ImageFileFormatBMP);
}

ImageFileDelegate*
ImageFileFormatBMP::open (std::istream & stream, bool ownStream) const
{
    return new ImageFileDelegateBMP (&stream, 0, ownStream);
}

ImageFileDelegate*
ImageFileFormatBMP::open (std::ostream & stream, bool ownStream) const
{
    return new ImageFileDelegateBMP (0, &stream, ownStream);
}

float
ImageFileFormatBMP::isIn (std::istream & stream) const
{
    String magic = "  ";  // 2 spaces
    getMagic (stream, magic);
    if (magic == "BM") return 0.8;
    if (magic == "BA") return 0.8;
    if (magic == "CI") return 0.8;
    if (magic == "CP") return 0.8;
    if (magic == "IC") return 0.8;
    if (magic == "PT") return 0.8;
    return 0;
}

float
ImageFileFormatBMP::handles (const String & formatName) const
{
    if (formatName.toLowerCase () == "bmp") return 1;
    return 0;
}
