/*
Copyright 2018-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


#ifndef n2a_holder_h
#define n2a_holder_h


#include "nosys.h"
#include "StringLite.h"
#include "matrix.h"
#include "MNode.h"
#include "canvas.h"
#ifdef HAVE_FFMPEG
#  include "video.h"
#endif
#ifdef HAVE_GL
#  include <GL/gl.h>  // Basic functions and types. According to Khronos, we're not supposed to include this when using glcorearb.h
#endif

#include <vector>
#include <unordered_map>

#include "shared.h"


/**
    Utility class for reading/accessing command-line parameters.
    These are primarily intended to override parameters within the model.
**/
template<class T>
class SHARED Parameters
{
public:
    std::unordered_map<String,String> namedValues;

    void   parse (const String & line);
    void   parse (int argc, const char * argv[]);
    void   read  (const String & parmFileName);
    void   read  (std::istream & stream);

    T      get   (const String & name, T defaultValue = 0) const;
    String get   (const String & name, const String & defaultValue = "") const;
};

class SHARED Holder
{
public:
    String fileName;
    Holder (const String & fileName);
    virtual ~Holder ();
};

template<class T>
class SHARED IteratorNonzero
{
public:
    int row;
    int column;
    T   value;

    virtual bool next () = 0;  // Advances to next nonzero element. Returns false if no more are available.
};

template<class T>
class SHARED IteratorSkip : public IteratorNonzero<T>
{
public:
    Matrix<T> * A;
    int nextRow;
    int nextColumn;
    T   nextValue;

    IteratorSkip (Matrix<T> * A);

    virtual bool next ();
    void         getNext ();
};

template<class T>
class SHARED IteratorSparse : public IteratorNonzero<T>
{
public:
    MatrixSparse<T> *                  A;
    int                                columns;
    typename std::map<int,T>::iterator it;

    IteratorSparse (MatrixSparse<T> * A);
    virtual bool next ();
};

extern SHARED int convert (String input, int exponent);

template<class T>
class SHARED MatrixInput : public Holder
{
public:
    MatrixAbstract<T> * A;  // Will be either Matrix or MatrixSparse, determined by matrixHelper when reading the file.

    MatrixInput (const String & fileName);
    virtual ~MatrixInput ();
};
#ifdef n2a_FP
template<class T> extern SHARED MatrixInput<T> * matrixHelper (const String & fileName, int exponent, MatrixInput<T> * oldHandle = 0);
#else
template<class T> extern SHARED MatrixInput<T> * matrixHelper (const String & fileName,               MatrixInput<T> * oldHandle = 0);
#endif

template<class T> extern SHARED IteratorNonzero<T> * getIterator (MatrixAbstract<T> * A);  // Returns an object that iterates over nonzero elements of A.

template<class T>
class SHARED ImageInput : public Holder
{
public:
#   ifdef HAVE_FFMPEG
    n2a::VideoIn * video;
#   endif

    n2a::Image                           image;
    String                               pattern;     ///< printf pattern for generating sequence file names. Includes full path to directory where sequence resides. If empty, then this is not a sequence or we are using FFmpeg to handle it.
    int                                  index;       ///< of current image in sequence
    T                                    t;           ///< next PTS for video, or next frame number for sequence
    double                               framePeriod; ///< for converting PTS to sequence number. Nonzero only when handling a sequence through FFmpeg.
    std::unordered_map<String,Matrix<T>> channels;

    ImageInput (const String & fileName);
    ~ImageInput ();

#   ifdef n2a_FP
    Matrix<T> get (String channelName, T now, int exponent);
#   else
    Matrix<T> get (String channelName, T now);  ///< @param now If positive, then desired PTS in seconds. If negative, then $t.
#   endif
};
template<class T> extern SHARED ImageInput<T> * imageInputHelper (const String & fileName, ImageInput<T> * oldHandle = 0);

#ifdef HAVE_GL

class LightLocation
{
public:
    GLint infinite;
    GLint position;
    GLint direction;
    GLint ambient;
    GLint diffuse;
    GLint specular;
    GLint spotExponent;
    GLint spotCutoff;
    GLint attenuation0;
    GLint attenuation1;
    GLint attenuation2;

    LightLocation (GLuint program, int index);
};

class Light
{
public:
    bool  infinite;
    float position[3];
    float direction[3];
    float ambient[3];
    float diffuse[3];
    float specular[3];
    float spotExponent;
    float spotCutoff;  // cos(cutoff) rather than raw angle
    float attenuation0;
    float attenuation1;
    float attenuation2;

    Light ();
    void setUniform (const LightLocation & l, const Matrix<float> & view);
};

