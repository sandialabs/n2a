/*
Author: Fred Rothganger
Copyright (c) 2001-2004 Dept. of Computer Science and Beckman Institute,
                        Univ. of Illinois.  All rights reserved.
Distributed under the UIUC/NCSA Open Source License.  See the file LICENSE
for details.


Copyright 2005-2025 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


// Force stdint.h under Fedora Core (and maybe others) to define the INT64_C
// macro for use with FFMPEG constants (namely AV_NOPTS_VALUE).  This
// definition must occur before stdint.h is otherwise included.
#define __STDC_CONSTANT_MACROS
#define __STDC_LIMIT_MACROS

#include "mymath.h"
#include "video.h"

#include <time.h>
#ifdef HAVE_JNI
#  include <jni.h>
#endif

extern "C"
{
# include <libavcodec/avcodec.h>
# include <libavformat/avformat.h>
}

using namespace n2a;
using namespace std;


// class VideoInFileFFMPEG ----------------------------------------------------

class VideoInFileFFMPEG : public VideoInFile
{
public:
    VideoInFileFFMPEG (const String & fileName);
    virtual ~VideoInFileFFMPEG ();

    virtual void pause ();
    virtual void seekFrame (int frame);
    virtual void seekTime (double timestamp);
    virtual void readNext (Image & image);
    virtual bool good () const;

    virtual String get (const String & name) const;
    virtual void   set (const String & name, const String & value);

protected:
    void open (const String & fileName);
    void close ();
    void readNext (Image * image);  ///< same as readNext() above, except if image is null, then don't do extraction step
    void extractImage (Image & image);

    AVFormatContext * fc;
    AVStream *        stream;
    const AVCodec *   codec;
    AVCodecContext *  cc;
    AVPacket *        packet;        ///< Ensure that if nextImage() attaches image to packet, the memory won't be freed before next read.
    AVFrame *         frame;
    int               state;         ///< state == 0 means good; anything else means we can't read more frames
    bool              gotPicture;    ///< indicates that the image in "frame" should be return on next call to readNext().
    bool              timestampMode; ///< Indicates that image.timestamp should be frame number rather than presentation time.
    int64_t           expectedSkew;  ///< How much before a given target time to read in order to get a keyframe, in stream units.
    bool              hasTimestamps; ///< Indicates that container has seekable time values.
    bool              hasKeyframes;  ///< Indicates that container has valid keyframe flags.
    bool              seekLinear;    ///< Indicates that the file only supports linear seeking, not random seeking.  Generally due to lack of timestamps in stream.
    int64_t           nextPTS;       ///< DTS of most recent packet pushed into decoder.  In MPEG, this will be the PTS of the next frame to come out of decoder, which occurs next time a packet is pushed in.
    double            startTime;     ///< Best estimate of timestamp on first image in video.
    bool              interleaveRTP; ///< Forces RTP interleaving over a TCP connection. This is the only way to guarantee 100% packet delivery.
    bool              paused;        ///< If this is a network stream, indicates that streaming is paused.
};

VideoInFileFFMPEG::VideoInFileFFMPEG (const String & fileName)
{
    fc            = 0;
    stream        = 0;
    codec         = 0;
    cc            = 0;
    packet        = av_packet_alloc ();
    frame         = av_frame_alloc ();
    timestampMode = false;
    interleaveRTP = true;
    paused        = true;

    open (fileName);
}

VideoInFileFFMPEG::~VideoInFileFFMPEG ()
{
    close ();

    av_frame_free (&frame);
    av_packet_free (&packet);
}

void
VideoInFileFFMPEG::open (const String & fileName)
{
    close ();

    gotPicture = false;

    AVDictionary * options = 0;
    if (interleaveRTP) av_dict_set (&options, "rtsp_transport", "tcp", 0);  // It doesn't hurt to set this option, even if we are not doing RTP.
    state = avformat_open_input (&fc, fileName.c_str (), 0, &options);
    av_dict_free (&options);
    if (state < 0) return;

    state = avformat_find_stream_info (fc, 0);
    if (state < 0) return;
    paused = false;  // Finding stream info requires streaming, so assume it is on.

    for (int i = 0; i < fc->nb_streams; i++)
    {
        fc->streams[i]->discard = AVDISCARD_ALL;
        if (stream == 0  &&  fc->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_VIDEO)
        {
            stream = fc->streams[i];
            fc->streams[i]->discard = AVDISCARD_DEFAULT;
        }
    }
    if (! stream)
    {
        state = -10;
        return;
    }

    codec = avcodec_find_decoder (stream->codecpar->codec_id);
    if (! codec)
    {
        state = -11;
        return;
    }

    cc = avcodec_alloc_context3 (codec);
    if (! cc)
    {
        state = -12;
        return;
    }

    if (codec->capabilities & AV_CODEC_CAP_TRUNCATED)
    {
        cc->flags |= AV_CODEC_FLAG_TRUNCATED;
    }

    state = avcodec_open2 (cc, codec, 0);
    if (state < 0) return;

    hasTimestamps = true;
    hasKeyframes  = true;
    seekLinear    = false;
    expectedSkew  = 0;
    nextPTS       = 0;

    startTime = 0;
    if (stream->start_time != AV_NOPTS_VALUE)
    {
        startTime = (double) stream->start_time * stream->time_base.num / stream->time_base.den;
    }
    else if (fc->start_time != AV_NOPTS_VALUE)
    {
        startTime = (double) fc->start_time / AV_TIME_BASE;
    }
    // The start time reported by ffmpeg is for the first DTS in the stream,
    // but the PTS of the first frame out of the decoder is often later than that.
    // Not sure how to fix this, except to change startTime after the fact when
    // the first frame arrives. At a minium, we assume firt PTS is no less than 0.
    if (startTime < 0) startTime = 0;
}

void
VideoInFileFFMPEG::close ()
{
    av_frame_unref (frame);
    av_packet_unref (packet);
    avcodec_free_context (&cc);  // These functions guard against null.
    codec = 0;
    stream = 0;
    avformat_close_input (&fc);

    state = -13;
}

void
VideoInFileFFMPEG::pause ()
{
    if (fc) av_read_pause (fc);
    paused = true;
}

/**
   Assumes frame rate is constant throughout entire video, or at least
   from the beginning to the requested frame.
**/
void
VideoInFileFFMPEG::seekFrame (int frameNumber)
{
    if (state  ||  ! stream) return;

    if (seekLinear)
    {
        if (frameNumber < cc->frame_number)
        {
            // Reset to start of file
            // TODO: if AVFMT_NO_BYTE_SEEK, then reopen file instead.
            state = av_seek_frame (fc, stream->index, 0, AVSEEK_FLAG_BYTE);
            if (state < 0) return;
            avcodec_flush_buffers (cc);
            av_packet_unref (packet);
            cc->frame_number = 0;
        }

        // Read forward until finding the exact frame requested.
        while (cc->frame_number < frameNumber)
        {
            readNext (0);  // decode frame, but don't fill in image
            if (! gotPicture) return;
            gotPicture = false;  // pretend that we consumed the image
        }
    }
    else
    {
        seekTime (startTime + (double) frameNumber * stream->r_frame_rate.den / stream->r_frame_rate.num);  // = frameNumber / r_frame_rate
    }
}

