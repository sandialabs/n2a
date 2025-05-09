/*
Copyright 2018-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

/*
For 3D graphics support, this software depends on header files from the Khronos Group.
Download the following files and place them in same directory as holder.h:
    https://www.khronos.org/registry/OpenGL/api/GL/glcorearb.h
    https://www.khronos.org/registry/OpenGL/api/GL/wglext.h
Store in subdirectory KHR:
    https://www.khronos.org/registry/EGL/api/KHR/khrplatform.h
*/


#ifndef n2a_holder_h
#define n2a_holder_h


#include "nosys.h"
#include "mystring.h"
#include "matrix.h"
#include "MNode.h"
#include "canvas.h"
#ifdef HAVE_FFMPEG
#  include "video.h"
#endif

// Control how Windows gets included.
#ifdef _MSC_VER
#  define WIN32_LEAN_AND_MEAN
#  include <windows.h>
#  undef min
#  undef max
#endif

#ifdef HAVE_GL
#  include "glcorearb.h"
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
    void   parse (int argc, const char * argv[]);  ///< The arguments have the same semantics as main(argc, argv). In particular, the first argument is ignored because it is the name of the program.
    void   read  (const String & parmFileName);
    void   read  (std::istream & stream);

    T      get   (const String & name, T defaultValue = (T) 0) const;  // TODO: perform unit conversion. Consider using https://github.com/LLNL/units or https://github.com/martinmoene/PhysUnits-RT
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

SHARED int convert (String input, int exponent);

template<class T>
class SHARED MatrixInput : public Holder
{
public:
    MatrixAbstract<T> * A;  // Will be either Matrix or MatrixSparse, determined by matrixHelper when reading the file.

    MatrixInput (const String & fileName);
    virtual ~MatrixInput ();
};
#ifdef n2a_FP
template<class T> SHARED MatrixInput<T> * matrixHelper (const String & fileName, int exponent, MatrixInput<T> * oldHandle = 0);
#else
template<class T> SHARED MatrixInput<T> * matrixHelper (const String & fileName,               MatrixInput<T> * oldHandle = 0);
#endif

template<class T> SHARED IteratorNonzero<T> * getIterator (MatrixAbstract<T> * A);  // Returns an object that iterates over nonzero elements of A.

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
    Matrix<T> get (String channelName, T now, bool step, int exponent);
#   else
    Matrix<T> get (String channelName, T now, bool step);  ///< @param step Indicates one frame step per cycle. If true, then "now" is $t. If false, then "now" is desired PTS in seconds.
#   endif
};
template<class T> SHARED ImageInput<T> * imageInputHelper (const String & fileName, ImageInput<T> * oldHandle = 0);

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

class SHARED Light
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

    void clear ();  // Reset all values to default.
    void setUniform (const LightLocation & l, const Matrix<float> & view);
};

class SHARED Material
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
    void setUniform () const;
};

void put       (std::vector<GLfloat> & vertices,                  float x, float y, float z, float n[3]);
void put       (std::vector<GLfloat> & vertices, Matrix<float> f, float x, float y, float z, float nx, float ny, float nz);
int  putUnique (std::vector<GLfloat> & vertices,                  float x, float y, float z);

void icosphere          (std::vector<GLfloat> & vertices, std::vector<GLuint> & indices);
void icosphereSubdivide (std::vector<GLfloat> & vertices, std::vector<GLuint> & indices);
int  split              (std::vector<GLfloat> & vertices, int v0, int v1);

#ifdef n2a_FP
                  SHARED void setVector (float target[], const Matrix<int> & value, int exponent);
#else
template<class T> SHARED void setVector (float target[], const Matrix<T>   & value);
#endif

#endif

// Utility functions to set material colors. May also be useful in other contexts.
                  SHARED void setColor (float target[], uint32_t            color, bool withAlpha);
template<class T> SHARED void setColor (float target[], const Matrix<T>   & color, bool withAlpha);
#ifdef n2a_FP
template<int>     SHARED void setColor (float target[], const Matrix<int> & color, bool withAlpha);  // exponent = 0, which gives full range [0,1]
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
    Matrix<float>                nextProjection;  // Initialized to 4x4, all zeros. If all zeros at start of 3D drawing, then we generate a default matrix based on current view size.
    Matrix<float>                nextView;        // Initialized to 4x4 identity, which is also the default.
    std::map<int,Light *>        lights;
    std::map<String,GLuint>      buffers;
    int                          sphereStep;
    std::vector<GLfloat>         sphereVertices;
    std::vector<GLuint>          sphereIndices;
