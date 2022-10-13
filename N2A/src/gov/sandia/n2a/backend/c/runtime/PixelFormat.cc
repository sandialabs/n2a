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
#include "math.h"
#include "endian.h"

#include <algorithm>
#include <set>
#include <typeinfo>


// Include for tracing
//#include <iostream>


using namespace n2a;
using namespace std;


// Tables for packed YUV formats

PixelFormatPackedYUV::YUVindex tableUYVY[] =
{
  {1, 0, 2},
  {3, 0, 2},
  {-1}
};

PixelFormatPackedYUV::YUVindex tableYUYV[] =
{
  {0, 1, 3},
  {2, 1, 3},
  {-1}
};

PixelFormatPackedYUV::YUVindex tableUYV[] =
{
  {1, 0, 2},
  {-1}
};

PixelFormatPackedYUV::YUVindex tableUYYVYY[] =
{
  {1, 0, 3},
  {2, 0, 3},
  {4, 0, 3},
  {5, 0, 3},
  {-1}
};

PixelFormatPackedYUV::YUVindex tableUYVYUYVYYYYY[] =
{
  {1,  0, 2},
  {3,  0, 2},
  {5,  0, 2},
  {7,  0, 2},
  {8,  4, 6},
  {9,  4, 6},
  {10, 4, 6},
  {11, 4, 6},
  {-1}
};


PixelFormatGrayChar           n2a::GrayChar;
PixelFormatGrayAlphaChar      n2a::GrayAlphaChar;
PixelFormatGrayShort          n2a::GrayShort;
PixelFormatGrayShortSigned    n2a::GrayShortSigned;
PixelFormatGrayAlphaShort     n2a::GrayAlphaShort;
PixelFormatGrayFloat          n2a::GrayFloat;
PixelFormatGrayDouble         n2a::GrayDouble;
PixelFormatRGBAChar           n2a::RGBAChar;
PixelFormatRGBAShort          n2a::RGBAShort;
PixelFormatRGBAFloat          n2a::RGBAFloat;
PixelFormatRGBChar            n2a::RGBChar;
PixelFormatRGBShort           n2a::RGBShort;
PixelFormatPackedYUV          n2a::UYVY         (tableUYVY);
PixelFormatPackedYUV          n2a::YUYV         (tableYUYV);
PixelFormatPackedYUV          n2a::UYV          (tableUYV);
PixelFormatPackedYUV          n2a::UYYVYY       (tableUYYVYY);
PixelFormatPackedYUV          n2a::UYVYUYVYYYYY (tableUYVYUYVYYYYY);
PixelFormatPlanarYCbCr        n2a::YUV420 (2, 2);
PixelFormatPlanarYCbCr        n2a::YUV411 (4, 1);
PixelFormatHSLFloat           n2a::HSLFloat;
PixelFormatHSVFloat           n2a::HSVFloat;

// These "bits" formats must be endian independent.
#if BYTE_ORDER == LITTLE_ENDIAN
PixelFormatRGBABits n2a::B5G5R5   (2, 0x7C00,   0x3E0,    0x1F,       0x0);
PixelFormatRGBABits n2a::BGRChar  (3, 0xFF0000, 0xFF00,   0xFF,       0x0);
PixelFormatRGBABits n2a::BGRChar4 (4, 0xFF0000, 0xFF00,   0xFF,       0x0);
PixelFormatRGBABits n2a::BGRAChar (4, 0xFF0000, 0xFF00,   0xFF,       0xFF000000);
#elif BYTE_ORDER == BIG_ENDIAN
PixelFormatRGBABits n2a::B5G5R5   (2, 0x1F,     0x3E0,    0x7C00,     0x0);
PixelFormatRGBABits n2a::BGRChar  (3, 0xFF,     0xFF00,   0xFF0000,   0x0);
PixelFormatRGBABits n2a::BGRChar4 (4, 0xFF00,   0xFF0000, 0xFF000000, 0x0);
PixelFormatRGBABits n2a::BGRAChar (4, 0xFF00,   0xFF0000, 0xFF000000, 0xFF);
#else
// This traps unhandled endian types for the entire file, not just the "bits"
// formats.  The other BYTE_ORDER tests in this file can safely skip this case.
#  error This endian is not currently handled.
#endif

// Increment reference count for standard formats so they won't be destroyed
// when the last external reference releases them.
static int incrementRefcount ()
{
  GrayChar       .PointerPolyReferenceCount++;
  GrayAlphaChar  .PointerPolyReferenceCount++;
  GrayShort      .PointerPolyReferenceCount++;
  GrayShortSigned.PointerPolyReferenceCount++;
  GrayAlphaShort .PointerPolyReferenceCount++;
  GrayFloat      .PointerPolyReferenceCount++;
  GrayDouble     .PointerPolyReferenceCount++;
  RGBAChar       .PointerPolyReferenceCount++;
  RGBAShort      .PointerPolyReferenceCount++;
  RGBAFloat      .PointerPolyReferenceCount++;
  RGBChar        .PointerPolyReferenceCount++;
  RGBShort       .PointerPolyReferenceCount++;
  UYVY           .PointerPolyReferenceCount++;
  YUYV           .PointerPolyReferenceCount++;
  UYV            .PointerPolyReferenceCount++;
  UYYVYY         .PointerPolyReferenceCount++;
  UYVYUYVYYYYY   .PointerPolyReferenceCount++;
  YUV420         .PointerPolyReferenceCount++;
  YUV411         .PointerPolyReferenceCount++;
  HSLFloat       .PointerPolyReferenceCount++;
  HSVFloat       .PointerPolyReferenceCount++;
  B5G5R5         .PointerPolyReferenceCount++;
  BGRChar        .PointerPolyReferenceCount++;
  BGRChar4       .PointerPolyReferenceCount++;
  BGRAChar       .PointerPolyReferenceCount++;

  return 1;
}
static int refcountIncremented = incrementRefcount ();


// Color->gray conversion factors
// Make these user modfiable if at some point it turns out to be useful.
// First used: (54 183 19) / 256, same as linear sRGB below
// Linear sRGB to Y: 0.2126 0.7152 0.0722
// NTSC, PAL, and JPEG: 0.2989 0.5866 0.1145, produces a non-linear gray-value, appropriate for non-linear sRGB, which is our assumed RGB format
#define redWeight    76
#define greenWeight 150
#define blueWeight   29
#define totalWeight 255
#define redToY   0.2126
#define greenToY 0.7152
#define blueToY  0.0722

// YUV <-> RGB conversion matrices are specified by the standards in terms of
// non-linear RGB.  IE: even though the conversion matrices are linear ops,
// they work on non-linear inputs.  Therefore, even though YUV is essentially
// non-linear, it should not be linearized until after it is converted into
// RGB.  The matrices output non-linear RGB.

// R = Y           +1.4022*V
// G = Y -0.3456*U -0.7145*V
// B = Y +1.7710*U

// Y =  0.2989*R +0.5866*G +0.1145*B
// U = -0.1687*R -0.3312*G +0.5000*B
// V =  0.5000*R -0.4183*G -0.0816*B


// Structure used for converting odd stride (ie: stride = 3 bytes)
union Int2Char
{
  uint32_t  all;
  struct
  {
#   if BYTE_ORDER == LITTLE_ENDIAN
	uint8_t piece0;
	uint8_t piece1;
	uint8_t piece2;
	uint8_t barf;
#   elif BYTE_ORDER == BIG_ENDIAN
	uint8_t barf;
	uint8_t piece0;
	uint8_t piece1;
	uint8_t piece2;
#   endif
  };
};


// Support for shifting and scaling bit channels ------------------------------

/**
   Shift a 32-bit word up or down by a given number of bits.

   Timing on the pixel format test in test.cc shows that there is no single
   implementation of this function that works best for all conversion routines,
   at least under GCC.  The possible implementations tested are:
   inline assembly, inline C, macro.

   @param value The 32-bit word to be rotated.
   @param shift The number of bits to rotate.  Positive means towards the most
   significant end of the word ("left"), and negative means towards the least
   significant end.  You should limit this value to [-31,31] (although the
   x86 does take the modulus of the value in hardware).
 **/
static inline uint32_t
roll (uint32_t value, int shift)
{
# if defined (__GNUC__)  &&  defined (__i386__)

  uint32_t result;
  __asm (
		 "roll %%cl, %0\n"
		 : "=g" (result)
		 : "c" (shift), "0" (value)
		 :
		 );
  return result;

# elif defined (_MSC_VER)  &&  defined (_M_IX86)

  // TODO: benchmark this as well, to make sure asm is really helping
  __asm
  {
	mov eax, value
	mov ecx, shift
	rol eax, cl
  }
  // return with result in eax

# else

  //value &= 0x1F;
  //return value << shift | value >> 32 - shift;
  return shift > 0 ? value << shift : value >> -shift;

# endif
}

//#define roll(value,shift) ((shift) > 0 ? (value) << (shift) : (value) >> -(shift))
//#define rollback(value,shift) ((shift) > 0 ? (value) >> (shift) : (value) << -(shift))

/**
   Multiply a 32-bit word by a given value designed to lay down multiple
   copies of a subset of its bits, and then shift it up or down by a given
   number of bits.  The idea is to expand a channel with fewer bits to a
   channel with more bits in a way that spreads the quantized values across
   the entire range of the larger channel.  This is equivalent to rescaling
   the value by (2^{more bits} - 1) / (2^{fewer bits} - 1), however, only a
   single multiply with a carefully selected value is necessary.

   @param value The 32-bit word to be rotated.
   @param factor The value by which to multiply a.  This should consist of a
   series of 1 bits separated by (fewer bits - 1) zero bits.  The most
   significant 1 should be in the MSB of the word.  @see dublicateTable.
   @param shift The number of bits to down-shift the result of the
   multiplication.  Should be in [0,31].
 **/
static inline uint32_t
dublicate (uint32_t value, uint32_t factor, int shift)
{
  return ((uint64_t) value * factor) >> shift;
}

uint32_t dublicateTable[] = 
{
  0x80000000,  // 0 bits (never used)
  0xFFFFFFFF,  // 1
  0xAAAAAAAA,  // 2
  0x92492492,  // 3
  0x88888888,  // 4
  0x84210842,  // 5
  0x82082082,  // 6
  0x81020408,  // 7
  0x80808080,  // 8
  0x80402010,  // 9
  0x80200802,  // 10
  0x80100200,  // 11
  0x80080080,  // 12
  0x80040020,  // 13
  0x80020008,  // 14
  0x80010002,  // 15
  0x80008000,  // 16
  0x80004000,  // 17
  0x80002000,  // 18
  0x80001000,  // 19
  0x80000800,  // 20
  0x80000400,  // 21
  0x80000200,  // 22
  0x80000100,  // 23
  0x80000080,  // 24
  0x80000040,  // 25
  0x80000020,  // 26
  0x80000010,  // 27
  0x80000008,  // 28
  0x80000004,  // 29
  0x80000002,  // 30
  0x80000001,  // 31
  0x80000000,  // 32
};

static inline uint32_t
prepareDublicate (int & shift, int bits)
{
  shift = (31 - shift) & 0x3F;
  return dublicateTable[bits];
}


// class PixelFormat ----------------------------------------------------------

uint8_t * PixelFormat::lutFloat2Char = PixelFormat::buildFloat2Char ();
float *   PixelFormat::lutChar2Float = PixelFormat::buildChar2Float ();

PixelFormat::~PixelFormat ()
{
}

Image
PixelFormat::filter (const Image & image)
{
  Image result (*this);

  if (*image.format == *this)
  {
	result = image;
	return result;
  }

  result.resize (image.width, image.height);
  result.timestamp = image.timestamp;
  if (result.width <= 0  ||  result.height <= 0) return result;

  fromAny (image, result);

  return result;
}

/**
   Uses RGBAChar as a central format.  XYZ would be more accurate, but this
   is also adequate, since RGB values are well defined (as non-linear sRGB).
**/
void
PixelFormat::fromAny (const Image & image, Image & result) const
{
  // First convert to central format.
  Image central = image * RGBAChar;

  // Then conver to destination format.
  PixelBufferPacked * i = (PixelBufferPacked *) central.buffer;
  PixelBufferPacked * o = (PixelBufferPacked *) result.buffer;
  assert (i);
  uint32_t * source = (uint32_t *) i->base ();
  const int  step   = i->stride - central.width * sizeof (uint32_t);
  if (o)
  {
	const int destDepth = (int) depth;
	uint8_t * dest      = (uint8_t *) o->base ();
	uint8_t * end       = dest + o->stride * result.height;
	uint8_t * rowEnd    = dest + result.width * destDepth;
	while (dest < end)
	{
	  while (dest < rowEnd)
	  {
#       if BYTE_ORDER == LITTLE_ENDIAN
		setRGBA (dest, bswap(*source++));
#       elif BYTE_ORDER == BIG_ENDIAN
		setRGBA (dest, *source++);
#       endif
		dest += destDepth;
	  }
	  source = (uint32_t *) ((char *) source + step);
	  rowEnd += o->stride;
	}
  }
  else
  {
	for (int y = 0; y < image.height; y++)
	{
	  for (int x = 0; x < image.width; x++)
	  {
#       if BYTE_ORDER == LITTLE_ENDIAN
		setRGBA (result.buffer->pixel (x, y), bswap(*source++));
#       elif BYTE_ORDER == BIG_ENDIAN
		setRGBA (result.buffer->pixel (x, y), *source++);
#       endif
	  }
	  source = (uint32_t *) ((char *) source + step);
	}
  }
}

PixelBuffer *
PixelFormat::buffer () const
{
  if (planes == 1)
  {
	return new PixelBufferPacked;
  }
  else if (planes == 3)
  {
	return new PixelBufferPlanar;
  }
  else if (planes == -1)
  {
	const Macropixel * m = dynamic_cast<const Macropixel *> (this);
	if (!m) throw "Specified a 'groups' style buffer, but not a Macropixel format.";
	return new PixelBufferGroups (m->pixels, m->bytes);
  }
  else
  {
	throw "Need to override default PixelFormat::buffer()";
  }
}

PixelBuffer *
PixelFormat::attach (void * block, int width, int height, bool copy) const
{
  PixelBufferPacked * result = new PixelBufferPacked (block, width * (int) depth, height, (int) depth);
  if (copy) result->memory.copyFrom (result->memory);
  return result;
}

bool
PixelFormat::operator == (const PixelFormat & that) const
{
  return typeid (*this) == typeid (that);
}

void
PixelFormat::getRGBA (void * pixel, float values[]) const
{
  uint32_t rgba = getRGBA (pixel);
  values[0] = lutChar2Float[(rgba & 0xFF000000) >> 24];
  values[1] = lutChar2Float[(rgba &   0xFF0000) >> 16];
  values[2] = lutChar2Float[(rgba &     0xFF00) >>  8];
  values[3] = (rgba & 0xFF) / 255.0f;  // Don't linearize alpha, because already linear
}

void
PixelFormat::getXYZ (void * pixel, float values[]) const
{
  float rgbValues[4];
  getRGBA (pixel, rgbValues);

  // Matrix multiply to cast into XYZ space
  values[0] = 0.4124564f * rgbValues[0] + 0.3575761f * rgbValues[1] + 0.1804375f * rgbValues[2];
  values[1] = 0.2126729f * rgbValues[0] + 0.7151522f * rgbValues[1] + 0.0721750f * rgbValues[2];
  values[2] = 0.0193339f * rgbValues[0] + 0.1191920f * rgbValues[1] + 0.9503041f * rgbValues[2];
}

/**
   See PixelFormatUYVY::setRGBA() for more details on the conversion matrix.
 **/
uint32_t
PixelFormat::getYUV  (void * pixel) const
{
  uint32_t rgba = getRGBA (pixel);

  int32_t r = (rgba & 0xFF000000) >> 24;
  int32_t g = (rgba &   0xFF0000) >> 16;
  int32_t b = (rgba &     0xFF00) >>  8;

  uint32_t y = min (max (  0x4C84 * r + 0x962B * g + 0x1D4F * b            + 0x8000, 0), 0xFFFFFF) & 0xFF0000;
  uint32_t u = min (max (- 0x2B2F * r - 0x54C9 * g + 0x8000 * b + 0x800000 + 0x8000, 0), 0xFFFFFF) & 0xFF0000;
  uint32_t v = min (max (  0x8000 * r - 0x6B15 * g - 0x14E3 * b + 0x800000 + 0x8000, 0), 0xFFFFFF) & 0xFF0000;

  return y | (u >> 8) | (v >> 16);
}

void
PixelFormat::getHSL (void * pixel, float values[]) const
{
  float rgba[4];
  getRGBA (pixel, rgba);
  HSLFloat.setRGBA (values, rgba);
}

void
PixelFormat::getHSV (void * pixel, float values[]) const
{
  float rgba[4];
  getRGBA (pixel, rgba);
  HSVFloat.setRGBA (values, rgba);
}

uint8_t
PixelFormat::getGray (void * pixel) const
{
  uint32_t rgba = getRGBA (pixel);
  uint32_t r = (rgba & 0xFF000000) >> 16;
  uint32_t g = (rgba &   0xFF0000) >>  8;
  uint32_t b = (rgba &     0xFF00);
  return ((redWeight * r + greenWeight * g + blueWeight * b) / totalWeight + 0x80) >> 8;
}

void
PixelFormat::getGray (void * pixel, float & gray) const
{
  gray = lutChar2Float[getGray (pixel)];
}

uint8_t
PixelFormat::getAlpha (void * pixel) const
{
  uint32_t rgba = getRGBA (pixel);
  return rgba & 0xFF;
}

void
PixelFormat::setRGBA (void * pixel, float values[]) const
{
  uint32_t rgba = (uint32_t) (values[3] * 255);
  rgba |= lutFloat2Char[(uint32_t) (65535 * min (max (values[0], 0.0f), 1.0f))] << 24;
  rgba |= lutFloat2Char[(uint32_t) (65535 * min (max (values[1], 0.0f), 1.0f))] << 16;
  rgba |= lutFloat2Char[(uint32_t) (65535 * min (max (values[2], 0.0f), 1.0f))] <<  8;
  setRGBA (pixel, rgba);
}

void
PixelFormat::setXYZ (void * pixel, float values[]) const
{
  // Don't clamp XYZ values

  // Do matrix multiply to get linear RGB values
  float rgbValues[4];
  rgbValues[0] =  3.2404542f * values[0] - 1.5371385f * values[1] - 0.4985314f * values[2];
  rgbValues[1] = -0.9692660f * values[0] + 1.8760108f * values[1] + 0.0415560f * values[2];
  rgbValues[2] =  0.0556434f * values[0] - 0.2040259f * values[1] + 1.0572252f * values[2];
  rgbValues[3] = 1.0f;

  setRGBA (pixel, rgbValues);
}

void
PixelFormat::setYUV (void * pixel, uint32_t yuv) const
{
  // It is possible to pass a value where Y = 0 but U and V are not zero.
  // Technically, this is an illegal value.  However, this code doesn't
  // trap that case, so it can generate bogus RGB values when the pixel
  // should be black.

  int32_t y =   yuv & 0xFF0000;
  int32_t u = ((yuv &   0xFF00) >> 8) - 128;
  int32_t v =  (yuv &     0xFF)       - 128;

  // See PixelFormatUYVY::getRGBA() for an explanation of this arithmetic.
  uint32_t r = min (max (y               + 0x166F7 * v + 0x8000, 0), 0xFFFFFF);
  uint32_t g = min (max (y -  0x5879 * u -  0xB6E9 * v + 0x8000, 0), 0xFFFFFF);
  uint32_t b = min (max (y + 0x1C560 * u               + 0x8000, 0), 0xFFFFFF);

  setRGBA (pixel, ((r << 8) & 0xFF000000) | (g & 0xFF0000) | ((b >> 8) & 0xFF00) | 0xFF);
}

void
PixelFormat::setHSL (void * pixel, float values[]) const
{
  float rgba[4];
  HSLFloat.getRGBA (values, rgba);
  setRGBA (pixel, rgba);
}

void
PixelFormat::setHSV (void * pixel, float values[]) const
{
  float rgba[4];
  HSVFloat.getRGBA (values, rgba);
  setRGBA (pixel, rgba);
}

void
PixelFormat::setGray (void * pixel, uint8_t gray) const
{
  register uint32_t iv = gray;
  setRGBA (pixel, (iv << 24) | (iv << 16) | (iv << 8) | 0xFF);
}

void
PixelFormat::setGray (void * pixel, float gray) const
{
  gray = min (max (gray, 0.0f), 1.0f);
  uint32_t iv = lutFloat2Char[(uint16_t) (65535 * gray)];
  uint32_t rgba = (iv << 24) | (iv << 16) | (iv << 8) | 0xFF;
  setRGBA (pixel, rgba);
}

void
PixelFormat::setAlpha (void * pixel, uint8_t alpha) const
{
  uint32_t rgba = getRGBA (pixel);
  rgba = (rgba & 0xFFFFFF00) | alpha;
  setRGBA (pixel, rgba);
}

void
PixelFormat::blend (void * pixel, uint32_t rgba) const
{
  uint32_t p = getRGBA (pixel);
  alphaBlend (rgba, p);
  setRGBA (pixel, p);
}

void
PixelFormat::blend (void * pixel, float values[]) const
{
  float p[4];
  getRGBA (pixel, p);
  alphaBlend (values, p);
  setRGBA (pixel, p);
}

inline uint8_t *
PixelFormat::buildFloat2Char ()
{
  uint8_t * result = (uint8_t *) malloc (65536);
  for (int i = 0; i < 65536; i++)
  {
	double f = i / 65535.0;

	// For small numbers, use linear approximation.  sRGB says that some
	// systems can't handle these small pow() computations accurately.
	if (f <= 0.0031308)
	{
	  f *= 12.92;
	}
	else
	{
	  f = 1.055 * pow (f, 1.0 / 2.4) - 0.055;
	}

	result[i] = (uint8_t) roundp (f * 255);
  }
  return result;
}

inline float *
PixelFormat::buildChar2Float ()
{
  float * result = (float *) malloc (256 * sizeof (float));
  for (int i = 0; i < 256; i++)
  {
	double f = i / 255.0;

	if (f <= 0.04045)
	{
	  f /= 12.92;
	}
	else
	{
	  f = pow ((f + 0.055) / 1.055, 2.4);
	}

	result[i] = f;
  }
  return result;
}


// class PixelFormatPalette ---------------------------------------------------

PixelFormatPalette::PixelFormatPalette (uint8_t * r, uint8_t * g, uint8_t * b, int stride, int bits, bool bigendian)
{
  planes     = -1;
  depth      = bits / 8.0f;
  precedence = 0;  // Below everything.
  monochrome = true;  // will be evaluated as palette is scanned
  hasAlpha   = false;
  this->bits = bits;
  bytes      = 1;
  pixels     = 8 / bits;

  // build masks
  uint8_t mask = 0x1;
  for (int i = 1; i < bits; i++) mask |= mask << 1;

  int i = bigendian ? pixels - 1 : 0;
  int step = bigendian ? -1 : 1;
  int shift = 0;
  while (mask)
  {
	masks[i] = mask;
	shifts[i] = shift;
	mask <<= bits;
	shift += bits;
	i += step;
  }

  // Build palette
  if (r  &&  g  &&  b)
  {
	uint32_t * p   = palette;
	uint32_t * end = p + (0x1 << bits);
	while (p < end)
	{
	  *p++ = (*r << 24) | (*g << 16) | (*b << 8) | 0xFF;
	  if (*r != *g  ||  *g != *b) monochrome = false;
	  r += stride;
	  g += stride;
	  b += stride;
	}
  }
}

PixelBuffer *
PixelFormatPalette::attach (void * block, int width, int height, bool copy) const
{
  PixelBufferGroups * result = new PixelBufferGroups (block, (int) ceil ((float) width / pixels), height, pixels, bytes);
  if (copy) result->memory.copyFrom (result->memory);
  return result;
}

bool
PixelFormatPalette::operator == (const PixelFormat & that) const
{
  const PixelFormatPalette * other = dynamic_cast<const PixelFormatPalette *> (&that);
  if (! other) return false;
  if (bits != other->bits) return false;

  const uint32_t * o   = other->palette;
  const uint32_t * p   = palette;
  const uint32_t * end = p + (0x1 << bits);
  while (p < end)
  {
	if (*o++ != *p++) return false;
  }
  return true;
}

uint32_t
PixelFormatPalette::getRGBA  (void * pixel) const
{
  PixelBufferGroups::PixelData * data = (PixelBufferGroups::PixelData *) pixel;
  uint32_t index = (*data->address & masks[data->index]) >> shifts[data->index];
  return palette[index];
}