/**
   Assumes timestamps are monotonic in video file.
**/
void
VideoInFileFFMPEG::seekTime (double timestamp)
{
    if (state  ||  ! stream) return;

    timestamp = max (timestamp, startTime);

    if (seekLinear)
    {
        // The expression below uses floor() because timestamp refers to the
        // frame visible at the time, rather than the nearest frame boundary.
        seekFrame ((int) floor ((timestamp - startTime) * stream->r_frame_rate.num / stream->r_frame_rate.den + 1e-6));
        return;
    }

    // stream->time_base is the duration of one stream unit in seconds.
    // targetPTS uses ceil() to force rounding errors in the direction of the
    // next frame.  This produces more intuitive results.
    int64_t targetPTS   = (int64_t) ceil   (timestamp * stream->time_base.den / stream->time_base.num);
    int64_t startPTS    = (int64_t) roundp (startTime * stream->time_base.den / stream->time_base.num);
    int64_t horizonPTS  = targetPTS - (int64_t) roundp ((double) stream->time_base.den / stream->time_base.num);  // willing to sift forward up to 1 second before using seek
    int64_t framePeriod = (int64_t) roundp ((double) stream->r_frame_rate.den / stream->r_frame_rate.num * stream->time_base.den / stream->time_base.num);

    bool startOfFile = false;  // Indicates that we have already sought to/before start of file. Prevents infinite loops.
    while (targetPTS < frame->pts  ||  nextPTS <= targetPTS)  // targetPTS is not in [frame.pts, nextPTS), ie: not in the current frame. This relies on nextPTS always being set to the start of the next image, or to AV_NOPTS_VALUE if end of video.
    {
        if (targetPTS < nextPTS  ||  nextPTS < horizonPTS)  // Must move backwards or a long distance, so seek is needed.
        {
            // Use seek to position at or before the frame
            // Most format seek to DTS, but some seek to PTS. Those are supposed to set fc.flags with AVFMT_SEEK_TO_PTS.
            // The goal of expectedSkew is to seek far enough before the target PTS in either case.
            // This also includes enough to catch a key frame before the target so it can be properly decoded.
            // TODO: consider using av_seek_file() with min=INT64_MIN and max=targetPTS. Still need to use expectedSkew.
            int64_t seekDTS = targetPTS - expectedSkew;
            if (seekDTS < startPTS)  // An improper comparison between DTS and PTS, but we can make up for this by increasing expectedSkew.
            {
                // TODO: if AVFMT_NO_BYTE_SEEK, then reopen file instead.
                state = av_seek_frame (fc, stream->index, 0, AVSEEK_FLAG_BYTE);
                startOfFile = true;
            }
            else
            {
                int flags = 0;
                if (packet->size  &&  packet->dts > seekDTS) flags = AVSEEK_FLAG_BACKWARD;
                state = av_seek_frame (fc, stream->index, seekDTS, flags);
            }
            if (state < 0)
            {
                // Assume the failure is due to some form of unseekability,
                // so fall back to linear seek.
                state = 0;
                seekLinear = true;
                seekTime (timestamp);
                return;
            }

            // Read the next key frame. It is possible for a seek to find something
            // other than a key frame. For example, if an mpeg has timestamps on
            // packets other than I frames.
            avcodec_flush_buffers (cc);
            if (paused)
            {
                av_read_play (fc);
                paused = false;
            }
            int nonkey = 0;
            while (hasKeyframes)
            {
                av_packet_unref (packet);
                state = av_read_frame (fc, packet);
                if (state == AVERROR_EOF) break;
                if (state < 0) return;
                if (packet->stream_index != stream->index) continue;
                if (packet->flags & AV_PKT_FLAG_KEY) break;  // found a key frame in our selected stream
                if (++nonkey > 1000) hasKeyframes = false;  // The arbitrary limit of 1000 is used by ffmpeg itself in seek_frame_generic().
            }
            if (state == AVERROR_EOF)
            {
                if (startOfFile) return;  // Can't seek any earlier. return with active error condition.
                expectedSkew += framePeriod;
                continue;  // Error will get cleared during next seek.
            }

            // Since we already read the packet, we need to send it to the codec.
            state = avcodec_send_packet (cc, packet);  // This should not produce AVERROR(EAGAIN) since we just flushed the codec.
            if (state) return;
        }

        // Sift forward until the current frame contains the requested time.
        do
        {
            gotPicture = false;
            readNext (0);
            if (! gotPicture) return;
        }
        while (nextPTS <= targetPTS);

        // Adjust skew if necessary
        if (targetPTS < frame->pts)
        {
            // We overshot. Need to target further ahead.
            if (startOfFile) break;  // It's not possible to reach any further ahead. This can happen if start_time does not match actual first frame returned by codec.
            if (expectedSkew < frame->pts - targetPTS)
            {
                expectedSkew = frame->pts - targetPTS;
            }
            else
            {
                expectedSkew += framePeriod;
            }
        }
    }
    gotPicture = true;  // Hack to reactivate a frame if we already have it in hand.

    // Determine the number of frame that seek obtained
    // Use round() because PTS should be exactly on some frame's timestamp,
    // and we want to compensate for numerical error.
    // Add 1 to be consistent with normal frame_number semantics.  IE: we have
    // already retrieved the frame, so frame_number should point to next
    // frame.
    cc->frame_number = 1 + (int) roundp
    (
        ((double) (frame->pts - startPTS) * stream->time_base.num / stream->time_base.den)
        * stream->r_frame_rate.num / stream->r_frame_rate.den
    );
}

