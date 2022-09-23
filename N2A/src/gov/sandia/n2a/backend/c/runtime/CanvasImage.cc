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

#include "canvas.h"
#include "Matrix.tcc"
#include "MatrixFixed.tcc"

#include <algorithm>

using namespace n2a;
using namespace std;


// Utiltiy functions ----------------------------------------------------------

static inline void
flipX (float & angle)
{
    // Act as if circle has been flipped along vertical axis
    if (angle < M_PI) angle =     M_PI - angle;
    else              angle = 3 * M_PI - angle;
}

static inline void
flipY (float & angle)
{
    // Act as if circle has been flipped along horizontal axis
    angle = TWOPIf - angle;
}

static inline bool inRange (float angle, const float & startAngle, const float & endAngle)
{
    while (angle < startAngle) angle += TWOPIf;
    return angle <= endAngle;
}


// class CanvasImage ----------------------------------------------------------

// Most of the implementations here are hacks based on float.  They could be
// made more efficient for changing to Bresenhem-like approaches.

CanvasImage::CanvasImage (const PixelFormat & format)
:   Image (format)
{
    initialize ();
}

CanvasImage::CanvasImage (int width, int height, const PixelFormat & format)
:   Image (width, height, format)
{
    initialize ();
}

CanvasImage::CanvasImage (const Image & that)
:   Image (that)
{
    initialize ();
}

void
CanvasImage::initialize ()
{
    lineWidth = 0;
    setLineWidth (1);
}

/// Scanline convert a filled circle, with anti-aliasing
inline void
CanvasImage::scanCircle (const Point & p, double radius, uint32_t color, int x0, int y0, int x1, int y1)
{
    x0 = max (0, x0);
    y0 = max (0, y0);
    x1 = min (width  - 1, x1);
    y1 = min (height - 1, y1);

    double px = p[0];
    double py = p[1];
    uint32_t alpha = color & 0xFF;
    for (int x = x0; x <= x1; x++)
    {
        for (int y = y0; y <= y1; y++)
        {
            double dx = abs (x - px);
            double dy = abs (y - py);
            double r = sqrt (dx * dx + dy * dy);
            double m = radius - (r - 0.5);
            if (m < 0) continue;  // pixel is out of range
            m = min (1.0, m); // if pixel is fully inside circle, then limit alpha to 1
            blend (x, y, (color & 0xFFFFFF00) | (uint32_t) (alpha * m));
        }
    }
}

inline void
CanvasImage::scanCircle (const Point & p, double radius, uint32_t color)
{
    double px = p[0];
    double py = p[1];
    if (radius == 0.5)  // Early-out for 1px dot.
    {
        int x = (int) roundp (px);
        int y = (int) roundp (py);
        if (x >= 0 && y >= 0 && x < width && y < height) setRGBA (x, y, color);
        return;
    }

    int x0 = (int) roundp (px - radius);
    int y0 = (int) roundp (py - radius);
    int x1 = (int) roundp (px + radius);
    int y1 = (int) roundp (py + radius);

    scanCircle (p, radius, color, x0, y0, x1, y1);
}

// The following is an implementation of the Cohen-Sutherland clipping
// algorithm, for use by drawSegment(). We use Cohen-Sutherland because
// it has faster early-out than other clipping algorithms, and we assume
// that most segments do not cross the boundary of the drawable area.

static const int LEFT   = 0x1;
static const int RIGHT  = 0x2;
static const int TOP    = 0x4;
static const int BOTTOM = 0x8;

static inline int
clipCode (const double & fWidth, const double & fHeight, const Point & a)
{
    int result = 0;
    double ax = a[0];
    double ay = a[1];
    if      (ax < -0.5)    result |= LEFT;
    else if (ax > fWidth)  result |= RIGHT;
    if      (ay < -0.5)    result |= TOP;
    else if (ay > fHeight) result |= BOTTOM;
    return result;
}

static inline bool
clip (const int & width, const int & height, Point & a, Point & b)
{
    double & ax = a[0];
    double & ay = a[1];
    double & bx = b[0];
    double & by = b[1];

    const double fWidth  = width  - 0.500001;
    const double fHeight = height - 0.500001;
    int clipA = clipCode (fWidth, fHeight, a);
    int clipB = clipCode (fWidth, fHeight, b);
    while (true)
    {
        if (!(clipA | clipB)) return true;
        if (clipA & clipB) return false;

        double x;
        double y;
        int endpoint = clipA ? clipA : clipB;
        if (endpoint & LEFT)
        {
            x = 0;
            y = ay - ax * (by - ay) / (bx - ax);
        }
        else if (endpoint & RIGHT)
        {
            x = fWidth;
            y = ay + (fWidth - ax) * (by - ay) / (bx - ax);
        }
        else if (endpoint & TOP)
        {
            x = ax - ay * (bx - ax) / (by - ay);
            y = 0;
        }
        else  // endpoint & BOTTOM
        {
            x = ax + (fHeight - ay) * (bx - ax) / (by - ay);
            y = fHeight;
        }

        if (endpoint == clipA)
        {
            ax = x;
            ay = y;
            clipA = clipCode (fWidth, fHeight, a);
        }
        else  // endpoint == clipB
        {
            bx = x;
            by = y;
            clipB = clipCode (fWidth, fHeight, b);
        }
    }
}