class Material
{
public:
    float ambient[3];
    float diffuse[4];
    float emission[3];
    float specular[3];
    float shininess;

    static GLint locAmbient;
    static GLint locDiffuse;
    static GLint locEmission;
    static GLint locSpecular;
    static GLint locShininess;

    Material ();
    void setUniform ();
};

void put       (std::vector<GLfloat> & vertices,                  float x, float y, float z, float[3] n);
void put       (std::vector<GLfloat> & vertices, Matrix<float> f, float x, float y, float z, float nx, float ny, float nz);
int  putUnique (std::vector<GLfloat> & vertices,                  float x, float y, float z);

void icosphere          (std::vector<GLfloat> & vertices, std::vector<GLuint> & indices);
void icosphereSubdivide (std::vector<GLfloat> & vertices, std::vector<GLuint> & indices);
void split              (std::vector<GLfloat> & vertices, int v0, int v1);

#endif

template<class T>
class SHARED ImageOutput : public Holder
{
public:
    String path;    // prefix of fileName, not including suffix (format)
    String format;  // Name of format as recognized by supporting libraries. For video, can be set by keyword parameter. For image sequence, derived automatically from fileName suffix.
    bool   hold;    // Store a single frame rather than an image sequence.
    bool   dirCreated;

    int      width;
    int      height;
    uint32_t clearColor;  // kept in sync with cv

    T                t;
    int              frameCount; // Number of frames actually written so far.
    bool             haveData;   // indicates that something has been drawn since last write to disk
    n2a::CanvasImage canvas;     // Current image being built.
    bool             opened;     // Indicates that video or image-sequence output has been configured. This is delayed until first write to disk, so user can set video parameters.
#   ifdef HAVE_FFMPEG
    n2a::VideoOut *  video;
    String           codec;      // Optional specification of video encoder. Default is derived from container.
    double           timeScale;  // Multiply simtime (t) by this value to get PTS. Zero (default value) means 24fps, regardless of simtime.
#   endif

#   ifdef HAVE_GL
#     ifdef _WIN32
    HWND   window;
    HDC    dc;
    HGLRC  rc;
#     endif
    bool                         extensionsBound;  // Indicates that extension function addresses have been bound.
    GLuint                       program;
    GLuint                       rboColor;
    GLuint                       rboDepth;
    GLint                        locVertexPosition;
    GLint                        locVertexNormal;
    GLint                        locMatrixModelView;
    GLint                        locMatrixNormal;
    GLint                        locMatrixProjection;
    std::vector<LightLocation *> locLights;
    GLint                        locEnabled;
    bool                         have3D;
    float                        cv[4];  // Color vector; kept in sync with clearColor
    int                          lastWidth;
    int                          lastHeight;
    Matrix<float>                projection;
    Matrix<float>                view;
    std::map<int,Light *>        lights;
    std::map<String,GLuint>      buffers;
    int                          sphereStep;
    std::vector<GLfloat>         sphereVertices;
    std::vector<GLuint>          sphereIndices;
#   endif
    Matrix<float>                nextProjection;  // Initialized to 4x4, all zeros. If all zeros a start of 3D drawing, then we generate a default matrix based on current view size.
    Matrix<float>                nextView;        // Initialized to 4x4 identity, which is also the default.

    ImageOutput (const String & fileName);
    virtual ~ImageOutput ();
    void open ();  // Subroutine of writeImage()

    void setClearColor (uint32_t          color);
    void setClearColor (const Matrix<T> & color);  // Converting to Matrix<T> lets us be agnostic about orientation, unless it is already Matrix<T> and there are gaps between columns (unlikely).

    void next (T now);
#   ifdef n2a_FP
    // All pixel-valued arguments must agree on exponent. "now" is in time exponent.
    T drawDisc    (T now, bool raw, const MatrixFixed<T,3,1> & center, T radius,                               int exponent, uint32_t color);
    T drawSquare  (T now, bool raw, const MatrixFixed<T,3,1> & center, T w, T h,                               int exponent, uint32_t color);
    T drawSegment (T now, bool raw, const MatrixFixed<T,3,1> & p1, const MatrixFixed<T,3,1> & p2, T thickness, int exponent, uint32_t color);
#   else
    T drawDisc    (T now, bool raw, const MatrixFixed<T,3,1> & center, T radius,                               uint32_t color);
    T drawSquare  (T now, bool raw, const MatrixFixed<T,3,1> & center, T w, T h,                               uint32_t color);
    T drawSegment (T now, bool raw, const MatrixFixed<T,3,1> & p1, const MatrixFixed<T,3,1> & p2, T thickness, uint32_t color);
#   endif
    void writeImage ();

