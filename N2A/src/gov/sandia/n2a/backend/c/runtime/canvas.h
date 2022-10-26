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

#ifndef n2a_canvas_h
#define n2a_canvas_h


#include "matrix.h"
#include "image.h"

#undef SHARED
#ifdef _MSC_VER
#  ifdef _USRDLL
#    define SHARED __declspec(dllexport)
#  elif defined n2a_DLL
#    define SHARED __declspec(dllimport)
#  else
#    define SHARED
#  endif
#else
#  define SHARED
#endif

namespace n2a
{
    typedef MatrixFixed<double,3,1> Point;

    /**
        An image with drawing functions.

        Coordinate conventions: A floating-point coordinate is an exact position in space.
        Float coordinates convert to integer coordinates by truncation, because we treat
        it as addressing the whole pixel within which it falls. The whole pixel is a
        semi-open set on the plane, with the open sides at the high end of the coordinate
        values. Integer coordinates convert to float coordinates by adding 0.5. This is
        an arbitrary position within the pixel, but it minimizes the amount of distance
        from all points in the area.
    **/
    class CanvasImage : public Image
    {
    public:
        float lineWidth;

        CanvasImage (const PixelFormat & format = GrayChar);
        CanvasImage (int width, int height, const PixelFormat & format = GrayChar);
        CanvasImage (const Image & that);
        void initialize ();

        void scanCircle (const Point & p, double radius, uint32_t color, int x0, int y0, int x1, int y1);  // Draws filled circle with the given bounds.
        void scanCircle (const Point & p, double radius, uint32_t color);  // Draws a filled circle.

        // Drawing functions are the primary interface
        // Note: The original Canvas code in FL has many more functions, including drawText().
        void drawSegment         (const Point & a,       const Point & b,       uint32_t color = 0xFFFFFF);
        void drawFilledRectangle (const Point & corner0, const Point & corner1, uint32_t colorFill);

        // State information
        void setLineWidth (float width); ///< Width of pen for stroking lines, in native units.
    };
}

#endif