static inline void
bounds (const int & u, const double & v, const double & w, const double & cap, const double & r, const double & u0, const double & u1, const double & v0, const double & v1, double & lo, double & hi)
{
    lo = v - w;
    hi = v + w;

    double d = u - u0;
    if (d < cap)
    {
        double s = abs (d / r);  // sine
        double a = asin (s);
        double w2 = r * cos (a);
        if (v1 > v0) lo = v0 - w2;
        else         hi = v0 + w2;
    }

    d = u1 - u;
    if (d < cap)
    {
        double s = abs (d / r);
        double a = asin (s);
        double w2 = r * cos (a);
        if (v1 > v0) hi = v1 + w2;
        else         lo = v1 - w2;
    }
}

/**
    @todo Implement precision anit-aliasing. Rather than computing where each
    (top and bottom) edge of the line crosses the center of a column of pixels,
    compute where they cross the left and right edges of the column. This gives
    a parallelogram. Up to 2 pixels at each end of the column will be partially
    filled, and all the others completely filled. To handle end-caps, check
    if either end-point is closer than a given corner of the parallelogram.
    The very tip of the end-cap will instead require a filled-circle conversion.
    This would only be an approximation, but figuring intersection of area
    between a circle and a square is probably overkill.
**/
void
CanvasImage::drawSegment (const Point & a, const Point & b, uint32_t color)
{
    Point ta (a);
    Point tb (b);

    if (!clip (width, height, ta, tb)) return;

    double dx = tb[0] - ta[0];
    double dy = tb[1] - ta[1];

    if (dx == 0 && dy == 0)
    {
        scanCircle (ta, lineWidth / 2, color);
        return;
    }

    bool steep = abs (dy) > abs (dx);
    if (steep)  // sweeping on y axis
    {
        if (dy < 0) swap (ta, tb); // signs of dx and dy are both reversed, but slope keeps same sign, so no need to change dx and dy
    }
    else  // sweeping on x axis
    {
        if (dx < 0) swap (ta, tb);  // ditto
    }

    if (lineWidth == 1) // draw single-pixel line, as fast as possible
    {
        // Bresenham's algorithm, with strictly integer math

        int x0 = roundp (ta[0]);
        int y0 = roundp (ta[1]);
        int x1 = roundp (tb[0]);
        int y1 = roundp (tb[1]);

        int dx = abs (x1 - x0);
        int dy = abs (y1 - y0);

        if (steep)
        {
            int error = dy / 2;
            int step = x0 < x1 ? 1 : -1;
            int x = x0;
            for (int y = y0; y <= y1; y++)
            {
                setRGBA (x, y, color);
                if ((error -= dx) < 0)
                {
                    x += step;
                    error += dy;
                }
            }
        }
        else
        {
            int error = dx / 2;
            int step = y0 < y1 ? 1 : -1;
            int y = y0;
            for (int x = x0; x <= x1; x++)
            {
                setRGBA (x, y, color);
                if ((error -= dy) < 0)
                {
                    y += step;
                    error += dx;
                }
            }
        }

        return;
    }

    // General algorithm, based on floating-point math.
    double l = sqrt (dx * dx + dy * dy);
    double c = abs (dx) / l;  // cosine
    double s = abs (dy) / l;  // sine
    if (steep) swap (c, s);
    double r = lineWidth / 2;  // radius of line
    double w = r / c;  // width along pixel row
    double cap = r * s;  // bound where end-cap begins to cut off eges of line
    uint32_t alpha = color & 0xFF;
    if (steep)
    {
        int y0 = (int) ceil  (ta[1] - cap);
        int y1 = (int) floor (tb[1] + cap);
        double x0 = ta[0] - ta[1] * dx / dy;
        for (int y = y0; y <= y1; y++)
        {
            double lo;
            double hi;
            bounds (y, y * dx / dy + x0, w, cap, r, ta[1], tb[1], ta[0], tb[0], lo, hi);
            int xlo = (int) roundp (lo);
            int xhi = (int) roundp (hi);
            if (xlo == xhi)
            {
                blend (xlo, y, (color & 0xFFFFFF00) | (uint32_t) (alpha * lineWidth)); // this case is only possible when lineWidth < 1
            }
            else
            {
                blend (xlo, y, (color & 0xFFFFFF00) | (uint32_t) (alpha * (xlo + 0.5 - lo)));
                if (alpha == 0xFF) for (int x = xlo + 1; x < xhi; x++) setRGBA (x, y, color);
                else               for (int x = xlo + 1; x < xhi; x++) blend   (x, y, color);
                blend (xhi, y, (color & 0xFFFFFF00) | (uint32_t) (alpha * (hi - xhi + 0.5)));
            }
        }

        int v0 = (int) roundp (ta[0] - r);
        int v1 = (int) roundp (ta[0] + r);
        int u  = (int) roundp (ta[1] - r);
        scanCircle (ta, r, color, v0, u, v1, y0 - 1);

        v0 = (int) roundp (tb[0] - r);
        v1 = (int) roundp (tb[0] + r);
        u  = (int) roundp (tb[1] + r);
        scanCircle (tb, r, color, v0, y1 + 1, v1, u);
    }
    else
    {
        int x0 = (int) ceil  (ta[0] - cap);
        int x1 = (int) floor (tb[0] + cap);
        double y0 = ta[1] - ta[0] * dy / dx;
        for (int x = x0; x <= x1; x++)
        {
            double lo;
            double hi;
            bounds (x, x * dy / dx + y0, w, cap, r, ta[0], tb[0], ta[1], tb[1], lo, hi);
            int ylo = (int) roundp (lo);
            int yhi = (int) roundp (hi);
            if (ylo == yhi)
            {
                blend (x, ylo, (color & 0xFFFFFF00) | (uint32_t) (alpha * lineWidth));
            }
            else
            {
                blend (x, ylo, (color & 0xFFFFFF00) | (uint32_t) (alpha * (ylo + 0.5 - lo)));
                if (alpha == 0xFF) for (int y = ylo + 1; y < yhi; y++) setRGBA (x, y, color);
                else               for (int y = ylo + 1; y < yhi; y++) blend   (x, y, color);
                blend (x, yhi, (color & 0xFFFFFF00) | (uint32_t) (alpha * (hi - yhi + 0.5)));
            }
        }

        int v0 = (int) roundp (ta[1] - r);
        int v1 = (int) roundp (ta[1] + r);
        int u  = (int) roundp (ta[0] - r);
        scanCircle (ta, r, color, u, v0, x0 - 1, v1);

        v0 = (int) roundp (tb[1] - r);
        v1 = (int) roundp (tb[1] + r);
        u  = (int) roundp (tb[0] + r);
        scanCircle (tb, r, color, x1 + 1, v0, u, v1);
    }
}