void
PixelFormatPalette::setRGBA  (void * pixel, uint32_t rgba) const
{
  // Naive linear search for closest color
  int r =  rgba             >> 24;
  int g = (rgba & 0xFF0000) >> 16;
  int b = (rgba &   0xFF00) >>  8;

  const uint32_t * p         = palette;
  const uint32_t * end       = p + 256;
  const uint32_t * bestEntry = p;
  int smallestDifference = INT_MAX;
  while (p < end)
  {
	// Compute difference
	int pr =  *p             >> 24;
	int pg = (*p & 0xFF0000) >> 16;
	int pb = (*p &   0xFF00) >>  8;
	int difference = redWeight * abs (pr - r) + greenWeight * abs (pg - g) + blueWeight * abs (pb - b);

	// Compare
	if (difference < smallestDifference)
	{
	  smallestDifference = difference;
	  bestEntry = p;
	}

	p++;
  }
  uint8_t index = bestEntry - palette;

  // Write the resulting palette index into the buffer
  PixelBufferGroups::PixelData * data = (PixelBufferGroups::PixelData *) pixel;
  uint8_t m = masks[data->index];
  *data->address = (*data->address & ~m) | ((index << shifts[data->index]) & m);
}


// class PixelFormatGrayBits --------------------------------------------------

PixelFormatGrayBits::PixelFormatGrayBits (int bits, bool bigendian)
{
  planes     = -1;
  depth      = bits / 8.0f;
  precedence = 0;  // Below everything.  Should actually be lower than GrayChar, but plan to replace this ordering system anyway.
  monochrome = true;
  hasAlpha   = false;
  this->bits = bits;
  bytes      = 1;
  pixels     = 8 / bits;

  // build masks

  uint8_t mask = 0x1;
  for (int i = 1; i < bits; i++) mask |= mask << 1;

  int i = bigendian ? pixels - 1 : 0;
  int step = bigendian ? -1 : 1;
  int shift = 8 - bits;
  while (mask)
  {
	masks[i] = mask;
	shifts[i] = shift;
	mask <<= bits;
	shift -= bits;
	i += step;
  }
}

PixelBuffer *
PixelFormatGrayBits::attach (void * block, int width, int height, bool copy) const
{
  PixelBufferGroups * result = new PixelBufferGroups (block, (int) ceil ((float) width / pixels), height, pixels, 1);
  if (copy) result->memory.copyFrom (result->memory);
  return result;
}

bool
PixelFormatGrayBits::operator == (const PixelFormat & that) const
{
  const PixelFormatGrayBits * other = dynamic_cast<const PixelFormatGrayBits *> (&that);
  if (! other) return false;
  if (bits != other->bits) return false;

  const uint8_t * o   = other->masks;
  const uint8_t * m   = masks;
  const uint8_t * end = m + pixels;
  while (m < end)
  {
	if (*o++ != *m++) return false;
  }
  return true;
}

uint32_t
PixelFormatGrayBits::getRGBA  (void * pixel) const
{
  PixelBufferGroups::PixelData * data = (PixelBufferGroups::PixelData *) pixel;

  uint32_t t = (*data->address & masks[data->index]) << shifts[data->index];
  t = dublicate (t, dublicateTable[bits], 31);

  return (t << 24) | (t << 16) | (t << 8) | 0xFF;
}

void
PixelFormatGrayBits::setRGBA  (void * pixel, uint32_t rgba) const
{
  uint32_t r = (rgba & 0xFF000000) >> 16;
  uint32_t g = (rgba &   0xFF0000) >>  8;
  uint32_t b =  rgba &     0xFF00;
  uint8_t  t = ((r * redWeight + g * greenWeight + b * blueWeight) / totalWeight + 0x80) >> 8;

  PixelBufferGroups::PixelData * data = (PixelBufferGroups::PixelData *) pixel;
  uint8_t m = masks[data->index];
  *data->address = (*data->address & ~m) | ((t >> shifts[data->index]) & m);
}


// class PixelFormatGrayChar --------------------------------------------------

PixelFormatGrayChar::PixelFormatGrayChar ()
{
  planes     = 1;
  depth      = 1;
  precedence = 0;  // Below everything
  monochrome = true;
  hasAlpha   = false;
}

Image
PixelFormatGrayChar::filter (const Image & image)
{
  Image result (*this);

  if (*image.format == *this)
  {
	result = image;
	return result;
  }

  result.resize (image.width, image.height);
  result.timestamp = image.timestamp;
  if (result.width <= 0  ||  result.height <= 0) return result;

  if (typeid (* image.format) == typeid (PixelFormatGrayShort))
  {
	fromGrayShort (image, result);
  }
  else if (typeid (* image.format) == typeid (PixelFormatGrayFloat))
  {
	fromGrayFloat (image, result);
  }
  else if (typeid (* image.format) == typeid (PixelFormatGrayDouble))
  {
	fromGrayDouble (image, result);
  }
  else if (typeid (* image.format) == typeid (PixelFormatRGBAChar))
  {
	fromRGBAChar (image, result);
  }
  else if ((const PixelFormatRGBABits *) image.format)
  {
	fromRGBABits (image, result);
  }
  else if (typeid (* image.format) == typeid (PixelFormatPlanarYCbCr))
  {
	fromYCbCr (image, result);
  }
  else
  {
	fromAny (image, result);
  }

  return result;
}

void
PixelFormatGrayChar::fromGrayShort (const Image & image, Image & result) const
{
  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  PixelBufferPacked * o = (PixelBufferPacked *) result.buffer;
  assert (i  &&  o);

  uint16_t * fromPixel = (uint16_t *) i->base ();
  uint8_t *  toPixel   = (uint8_t *)  o->base ();
  uint8_t *  end       = toPixel + result.width * result.height;
  const int grayShift = ((const PixelFormatGrayShort *) image.format)->grayShift;
  const int step = i->stride - image.width * sizeof (short);
  while (toPixel < end)
  {
	uint8_t * rowEnd = toPixel + result.width;
	while (toPixel < rowEnd)
	{
	  *toPixel++ = lutFloat2Char[*fromPixel++ << grayShift];
	}
	fromPixel = (uint16_t *) ((char *) fromPixel + step);
  }
}

void
PixelFormatGrayChar::fromGrayFloat (const Image & image, Image & result) const
{
  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  PixelBufferPacked * o = (PixelBufferPacked *) result.buffer;
  assert (i  &&  o);

  float *   fromPixel = (float *)   i->base ();
  uint8_t * toPixel   = (uint8_t *) o->base ();
  uint8_t * end       = toPixel + result.width * result.height;
  const int step = i->stride - image.width * sizeof (float);
  while (toPixel < end)
  {
	uint8_t * rowEnd = toPixel + result.width;
	while (toPixel < rowEnd)
	{
	  float p = min (max (*fromPixel++, 0.0f), 1.0f);
	  *toPixel++ = lutFloat2Char[(uint16_t) (65535 * p)];
	}
	fromPixel = (float *) ((char *) fromPixel + step);
  }
}

void
PixelFormatGrayChar::fromGrayDouble (const Image & image, Image & result) const
{
  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  PixelBufferPacked * o = (PixelBufferPacked *) result.buffer;
  assert (i  &&  o);

  double *  fromPixel = (double *)  i->base ();
  uint8_t * toPixel   = (uint8_t *) o->base ();
  uint8_t * end       = toPixel + result.width * result.height;
  const int step = i->stride - image.width * sizeof (double);
  while (toPixel < end)
  {
	uint8_t * rowEnd = toPixel + result.width;
	while (toPixel < rowEnd)
	{
	  double p = min (max (*fromPixel++, 0.0), 1.0);
	  *toPixel++ = lutFloat2Char[(uint16_t) (65535 * p)];
	}
	fromPixel = (double *) ((char *) fromPixel + step);
  }
}

void
PixelFormatGrayChar::fromRGBAChar (const Image & image, Image & result) const
{
  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  PixelBufferPacked * o = (PixelBufferPacked *) result.buffer;
  assert (i  &&  o);

  uint8_t * fromPixel = (uint8_t *) i->base ();
  uint8_t * toPixel   = (uint8_t *) o->base ();
  uint8_t * end       = toPixel + result.width * result.height;
  const int step = i->stride - image.width * 4;
  while (toPixel < end)
  {
	uint8_t * rowEnd = toPixel + result.width;
	while (toPixel < rowEnd)
	{
	  uint32_t t;
	  t  = fromPixel[0] * (redWeight   << 8);
	  t += fromPixel[1] * (greenWeight << 8);
	  t += fromPixel[2] * (blueWeight  << 8);
	  fromPixel += 4;
	  *toPixel++ = (t / totalWeight + 0x80) >> 8;
	}
	fromPixel += step;
  }
}

void
PixelFormatGrayChar::fromRGBABits (const Image & image, Image & result) const
{
  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  PixelBufferPacked * o = (PixelBufferPacked *) result.buffer;
  assert (i  &&  o);

  const PixelFormatRGBABits * that = (const PixelFormatRGBABits *) image.format;

  const uint32_t grayMask = 0xFF00;
  int redShift;
  int greenShift;
  int blueShift;
  int alphaShift;
  that->shift (grayMask, grayMask, grayMask, grayMask, redShift, greenShift, blueShift, alphaShift);
  uint32_t redFactor   = prepareDublicate (redShift,   that->redBits);
  uint32_t greenFactor = prepareDublicate (greenShift, that->greenBits);
  uint32_t blueFactor  = prepareDublicate (blueShift,  that->blueBits);

  #define RGBBits2GrayChar(fromSize) \
  { \
    fromSize * fromPixel = (fromSize *) i->base (); \
    uint8_t  * toPixel   = (uint8_t *)  o->base (); \
    uint8_t  * end       = toPixel + result.width * result.height; \
	const int step = i->stride - image.width * sizeof (fromSize); \
    while (toPixel < end) \
    { \
	  uint8_t * rowEnd = toPixel + result.width; \
	  while (toPixel < rowEnd) \
	  { \
		uint32_t r = *fromPixel & that->redMask; \
		uint32_t g = *fromPixel & that->greenMask; \
		uint32_t b = *fromPixel & that->blueMask; \
		fromPixel++; \
		*toPixel++ = ((  (dublicate (r, redFactor,   redShift)   & grayMask) * redWeight \
					   + (dublicate (g, greenFactor, greenShift) & grayMask) * greenWeight \
					   + (dublicate (b, blueFactor,  blueShift)  & grayMask) * blueWeight \
					  ) / totalWeight + 0x80) >> 8; \
	  } \
	  fromPixel = (fromSize *) ((char *) fromPixel + step); \
    } \
  }

  switch ((int) that->depth)
  {
    case 1:
	  RGBBits2GrayChar (uint8_t);
	  break;
    case 2:
	  RGBBits2GrayChar (uint16_t);
	  break;
    case 3:
	{
	  const int step = i->stride - image.width * 3;
#     if BYTE_ORDER == LITTLE_ENDIAN
	  uint8_t * fromPixel = (uint8_t *) i->base () + i->stride * image.height - step - 3;
	  uint8_t * begin     = (uint8_t *) o->base ();
	  uint8_t * toPixel   = begin + result.width * result.height;
	  uint8_t * rowBegin  = toPixel - result.width;
	  // process last pixel
	  toPixel--;
	  Int2Char t;
	  t.piece0 = fromPixel[0];
	  t.piece1 = fromPixel[1];
	  t.piece2 = fromPixel[2];
	  fromPixel -= 3;
	  uint32_t r = t.all & that->redMask;
	  uint32_t g = t.all & that->greenMask;
	  uint32_t b = t.all & that->blueMask;
	  *toPixel-- = ((  (dublicate (r, redFactor,   redShift)   & grayMask) * redWeight
					 + (dublicate (g, greenFactor, greenShift) & grayMask) * greenWeight
					 + (dublicate (b, blueFactor,  blueShift)  & grayMask) * blueWeight
					) / totalWeight + 0x80) >> 8;
	  // process other pixels
	  while (toPixel >= begin)
	  {
		while (toPixel >= rowBegin)
		{
		  uint32_t t = * (uint32_t *) fromPixel;
		  fromPixel -= 3;
		  r = t & that->redMask;
		  g = t & that->greenMask;
		  b = t & that->blueMask;
		  *toPixel-- = ((  (dublicate (r, redFactor,   redShift)   & grayMask) * redWeight
						 + (dublicate (g, greenFactor, greenShift) & grayMask) * greenWeight
						 + (dublicate (b, blueFactor,  blueShift)  & grayMask) * blueWeight
						) / totalWeight + 0x80) >> 8;
		}
		fromPixel -= step;
		rowBegin -= result.width;
	  }
#     elif BYTE_ORDER == BIG_ENDIAN
	  uint8_t * fromPixel = (uint8_t *) i->base ();
	  uint8_t * toPixel   = (uint8_t *) o->base ();
	  uint8_t * end       = toPixel + result.width * result.height;
	  uint8_t * rowEnd    = toPixel + result.width;
	  // process first pixel
	  Int2Char t;
	  t.piece0 = fromPixel[0];
	  t.piece1 = fromPixel[1];
	  t.piece2 = fromPixel[2];
	  fromPixel += 2;  // only 2, because big-endian pointer is set back by 1
	  uint32_t r = t.all & that->redMask;
	  uint32_t g = t.all & that->greenMask;
	  uint32_t b = t.all & that->blueMask;
	  *toPixel++ = ((  (dublicate (r, redFactor,   redShift)   & grayMask) * redWeight
					 + (dublicate (g, greenFactor, greenShift) & grayMask) * greenWeight
					 + (dublicate (b, blueFactor,  blueShift)  & grayMask) * blueWeight
					) / totalWeight + 0x80) >> 8;
	  // process other pixels
	  while (toPixel < end)
	  {
		while (toPixel < rowEnd)
		{
		  uint32_t t = * (uint32_t *) fromPixel;
		  fromPixel += 3;
		  r = t & that->redMask;
		  g = t & that->greenMask;
		  b = t & that->blueMask;
		  *toPixel++ = ((  (dublicate (r, redFactor,   redShift)   & grayMask) * redWeight
						 + (dublicate (g, greenFactor, greenShift) & grayMask) * greenWeight
						 + (dublicate (b, blueFactor,  blueShift)  & grayMask) * blueWeight
						) / totalWeight + 0x80) >> 8;
		}
		fromPixel += step;
		rowEnd += result.width;
	  }
#     endif
	  break;
	}
    case 4:
    default:
	  RGBBits2GrayChar (uint32_t);
  }
}

void
PixelFormatGrayChar::fromYCbCr (const Image & image, Image & result) const
{
  PixelBufferPlanar * i = (PixelBufferPlanar *) image.buffer;
  PixelBufferPacked * o = (PixelBufferPacked *) result.buffer;
  assert (i  &&  o);

  uint8_t * fromPixel = (uint8_t *) i->plane0;
  uint8_t * toPixel   = (uint8_t *) o->base ();
  uint8_t * end       = toPixel + result.width * result.height;
  const int step = i->stride0 - image.width;
  while (toPixel < end)
  {
	uint8_t * rowEnd = toPixel + result.width;
	while (toPixel < rowEnd)
	{
	  *toPixel++ = PixelFormatPlanarYCbCr::lutYout[*fromPixel++];
	}
	fromPixel += step;
  }
}

void
PixelFormatGrayChar::fromAny (const Image & image, Image & result) const
{
  uint8_t * dest = (uint8_t *) ((PixelBufferPacked *) result.buffer)->base ();
  const PixelFormat * sourceFormat = image.format;

  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  if (i)
  {
	uint8_t * source      = (uint8_t *) i->base ();
	const int sourceDepth = (int) sourceFormat->depth;
	const int step        = i->stride - image.width * sourceDepth;
	uint8_t * end         = dest + result.width * result.height;
	while (dest < end)
	{
	  uint8_t * rowEnd = dest + result.width;
	  while (dest < rowEnd)
	  {
		*dest++ = sourceFormat->getGray (source);
		source += sourceDepth;
	  }
	  source += step;
	}
  }
  else
  {
	for (int y = 0; y < image.height; y++)
	{
	  for (int x = 0; x < image.width; x++)
	  {
		*dest++ = sourceFormat->getGray (image.buffer->pixel (x, y));
	  }
	}
  }
}

bool
PixelFormatGrayChar::operator == (const PixelFormat & that) const
{
  if (typeid (*this) == typeid (that))
  {
	return true;
  }
  if (const PixelFormatRGBABits * other = dynamic_cast<const PixelFormatRGBABits *> (& that))
  {
	return    other->depth     == depth
	       && other->redMask   == 0xFF
	       && other->greenMask == 0xFF
	       && other->blueMask  == 0xFF;
	// We don't care about contents of alpha mask.
  }
  return false;
}

uint32_t
PixelFormatGrayChar::getRGBA (void * pixel) const
{
  uint32_t t = *((uint8_t *) pixel);
  return (t << 24) | (t << 16) | (t << 8) | 0xFF;
}

void
PixelFormatGrayChar::getXYZ (void * pixel, float values[]) const
{
  float t = lutChar2Float[*(uint8_t *) pixel];
  values[0] = 0.950470f * t;
  values[1] =             t;
  values[2] = 1.088830f * t;
}

uint8_t
PixelFormatGrayChar::getGray (void * pixel) const
{
  return *((uint8_t *) pixel);
}

void
PixelFormatGrayChar::getGray (void * pixel, float & gray) const
{
  gray = lutChar2Float[*(uint8_t *) pixel];
}

void
PixelFormatGrayChar::setRGBA (void * pixel, uint32_t rgba) const
{
  uint32_t r = (rgba & 0xFF000000) >> 16;
  uint32_t g = (rgba &   0xFF0000) >>  8;
  uint32_t b =  rgba &     0xFF00;

  *((uint8_t *) pixel) = ((r * redWeight + g * greenWeight + b * blueWeight) / totalWeight + 0x80) >> 8;
}

void
PixelFormatGrayChar::setXYZ (void * pixel, float values[]) const
{
  // Convert Y value to non-linear form
  float v = min (max (values[1], 0.0f), 1.0f);
  *((uint8_t *) pixel) = lutFloat2Char[(uint16_t) (65535 * v)];
}

void
PixelFormatGrayChar::setGray (void * pixel, uint8_t gray) const
{
  *((uint8_t *) pixel) = gray;
}

void
PixelFormatGrayChar::setGray (void * pixel, float gray) const
{
  gray = min (max (gray, 0.0f), 1.0f);
  *((uint8_t *) pixel) = lutFloat2Char[(uint16_t) (65535 * gray)];
}


// class PixelFormatGrayAlphaChar ---------------------------------------------

PixelFormatGrayAlphaChar::PixelFormatGrayAlphaChar ()
{
  planes     = 1;
  depth      = 2;
  precedence = 2;  // same as GrayShort
  monochrome = true;
  hasAlpha   = true;
}

uint32_t
PixelFormatGrayAlphaChar::getRGBA (void * pixel) const
{
  uint32_t t = *((uint8_t *) pixel);
  return (t << 24) | (t << 16) | (t << 8) | *((uint8_t *) pixel + 1);
}

void
PixelFormatGrayAlphaChar::setRGBA (void * pixel, uint32_t rgba) const
{
  uint32_t r = (rgba & 0xFF000000) >> 16;
  uint32_t g = (rgba &   0xFF0000) >>  8;
  uint32_t b =  rgba &     0xFF00;
  uint32_t a =  rgba &       0xFF;

  *((uint16_t *) pixel) = (a << 8) | (((r * redWeight + g * greenWeight + b * blueWeight) / totalWeight + 0x80) >> 8);
}


// class PixelFormatGrayShort -------------------------------------------------

PixelFormatGrayShort::PixelFormatGrayShort (uint16_t grayMask)
{
  planes     = 1;
  depth      = 2;
  precedence = 2;  // Above GrayChar and UYVY, but below everything else
  monochrome = true;
  hasAlpha   = false;

  this->grayMask = grayMask;
  grayShift = 0;
  while (grayMask >>= 1) {grayShift++;}
  grayShift = 15 - grayShift;
}

Image
PixelFormatGrayShort::filter (const Image & image)
{
  Image result (*this);

  if (*image.format == *this)
  {
	result = image;
	return result;
  }

  result.resize (image.width, image.height);
  result.timestamp = image.timestamp;
  if (result.width <= 0  ||  result.height <= 0) return result;

  if (typeid (*image.format) == typeid (PixelFormatGrayChar))
  {
	fromGrayChar (image, result);
  }
  else if (typeid (*image.format) == typeid (PixelFormatGrayFloat))
  {
	fromGrayFloat (image, result);
  }
  else if (typeid (*image.format) == typeid (PixelFormatGrayDouble))
  {
	fromGrayDouble (image, result);
  }
  else
  {
	fromAny (image, result);
  }

  return result;
}

void
PixelFormatGrayShort::fromGrayChar (const Image & image, Image & result) const
{
  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  PixelBufferPacked * o = (PixelBufferPacked *) result.buffer;
  assert (i  &&  o);

  uint8_t *  fromPixel = (uint8_t *)  i->base ();
  uint16_t * toPixel   = (uint16_t *) o->base ();
  uint16_t * end       = toPixel + result.width * result.height;
  const int step = i->stride - image.width;
  while (toPixel < end)
  {
	uint16_t * rowEnd = toPixel + result.width;
	while (toPixel < rowEnd)
	{
	  *toPixel++ = (uint16_t) (grayMask * lutChar2Float[*fromPixel++]);
	}
	fromPixel += step;
  }
}

void
PixelFormatGrayShort::fromGrayFloat (const Image & image, Image & result) const
{
  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  PixelBufferPacked * o = (PixelBufferPacked *) result.buffer;
  assert (i  &&  o);

  float *    fromPixel = (float *)    i->base ();
  uint16_t * toPixel   = (uint16_t *) o->base ();
  uint16_t * end       = toPixel + result.width * result.height;
  const int step = i->stride - image.width * sizeof (float);
  while (toPixel < end)
  {
	uint16_t * rowEnd = toPixel + result.width;
	while (toPixel < rowEnd)
	{
	  float p = min (max (*fromPixel++, 0.0f), 1.0f);
	  *toPixel++ = (uint16_t) (p * grayMask);
	}
	fromPixel = (float *) ((char *) fromPixel + step);
  }
}

void
PixelFormatGrayShort::fromGrayDouble (const Image & image, Image & result) const
{
  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  PixelBufferPacked * o = (PixelBufferPacked *) result.buffer;
  assert (i  &&  o);

  double *   fromPixel = (double *)   i->base ();
  uint16_t * toPixel   = (uint16_t *) o->base ();
  uint16_t * end       = toPixel + result.width * result.height;
  const int step = i->stride - image.width * sizeof (double);
  while (toPixel < end)
  {
	uint16_t * rowEnd = toPixel + result.width;
	while (toPixel < rowEnd)
	{
	  double p = min (max (*fromPixel++, 0.0), 1.0);
	  *toPixel++ = (uint16_t) (p * grayMask);
	}
	fromPixel = (double *) ((char *) fromPixel + step);
  }
}

void
PixelFormatGrayShort::fromAny (const Image & image, Image & result) const
{
  uint16_t * dest = (uint16_t *) ((PixelBufferPacked *) result.buffer)->base ();
  const PixelFormat * sourceFormat = image.format;

  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  if (i)
  {
	uint8_t *  source      = (uint8_t *) i->base ();
	const int  sourceDepth = (int) sourceFormat->depth;
	const int  step        = i->stride - image.width * sourceDepth;
	uint16_t * end         = dest + image.width * image.height;
	while (dest < end)
	{
	  uint16_t * rowEnd = dest + result.width;
	  while (dest < rowEnd)
	  {
		float gray;
		sourceFormat->getGray (source, gray);
		*dest++ = (uint16_t) (grayMask * gray);
		source += sourceDepth;
	  }
	  source += step;
	}
  }
  else
  {
	for (int y = 0; y < image.height; y++)
	{
	  for (int x = 0; x < image.width; x++)
	  {
		float gray;
		sourceFormat->getGray (image.buffer->pixel (x, y), gray);
		*dest++ = (uint16_t) (grayMask * gray);
	  }
	}
  }
}

bool
PixelFormatGrayShort::operator == (const PixelFormat & that) const
{
  if (const PixelFormatGrayShort * other = dynamic_cast<const PixelFormatGrayShort *> (& that))
  {
	return other->grayMask == grayMask;  // grayShift should generally be the same if mask is the same
  }
  if (const PixelFormatRGBABits * other = dynamic_cast<const PixelFormatRGBABits *> (& that))
  {
	return    other->depth     == depth
	       && other->redMask   == grayMask
	       && other->greenMask == grayMask
	       && other->blueMask  == grayMask;
  }
  return false;
}

