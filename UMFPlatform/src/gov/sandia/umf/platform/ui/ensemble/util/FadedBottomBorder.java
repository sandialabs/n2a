/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.umf.platform.ui.ensemble.util;

import java.awt.Color;
import java.awt.Component;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Paint;
import java.awt.Point;

import javax.swing.border.AbstractBorder;

public class FadedBottomBorder extends AbstractBorder {

    private int thickness;
    private Color lineColor = Color.red;

    public FadedBottomBorder(int thick, Color lc) {
        thickness = thick;
        lineColor = lc;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Graphics2D g2d = (Graphics2D) g;
        Paint prev = g2d.getPaint();
        g2d.setColor(lineColor);
        Point startPoint = new Point(x, y + height - 1);
        Point endPoint = new Point(x + width - 1, y + height - 1);
        Color faded = new Color(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), 0);
        Paint gradientPaint = new GradientPaint(startPoint, lineColor, endPoint, faded, false);
        g2d.setPaint(gradientPaint);
        g2d.fillRect(x, y + height - thickness, width, thickness);
        g2d.setPaint(prev);
    }

    @Override
    public Insets getBorderInsets(Component c) {
        // return the top, bottom, left, right spacing in pixels the border will occupy
        return new Insets(thickness, thickness, thickness, thickness);
    }

    @Override
    public Insets getBorderInsets(Component c, Insets insets) {
        insets.left = insets.top = insets.right = insets.bottom = thickness;
        return insets;
    }
}