#   endif

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
    Light * addLight    (int index);
    void    removeLight (int index);
    bool next3D (const Matrix<float> * model, const Material & material);  // Additional setup work done by 3D draw functions. Does both one-time initialization and per-frame initialization, as needed.
    GLuint getBuffer (String name, bool vertices);  // vertices==true indicates that this is a vertex array; vertices==false indicates that this is an index array
#     ifdef n2a_FP
    T drawCube     (T now, const Material & material, const Matrix<float> & model,       int exponentP);
    T drawCylinder (T now, const Material & material, const MatrixFixed<float,3,1> & p1, int exponentP, float r1, int exponentR, const MatrixFixed<float,3,1> & p2, float r2 = -1, int cap1 = 0, int cap2 = 0, int steps = 6, int stepsCap = -1);
    T drawPlane    (T now, const Material & material, const Matrix<float> & model,       int exponentP);
    T drawSphere   (T now, const Material & material, const Matrix<float> & model,       int exponentP, int steps = 1);
#     else
    T drawCube     (T now, const Material & material, const Matrix<float> & model);
    T drawCylinder (T now, const Material & material, const MatrixFixed<float,3,1> & p1, float r1, const MatrixFixed<float,3,1> & p2, float r2 = -1, int cap1 = 0, int cap2 = 0, int steps = 6, int stepsCap = -1);
    T drawPlane    (T now, const Material & material, const Matrix<float> & model);
    T drawSphere   (T now, const Material & material, const Matrix<float> & model, int steps = 1);
#     endif
#   endif
};
template<class T> SHARED ImageOutput<T> * imageOutputHelper (const String & fileName, ImageOutput<T> * oldHandle = 0);

template<class T>
class SHARED Mfile : public Holder
{
public:
    n2a::MDoc *                           doc;
    std::map<String,MatrixAbstract<T>*>   matrices;  // Could use unordered_map. Generally, there will be very few entries (like 1), so not sure which will cost the least.
    std::map<String,std::vector<String>*> childKeys;

    Mfile (const String & fileName);
    virtual ~Mfile ();

#   ifdef n2a_FP
    MatrixAbstract<T> * getMatrix (const char * delimiter, const std::vector<String> & path, int exponent);
#   else
    MatrixAbstract<T> * getMatrix (const char * delimiter, const std::vector<String> & path);
#   endif
    String getChildKey (const char * delimiter, const std::vector<String> & path, const int index);
};
template<class T> SHARED Mfile<T> * MfileHelper (const String & fileName, Mfile<T> * oldHandle = 0);

SHARED std::vector<String> keyPath (const char * delimiter, const std::vector<String> & path);  ///< Converts any path elements with delimiters into separate elements.
template<typename... Args> std::vector<String> keyPath (const char * delimiter, Args... keys) {return keyPath (delimiter, {keys...});}

/// Convert date to Unix time. Dates before epoch will be negative.
template<class T> SHARED T convertDate (const String & field, T defaultValue);

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
    int                            exponent;    ///< of value returned by get()
    int                            exponentRow; ///< of row value passed into get()
#   endif

    InputHolder (const String & fileName);
    virtual ~InputHolder ();

    void      getRow (T row); ///< subroutine of get()
    T         get    (T row, const String & column);
    T         get    (T row, T column);
    Matrix<T> get    (T row);
};
#ifdef n2a_FP
template<class T> SHARED InputHolder<T> * inputHelper (const String & fileName, int exponent, int exponentRow, InputHolder<T> * oldHandle = 0);
#else
template<class T> SHARED InputHolder<T> * inputHelper (const String & fileName,                                InputHolder<T> * oldHandle = 0);
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

    void trace (T now);  ///< Subroutine for other trace() functions.
    int  getColumnIndex (const String & column);  ///< Retrieves index of existing column, or creates new column.
    void setMode (int index, const char * mode, const char * lineSeparator = ",", const char * keySeparator = "=");  ///< Subroutine for other trace() functions.
#   ifdef n2a_FP
    T         trace (T now, const String & column, T                 value, int exponent, const char * mode = 0);
    Matrix<T> trace (T now, const String & column, const Matrix<T> & A,     int exponent, const char * mode = 0);
#   else
    T         trace (T now, const String & column, T                 value,               const char * mode = 0);
    Matrix<T> trace (T now, const String & column, const Matrix<T> & A,                   const char * mode = 0);
#   endif
    void writeTrace ();
    void writeModes ();
};
template<class T> SHARED OutputHolder<T> * outputHelper (const String & fileName, OutputHolder<T> * oldHandle = 0);


#endif