uint32_t
PixelFormatGrayShort::getRGBA (void * pixel) const
{
  uint32_t t = lutFloat2Char[*(uint16_t *) pixel << grayShift];
  return (t << 24) | (t << 16) | (t << 8) | 0xFF;
}

void
PixelFormatGrayShort::getXYZ (void * pixel, float values[]) const
{
  float t = *((uint16_t *) pixel) / (float) grayMask;
  values[0] = 0.950470f * t;
  values[1] =             t;
  values[2] = 1.088830f * t;
}

uint8_t
PixelFormatGrayShort::getGray (void * pixel) const
{
  return lutFloat2Char[*(uint16_t *) pixel << grayShift];
}

void
PixelFormatGrayShort::getGray (void * pixel, float & gray) const
{
  gray = *((uint16_t *) pixel) / (float) grayMask;
}

void
PixelFormatGrayShort::setRGBA (void * pixel, uint32_t rgba) const
{
  float r = lutChar2Float[ rgba             >> 24];
  float g = lutChar2Float[(rgba & 0xFF0000) >> 16];
  float b = lutChar2Float[(rgba &   0xFF00) >>  8];

  float t = r * redToY + g * greenToY + b * blueToY;
  *((uint16_t *) pixel) = (uint16_t) (grayMask * t);
}

void
PixelFormatGrayShort::setXYZ (void * pixel, float values[]) const
{
  float v = min (max (values[1], 0.0f), 1.0f);
  *((uint16_t *) pixel) = (uint16_t) (grayMask * v);
}

void
PixelFormatGrayShort::setGray (void * pixel, uint8_t gray) const
{
  *((uint16_t *) pixel) = (uint16_t) (grayMask * lutChar2Float[gray]);
}

void
PixelFormatGrayShort::setGray (void * pixel, float gray) const
{
  gray = min (max (gray, 0.0f), 1.0f);
  *((uint16_t *) pixel) = (uint16_t) (grayMask * gray);
}


// class PixelFormatGrayShortSigned -------------------------------------------

PixelFormatGrayShortSigned::PixelFormatGrayShortSigned (int32_t bias, int32_t scale)
: bias (bias),
  scale (scale)
{
  planes     = 1;
  depth      = 2;
  precedence = 2;  // Above GrayChar and UYVY, but below everything else
  monochrome = true;
  hasAlpha   = false;
}

bool
PixelFormatGrayShortSigned::operator == (const PixelFormat & that) const
{
  if (const PixelFormatGrayShortSigned * other = dynamic_cast<const PixelFormatGrayShortSigned *> (& that))
  {
	return other->bias == bias;
  }
  return false;
}

uint32_t
PixelFormatGrayShortSigned::getRGBA (void * pixel) const
{
  uint32_t t = lutFloat2Char[min (65535, max (0, *(int16_t *) pixel + bias))];
  return (t << 24) | (t << 16) | (t << 8) | 0xFF;
}

void
PixelFormatGrayShortSigned::getRGBA (void * pixel, float values[]) const
{
  float gray = (*(int16_t *) pixel + bias) / (float) scale;  // allow float values to go outside [0,1]
  values[0] = gray;
  values[1] = gray;
  values[2] = gray;
  values[3] = 1.0;
}

void
PixelFormatGrayShortSigned::getXYZ (void * pixel, float values[]) const
{
  float t = (*(int16_t *) pixel + bias) / (float) scale;
  values[0] = 0.950470f * t;
  values[1] =             t;
  values[2] = 1.088830f * t;
}

void
PixelFormatGrayShortSigned::getGray (void * pixel, float & gray) const
{
  gray = (*(int16_t *) pixel + bias) / (float) scale;
}

void
PixelFormatGrayShortSigned::setRGBA (void * pixel, uint32_t rgba) const
{
  float r = lutChar2Float[ rgba             >> 24];
  float g = lutChar2Float[(rgba & 0xFF0000) >> 16];
  float b = lutChar2Float[(rgba &   0xFF00) >>  8];

  float t = r * redToY + g * greenToY + b * blueToY;
  *((int16_t *) pixel) = (int16_t) min (32767, max (-32768, ((int32_t) (t * scale) - bias)));
}

void
PixelFormatGrayShortSigned::setRGBA (void * pixel, float values[]) const
{
  float t = values[0] * redToY + values[1] * greenToY + values[2] * blueToY;
  *((int16_t *) pixel) = (int16_t) min (32767, max (-32768, ((int32_t) (t * scale) - bias)));
}

void
PixelFormatGrayShortSigned::setXYZ (void * pixel, float values[]) const
{
  *((int16_t *) pixel) = (int16_t) min (32767, max (-32768, ((int32_t) (values[1] * scale) - bias)));
}

void
PixelFormatGrayShortSigned::setGray (void * pixel, float gray) const
{
  *((int16_t *) pixel) = (int16_t) min (32767, max (-32768, ((int32_t) (gray * scale) - bias)));
}


// class PixelFormatGrayAlphaShort --------------------------------------------

PixelFormatGrayAlphaShort::PixelFormatGrayAlphaShort ()
{
  planes     = 1;
  depth      = 4;
  precedence = 2;  // same as GrayShort
  monochrome = true;
  hasAlpha   = true;
}

uint32_t
PixelFormatGrayAlphaShort::getRGBA (void * pixel) const
{
  uint32_t t = lutFloat2Char[*(uint16_t *) pixel];
  uint32_t a = *((uint16_t *) pixel + 1);
  return (t << 24) | (t << 16) | (t << 8) | (a >> 8);
}

void
PixelFormatGrayAlphaShort::setRGBA (void * pixel, uint32_t rgba) const
{
  float r = lutChar2Float[ rgba             >> 24];
  float g = lutChar2Float[(rgba & 0xFF0000) >> 16];
  float b = lutChar2Float[(rgba &   0xFF00) >>  8];
  uint32_t a =             rgba &     0xFF;

  float t = r * redToY + g * greenToY + b * blueToY;
  *((uint32_t *) pixel) = (0x1010000 * a) | ((uint16_t) (0xFFFF * t));
}


// class PixelFormatGrayFloat -------------------------------------------------

PixelFormatGrayFloat::PixelFormatGrayFloat ()
{
  planes      = 1;
  depth       = 4;
  precedence  = 4;  // Above all integer formats and below GrayDouble
  monochrome  = true;
  hasAlpha    = false;
}

Image
PixelFormatGrayFloat::filter (const Image & image)
{
  Image result (*this);

  if (*image.format == *this)
  {
	result = image;
	return result;
  }

  result.resize (image.width, image.height);
  result.timestamp = image.timestamp;
  if (result.width <= 0  ||  result.height <= 0) return result;

  if (typeid (* image.format) == typeid (PixelFormatGrayChar))
  {
	fromGrayChar (image, result);
  }
  else if (typeid (* image.format) == typeid (PixelFormatGrayShort))
  {
	fromGrayShort (image, result);
  }
  else if (typeid (* image.format) == typeid (PixelFormatGrayDouble))
  {
	fromGrayDouble (image, result);
  }
  else if (typeid (* image.format) == typeid (PixelFormatRGBAChar))
  {
	fromRGBAChar (image, result);
  }
  else if (typeid (* image.format) == typeid (PixelFormatRGBChar))
  {
	fromRGBChar (image, result);
  }
  else if (typeid (* image.format) == typeid (PixelFormatRGBABits))
  {
	fromRGBABits (image, result);
  }
  else if (typeid (* image.format) == typeid (PixelFormatPlanarYCbCr))
  {
	fromYCbCr (image, result);
  }
  else
  {
	fromAny (image, result);
  }

  return result;
}

void
PixelFormatGrayFloat::fromGrayChar (const Image & image, Image & result) const
{
  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  PixelBufferPacked * o = (PixelBufferPacked *) result.buffer;
  assert (i  &&  o);

  uint8_t * fromPixel = (uint8_t *) i->base ();
  float *   toPixel   = (float *)   o->base ();
  float *   end       = toPixel + result.width * result.height;
  const int step = i->stride - image.width;
  while (toPixel < end)
  {
	float * rowEnd = toPixel + result.width;
	while (toPixel < rowEnd)
	{
	  *toPixel++ = lutChar2Float[*fromPixel++];
	}
	fromPixel += step;
  }
}

void
PixelFormatGrayFloat::fromGrayShort (const Image & image, Image & result) const
{
  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  PixelBufferPacked * o = (PixelBufferPacked *) result.buffer;
  assert (i  &&  o);

  uint16_t * fromPixel = (uint16_t *) i->base ();
  float *    toPixel   = (float *)    o->base ();
  float *    end       = toPixel + result.width * result.height;
  const int step = i->stride - image.width * sizeof (short);
  const float grayMask = ((const PixelFormatGrayShort *) image.format)->grayMask;
  while (toPixel < end)
  {
	float * rowEnd = toPixel + result.width;
	while (toPixel < rowEnd)
	{
	  *toPixel++ = *fromPixel++ / grayMask;
	}
	fromPixel = (uint16_t *) ((char *) fromPixel + step);
  }
}

void
PixelFormatGrayFloat::fromGrayDouble (const Image & image, Image & result) const
{
  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  PixelBufferPacked * o = (PixelBufferPacked *) result.buffer;
  assert (i  &&  o);

  double * fromPixel = (double *) i->base ();
  float *  toPixel   = (float *)  o->base ();
  float *  end       = toPixel + result.width * result.height;
  const int step = i->stride - image.width * sizeof (double);
  while (toPixel < end)
  {
	float * rowEnd = toPixel + result.width;
	while (toPixel < rowEnd)
	{
	  *toPixel++ = (float) *fromPixel++;
	}
	fromPixel = (double *) ((char *) fromPixel + step);
  }
}

void
PixelFormatGrayFloat::fromRGBAChar (const Image & image, Image & result) const
{
  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  PixelBufferPacked * o = (PixelBufferPacked *) result.buffer;
  assert (i  &&  o);

  uint8_t * fromPixel = (uint8_t *) i->base ();
  float *   toPixel   = (float *)   o->base ();
  float *   end       = toPixel + result.width * result.height;
  const int step = i->stride - image.width * 4;
  while (toPixel < end)
  {
	float * rowEnd = toPixel + result.width;
	while (toPixel < rowEnd)
	{
	  float r = lutChar2Float[fromPixel[0]];
	  float g = lutChar2Float[fromPixel[1]];
	  float b = lutChar2Float[fromPixel[2]];
	  fromPixel += 4;
	  *toPixel++ = redToY * r + greenToY * g + blueToY * b;
	}
	fromPixel += step;
  }
}

void
PixelFormatGrayFloat::fromRGBChar (const Image & image, Image & result) const
{
  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  PixelBufferPacked * o = (PixelBufferPacked *) result.buffer;
  assert (i  &&  o);

  uint8_t * fromPixel = (uint8_t *) i->base ();
  float *   toPixel   = (float *)   o->base ();
  float *   end       = toPixel + result.width * result.height;
  const int step = i->stride - image.width * 3;
  while (toPixel < end)
  {
	float * rowEnd = toPixel + result.width;
	while (toPixel < rowEnd)
	{
	  float r = lutChar2Float[fromPixel[0]];
	  float g = lutChar2Float[fromPixel[1]];
	  float b = lutChar2Float[fromPixel[2]];
	  fromPixel += 3;
	  *toPixel++ = redToY * r + greenToY * g + blueToY * b;
	}
	fromPixel += step;
  }
}

void
PixelFormatGrayFloat::fromRGBABits (const Image & image, Image & result) const
{
  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  PixelBufferPacked * o = (PixelBufferPacked *) result.buffer;
  assert (i  &&  o);

  const PixelFormatRGBABits * that = (const PixelFormatRGBABits *) image.format;

  int redShift;
  int greenShift;
  int blueShift;
  int alphaShift;
  that->shift (0xFF, 0xFF, 0xFF, 0xFF, redShift, greenShift, blueShift, alphaShift);
  uint32_t redFactor   = prepareDublicate (redShift,   that->redBits);
  uint32_t greenFactor = prepareDublicate (greenShift, that->greenBits);
  uint32_t blueFactor  = prepareDublicate (blueShift,  that->blueBits);

  #define RGBBits2GrayFloat(imageSize) \
  { \
    imageSize * fromPixel = (imageSize *) i->base (); \
    float *     toPixel   = (float *)     o->base (); \
    float *     end       = toPixel + result.width * result.height; \
	const int step = i->stride - image.width * sizeof (imageSize); \
	while (toPixel < end) \
    { \
	  float * rowEnd = toPixel + result.width; \
	  while (toPixel < rowEnd) \
	  { \
		uint32_t r = *fromPixel & that->redMask; \
		uint32_t g = *fromPixel & that->greenMask; \
		uint32_t b = *fromPixel & that->blueMask; \
		fromPixel++; \
		float fr = lutChar2Float[dublicate (r, redFactor,   redShift)]; \
		float fg = lutChar2Float[dublicate (g, greenFactor, greenShift)]; \
		float fb = lutChar2Float[dublicate (b, blueFactor,  blueShift)]; \
		*toPixel++ = redToY * fr + greenToY * fg + blueToY * fb; \
	  } \
	  fromPixel = (imageSize *) ((char *) fromPixel + step); \
	} \
  }

  switch ((int) that->depth)
  {
    case 1:
	  RGBBits2GrayFloat (uint8_t);
	  break;
    case 2:
	  RGBBits2GrayFloat (uint16_t);
	  break;
    case 3:
	{
	  const int step = i->stride - image.width * 3;
#     if BYTE_ORDER == LITTLE_ENDIAN
	  uint8_t * fromPixel = (uint8_t *) i->base () + i->stride * image.height - step - 3;
	  float *   begin     = (float *)   o->base ();
	  float *   toPixel   = begin + result.width * result.height;
	  float *   rowBegin  = toPixel - result.width;
	  // Process last pixel
	  toPixel--;
	  Int2Char t;
	  t.piece0 = fromPixel[0];
	  t.piece1 = fromPixel[1];
	  t.piece2 = fromPixel[2];
	  fromPixel -= 3;
	  uint32_t r = t.all & that->redMask;
	  uint32_t g = t.all & that->greenMask;
	  uint32_t b = t.all & that->blueMask;
	  float fr = lutChar2Float[dublicate (r, redFactor,   redShift)];
	  float fg = lutChar2Float[dublicate (g, greenFactor, greenShift)];
	  float fb = lutChar2Float[dublicate (b, blueFactor,  blueShift)];
	  *toPixel-- = redToY * fr + greenToY * fg + blueToY * fb;
	  // Process other pixels
	  while (toPixel >= begin)
	  {
		while (toPixel >= rowBegin)
		{
		  uint32_t t = * (uint32_t *) fromPixel;
		  fromPixel -= 3;
		  r = t & that->redMask;
		  g = t & that->greenMask;
		  b = t & that->blueMask;
		  fr = lutChar2Float[dublicate (r, redFactor,   redShift)];
		  fg = lutChar2Float[dublicate (g, greenFactor, greenShift)];
		  fb = lutChar2Float[dublicate (b, blueFactor,  blueShift)];
		  *toPixel-- = redToY * fr + greenToY * fg + blueToY * fb;
		}
		fromPixel -= step;
		rowBegin -= result.width;
	  }
#     elif BYTE_ORDER == BIG_ENDIAN
	  uint8_t * fromPixel = (uint8_t *) i->base ();
	  float *   toPixel   = (float *)   o->base ();
	  float *   end       = toPixel + result.width * result.height;
	  float *   rowEnd    = toPixel + result.width;
	  // Process first pixel
	  Int2Char t;
	  t.piece0 = fromPixel[0];
	  t.piece1 = fromPixel[1];
	  t.piece2 = fromPixel[2];
	  fromPixel += 2;  // only 2, because big-endian pointer is set back by 1
	  uint32_t r = t.all & that->redMask;
	  uint32_t g = t.all & that->greenMask;
	  uint32_t b = t.all & that->blueMask;
	  float fr = lutChar2Float[dublicate (r, redFactor,   redShift)];
	  float fg = lutChar2Float[dublicate (g, greenFactor, greenShift)];
	  float fb = lutChar2Float[dublicate (b, blueFactor,  blueShift)];
	  *toPixel++ = redToY * fr + greenToY * fg + blueToY * fb;
	  // Process other pixels
	  while (toPixel < end)
	  {
		while (toPixel < rowEnd)
		{
		  uint32_t t = * (uint32_t *) fromPixel;
		  fromPixel += 3;
		  r = t & that->redMask;
		  g = t & that->greenMask;
		  b = t & that->blueMask;
		  fr = lutChar2Float[dublicate (r, redFactor,   redShift)];
		  fg = lutChar2Float[dublicate (g, greenFactor, greenShift)];
		  fb = lutChar2Float[dublicate (b, blueFactor,  blueShift)];
		  *toPixel++ = redToY * fr + greenToY * fg + blueToY * fb;
		}
		fromPixel += step;
		rowEnd += result.width;
	  }
#     endif
	  break;
	}
    case 4:
    default:
	  RGBBits2GrayFloat (uint32_t);
  }
}

void
PixelFormatGrayFloat::fromYCbCr (const Image & image, Image & result) const
{
  PixelBufferPlanar * i = (PixelBufferPlanar *) image.buffer;
  PixelBufferPacked * o = (PixelBufferPacked *) result.buffer;
  assert (i  &&  o);

  uint8_t * fromPixel = (uint8_t *) i->plane0;
  float *   toPixel   = (float *)   o->base ();
  float *   end       = toPixel + result.width * result.height;
  const int step = i->stride0 - image.width;
  while (toPixel < end)
  {
	float * rowEnd = toPixel + result.width;
	while (toPixel < rowEnd)
	{
	  *toPixel++ = PixelFormatPlanarYCbCr::lutGrayOut[*fromPixel++];
	}
	fromPixel += step;
  }
}

void
PixelFormatGrayFloat::fromAny (const Image & image, Image & result) const
{
  float * dest = (float *) ((PixelBufferPacked *) result.buffer)->base ();
  const PixelFormat * sourceFormat = image.format;

  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  if (i)
  {
	uint8_t * source      = (uint8_t *) i->base ();
	const int sourceDepth = (int) sourceFormat->depth;
	const int step        = i->stride - image.width * sourceDepth;
	float *   end         = dest + image.width * image.height;
	while (dest < end)
	{
	  float * rowEnd = dest + result.width;
	  while (dest < rowEnd)
	  {
		sourceFormat->getGray (source, *dest++);
		source += sourceDepth;
	  }
	  source += step;
	}
  }
  else
  {
	for (int y = 0; y < image.height; y++)
	{
	  for (int x = 0; x < image.width; x++)
	  {
		sourceFormat->getGray (image.buffer->pixel (x, y), *dest++);
	  }
	}
  }
}

uint32_t
PixelFormatGrayFloat::getRGBA (void * pixel) const
{
  float v = min (max (*((float *) pixel), 0.0f), 1.0f);
  uint32_t t = lutFloat2Char[(uint16_t) (65535 * v)];
  return (t << 24) | (t << 16) | (t << 8) | 0xFF;
}

void
PixelFormatGrayFloat::getRGBA (void * pixel, float values[]) const
{
  float i = *((float *) pixel);
  values[0] = i;
  values[1] = i;
  values[2] = i;
  values[3] = 1.0f;
}

void
PixelFormatGrayFloat::getXYZ (void * pixel, float values[]) const
{
  float t = *((float *) pixel);
  values[0] = 0.950470f * t;
  values[1] =             t;
  values[2] = 1.088830f * t;
}

uint8_t
PixelFormatGrayFloat::getGray (void * pixel) const
{
  float v = min (max (*((float *) pixel), 0.0f), 1.0f);
  return lutFloat2Char[(uint16_t) (65535 * v)];
}

void
PixelFormatGrayFloat::getGray (void * pixel, float & gray) const
{
  gray = *((float *) pixel);
}

void
PixelFormatGrayFloat::setRGBA (void * pixel, uint32_t rgba) const
{
  float r = lutChar2Float[(rgba & 0xFF000000) >> 24];
  float g = lutChar2Float[(rgba &   0xFF0000) >> 16];
  float b = lutChar2Float[(rgba &     0xFF00) >>  8];
  *((float *) pixel) = redToY * r + greenToY * g + blueToY * b;
}

void
PixelFormatGrayFloat::setRGBA (void * pixel, float values[]) const
{
  *((float *) pixel) = redToY * values[0] + greenToY * values[1] + blueToY * values[2];
}

void
PixelFormatGrayFloat::setXYZ (void * pixel, float values[]) const
{
  *((float *) pixel) = values[1];
}

void
PixelFormatGrayFloat::setGray  (void * pixel, uint8_t gray) const
{
  *((float *) pixel) = lutChar2Float[gray];
}

void
PixelFormatGrayFloat::setGray  (void * pixel, float gray) const
{
  *((float *) pixel) = gray;
}


// class PixelFormatGrayDouble ------------------------------------------------

PixelFormatGrayDouble::PixelFormatGrayDouble ()
{
  planes      = 1;
  depth       = 8;
  precedence  = 6;  // above all integer formats and above GrayFloat
  monochrome  = true;
  hasAlpha    = false;
}

Image
PixelFormatGrayDouble::filter (const Image & image)
{
  Image result (*this);

  if (*image.format == *this)
  {
	result = image;
	return result;
  }

  result.resize (image.width, image.height);
  result.timestamp = image.timestamp;
  if (result.width <= 0  ||  result.height <= 0) return result;

  if (typeid (* image.format) == typeid (PixelFormatGrayChar))
  {
	fromGrayChar (image, result);
  }
  else if (typeid (* image.format) == typeid (PixelFormatGrayShort))
  {
	fromGrayShort (image, result);
  }
  else if (typeid (* image.format) == typeid (PixelFormatGrayFloat))
  {
	fromGrayFloat (image, result);
  }
  else if (typeid (* image.format) == typeid (PixelFormatRGBAChar))
  {
	fromRGBAChar (image, result);
  }
  else if (typeid (* image.format) == typeid (PixelFormatRGBChar))
  {
	fromRGBChar (image, result);
  }
  else if (typeid (* image.format) == typeid (PixelFormatRGBABits))
  {
	fromRGBABits (image, result);
  }
  else if (typeid (* image.format) == typeid (PixelFormatPlanarYCbCr))
  {
	fromYCbCr (image, result);
  }
  else
  {
	fromAny (image, result);
  }

  return result;
}

void
PixelFormatGrayDouble::fromGrayChar (const Image & image, Image & result) const
{
  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  PixelBufferPacked * o = (PixelBufferPacked *) result.buffer;
  assert (i  &&  o);

  uint8_t * fromPixel = (uint8_t *) i->base ();
  double *  toPixel   = (double *)  o->base ();
  double *  end       = toPixel + result.width * result.height;
  const int step = i->stride - image.width;
  while (toPixel < end)
  {
	double * rowEnd = toPixel + result.width;
	while (toPixel < rowEnd)
	{
	  *toPixel++ = lutChar2Float[*fromPixel++];
	}
	fromPixel += step;
  }
}

void
PixelFormatGrayDouble::fromGrayShort (const Image & image, Image & result) const
{
  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  PixelBufferPacked * o = (PixelBufferPacked *) result.buffer;
  assert (i  &&  o);

  uint16_t * fromPixel = (uint16_t *) i->base ();
  double *   toPixel   = (double *)   o->base ();
  double *   end       = toPixel + result.width * result.height;
  const int step = i->stride - image.width * sizeof (short);
  const double grayMask = ((const PixelFormatGrayShort *) image.format)->grayMask;
  while (toPixel < end)
  {
	double * rowEnd = toPixel + result.width;
	while (toPixel < rowEnd)
	{
	  *toPixel++ = *fromPixel++ / grayMask;
	}
	fromPixel = (uint16_t *) ((char *) fromPixel + step);
  }
}

void
PixelFormatGrayDouble::fromGrayFloat (const Image & image, Image & result) const
{
  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  PixelBufferPacked * o = (PixelBufferPacked *) result.buffer;
  assert (i  &&  o);

  float *  fromPixel = (float *)  i->base ();
  double * toPixel   = (double *) o->base ();
  double * end       = toPixel + result.width * result.height;
  const int step = i->stride - image.width * sizeof (float);
  while (toPixel < end)
  {
	double * rowEnd = toPixel + result.width;
	while (toPixel < rowEnd)
	{
	  *toPixel++ = *fromPixel++;
	}
	fromPixel = (float *) ((char *) fromPixel + step);
  }
}

