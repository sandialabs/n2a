/*
Copyright 2013,2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

import javax.swing.JPanel;

/**
 * A GradientPanel attempts to simplify the creation of a
 * panel with a gradient background.  Normally one would
 * override the paintComponent method of a JPanel and then
 * use GradientPaint to create this effect.  This class
 * provides a way to create that effect without having to
 * write as much code, think very hard about it, nor
 * hard-coding any single static gradient pattern.
 *
 * This class has 4 important configurable properties:
 *   * Color 1
 *   * Color 2
 *   * Angle (expressed in degrees on (-INF, INF))
 *   * Span (expressed as a percentage on [0.0, INF))
 *
 * The angle initially starts out as 0.0 and represents
 * the angle of the dividing line between the two colors.
 * If the angle is 0.0, the dividing line is completely
 * horizontal. If the angle is 90.0, the dividing line
 * is completely vertical.  Color 1 and Color 2 are
 * the two colors on the top and bottom or left and right
 * of the dividing line, respectively, in the above two
 * cases.  When the span is set to its default value (1.0),
 * a panel with an angle of 0.0, with colors
 * blue and green, is the same as a panel with an angle
 * of 180.0, with colors green and blue. The setColor
 * method takes a single color, and chooses for Color 2
 * the Color.darker() of that color.
 *
 * The span initially starts out as 1.0 (which represents
 * a 100% span).  It exists in
 * case you don't want the gradient to travel across the
 * entire length of the panel.  Setting the span to 0.5,
 * for example, makes the area that is applied the gradient
 * only extend halfway from the Color 1 side to the Color 2
 * side.  This means that half of the panel will be a
 * pure Color 2.  You can set values greater than 1.0 to
 * push the span off past the Color 2 edge, creating an
 * even more subtle gradient effect.
 *
 * One possible deficiency of this class is that the points
 * that represent the end of the gradient line are always on
 * the edge of the panel or inside it.  So even if span is
 * set to 1.0, when the end points are not exactly on one
 * of the 8 cardinal directions (N, NE, E, ...), there can
 * be areas on the panel that are pure Color 1 or pure
 * Color 2.  This could be fixed with a little geometry,
 * extending these points outside the bounds of the panel
 * until the gradient covers the panel corner to corner
 * regardless of angle.  See the TestGradientPanel below
 * to see where each of the relevant points lie.
 *
 * Remember that components that are contained within
 * this panel need to have setOpaque(false) called on
 * them to have the gradient show through.
 *
 * @author Derek Trumbo
 */

public class GradientPanel extends JPanel {

    ////////////
    // FIELDS //
    ////////////

    public static Color INIT_COLOR = new JPanel().getBackground();

    protected Color color1;
    protected Color color2;

    protected double angle = 0.0;   // Expressed in degrees on [0.0, 360.0)
    protected double span = 1.0;    // Expressed as a percentage on [0.0, 100.0]

    protected int initEdgeX;
    protected int initEdgeY;
    protected int farEdgeX;
    protected int farEdgeY;
    protected int insideX;
    protected int insideY;

    private boolean gradientDisabled;

    //////////////////
    // CONSTRUCTORS //
    //////////////////

    // Existing constructors.

    public GradientPanel() {
        setColor(INIT_COLOR);
        addListeners();
    }
    public GradientPanel(boolean isDoubleBuffered) {
        super(isDoubleBuffered);
        setColor(INIT_COLOR);
        addListeners();
    }
    public GradientPanel(LayoutManager layout) {
        super(layout);
        setColor(INIT_COLOR);
        addListeners();
    }
    public GradientPanel(LayoutManager layout, boolean isDoubleBuffered) {
        super(layout, isDoubleBuffered);
        setColor(INIT_COLOR);
        addListeners();
    }

    // New constructors.

    public GradientPanel(Color c) {
        this(new FlowLayout(), true, c);
    }
    public GradientPanel(LayoutManager layout, Color c) {
        this(layout, true, c);
    }
    public GradientPanel(LayoutManager layout, boolean isDoubleBuffered, Color c) {
        super(layout, isDoubleBuffered);
        setColor(c);
        addListeners();
    }

    public GradientPanel(Color c1, Color c2) {
        this(new FlowLayout(), true, c1, c2);
    }
    public GradientPanel(LayoutManager layout, Color c1, Color c2) {
        this(layout, true, c1, c2);
    }
    public GradientPanel(LayoutManager layout, boolean isDoubleBuffered, Color c1, Color c2) {
        super(layout, isDoubleBuffered);
        setColors(c1, c2);
        addListeners();
    }

    ////////////////////
    // INITIALIZATION //
    ////////////////////