void
VideoInFileFFMPEG::readNext (Image & image)
{
    readNext (&image);
}

void
VideoInFileFFMPEG::readNext (Image * image)
{
    if (state) return;  // Don't attempt to read when we are in an error state.

    if (paused) av_read_play (fc);
    paused = false;

    while (! gotPicture)
    {
        state = avcodec_receive_frame (cc, frame);
        if (state == AVERROR(EAGAIN))  // need another packet
        {
            do
            {
                av_packet_unref (packet);
                state = av_read_frame (fc, packet);
            }
            while (state == AVERROR(EAGAIN)  ||  state == 0  &&  packet->stream_index != stream->index);
            if (state) state = avcodec_send_packet (cc, 0);  // Start draining last frames
            else       state = avcodec_send_packet (cc, packet);
            if (state == 0) continue;  // Try again to get a frame.
        }
        if (state < 0) return;  // Either we have drained the last frame or there is an error.
        gotPicture = true; // At this point, frame is good

        if (frame->pts == AV_NOPTS_VALUE)
        {
            frame->pts = frame->best_effort_timestamp;  // Less accurate, but more available.
        }

        // TODO: change pkt_duration to duration once this is commonly available in distribution packages (some time in 2023).
        if (frame->pkt_duration)
        {
            nextPTS = frame->pts + frame->pkt_duration;
        }
        else
        {
            nextPTS = frame->pts + (int64_t) roundp ((double) stream->r_frame_rate.den / stream->r_frame_rate.num * stream->time_base.den / stream->time_base.num);  // TODO: use AV rational arithmetic instead
        }
    }

    if (! image) return;
    switch (cc->pix_fmt)
    {
        case AV_PIX_FMT_YUV420P:   // any AVColorRange
        case AV_PIX_FMT_YUVJ420P:  // specifically AVCOL_RANGE_JPEG
            assert (frame->linesize[1] == frame->linesize[2]);
            image->format = &YUV420;
            image->buffer = new PixelBufferPlanar (frame->data[0], frame->data[1], frame->data[2], frame->linesize[0], frame->linesize[1], cc->height, YUV420.ratioH, YUV420.ratioV);
            image->width = cc->width;
            image->height = cc->height;
            break;
        case AV_PIX_FMT_YUV411P:
            assert (frame->linesize[1] == frame->linesize[2]);
            image->format = &YUV411;
            image->buffer = new PixelBufferPlanar (frame->data[0], frame->data[1], frame->data[2], frame->linesize[0], frame->linesize[1], cc->height, YUV411.ratioH, YUV411.ratioV);
            image->width = cc->width;
            image->height = cc->height;
            break;
        case AV_PIX_FMT_YUYV422:
            image->attach (frame->data[0], cc->width, cc->height, YUYV);
            break;
        case AV_PIX_FMT_UYVY422:
            image->attach (frame->data[0], cc->width, cc->height, UYVY);
            break;
        case AV_PIX_FMT_RGB24:
            image->attach (frame->data[0], cc->width, cc->height, RGBChar);
            break;
        case AV_PIX_FMT_BGR24:
            image->attach (frame->data[0], cc->width, cc->height, BGRChar);
            break;
        case AV_PIX_FMT_GRAY8:
            image->attach (frame->data[0], cc->width, cc->height, GrayChar);
            break;
        default:
            cerr << "Unsupported AV_PIX_FMT (see enumeration in libavutil/pixfmt.h): " << cc->pix_fmt << endl;
            throw "Unsupported AV_PIX_FMT";
    }

    if (timestampMode)
    {
        image->timestamp = cc->frame_number - 1;
    }
    else
    {
        image->timestamp = (double) frame->pts * stream->time_base.num / stream->time_base.den;
    }
    gotPicture = 0;
}

/**
   Registry of states:
   0 = everything good
   [-7,-1] = FFMPEG libavformat errors, see avformat.h
   -10 = did not find a video stream
   -11 = did not find a codec
   -12 = failed to allocate codec context
   -13 = VideoInFile is closed
 **/
bool
VideoInFileFFMPEG::good () const
{
    return ! state;
}