void
PixelFormatGrayDouble::fromRGBAChar (const Image & image, Image & result) const
{
  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  PixelBufferPacked * o = (PixelBufferPacked *) result.buffer;
  assert (i  &&  o);

  uint8_t * fromPixel = (uint8_t *) i->base ();
  double *  toPixel   = (double *)  o->base ();
  double *  end       = toPixel + result.width * result.height;
  const int step = i->stride - image.width * 4;
  while (toPixel < end)
  {
	double * rowEnd = toPixel + result.width;
	while (toPixel < rowEnd)
	{
	  double r = lutChar2Float[fromPixel[0]];
	  double g = lutChar2Float[fromPixel[1]];
	  double b = lutChar2Float[fromPixel[2]];
	  fromPixel += 4;
	  *toPixel++ = redToY * r + greenToY * g + blueToY * b;
	}
	fromPixel += step;
  }
}

void
PixelFormatGrayDouble::fromRGBChar (const Image & image, Image & result) const
{
  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  PixelBufferPacked * o = (PixelBufferPacked *) result.buffer;
  assert (i  &&  o);

  uint8_t * fromPixel = (uint8_t *) i->base ();
  double *  toPixel   = (double *)  o->base ();
  double *  end       = toPixel + result.width * result.height;
  const int step = i->stride - image.width * 3;
  while (toPixel < end)
  {
	double * rowEnd = toPixel + result.width;
	while (toPixel < rowEnd)
	{
	  double r = lutChar2Float[fromPixel[0]];
	  double g = lutChar2Float[fromPixel[1]];
	  double b = lutChar2Float[fromPixel[2]];
	  fromPixel += 3;
	  *toPixel++ = redToY * r + greenToY * g + blueToY * b;
	}
	fromPixel += step;
  }
}

void
PixelFormatGrayDouble::fromRGBABits (const Image & image, Image & result) const
{
  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  PixelBufferPacked * o = (PixelBufferPacked *) result.buffer;
  assert (i  &&  o);

  const PixelFormatRGBABits * that = (const PixelFormatRGBABits *) image.format;

  int redShift;
  int greenShift;
  int blueShift;
  int alphaShift;
  that->shift (0xFF, 0xFF, 0xFF, 0xFF, redShift, greenShift, blueShift, alphaShift);
  uint32_t redFactor   = prepareDublicate (redShift,   that->redBits);
  uint32_t greenFactor = prepareDublicate (greenShift, that->greenBits);
  uint32_t blueFactor  = prepareDublicate (blueShift,  that->blueBits);

  #define RGBBits2GrayDouble(imageSize) \
  { \
    imageSize * fromPixel = (imageSize *) i->base (); \
    double *    toPixel   = (double *)    o->base (); \
    double *    end       = toPixel + result.width * result.height; \
	const int step = i->stride - image.width * sizeof (imageSize); \
	while (toPixel < end) \
    { \
	  double * rowEnd = toPixel + result.width; \
	  while (toPixel < rowEnd) \
	  { \
		uint32_t r = *fromPixel & that->redMask; \
		uint32_t g = *fromPixel & that->greenMask; \
		uint32_t b = *fromPixel & that->blueMask; \
		fromPixel++; \
		double fr = lutChar2Float[dublicate (r, redFactor,   redShift)]; \
		double fg = lutChar2Float[dublicate (g, greenFactor, greenShift)]; \
		double fb = lutChar2Float[dublicate (b, blueFactor,  blueShift)]; \
		*toPixel++ = redToY * fr + greenToY * fg + blueToY * fb; \
	  } \
	  fromPixel = (imageSize *) ((char *) fromPixel + step); \
	} \
  }

  switch ((int) that->depth)
  {
    case 1:
	  RGBBits2GrayDouble (uint8_t);
	  break;
    case 2:
	  RGBBits2GrayDouble (uint16_t);
	  break;
    case 3:
	{
	  const int step = i->stride - image.width * 3;
#     if BYTE_ORDER == LITTLE_ENDIAN
	  uint8_t * fromPixel = (uint8_t *) i->base () + i->stride * image.height - step - 3;
	  double *  begin     = (double *)  o->base ();
	  double *  toPixel   = begin + result.width * result.height;
	  double *  rowBegin  = toPixel - result.width;
	  // Process lasst pixel
	  toPixel--;
	  Int2Char t;
	  t.piece0 = fromPixel[0];
	  t.piece1 = fromPixel[1];
	  t.piece2 = fromPixel[2];
	  fromPixel -= 3;
	  uint32_t r = t.all & that->redMask;
	  uint32_t g = t.all & that->greenMask;
	  uint32_t b = t.all & that->blueMask;
	  double fr = lutChar2Float[dublicate (r, redFactor,   redShift)];
	  double fg = lutChar2Float[dublicate (g, greenFactor, greenShift)];
	  double fb = lutChar2Float[dublicate (b, blueFactor,  blueShift)];
	  *toPixel-- = redToY * fr + greenToY * fg + blueToY * fb;
	  // Process other pixels
	  while (toPixel >= begin)
	  {
		while (toPixel >= rowBegin)
		{
		  uint32_t t = * (uint32_t *) fromPixel;
		  fromPixel -= 3;
		  r = t & that->redMask;
		  g = t & that->greenMask;
		  b = t & that->blueMask;
		  fr = lutChar2Float[dublicate (r, redFactor,   redShift)];
		  fg = lutChar2Float[dublicate (g, greenFactor, greenShift)];
		  fb = lutChar2Float[dublicate (b, blueFactor,  blueShift)];
		  *toPixel-- = redToY * fr + greenToY * fg + blueToY * fb;
		}
		fromPixel -= step;
		rowBegin -= result.width;
	  }
#     elif BYTE_ORDER == BIG_ENDIAN
	  uint8_t * fromPixel = (uint8_t *) i->base ();
	  double *  toPixel   = (double *)  o->base ();
	  double *  end       = toPixel + result.width * result.height;
	  double *  rowEnd    = toPixel + result.width;
	  // Process first pixel
	  Int2Char t;
	  t.piece0 = fromPixel[0];
	  t.piece1 = fromPixel[1];
	  t.piece2 = fromPixel[2];
	  fromPixel += 2;  // only 2, because big-endian pointer is set back by 1
	  uint32_t r = t.all & that->redMask;
	  uint32_t g = t.all & that->greenMask;
	  uint32_t b = t.all & that->blueMask;
	  double fr = lutChar2Float[dublicate (r, redFactor,   redShift)];
	  double fg = lutChar2Float[dublicate (g, greenFactor, greenShift)];
	  double fb = lutChar2Float[dublicate (b, blueFactor,  blueShift)];
	  *toPixel++ = redToY * fr + greenToY * fg + blueToY * fb;
	  // Process other pixels
	  while (toPixel < end)
	  {
		while (toPixel < rowEnd)
		{
		  uint32_t t = * (uint32_t *) fromPixel;
		  fromPixel += 3;
		  r = t & that->redMask;
		  g = t & that->greenMask;
		  b = t & that->blueMask;
		  fr = lutChar2Float[dublicate (r, redFactor,   redShift)];
		  fg = lutChar2Float[dublicate (g, greenFactor, greenShift)];
		  fb = lutChar2Float[dublicate (b, blueFactor,  blueShift)];
		  *toPixel++ = redToY * fr + greenToY * fg + blueToY * fb;
		}
		fromPixel += step;
		rowEnd += result.width;
	  }
#     endif
	  break;
	}
    case 4:
    default:
	  RGBBits2GrayDouble (uint32_t);
  }
}

void
PixelFormatGrayDouble::fromYCbCr (const Image & image, Image & result) const
{
  PixelBufferPlanar * i = (PixelBufferPlanar *) image.buffer;
  PixelBufferPacked * o = (PixelBufferPacked *) result.buffer;
  assert (i  &&  o);

  uint8_t * fromPixel = (uint8_t *) i->plane0;
  double *  toPixel   = (double *)  o->base ();
  double *  end       = toPixel + result.width * result.height;
  const int step = i->stride0 - image.width;
  while (toPixel < end)
  {
	double * rowEnd = toPixel + result.width;
	while (toPixel < rowEnd)
	{
	  *toPixel++ = PixelFormatPlanarYCbCr::lutGrayOut[*fromPixel++];
	}
	fromPixel += step;
  }
}

void
PixelFormatGrayDouble::fromAny (const Image & image, Image & result) const
{
  double * dest = (double *) ((PixelBufferPacked *) result.buffer)->base ();
  const PixelFormat * sourceFormat = image.format;

  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  if (i)
  {
	uint8_t * source      = (uint8_t *) i->base ();
	const int sourceDepth = (int) sourceFormat->depth;
	const int step        = i->stride - image.width * sourceDepth;
	double *  end         = dest + image.width * image.height;
	while (dest < end)
	{
	  double * rowEnd = dest + result.width;
	  while (dest < rowEnd)
	  {
		float value;
		sourceFormat->getGray (source, value);
		*dest++ = value;
		source += sourceDepth;
	  }
	  source += step;
	}
  }
  else
  {
	for (int y = 0; y < image.height; y++)
	{
	  for (int x = 0; x < image.width; x++)
	  {
		float value;
		sourceFormat->getGray (image.buffer->pixel (x, y), value);
		*dest++ = value;
	  }
	}
  }
}

uint32_t
PixelFormatGrayDouble::getRGBA (void * pixel) const
{
  double v = min (max (*((double *) pixel), 0.0), 1.0);
  uint32_t t = lutFloat2Char[(uint16_t) (65535 * v)];
  return (t << 24) | (t << 16) | (t << 8) | 0xFF;
}

void
PixelFormatGrayDouble::getRGBA (void * pixel, float values[]) const
{
  float i = *((double *) pixel);
  values[0] = i;
  values[1] = i;
  values[2] = i;
  values[3] = 1.0f;
}

void
PixelFormatGrayDouble::getXYZ (void * pixel, float values[]) const
{
  float t = *((double *) pixel);
  values[0] = 0.950470f * t;
  values[1] =             t;
  values[2] = 1.088830f * t;
}

uint8_t
PixelFormatGrayDouble::getGray (void * pixel) const
{
  double v = min (max (*((double *) pixel), 0.0), 1.0);
  return lutFloat2Char[(uint16_t) (65535 * v)];
}

void
PixelFormatGrayDouble::getGray (void * pixel, float & gray) const
{
  gray = *((double *) pixel);
}

void
PixelFormatGrayDouble::setRGBA (void * pixel, uint32_t rgba) const
{
  double r = lutChar2Float[(rgba & 0xFF000000) >> 24];
  double g = lutChar2Float[(rgba &   0xFF0000) >> 16];
  double b = lutChar2Float[(rgba &     0xFF00) >>  8];
  *((double *) pixel) = redToY * r + greenToY * g + blueToY * b;
}

void
PixelFormatGrayDouble::setRGBA (void * pixel, float values[]) const
{
  *((double *) pixel) = redToY * values[0] + greenToY * values[1] + blueToY * values[2];
}

void
PixelFormatGrayDouble::setXYZ (void * pixel, float values[]) const
{
  *((double *) pixel) = values[1];
}

void
PixelFormatGrayDouble::setGray  (void * pixel, uint8_t gray) const
{
  *((double *) pixel) = lutChar2Float[gray];
}

void
PixelFormatGrayDouble::setGray  (void * pixel, float gray) const
{
  *((double *) pixel) = gray;
}


// class PixelFormatRGBABits --------------------------------------------------

PixelFormatRGBABits::PixelFormatRGBABits (int depth, uint32_t redMask, uint32_t greenMask, uint32_t blueMask, uint32_t alphaMask)
{
  this->depth     = depth;
  this->redMask   = redMask;
  this->greenMask = greenMask;
  this->blueMask  = blueMask;
  this->alphaMask = alphaMask;

  redBits   = countBits (redMask);
  greenBits = countBits (greenMask);
  blueBits  = countBits (blueMask);
  alphaBits = countBits (alphaMask);

  planes     = 1;
  precedence = 3;  // Above GrayChar and below all floating point formats
  monochrome = redMask == greenMask  &&  greenMask == blueMask;
  hasAlpha   = alphaMask;
}

Image
PixelFormatRGBABits::filter (const Image & image)
{
  Image result (*this);

  if (*image.format == *this)
  {
	result = image;
	return result;
  }

  result.resize (image.width, image.height);
  result.timestamp = image.timestamp;
  if (result.width <= 0  ||  result.height <= 0) return result;

  if (typeid (* image.format) == typeid (PixelFormatGrayChar))
  {
	fromGrayChar (image, result);
  }
  else if (typeid (* image.format) == typeid (PixelFormatGrayShort))
  {
	fromGrayShort (image, result);
  }
  else if (typeid (* image.format) == typeid (PixelFormatGrayFloat))
  {
	fromGrayFloat (image, result);
  }
  else if (typeid (* image.format) == typeid (PixelFormatGrayDouble))
  {
	fromGrayDouble (image, result);
  }
  else if ((const PixelFormatRGBABits *) image.format)
  {
	fromRGBABits (image, result);
  }
  else if (typeid (* image.format) == typeid (PixelFormatPlanarYCbCr))
  {
	fromYCbCr (image, result);
  }
  else
  {
	fromAny (image, result);
  }

  return result;
}

#define Bits2Bits(fromSize,toSize,fromRed,fromGreen,fromBlue,fromAlpha,toRed,toGreen,toBlue,toAlpha) \
{ \
  fromSize * fromPixel = (fromSize *) i->base (); \
  toSize *   toPixel   = (toSize *)   o->base (); \
  toSize *   end       = toPixel + result.width * result.height; \
  while (toPixel < end) \
  { \
	toSize * rowEnd = toPixel + result.width; \
	while (toPixel < rowEnd) \
	{ \
	  uint32_t r = *fromPixel & fromRed; \
	  uint32_t g = *fromPixel & fromGreen; \
	  uint32_t b = *fromPixel & fromBlue; \
	  uint32_t a = fromAlpha ? *fromPixel & fromAlpha : 0xFFFFFFFF; \
	  fromPixel++; \
	  *toPixel++ =   (dublicate (r, redFactor,   redShift)   & toRed) \
		           | (dublicate (g, greenFactor, greenShift) & toGreen) \
		           | (dublicate (b, blueFactor,  blueShift)  & toBlue) \
		           | (dublicate (a, alphaFactor, alphaShift) & toAlpha); \
	} \
	fromPixel = (fromSize *) ((char *) fromPixel + step); \
  } \
}

#define GrayFloat2Bits(fromSize,toSize) \
{ \
  fromSize * fromPixel = (fromSize *) i->base (); \
  toSize *   toPixel   = (toSize *)   o->base (); \
  toSize *   end       = toPixel + result.width * result.height; \
  while (toPixel < end) \
  { \
	toSize * rowEnd = toPixel + result.width; \
	while (toPixel < rowEnd) \
	{ \
	  fromSize v = min (max (*fromPixel++, (fromSize) 0.0), (fromSize) 1.0); \
	  uint32_t t = lutFloat2Char[(uint16_t) (65535 * v)]; \
	  *toPixel++ =   (roll (t, redShift)   & redMask) \
	               | (roll (t, greenShift) & greenMask) \
	               | (roll (t, blueShift)  & blueMask) \
	               | alphaMask; \
	} \
	fromPixel = (fromSize *) ((char *) fromPixel + step); \
  } \
}

// The following macros address pixels consisting of three bytes.  For
// efficiency's sake, there are versions specific to each endian.
#if BYTE_ORDER == LITTLE_ENDIAN

#define OddBits2Bits(toSize,fromRed,fromGreen,fromBlue,fromAlpha,toRed,toGreen,toBlue,toAlpha) \
{ \
  uint8_t * fromPixel = (uint8_t *) i->base () + i->stride * image.height - step - 3; \
  toSize *  begin     = (toSize *)  o->base (); \
  toSize *  toPixel   = begin + result.width * result.height; \
  toSize *  rowBegin  = toPixel - result.width; \
  toPixel--; \
  Int2Char t; \
  t.piece0 = fromPixel[0]; \
  t.piece1 = fromPixel[1]; \
  t.piece2 = fromPixel[2]; \
  fromPixel -= 3; \
  uint32_t r = t.all & fromRed; \
  uint32_t g = t.all & fromGreen; \
  uint32_t b = t.all & fromBlue; \
  uint32_t a = fromAlpha ? t.all & fromAlpha : 0xFFFFFFFF; \
  *toPixel-- =   (dublicate (r, redFactor,   redShift)   & toRed) \
               | (dublicate (g, greenFactor, greenShift) & toGreen) \
               | (dublicate (b, blueFactor,  blueShift)  & toBlue) \
               | (dublicate (a, alphaFactor, alphaShift) & toAlpha); \
  while (toPixel >= begin) \
  { \
	while (toPixel >= rowBegin) \
	{ \
	  uint32_t t = * (uint32_t *) fromPixel; \
	  fromPixel -= 3; \
	  r = t & fromRed; \
	  g = t & fromGreen; \
	  b = t & fromBlue; \
	  a = fromAlpha ? t & fromAlpha : 0xFFFFFFFF; \
	  *toPixel-- =   (dublicate (r, redFactor,   redShift)   & toRed) \
	               | (dublicate (g, greenFactor, greenShift) & toGreen) \
	               | (dublicate (b, blueFactor,  blueShift)  & toBlue) \
	               | (dublicate (a, alphaFactor, alphaShift) & toAlpha); \
	} \
	fromPixel -= step; \
	rowBegin -= result.width; \
  } \
}

#define Bits2OddBits(fromSize,fromRed,fromGreen,fromBlue,fromAlpha,toRed,toGreen,toBlue,toAlpha) \
{ \
  fromSize * fromPixel = (fromSize *) i->base (); \
  uint8_t *  toPixel   = (uint8_t *)  o->base (); \
  uint8_t *  end       = toPixel + o->stride * result.height - 3; \
  uint32_t r; \
  uint32_t g; \
  uint32_t b; \
  uint32_t a; \
  while (toPixel < end) \
  { \
	uint8_t * rowEnd = min (toPixel + o->stride, end); \
	while (toPixel < rowEnd) \
	{ \
	  r = *fromPixel & fromRed; \
	  g = *fromPixel & fromGreen; \
	  b = *fromPixel & fromBlue; \
	  a = fromAlpha ? *fromPixel & fromAlpha : 0xFFFFFFFF; \
	  fromPixel++; \
	  * (uint32_t *) toPixel =   (dublicate (r, redFactor,   redShift)   & toRed) \
		                       | (dublicate (g, greenFactor, greenShift) & toGreen) \
		                       | (dublicate (b, blueFactor,  blueShift)  & toBlue) \
		                       | (dublicate (a, alphaFactor, alphaShift) & toAlpha); \
	  toPixel += 3; \
	} \
	fromPixel = (fromSize *) ((char *) fromPixel + step); \
  } \
  fromPixel = (fromSize *) ((char *) fromPixel - step); \
  r = *fromPixel & fromRed; \
  g = *fromPixel & fromGreen; \
  b = *fromPixel & fromBlue; \
  a = fromAlpha ? *fromPixel & fromAlpha : 0xFFFFFFFF; \
  Int2Char t; \
  t.all =   (dublicate (r, redFactor,   redShift)   & toRed) \
	      | (dublicate (g, greenFactor, greenShift) & toGreen) \
	      | (dublicate (b, blueFactor,  blueShift)  & toBlue) \
	      | (dublicate (a, alphaFactor, alphaShift) & toAlpha); \
  toPixel[0] = t.piece0; \
  toPixel[1] = t.piece1; \
  toPixel[2] = t.piece2; \
}

#define OddBits2OddBits(fromRed,fromGreen,fromBlue,fromAlpha,toRed,toGreen,toBlue,toAlpha) \
{ \
  uint8_t * fromPixel = (uint8_t *) i->base (); \
  uint8_t * toPixel   = (uint8_t *) o->base (); \
  uint8_t * end       = toPixel + o->stride * result.height - 3; \
  uint32_t r; \
  uint32_t g; \
  uint32_t b; \
  uint32_t a; \
  while (toPixel < end) \
  { \
	uint8_t * rowEnd = min (toPixel + o->stride, end); \
	while (toPixel < rowEnd) \
	{ \
	  uint32_t t = * (uint32_t *) fromPixel; \
	  fromPixel += 3; \
	  r = t & fromRed; \
	  g = t & fromGreen; \
	  b = t & fromBlue; \
	  a = fromAlpha ? t & fromAlpha : 0xFFFFFFFF; \
	  * (uint32_t *) toPixel =   (dublicate (r, redFactor,   redShift)   & toRed) \
	                           | (dublicate (g, greenFactor, greenShift) & toGreen) \
	                           | (dublicate (b, blueFactor,  blueShift)  & toBlue) \
	                           | (dublicate (a, alphaFactor, alphaShift) & toAlpha); \
	  toPixel += 3; \
	} \
	fromPixel += step; \
  } \
  fromPixel -= step; \
  Int2Char t; \
  t.piece0 = fromPixel[0]; \
  t.piece1 = fromPixel[1]; \
  t.piece2 = fromPixel[2]; \
  r = t.all & fromRed; \
  g = t.all & fromGreen; \
  b = t.all & fromBlue; \
  a = fromAlpha ? t.all & fromAlpha : 0xFFFFFFFF; \
  t.all =   (dublicate (r, redFactor,   redShift)   & toRed) \
          | (dublicate (g, greenFactor, greenShift) & toGreen) \
          | (dublicate (b, blueFactor,  blueShift)  & toBlue) \
          | (dublicate (a, alphaFactor, alphaShift) & toAlpha); \
  toPixel[0] = t.piece0; \
  toPixel[1] = t.piece1; \
  toPixel[2] = t.piece2; \
}

#define GrayFloat2OddBits(fromSize) \
{ \
  fromSize * fromPixel = (fromSize *) i->base (); \
  uint8_t *  toPixel   = (uint8_t *)  o->base (); \
  uint8_t *  end       = toPixel + o->stride * result.height - 3; \
  fromSize v; \
  while (toPixel < end) \
  { \
	uint8_t * rowEnd = min (toPixel + o->stride, end); \
	while (toPixel < rowEnd) \
	{ \
	  v = min (max (*fromPixel++, (fromSize) 0.0), (fromSize) 1.0); \
	  uint32_t t = lutFloat2Char[(uint16_t) (65535 * v)]; \
	  * (uint32_t *) toPixel =   (roll (t, redShift)   & redMask) \
	                           | (roll (t, greenShift) & greenMask) \
	                           | (roll (t, blueShift)  & blueMask) \
	                           | alphaMask; \
	  toPixel += 3; \
	} \
	fromPixel = (fromSize *) ((char *) fromPixel + step); \
  } \
  fromPixel = (fromSize *) ((char *) fromPixel - step); \
  v = min (max (*fromPixel, (fromSize) 0.0), (fromSize) 1.0); \
  Int2Char t; \
  t.all = lutFloat2Char[(uint16_t) (65535 * v)]; \
  t.all =   (roll (t.all, redShift)   & redMask) \
          | (roll (t.all, greenShift) & greenMask) \
          | (roll (t.all, blueShift)  & blueMask) \
          | alphaMask; \
  toPixel[0] = t.piece0; \
  toPixel[1] = t.piece1; \
  toPixel[2] = t.piece2; \
}

#elif BYTE_ORDER == BIG_ENDIAN

#define OddBits2Bits(toSize,fromRed,fromGreen,fromBlue,fromAlpha,toRed,toGreen,toBlue,toAlpha) \
{ \
  uint8_t * fromPixel = (uint8_t *) i->base (); \
  toSize *  toPixel   = (toSize *)  o->base (); \
  toSize *  end       = toPixel + result.width * result.height; \
  toSize *  rowEnd    = toPixel + result.width; \
  Int2Char t; \
  t.piece0 = fromPixel[0]; \
  t.piece1 = fromPixel[1]; \
  t.piece2 = fromPixel[2]; \
  fromPixel += 2; \
  uint32_t r = t.all & fromRed; \
  uint32_t g = t.all & fromGreen; \
  uint32_t b = t.all & fromBlue; \
  uint32_t a = fromAlpha ? t.all & fromAlpha : 0xFFFFFFFF; \
  *toPixel++ =   (dublicate (r, redFactor,   redShift)   & toRed) \
               | (dublicate (g, greenFactor, greenShift) & toGreen) \
               | (dublicate (b, blueFactor,  blueShift)  & toBlue) \
               | (dublicate (a, alphaFactor, alphaShift) & toAlpha); \
  while (toPixel < end) \
  { \
	while (toPixel < rowEnd) \
	{ \
	  uint32_t t = * (uint32_t *) fromPixel; \
	  fromPixel += 3; \
	  r = t & fromRed; \
	  g = t & fromGreen; \
	  b = t & fromBlue; \
	  a = fromAlpha ? t & fromAlpha : 0xFFFFFFFF; \
	  *toPixel++ =   (dublicate (r, redFactor,   redShift)   & toRed) \
	               | (dublicate (g, greenFactor, greenShift) & toGreen) \
	               | (dublicate (b, blueFactor,  blueShift)  & toBlue) \
	               | (dublicate (a, alphaFactor, alphaShift) & toAlpha); \
	} \
	fromPixel += step; \
	rowEnd += result.width; \
  } \
}

