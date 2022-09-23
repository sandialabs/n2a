/*
Author: Fred Rothganger
Copyright (c) 2001-2004 Dept. of Computer Science and Beckman Institute,
                        Univ. of Illinois.  All rights reserved.
Distributed under the UIUC/NCSA Open Source License.  See the file LICENSE
for details.


Copyright 2005-2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


#include "image.h"
#include "Matrix.tcc"

#include <assert.h>
#include <fstream>
#include <chrono>

// Include for tracing
//#include <iostream>

using namespace n2a;
using namespace std;


// utility function -----------------------------------------------------------

double
getTimestamp ()
{
    return std::chrono::time_point_cast<std::chrono::duration<double>> (std::chrono::system_clock::now ()).time_since_epoch ().count ();
}


// class Image ----------------------------------------------------------------

Image::Image ()
{
    timestamp = getTimestamp ();
    format    = &GrayChar;
    width     = 0;
    height    = 0;
}

Image::Image (const PixelFormat & format)
{
    timestamp    = getTimestamp ();
    this->format = &format;
    width        = 0;
    height       = 0;
}

Image::Image (int width, int height)
{
    timestamp = getTimestamp ();
    format    = &GrayChar;
    resize (width, height);
}

Image::Image (int width, int height, const PixelFormat & format)
{
    timestamp    = getTimestamp ();
    this->format = &format;
    resize (width, height);
}

Image::Image (const Image & that)
{
    buffer    = that.buffer;
    format    = that.format;
    timestamp = that.timestamp;
    width     = that.width;
    height    = that.height;
}

Image::Image (void * block, int width, int height, const PixelFormat & format)
{
    attach (block, width, height, format);
}

Image::Image (const MatrixAbstract<float> & A)
{
    attach (Matrix<float> (A), GrayFloat);
}

Image::Image (const MatrixAbstract<double> & A)
{
    attach (Matrix<double> (A), GrayDouble);
}

Image::Image (const String & fileName)
{
    read (fileName);
}

void
Image::read (const String & fileName)
{
    ImageFile file (fileName, "r");
    file.read (*this);
}

void
Image::read (istream & stream)
{
    ImageFile file (stream);
    file.read (*this);
    timestamp = getTimestamp ();
}

void
Image::write (const String & fileName, const String & formatName) const
{
    ImageFile file (fileName, "w", formatName);
    file.write (*this);
}

void
Image::write (ostream & stream, const String & formatName) const
{
    ImageFile file (stream, formatName);
    file.write (*this);
}

void
Image::copyFrom (const Image & that)
{
    if (that.buffer == 0) buffer = 0;
    else                  buffer = that.buffer->duplicate ();

    format    = that.format;
    width     = that.width;
    height    = that.height;
    timestamp = that.timestamp;
}

void
Image::copyFrom (void * block, int width, int height, const PixelFormat & format)
{
    timestamp    = getTimestamp ();
    this->width  = max (0, width);
    this->height = max (0, height);
    buffer       = format.attach (block, this->width, this->height, true);
    this->format = &format;
}

void
Image::attach (void * block, int width, int height, const PixelFormat & format)
{
    timestamp    = getTimestamp ();
    this->width  = max (0, width);
    this->height = max (0, height);
    buffer       = format.attach (block, this->width, this->height);
    this->format = &format;
}

void
Image::attach (const Matrix<float> & A)
{
    attach (A, GrayFloat);
}

void
Image::attach (const Matrix<double> & A)
{
    attach (A, GrayDouble);
}

void
Image::detach ()
{
    buffer = 0;
}

/**
    Changes image to new size.
    @param preserve If true, then any pixels that are still visible are
    aligned correctly and any newly exposed pixels are set to black.
    If false, then the content of the buffer is undefined.
**/
void
Image::resize (int width, int height, bool preserve)
{
    width  = max (width, 0);
    height = max (height, 0);
    if (! buffer.memory  ||  buffer->planes != format->planes)
    {
        buffer = format->buffer ();
    }
    buffer->resize (width, height, *format, preserve);
    this->width  = width;
    this->height = height;
}

void
Image::clear (unsigned int rgba)
{
    if (rgba)
    {
        for (int y = 0; y < height; y++)
        {
            for (int x = 0; x < width; x++)
            {
                format->setRGBA (buffer->pixel (x, y), rgba);
            }
        }
    }
    else
    {
        buffer->clear ();
    }
}