    // 3D drawing functions.
#   ifdef HAVE_GL
    bool next3D (const Matrix<T> & model, const Material & material);  // Additional setup work done by 3D draw functions. Does both one-time initialization and per-frame initialization, as needed.
    T drawCube     (T now, const Matrix<T> & model, const Material & material);
    T drawCylinder (T now,                          const Material & material, const MatrixFixed<T,3,1> & p1, T r1, const MatrixFixed<T,3,1> & p2, T r2 = -1, int steps = 6, int stepsCap = -1);
    T drawPlane    (T now, const Matrix<T> & model, const Material & material);
    T drawSphere   (T now, const Matrix<T> & model, const Material & material, int steps = 1);
    GLuint getBuffer (String name, int size);
#   endif
};
template<class T> extern SHARED ImageOutput<T> * imageOutputHelper (const String & fileName, ImageOutput<T> * oldHandle = 0);

template<class T>
class SHARED Mfile : public Holder
{
public:
    n2a::MDoc *                          doc;
    std::map<String,MatrixAbstract<T> *> matrices;  // Could use unordered_map. Generally, there will be very few entries (like 1), so not sure which will cost the least.

    Mfile (const String & fileName);
    virtual ~Mfile ();

#   ifdef n2a_FP
    MatrixAbstract<T> * getMatrix (const std::vector<String> & path, int exponent);
#   else
    MatrixAbstract<T> * getMatrix (const std::vector<String> & path);
#   endif
};
template<class T> extern SHARED Mfile<T> * MfileHelper (const String & fileName, Mfile<T> * oldHandle = 0);

extern SHARED std::vector<String> keyPath (const std::vector<String> & path);  ///< Converts any path elements with delimiters (/) into separate elements.
template<typename... Args> std::vector<String> keyPath (Args... keys) {return keyPath ({keys...});}

template<class T>
class SHARED InputHolder : public Holder
{
public:
    std::istream *                 in;
    T                              currentLine;
    T *                            currentValues;
    int                            currentCount;
    T                              nextLine;
    T *                            nextValues;
    int                            nextCount;
    Matrix<T> *                    A;
    T                              Alast;
    int                            columnCount;
    std::unordered_map<String,int> columnMap;
    int                            timeColumn;
    bool                           timeColumnSet;
    bool                           time;     ///< mode
    bool                           smooth;   ///< mode; when true, time must also be true
    char                           delimiter;
    bool                           delimiterSet;
    T                              epsilon;  ///< for time values
#   ifdef n2a_FP
    int                            exponent;  ///< of value returned by get()
#   endif

    InputHolder (const String & fileName);
    virtual ~InputHolder ();

    void      getRow (T row); ///< subroutine of get()
    T         get    (T row, const String & column);
    T         get    (T row, T column);
    Matrix<T> get    (T row);
};
#ifdef n2a_FP
template<class T> extern SHARED InputHolder<T> * inputHelper (const String & fileName, int exponent, InputHolder<T> * oldHandle = 0);
#else
template<class T> extern SHARED InputHolder<T> * inputHelper (const String & fileName,               InputHolder<T> * oldHandle = 0);
#endif

template<class T>
class SHARED OutputHolder : public Holder
{
public:
    bool                                   raw;             ///< Indicates that column is an exact index.
    std::ostream *                         out;
    String                                 columnFileName;
    std::unordered_map<String,int>         columnMap;
    std::vector<std::map<String,String> *> columnMode;
    std::vector<float>                     columnValues;
    int                                    columnsPrevious; ///< Number of columns written in previous cycle.
    bool                                   traceReceived;   ///< Indicates that at least one column was touched during the current cycle.
    T                                      t;

    OutputHolder (const String & fileName);
    virtual ~OutputHolder ();

    void trace (T now);               ///< Subroutine for other trace() functions.
    void addMode (const char * mode); ///< Subroutine for other trace() functions.
#   ifdef n2a_FP
    T         trace (T now, const String & column, T                 value, int exponent, const char * mode = 0);
    Matrix<T> trace (T now, const String & column, const Matrix<T> & A,     int exponent, const char * mode = 0);
    T         trace (T now, T              column, T                 value, int exponent, const char * mode = 0);
#   else
    T         trace (T now, const String & column, T                 value,               const char * mode = 0);
    Matrix<T> trace (T now, const String & column, const Matrix<T> & A,                   const char * mode = 0);
    T         trace (T now, T              column, T                 value,               const char * mode = 0);
#   endif
    void writeTrace ();
    void writeModes ();
};
template<class T> extern SHARED OutputHolder<T> * outputHelper (const String & fileName, OutputHolder<T> * oldHandle = 0);


#endif