#define Bits2OddBits(fromSize,fromRed,fromGreen,fromBlue,fromAlpha,toRed,toGreen,toBlue,toAlpha) \
{ \
  fromSize * fromPixel = (fromSize *) ((char *) i->base () + i->stride * image.height - step) - 1; \
  uint8_t *  begin     = (uint8_t *) o->base (); \
  uint8_t *  toPixel   = begin + o->stride * result.height - 4; \
  uint32_t r; \
  uint32_t g; \
  uint32_t b; \
  uint32_t a; \
  while (toPixel > begin) \
  { \
	uint8_t * rowBegin = max (toPixel - o->stride, begin); \
	while (toPixel > rowBegin) \
	{ \
	  r = *fromPixel & fromRed; \
	  g = *fromPixel & fromGreen; \
	  b = *fromPixel & fromBlue; \
	  a = fromAlpha ? *fromPixel & fromAlpha : 0xFFFFFFFF; \
	  fromPixel--; \
	  * (uint32_t *) toPixel =   (dublicate (r, redFactor,   redShift)   & toRed) \
	                           | (dublicate (g, greenFactor, greenShift) & toGreen) \
	                           | (dublicate (b, blueFactor,  blueShift)  & toBlue) \
	                           | (dublicate (a, alphaFactor, alphaShift) & toAlpha); \
	  toPixel -= 3; \
	} \
	fromPixel = (fromSize *) ((char *) fromSize + step); \
  } \
  fromPixel = (fromSize *) ((char *) fromSize - step); \
  toPixel++; \
  r = *fromPixel & fromRed; \
  g = *fromPixel & fromGreen; \
  b = *fromPixel & fromBlue; \
  a = fromAlpha ? *fromPixel & fromAlpha : 0xFFFFFFFF; \
  Int2Char t; \
  t.all =   (dublicate (r, redFactor,   redShift)   & toRed) \
	      | (dublicate (g, greenFactor, greenShift) & toGreen) \
	      | (dublicate (b, blueFactor,  blueShift)  & toBlue) \
	      | (dublicate (a, alphaFactor, alphaShift) & toAlpha); \
  toPixel[0] = t.piece0; \
  toPixel[1] = t.piece1; \
  toPixel[2] = t.piece2; \
}

#define OddBits2OddBits(fromRed,fromGreen,fromBlue,fromAlpha,toRed,toGreen,toBlue,toAlpha) \
{ \
  uint8_t * fromPixel = (uint8_t *) i->base () + i->stride * image.height - step - 4; \
  uint8_t * begin     = (uint8_t *) o->base (); \
  uint8_t * toPixel   = begin + o->stride * result.height - 4; \
  uint32_t r; \
  uint32_t g; \
  uint32_t b; \
  uint32_t a; \
  while (toPixel > begin) \
  { \
	uint8_t * rowBegin = max (toPixel - o->stride, begin); \
	while (toPixel > rowBegin) \
	{ \
	  uint32_t t = * (uint32_t *) fromPixel; \
	  fromPixel -= 3; \
	  r = t & fromRed; \
	  g = t & fromGreen; \
	  b = t & fromBlue; \
	  a = fromAlpha ? t & fromAlpha : 0xFFFFFFFF; \
	  * (uint32_t *) toPixel =   (dublicate (r, redFactor,   redShift)   & toRed) \
	                           | (dublicate (g, greenFactor, greenShift) & toGreen) \
	                           | (dublicate (b, blueFactor,  blueShift)  & toBlue) \
	                           | (dublicate (a, alphaFactor, alphaShift) & toAlpha); \
	  toPixel -= 3; \
	} \
	fromPixel -= step; \
  } \
  fromPixel += step + 1; \
  toPixel++; \
  Int2Char t; \
  t.piece0 = fromPixel[0]; \
  t.piece1 = fromPixel[1]; \
  t.piece2 = fromPixel[2]; \
  r = t.all & fromRed; \
  g = t.all & fromGreen; \
  b = t.all & fromBlue; \
  a = fromAlpha ? t.all & fromAlpha : 0xFFFFFFFF; \
  t.all =   (dublicate (r, redFactor,   redShift)   & toRed) \
          | (dublicate (g, greenFactor, greenShift) & toGreen) \
          | (dublicate (b, blueFactor,  blueShift)  & toBlue) \
          | (dublicate (a, alphaFactor, alphaShift) & toAlpha); \
  toPixel[0] = t.piece0; \
  toPixel[1] = t.piece1; \
  toPixel[2] = t.piece2; \
}

#define GrayFloat2OddBits(fromSize) \
{ \
  fromSize * fromPixel = (fromSize *) ((char *) i->base () + i->stride * image.height - step) - 1; \
  uint8_t *  begin     = (uint8_t *) o->base (); \
  uint8_t *  toPixel   = begin + o->stride * result.height; \
  while (toPixel > begin) \
  { \
	uint8_t * rowBegin = max (toPixel - o->stride, begin); \
	while (toPixel > rowBegin) \
	{ \
	  fromSize v = min (max (*fromPixel--, (fromSize) 0.0), (fromSize) 1.0); \
	  uint32_t t = lutFloat2Char[(uint16_t) (65535 * v)]; \
	  * (uint32_t *) toPixel =   (roll (t, redShift)   & redMask) \
	                           | (roll (t, greenShift) & greenMask) \
	                           | (roll (t, blueShift)  & blueMask) \
	                           | alphaMask; \
	  toPixel -= 3; \
	} \
	fromPixel = (fromSize *) ((char *) fromPixel - step); \
  } \
  fromPixel = (fromSize *) ((char *) fromPixel + step); \
  toPixel++; \
  fromSize v = min (max (*fromPixel, (fromSize) 0.0), (fromSize) 1.0); \
  Int2Char t; \
  t.all = lutFloat2Char[(uint16_t) (65535 * v)]; \
  t.all =   (roll (t, redShift)   & redMask) \
          | (roll (t, greenShift) & greenMask) \
          | (roll (t, blueShift)  & blueMask) \
          | alphaMask; \
  toPixel[0] = t.piece0; \
  toPixel[1] = t.piece1; \
  toPixel[2] = t.piece2; \
}

#endif

void
PixelFormatRGBABits::fromGrayChar (const Image & image, Image & result) const
{
  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  PixelBufferPacked * o = (PixelBufferPacked *) result.buffer;
  assert (i  &&  o);

  int redShift;
  int greenShift;
  int blueShift;
  int alphaShift;
  shift (0xFF, 0xFF, 0xFF, 0, redShift, greenShift, blueShift, alphaShift);
  redShift *= -1;
  greenShift *= -1;
  blueShift *= -1;
  alphaShift *= -1;
  uint32_t redFactor   = prepareDublicate (redShift,   8);
  uint32_t greenFactor = prepareDublicate (greenShift, 8);
  uint32_t blueFactor  = prepareDublicate (blueShift,  8);
  uint32_t alphaFactor = prepareDublicate (alphaShift, 0);

  const int step = i->stride - image.width;

  switch ((int) depth)
  {
    case 1:
	  Bits2Bits (uint8_t, uint8_t, 0xFF, 0xFF, 0xFF, 0, redMask, greenMask, blueMask, alphaMask);
	  break;
    case 2:
	  Bits2Bits (uint8_t, uint16_t, 0xFF, 0xFF, 0xFF, 0, redMask, greenMask, blueMask, alphaMask);
	  break;
    case 3:
	  Bits2OddBits (uint8_t, 0xFF, 0xFF, 0xFF, 0, redMask, greenMask, blueMask, alphaMask);
	  break;
    case 4:
    default:
	  Bits2Bits (uint8_t, uint32_t, 0xFF, 0xFF, 0xFF, 0, redMask, greenMask, blueMask, alphaMask);
  }
}

void
PixelFormatRGBABits::fromGrayShort (const Image & image, Image & result) const
{
  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  PixelBufferPacked * o = (PixelBufferPacked *) result.buffer;
  assert (i  &&  o);

  const int grayShift = ((const PixelFormatGrayShort *) image.format)->grayShift;
  int redShift;
  int greenShift;
  int blueShift;
  int alphaShift;
  shift (0xFF, 0xFF, 0xFF, 0, redShift, greenShift, blueShift, alphaShift);
  redShift *= -1;
  greenShift *= -1;
  blueShift *= -1;

  const int step = i->stride - image.width * sizeof (short);

# define GrayShort2Bits(toSize) \
  { \
    uint16_t * fromPixel = (uint16_t *) i->base (); \
	toSize *   toPixel   = (toSize *)   o->base (); \
	toSize *   end       = toPixel + result.width * result.height; \
	while (toPixel < end) \
	{ \
	  toSize * rowEnd = toPixel + result.width; \
	  while (toPixel < rowEnd) \
	  { \
		uint32_t t = lutFloat2Char[*fromPixel++ << grayShift]; \
		*toPixel++ =   (roll (t, redShift)   & redMask) \
		             | (roll (t, greenShift) & greenMask) \
		             | (roll (t, blueShift)  & blueMask) \
		             | alphaMask; \
	  } \
	  fromPixel = (uint16_t *) ((char *) fromPixel + step); \
	} \
  }

  switch ((int) depth)
  {
    case 1:
	  GrayShort2Bits (uint8_t);
	  break;
    case 2:
	  GrayShort2Bits (uint16_t);
	  break;
    case 3:
	{
#     if BYTE_ORDER == LITTLE_ENDIAN
	  uint16_t * fromPixel = (uint16_t *) i->base ();
	  uint8_t *  toPixel   = (uint8_t *)  o->base ();
	  uint8_t *  end       = toPixel + o->stride * result.height - 3;
	  while (toPixel < end)
	  {
		uint8_t * rowEnd = min (toPixel + o->stride, end);
		while (toPixel < rowEnd)
		{
		  uint32_t v = lutFloat2Char[*fromPixel++ << grayShift];
		  * (uint32_t *) toPixel =   (roll (v, redShift)   & redMask)
		                           | (roll (v, greenShift) & greenMask)
		                           | (roll (v, blueShift)  & blueMask)
		                           | alphaMask;
		  toPixel += 3;
		}
		fromPixel = (uint16_t *) ((char *) fromPixel + step);
	  }
	  fromPixel = (uint16_t *) ((char *) fromPixel - step);
	  uint32_t v = lutFloat2Char[*fromPixel++ << grayShift];
	  Int2Char t;
	  t.all =   (roll (v, redShift)   & redMask)
		      | (roll (v, greenShift) & greenMask)
		      | (roll (v, blueShift)  & blueMask)
		      | alphaMask;
	  toPixel[0] = t.piece0;
	  toPixel[1] = t.piece1;
	  toPixel[2] = t.piece2;
#     elif BYTE_ORDER == BIG_ENDIAN
	  uint16_t * fromPixel = (uint16_t *) ((char *) i->base () + i->stride * image.height - step) - 1;
	  uint8_t *  begin     = (uint8_t *) o->base ();
	  uint8_t *  toPixel   = end + o->stride * result.height - 4;
	  while (toPixel > begin)
	  {
		uint8_t * rowBegin = max (toPixel - o->stride, begin);
		while (toPixel > rowBegin)
		{
		  uint32_t v = lutFloat2Char[*fromPixel-- << grayShift];
		  * (uint32_t *) toPixel =   (roll (v, redShift)   & redMask)
		                               | (roll (v, greenShift) & greenMask)
		                               | (roll (v, blueShift)  & blueMask)
		                               | alphaMask;
		  toPixel -= 3;
		}
		fromPixel = (uint16_t *) ((char *) fromPixel - step);
	  }
	  fromPixel = (uint16_t *) ((char *) fromPixel + step);
	  toPixel++;
	  uint32_t v = lutFloat2Char[*fromPixel << grayShift];
	  Int2Char t;
	  t.all =   (roll (v, redShift)   & redMask)
		      | (roll (v, greenShift) & greenMask)
		      | (roll (v, blueShift)  & blueMask)
		      | alphaMask;
	  toPixel[0] = t.piece0;
	  toPixel[1] = t.piece1;
	  toPixel[2] = t.piece2;
#     endif
	  break;
	}
    case 4:
    default:
	  GrayShort2Bits (uint32_t);
  }
}

void
PixelFormatRGBABits::fromGrayFloat (const Image & image, Image & result) const
{
  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  PixelBufferPacked * o = (PixelBufferPacked *) result.buffer;
  assert (i  &&  o);

  int redShift;
  int greenShift;
  int blueShift;
  int alphaShift;
  shift (0xFF, 0xFF, 0xFF, 0, redShift, greenShift, blueShift, alphaShift);
  redShift *= -1;
  greenShift *= -1;
  blueShift *= -1;

  const int step = i->stride - image.width * sizeof (float);

  switch ((int) depth)
  {
    case 1:
	  GrayFloat2Bits (float, uint8_t);
	  break;
    case 2:
	  GrayFloat2Bits (float, uint16_t);
	  break;
    case 3:
	  GrayFloat2OddBits (float);
	  break;
    case 4:
    default:
	  GrayFloat2Bits (float, uint32_t);
  }
}

void
PixelFormatRGBABits::fromGrayDouble (const Image & image, Image & result) const
{
  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  PixelBufferPacked * o = (PixelBufferPacked *) result.buffer;
  assert (i  &&  o);

  int redShift;
  int greenShift;
  int blueShift;
  int alphaShift;
  shift (0xFF, 0xFF, 0xFF, 0, redShift, greenShift, blueShift, alphaShift);
  redShift *= -1;
  greenShift *= -1;
  blueShift *= -1;

  const int step = i->stride - image.width * sizeof (double);

  switch ((int) depth)
  {
    case 1:
	  GrayFloat2Bits (double, uint8_t);
	  break;
    case 2:
	  GrayFloat2Bits (double, uint16_t);
	  break;
    case 3:
	  GrayFloat2OddBits (double);
	  break;
    case 4:
    default:
	  GrayFloat2Bits (double, uint32_t);
  }
}

void
PixelFormatRGBABits::fromRGBABits (const Image & image, Image & result) const
{
  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  PixelBufferPacked * o = (PixelBufferPacked *) result.buffer;
  assert (i  &&  o);

  const PixelFormatRGBABits * that = (const PixelFormatRGBABits *) image.format;

  int redShift;
  int greenShift;
  int blueShift;
  int alphaShift;
  that->shift (redMask, greenMask, blueMask, alphaMask, redShift, greenShift, blueShift, alphaShift);
  uint32_t redFactor   = prepareDublicate (redShift,   that->redBits);
  uint32_t greenFactor = prepareDublicate (greenShift, that->greenBits);
  uint32_t blueFactor  = prepareDublicate (blueShift,  that->blueBits);
  uint32_t alphaFactor = prepareDublicate (alphaShift, that->alphaBits);

  const int thatDepth = (int) that->depth;
  const int step = i->stride - image.width * thatDepth;

  switch ((int) depth)
  {
    case 1:
	  switch (thatDepth)
	  {
	    case 1:
		  Bits2Bits (uint8_t, uint8_t, that->redMask, that->greenMask, that->blueMask, that->alphaMask, redMask, greenMask, blueMask, alphaMask);
		  break;
	    case 2:
		  Bits2Bits (uint16_t, uint8_t, that->redMask, that->greenMask, that->blueMask, that->alphaMask, redMask, greenMask, blueMask, alphaMask);
		  break;
	    case 3:
		  OddBits2Bits (uint8_t, that->redMask, that->greenMask, that->blueMask, that->alphaMask, redMask, greenMask, blueMask, alphaMask);
		  break;
	    case 4:
	    default:
		  Bits2Bits (uint32_t, uint8_t, that->redMask, that->greenMask, that->blueMask, that->alphaMask, redMask, greenMask, blueMask, alphaMask);
	  }
	  break;
    case 2:
	  switch (thatDepth)
	  {
	    case 1:
		  Bits2Bits (uint8_t, uint16_t, that->redMask, that->greenMask, that->blueMask, that->alphaMask, redMask, greenMask, blueMask, alphaMask);
		  break;
	    case 2:
		  Bits2Bits (uint16_t, uint16_t, that->redMask, that->greenMask, that->blueMask, that->alphaMask, redMask, greenMask, blueMask, alphaMask);
		  break;
	    case 3:
		  OddBits2Bits (uint16_t, that->redMask, that->greenMask, that->blueMask, that->alphaMask, redMask, greenMask, blueMask, alphaMask);
		  break;
	    case 4:
	    default:
		  Bits2Bits (uint32_t, uint16_t, that->redMask, that->greenMask, that->blueMask, that->alphaMask, redMask, greenMask, blueMask, alphaMask);
	  }
	  break;
    case 3:
	  switch (thatDepth)
	  {
	    case 1:
		  Bits2OddBits (uint8_t, that->redMask, that->greenMask, that->blueMask, that->alphaMask, redMask, greenMask, blueMask, alphaMask);
		  break;
	    case 2:
		  Bits2OddBits (uint16_t, that->redMask, that->greenMask, that->blueMask, that->alphaMask, redMask, greenMask, blueMask, alphaMask);
		  break;
	    case 3:
		  OddBits2OddBits (that->redMask, that->greenMask, that->blueMask, that->alphaMask, redMask, greenMask, blueMask, alphaMask);
		  break;
	    case 4:
	    default:
		  Bits2OddBits (uint32_t, that->redMask, that->greenMask, that->blueMask, that->alphaMask, redMask, greenMask, blueMask, alphaMask);
	  }
	  break;
    case 4:
    default:
	  switch (thatDepth)
	  {
	    case 1:
		  Bits2Bits (uint8_t, uint32_t, that->redMask, that->greenMask, that->blueMask, that->alphaMask, redMask, greenMask, blueMask, alphaMask);
		  break;
	    case 2:
		  Bits2Bits (uint16_t, uint32_t, that->redMask, that->greenMask, that->blueMask, that->alphaMask, redMask, greenMask, blueMask, alphaMask);
		  break;
	    case 3:
		  OddBits2Bits (uint32_t, that->redMask, that->greenMask, that->blueMask, that->alphaMask, redMask, greenMask, blueMask, alphaMask);
		  break;
	    case 4:
	    default:
		  Bits2Bits (uint32_t, uint32_t, that->redMask, that->greenMask, that->blueMask, that->alphaMask, redMask, greenMask, blueMask, alphaMask);
	  }
	  break;
  }
}

void
PixelFormatRGBABits::fromYCbCr (const Image & image, Image & result) const
{
  PixelBufferPlanar * i = (PixelBufferPlanar *) image.buffer;
  PixelBufferPacked * o = (PixelBufferPacked *) result.buffer;
  assert (i  &&  o);

  int redShift;
  int greenShift;
  int blueShift;
  int alphaShift;
  shift (0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0, redShift, greenShift, blueShift, alphaShift);
  redShift *= -1;
  greenShift *= -1;
  blueShift *= -1;

  // When converting to channels with 8 or more bits, round up.  When
  // converting to channels with fewer bits, truncate the effective 8-bit value.
  int roundR = 0x800000 >> max (redBits,   8);
  int roundG = 0x800000 >> max (greenBits, 8);
  int roundB = 0x800000 >> max (blueBits,  8);

  uint8_t * fromPixel = (uint8_t *) i->plane0;
  uint8_t * Cb        = (uint8_t *) i->plane1;
  uint8_t * Cr        = (uint8_t *) i->plane2;

  assert (image.width % i->ratioH == 0  &&  image.height % i->ratioV == 0);
  int rowWidth = result.width;
  int blockRowWidth = i->ratioH;
  int blockSwath = result.width * i->ratioV;
  int step12 = i->stride12 - image.width / i->ratioH;
  int toStep   = result.width * i->ratioV - result.width;
  int fromStep = i->stride0   * i->ratioV - image.width;
  int toBlockStep   = i->ratioH - result.width * i->ratioV;
  int fromBlockStep = i->ratioH - i->stride0   * i->ratioV;
  int toBlockRowStep   = result.width - i->ratioH;
  int fromBlockRowStep = i->stride0   - i->ratioH;

  // This can be made more efficient by making code specific to certain
  // combinations of ratioH and ratioV which unrolls the inner loops.

#define YCbCr2Bits(toSize) \
{ \
  toSize * toPixel = (toSize *) o->base (); \
  toSize * end     = toPixel + result.width * result.height; \
  while (toPixel < end) \
  { \
	toSize * rowEnd = toPixel + rowWidth; \
	while (toPixel < rowEnd) \
	{ \
	  int u = *Cb++ - 128; \
	  int v = *Cr++ - 128; \
	  int tr =               0x19895 * v; \
	  int tg =  0x644A * u +  0xD01F * v; \
	  int tb = 0x20469 * u; \
	  toSize * blockEnd = toPixel + blockSwath; \
	  while (toPixel < blockEnd) \
	  { \
		toSize * blockRowEnd = toPixel + blockRowWidth; \
		while (toPixel < blockRowEnd) \
		{ \
		  int y = (*fromPixel++ - 16) * 0x12A15; \
		  uint32_t r = min (max (y + tr + roundR, 0), 0xFFFFFF); \
		  uint32_t g = min (max (y - tg + roundG, 0), 0xFFFFFF); \
		  uint32_t b = min (max (y + tb + roundB, 0), 0xFFFFFF); \
		  *toPixel++ =   (roll (r, redShift)   & redMask) \
			           | (roll (g, greenShift) & greenMask) \
			           | (roll (b, blueShift)  & blueMask) \
			           | alphaMask; \
		} \
		toPixel += toBlockRowStep; \
		fromPixel += fromBlockRowStep; \
	  } \
	  toPixel += toBlockStep; \
	  fromPixel += fromBlockStep; \
	} \
	toPixel += toStep; \
	fromPixel += fromStep; \
	Cb += step12; \
	Cr += step12; \
  } \
}

  switch ((int) depth)
  {
    case 1:
	  YCbCr2Bits (uint8_t);
	  break;
    case 2:
	  YCbCr2Bits (uint16_t);
	  break;
    case 3:
	{
	  blockSwath *= 3;
	  rowWidth *= 3;
	  blockRowWidth *= 3;
	  toBlockRowStep *= 3;
	  toBlockStep *= 3;
	  toStep *= 3;
	  uint8_t * toPixel = (uint8_t *) o->base ();
	  uint8_t * end     = toPixel + result.width * result.height * 3;
	  while (toPixel < end)
	  {
		uint8_t * rowEnd = toPixel + rowWidth;
		while (toPixel < rowEnd)
		{
		  int u = *Cb++ - 128;
		  int v = *Cr++ - 128;
		  int tr =               0x19895 * v;
		  int tg =  0x644A * u +  0xD01F * v;
		  int tb = 0x20469 * u;
		  uint8_t * blockEnd = toPixel + blockSwath;
		  while (toPixel < blockEnd)
		  {
			uint8_t * blockRowEnd = toPixel + blockRowWidth;
			while (toPixel < blockRowEnd)
			{
			  int y = (*fromPixel++ - 16) * 0x12A15;
			  uint32_t r = min (max (y + tr + roundR, 0), 0xFFFFFF);
			  uint32_t g = min (max (y - tg + roundG, 0), 0xFFFFFF);
			  uint32_t b = min (max (y + tb + roundB, 0), 0xFFFFFF);
			  Int2Char t;
			  t.all =   (roll (r, redShift)   & redMask)
				      | (roll (g, greenShift) & greenMask)
				      | (roll (b, blueShift)  & blueMask)
				      | alphaMask;
			  toPixel[0] = t.piece0;
			  toPixel[1] = t.piece1;
			  toPixel[2] = t.piece2;
			  toPixel += 3;
			}
			toPixel += toBlockRowStep;
			fromPixel += fromBlockRowStep;
		  }
		  toPixel += toBlockStep;
		  fromPixel += fromBlockStep;
		}
		toPixel += toStep;
		fromPixel += fromStep;
		Cb += step12;
		Cr += step12;
	  }
	  break;
	}
    case 4:
	  YCbCr2Bits (uint32_t);
	  break;
    default:
	  throw "Unhandled depth in PixelFormatRGBABits::fromYCbCr";
  }
}

bool
PixelFormatRGBABits::operator == (const PixelFormat & that) const
{
  if (depth != that.depth)
  {
	return false;
  }
  if (const PixelFormatRGBABits * other = dynamic_cast<const PixelFormatRGBABits *> (& that))
  {
	return    redMask   == other->redMask
	       && greenMask == other->greenMask
	       && blueMask  == other->blueMask
	       && alphaMask == other->alphaMask;
  }
  if (const PixelFormatGrayChar * other = dynamic_cast<const PixelFormatGrayChar *> (& that))
  {
	return    redMask   == 0xFF
	       && greenMask == 0xFF
	       && blueMask  == 0xFF;
  }
  if (const PixelFormatGrayShort * other = dynamic_cast<const PixelFormatGrayShort *> (& that))
  {
	return    redMask   == other->grayMask
	       && greenMask == other->grayMask
	       && blueMask  == other->grayMask;
  }
  return false;
}