String
VideoInFileFFMPEG::get (const String & name) const
{
    char buffer[100];
    if (stream)
    {
        if (name == "duration")  // total length of video
        {
            if (fc->duration == AV_NOPTS_VALUE) return "";
            return (double) fc->duration / AV_TIME_BASE;
        }
        if (name == "startTime")
        {
            return startTime;
        }
        if (name == "startTimeNTP")
        {
            if (fc  &&  strcmp (fc->iformat->name, "rtsp") == 0)
            {
                if (fc->start_time_realtime != AV_NOPTS_VALUE)
                {
                    time_t hi = fc->start_time_realtime / 1000000;
                    struct tm * time = localtime (&hi);
                    double seconds = time->tm_sec + (fc->start_time_realtime % 1000000) / 1000000.0;
                    sprintf (buffer, "%04i%02i%02i%02i%02i%s%f",
                             time->tm_year + 1900,
                             time->tm_mon + 1,
                             time->tm_mday,
                             time->tm_hour,
                             time->tm_min,
                             (seconds < 10 ? "0" : ""),
                             seconds);
                    return buffer;
                }
            }
            return "";
        }
        if (name == "framePeriod")  // average duration of a frame
        {
            return (double) stream->r_frame_rate.den / stream->r_frame_rate.num;
        }
        if (name == "nextPTS")  // Expected timestamp of next frame in seconds. Not necessarily the same as current PTS + "framePeriod", since video can have variable frame rate.
        {
            if (state  ||  nextPTS == AV_NOPTS_VALUE) return INFINITY;
            return (double) nextPTS * stream->time_base.num / stream->time_base.den;
        }
    }
    if (fc)
    {
        if (name == "filename") return fc->url;
        return "";
    }
    if (name == "interleaveRTP")
    {
        if (interleaveRTP) return "1";
        else               return "0";
    }
    if (name == "timestampMode")
    {
        if (timestampMode) return "1";
        else               return "0";
    }
    return "";
}

void
VideoInFileFFMPEG::set (const String & name, const String & value)
{
    if (name == "interleaveRTP")
    {
        interleaveRTP = atoi (value.c_str ());
        return;
    }
    if (name == "timestampMode")
    {
        timestampMode = atoi (value.c_str ());
        return;
    }
}

#ifdef HAVE_JNI

extern "C" JNIEXPORT jstring JNICALL
Java_gov_sandia_n2a_backend_c_VideoIn_suffixes (JNIEnv * env, jclass obj)
{
    // Enumerate formats and accumulate their suffixes in a single string.
    String result;
    void * i = 0;
    while (const AVInputFormat * format = av_demuxer_iterate (&i))
    {
        if (format->extensions == 0) continue;
        result += ",";
        result += format->extensions;
    }
    return env->NewStringUTF (result.c_str ());
}

PixelFormat2BufferedImage pixelFormat2BufferedImageMap[] =
{
    {&BGRxChar,   TYPE_INT_RGB,        4},
    {&BGRAChar,   TYPE_INT_ARGB,       4},
    {&BGRAChar,   TYPE_INT_ARGB_PRE,   4},
    {&RGBxChar,   TYPE_INT_BGR,        4},
    {&BGRChar,    TYPE_3BYTE_BGR,      3},
    {&ABGRChar,   TYPE_4BYTE_ABGR,     4},
    {&ABGRChar,   TYPE_4BYTE_ABGR_PRE, 4},
    {&B5G6R5,     TYPE_USHORT_565_RGB, 2},
    {&B5G5R5,     TYPE_USHORT_555_RGB, 2},
    {&GrayChar,   TYPE_BYTE_GRAY,      1},  // TODO: Comes out dark. Probably should be treated as linear.
    {&GrayShort,  TYPE_USHORT_GRAY,    2},
    {0}
};

// Subroutine of convert{type} routines below
static void convert (jint width, jint height, jint format, void * cbuffer, jint colorSpace, double * cmatrix)
{
    PixelFormat2BufferedImage * m = pixelFormat2BufferedImageMap;
    while (m->pf)
    {
        if (m->bi == format) break;
        m++;
    }
    if (m->pf == 0) return;  // TODO: throw an error

    Image image (cbuffer, width, height, *m->pf);
    Image image2;
    switch (colorSpace)
    {
        case 0: image2 = image * RGBFloat;  break;
        case 1: image2 = image * sRGBFloat; break;
        case 2: image2 = image * XYZFloat;  break;
        case 3: image2 = image * HSVFloat;  break;
    }

    float *  from = (float *) image2.buffer->pixel (0, 0);
    float *  end  = from + width * height * 3;
    double * to   = cmatrix;
    while (from < end) *to++ = *from++;
}

extern "C" JNIEXPORT void JNICALL
Java_gov_sandia_n2a_backend_c_VideoIn_convertByte (JNIEnv * env, jclass obj, jint width, jint height, jint format, jbyteArray buffer, jint colorSpace, jdoubleArray matrix)
{
    void * cbuffer = env->GetPrimitiveArrayCritical (buffer, 0);
    void * cmatrix = env->GetPrimitiveArrayCritical (matrix, 0);
    convert (width, height, format, cbuffer, colorSpace, (double *) cmatrix);
    env->ReleasePrimitiveArrayCritical (buffer, cbuffer, 0);
    env->ReleasePrimitiveArrayCritical (buffer, cmatrix, 0);
}

extern "C" JNIEXPORT void JNICALL
Java_gov_sandia_n2a_backend_c_VideoIn_convertShort (JNIEnv * env, jclass obj, jint width, jint height, jint format, jshortArray buffer, jint colorSpace, jdoubleArray matrix)
{
    void * cbuffer = env->GetPrimitiveArrayCritical (buffer, 0);
    void * cmatrix = env->GetPrimitiveArrayCritical (matrix, 0);
    convert (width, height, format, cbuffer, colorSpace, (double *) cmatrix);
    env->ReleasePrimitiveArrayCritical (buffer, cbuffer, 0);
    env->ReleasePrimitiveArrayCritical (buffer, cmatrix, 0);
}

