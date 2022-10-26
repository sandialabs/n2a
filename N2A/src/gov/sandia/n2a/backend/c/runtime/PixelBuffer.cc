/*
Copyright 2009-2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


#include "math.h"
#include "image.h"

using namespace n2a;
using namespace std;


// Utility functions ----------------------------------------------------------

/**
    @param oldStride Curent width of one row in bytes (not pixels).
    @param newStride Desired width of one row in bytes.
**/
void
n2a::reshapeBuffer (Pointer & memory, int oldStride, int newStride, int newHeight, int pad)
{
    int oldHeight = memory.size ();
    if (oldHeight <= 0)
    {
        oldHeight = 0;
    }
    else if (oldStride > 0)
    {
        oldHeight /= oldStride;
    }
    int copyWidth  = min (newStride, oldStride);
    int copyHeight = min (newHeight, oldHeight);

    if (newStride == oldStride)
    {
        if (newHeight > oldHeight)
        {
            Pointer temp (memory);
            memory.detach ();
            memory.grow (newStride * newHeight + pad);
            int count = newStride * copyHeight;
            memcpy ((char *) memory, (char *) temp, count);
            assert (count >= 0  &&  count < memory.size ());
            memset ((char *) memory + count, 0, memory.size () - count);
        }
    }
    else  // different strides
    {
        Pointer temp (memory);
        memory.detach ();
        memory.grow (newStride * newHeight + pad);
        memory.clear ();

        unsigned char * target = (unsigned char *) memory;
        unsigned char * source = (unsigned char *) temp;
        for (int y = 0; y < copyHeight; y++)
        {
            memcpy (target, source, copyWidth);
            target += newStride;
            source += oldStride;
        }
    }
}


// class PixelBuffer ----------------------------------------------------------

PixelBuffer::~PixelBuffer ()
{
}

bool
PixelBuffer::operator == (const PixelBuffer & that) const
{
    return    typeid (*this) == typeid (that)
           && planes == that.planes;
}


// class PixelBufferPacked ----------------------------------------------------

PixelBufferPacked::PixelBufferPacked (int depth)
{
    planes      = 1;
    offset      = 0;
    stride      = 0;
    this->depth = depth;
}

PixelBufferPacked::PixelBufferPacked (int stride, int height, int depth)
:   memory (stride * height)
{
    planes       = 1;
    offset       = 0;
    this->stride = stride;
    this->depth  = depth;
}

PixelBufferPacked::PixelBufferPacked (void * buffer, int stride, int height, int depth)
{
    planes       = 1;
    offset       = 0;
    this->stride = stride;
    this->depth  = depth;
    this->memory.attach (buffer, stride * height);
}

PixelBufferPacked::PixelBufferPacked (const Pointer & buffer, int stride, int depth, int offset)
{
    planes       = 1;
    this->offset = offset;
    this->stride = stride;
    this->depth  = depth;
    this->memory = buffer;
}

PixelBufferPacked::~PixelBufferPacked ()
{
}

void *
PixelBufferPacked::pixel (int x, int y)
{
    return & ((char *) memory)[offset + y * stride + x * depth];
}

void
PixelBufferPacked::resize (int width, int height, const PixelFormat & format, bool preserve)
{
    if (width <= 0  ||  height <= 0)
    {
        offset = 0;
        stride = 0;
        depth  = (int) format.depth;
        memory.detach ();
        return;
    }

    if (!preserve  ||  format.depth != depth  ||  offset)
    {
        offset = 0;
        depth  = (int) format.depth;
        stride = width * depth;
        memory.grow (stride * height + (depth == 3 ? 1 : 0));
        return;
    }

    reshapeBuffer (memory, stride, width * depth, height, depth == 3 ? 1 : 0);
    stride = width * depth;
}

PixelBuffer *
PixelBufferPacked::duplicate () const
{
    PixelBufferPacked *result = new PixelBufferPacked (depth);
    ptrdiff_t size = memory.size () - offset;
    if (size > 0) result->memory.copyFrom (((char*) memory) + offset, size);
    result->offset = 0;
    result->stride = stride;
    return result;
}

void
PixelBufferPacked::clear ()
{
    memory.clear ();
}

bool
PixelBufferPacked::operator == (const PixelBuffer & that) const
{
    const PixelBufferPacked * p = dynamic_cast<const PixelBufferPacked *> (&that);
    // If p exists, then implicitly the number of planes is 1.
    return    p
           && offset == p->offset
           && stride == p->stride
           && depth  == p->depth
           && memory == p->memory;
}

void
PixelBufferPacked::copyFrom (void * buffer, int stride, int height, int depth)
{
    this->memory.copyFrom (buffer, stride * height);
    offset       = 0;
    this->stride = stride;
    this->depth  = depth;
}

void *
PixelBufferPacked::base () const
{
    return ((char *) memory) + offset;
}


// class PixelBufferPlanar ----------------------------------------------------

PixelBufferPlanar::PixelBufferPlanar ()
{
    planes   = 3;
    stride0  = 0;
    stride12 = 0;
    ratioH   = 1;
    ratioV   = 1;
}

PixelBufferPlanar::PixelBufferPlanar (int stride, int height, int ratioH, int ratioV)
{
    planes       = 3;
    stride0      = stride;
    stride12     = stride / ratioH;
    this->ratioH = ratioH;
    this->ratioV = ratioV;

    plane0.grow (stride0  * height);
    plane1.grow (stride12 * height);
    plane2.grow (stride12 * height);
}