void
PixelFormatRGBABits::shift (uint32_t redMask, uint32_t greenMask, uint32_t blueMask, uint32_t alphaMask, int & redShift, int & greenShift, int & blueShift, int & alphaShift) const
{
  uint32_t t;

  redShift = 0;
  if (redMask  &&  this->redMask)
  {
	while (redMask >>= 1) {redShift++;}
	t = this->redMask;
	while (t >>= 1) {redShift--;}
  }

  greenShift = 0;
  if (greenMask  &&  this->greenMask)
  {
	while (greenMask >>= 1) {greenShift++;}
	t = this->greenMask;
	while (t >>= 1) {greenShift--;}
  }

  blueShift = 0;
  if (blueMask  &&  this->blueMask)
  {
	while (blueMask >>= 1) {blueShift++;}
	t = this->blueMask;
	while (t >>= 1) {blueShift--;}
  }

  alphaShift = 0;
  if (alphaMask  &&  this->alphaMask)
  {
	while (alphaMask >>= 1) {alphaShift++;}
	t = this->alphaMask;
	while (t >>= 1) {alphaShift--;}
  }
}

int
PixelFormatRGBABits::countBits (uint32_t mask)
{
  int count = 0;
  while (mask)
  {
	if (mask & 0x1) count++;
	mask >>= 1;
  }
  return count;
}

uint32_t
PixelFormatRGBABits::getRGBA (void * pixel) const
{
  int redShift;
  int greenShift;
  int blueShift;
  int alphaShift;
  shift (0xFF000000, 0xFF0000, 0xFF00, 0xFF, redShift, greenShift, blueShift, alphaShift);

  Int2Char value;

  switch ((int) depth)
  {
    case 1:
	  value.all = *((uint8_t *) pixel);
	  break;
    case 2:
	  value.all = *((uint16_t *) pixel);
	  break;
    case 3:
	  value.piece0 = ((uint8_t *) pixel)[0];
	  value.piece1 = ((uint8_t *) pixel)[1];
	  value.piece2 = ((uint8_t *) pixel)[2];
	  break;
    case 4:
    default:
	  value.all = *((uint32_t *) pixel);
	  break;
  }

  uint32_t r = value.all & redMask;
  uint32_t g = value.all & greenMask;
  uint32_t b = value.all & blueMask;
  uint32_t a = value.all & alphaMask;

  uint32_t redFactor   = prepareDublicate (redShift,   redBits);
  uint32_t greenFactor = prepareDublicate (greenShift, greenBits);
  uint32_t blueFactor  = prepareDublicate (blueShift,  blueBits);
  int ab = alphaBits;
  if (! alphaMask)
  {
	a = 0xFF;
	ab = 8;
  }
  uint32_t alphaFactor = prepareDublicate (alphaShift, ab);

  return   (dublicate (r, redFactor,   redShift)   & 0xFF000000)
		 | (dublicate (g, greenFactor, greenShift) &   0xFF0000)
		 | (dublicate (b, blueFactor,  blueShift)  &     0xFF00)
		 | (dublicate (a, alphaFactor, alphaShift) &       0xFF);
}

uint8_t
PixelFormatRGBABits::getAlpha (void * pixel) const
{
  if (! alphaMask) return 0xFF;

  Int2Char value;
  switch ((int) depth)
  {
    case 1:
	  value.all = *((uint8_t *) pixel);
	  break;
    case 2:
	  value.all = *((uint16_t *) pixel);
	  break;
    case 3:
	  value.piece0 = ((uint8_t *) pixel)[0];
	  value.piece1 = ((uint8_t *) pixel)[1];
	  value.piece2 = ((uint8_t *) pixel)[2];
	  break;
    case 4:
    default:
	  value.all = *((uint32_t *) pixel);
	  break;
  }
  uint32_t a = value.all & alphaMask;

  int shift = 7;
  uint32_t mask = alphaMask;
  while (mask >>= 1) {shift--;}
  uint32_t factor = prepareDublicate (shift, alphaBits);

  return dublicate (a, factor, shift) & 0xFF;
}

/**
   This function assumes that this format does not use more than 8 bits per
   channel, since the connotation of a "bits" format is one that is smaller
   than RGBAChar.  This allows us to use roll() rather than dublicate(), and
   to avoid the associated extra setup.
 **/
void
PixelFormatRGBABits::setRGBA (void * pixel, uint32_t rgba) const
{
  uint32_t r = rgba & 0xFF000000;
  uint32_t g = rgba &   0xFF0000;
  uint32_t b = rgba &     0xFF00;
  uint32_t a = rgba &       0xFF;

  int redShift;
  int greenShift;
  int blueShift;
  int alphaShift;
  shift (0xFF000000, 0xFF0000, 0xFF00, 0xFF, redShift, greenShift, blueShift, alphaShift);

  Int2Char value;
  value.all =   (roll (r, -redShift)   & redMask)
	          | (roll (g, -greenShift) & greenMask)
              | (roll (b, -blueShift)  & blueMask)
              | (roll (a, -alphaShift) & alphaMask);

  switch ((int) depth)
  {
    case 1:
	  *((uint8_t *) pixel) = value.all;
	  break;
    case 2:
	  *((uint16_t *) pixel) = value.all;
	  break;
    case 3:
	  ((uint8_t *) pixel)[0] = value.piece0;
	  ((uint8_t *) pixel)[1] = value.piece1;
	  ((uint8_t *) pixel)[2] = value.piece2;
	  break;
    case 4:
    default:
	  *((uint32_t *) pixel) = value.all;
	  break;
  }
}

/**
   Bit masking safely preserves data outside the current pixel, so this
   function does not try to handle different pixel sizes explicitly.
   However, this leaves it vulnerable to writing past the end of the buffer
   on the last pixel.
**/
void
PixelFormatRGBABits::setAlpha (void * pixel, uint8_t alpha) const
{
  int shift = -7;
  uint32_t mask = alphaMask;
  while (mask >>= 1) {shift++;}

  uint32_t a = alpha;
  a = roll (a, shift) & alphaMask;

  * (uint32_t *) pixel = a | ((* (uint32_t *) pixel) & ~alphaMask);
}


// class PixelFormatRGBAChar ---------------------------------------------------

PixelFormatRGBAChar::PixelFormatRGBAChar ()
#if BYTE_ORDER == LITTLE_ENDIAN
: PixelFormatRGBABits (4, 0xFF, 0xFF00, 0xFF0000, 0xFF000000)
#else
: PixelFormatRGBABits (4, 0xFF000000, 0xFF0000, 0xFF00, 0xFF)
#endif
{
}

Image
PixelFormatRGBAChar::filter (const Image & image)
{
  Image result (*this);

  if (*image.format == *this)
  {
	result = image;
	return result;
  }

  result.resize (image.width, image.height);
  result.timestamp = image.timestamp;
  if (result.width <= 0  ||  result.height <= 0) return result;

  if (typeid (* image.format) == typeid (PixelFormatGrayChar))
  {
	fromGrayChar (image, result);
  }
  else if (typeid (* image.format) == typeid (PixelFormatGrayShort))
  {
	fromGrayShort (image, result);
  }
  else if (typeid (* image.format) == typeid (PixelFormatGrayFloat))
  {
	fromGrayFloat (image, result);
  }
  else if (typeid (* image.format) == typeid (PixelFormatGrayDouble))
  {
	fromGrayDouble (image, result);
  }
  else if (typeid (* image.format) == typeid (PixelFormatRGBChar))
  {
	fromRGBChar (image, result);
  }
  else if ((const PixelFormatRGBABits *) image.format)
  {
	fromRGBABits (image, result);
  }
  else if (typeid (* image.format) == typeid (PixelFormatPackedYUV))
  {
	fromPackedYUV (image, result);
  }
  else if (typeid (* image.format) == typeid (PixelFormatPlanarYCbCr))
  {
	fromYCbCr (image, result);
  }
  else
  {
	fromAny (image, result);
  }

  return result;
}

void
PixelFormatRGBAChar::fromGrayChar (const Image & image, Image & result) const
{
  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  PixelBufferPacked * o = (PixelBufferPacked *) result.buffer;
  assert (i  &&  o);

  uint8_t * fromPixel = (uint8_t *) i->base ();
  uint8_t * toPixel   = (uint8_t *) o->base ();
  uint8_t * end       = toPixel + result.width * result.height * 4;
  const int step = i->stride - image.width;
  while (toPixel < end)
  {
	uint8_t * rowEnd = toPixel + o->stride;
	while (toPixel < rowEnd)
	{
	  uint8_t t = *fromPixel++;
	  toPixel[0] = t;
	  toPixel[1] = t;
	  toPixel[2] = t;
	  toPixel[3] = 0xFF;
	  toPixel += 4;
	}
	fromPixel += step;
  }
}

void
PixelFormatRGBAChar::fromGrayFloat (const Image & image, Image & result) const
{
  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  PixelBufferPacked * o = (PixelBufferPacked *) result.buffer;
  assert (i  &&  o);

  float *   fromPixel = (float *)   i->base ();
  uint8_t * toPixel   = (uint8_t *) o->base ();
  uint8_t * end       = toPixel + result.width * result.height * 4;
  const int step = i->stride - image.width * sizeof (float);
  while (toPixel < end)
  {
	uint8_t * rowEnd = toPixel + result.width * 4;
	while (toPixel < rowEnd)
	{
	  float v = min (max (*fromPixel++, 0.0f), 1.0f);
	  uint8_t t = lutFloat2Char[(uint16_t) (65535 * v)];
	  toPixel[0] = t;
	  toPixel[1] = t;
	  toPixel[2] = t;
	  toPixel[3] = 0xFF;
	  toPixel += 4;
	}
	fromPixel = (float *) ((char *) fromPixel + step);
  }
}

void
PixelFormatRGBAChar::fromGrayDouble (const Image & image, Image & result) const
{
  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  PixelBufferPacked * o = (PixelBufferPacked *) result.buffer;
  assert (i  &&  o);

  double *  fromPixel = (double *)  i->base ();
  uint8_t * toPixel   = (uint8_t *) o->base ();
  uint8_t * end       = toPixel + result.width * result.height * 4;
  const int step = i->stride - image.width * sizeof (double);
  while (toPixel < end)
  {
	uint8_t * rowEnd = toPixel + result.width * 4;
	while (toPixel < rowEnd)
	{
	  double v = min (max (*fromPixel++, 0.0), 1.0);
	  uint8_t t = lutFloat2Char[(uint16_t) (65535 * v)];
	  toPixel[0] = t;
	  toPixel[1] = t;
	  toPixel[2] = t;
	  toPixel[3] = 0xFF;
	  toPixel += 4;
	}
	fromPixel = (double *) ((char *) fromPixel + step);
  }
}

void
PixelFormatRGBAChar::fromRGBChar (const Image & image, Image & result) const
{
  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  PixelBufferPacked * o = (PixelBufferPacked *) result.buffer;
  assert (i  &&  o);

  uint8_t * fromPixel = (uint8_t *) i->base ();
  uint8_t * toPixel   = (uint8_t *) o->base ();
  uint8_t * end       = toPixel + result.width * result.height * 4;
  const int step = i->stride - image.width * 3;
  while (toPixel < end)
  {
	uint8_t * rowEnd = toPixel + result.width * 4;
	while (toPixel < rowEnd)
	{
	  toPixel[0] = fromPixel[0];
	  toPixel[1] = fromPixel[1];
	  toPixel[2] = fromPixel[2];
	  toPixel[3] = 0xFF;
	  toPixel += 4;
	  fromPixel += 3;
	}
	fromPixel += step;
  }
}

void
PixelFormatRGBAChar::fromPackedYUV (const Image & image, Image & result) const
{
  const PixelFormatPackedYUV *     sourceFormat = (const PixelFormatPackedYUV *) image.format;
  assert (sourceFormat);
  PixelBufferGroups *              i            = (PixelBufferGroups *) image.buffer;
  assert (i);
  PixelFormatPackedYUV::YUVindex * table        = sourceFormat->table;
  uint8_t *                        fromPixel    = (uint8_t *) i->memory;
  const int                        bytes        = i->bytes;
  const int                        fromStep     = i->stride - (int) floor ((image.width + 0.5) / i->pixels) * bytes;

  PixelBufferPacked * o = (PixelBufferPacked *) result.buffer;
  assert (o);
  uint32_t *       toPixel  = (uint32_t *) o->base ();
  const uint32_t * end      = (uint32_t *) ((char *) toPixel + o->stride * result.height);
  const int        rowWidth = (int) roundp (result.width * depth);
  const int        toStep   = o->stride - rowWidth;

  while (toPixel < end)
  {
	PixelFormatPackedYUV::YUVindex * index = table;
	const uint32_t * rowEnd = (uint32_t *) ((char *) toPixel + rowWidth);
	while (toPixel < rowEnd)
	{
	  int y = fromPixel[index->y] << 16;
	  int u = fromPixel[index->u] - 128;
	  int v = fromPixel[index->v] - 128;

	  uint32_t r = min (max (y               + 0x166F7 * v + 0x8000, 0), 0xFFFFFF);
	  uint32_t g = min (max (y -  0x5879 * u -  0xB6E9 * v + 0x8000, 0), 0xFFFFFF);
	  uint32_t b = min (max (y + 0x1C560 * u               + 0x8000, 0), 0xFFFFFF);

#     if BYTE_ORDER == LITTLE_ENDIAN
	  *toPixel++ = (b & 0xFF0000) | ((g >> 8) & 0xFF00) | ((r >> 16) & 0xFF) | 0xFF000000;
#     elif BYTE_ORDER == BIG_ENDIAN
	  *toPixel++ = ((r << 8) & 0xFF000000) | (g & 0xFF0000) | ((b >> 8) & 0xFF00) | 0xFF;
#     endif

	  index++;
	  if (index->y < 0)
	  {
		index = table;
		fromPixel += bytes;
	  }
	}
	fromPixel += fromStep;
	toPixel = (uint32_t *) ((char *) toPixel + toStep);
  }
}

void
PixelFormatRGBAChar::fromAny (const Image & image, Image & result) const
{
  const PixelFormat * sourceFormat = image.format;

  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  PixelBufferPacked * o = (PixelBufferPacked *) result.buffer;
  assert (o);

  uint32_t * dest = (uint32_t *) o->base ();

  if (i)
  {
	uint32_t * end     = dest + result.width * result.height;
	uint32_t * rowEnd  = dest + result.width;  // Output of this conversion must have stride == row length.
	uint8_t * source      = (uint8_t *) i->base ();
	const int sourceDepth = (int) sourceFormat->depth;
	const int step        = i->stride - image.width * sourceDepth;
	while (dest < end)
	{
	  while (dest < rowEnd)
	  {
#       if BYTE_ORDER == LITTLE_ENDIAN
		*dest++ = bswap (sourceFormat->getRGBA (source));
#       else
		*dest++ = sourceFormat->getRGBA (source);
#       endif
		source += sourceDepth;
	  }
	  source += step;
	  rowEnd += result.width;
	}
  }
  else
  {
	for (int y = 0; y < image.height; y++)
	{
	  for (int x = 0; x < image.width; x++)
	  {
#       if BYTE_ORDER == LITTLE_ENDIAN
		*dest++ = bswap (sourceFormat->getRGBA (image.buffer->pixel (x, y)));
#       else
		*dest++ = sourceFormat->getRGBA (image.buffer->pixel (x, y));
#       endif
	  }
	}
  }
}

uint32_t
PixelFormatRGBAChar::getRGBA (void * pixel) const
{
# if BYTE_ORDER == LITTLE_ENDIAN
  return bswap (*((uint32_t *) pixel));
# else
  return *((uint32_t *) pixel);
# endif
}

uint8_t
PixelFormatRGBAChar::getAlpha (void * pixel) const
{
  return ((uint8_t *) pixel)[3];
}

void
PixelFormatRGBAChar::setRGBA (void * pixel, uint32_t rgba) const
{
# if BYTE_ORDER == LITTLE_ENDIAN
  *((uint32_t *) pixel) = bswap (rgba);
# else
  *((uint32_t *) pixel) = rgba;
# endif
}

void
PixelFormatRGBAChar::setAlpha (void * pixel, uint8_t alpha) const
{
  ((uint8_t *) pixel)[3] = alpha;
}


// class PixelFormatRGBChar ---------------------------------------------------

PixelFormatRGBChar::PixelFormatRGBChar ()
#if BYTE_ORDER == LITTLE_ENDIAN
: PixelFormatRGBABits (3, 0xFF, 0xFF00, 0xFF0000, 0x0)
#else
: PixelFormatRGBABits (3, 0xFF0000, 0xFF00, 0xFF, 0x0)
#endif
{
}

Image
PixelFormatRGBChar::filter (const Image & image)
{
  Image result (*this);

  if (*image.format == *this)
  {
	result = image;
	return result;
  }

  result.resize (image.width, image.height);
  result.timestamp = image.timestamp;
  if (result.width <= 0  ||  result.height <= 0) return result;

  if (typeid (* image.format) == typeid (PixelFormatGrayChar))
  {
	fromGrayChar (image, result);
  }
  else if (typeid (* image.format) == typeid (PixelFormatGrayShort))
  {
	fromGrayShort (image, result);
  }
  else if (typeid (* image.format) == typeid (PixelFormatGrayFloat))
  {
	fromGrayFloat (image, result);
  }
  else if (typeid (* image.format) == typeid (PixelFormatGrayDouble))
  {
	fromGrayDouble (image, result);
  }
  else if (typeid (* image.format) == typeid (PixelFormatRGBAChar))
  {
	fromRGBAChar (image, result);
  }
  else if (typeid (* image.format) == typeid (PixelFormatRGBABits))
  {
	fromRGBABits (image, result);
  }
  else
  {
	fromAny (image, result);
  }

  return result;
}

void
PixelFormatRGBChar::fromGrayChar (const Image & image, Image & result) const
{
  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  PixelBufferPacked * o = (PixelBufferPacked *) result.buffer;
  assert (i  &&  o);

  uint8_t * fromPixel = (uint8_t *) i->base ();
  uint8_t * toPixel   = (uint8_t *) o->base ();
  uint8_t * end       = toPixel + result.width * result.height * 3;
  const int step = i->stride - image.width;
  while (toPixel < end)
  {
	uint8_t * rowEnd = toPixel + result.width * 3;
	while (toPixel < rowEnd)
	{
	  uint8_t t = *fromPixel++;
	  toPixel[0] = t;
	  toPixel[1] = t;
	  toPixel[2] = t;
	  toPixel += 3;
	}
	fromPixel += step;
  }
}

void
PixelFormatRGBChar::fromGrayShort (const Image & image, Image & result) const
{
  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  PixelBufferPacked * o = (PixelBufferPacked *) result.buffer;
  assert (i  &&  o);

  uint16_t * fromPixel = (uint16_t *) i->base ();
  uint8_t *  toPixel   = (uint8_t *)  o->base ();
  uint8_t *  end       = toPixel + result.width * result.height * 3;
  const int grayShift = ((const PixelFormatGrayShort *) image.format)->grayShift;
  const int step = i->stride - image.width * sizeof (short);
  while (toPixel < end)
  {
	uint8_t * rowEnd = toPixel + result.width * 3;
	while (toPixel < rowEnd)
	{
	  uint8_t t = lutFloat2Char[*fromPixel++ << grayShift];
	  toPixel[0] = t;
	  toPixel[1] = t;
	  toPixel[2] = t;
	  toPixel += 3;
	}
	fromPixel = (uint16_t *) ((char *) fromPixel + step);
  }
}

void
PixelFormatRGBChar::fromGrayFloat (const Image & image, Image & result) const
{
  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  PixelBufferPacked * o = (PixelBufferPacked *) result.buffer;
  assert (i  &&  o);

  float *   fromPixel = (float *)   i->base ();
  uint8_t * toPixel   = (uint8_t *) o->base ();
  uint8_t * end       = toPixel + result.width * result.height * 3;
  const int step = i->stride - image.width * sizeof (float);
  while (toPixel < end)
  {
	uint8_t * rowEnd = toPixel + result.width * 3;
	while (toPixel < rowEnd)
	{
	  float v = min (max (*fromPixel++, 0.0f), 1.0f);
	  uint8_t t = lutFloat2Char[(uint16_t) (65535 * v)];
	  toPixel[0] = t;
	  toPixel[1] = t;
	  toPixel[2] = t;
	  toPixel += 3;
	}
	fromPixel = (float *) ((char *) fromPixel + step);
  }
}

void
PixelFormatRGBChar::fromGrayDouble (const Image & image, Image & result) const
{
  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  PixelBufferPacked * o = (PixelBufferPacked *) result.buffer;
  assert (i  &&  o);

  double *  fromPixel = (double *)  i->base ();
  uint8_t * toPixel   = (uint8_t *) o->base ();
  uint8_t * end       = toPixel + result.width * result.height * 3;
  const int step = i->stride - image.width * sizeof (double);
  while (toPixel < end)
  {
	uint8_t * rowEnd = toPixel + result.width * 3;
	while (toPixel < rowEnd)
	{
	  double v = min (max (*fromPixel++, 0.0), 1.0);
	  uint8_t t = lutFloat2Char[(uint16_t) (65535 * v)];
	  toPixel[0] = t;
	  toPixel[1] = t;
	  toPixel[2] = t;
	  toPixel += 3;
	}
	fromPixel = (double *) ((char *) fromPixel + step);
  }
}

void
PixelFormatRGBChar::fromRGBAChar (const Image & image, Image & result) const
{
  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  PixelBufferPacked * o = (PixelBufferPacked *) result.buffer;
  assert (i  &&  o);

  uint8_t * fromPixel = (uint8_t *) i->base ();
  uint8_t * toPixel   = (uint8_t *) o->base ();
  uint8_t * end       = toPixel + result.width * result.height * 3;
  const int step = i->stride - image.width * 4;
  while (toPixel < end)
  {
	uint8_t * rowEnd = toPixel + result.width * 3;
	while (toPixel < rowEnd)
	{
	  toPixel[0] = fromPixel[0];
	  toPixel[1] = fromPixel[1];
	  toPixel[2] = fromPixel[2];
	  toPixel += 3;
	  fromPixel += 4;
	}
	fromPixel += step;
  }
}

uint32_t
PixelFormatRGBChar::getRGBA  (void * pixel) const
{
  // Note: This code will overrun the end of a buffer unless the allocation
  // is padded by 1 byte.  For efficiency's sake, we rely on the code
  // that allocates the buffer to do this for us, rather than doing extra
  // work to prevent the end-case.
# if BYTE_ORDER == LITTLE_ENDIAN
  return bswap (*((uint32_t *) pixel)) | 0xFF;
# else
  return *((uint32_t *) pixel) | 0xFF;
# endif
}

void
PixelFormatRGBChar::setRGBA  (void * pixel, uint32_t rgba) const
{
  ((uint8_t *) pixel)[2] = (rgba >>= 8) & 0xFF;
  ((uint8_t *) pixel)[1] = (rgba >>= 8) & 0xFF;
  ((uint8_t *) pixel)[0] =  rgba >>  8;
}


// class PixelFormatRGBAShort -------------------------------------------------

PixelFormatRGBAShort::PixelFormatRGBAShort ()
{
  planes     = 1;
  depth      = 8;
  precedence = 5;  // Above RGBAChar and GrayFloat.  Slightly below GrayDouble.
  monochrome = false;
  hasAlpha   = true;
}

uint32_t
PixelFormatRGBAShort::getRGBA (void * pixel) const
{
  return   (lutFloat2Char[((uint16_t *) pixel)[0]] << 24)
         | (lutFloat2Char[((uint16_t *) pixel)[1]] << 16)
         | (lutFloat2Char[((uint16_t *) pixel)[2]] <<  8)
         |  lutFloat2Char[((uint16_t *) pixel)[3]];
}

void
PixelFormatRGBAShort::getRGBA (void * pixel, float values[]) const
{
  values[0] = ((uint16_t *) pixel)[0] / 65535.0f;
  values[1] = ((uint16_t *) pixel)[1] / 65535.0f;
  values[2] = ((uint16_t *) pixel)[2] / 65535.0f;
  values[3] = ((uint16_t *) pixel)[3] / 65535.0f;
}

uint8_t
PixelFormatRGBAShort::getAlpha (void * pixel) const
{
  return ((uint16_t *) pixel)[3] >> 8;
}