extern "C" JNIEXPORT void JNICALL
Java_gov_sandia_n2a_backend_c_VideoIn_convertInt (JNIEnv * env, jclass obj, jint width, jint height, jint format, jintArray buffer, jint colorSpace, jdoubleArray matrix)
{
    void * cbuffer = env->GetPrimitiveArrayCritical (buffer, 0);
    void * cmatrix = env->GetPrimitiveArrayCritical (matrix, 0);
    convert (width, height, format, cbuffer, colorSpace, (double *) cmatrix);
    env->ReleasePrimitiveArrayCritical (buffer, cbuffer, 0);
    env->ReleasePrimitiveArrayCritical (buffer, cmatrix, 0);
}

extern "C" JNIEXPORT jlong JNICALL
Java_gov_sandia_n2a_backend_c_VideoIn_construct (JNIEnv * env, jclass obj, jstring path)
{
    VideoFileFormatFFMPEG::use ();
    const char * cpath = env->GetStringUTFChars (path, 0);
    VideoIn * result = new VideoIn (cpath);
    env->ReleaseStringUTFChars (path, cpath);
    return (jlong) result;
}

extern "C" JNIEXPORT void JNICALL
Java_gov_sandia_n2a_backend_c_VideoIn_open (JNIEnv * env, jclass obj, jlong handle, jstring path)
{
    const char * cpath = env->GetStringUTFChars (path, 0);
    ((VideoIn *) handle)->open (cpath);
    env->ReleaseStringUTFChars (path, cpath);
}

extern "C" JNIEXPORT void JNICALL
Java_gov_sandia_n2a_backend_c_VideoIn_close (JNIEnv * env, jclass obj, jlong handle)
{
    ((VideoIn *) handle)->close ();
}

extern "C" JNIEXPORT void JNICALL
Java_gov_sandia_n2a_backend_c_VideoIn_seekFrame (JNIEnv * env, jclass obj, jlong handle, jint frame)
{
    ((VideoIn *) handle)->seekFrame (frame);
}

extern "C" JNIEXPORT void JNICALL
Java_gov_sandia_n2a_backend_c_VideoIn_seekTime (JNIEnv * env, jclass obj, jlong handle, jdouble timestamp)
{
    ((VideoIn *) handle)->seekTime (timestamp);
}

