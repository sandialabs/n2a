/*
Author: Fred Rothganger
Copyright (c) 2001-2004 Dept. of Computer Science and Beckman Institute,
                        Univ. of Illinois.  All rights reserved.
Distributed under the UIUC/NCSA Open Source License.  See the file LICENSE
for details.


Copyright 2005-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


#ifndef n2a_image_h
#define n2a_image_h


#include "matrix.h"
#include "pointer.h"
#include "mystring.h"

#include <iostream>
#include <vector>
#include <assert.h>
#include <chrono>

#include "shared.h"

namespace n2a
{
    // utility function -----------------------------------------------------------

    inline double
    getTimestamp ()
    {
        return std::chrono::time_point_cast<std::chrono::duration<double>> (std::chrono::system_clock::now ()).time_since_epoch ().count ();
    }


    // Forward declaration for use by Image
    class SHARED PixelBuffer;
    class SHARED PixelFormat;
    class SHARED ImageFileFormat;


    // Image --------------------------------------------------------------------

    class SHARED Image
    {
    public:
        Image (); ///< Creates a new image of GrayChar, but with no buffer memory allocated.
        Image (const PixelFormat & format); ///< Same as above, but with given PixelFormat
        Image (int width, int height); ///< Allocates buffer of size width x height x GrayChar.depth bytes.
        Image (int width, int height, const PixelFormat & format); ///< Same as above, but with given PixelFormat
        Image (const Image & that); ///< Points our buffer to same location as "that" and copies all of its metadata.
        Image (void * block, int width, int height, const PixelFormat & format); ///< Binds to an external block of memory.  Implies PixelBufferPacked.
        Image (const MatrixAbstract<float> & A); ///< Binds to the given matrix if possible.  Otherwise copies from it.
        Image (const MatrixAbstract<double> & A); ///< Binds to the given matrix if possible.  Otherwise copies from it.
        Image (const String & fileName); ///< Create image initialized with contents of file.

        void read (const String & fileName); ///< Read image from fileName.  Format will be determined automatically.
        void read (std::istream & stream);
        void write (const String & fileName, const String & formatName = "") const; ///< Write image to fileName.
        void write (std::ostream & stream, const String & formatName = "bmp") const;

        void copyFrom (const Image & that); ///< Duplicates another Image.  Copy all raster info into private buffer, and copy all other metadata.
        void copyFrom (void * block, int width, int height, const PixelFormat & format); ///< Copy from a non-Image source.  Determine size of buffer in bytes as width x height x depth.
        void attach (void * block, int width, int height, const PixelFormat & format); ///< Binds to an external block of memory.
        void attach (const Matrix<float> & A);
        void attach (const Matrix<double> & A);
        void detach (); ///< Set the state of this image as if it has no buffer.  Releases (but only frees if appropriate) any memory.

        template<class T> Matrix<T> toMatrix () const; ///< Bind the buffer to a matrix.  Unfortunately you must know a priori what numeric type to use.
        template<class T> void attach (const Matrix<T> & A, const PixelFormat & format); ///< Binds to the contents of the given matrix.  width and height are taken from rows() and columns() respectively.

        void resize (int width, int height, bool preserve = false);
        void clear (uint32_t rgba = 0); ///< Initialize buffer, if it exists, to given color.  In case rgba == 0, simply zeroes out memory, since this generally results in black in most pixel formats.

        // Note that bounds checking for these accessor functions is the responsibility of the caller.
        uint32_t getRGBA  (int x, int y) const;
        void     getRGBA  (int x, int y, float values[]) const;
        void     getXYZ   (int x, int y, float values[]) const;
        uint32_t getYUV   (int x, int y) const;
        void     getHSL   (int x, int y, float values[]) const;
        void     getHSV   (int x, int y, float values[]) const;
        uint8_t  getGray  (int x, int y) const;
        void     getGray  (int x, int y, float & gray) const;
        uint8_t  getAlpha (int x, int y) const;
        void     setRGBA  (int x, int y, uint32_t rgba);
        void     setRGBA  (int x, int y, float values[]);
        void     setXYZ   (int x, int y, float values[]);
        void     setYUV   (int x, int y, uint32_t yuv);
        void     setHSL   (int x, int y, float values[]);
        void     setHSV   (int x, int y, float values[]);
        void     setGray  (int x, int y, uint8_t gray);
        void     setGray  (int x, int y, float gray);
        void     setAlpha (int x, int y, uint8_t alpha);
        void     blend    (int x, int y, uint32_t rgba); ///< similar setRGBA(), but respects semantics of alpha channels
        void     blend    (int x, int y, float values[]); ///< similar setRGBA(), but respects semantics of alpha channels

        PointerPoly<PixelBuffer>       buffer;
        PointerPoly<const PixelFormat> format;
        int                            width;     ///< This should be viewed as cached information from the PixelBuffer.  Only make changes via resize().
        int                            height;    ///< This should be viewed as cached information from the PixelBuffer.  Only make changes via resize().
        double                         timestamp; ///< Time when image was captured.  If part of a video, then time when image should be displayed.
    };


    // Filter -------------------------------------------------------------------

    /**
        Base class for reified functions that take as input an image and output
        another image.
    **/
    class SHARED Filter
    {
    public:
        virtual Image filter (const Image & image) = 0; ///< This could be const, but it is useful to allow filters to collect statistics.  Note that such filters are not thread safe.
    };

    inline Image
    operator * (const Filter & filter, const Image & image)
    {
        // We lie about Filter being const.  This allows you to construct a Filter
        // object right in the middle of an expression without getting complaints
        // from the compiler.  Ditto for the other operators below.
        return ((Filter&) filter).filter (image);
    }

    inline Image
    operator * (const Image & image, const Filter & filter)
    {
        return ((Filter&) filter).filter (image);
    }

    inline Image &
    operator *= (Image & image, const Filter & filter)
    {
        return image = image * ((Filter&) filter);
    }


    // PixelBuffer --------------------------------------------------------------

    /**
        Changes the stride and height of a dense raster in memory.  Used mainly as
        a utility function by PixelBuffer, but sometimes useful elsewhere.
    **/
    SHARED void reshapeBuffer (Pointer & memory, int oldStride, int newStride, int newHeight, int pad = 0);

    /**
        An interface for various classes that manage the storage of image data.
    **/
    class SHARED PixelBuffer : public ReferenceCounted
    {
    public:
        virtual ~PixelBuffer ();

        /**
            Maps (x,y) coordinates to a pointer that tells the PixelFormat where
            to get the color information.  See comment on planes for details.

            This function is not thread-safe for two reasons.  First, if the
            underlying storage moves in memory (such as cached blocks from a large
            disk file), then one call to this function could invalidate the
            pointer(s) returned by another call.  Second, if planes != 1, then one
            call to this function will change the array of pointers filled in by
            another call.  For multi-threaded code, you should surround the call to
            this function and the call to PixelFormat in a mutex.  Alternately, if
            the underlying storage doesn't move in memory, then you could create
            multiple instances of PixelBuffer that hold the same underlying storage.
        **/
        virtual void *        pixel (int x, int y) = 0;
        virtual void          resize (int width, int height, const PixelFormat & format, bool preserve = false) = 0; ///< Same semantics as Image::resize()
        virtual PixelBuffer * duplicate () const = 0; ///< Make a copy of self on the heap, with deap-copy semantics.
        virtual void          clear () = 0;  ///< Fill buffer(s) with zeros.
        virtual bool          operator == (const PixelBuffer & that) const;
        bool                  operator != (const PixelBuffer & that) const {return !(*this == that);}

        /**
            Indicates the structure pointed to by pixel().  There are three forms:
            <OL>
            <LI>planes = 1 -- A direct pointer to a packed pixel containing all the
            color channels.
            <LI>planes > 1 -- A pointer to an array of pointers, each one addressing one
            of the color channels.  The value of planes indicates the size of the array.
            <LI>planes < 1 -- A pointer to a structure.  Each kind of structure is assigned
            a unique negative number.  Current structures are:
                <UL>
                <LI>-1 -- PixelBufferGroups::PixelData
                </UL>
            </OL>
            Any value other than planes==1 is not thread safe.  The structure or
            array is a member of the PixelBuffer object, and its contents will be
            changed by the next call to pixel().  For safe operation, you must
            create a separate PixelBuffer object for each thread.  The default
            mode when assigning Image objects is to share the same PixelBuffer,
            so you must explicitly duplicate it.

            <p>A PixelFormat must have exactly the same value of planes to be
            compatible.
        **/
        int planes;
    };

    /**
        The default structure for most Images.  Each pixel contains its color
        channels contiguously, and pixels are arranged contiguously in memory.
    **/
    class SHARED PixelBufferPacked : public PixelBuffer
    {
    public:
        PixelBufferPacked (int depth = 1);
        PixelBufferPacked (int stride, int height, int depth);
        PixelBufferPacked (void * buffer, int stride, int height, int depth); ///< Binds to an external block of memory.
        PixelBufferPacked (const Pointer & buffer, int stride, int depth, int offset = 0);
        virtual ~PixelBufferPacked ();

        virtual void *        pixel (int x, int y);
        virtual void          resize (int width, int height, const PixelFormat & format, bool preserve = false); ///< We assume that stride must now be set to width.  The alternative, if width < stride, would be to take no action.
        virtual PixelBuffer * duplicate () const;
        virtual void          clear ();
        virtual bool          operator == (const PixelBuffer & that) const;

        void   copyFrom (void * buffer, int stride, int height, int depth); ///< Makes a duplicate of the block of memory.
        void * base () const;

        int     offset;
        int     stride;
        int     depth;
        Pointer memory;
    };

    /**
        Each color channel is stored in a separate block of memory.  The blocks
        are not necessarily contiguous with each other, and they don't
        necessarily have stride == width.  This structure is typical for YUV
        data used in video processing (see VideoFileFormatFFMPEG).

        Assumes only three color channels.  Treats first channel as "full size",
        while the second and third channels have vertical and horizontal devisors.
        Assumes that the second and third channels have exactly the same geometry.
        Assumes that the depth of any given channel is exactly one byte.
        When resizing or intializing without specifying stride, determines a
        stride0 that is a multiple of 16 bytes.
    **/
    class SHARED PixelBufferPlanar : public PixelBuffer
    {
    public:
        PixelBufferPlanar ();
        PixelBufferPlanar (int stride, int height, int ratioH = 1, int ratioV = 1);
        PixelBufferPlanar (void * buffer0, void * buffer1, void * buffer2, int stride0, int stride12, int height, int ratioH = 1, int ratioV = 1);
        virtual ~PixelBufferPlanar ();

        virtual void *        pixel (int x, int y);
        virtual void          resize (int width, int height, const PixelFormat & format, bool preserve = false); ///< Assumes that ratioH and ratioV are already set correctly.
        virtual PixelBuffer * duplicate () const;
        virtual void          clear ();
        virtual bool          operator == (const PixelBuffer & that) const;

        Pointer plane0;
        Pointer plane1;
        Pointer plane2;
        int stride0;
        int stride12; ///< Can be derived from stride0 / ratioH, but more efficient to pre-compute it.
        int ratioH;
        int ratioV;

        void * pixelArray[3]; ///< Temporary storage for marshalled addresses.  Not thread-safe.
    };

    /**
        Describes a packed buffer in which the pixels on each row are divided
        into functionally inseparable groups.  There are at least two cases
        where this happens:
        <ul>
        <li>gray formats where each pixel is smaller than one byte, for example
        1-bit monochrome with 8 pixels per byte.
        <li>YUV formats where several Y values share a common pair of U and V
        values.
        </ul>
    **/
    class SHARED PixelBufferGroups : public PixelBuffer
    {
    public:
        PixelBufferGroups (int pixels, int bytes);
        PixelBufferGroups (int stride, int height, int pixels, int bytes);
        PixelBufferGroups (void * buffer, int stride, int height, int pixels, int bytes);
        virtual ~PixelBufferGroups ();

        virtual void *        pixel (int x, int y);
        virtual void          resize (int width, int height, const PixelFormat & format, bool preserve = false); ///< stride will be set to ceil (width * groupBytes / groupPixels).
        virtual PixelBuffer * duplicate () const;
        virtual void          clear ();
        virtual bool          operator == (const PixelBuffer & that) const;

        int     stride;
        int     pixels; ///< Pixels per group.
        int     bytes;  ///< Bytes per group.
        Pointer memory;

        struct PixelData
        {
            uint8_t * address; ///< Pointer to first byte of pixel group.
            int       index;   ///< Indicates which pixel in group to select.  Defined simply as x % groupPixels.
        };
        PixelData pixelData;
    };


    // PixelFormat --------------------------------------------------------------

    /**
        A PixelFormat wraps access to an element of an Image.  A pixel itself is
        sort of the combination of a pointer to memory and a PixelFormat.
        A PixelFormat describes the entire collection of pixels in an image, and
        we use it to interpret each pixel in the image.
        PixelFormat extends Filter so it can be used directly to convert image
        format.  The "from*" methods implement an n^2 set of direct conversions
        between selected formats.  These conversions occur frequently when
        displaying images on an X windows system.
        For the present, all formats except for XYZ make sRGB assumptions (see
        www.srgb.com).  In addition, all integer values are non-linear (with
        gamma = 2.2 as per sRGB spec), and all floating point values are linear.
        We can add parameters to the formats if we need to distinguish more color
        spaces.

        <p>Naming convention for PixelFormats
        = <color space><basic C type for one channel>
        Color space names refer to sequence of channels (usually bytes) in
        memory, rather than in machine words (eg: registers in the processor).
        The leftmost letter in a name refers to the lowest numbered address.
        If a channel is larger than one byte, then the bytes are laid out
        within the channel according to the standard for the machine.
        The channel name "x" is a byte that is unused.

        <p>Naming convention for accessor methods --
        The data is in machine words, so names describe sequence
        within machine words.  Accessors guarantee that the order in the
        machine word will be the same, regardless of endian.  (This implies
        that the implementation of an accessor changes with endian.)
        The leftmost letter refers to the most significant byte in the word.
        Some accessors take arrays, and since arrays are memory blocks they
        follow the memory order convention.

        <p>According to the Poynton color FAQ, YUV refers to a very specific
        scaling of the color difference values for composite signals, and it is
        improper to use these channel names for anything else.  However, in
        colloquial usage U and V refer generically to color difference values
        without regard to scaling.  If there is ever a need to deal with the
        real YUV color space, everything will have to be renamed to make the
        semantics clear.  Until then, in this code U and V will be synonyms
        for Pb and Pr scaled to unsigned chars with a bias of 128.

        <p>Another note on YUV: These formats are pretty specific to video,
        and it is unlikely that there will ever be YUV formats with channel
        sizes other than char, so these format names do not bear the obligatory
        "Char" on the end.  This can also be changed once the first exception
        becomes evident.

        @todo Add accessor for numbered color channels.  This will be most
        meaningful for hyperspectal data.
    **/
    class SHARED PixelFormat : public Filter, public ReferenceCounted
    {
    public:
        virtual ~PixelFormat ();

        virtual Image filter  (const Image & image);  ///< Return an Image in this format
        virtual void  fromAny (const Image & image, Image & result) const;

        virtual PixelBuffer * buffer () const; ///< Construct a PixelBuffer suitable for holding data of the type described by this object.
        virtual PixelBuffer * attach (void * block, int width, int height, bool copy = false) const; ///< Creates a suitable PixelBuffer bound to the given external block of memory.  Makes best effort to guess the start of each plane in the case of planar formats.  (Default implementation assumes packed buffer.)

        virtual bool operator == (const PixelFormat & that) const; ///< Checks if this and that describe the same interpretation of memory contents.
        bool         operator != (const PixelFormat & that) const {return ! operator== (that);}

        virtual uint32_t getRGBA  (void * pixel) const = 0; ///< Return value is always assumed to be non-linear sRGB.  Same for other integer RGB methods below.
        virtual void     getRGBA  (void * pixel, float values[]) const; ///< "values" must have at least four elements.  Each returned value is in [0,1].
        virtual void     getXYZ   (void * pixel, float values[]) const;
        virtual uint32_t getYUV   (void * pixel) const;
        virtual void     getHSL   (void * pixel, float values[]) const;
        virtual void     getHSV   (void * pixel, float values[]) const;
        virtual uint8_t  getGray  (void * pixel) const;
        virtual void     getGray  (void * pixel, float & gray) const;
        virtual uint8_t  getAlpha (void * pixel) const; ///< Returns fully opaque by default.  PixelFormats that actually have an alpha channel must override this to return correct value.
        virtual void     setRGBA  (void * pixel, uint32_t rgba) const = 0;
        virtual void     setRGBA  (void * pixel, float values[]) const; ///< Each value must be in [0,1].  Values outside this range will be clamped and modified directly in the array.
        virtual void     setXYZ   (void * pixel, float values[]) const;
        virtual void     setYUV   (void * pixel, uint32_t yuv) const;
        virtual void     setHSL   (void * pixel, float values[]) const;
        virtual void     setHSV   (void * pixel, float values[]) const;
        virtual void     setGray  (void * pixel, uint8_t gray) const;
        virtual void     setGray  (void * pixel, float gray) const;
        virtual void     setAlpha (void * pixel, uint8_t alpha) const; ///< Ignored by default.  Formats that actually have an alpha channel must override this method.
        virtual void     blend    (void * pixel, uint32_t rgba) const;
        virtual void     blend    (void * pixel, float values[]) const;

        int   planes;     ///< The number of entries in the array passed through the "pixel" parameter.  See PixelBuffer::planes for semantics.  This format must agree with the PixelBuffer on the meaning of the pixel pointer.
        float depth;      ///< Number of bytes per pixel, including any padding.  This could have been defined as bits per pixel, but there actually exists a format (4CC==IF09) which has a non-integral number of bits per pixel.  Defined as bytes, this field allows one to compute the total number of bytes needed by the image (even for planar formats) as width * height * depth.
        int   precedence; ///< Imposes a (partial?) order on formats according to information content.  Bigger numbers have more information.
        // The following two flags could be implemented several different ways.
        // One alternative would be to make a more complicated class hierarchy
        // that implies the information.  Eg: have intermediate classes
        // PixelFormatMonochrome and PixelFormatColor.  Another alternative is
        // to put channel information into a bitmap and use masks to determine
        // various states.
        bool monochrome; ///< Indicates that this format has no color components.
        bool hasAlpha;   ///< Indicates that this format has a real alpha channel (as apposed to a default alpha value).

        // Look up tables for conversion between linear and non-linear values.
        static uint8_t * lutFloat2Char;      ///< First convert float in [0,1] to unsigned short, then offset into this table to get unsigned char value.
        static float *   lutChar2Float;      ///< Use unsigned char value directly as index into this table of float values.
        static uint8_t * buildFloat2Char (); ///< Construct lutFloat2Char during static initialization.
        static float *   buildChar2Float (); ///< Construct lutChar2Float during static initialization.
    };
    SHARED void freeLUT ();  ///< Free static lookup tables created by PixelFormat and PixelFormatPlanarYCbCr. Called just before program terminates to make memory debuggers like valgrind happy. Otherwise, completely unnecessary.

    /**
        Specifies the interface required by PixelBufferGroups.
    **/
    class SHARED Macropixel
    {
    public:
        int pixels; ///< Pixels per group.
        int bytes;  ///< Bytes per group.
    };

    class SHARED PixelFormatPalette : public PixelFormat, public Macropixel
    {
    public:
        PixelFormatPalette (uint8_t * r = 0, uint8_t * g = 0, uint8_t * b = 0, int stride = 1, int bits = 8, bool bigendian = true); ///< r, g, b and stride specify the structure of the source table, which we copy into our internal format.

        virtual PixelBuffer * attach (void * block, int width, int height, bool copy = false) const;

        virtual bool operator == (const PixelFormat & that) const;

        virtual uint32_t getRGBA (void * pixel) const;
        virtual void     setRGBA (void * pixel, uint32_t rgba) const;

        int      bits;         ///< Number of bits in one pixel.
        uint8_t  masks[8];     ///< An array of bit masks for each pixel packed in one byte.  x % bits gives an offset into this array.  There may be some wasted entries, but this is a trivial loss in exchange for the simplicity of a fixed-size array.
        int      shifts[8];    ///< How far to downshift the indexed pixel to put it in the least significant position.
        uint32_t palette[256]; ///< Colors stored as rgba values, that is, exactly the form returned by getRGBA().
    };

    /**
        Interprets gray values consisting of fewer than 8 bits, such that several
        (2, 4 or 8) gray pixels are packed into one byte.  This could
        easily be generalized to chunks bigger than 1 byte, but presently there
        is no need.  PixelFormatGrayChar is equivalent to this format with
        bits == 8.  PixelFormatGrayShort also handles a set of significant bits
        other than the full word, but it is a somewhat different layout, since
        only one pixel occurs in any one word.
    **/
    class SHARED PixelFormatGrayBits : public PixelFormat, public Macropixel
    {
    public:
        /**
            @param bigendian Indicates that the pixel with the smallest horizontal
            coordinate (that is, X position) appears in the most significant bit(s)
            of the byte.  Within a single pixel, the ordering of the bits is
            unnaffected by this flag, and always follows the convention of the
            machine.
        **/
        PixelFormatGrayBits (int bits = 1, bool bigendian = true);

        virtual PixelBuffer*
        attach (void * block, int width, int height, bool copy = false) const;

        virtual bool operator == (const PixelFormat & that) const;

        virtual uint32_t getRGBA (void * pixel) const;
        virtual void     setRGBA (void * pixel, uint32_t rgba) const;

        int     bits;      ///< Number of bits in one pixel.
        uint8_t masks[8];  ///< An array of bit masks for each pixel packed in one byte.  x % bits gives an offset into this array.  There may be some wasted entries, but this is a trivial loss in exchange for the simplicity of a fixed-size array.
        int     shifts[8]; ///< How far to upshift the indexed pixel to put it in the most significant position.
    };

    class SHARED PixelFormatGrayChar : public PixelFormat
    {
    public:
        PixelFormatGrayChar ();

        virtual Image filter         (const Image & image);
        virtual void  fromAny        (const Image & image, Image & result) const;
        void          fromGrayShort  (const Image & image, Image & result) const;
        void          fromGrayFloat  (const Image & image, Image & result) const;
        void          fromGrayDouble (const Image & image, Image & result) const;
        void          fromRGBAChar   (const Image & image, Image & result) const;
        void          fromRGBABits   (const Image & image, Image & result) const;
        void          fromYCbCr      (const Image & image, Image & result) const;

        virtual bool operator == (const PixelFormat & that) const;

        virtual uint32_t getRGBA (void * pixel) const;
        virtual void     getXYZ  (void * pixel, float values[]) const;
        virtual uint8_t  getGray (void * pixel) const;
        virtual void     getGray (void * pixel, float & gray) const;
        virtual void     setRGBA (void * pixel, uint32_t rgba) const;
        virtual void     setXYZ  (void * pixel, float values[]) const;
        virtual void     setGray (void * pixel, uint8_t gray) const;
        virtual void     setGray (void * pixel, float gray) const;
    };

    class SHARED PixelFormatGrayAlphaChar : public PixelFormat
    {
    public:
        PixelFormatGrayAlphaChar ();

        virtual uint32_t getRGBA (void * pixel) const;
        virtual void     setRGBA (void * pixel, uint32_t rgba) const;
    };

    class SHARED PixelFormatGrayShort : public PixelFormat
    {
    public:
        PixelFormatGrayShort (uint16_t grayMask = 0xFFFF);

        virtual Image filter         (const Image & image);
        virtual void  fromAny        (const Image & image, Image & result) const;
        void          fromGrayChar   (const Image & image, Image & result) const;
        void          fromGrayFloat  (const Image & image, Image & result) const;
        void          fromGrayDouble (const Image & image, Image & result) const;

        virtual bool operator == (const PixelFormat & that) const;

        virtual uint32_t getRGBA (void * pixel) const;
        virtual void     getXYZ  (void * pixel, float values[]) const;
        virtual uint8_t  getGray (void * pixel) const;
        virtual void     getGray (void * pixel, float & gray) const;
        virtual void     setRGBA (void * pixel, uint32_t rgba) const;
        virtual void     setXYZ  (void * pixel, float values[]) const;
        virtual void     setGray (void * pixel, uint8_t gray) const;
        virtual void     setGray (void * pixel, float gray) const;

        uint16_t grayMask;  ///< Indicates what (contiguous) bit in the pixel actually carry intensity info.
        int      grayShift; ///< How many bits to shift grayMask to align the msb with bit 15.
    };

    /**
        Pixels are 16-bit signed integers.
    **/
    class SHARED PixelFormatGrayShortSigned : public PixelFormat
    {
    public:
        PixelFormatGrayShortSigned (int32_t bias = 0x8000, int32_t scale = 0xFFFF);

        virtual bool operator == (const PixelFormat & that) const;

        virtual uint32_t getRGBA (void * pixel) const;
        virtual void     getRGBA (void * pixel, float values[]) const;
        virtual void     getXYZ  (void * pixel, float values[]) const;
        virtual void     getGray (void * pixel, float & gray) const;
        virtual void     setRGBA (void * pixel, uint32_t rgba) const;
        virtual void     setRGBA (void * pixel, float values[]) const;
        virtual void     setXYZ  (void * pixel, float values[]) const;
        virtual void     setGray (void * pixel, float gray) const;

        int32_t bias;  ///< The value to add when converting to an unsigned integer: u = s + bias. Negative results are clipped to zero. Maximum positive result is 0xFFFF. In some cases the result may exceed this, but only when converting to floating-point formats.
        int32_t scale; ///< The unsigned value of maximum brightness when converting to floating-point: f = (s + bias) * scale. WARNING: Changing the default will produce semantic differences between float and integer results.
    };

    class SHARED PixelFormatGrayAlphaShort : public PixelFormat
    {
    public:
        PixelFormatGrayAlphaShort ();

        virtual uint32_t getRGBA (void * pixel) const;
        virtual void     setRGBA (void * pixel, uint32_t rgba) const;
    };

    class SHARED PixelFormatGrayFloat : public PixelFormat
    {
    public:
        PixelFormatGrayFloat ();

        virtual Image filter         (const Image & image);
        virtual void  fromAny        (const Image & image, Image & result) const;
        void          fromGrayChar   (const Image & image, Image & result) const;
        void          fromGrayShort  (const Image & image, Image & result) const;
        void          fromGrayDouble (const Image & image, Image & result) const;
        void          fromRGBAChar   (const Image & image, Image & result) const;
        void          fromRGBChar    (const Image & image, Image & result) const;
        void          fromRGBABits   (const Image & image, Image & result) const;
        void          fromYCbCr      (const Image & image, Image & result) const;

        virtual uint32_t getRGBA (void * pixel) const;
        virtual void     getRGBA (void * pixel, float values[]) const;
        virtual void     getXYZ  (void * pixel, float values[]) const;
        virtual uint8_t  getGray (void * pixel) const;
        virtual void     getGray (void * pixel, float & gray) const;
        virtual void     setRGBA (void * pixel, uint32_t rgba) const;
        virtual void     setRGBA (void * pixel, float values[]) const;
        virtual void     setXYZ  (void * pixel, float values[]) const;
        virtual void     setGray (void * pixel, uint8_t gray) const;
        virtual void     setGray (void * pixel, float gray) const;
    };

    class SHARED PixelFormatGrayDouble : public PixelFormat
    {
    public:
        PixelFormatGrayDouble ();

        virtual Image filter        (const Image & image);
        virtual void  fromAny       (const Image & image, Image & result) const;
        void          fromGrayChar  (const Image & image, Image & result) const;
        void          fromGrayShort (const Image & image, Image & result) const;
        void          fromGrayFloat (const Image & image, Image & result) const;
        void          fromRGBAChar  (const Image & image, Image & result) const;
        void          fromRGBChar   (const Image & image, Image & result) const;
        void          fromRGBABits  (const Image & image, Image & result) const;
        void          fromYCbCr     (const Image & image, Image & result) const;

        virtual uint32_t getRGBA (void * pixel) const;
        virtual void     getRGBA (void * pixel, float values[]) const;
        virtual void     getXYZ  (void * pixel, float values[]) const;
        virtual uint8_t  getGray (void * pixel) const;
        virtual void     getGray (void * pixel, float & gray) const;
        virtual void     setRGBA (void * pixel, uint32_t rgba) const;
        virtual void     setRGBA (void * pixel, float values[]) const;
        virtual void     setXYZ  (void * pixel, float values[]) const;
        virtual void     setGray (void * pixel, uint8_t gray) const;
        virtual void     setGray (void * pixel, float gray) const;
    };

    /**
        Allows construction of arbitrary RGBA formats.  Mainly used to support
        X windows interface.  This class is named "RGBA", but this just
        indicates what channels are supported.  The order of the
        channels is actually arbitrary.  Bitmasks define the position of each
        channel, and are by nature in terms of machine words.  Therefore,
        a particular set of bitmasks will have different meanings on different
        endian machines.
    **/
    class SHARED PixelFormatRGBABits : public PixelFormat
    {
    public:
#       if BYTE_ORDER == LITTLE_ENDIAN
        PixelFormatRGBABits (int depth = 4, uint32_t redMask = 0xFF, uint32_t greenMask = 0xFF00, uint32_t blueMask = 0xFF0000, uint32_t alphaMask = 0xFF000000);
#       else
        PixelFormatRGBABits (int depth = 4, uint32_t redMask = 0xFF000000, uint32_t greenMask = 0xFF0000, uint32_t blueMask = 0xFF00, uint32_t alphaMask = 0xFF);
#       endif

        virtual Image filter         (const Image & image);
        void          fromGrayChar   (const Image & image, Image & result) const;
        void          fromGrayShort  (const Image & image, Image & result) const;
        void          fromGrayFloat  (const Image & image, Image & result) const;
        void          fromGrayDouble (const Image & image, Image & result) const;
        void          fromRGBABits   (const Image & image, Image & result) const;
        void          fromYCbCr      (const Image & image, Image & result) const;

        virtual bool operator == (const PixelFormat & that) const;

        virtual uint32_t getRGBA  (void * pixel) const;
        virtual uint8_t  getAlpha (void * pixel) const;
        virtual void     setRGBA  (void * pixel, uint32_t rgba) const;
        virtual void     setAlpha (void * pixel, uint8_t alpha) const;

        void       shift (uint32_t redMask, uint32_t greenMask, uint32_t blueMask, uint32_t alphaMask, int & redShift, int & greenShift, int & blueShift, int & alphaShift) const;
        static int countBits (uint32_t mask);

        uint32_t redMask;
        uint32_t greenMask;
        uint32_t blueMask;
        uint32_t alphaMask;
        int redBits;
        int greenBits;
        int blueBits;
        int alphaBits;
    };

    class SHARED PixelFormatRGBAChar : public PixelFormatRGBABits
    {
    public:
        PixelFormatRGBAChar ();

        virtual Image filter         (const Image & image);
        virtual void  fromAny        (const Image & image, Image & result) const;
        void          fromGrayChar   (const Image & image, Image & result) const;
        void          fromGrayFloat  (const Image & image, Image & result) const;
        void          fromGrayDouble (const Image & image, Image & result) const;
        void          fromRGBChar    (const Image & image, Image & result) const;
        void          fromPackedYUV  (const Image & image, Image & result) const;

        virtual uint32_t getRGBA  (void * pixel) const;
        virtual uint8_t  getAlpha (void * pixel) const;
        virtual void     setRGBA  (void * pixel, uint32_t rgba) const;
        virtual void     setAlpha (void * pixel, uint8_t alpha) const;
    };

    class SHARED PixelFormatRGBChar : public PixelFormatRGBABits
    {
    public:
        PixelFormatRGBChar ();

        virtual Image filter         (const Image & image);
        void          fromGrayChar   (const Image & image, Image & result) const;
        void          fromGrayShort  (const Image & image, Image & result) const;
        void          fromGrayFloat  (const Image & image, Image & result) const;
        void          fromGrayDouble (const Image & image, Image & result) const;
        void          fromRGBAChar   (const Image & image, Image & result) const;

        virtual uint32_t getRGBA (void * pixel) const;
        virtual void     setRGBA (void * pixel, uint32_t rgba) const;
    };

    class SHARED PixelFormatRGBAShort : public PixelFormat
    {
    public:
        PixelFormatRGBAShort ();

        virtual uint32_t getRGBA  (void * pixel) const;
        virtual void     getRGBA  (void * pixel, float values[]) const;
        virtual uint8_t  getAlpha (void * pixel) const;
        virtual void     setRGBA  (void * pixel, uint32_t rgba) const;
        virtual void     setRGBA  (void * pixel, float values[]) const;
        virtual void     setAlpha (void * pixel, uint8_t alpha) const;
    };

    class SHARED PixelFormatRGBShort : public PixelFormat
    {
    public:
        PixelFormatRGBShort ();

        virtual uint32_t getRGBA (void * pixel) const;
        virtual void     setRGBA (void * pixel, uint32_t rgba) const;
    };

    class SHARED PixelFormatRGBAFloat : public PixelFormat
    {
    public:
        PixelFormatRGBAFloat ();

        virtual void fromAny (const Image & image, Image & result) const; ///< This method is necessary because the default conversion goes through RGBAChar, sometimes producing unnecessary information loss.

        virtual uint32_t getRGBA  (void * pixel) const;
        virtual void     getRGBA  (void * pixel, float values[]) const;
        virtual uint8_t  getAlpha (void * pixel) const;
        virtual void     setRGBA  (void * pixel, uint32_t rgba) const;
        virtual void     setRGBA  (void * pixel, float values[]) const;
        virtual void     setAlpha (void * pixel, uint8_t alpha) const;
        virtual void     blend    (void * pixel, float values[]) const;
    };

    /// Similar to RGBAFloat, but without the alpha channel
    class SHARED PixelFormatRGBFloat : public PixelFormat
    {
    public:
        PixelFormatRGBFloat ();

        virtual void fromAny (const Image & image, Image & result) const; ///< See comment on RGBAFloat.

        virtual uint32_t getRGBA (void * pixel) const;
        virtual void     getRGBA (void * pixel, float values[]) const;
        virtual void     setRGBA (void * pixel, uint32_t rgba) const;
        virtual void     setRGBA (void * pixel, float values[]) const;
    };

    /// Stores non-linear values as float
    class SHARED PixelFormatSRGBFloat : public PixelFormat
    {
    public:
        PixelFormatSRGBFloat ();

        virtual uint32_t getRGBA (void * pixel) const;
        virtual void     getRGBA (void * pixel, float values[]) const;
        virtual void     setRGBA (void * pixel, uint32_t rgba) const;
        virtual void     setRGBA (void * pixel, float values[]) const;
    };

    class SHARED PixelFormatXYZFloat : public PixelFormat
    {
    public:
        PixelFormatXYZFloat ();

        virtual uint32_t getRGBA (void * pixel) const;
        virtual void     getXYZ  (void * pixel, float values[]) const;
        virtual void     setRGBA (void * pixel, uint32_t rgba) const;
        virtual void     setXYZ  (void * pixel, float values[]) const;
    };

    class SHARED PixelFormatYUV : public PixelFormat
    {
    public:
        PixelFormatYUV (int ratioH = 1, int ratioV = 1);

        int ratioH;  ///< How many horizontal luma samples per chroma sample.
        int ratioV;  ///< How many vertical luma samples per chroma sample.
    };

    class SHARED PixelFormatPackedYUV : public PixelFormatYUV, public Macropixel
    {
    public:
        struct YUVindex
        {
            int y;
            int u;
            int v;
        };

        PixelFormatPackedYUV (YUVindex * table = 0);
        virtual ~PixelFormatPackedYUV ();

        virtual Image filter  (const Image & image);
        virtual void  fromAny (const Image & image, Image & result) const;
        void          fromYUV (const Image & image, Image & result) const;

        virtual PixelBuffer * attach (void * block, int width, int height, bool copy = false) const;

        virtual bool operator == (const PixelFormat & that) const;

        virtual uint32_t getRGBA (void * pixel) const;
        virtual uint32_t getYUV  (void * pixel) const;
        virtual uint8_t  getGray (void * pixel) const;
        virtual void     setRGBA (void * pixel, uint32_t rgba) const;
        virtual void     setYUV  (void * pixel, uint32_t yuv) const;

        YUVindex * table;
    };

    class SHARED PixelFormatPlanarYUV : public PixelFormatYUV
    {
    public:
        PixelFormatPlanarYUV (int ratioH = 1, int ratioV = 1);

        virtual void fromAny (const Image & image, Image & result) const;

        virtual PixelBuffer * attach (void * block, int width, int height, bool copy = false) const;

        virtual bool operator == (const PixelFormat & that) const;

        virtual uint32_t getRGBA (void * pixel) const;
        virtual uint32_t getYUV  (void * pixel) const;
        virtual uint8_t  getGray (void * pixel) const;
        virtual void     setRGBA (void * pixel, uint32_t rgba) const;
        virtual void     setYUV  (void * pixel, uint32_t yuv) const;
    };

    /**
        Same as PixelFormatPlanarYUV, except that 16 <= Y <= 235 and
        16 <= U,V <= 240.  Video standards call for footroom and headroom
        to allow for analog signal overshoots.
    **/
    class SHARED PixelFormatPlanarYCbCr : public PixelFormatYUV
    {
    public:
        PixelFormatPlanarYCbCr (int ratioH = 1, int ratioV = 1);

        virtual void fromAny (const Image & image, Image & result) const;

        virtual PixelBuffer * attach (void * block, int width, int height, bool copy = false) const;

        virtual bool operator == (const PixelFormat & that) const;

        virtual uint32_t getRGBA (void * pixel) const;
        virtual uint32_t getYUV  (void * pixel) const;
        virtual void     getGray (void * pixel, float & gray) const;
        virtual void     setRGBA (void * pixel, uint32_t rgba) const;
        virtual void     setYUV  (void * pixel, uint32_t yuv) const;
        virtual void     setGray (void * pixel, float gray) const;

        static uint8_t * lutYin;
        static uint8_t * lutUVin;
        static uint8_t * lutYout;
        static uint8_t * lutUVout;
        static float *   lutGrayOut;
        static uint8_t * buildAll (); ///< Returns the value of lutYin, but actually constructs and assigns all 6 luts.
    };

    class SHARED PixelFormatHSLFloat : public PixelFormat
    {
    public:
        PixelFormatHSLFloat ();

        virtual uint32_t getRGBA (void * pixel) const;
        virtual void     getRGBA (void * pixel, float values[]) const;
        virtual void     getHSL  (void * pixel, float values[]) const;
        virtual void     setRGBA (void * pixel, uint32_t rgba) const;
        virtual void     setRGBA (void * pixel, float values[]) const;
        virtual void     setHSL  (void * pixel, float values[]) const;
    };

    class SHARED PixelFormatHSVFloat : public PixelFormat
    {
    public:
        PixelFormatHSVFloat ();

        virtual uint32_t getRGBA (void * pixel) const;
        virtual void     getRGBA (void * pixel, float values[]) const;
        virtual void     getHSV  (void * pixel, float values[]) const;
        virtual void     setRGBA (void * pixel, uint32_t rgba) const;
        virtual void     setRGBA (void * pixel, float values[]) const;
        virtual void     setHSV  (void * pixel, float values[]) const;
    };

    extern SHARED PixelFormatGrayChar        GrayChar;
    extern SHARED PixelFormatGrayAlphaChar   GrayAlphaChar;
    extern SHARED PixelFormatGrayShort       GrayShort;
    extern SHARED PixelFormatGrayShortSigned GrayShortSigned;
    extern SHARED PixelFormatGrayAlphaShort  GrayAlphaShort;
    extern SHARED PixelFormatGrayFloat       GrayFloat;
    extern SHARED PixelFormatGrayDouble      GrayDouble;
    extern SHARED PixelFormatRGBAChar        RGBAChar;
    extern SHARED PixelFormatRGBAShort       RGBAShort;
    extern SHARED PixelFormatRGBAFloat       RGBAFloat;
    extern SHARED PixelFormatRGBChar         RGBChar;
    extern SHARED PixelFormatRGBShort        RGBShort;
    extern SHARED PixelFormatRGBFloat        RGBFloat;
    extern SHARED PixelFormatSRGBFloat       sRGBFloat; // with sRGB gamma, rather than linear values
    extern SHARED PixelFormatXYZFloat        XYZFloat;
    extern SHARED PixelFormatRGBABits        B5G5R5;
    extern SHARED PixelFormatRGBABits        B5G6R5;
    extern SHARED PixelFormatRGBABits        BGRChar;
    extern SHARED PixelFormatRGBABits        BGRxChar;  // 4-byte word, where last byte is not used
    extern SHARED PixelFormatRGBABits        RGBxChar;  // ditto
    extern SHARED PixelFormatRGBABits        BGRAChar;
    extern SHARED PixelFormatRGBABits        ABGRChar;
    extern SHARED PixelFormatPackedYUV       UYVY;
    extern SHARED PixelFormatPackedYUV       YUYV;
    extern SHARED PixelFormatPackedYUV       UYV;
    extern SHARED PixelFormatPackedYUV       UYYVYY;
    extern SHARED PixelFormatPackedYUV       UYVYUYVYYYYY;
    extern SHARED PixelFormatPlanarYCbCr     YUV420;
    extern SHARED PixelFormatPlanarYCbCr     YUV411;
    extern SHARED PixelFormatHSLFloat        HSLFloat;
    extern SHARED PixelFormatHSVFloat        HSVFloat;

    // Naming convention for RGBABits:
    // R<red bits>G<green bits>B<blue bits>
    // EG: R5G5B5 would be a 15 bit RGB format.


    // File formats -------------------------------------------------------------

    /**
        Helper class for ImageFile that actually implements the methods for a
        specific codec.
    **/
    class SHARED ImageFileDelegate : public ReferenceCounted
    {
    public:
        virtual ~ImageFileDelegate ();

        virtual void read (Image & image, int x = 0, int y = 0, int width = 0, int height = 0) = 0;
        virtual void write (const Image & image, int x = 0, int y = 0) = 0;

        virtual String get (const String & name) const = 0;
        virtual void   set (const String & name, const String & value) = 0;
    };

    /**
        Read or write an image stored in a file or stream.
        While both reading and writing
        functions appear here, in general only one will work for any given
        open file and the other will throw exceptions if called.

        Metadata -- There are get() and set() functions for accessing named
        values associated with or stored in the file.  Some of these entries
        may control the behavior of the storage or retrieval process, and others
        may be descriptive information actually stored in the file.  This
        interface makes no distinction between the two; the individual image
        codecs determine the semantics of the entries.  When writing a file,
        you should set all the metadata first before calling the write() method.
        If you specify a metadata entry that is not present or not recognized
        by the codec, it will silently ignore the request.  In addition, the
        get() methods will leave the value parameter unmodified, allowing you
        to set a fallback value before making the get() call.

        Big images -- Some very large rasters are typically broken up into
        blocks.  The read() and write() functions include optional parameters
        that allow you specify a portion of the full raster.  The most efficient
        strategy in this case is to address an integer number of blocks and
        position the image at a block boundary.  However, this is not required.
        There are some reserved metadata entries for specifying or querying
        block structure:
        <ul>
        <li>width -- total horizontal pixels.  Same semantics as Image::width.
        <li>height -- total vertical pixels.  Same semantics as Image::height.
        <li>blockWidth -- horizontal pixels in one block.  If the image is
        stored in "strips", then this will be the same as width.
        <li>blockHeight -- vertical pixels in one tile
        <li>
        </ul>
        These entries will always have the semantics described above, regardless
        of the image codec.  The image codec may also specify other entries
        with the same meanings.

        Coordinates -- Some codecs (namely TIFF) allow a rich set of image
        origins and axis orientations.  These apply only to the display of
        images.  As far as the image raster is concerned, these mean nothing.
        There are simply two axis that start at zero and count up.
        In memory, the raster is stored in row-major order.  To simplify the
        description of this interface, assume the most common arrangement:
        the origin is in the upper-left corner, x increases to the right and
        y increases downward.
    **/
    class SHARED ImageFile
    {
    public:
        ImageFile ();
        ImageFile (const String & fileName, const String & mode = "r", const String & formatName = "");
        ImageFile (std::istream & stream);
        ImageFile (std::ostream & stream, const String & formatName = "bmp");

        /**
         Open a file for reading or writing.  When writing, the file suffix
         indicates the format, but the formatName may optionally override this.
         When reading, the format is determined primarily by the magic string at
         the start of the file. The file suffix provides secondary guidance,
         and the formatName is ignored.
         **/
        void open (const String & fileName, const String & mode = "r", const String & formatName = "");
        void open (std::istream & stream);
        void open (std::ostream & stream, const String & formatName = "bmp");
        void close ();

        /**
            Fill in image with pixels from the file.  This function by default
            retrieves the entire raster.  However, if the codec supports big
            images, is possible to select just a portion of it by using the
            optional parameters.  If the codec does not support big images,
            it will silently ignore the optional parameters and retrieve the
            entire raster.
            \param x The horizontal start position in the raster.
            \param y The vertical start position in the raster.
            \param width The number of horizontal pixels to retrieve.  If 0 (the
            default) then retrieve all the image that lies to the right of the
            start position.
            \param height The number of vertical pixels to retrieve.  If 0 (the
            default) then retrieve all the image that lies below the start
            position.
        **/
        void read (Image & image, int x = 0, int y = 0, int width = 0, int height = 0);

        /**
            Place the contents of image into this file's raster.  If you are
            writing a big raster (one that uses multiple blocks), you should
            specify imageWidth and imageHeight before writing.  The block size
            will be set to the size of the given image.  If you do not specify
            imageWidth and imageHeight, and do not specify the position for the
            given image, then this function will treat the given image as the
            entire raster, which is the common case.
            \param x The horizontal start position in the raster.  This should
            be an integer multiple of image.width.
            \param y The vertical start position in the raster.  This should be
            an integer multiple of image.height.
        **/
        void write (const Image & image, int x = 0, int y = 0);

        String get (const String & name) const;
        void   set (const String & name, const String & value);

        PointerPoly<ImageFileDelegate> delegate;
        double timestamp; ///< When it can be determined from the filesystem, apply it to the image.
    };

    /**
        \todo Add a mutex around the static variable formats.
    **/
    class SHARED ImageFileFormat
    {
    public:
        virtual
        ~ImageFileFormat ();

        virtual ImageFileDelegate * open (std::istream & stream, bool ownStream = false) const = 0;
        virtual ImageFileDelegate * open (std::ostream & stream, bool ownStream = false) const = 0;
        virtual float               isIn (std::istream & stream) const = 0; ///< Determines probability that this format is on the stream.  Always rewinds stream back to where it was when function was called.
        virtual float               handles (const String & formatName) const = 0; ///< Determines probability that this object handles the format with the given human readable name.

        static float find (const String & fileName, ImageFileFormat *& result); ///< Determines what format the stream is in.
        static float find (std::istream & stream, ImageFileFormat *& result); ///< Ditto.  Always returns stream to original position.
        static float findName (const String & formatName, ImageFileFormat *& result); ///< Determines what format to use based on given name.
        static void  getMagic (std::istream & stream, String & magic); ///< Attempts to read magic.size () worth of bytes from stream and return them in magic.  Always returns stream to original position.

        static std::vector<ImageFileFormat*> formats;
    };
    SHARED void freeFormats ();  ///< Dispose of registered ImageFileFormat objects. Called just before program terminates to make memory debuggers like valgrind happy. Otherwise, completely unnecessary.

    class SHARED ImageFileFormatBMP : public ImageFileFormat
    {
    public:
        static void use ();
        virtual ImageFileDelegate * open (std::istream & stream, bool ownStream = false) const;
        virtual ImageFileDelegate * open (std::ostream & stream, bool ownStream = false) const;
        virtual float               isIn (std::istream & stream) const;
        virtual float               handles (const String & formatName) const;
    };


    // Image inlines ------------------------------------------------------------

    template<class T>
    inline void
    Image::attach (const Matrix<T> & A, const PixelFormat & format)
    {
        timestamp    = getTimestamp ();
        this->format = &format;
        width        = A.rows ();  // Because matrices are usually column major.
        height       = A.columns ();
        if (A.strideR_ == 1)
        {
            buffer = new PixelBufferPacked (A.data, A.strideC_ * sizeof (T), sizeof (T), A.offset * sizeof(T));
        }
        else if (A.strideC_ == 1)
        {
            std::swap (width, height);
            buffer = new PixelBufferPacked (A.data, A.strideR_ * sizeof(T), sizeof(T), A.offset * sizeof(T));
        }
        else throw "One dimension of the given matrix must have a stride of 1";
    }

    template<class T>
    inline Matrix<T>
    Image::toMatrix () const
    {
        PixelBufferPacked *pbp = (PixelBufferPacked*) buffer;
        if (!pbp) throw "toMatrix only handles packed buffers";
        return Matrix<T> (pbp->memory, pbp->offset / sizeof(T), width, height, 1, pbp->stride / sizeof(T));
    }

    inline uint32_t
    Image::getRGBA (int x, int y) const
    {
        assert (x >= 0 && x < width && y >= 0 && y < height);
        return format->getRGBA (buffer->pixel (x, y));
    }

    inline void
    Image::getRGBA (int x, int y, float values[]) const
    {
        assert (x >= 0 && x < width && y >= 0 && y < height);
        format->getRGBA (buffer->pixel (x, y), values);
    }

    inline void
    Image::getXYZ (int x, int y, float values[]) const
    {
        assert (x >= 0 && x < width && y >= 0 && y < height);
        format->getXYZ (buffer->pixel (x, y), values);
    }

    inline uint32_t
    Image::getYUV (int x, int y) const
    {
        assert (x >= 0 && x < width && y >= 0 && y < height);
        return format->getYUV (buffer->pixel (x, y));
    }

    inline void
    Image::getHSL (int x, int y, float values[]) const
    {
        assert (x >= 0 && x < width && y >= 0 && y < height);
        format->getHSL (buffer->pixel (x, y), values);
    }

    inline void
    Image::getHSV (int x, int y, float values[]) const
    {
        assert (x >= 0 && x < width && y >= 0 && y < height);
        format->getHSV (buffer->pixel (x, y), values);
    }

    inline uint8_t
    Image::getGray (int x, int y) const
    {
        assert (x >= 0 && x < width && y >= 0 && y < height);
        return format->getGray (buffer->pixel (x, y));
    }

    inline void
    Image::getGray (int x, int y, float & gray) const
    {
        assert (x >= 0 && x < width && y >= 0 && y < height);
        format->getGray (buffer->pixel (x, y), gray);
    }

    inline uint8_t
    Image::getAlpha (int x, int y) const
    {
        assert (x >= 0 && x < width && y >= 0 && y < height);
        return format->getAlpha (buffer->pixel (x, y));
    }

    inline void
    Image::setRGBA (int x, int y, uint32_t rgba)
    {
        assert (x >= 0 && x < width && y >= 0 && y < height);
        format->setRGBA (buffer->pixel (x, y), rgba);
    }

    inline void
    Image::setRGBA (int x, int y, float values[])
    {
        assert (x >= 0 && x < width && y >= 0 && y < height);
        format->setRGBA (buffer->pixel (x, y), values);
    }

    inline void
    Image::setXYZ (int x, int y, float values[])
    {
        assert (x >= 0 && x < width && y >= 0 && y < height);
        format->setXYZ (buffer->pixel (x, y), values);
    }

    inline void
    Image::setYUV (int x, int y, uint32_t yuv)
    {
        assert (x >= 0 && x < width && y >= 0 && y < height);
        format->setYUV (buffer->pixel (x, y), yuv);
    }

    inline void
    Image::setHSL (int x, int y, float values[])
    {
        assert (x >= 0 && x < width && y >= 0 && y < height);
        format->setHSL (buffer->pixel (x, y), values);
    }

    inline void
    Image::setHSV (int x, int y, float values[])
    {
        assert (x >= 0 && x < width && y >= 0 && y < height);
        format->setHSV (buffer->pixel (x, y), values);
    }

    inline void
    Image::setGray (int x, int y, uint8_t gray)
    {
        assert (x >= 0 && x < width && y >= 0 && y < height);
        format->setGray (buffer->pixel (x, y), gray);
    }

    inline void
    Image::setGray (int x, int y, float gray)
    {
        assert (x >= 0 && x < width && y >= 0 && y < height);
        format->setGray (buffer->pixel (x, y), gray);
    }

    inline void
    Image::setAlpha (int x, int y, uint8_t alpha)
    {
        assert (x >= 0 && x < width && y >= 0 && y < height);
        format->setAlpha (buffer->pixel (x, y), alpha);
    }

    inline void
    Image::blend (int x, int y, uint32_t rgba)
    {
        assert (x >= 0 && x < width && y >= 0 && y < height);
        format->blend (buffer->pixel (x, y), rgba);
    }

    inline void
    Image::blend (int x, int y, float values[])
    {
        assert (x >= 0 && x < width && y >= 0 && y < height);
        format->blend (buffer->pixel (x, y), values);
    }

    inline static void
    alphaBlend (const float from[], float to[])
    {
        float fromA = from[3];
        if (! fromA) return;  // If source alpha is 0, then no change to destination.
        float toA  = to[3] * (1 - fromA);
        float newA = fromA + toA;
        fromA /= newA;
        toA   /= newA;
        to[0] = fromA * from[0] + toA * to[0];
        to[1] = fromA * from[1] + toA * to[1];
        to[2] = fromA * from[2] + toA * to[2];
        to[3] = newA;
    }

    /**
        Works on machine word format rgba, just like get/setRGBA() on image.
        On little-endian systems, memory format would be ABGR.
        On big-endian, memory format would be RGBA.
    **/
    inline static void
    alphaBlend (const uint32_t & from, uint32_t & to)
    {
        // Convert everything to fixed-point, with decimal at bit 15.
        // This has the advantage of keeping everything within a 32-bit word.
        uint32_t fromA = ((from & 0xFF) << 15) / 0xFF;
        if (! fromA) return;
        uint32_t toA   = ((to   & 0xFF) << 15) / 0xFF;
        toA = toA * (0x8000 - fromA) >> 15;
        uint32_t newA = fromA + toA;
        fromA = (fromA << 15) / newA;
        toA   = (toA   << 15) / newA;
        uint32_t r = ((from & 0xFF000000) >> 24) * fromA + ((to & 0xFF000000) >> 24) * toA + 0x4000;
        uint32_t g = ((from &   0xFF0000) >> 16) * fromA + ((to &   0xFF0000) >> 16) * toA + 0x4000;
        uint32_t b = ((from &     0xFF00) >>  8) * fromA + ((to &     0xFF00) >>  8) * toA + 0x4000;
        to = (r & 0x7F8000) << 9 | (g & 0x7F8000) << 1 | (b & 0x7F8000) >> 7 | newA * 0xFF >> 15;
    }

    /**
        Version of alphaBlend which assumes opposite endian.
        On little-endian systems, memory format would be RGBA.
        On big-endian, memory format would be ABGR.
    **/
    inline static void
    alphaBlendOE (const uint32_t & from, uint32_t & to)
    {
        // Convert everything to fixed-point, with decimal at bit 15.
        // This has the advantage of keeping everything within a 32-bit word.
        uint32_t fromA = ((from & 0xFF000000) >> 9) / 0xFF;
        if (! fromA) return;
        uint32_t toA   = ((to   & 0xFF000000) >> 9) / 0xFF;
        toA = toA * (0x8000 - fromA) >> 15;
        uint32_t newA = fromA + toA;
        fromA = (fromA << 15) / newA;
        toA   = (toA   << 15) / newA;
        uint32_t r =  (from &     0xFF)        * fromA +  (to &     0xFF)        * toA + 0x4000;
        uint32_t g = ((from &   0xFF00) >>  8) * fromA + ((to &   0xFF00) >>  8) * toA + 0x4000;
        uint32_t b = ((from & 0xFF0000) >> 16) * fromA + ((to & 0xFF0000) >> 16) * toA + 0x4000;
        to = (r & 0x7F8000) >> 15 | (g & 0x7F8000) >> 7 | (b & 0x7F8000) << 1 | (newA * 0xFF & 0x7F8000) << 9;
    }
}

#ifdef HAVE_JNI

// These should exactly match the BufferedImage types in Java.
enum
{
    TYPE_CUSTOM,
    TYPE_INT_RGB,
    TYPE_INT_ARGB,
    TYPE_INT_ARGB_PRE,
    TYPE_INT_BGR,
    TYPE_3BYTE_BGR,
    TYPE_4BYTE_ABGR,
    TYPE_4BYTE_ABGR_PRE,
    TYPE_USHORT_565_RGB,
    TYPE_USHORT_555_RGB,
    TYPE_BYTE_GRAY,
    TYPE_USHORT_GRAY,
    TYPE_BYTE_BINARY,
    TYPE_BYTE_INDEXED
};

struct PixelFormat2BufferedImage
{
    n2a::PixelFormat * pf;
    int                bi;
    int                size;  // number of bytes per pixel
};

extern PixelFormat2BufferedImage pixelFormat2BufferedImageMap[];

#endif

#endif