void
PixelFormatRGBAShort::setRGBA (void * pixel, uint32_t rgba) const
{
  ((uint16_t *) pixel)[0] = (uint16_t) (65535 * lutChar2Float[ rgba             >> 24]);
  ((uint16_t *) pixel)[1] = (uint16_t) (65535 * lutChar2Float[(rgba & 0xFF0000) >> 16]);
  ((uint16_t *) pixel)[2] = (uint16_t) (65535 * lutChar2Float[(rgba &   0xFF00) >>  8]);
  ((uint16_t *) pixel)[3] = (uint16_t) (65535 * lutChar2Float[ rgba &     0xFF       ]);
}

void
PixelFormatRGBAShort::setRGBA (void * pixel, float values[]) const
{
  ((uint16_t *) pixel)[0] = (uint16_t) (65535 * min (max (values[0], 0.0f), 1.0f));
  ((uint16_t *) pixel)[1] = (uint16_t) (65535 * min (max (values[1], 0.0f), 1.0f));
  ((uint16_t *) pixel)[2] = (uint16_t) (65535 * min (max (values[2], 0.0f), 1.0f));
  ((uint16_t *) pixel)[3] = (uint16_t) (65535 * min (max (values[3], 0.0f), 1.0f));
}

void
PixelFormatRGBAShort::setAlpha (void * pixel, uint8_t alpha) const
{
  ((uint16_t *) pixel)[3] = alpha << 8;
}


// class PixelFormatRGBShort -------------------------------------------------

PixelFormatRGBShort::PixelFormatRGBShort ()
{
  planes     = 1;
  depth      = 6;
  precedence = 5;  // Above RGBAChar and GrayFloat.  Slightly below GrayDouble.
  monochrome = false;
  hasAlpha   = false;
}

uint32_t
PixelFormatRGBShort::getRGBA (void * pixel) const
{
  return   (lutFloat2Char[((uint16_t *) pixel)[0]] << 24)
         | (lutFloat2Char[((uint16_t *) pixel)[1]] << 16)
         | (lutFloat2Char[((uint16_t *) pixel)[2]] <<  8)
         | 0xFF;
}

void
PixelFormatRGBShort::setRGBA (void * pixel, uint32_t rgba) const
{
  ((uint16_t *) pixel)[0] = (uint16_t) (65535 * lutChar2Float[ rgba             >> 24]);
  ((uint16_t *) pixel)[1] = (uint16_t) (65535 * lutChar2Float[(rgba & 0xFF0000) >> 16]);
  ((uint16_t *) pixel)[2] = (uint16_t) (65535 * lutChar2Float[(rgba &   0xFF00) >>  8]);
}


// class PixelFormatRGBAFloat -------------------------------------------------

PixelFormatRGBAFloat::PixelFormatRGBAFloat ()
{
  planes     = 1;
  depth      = 4 * sizeof (float);
  precedence = 7;  // Above everything
  monochrome = false;
  hasAlpha   = true;
}

void
PixelFormatRGBAFloat::fromAny (const Image & image, Image & result) const
{
  float * dest = (float *) ((PixelBufferPacked *) result.buffer)->base ();
  const PixelFormat * sourceFormat = image.format;

  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  if (i)
  {
	uint8_t * source      = (uint8_t *) i->base ();
	const int sourceDepth = (int) sourceFormat->depth;
	const int step        = i->stride - image.width * sourceDepth;
	float *   end         = dest + image.width * image.height * 4;
	while (dest < end)
	{
	  float * rowEnd = dest + result.width * 4;
	  while (dest < rowEnd)
	  {
		sourceFormat->getRGBA (source, dest);
		source += sourceDepth;
		dest += 4;
	  }
	  source += step;
	}
  }
  else
  {
	for (int y = 0; y < image.height; y++)
	{
	  for (int x = 0; x < image.width; x++)
	  {
		sourceFormat->getRGBA (image.buffer->pixel (x, y), dest);
		dest += 4;
	  }
	}
  }
}

uint32_t
PixelFormatRGBAFloat::getRGBA (void * pixel) const
{
  float rgbaValues[4];
  getRGBA (pixel, rgbaValues);
  for (int i = 0; i < 4; i++)
  {
	rgbaValues[i] = max (rgbaValues[i], 0.0f);
	rgbaValues[i] = min (rgbaValues[i], 1.0f);
  }
  uint32_t r = (uint32_t) lutFloat2Char[(uint16_t) (65535 * rgbaValues[0])] << 24;
  uint32_t g = (uint32_t) lutFloat2Char[(uint16_t) (65535 * rgbaValues[1])] << 16;
  uint32_t b = (uint32_t) lutFloat2Char[(uint16_t) (65535 * rgbaValues[2])] <<  8;
  uint32_t a = (uint32_t) (255 * rgbaValues[3]);  // assume alpha is already linear
  return r | g | b | a;
}

void
PixelFormatRGBAFloat::getRGBA (void * pixel, float values[]) const
{
  //memcpy (values, pixel, 4 * sizeof (float));  // It is probably more efficient to directly copy these than to set up the function call.
  values[0] = ((float *) pixel)[0];
  values[1] = ((float *) pixel)[1];
  values[2] = ((float *) pixel)[2];
  values[3] = ((float *) pixel)[3];
}

uint8_t
PixelFormatRGBAFloat::getAlpha (void * pixel) const
{
  return (uint8_t) (((float *) pixel)[3] * 255);
}

void
PixelFormatRGBAFloat::setRGBA (void * pixel, uint32_t rgba) const
{
  float * rgbaValues = (float *) pixel;
  rgbaValues[0] = lutChar2Float[ rgba             >> 24];
  rgbaValues[1] = lutChar2Float[(rgba & 0xFF0000) >> 16];
  rgbaValues[2] = lutChar2Float[(rgba &   0xFF00) >>  8];
  rgbaValues[3] = (rgba & 0xFF) / 255.0f;  // Don't linearize alpha, because it is always linear
}

void
PixelFormatRGBAFloat::setRGBA (void * pixel, float values[]) const
{
  //memcpy (pixel, values, 4 * sizeof (float));  // It is probably more efficient to directly copy these than to set up the function call.
  ((float *) pixel)[0] = values[0];
  ((float *) pixel)[1] = values[1];
  ((float *) pixel)[2] = values[2];
  ((float *) pixel)[3] = values[3];
}

void
PixelFormatRGBAFloat::setAlpha (void * pixel, uint8_t alpha) const
{
  ((float *) pixel)[3] = alpha / 255.0f;
}

void
PixelFormatRGBAFloat::blend (void * pixel, float values[]) const
{
  alphaBlend (values, (float *) pixel);
}


// class PixelFormatYUV -------------------------------------------------------

PixelFormatYUV::PixelFormatYUV (int ratioH, int ratioV)
: ratioH (ratioH),
  ratioV (ratioV)
{
}


// class PixelFormatPackedYUV -------------------------------------------------

PixelFormatPackedYUV::PixelFormatPackedYUV (YUVindex * table)
: PixelFormatYUV (1, 1)
{
  planes     = -1;
  precedence = 1;
  monochrome = false;
  hasAlpha   = false;

  // Analyze table to initialize remaining members
  this->table = table;
  pixels = 0;
  bytes = 0;
  depth = 0;
  if (table)
  {
	// Count number of entries in table, which is same as pixels in macropixel.
	YUVindex * i = table;
	while (i->y >= 0)
	{
	  i++;
	  pixels++;
	}
	this->table = new YUVindex[pixels + 1];

	set<int> Usamples;
	set<int> Vsamples;
	i = table;
	YUVindex * j = this->table;
	while (i->y >= 0)
	{
	  bytes = max (bytes, i->y, i->u, i->v);
	  Usamples.insert (i->u);
	  Vsamples.insert (i->v);
	  *j++ = *i++;
	}
	j->y = -1;
	bytes++;  // Up to now, bytes is the index of highest byte in group.  Must convert to quantity of bytes.
	depth = (float) bytes / pixels;
	ratioH = pixels / min (Usamples.size (), Vsamples.size ());
  }
}

PixelFormatPackedYUV::~PixelFormatPackedYUV ()
{
  delete [] table;
}

Image
PixelFormatPackedYUV::filter (const Image & image)
{
  Image result (*this);

  if (*image.format == *this)
  {
	result = image;
	return result;
  }

  result.resize (image.width, image.height);
  result.timestamp = image.timestamp;
  if (result.width <= 0  ||  result.height <= 0) return result;

  if ((const PixelFormatYUV *) image.format)
  {
	fromYUV (image, result);
  }
  else
  {
	fromAny (image, result);
  }

  return result;
}

void
PixelFormatPackedYUV::fromAny (const Image & image, Image & result) const
{
  const PixelFormat * sourceFormat = image.format;
  PixelBuffer *       sourceBuffer = (PixelBuffer *) image.buffer;
  const int           sourceDepth  = (int) sourceFormat->depth;

  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  PixelBufferGroups * o = (PixelBufferGroups *) result.buffer;
  assert (o);

  uint8_t *       address  = (uint8_t *) o->memory;
  const uint8_t * end      = address + o->stride * result.height;
  const int       rowWidth = (int) (result.width * depth);
  const int       toStep   = o->stride - rowWidth;

  const uint32_t  shift    = 16 + (int) roundp (log ((double) ratioH) / log (2.0));
  const int       bias     = 0x808 << (shift - 4);  // include both bias and rounding in single constant
  const int       maximum  = (~(uint32_t) 0) >> (24 - shift);

  if (i)
  {
	uint8_t * source = (uint8_t *) i->base ();
	const int fromStep = i->stride - image.width * sourceDepth;

	while (address < end)
	{
	  const uint8_t * rowEnd = address + rowWidth;
	  while (address < rowEnd)
	  {
		YUVindex * index = table;
		while (index->y >= 0)
		{
		  int r = 0;
		  int g = 0;
		  int b = 0;
		  for (int p = 0; p < ratioH; p++)
		  {
			uint32_t rgba = sourceFormat->getRGBA (source);
			source += sourceDepth;
			int sr =  rgba             >> 24;  // assumes 32-bit int
			int sg = (rgba & 0xFF0000) >> 16;
			int sb = (rgba &   0xFF00) >>  8;
			r += sr;
			g += sg;
			b += sb;
			address[(*index++).y] = min (max (0x4C84 * sr + 0x962B * sg + 0x1D4F * sb + 0x8000, 0), 0xFFFFFF) >> 16;
		  }
		  // This assumes that all ratioH pixels share the same U and V values,
		  // and that ratioH accurately represents the structure of the group.
		  index--;
		  address[index->u] = min (max (- 0x2B2F * r - 0x54C9 * g + 0x8000 * b + bias, 0), maximum) >> shift;
		  address[index->v] = min (max (  0x8000 * r - 0x6B15 * g - 0x14E3 * b + bias, 0), maximum) >> shift;
		  index++;
		}
		address += bytes;
	  }
	  address += toStep;
	  source += fromStep;
	}
  }
  else
  {
	int y = 0;
	while (address < end)
	{
	  int x = 0;
	  const uint8_t * rowEnd = address + rowWidth;
	  while (address < rowEnd)
	  {
		YUVindex * index = table;
		while (index->y >= 0)
		{
		  int r = 0;
		  int g = 0;
		  int b = 0;
		  for (int p = 0; p < ratioH; p++)
		  {
			uint32_t rgba = sourceFormat->getRGBA (sourceBuffer->pixel (x++, y));
			int sr =  rgba             >> 24;
			int sg = (rgba & 0xFF0000) >> 16;
			int sb = (rgba &   0xFF00) >>  8;
			r += sr;
			g += sg;
			b += sb;
			address[(*index++).y] = min (max (0x4C84 * sr + 0x962B * sg + 0x1D4F * sb + 0x8000, 0), 0xFFFFFF) >> 16;
		  }
		  index--;
		  address[index->u] = min (max (- 0x2B2F * r - 0x54C9 * g + 0x8000 * b + bias, 0), maximum) >> shift;
		  address[index->v] = min (max (  0x8000 * r - 0x6B15 * g - 0x14E3 * b + bias, 0), maximum) >> shift;
		  index++;
		}
		address += bytes;
	  }
	  address += toStep;
	  y++;
	}
  }
}

void
PixelFormatPackedYUV::fromYUV (const Image & image, Image & result) const
{
  const PixelFormat * sourceFormat = image.format;
  PixelBuffer *       sourceBuffer = (PixelBuffer *) image.buffer;
  const int           sourceDepth  = (int) sourceFormat->depth;

  PixelBufferGroups * o = (PixelBufferGroups *) result.buffer;
  assert (o);
  uint8_t *       address  = (uint8_t *) o->memory;
  const uint8_t * end      = address + o->stride * result.height;
  const int       rowWidth = (int) (result.width * depth);
  const int       toStep   = o->stride - rowWidth;

  const uint32_t shift = 8 + (int) roundp (log ((double) ratioH) / log (2.0));
  const uint32_t roundup = 0x80 << (shift - 8);

  int y = 0;
  while (address < end)
  {
	int x = 0;
	const uint8_t * rowEnd = address + rowWidth;
	while (address < rowEnd)
	{
	  YUVindex * index = table;
	  while (index->y >= 0)
	  {
		uint32_t u = 0;
		uint32_t v = 0;
		for (int p = 0; p < ratioH; p++)
		{
		  uint32_t yuv = sourceFormat->getYUV (sourceBuffer->pixel (x++, y));
		  u +=  yuv & 0xFF00;
		  v += (yuv &   0xFF) << 8;
		  address[(*index++).y] = yuv >> 16;  // don't mask, on assumption that higher order bits of yuv are 0
		}
		index--;
		address[index->u] = (u + roundup) >> shift;
		address[index->v] = (v + roundup) >> shift;
		index++;
	  }
	  address += bytes;
	}
	address += toStep;
	y++;
  }
}

PixelBuffer *
PixelFormatPackedYUV::attach (void * block, int width, int height, bool copy) const
{
  PixelBufferGroups * result = new PixelBufferGroups (block, (int) ceil ((float) width / pixels) * bytes, height, pixels, bytes);
  if (copy) result->memory.copyFrom (result->memory);
  return result;
}

bool
PixelFormatPackedYUV::operator == (const PixelFormat & that) const
{
  const PixelFormatPackedYUV * p = dynamic_cast<const PixelFormatPackedYUV *> (&that);
  if (! p  ||  p->pixels != pixels) return false;
  for (int i = 0; i < pixels; i++)
  {
	YUVindex & me = table[i];
	YUVindex & thee = p->table[i];
	if (me.y != thee.y  ||  me.u != thee.u  ||  me.v != thee.v) return false;
  }
  return true;
}

uint32_t
PixelFormatPackedYUV::getRGBA (void * pixel) const
{
  PixelBufferGroups::PixelData * data = (PixelBufferGroups::PixelData *) pixel;
  register uint8_t * address = data->address;
  YUVindex & index = table[data->index];

  int y = address[index.y] << 16;
  int u = address[index.u] - 128;
  int v = address[index.v] - 128;

  uint32_t r = min (max (y               + 0x166F7 * v + 0x8000, 0), 0xFFFFFF);
  uint32_t g = min (max (y -  0x5879 * u -  0xB6E9 * v + 0x8000, 0), 0xFFFFFF);
  uint32_t b = min (max (y + 0x1C560 * u               + 0x8000, 0), 0xFFFFFF);

  return ((r << 8) & 0xFF000000) | (g & 0xFF0000) | ((b >> 8) & 0xFF00) | 0xFF;
}

uint32_t
PixelFormatPackedYUV::getYUV (void * pixel) const
{
  PixelBufferGroups::PixelData * data = (PixelBufferGroups::PixelData *) pixel;
  register uint8_t * address = data->address;
  YUVindex & index = table[data->index];

  return (address[index.y] << 16) | (address[index.u] << 8) | address[index.v];
}

uint8_t
PixelFormatPackedYUV::getGray (void * pixel) const
{
  PixelBufferGroups::PixelData * data = (PixelBufferGroups::PixelData *) pixel;
  return data->address[table[data->index].y];
}

void
PixelFormatPackedYUV::setRGBA (void * pixel, uint32_t rgba) const
{
  int r = (rgba & 0xFF000000) >> 24;
  int g = (rgba &   0xFF0000) >> 16;
  int b = (rgba &     0xFF00) >>  8;

  uint8_t y = min (max (  0x4C84 * r + 0x962B * g + 0x1D4F * b            + 0x8000, 0), 0xFFFFFF) >> 16;
  uint8_t u = min (max (- 0x2B2F * r - 0x54C9 * g + 0x8000 * b + 0x800000 + 0x8000, 0), 0xFFFFFF) >> 16;
  uint8_t v = min (max (  0x8000 * r - 0x6B15 * g - 0x14E3 * b + 0x800000 + 0x8000, 0), 0xFFFFFF) >> 16;

  PixelBufferGroups::PixelData * data = (PixelBufferGroups::PixelData *) pixel;
  register uint8_t * address = data->address;
  YUVindex & index = table[data->index];

  address[index.y] = y;
  address[index.u] = u;
  address[index.v] = v;
}

void
PixelFormatPackedYUV::setYUV (void * pixel, uint32_t yuv) const
{
  PixelBufferGroups::PixelData * data = (PixelBufferGroups::PixelData *) pixel;
  register uint8_t * address = data->address;
  YUVindex & index = table[data->index];

  address[index.y] =  yuv           >> 16;
  address[index.u] = (yuv & 0xFF00) >>  8;
  address[index.v] =  yuv &   0xFF;
}


// class PixelFormatPlanarYUV -------------------------------------------------

PixelFormatPlanarYUV::PixelFormatPlanarYUV (int ratioH, int ratioV)
: PixelFormatYUV (ratioH, ratioV)
{
  planes     = 3;
  depth      = sizeof (char);
  precedence = 1;
  monochrome = false;
  hasAlpha   = false;
}

void
PixelFormatPlanarYUV::fromAny (const Image & image, Image & result) const
{
  assert (image.width % ratioH == 0  &&  image.height % ratioV == 0);

  const PixelFormat * sourceFormat = image.format;
  PixelBuffer *       sourceBuffer = (PixelBuffer *) image.buffer;
  const int           sourceDepth  = (int) sourceFormat->depth;

  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  PixelBufferPlanar * o = (PixelBufferPlanar *) result.buffer;

  int rowWidth      = result.width;
  int blockRowWidth = ratioH;

  const uint32_t shift   = 16 + (int) roundp (log ((double) ratioH * ratioV) / log (2.0));
  const int      bias    = 0x808 << (shift - 4);  // include both bias and rounding in single constant
  const int      maximum = (~(uint32_t) 0) >> (24 - shift);

  if (o)
  {
	uint8_t * Y = (uint8_t *) o->plane0;
	uint8_t * U = (uint8_t *) o->plane1;
	uint8_t * V = (uint8_t *) o->plane2;

	const int blockSwath     = o->stride0 * ratioV;
	const int step12         = o->stride12 - result.width / ratioH;
	const int toStep         = o->stride0 * ratioV - result.width;
	const int toBlockStep    = ratioH - o->stride0 * ratioV;
	const int toBlockRowStep = o->stride0 - ratioH;

	if (i)
	{
	  uint8_t * source = (uint8_t *) i->base ();

	  const int fromStep         = i->stride * ratioV - image.width * sourceDepth;
	  const int fromBlockStep    = ratioH * sourceDepth - i->stride * ratioV;
	  const int fromBlockRowStep = i->stride - ratioH * sourceDepth;

	  uint8_t * end = Y + result.width * result.height;
	  while (Y < end)
	  {
		uint8_t * rowEnd = Y + rowWidth;
		while (Y < rowEnd)
		{
		  int r = 0;
		  int g = 0;
		  int b = 0;
		  uint8_t * blockEnd = Y + blockSwath;
		  while (Y < blockEnd)
		  {
			uint8_t * blockRowEnd = Y + blockRowWidth;
			while (Y < blockRowEnd)
			{
			  uint32_t rgba = sourceFormat->getRGBA (source);
			  source += sourceDepth;
			  int sr =  rgba             >> 24;  // assumes 32-bit int
			  int sg = (rgba & 0xFF0000) >> 16;
			  int sb = (rgba &   0xFF00) >>  8;
			  r += sr;
			  g += sg;
			  b += sb;
			  *Y++ = min (max (0x4C84 * sr + 0x962B * sg + 0x1D4F * sb + 0x8000, 0), 0xFFFFFF) >> 16;
			}
			Y += toBlockRowStep;
			source += fromBlockRowStep;
		  }
		  *U++ = min (max (- 0x2B2F * r - 0x54C9 * g + 0x8000 * b + bias, 0), maximum) >> shift;
		  *V++ = min (max (  0x8000 * r - 0x6B15 * g - 0x14E3 * b + bias, 0), maximum) >> shift;
		  Y += toBlockStep;
		  source += fromBlockStep;
		}
		Y += toStep;
		source += fromStep;
		U += step12;
		V += step12;
	  }
	}
	else
	{
	  int y = 0;
	  uint8_t * end = Y + result.width * result.height;
	  while (Y < end)
	  {
		int x = 0;
		uint8_t * rowEnd = Y + rowWidth;
		while (Y < rowEnd)
		{
		  int r = 0;
		  int g = 0;
		  int b = 0;
		  uint8_t * blockEnd = Y + blockSwath;
		  while (Y < blockEnd)
		  {
			uint8_t * blockRowEnd = Y + blockRowWidth;
			while (Y < blockRowEnd)
			{
			  uint32_t rgba = sourceFormat->getRGBA (sourceBuffer->pixel (x++, y));
			  int sr =  rgba             >> 24;
			  int sg = (rgba & 0xFF0000) >> 16;
			  int sb = (rgba &   0xFF00) >>  8;
			  r += sr;
			  g += sg;
			  b += sb;
			  *Y++ = min (max (0x4C84 * sr + 0x962B * sg + 0x1D4F * sb + 0x8000, 0), 0xFFFFFF) >> 16;
			}
			Y += toBlockRowStep;
			x -= ratioH;
			y++;
		  }
		  *U++ = min (max (- 0x2B2F * r - 0x54C9 * g + 0x8000 * b + bias, 0), maximum) >> shift;
		  *V++ = min (max (  0x8000 * r - 0x6B15 * g - 0x14E3 * b + bias, 0), maximum) >> shift;
		  Y += toBlockStep;
		  x += ratioH;
		  y -= ratioV;
		}
		Y += toStep;
		y += ratioV;
		U += step12;
		V += step12;
	  }
	}
  }
  else
  {
	PixelBuffer * destBuffer = (PixelBuffer *) result.buffer;

	if (i)
	{
	  uint8_t * source = (uint8_t *) i->base ();

	  rowWidth      *= sourceDepth;
	  blockRowWidth *= sourceDepth;
	  const int blockSwath       = i->stride * ratioV;
	  const int fromStep         = i->stride * ratioV - image.width * sourceDepth;
	  const int fromBlockStep    = ratioH * sourceDepth - i->stride * ratioV;
	  const int fromBlockRowStep = i->stride - ratioH * sourceDepth;

	  int y = 0;
	  uint8_t * end = source + i->stride * image.height;
	  while (source < end)
	  {
		int x = 0;
		uint8_t * rowEnd = source + rowWidth;
		while (source < rowEnd)
		{
		  int r = 0;
		  int g = 0;
		  int b = 0;
		  uint8_t * blockEnd = source + blockSwath;
		  while (source < blockEnd)
		  {
			uint8_t * blockRowEnd = source + blockRowWidth;
			while (source < blockRowEnd)
			{
			  uint32_t rgba = sourceFormat->getRGBA (source);
			  source += sourceDepth;
			  int sr =  rgba             >> 24;  // assumes 32-bit int
			  int sg = (rgba & 0xFF0000) >> 16;
			  int sb = (rgba &   0xFF00) >>  8;
			  r += sr;
			  g += sg;
			  b += sb;
			  *((uint8_t **) destBuffer->pixel (x++, y))[0] = min (max (0x4C84 * sr + 0x962B * sg + 0x1D4F * sb + 0x8000, 0), 0xFFFFFF) >> 16;
			}
			source += fromBlockRowStep;
			x -= ratioH;
			y++;
		  }
		  y -= ratioV;
		  uint8_t ** pixel = (uint8_t **) destBuffer->pixel (x, y);
		  *pixel[1] = min (max (- 0x2B2F * r - 0x54C9 * g + 0x8000 * b + bias, 0), maximum) >> shift;
		  *pixel[2] = min (max (  0x8000 * r - 0x6B15 * g - 0x14E3 * b + bias, 0), maximum) >> shift;
		  source += fromBlockStep;
		  x += ratioH;
		}
		source += fromStep;
		y += ratioV;
	  }
	}
	else
	{
	  for (int y = 0; y < result.height; y += ratioV)
	  {
		for (int x = 0; x < result.width; x += ratioH)
		{
		  int r = 0;
		  int g = 0;
		  int b = 0;
		  int yend = y + ratioV;
		  int xend = x + ratioH;
		  for (int yy = y; yy < yend; yy++)
		  {
			for (int xx = x; xx < xend; xx++)
			{
			  uint32_t rgba = sourceFormat->getRGBA (sourceBuffer->pixel (xx, yy));
			  int sr =  rgba             >> 24;
			  int sg = (rgba & 0xFF0000) >> 16;
			  int sb = (rgba &   0xFF00) >>  8;
			  r += sr;
			  g += sg;
			  b += sb;
			  *((uint8_t **) destBuffer->pixel (xx, yy))[0] = min (max (0x4C84 * sr + 0x962B * sg + 0x1D4F * sb + 0x8000, 0), 0xFFFFFF) >> 16;
			}
		  }
		  uint8_t ** pixel = (uint8_t **) destBuffer->pixel (x, y);
		  *pixel[1] = min (max (- 0x2B2F * r - 0x54C9 * g + 0x8000 * b + bias, 0), maximum) >> shift;
		  *pixel[2] = min (max (  0x8000 * r - 0x6B15 * g - 0x14E3 * b + bias, 0), maximum) >> shift;
		}
	  }
	}
  }
}