struct Segment
{
    float x;
    float slope;
};

struct Vertex
{
    Point             p;
    Vertex *          pred;
    Vertex *          succ;
    vector<Segment *> active;
};

static inline void
advanceX (float deltaY, vector<Segment *> & active)
{
    // Increment X positions
    for (int i = 0; i < active.size (); i++)
    {
        active[i]->x += active[i]->slope * deltaY;
    }

    // Bubble sort
    bool changed = true;
    while (changed)
    {
        changed = false;
        for (int i = 1; i < active.size (); i++)
        {
            if (active[i - 1]->x > active[i]->x)
            {
                swap (active[i - 1], active[i]);
                changed = true;
            }
        }
    }
}

static inline void
insertSegment (Vertex * smallerY, Vertex * biggerY, vector<Segment *> & active)
{
    Segment * s = new Segment;
    s->x = smallerY->p[0];
    s->slope = (biggerY->p[0] - smallerY->p[0]) / (biggerY->p[1] - smallerY->p[1]);
    biggerY->active.push_back (s);

    // Insert into active in X order.
    // This linear search is reasonably efficient when there are only 2 or 4 active segments.
    // Should use the more efficient binary search for more complex polygons
    int i;
    for (i = 0; i < active.size (); i++)
    {
        if (s->x <= active[i]->x)
        {
            active.insert (active.begin () + i, s);
            break;
        }
    }
    if (i >= active.size ()) active.push_back (s);
}

void
CanvasImage::drawFilledRectangle (const Point & corner0, const Point & corner1, uint32_t colorFill)
{
    int x0 = (int) roundp (corner0[0]);
    int x1 = (int) roundp (corner1[0]);
    int y0 = (int) roundp (corner0[1]);
    int y1 = (int) roundp (corner1[1]);

    if (x0 > x1) swap (x0, x1);
    if (y0 > y1) swap (y0, y1);

    if (x1 < 0 || x0 >= width || y1 < 0 || y0 >= height) return;

    x0 = max (x0, 0);
    x1 = min (x1, width - 1);
    y0 = max (y0, 0);
    y1 = min (y1, height - 1);

    for (int y = y0; y <= y1; y++)
    {
        for (int x = x0; x <= x1; x++)
        {
            setRGBA (x, y, colorFill);
        }
    }
}

void
CanvasImage::drawCircle (const Point & center, float radius, uint32_t color, float startAngle, float endAngle)
{
    // TODO: find a method (such as scanCircle()) which does a simple circle, with angles or anisotropy
    //MatrixFixed<double,2,2> A;
    //A.identity ();
    //drawEllipse (center, A, radius, color, startAngle, endAngle);
}

void
CanvasImage::setLineWidth (float width)
{
    lineWidth = width;
}
