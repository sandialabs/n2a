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


#include "video.h"

using namespace n2a;
using namespace std;


// class VideoIn --------------------------------------------------------------

VideoIn::VideoIn (const String & fileName)
{
    file = 0;
    open (fileName);
}

VideoIn::~VideoIn ()
{
    close ();
}

void
VideoIn::open (const String & fileName)
{
    close ();
    VideoFileFormat * format = VideoFileFormat::find (fileName);
    if (format) file = format->openInput (fileName);
}

void
VideoIn::close ()
{
    delete file;
    file = 0;
}

void
VideoIn::pause ()
{
    if (file) file->pause ();
}

void
VideoIn::seekFrame (int frame)
{
    if (file) file->seekFrame (frame);
}

void
VideoIn::seekTime (double timestamp)
{
    if (file) file->seekTime (timestamp);
}

VideoIn &
VideoIn::operator >> (Image & image)
{
    if (file) file->readNext (image);
    return *this;
}

bool
VideoIn::good () const
{
    return file  &&  file->good ();
}

String
VideoIn::get (const String & name) const
{
    if (file) return file->get (name);
    return "";
}

void
VideoIn::set (const String & name, const String & value)
{
    if (file) file->set (name, value);
}


// class VideoOut -------------------------------------------------------------

VideoOut::VideoOut (const String & fileName, const String & formatName, const String & codecName)
{
    file = 0;
    VideoFileFormat * format = VideoFileFormat::find (formatName, codecName);
    if (format) file = format->openOutput (fileName, formatName, codecName);
}

VideoOut::~VideoOut ()
{
    delete file;
}

VideoOut &
VideoOut::operator << (const Image & image)
{
    if (file) file->writeNext (image);
    return *this;
}

bool
VideoOut::good () const
{
    return file  &&  file->good ();
}

String
VideoOut::get (const String & name) const
{
    if (file) return file->get (name);
    return "";
}

void
VideoOut::set (const String & name, const String & value)
{
    if (file) file->set (name, value);
}


// class VideoInFile ----------------------------------------------------------

VideoInFile::~VideoInFile ()
{
}


// class VideoOutFile ---------------------------------------------------------

VideoOutFile::~VideoOutFile ()
{
}


// class VideoFileFormat ------------------------------------------------------

vector<VideoFileFormat *> VideoFileFormat::formats;

VideoFileFormat::~VideoFileFormat ()
{
    vector<VideoFileFormat *>::iterator i;
    for (i = formats.begin (); i < formats.end (); i++)
    {
        if (*i == this)
        {
            formats.erase (i);
            break;
        }
    }
}

VideoFileFormat *
VideoFileFormat::find (const String & fileName)
{
    VideoFileFormat * result = NULL;
    float bestProbability = 0;
    vector<VideoFileFormat *>::reverse_iterator i;
    for (i = formats.rbegin (); i != formats.rend (); i++)
    {
        float p = (*i)->isIn (fileName);
        if (p > bestProbability)
        {
            result = *i;
            bestProbability = p;
        }
    }

    return result;
}

VideoFileFormat *
VideoFileFormat::find (const String & formatName, const String & codecName)
{
    VideoFileFormat * result = NULL;
    float bestProbability = 0;
    vector<VideoFileFormat *>::reverse_iterator i;
    for (i = formats.rbegin (); i != formats.rend (); i++)
    {
        float p = (*i)->handles (formatName, codecName);
        if (p > bestProbability)
        {
            result = *i;
            bestProbability = p;
        }
    }

    return result;
}