/**
    Attach to an FFMPEG picture.
**/
PixelBufferPlanar::PixelBufferPlanar (void * buffer0, void * buffer1, void * buffer2, int stride0, int stride12, int height, int ratioH, int ratioV)
{
    planes         = 3;
    this->stride0  = stride0;
    this->stride12 = stride12;
    this->ratioH   = ratioH;
    this->ratioV   = ratioV;

    plane0.attach (buffer0, stride0  * height);
    plane1.attach (buffer1, stride12 * height / ratioV);
    plane2.attach (buffer2, stride12 * height / ratioV);
}

PixelBufferPlanar::~PixelBufferPlanar ()
{
}

void *
PixelBufferPlanar::pixel (int x, int y)
{
    int x12 = x / ratioH;
    int y12 = y / ratioV;

    pixelArray[0] = ((char *) plane0) + (y   * stride0  + x);
    pixelArray[1] = ((char *) plane1) + (y12 * stride12 + x12);
    pixelArray[2] = ((char *) plane2) + (y12 * stride12 + x12);

    return pixelArray;
}

void
PixelBufferPlanar::resize (int width, int height, const PixelFormat & format, bool preserve)
{
    if (width <= 0  ||  height <= 0)
    {
        plane0.detach ();
        plane1.detach ();
        plane2.detach ();
        return;
    }

    const PixelFormatYUV * f = dynamic_cast<const PixelFormatYUV*> (&format);
    if (f)
    {
        ratioH = f->ratioH;
        ratioV = f->ratioV;
    }
    else
    {
        ratioH = 1;
        ratioV = 1;
    }

    if (preserve)
    {
        reshapeBuffer (plane0, stride0, width, height);
        reshapeBuffer (plane1, stride12, width / ratioH, height / ratioV);
        reshapeBuffer (plane2, stride12, width / ratioH, height / ratioV);
        stride0 = width;
        stride12 = width / ratioH;
    }
    else
    {
        stride0  = width;
        stride12 = width / ratioH;
        plane0.grow (stride0  * height);
        plane1.grow (stride12 * height);
        plane2.grow (stride12 * height);
    }
}

PixelBuffer *
PixelBufferPlanar::duplicate () const
{
    PixelBufferPlanar * result = new PixelBufferPlanar ();
    result->ratioH   = ratioH;
    result->ratioV   = ratioV;
    result->stride0  = stride0;
    result->stride12 = stride12;

    result->plane0.copyFrom (plane0);
    result->plane1.copyFrom (plane1);
    result->plane2.copyFrom (plane2);

    return result;
}

void
PixelBufferPlanar::clear ()
{
  plane0.clear ();
  plane1.clear ();
  plane2.clear ();
}

bool
PixelBufferPlanar::operator == (const PixelBuffer & that) const
{
    const PixelBufferPlanar * p = dynamic_cast<const PixelBufferPlanar *> (&that);
    return    p
           && ratioH   == p->ratioH
           && ratioV   == p->ratioV
           && stride0  == p->stride0
           && stride12 == p->stride12
           && plane0   == p->plane0
           && plane1   == p->plane1
           && plane2   == p->plane2;
}


// class PixelBufferGroups ----------------------------------------------------

PixelBufferGroups::PixelBufferGroups (int pixels, int bytes)
{
    planes       = -1;
    this->pixels = pixels;
    this->bytes  = bytes;
    stride       = 0;
}

PixelBufferGroups::PixelBufferGroups (int stride, int height, int pixels, int bytes)
{
    planes       = -1;
    this->pixels = pixels;
    this->bytes  = bytes;
    this->stride = stride;
    memory.grow (stride * height);
}

PixelBufferGroups::PixelBufferGroups (void * buffer, int stride, int height, int pixels, int bytes)
{
    planes       = -1;
    this->pixels = pixels;
    this->bytes  = bytes;
    this->stride = stride;
    memory.attach (buffer, stride * height);
}

PixelBufferGroups::~PixelBufferGroups ()
{
}

void *
PixelBufferGroups::pixel (int x, int y)
{
    pixelData.address = (unsigned char *) memory + (y * stride + (x / pixels) * bytes);
    pixelData.index   = x % pixels;
    return & pixelData;
}

void
PixelBufferGroups::resize (int width, int height, const PixelFormat & format, bool preserve)
{
    if (width <= 0  ||  height <= 0)
    {
        stride = 0;
        memory.detach ();
        return;
    }

    const Macropixel * f = dynamic_cast<const Macropixel *> (&format);
    if (!f) throw "Need PixelFormat that specifies macropixel parameters.";
    int newStride = (int) ceil ((float) width / f->pixels) * f->bytes;  // Always allocate a stride that can contain a whole number of groups and also the full width.  It is permissable to use part of a group for the last few pixels of the line, provided there is storage for the full group.

    if (! preserve  ||  f->pixels != pixels  ||  f->bytes != bytes)
    {
        pixels = f->pixels;
        bytes  = f->bytes;
        stride = newStride;
        memory.grow (newStride * height);
        return;
    }

    reshapeBuffer (memory, stride, newStride, height);
    stride = newStride;
}

PixelBuffer *
PixelBufferGroups::duplicate () const
{
    PixelBufferGroups * result = new PixelBufferGroups (pixels, bytes);
    result->memory.copyFrom (memory);
    result->stride = stride;
    return result;
}

void
PixelBufferGroups::clear ()
{
    memory.clear ();
}

bool
PixelBufferGroups::operator == (const PixelBuffer & that) const
{
    const PixelBufferGroups * p = dynamic_cast<const PixelBufferGroups *> (&that);
    return    p
           && stride == p->stride
           && pixels == p->pixels
           && bytes  == p->bytes
           && memory == p->memory;
}