extern "C" JNIEXPORT jobject JNICALL
Java_gov_sandia_n2a_backend_c_VideoIn_readNext (JNIEnv * env, jclass obj, jlong handle)
{
    VideoIn & video = * (VideoIn *) handle;
    Image image;
    try
    {
        video >> image;
    }
    catch (const char * message)
    {
        cerr << "Exception: " << message << endl;
        return 0;  // TODO: should video be marked bad?
    }
    if (! video.good ()) return 0;

    PixelFormat2BufferedImage * m = pixelFormat2BufferedImageMap;
    while (m->pf)
    {
        if (m->pf == &*image.format) break;
        m++;
    }
    if (m->pf == 0)
    {
        m = pixelFormat2BufferedImageMap;
        image *= *m->pf;
    }

    jclass     cls     = env->FindClass   ("gov/sandia/n2a/backend/c/VideoIn$Image");
    jmethodID  ctor    = env->GetMethodID (cls, "<init>", "(IIII)V");
    jobject    result  = env->NewObject   (cls, ctor, image.width, image.height, m->bi, m->size);
    jfieldID   fieldID = env->GetFieldID  (cls, "buffer", "[B");
    jbyteArray buffer  = static_cast<jbyteArray> (env->GetObjectField (result, fieldID));
    int        count   = image.width * image.height * m->size;
    env->SetByteArrayRegion (buffer, 0, count, (jbyte *) image.buffer->pixel (0, 0));

    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_gov_sandia_n2a_backend_c_VideoIn_good (JNIEnv * env, jclass obj, jlong handle)
{
    return ((VideoIn *) handle)->good ();
}

extern "C" JNIEXPORT jstring JNICALL
Java_gov_sandia_n2a_backend_c_VideoIn_get (JNIEnv * env, jclass obj, jlong handle, jstring name)
{
    const char * cname = env->GetStringUTFChars (name, 0);
    String value = ((VideoIn *) handle)->get (cname);
    env->ReleaseStringUTFChars (name, cname);
    return env->NewStringUTF (value.c_str ());
}

extern "C" JNIEXPORT void JNICALL
Java_gov_sandia_n2a_backend_c_VideoIn_set (JNIEnv * env, jclass obj, jlong handle, jstring name, jstring value)
{
    const char * cname  = env->GetStringUTFChars (name,  0);
    const char * cvalue = env->GetStringUTFChars (value, 0);
    ((VideoIn *) handle)->set (cname, cvalue);
    env->ReleaseStringUTFChars (name,  cname);
    env->ReleaseStringUTFChars (value, cvalue);
}

#endif


// class VideoOutFileFFMPEG ---------------------------------------------------

class VideoOutFileFFMPEG : public VideoOutFile
{
public:
    VideoOutFileFFMPEG (const String & fileName, const String & formatName, const String & codecName);
    ~VideoOutFileFFMPEG ();

    void open (const String & fileName, const String & formatName, const String & codecName);
    void close ();
    virtual void writeNext (const Image & image);
    void drainEncoder ();  // Subroutine of writeNext() and close().
    virtual bool good () const;
    virtual String get (const String & name) const;
    virtual void   set (const String & name, const String & value);

    AVFormatContext *  fc;
    AVStream *         stream;
    const AVCodec *    codec;
    AVCodecContext *   cc;
    AVFrame *          frame;
    AVPacket *         packet;
    n2a::PixelFormat * pixelFormat; ///< The format in which the codec receives the image.
    bool               needHeader;  ///< indicates that file header needs to be written; also that codec needs to be opened
    n2a::Pointer       videoBuffer; ///< Working memory for encoder.
    int                state;
};

VideoOutFileFFMPEG::VideoOutFileFFMPEG (const String & fileName, const String & formatName, const String & codecName)
{
    fc          = 0;
    stream      = 0;
    codec       = 0;
    cc          = 0;
    frame       = av_frame_alloc ();
    packet      = av_packet_alloc ();
    pixelFormat = 0;
    needHeader  = false;

    open (fileName, formatName, codecName);
}

VideoOutFileFFMPEG::~VideoOutFileFFMPEG ()
{
    close ();

    av_packet_free (&packet);
    av_frame_free (&frame);
}

void
VideoOutFileFFMPEG::open (const String & fileName, const String & formatName, const String & codecName)
{
    close ();

    // Select container format
    const char * formatAddress = formatName.size () ? formatName.c_str () : 0;
    AVOutputFormat * format = (AVOutputFormat *) av_guess_format
    (
        formatAddress,
        fileName.c_str (),
        formatAddress
    );
    if (! format)
    {
        state = -10;
        return;
    }

    // Initialize format context.
    state = avformat_alloc_output_context2 (&fc, format, 0, fileName.c_str ());
    if (state) return;

    // Select codec
    if (codecName.size ())
    {
        void * i = 0;
        while (const AVCodec * c = av_codec_iterate (&i))
        {
            if (av_codec_is_encoder (c)  &&  c->type == AVMEDIA_TYPE_VIDEO  &&  codecName == c->name)
            {
                codec = c;
                break;
            }
        }
    }
    if (! codec)
    {
        // Use default codec for container
        codec = avcodec_find_encoder (fc->oformat->video_codec);
    }
    if (! codec)
    {
        state = -11;
        return;
    }

    // Create stream
    stream = avformat_new_stream (fc, 0);
    if (! stream)
    {
        state = -12;
        return;
    }

    // Create codec context
    cc = avcodec_alloc_context3 (codec);
    if (! cc)
    {
        state = -13;
        return;
    }

    // Set codec parameters.
    cc->codec_type = codec->type;
    cc->codec_id   = codec->id;
    cc->gop_size   = 12; // default = 50; industry standard is 12 or 15
    if (codec->id == AV_CODEC_ID_MPEG2VIDEO)
    {
        cc->max_b_frames = 2;
    }
    if (fc->oformat->flags & AVFMT_GLOBALHEADER)
    {
        cc->flags |= AV_CODEC_FLAG_GLOBAL_HEADER;
    }
    if (codec->supported_framerates)
    {
        const AVRational & fr = codec->supported_framerates[0];
        cc->time_base.num = fr.den;
        cc->time_base.den = fr.num;
    }
    else  // any framerate is ok, so pick our favorite default
    {
        cc->time_base.num = 1;
        cc->time_base.den = 24;
    }

    // Open file for writing
    if (! (fc->oformat->flags & AVFMT_NOFILE))
    {
        state = avio_open (&fc->pb, fileName.c_str (), AVIO_FLAG_WRITE);
        if (state < 0) return;
    }

    pixelFormat = 0;
    needHeader  = true;
    state       = 0;
}

void
VideoOutFileFFMPEG::close ()
{
    if (fc)
    {
        if (cc)
        {
            state = avcodec_send_frame (cc, 0);  // Signal that all frames have been written, so begin the final drain.
            drainEncoder ();
        }
        if (! needHeader  &&  ! state)  // A header was written, and probably some frames as well, so file needs to be closed out properly.
        {
            av_write_trailer (fc);  // Clears private data used by avformat.  Private data is not allocated until av_write_header(), so this is balanced.
        }
        if (! (fc->oformat->flags & AVFMT_NOFILE)) avio_closep (&fc->pb);
        avformat_free_context (fc);
        fc = 0;
    }

    if (cc) avcodec_free_context (&cc);  // Disposes of stream. Nulls out cc.
    codec = 0;
    stream = 0;

    state = -14;
}

struct PixelFormatMapping
{
    n2a::PixelFormat * fl;
    AVPixelFormat      av;
};

static PixelFormatMapping pixelFormatMap[] =
{
    // For efficiency, the first few entries in this list are the most likely formats.
    // After that, the rest follow the order in image.h
    {&YUV420,         AV_PIX_FMT_YUV420P},
    {&YUV420,         AV_PIX_FMT_YUVJ420P},  // Deprecated, but needed for reverse lookup on older versions of FFMPEG library.
    {&YUV411,         AV_PIX_FMT_YUV411P},
    {&YUYV,           AV_PIX_FMT_YUYV422},
    {&UYVY,           AV_PIX_FMT_UYVY422},
    {&RGBChar,        AV_PIX_FMT_RGB24},
    {&BGRChar,        AV_PIX_FMT_BGR24},
    {&GrayChar,       AV_PIX_FMT_GRAY8},
    {&GrayShort,      AV_PIX_FMT_GRAY16LE},
    {&GrayAlphaChar,  AV_PIX_FMT_YA8},
    {&GrayAlphaShort, AV_PIX_FMT_YA16LE},
    {&GrayFloat,      AV_PIX_FMT_GRAYF32LE},
    {&RGBAChar,       AV_PIX_FMT_RGBA},
    {&RGBAShort,      AV_PIX_FMT_RGBA64LE},
    {&RGBShort,       AV_PIX_FMT_RGB48LE},
    {&B5G5R5,         AV_PIX_FMT_BGR555LE},
    {&BGRxChar,       AV_PIX_FMT_BGR0},
    {&RGBxChar,       AV_PIX_FMT_RGB0},
    {&BGRAChar,       AV_PIX_FMT_BGRA},
    {&UYYVYY,         AV_PIX_FMT_UYYVYY411},
    {0,               (AVPixelFormat) -1}
};

void
VideoOutFileFFMPEG::writeNext (const Image & image)
{
    if (state) return;

    cc->width  = image.width;
    cc->height = image.height;
    if (cc->pix_fmt == AV_PIX_FMT_NONE)
    {
        enum AVPixelFormat best = AV_PIX_FMT_YUV420P;  // FFMPEG's default
        if (codec->pix_fmts)  // options are available, so enumerate and find best match for image.format
        {
            best = codec->pix_fmts[0];

            // Select AV_PIX_FMT associate with image.format
            PixelFormatMapping * m = pixelFormatMap;
            while (m->fl)
            {
                if ((const n2a::PixelFormat *) image.format == m->fl) break;
                m++;
            }
            enum AVPixelFormat target = m->av;
            if (target == -1  &&  image.format->monochrome) target = AV_PIX_FMT_GRAY8;

            // See if AV_PIX_FMT is in supported list
            if (target >= 0)
            {
                const enum AVPixelFormat * p = codec->pix_fmts;
                while (*p != -1)
                {
                    if (*p == target)
                    {
                        best = *p;
                        break;
                    }
                    p++;
                }
            }
        }
        cc->pix_fmt = best;
        cc->color_range = AVCOL_RANGE_JPEG;
    }

    if (needHeader)
    {
        // Must know pixel format before opening codec, and we only know that
        // after receiving the first image, so we open the codec here rather than
        // in open().
        state = avcodec_open2 (cc, codec, 0);
        if (state < 0) return;

        state = avcodec_parameters_from_context (stream->codecpar, cc);
        if (state < 0) return;

        state = avformat_write_header (fc, 0);
        if (state < 0) return;

        state = 0;
        needHeader = false;
    }

    if (! pixelFormat)
    {
        PixelFormatMapping * m = pixelFormatMap;
        while (m->fl)
        {
            if (m->av == cc->pix_fmt) break;
            m++;
        }
        if (! m->fl) throw "Unsupported AV_PIX_FMT";
        pixelFormat = m->fl;
        if (typeid (*pixelFormat) == typeid (PixelFormatPlanarYCbCr)) cc->color_range = AVCOL_RANGE_MPEG;
    }

    // Get image into a format that FFMPEG understands...
    frame->width  = cc->width;
    frame->height = cc->height;
    frame->format = cc->pix_fmt;
    Image converted = image * *pixelFormat;
    if (PixelBufferPlanar * pb = (PixelBufferPlanar *) converted.buffer)
    {
        frame->data[0] = (unsigned char *) pb->plane0;
        frame->data[1] = (unsigned char *) pb->plane1;
        frame->data[2] = (unsigned char *) pb->plane2;
        frame->linesize[0] = pb->stride0;
        frame->linesize[1] = pb->stride12;  // assumes planes always have depth 1
        frame->linesize[2] = pb->stride12;
    }
    else if (PixelBufferPacked * pb = (PixelBufferPacked *) converted.buffer)
    {
        frame->data[0]     = (unsigned char *) pb->base ();
        frame->linesize[0] = pb->stride;
    }
    else throw "Unhandled buffer type";

    if (image.timestamp < 95443)  // approximately 2^33 / 90kHz, or about 26.5 hours.  Times larger than this are probably coming from the system clock and are not intended to be encoded in the video.
    {
        frame->pts = (int64_t) roundp (image.timestamp * cc->time_base.den / cc->time_base.num);
    }

    // Finally, encode and write the frame
    state = avcodec_send_frame (cc, frame);  // This should never return AVERROR(EAGAIN), because we always drain immediately ...
    if (state == 0) drainEncoder ();
}

void
VideoOutFileFFMPEG::drainEncoder ()
{
    while (state == 0)
    {
        state = avcodec_receive_packet (cc, packet);
        if (state == AVERROR_EOF)  // done draining for last time
        {
            av_interleaved_write_frame (fc, 0);  // Flush any remaining buffered packets.
            state = 0;  // not an error condition
            return;
        }
        if (state == AVERROR(EAGAIN))  // waiting for more frames before outputting
        {
            state = 0;  // not an error condition
            return;
        }
        if (state) return;  // error

        av_packet_rescale_ts (packet, cc->time_base, stream->time_base);  // Scales both pts and dts.
        packet->stream_index = stream->index;
        state = av_interleaved_write_frame (fc, packet);
    }
}

bool
VideoOutFileFFMPEG::good () const
{
    return ! state;
}

String
VideoOutFileFFMPEG::get (const String & name) const
{
    return "";
}

void
VideoOutFileFFMPEG::set (const String & name, const String & value)
{
    if (! stream) return;

    if (name == "framerate")
    {
        double v = atof (value.c_str ());
        if (codec  &&  codec->supported_framerates)
        {
            // Restricted set of framerates, so select closest one
            const AVRational * fr = codec->supported_framerates;
            const AVRational * bestRate = fr;
            double bestDistance = INFINITY;
            while (fr->den)
            {
                double rate = (double) fr->num / fr->den;
                double distance = fabs (v - rate);
                if (distance < bestDistance)
                {
                    bestDistance = distance;
                    bestRate = fr;
                }
                fr++;
            }
            cc->time_base.num = bestRate->den;
            cc->time_base.den = bestRate->num;
        }
        else  // arbitrary framerate is acceptable
        {
            cc->time_base.num = AV_TIME_BASE;
            cc->time_base.den = (int) roundp (v * AV_TIME_BASE);
        }
    }
    else if (name == "bitrate")
    {
        cc->bit_rate = atoi (value.c_str ());
    }
    else if (name == "gop")
    {
        cc->gop_size = atoi (value.c_str ());
    }
    else if (name == "bframes")
    {
        cc->max_b_frames = atoi (value.c_str ());
    }
}

#ifdef HAVE_JNI

extern "C" JNIEXPORT jlong JNICALL
Java_gov_sandia_n2a_backend_c_VideoOut_construct (JNIEnv * env, jclass obj, jstring path, jstring format, jstring codec)
{
    VideoFileFormatFFMPEG::use ();
    const char * cpath   = env->GetStringUTFChars (path,   0);
    const char * cformat = env->GetStringUTFChars (format, 0);
    const char * ccodec  = env->GetStringUTFChars (codec,  0);
    VideoOut * result = new VideoOut (cpath, cformat, ccodec);
    env->ReleaseStringUTFChars (path, cpath);
    env->ReleaseStringUTFChars (path, cformat);
    env->ReleaseStringUTFChars (path, ccodec);
    return (jlong) result;
}

// Subroutine of write{type} routines below
static void writeNext (jlong handle, jdouble timestamp, jint width, jint height, jint format, void * cbuffer)
{
    VideoOut & video = * (VideoOut *) handle;

    PixelFormat2BufferedImage * m = pixelFormat2BufferedImageMap;
    while (m->pf)
    {
        if (m->bi == format) break;
        m++;
    }
    if (m->pf == 0) return;  // TODO: throw an error

    Image image (cbuffer, width, height, *m->pf);
    image.timestamp = timestamp;
    try
    {
        video << image;
    }
    catch (const char * message)
    {
        cerr << "Exception: " << message << endl;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_gov_sandia_n2a_backend_c_VideoOut_writeByte (JNIEnv * env, jclass obj, jlong handle, jdouble timestamp, jint width, jint height, jint format, jbyteArray buffer)
{
    void * cbuffer = env->GetPrimitiveArrayCritical (buffer, 0);
    writeNext (handle, timestamp, width, height, format, cbuffer);
    env->ReleasePrimitiveArrayCritical (buffer, cbuffer, 0);
}

extern "C" JNIEXPORT void JNICALL
Java_gov_sandia_n2a_backend_c_VideoOut_writeShort (JNIEnv * env, jclass obj, jlong handle, jdouble timestamp, jint width, jint height, jint format, jshortArray buffer)
{
    void * cbuffer = env->GetPrimitiveArrayCritical (buffer, 0);
    writeNext (handle, timestamp, width, height, format, cbuffer);
    env->ReleasePrimitiveArrayCritical (buffer, cbuffer, 0);
}

extern "C" JNIEXPORT void JNICALL
Java_gov_sandia_n2a_backend_c_VideoOut_writeInt (JNIEnv * env, jclass obj, jlong handle, jdouble timestamp, jint width, jint height, jint format, jintArray buffer)
{
    void * cbuffer = env->GetPrimitiveArrayCritical (buffer, 0);
    writeNext (handle, timestamp, width, height, format, cbuffer);
    env->ReleasePrimitiveArrayCritical (buffer, cbuffer, 0);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_gov_sandia_n2a_backend_c_VideoOut_good (JNIEnv * env, jclass obj, jlong handle)
{
    return ((VideoOut *) handle)->good ();
}

extern "C" JNIEXPORT jstring JNICALL
Java_gov_sandia_n2a_backend_c_VideoOut_get (JNIEnv * env, jclass obj, jlong handle, jstring name)
{
    const char * cname = env->GetStringUTFChars (name, 0);
    String value = ((VideoOut *) handle)->get (cname);
    env->ReleaseStringUTFChars (name, cname);
    return env->NewStringUTF (value.c_str ());
}

extern "C" JNIEXPORT void JNICALL
Java_gov_sandia_n2a_backend_c_VideoOut_set (JNIEnv * env, jclass obj, jlong handle, jstring name, jstring value)
{
    const char * cname  = env->GetStringUTFChars (name,  0);
    const char * cvalue = env->GetStringUTFChars (value, 0);
    ((VideoOut *) handle)->set (cname, cvalue);
    env->ReleaseStringUTFChars (name,  cname);
    env->ReleaseStringUTFChars (value, cvalue);
}

#endif


// class VideoFileFormatFFMPEG ------------------------------------------------

void
VideoFileFormatFFMPEG::use ()
{
    vector<VideoFileFormat *>::iterator i;
    for (i = formats.begin (); i < formats.end (); i++)
    {
        if (typeid (**i) == typeid (VideoFileFormatFFMPEG)) return;
    }
    formats.push_back (new VideoFileFormatFFMPEG);
}

VideoFileFormatFFMPEG::VideoFileFormatFFMPEG ()
{
#   if CONFIG_AVDEVICE
    avdevice_register_all ();
#   endif
    avformat_network_init ();
}

VideoInFile *
VideoFileFormatFFMPEG::openInput (const String & fileName) const
{
    return new VideoInFileFFMPEG (fileName);
}

VideoOutFile *
VideoFileFormatFFMPEG::openOutput (const String & fileName, const String & formatName, const String & codecName) const
{
    return new VideoOutFileFFMPEG (fileName, formatName, codecName);
}

float
VideoFileFormatFFMPEG::isIn (const String & fileName) const
{
    return 1.0;  // For now, we pretend to handle everything.
}

float
VideoFileFormatFFMPEG::handles (const String & formatName, const String & codecName) const
{
    return 1.0;
}