PixelBuffer *
PixelFormatPlanarYUV::attach (void * block, int width, int height, bool copy) const
{
  int size = width * height;
  char * buffer1 = (char *) block + size;
  char * buffer2 = buffer1 + size / (ratioH * ratioV);
  PixelBufferPlanar * result = new PixelBufferPlanar (block, buffer1, buffer2, width, width / ratioH, height, ratioH, ratioV);

  if (copy)
  {
	PixelBufferPlanar * temp = result;
	result = (PixelBufferPlanar *) temp->duplicate ();
	delete temp;
  }

  return result;
}

bool
PixelFormatPlanarYUV::operator == (const PixelFormat & that) const
{
  const PixelFormatPlanarYUV * p = dynamic_cast<const PixelFormatPlanarYUV *> (&that);
  return    p
         && ratioH == p->ratioH
         && ratioV == p->ratioV;
}

uint32_t
PixelFormatPlanarYUV::getRGBA (void * pixel) const
{
  int y = *((uint8_t **) pixel)[0] << 16;
  int u = *((uint8_t **) pixel)[1] - 128;
  int v = *((uint8_t **) pixel)[2] - 128;

  // R = Y           +1.4022*V
  // G = Y -0.3456*U -0.7145*V
  // B = Y +1.7710*U
  // The coefficients below are in fixed-point with decimal between bits 15 and 16.
  // Figure out a more elegant way to express these constants letting the
  // compiler do the work!
  uint32_t r = min (max (y               + 0x166F7 * v + 0x8000, 0), 0xFFFFFF);
  uint32_t g = min (max (y -  0x5879 * u -  0xB6E9 * v + 0x8000, 0), 0xFFFFFF);
  uint32_t b = min (max (y + 0x1C560 * u               + 0x8000, 0), 0xFFFFFF);

  return ((r << 8) & 0xFF000000) | (g & 0xFF0000) | ((b >> 8) & 0xFF00) | 0xFF;
}

uint32_t
PixelFormatPlanarYUV::getYUV (void * pixel) const
{
  return (*((uint8_t **) pixel)[0] << 16) | (*((uint8_t **) pixel)[1] << 8) | *((uint8_t **) pixel)[2];
}

uint8_t
PixelFormatPlanarYUV::getGray (void * pixel) const
{
  return *((uint8_t **) pixel)[0];
}

void
PixelFormatPlanarYUV::setRGBA (void * pixel, uint32_t rgba) const
{
  int r = (rgba & 0xFF000000) >> 24;
  int g = (rgba &   0xFF0000) >> 16;
  int b = (rgba &     0xFF00) >>  8;

  // Y =  0.2989*R +0.5866*G +0.1145*B
  // U = -0.1687*R -0.3312*G +0.5000*B
  // V =  0.5000*R -0.4183*G -0.0816*B
  *((uint8_t **) pixel)[0] = min (max (  0x4C84 * r + 0x962B * g + 0x1D4F * b            + 0x8000, 0), 0xFFFFFF) >> 16;
  *((uint8_t **) pixel)[1] = min (max (- 0x2B2F * r - 0x54C9 * g + 0x8000 * b + 0x800000 + 0x8000, 0), 0xFFFFFF) >> 16;
  *((uint8_t **) pixel)[2] = min (max (  0x8000 * r - 0x6B15 * g - 0x14E3 * b + 0x800000 + 0x8000, 0), 0xFFFFFF) >> 16;
}

void
PixelFormatPlanarYUV::setYUV  (void * pixel, uint32_t yuv) const
{
  *((uint8_t **) pixel)[0] =  yuv           >> 16;
  *((uint8_t **) pixel)[1] = (yuv & 0xFF00) >>  8;
  *((uint8_t **) pixel)[2] =  yuv &   0xFF;
}


// class PixelFormatPlanarYCbCr -----------------------------------------------

uint8_t * PixelFormatPlanarYCbCr::lutYin = PixelFormatPlanarYCbCr::buildAll ();
uint8_t * PixelFormatPlanarYCbCr::lutUVin;
uint8_t * PixelFormatPlanarYCbCr::lutYout;
uint8_t * PixelFormatPlanarYCbCr::lutUVout;
float *   PixelFormatPlanarYCbCr::lutGrayOut;

PixelFormatPlanarYCbCr::PixelFormatPlanarYCbCr (int ratioH, int ratioV)
: PixelFormatYUV (ratioH, ratioV)
{
  planes     = 3;
  depth      = sizeof (char);
  precedence = 1;  // same as UYVY
  monochrome = false;
  hasAlpha   = false;
}

uint8_t *
PixelFormatPlanarYCbCr::buildAll ()
{
  lutYin     = (uint8_t *) malloc (256);
  lutUVin    = (uint8_t *) malloc (256);
  lutYout    = (uint8_t *) malloc (256);
  lutUVout   = (uint8_t *) malloc (256);
  lutGrayOut = (float *)   malloc (256 * sizeof (float));

  for (int i = 0; i < 256; i++)
  {
	lutYin[i]   = (int) roundp (i * 219.0 / 255.0) + 16;
	lutUVin[i]  = (int) roundp (i * 224.0 / 255.0) + 16;
	lutYout[i]  = min (max ((int) roundp ((i - 16) * 255.0 / 219.0), 0), 255);
	lutUVout[i] = min (max ((int) roundp ((i - 16) * 255.0 / 224.0), 0), 255);

	double f = (i - 16) / 219.0;
	if (f <= 0.04045)  // This linear portion extends into the negative numbers
	{
	  f /= 12.92;
	}
	else
	{
	  f = pow ((f + 0.055) / 1.055, 2.4);
	}
	lutGrayOut[i] = f;
  }
  return lutYin;
}

void
PixelFormatPlanarYCbCr::fromAny (const Image & image, Image & result) const
{
  assert (image.width % ratioH == 0  &&  image.height % ratioV == 0);

  const PixelFormat * sourceFormat = image.format;

  PixelBufferPacked * i = (PixelBufferPacked *) image.buffer;
  PixelBufferPlanar * o = (PixelBufferPlanar *) result.buffer;
  assert (o);
  uint8_t * Y  = (uint8_t *) o->plane0;
  uint8_t * Cb = (uint8_t *) o->plane1;
  uint8_t * Cr = (uint8_t *) o->plane2;

  const int rowWidth       = result.width;
  const int blockRowWidth  = ratioH;
  const int blockSwath     = o->stride0 * ratioV;
  const int step12         = o->stride12 - result.width / ratioH;
  const int toStep         = o->stride0 * ratioV - result.width;
  const int toBlockStep    = ratioH - o->stride0 * ratioV;
  const int toBlockRowStep = o->stride0 - ratioH;

  const uint32_t shift = 16 + (int) roundp (log ((double) ratioH * ratioV) / log (2.0));
  const int bias = 0x808 << (shift - 4);

  if (i)
  {
	uint8_t * source      = (uint8_t *) i->base ();
	const int sourceDepth = (int) sourceFormat->depth;

	const int fromStep         = i->stride * ratioV - image.width * sourceDepth;
	const int fromBlockStep    = ratioH * sourceDepth - i->stride * ratioV;
	const int fromBlockRowStep = i->stride - ratioH * sourceDepth;

	uint8_t * end = Y + result.width * result.height;
	while (Y < end)
	{
	  uint8_t * rowEnd = Y + rowWidth;
	  while (Y < rowEnd)
	  {
		int r = 0;
		int g = 0;
		int b = 0;
		uint8_t * blockEnd = Y + blockSwath;
		while (Y < blockEnd)
		{
		  uint8_t * blockRowEnd = Y + blockRowWidth;
		  while (Y < blockRowEnd)
		  {
			uint32_t rgba = sourceFormat->getRGBA (source);
			source += sourceDepth;
			int sr =  rgba             >> 24;  // assumes 32-bit int
			int sg = (rgba & 0xFF0000) >> 16;
			int sb = (rgba &   0xFF00) >>  8;
			r += sr;
			g += sg;
			b += sb;
			*Y++ = (0x41BD * sr + 0x810F * sg + 0x1910 * sb + 0x100000 + 0x8000) >> 16;
		  }
		  Y += toBlockRowStep;
		  source += fromBlockRowStep;
		}
		*Cb++ = (- 0x25F2 * r - 0x4A7E * g + 0x7070 * b + bias) >> shift;
		*Cr++ = (  0x7070 * r - 0x5E28 * g - 0x1248 * b + bias) >> shift;
		Y += toBlockStep;
		source += fromBlockStep;
	  }
	  Y += toStep;
	  source += fromStep;
	  Cb += step12;
	  Cr += step12;
	}
  }
  else
  {
	int y = 0;
	uint8_t * end = Y + result.width * result.height;
	while (Y < end)
	{
	  int x = 0;
	  uint8_t * rowEnd = Y + rowWidth;
	  while (Y < rowEnd)
	  {
		int r = 0;
		int g = 0;
		int b = 0;
		uint8_t * blockEnd = Y + blockSwath;
		while (Y < blockEnd)
		{
		  uint8_t * blockRowEnd = Y + blockRowWidth;
		  while (Y < blockRowEnd)
		  {
			uint32_t rgba = sourceFormat->getRGBA (image.buffer->pixel (x++, y));
			int sr =  rgba             >> 24;  // assumes 32-bit int
			int sg = (rgba & 0xFF0000) >> 16;
			int sb = (rgba &   0xFF00) >>  8;
			r += sr;
			g += sg;
			b += sb;
			*Y++ = (0x41BD * sr + 0x810F * sg + 0x1910 * sb + 0x100000 + 0x8000) >> 16;
		  }
		  Y += toBlockRowStep;
		  x -= ratioH;
		  y++;
		}
		*Cb++ = (- 0x25F2 * r - 0x4A7E * g + 0x7070 * b + bias) >> shift;
		*Cr++ = (  0x7070 * r - 0x5E28 * g - 0x1248 * b + bias) >> shift;
		Y += toBlockStep;
		x += ratioH;
		y -= ratioV;
	  }
	  Y += toStep;
	  y += ratioV;
	  Cb += step12;
	  Cr += step12;
	}
  }
}

PixelBuffer *
PixelFormatPlanarYCbCr::attach (void * block, int width, int height, bool copy) const
{
  int size = width * height;
  char * buffer1 = (char *) block + size;
  char * buffer2 = buffer1 + size / (ratioH * ratioV);
  PixelBufferPlanar * result = new PixelBufferPlanar (block, buffer1, buffer2, width, width / ratioH, height, ratioH, ratioV);

  if (copy)
  {
	PixelBufferPlanar * temp = result;
	result = (PixelBufferPlanar *) temp->duplicate ();
	delete temp;
  }

  return result;
}

bool
PixelFormatPlanarYCbCr::operator == (const PixelFormat & that) const
{
  const PixelFormatPlanarYCbCr * p = dynamic_cast<const PixelFormatPlanarYCbCr *> (&that);
  return    p
         && ratioH == p->ratioH
         && ratioV == p->ratioV;
}

uint32_t
PixelFormatPlanarYCbCr::getRGBA (void * pixel) const
{
  // Converts from YCbCr using the matrix given in the Poynton color FAQ:
  // [R]    1  [298.082    0      408.583]   [Y  -  16]
  // [G] = --- [298.082 -100.291 -208.120] * [Cb - 128]
  // [B]   256 [298.082  516.411    0    ]   [Cr - 128]
  // These values are for fixed-point after bit 8, but in this code they are
  // adjusted to put the fixed-point after bit 16.

  int y = (*((uint8_t **) pixel)[0] -  16) * 0x12A15;
  int u =  *((uint8_t **) pixel)[1] - 128;
  int v =  *((uint8_t **) pixel)[2] - 128;

  uint32_t r = min (max (y               + 0x19895 * v + 0x8000, 0), 0xFFFFFF);
  uint32_t g = min (max (y -  0x644A * u -  0xD01F * v + 0x8000, 0), 0xFFFFFF);
  uint32_t b = min (max (y + 0x20469 * u               + 0x8000, 0), 0xFFFFFF);

  return ((r << 8) & 0xFF000000) | (g & 0xFF0000) | ((b >> 8) & 0xFF00) | 0xFF;
}

/**
   This function does not provide direct access to the scaled values stored
   in memory, but instead rescales them to standard [0,255] range values.
 **/
uint32_t
PixelFormatPlanarYCbCr::getYUV (void * pixel) const
{
  return   (lutYout [*((uint8_t **) pixel)[0]] << 16)
	     | (lutUVout[*((uint8_t **) pixel)[1]] <<  8)
	     |  lutUVout[*((uint8_t **) pixel)[2]];
}

/**
   This function can return values outside of [0,1] if pixels are blacker
   than black or whiter than white.
 **/
void
PixelFormatPlanarYCbCr::getGray (void * pixel, float & gray) const
{
  gray = lutGrayOut[*((uint8_t **) pixel)[0]];
}

void
PixelFormatPlanarYCbCr::setRGBA (void * pixel, uint32_t rgba) const
{
  // Converts to YCbCr using the matrix given in the Poynton color FAQ:
  // [Y ]    1  [ 65.738  129.057   25.064]   [R]   [ 16]
  // [Cb] = --- [-37.945  -74.494  112.439] * [G] + [128]
  // [Cr]   256 [112.439  -94.154  -18.285]   [B]   [128]
  // These values are for fixed-point after bit 8, but in this code they
  // are adjusted to put the fixed-point after bit 16.  The hex values
  // below have been carefully adjusted so the Cb and Cr rows sum to zero
  // and the Y row sums to 219 * (256/255).  Note that clamping is unecessary
  // because of the footroom and headroom in the resulting values.

  int r = (rgba & 0xFF000000) >> 24;
  int g = (rgba &   0xFF0000) >> 16;
  int b = (rgba &     0xFF00) >>  8;

  *((uint8_t **) pixel)[0] = (  0x41BD * r + 0x810F * g + 0x1910 * b + 0x100000 + 0x8000) >> 16;
  *((uint8_t **) pixel)[1] = (- 0x25F2 * r - 0x4A7E * g + 0x7070 * b + 0x800000 + 0x8000) >> 16;
  *((uint8_t **) pixel)[2] = (  0x7070 * r - 0x5E28 * g - 0x1248 * b + 0x800000 + 0x8000) >> 16;
}

/**
   This function does not directly set the values stored in memory, but
   instead rescales them to their shortened ranges.
 **/
void
PixelFormatPlanarYCbCr::setYUV  (void * pixel, uint32_t yuv) const
{
  *((uint8_t **) pixel)[0] = lutYin [ yuv           >> 16];
  *((uint8_t **) pixel)[1] = lutUVin[(yuv & 0xFF00) >>  8];
  *((uint8_t **) pixel)[2] = lutUVin[ yuv &   0xFF       ];
}

/**
   This function can set values outside of [0,1] if pixels are blacker
   than black or whiter than white.
 **/
void
PixelFormatPlanarYCbCr::setGray (void * pixel, float gray) const
{
  // de-linearize
  if (gray <= 0.0031308f)
  {
	gray *= 12.92f;
  }
  else
  {
	gray = 1.055f * powf (gray, 1.0f / 2.4f) - 0.055f;
  }

  *((uint8_t **) pixel)[0] = (uint8_t) min (max (gray * 219.0f + 16.0f, 1.0f), 254.0f);
}


// class PixelFormatHSLFloat --------------------------------------------------

PixelFormatHSLFloat::PixelFormatHSLFloat ()
{
  planes     = 1;
  depth      = 3 * sizeof (float);
  precedence = 7;  // on par with RGBAFloat
  monochrome = false;
  hasAlpha   = false;
}

uint32_t
PixelFormatHSLFloat::getRGBA (void * pixel) const
{
  float rgbaValues[4];
  getRGBA (pixel, rgbaValues);
  uint32_t r = lutFloat2Char[(uint16_t) (65535 * rgbaValues[0])];
  uint32_t g = lutFloat2Char[(uint16_t) (65535 * rgbaValues[1])];
  uint32_t b = lutFloat2Char[(uint16_t) (65535 * rgbaValues[2])];
  uint32_t a = (uint32_t) (255 * rgbaValues[3]);
  return (r << 24) | (g << 16) | (b << 8) | a;
}

static const float root32    = sqrtf (3.0f) / 2.0f;
static const float onesixth  = 1.0f / 6.0f;
static const float onethird  = 1.0f / 3.0f;
static const float twothirds = 2.0f / 3.0f;

static inline float
HS (const float & n1, const float & n2, float h)
{
  if (h > 1.0f)
  {
	h -= 1.0f;
  }
  if (h < 0)
  {
	h += 1.0f;
  }

  if (h < onesixth)
  {
    return n1 + (n2 - n1) * h * 6.0f;
  }
  else if (h < 0.5f)
  {
    return n2;
  }
  else if (h < twothirds)
  {
    return n1 + (n2 - n1) * (twothirds - h) * 6.0f;
  }
  else
  {
    return n1;
  }
}

void
PixelFormatHSLFloat::getRGBA (void * pixel, float values[]) const
{
  float h = ((float *) pixel)[0];
  float s = ((float *) pixel)[1];
  float l = ((float *) pixel)[2];

  if (s == 0)
  {
    values[0] = l;
    values[1] = l;
    values[2] = l;
  }
  else
  {
	float m2;
	if (l <= 0.5f)
	{
	  m2 = l + l * s;
	}
	else
	{
	  m2 = l + s - l * s;
	}
	float m1 = 2.0f * l - m2;

	float barf;
	h = modff (h, &barf);
	if (h < 0)
	{
	  h += 1.0f;
	}

    values[0] = HS (m1, m2, h + onethird);
    values[1] = HS (m1, m2, h);
    values[2] = HS (m1, m2, h - onethird);
  }

  values[3] = 1.0f;
}

void
PixelFormatHSLFloat::getHSL (void * pixel, float values[]) const
{
  values[0] = ((float *) pixel)[0];
  values[1] = ((float *) pixel)[1];
  values[2] = ((float *) pixel)[2];
}

void
PixelFormatHSLFloat::setRGBA (void * pixel, uint32_t rgba) const
{
  float rgbaValues[3];  // Ignore alpha channel, because it is not processed or stored by any function of this format.
  rgbaValues[0] = lutChar2Float[ rgba             >> 24];
  rgbaValues[1] = lutChar2Float[(rgba & 0xFF0000) >> 16];
  rgbaValues[2] = lutChar2Float[(rgba &   0xFF00) >>  8];
  setRGBA (pixel, rgbaValues);
}

void
PixelFormatHSLFloat::setRGBA (void * pixel, float values[]) const
{
  // Lightness
  float rgbmax = max (values[0], max (values[1], values[2]));
  float rgbmin = min (values[0], min (values[1], values[2]));
  float l = (rgbmax + rgbmin) / 2.0f;

  // Hue and Saturation
  float h;
  float s;
  if (rgbmax == rgbmin)
  {
	h = 0;
    s = 0;
  }
  else
  {
	float mmm = rgbmax - rgbmin;  // "max minus min"
	float mpm = rgbmax + rgbmin;  // "max plus min"

	// Saturation
	if (l <= 0.5f)
	{
      s = mmm / mpm;
	}
    else
	{
      s = mmm / (2.0f - mpm);
	}

	// Hue
	float x =  -0.5f * values[0] -   0.5f * values[1] + values[2];
	float y = root32 * values[0] - root32 * values[1];
	h = atan2f (y, x) / TWOPIf - onethird;
	if (h < 0)
	{
	  h += 1.0f;
	}
  }

  ((float *) pixel)[0] = h;
  ((float *) pixel)[1] = s;
  ((float *) pixel)[2] = l;
}

void
PixelFormatHSLFloat::setHSL (void * pixel, float values[]) const
{
  ((float *) pixel)[0] = values[0];
  ((float *) pixel)[1] = values[1];
  ((float *) pixel)[2] = values[2];
}


// class PixelFormatHSVFloat --------------------------------------------------

PixelFormatHSVFloat::PixelFormatHSVFloat ()
{
  planes     = 1;
  depth      = 3 * sizeof (float);
  precedence = 7;  // on par with RGBAFloat
  monochrome = false;
  hasAlpha   = false;
}

uint32_t
PixelFormatHSVFloat::getRGBA (void * pixel) const
{
  float rgbaValues[4];
  getRGBA (pixel, rgbaValues);
  uint32_t r = lutFloat2Char[(uint16_t) (65535 * rgbaValues[0])];
  uint32_t g = lutFloat2Char[(uint16_t) (65535 * rgbaValues[1])];
  uint32_t b = lutFloat2Char[(uint16_t) (65535 * rgbaValues[2])];
  uint32_t a = (uint32_t) (255 * rgbaValues[3]);
  return (r << 24) | (g << 16) | (b << 8) | a;
}

void
PixelFormatHSVFloat::getRGBA (void * pixel, float values[]) const
{
  float h = ((float *) pixel)[0];
  float s = ((float *) pixel)[1];
  float v = ((float *) pixel)[2];

  assert (h >= 0  &&  h <= 1);

  float c = v * s;
  h *= 6;
  float x = c * (1 - abs (fmod (h, 2) - 1));

  if (h < 1)
  {
	values[0] = c;
	values[1] = x;
	values[2] = 0;
  }
  else if (h < 2)
  {
	values[0] = x;
	values[1] = c;
	values[2] = 0;
  }
  else if (h < 3)
  {
	values[0] = 0;
	values[1] = c;
	values[2] = x;
  }
  else if (h < 4)
  {
	values[0] = 0;
	values[1] = x;
	values[2] = c;
  }
  else if (h < 5)
  {
	values[0] = x;
	values[1] = 0;
	values[2] = c;
  }
  else  // h <= 6
  {
	values[0] = c;
	values[1] = 0;
	values[2] = x;
  }

  float m = v - c;
  values[0] += m;
  values[1] += m;
  values[2] += m;
  values[3] = 1.0f;
}

void
PixelFormatHSVFloat::getHSV (void * pixel, float values[]) const
{
  values[0] = ((float *) pixel)[0];
  values[1] = ((float *) pixel)[1];
  values[2] = ((float *) pixel)[2];
}

void
PixelFormatHSVFloat::setRGBA (void * pixel, uint32_t rgba) const
{
  float rgbaValues[3];  // Ignore alpha channel, because it is not processed or stored by any function of this format.
  rgbaValues[0] = lutChar2Float[ rgba             >> 24];
  rgbaValues[1] = lutChar2Float[(rgba & 0xFF0000) >> 16];
  rgbaValues[2] = lutChar2Float[(rgba &   0xFF00) >>  8];
  setRGBA (pixel, rgbaValues);
}

void
PixelFormatHSVFloat::setRGBA (void * pixel, float values[]) const
{
  const float & r = values[0];
  const float & g = values[1];
  const float & b = values[2];

  float v =     max (r, max (g, b));
  float c = v - min (r, min (g, b));

  // Hue and Saturation
  float h;
  if      (c == 0) h = 0;
  else if (v == r) h = (g - b) / c;
  else if (v == g) h = (b - r) / c + 2;
  else             h = (r - g) / c + 4;  // v == b
  if (h < 0) h += 6;  // this is the only boundary condition
  h /= 6;

  float s;
  if (c == 0) s = 0;
  else        s = c / v;

  ((float *) pixel)[0] = h;
  ((float *) pixel)[1] = s;
  ((float *) pixel)[2] = v;
}

void
PixelFormatHSVFloat::setHSV (void * pixel, float values[]) const
{
  ((float *) pixel)[0] = values[0];
  ((float *) pixel)[1] = values[1];
  ((float *) pixel)[2] = values[2];
}
