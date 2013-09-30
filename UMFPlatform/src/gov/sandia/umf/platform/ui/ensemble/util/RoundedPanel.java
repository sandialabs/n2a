/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.ensemble.util;

import gov.sandia.umf.platform.ui.ensemble.images.ImageUtil;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;

import replete.gui.controls.IconButton;
import replete.gui.windows.EscapeFrame;
import replete.util.Lay;

//http://www.codeproject.com/Articles/114959/Rounded-Border-JPanel-JPanel-graphics-improvements

public class RoundedPanel extends JPanel {
    /** Stroke size. it is recommended to set it to 1 for better view */
    protected int strokeSize = 1;
    /** Color of shadow */
    protected Color shadowColor = Color.black;
    /** Sets if it drops shadow */
    protected boolean shady = true;
    /** Sets if it has an High Quality view */
    protected boolean highQuality = true;
    /** Double values for Horizontal and Vertical radius of corner arcs */
    protected Dimension arcs = new Dimension(20, 20);
    /** Distance between shadow border and opaque panel border */
    protected int shadowGap = 5;
    /** The offset of shadow.  */
    protected int shadowOffset = 4;
    /** The transparency value of shadow. ( 0 - 255) */
    protected int shadowAlpha = 150;

    public RoundedPanel() {
        super();
        setOpaque(false);
    }

    public static void main(String[] args) {
        JFrame f = new EscapeFrame();


RoundedPanel r;
r =new RoundedPanel();

//r.setLayout(new BorderLayout());
//r.add(new JLabel("asdfasf"), BorderLayout.NORTH);
//r.add(new JButton("afds"), BorderLayout.CENTER);

        Lay.BLtg(r,
            "N", Lay.BL(
                "C", Lay.GL(2, 1,
                    Lay.lb("Group #"),
                    new JComboBox(new Object[] {"Mixed", "Latin Hypercube", "Monte Carlo"}),
                    "opaque=false"
                ),
                "E", Lay.BL(
                    "C", Lay.BL(
                        "W", Lay.lb("#"),
                        "C", new JTextField(10)
                    ),
                    "E", new IconButton(ImageUtil.getImage("cancel.gif"), "remove group!"),
                    "opaque=false"
                ),
                "bg=yellow,eb=10,opaque=false"
            ),
            "C", Lay.BxL("Y", Lay.lb("adfasdf" + " Const(" + 3333 + ")"), "eb=10,opaque=false")
            //"mb=[1,black],augb=eb(10),bg=red,dimh=100"
        );
Lay.BLtg(f, "C", r, "size=[500,500],center=2,visible");

    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int width = getWidth();
        int height = getHeight();
        int shadowGap = this.shadowGap;
        Color shadowColorA = new Color(shadowColor.getRed(),
    shadowColor.getGreen(), shadowColor.getBlue(), shadowAlpha);
        Graphics2D graphics = (Graphics2D) g;

        //Sets antialiasing if HQ.
        if (highQuality) {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
        }

        //Draws shadow borders if any.
        if (shady) {
            graphics.setColor(shadowColorA);
            graphics.fillRoundRect(
                    shadowOffset,// X position
                    shadowOffset,// Y position
                    width - strokeSize - shadowOffset, // width
                    height - strokeSize - shadowOffset, // height
                    arcs.width, arcs.height);// arc Dimension
        } else {
            shadowGap = 1;
        }

        //Draws the rounded opaque panel with borders.
        graphics.setColor(getBackground());
        graphics.fillRoundRect(0, 0, width - shadowGap,height - shadowGap, arcs.width, arcs.height);
        graphics.setColor(getForeground());
        graphics.setStroke(new BasicStroke(strokeSize));
        graphics.drawRoundRect(0, 0, width - shadowGap, height - shadowGap, arcs.width, arcs.height);
//System.out.println(height);
        //Sets strokes to default, is better.
        graphics.setStroke(new BasicStroke());
    }
}