    protected void addListeners() {
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                calcGradientExtentPoints();
            }
        });
    }

    ///////////////////////
    // GETTERS / SETTERS //
    ///////////////////////

    public Color getColor1() {
        return color1;
    }
    public Color getColor2() {
        return color2;
    }

    public void setColor(Color c) {
        if(c == null) {
            throw new IllegalArgumentException("color cannot be null.");
        }
        color1 = c;
        color2 = c.darker();
        repaint();
    }
    public void setColor1(Color c) {
        if(c == null) {
            throw new IllegalArgumentException("color cannot be null.");
        }
        color1 = c;
        repaint();
    }
    public void setColor2(Color c) {
        if(c == null) {
            throw new IllegalArgumentException("color cannot be null.");
        }
        color2 = c;
        repaint();
    }
    public void setColors(Color c1, Color c2) {
        if(c1 == null || c2 == null) {
            throw new IllegalArgumentException("colors cannot be null.");
        }
        color1 = c1;
        color2 = c2;
        repaint();
    }

    public double getAngle() {
        return angle;
    }
    public void setAngle(double angle) {
        this.angle = angle;
        calcGradientExtentPoints();
    }
    public double getSpan() {
        return span;
    }
    public void setSpan(double span) {
        this.span = span;
        calcGradientExtentPoints();
    }
    public boolean isGradientDisabled() {
        return gradientDisabled;
    }
    public void setGradientDisabled(boolean gradientDisabled) {
        this.gradientDisabled = gradientDisabled;
        repaint();
    }


    ///////////////
    // CALCULATE //
    ///////////////

    // This is called by the set methods and resize
    // event so that all these points do not need
    // to be calculated each time paint is called.

    protected void calcGradientExtentPoints() {

        // Start by always rotating by 90 degrees because
        // the angle of the gradient line is perpendicular
        // to the line that will divides the two colors.
        double gradientPaintAngle = angle + 90.0;

        // Remove extra revolutions and negative angles.
        int revs = (int) (gradientPaintAngle / 360.0);
        double localAngle = gradientPaintAngle - revs * 360.0;
        if(localAngle < 0.0) {
            localAngle += 360.0;
        }

        int halfWidth = getWidth() / 2;
        int halfHeight = getHeight() / 2;

        if(localAngle >= 45.0 && localAngle < 135.0) {
            double availableAngle = Math.atan((double) halfWidth / halfHeight);
            double correctedAngle = (90 - localAngle) / 45.0 * availableAngle;

            initEdgeX = (int) (halfWidth + Math.tan(correctedAngle) * halfHeight);
            initEdgeY = 0;

            farEdgeX = getWidth() - initEdgeX;
            farEdgeY = getHeight();

        } else if(localAngle >= 135.0 && localAngle < 225.0) {
            double availableAngle = Math.atan((double) halfHeight / halfWidth);
            double correctedAngle = (localAngle - 180.0) / 45.0 * availableAngle;

            initEdgeX = 0;
            initEdgeY = (int) (halfHeight + Math.tan(correctedAngle) * halfWidth);

            farEdgeX = getWidth();
            farEdgeY = getHeight() - initEdgeY;
        } else if(localAngle >= 225.0 && localAngle < 315.0) {
            double availableAngle = Math.atan((double) halfWidth / halfHeight);
            double correctedAngle = (270.0 - localAngle) / 45.0 * availableAngle;

            initEdgeX = (int) (halfWidth - Math.tan(correctedAngle) * halfHeight);
            initEdgeY = getHeight();

            farEdgeX = getWidth() - initEdgeX;
            farEdgeY = 0;
        } else {
            double correctedAngle = localAngle;
            if(localAngle >= 315.0) {
                correctedAngle = localAngle - 360.0;
            }
            double availableAngle = Math.atan((double) halfHeight / halfWidth);
            correctedAngle = correctedAngle / 45.0 * availableAngle;

            initEdgeX = getWidth();
            initEdgeY = (int) (halfHeight - Math.tan(correctedAngle) * halfWidth);

            farEdgeX = 0;
            farEdgeY = getHeight() - initEdgeY;
        }

        insideX = (int) ((farEdgeX - initEdgeX) * span + initEdgeX);
        insideY = (int) ((farEdgeY - initEdgeY) * span + initEdgeY);

        if(localAngle == 90.0 || localAngle == 270.0) {
            insideX = initEdgeX;
        } else if(localAngle == 0.0 || localAngle == 180.0) {
            insideY = initEdgeY;
        }

        repaint();
    }


    ///////////
    // PAINT //
    ///////////

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        if(gradientDisabled) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g;
        g2.setPaint(new GradientPaint(initEdgeX, initEdgeY, color1, insideX, insideY, color2));
        g2.fillRect(0, 0, getWidth(), getHeight());
    }
}
