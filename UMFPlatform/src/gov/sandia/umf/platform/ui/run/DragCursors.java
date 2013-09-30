/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.run;

import gov.sandia.umf.platform.ui.ensemble.images.ImageUtil;

import java.awt.Cursor;
import java.awt.Image;
import java.awt.Point;
import java.awt.Toolkit;

public class DragCursors {

    ////////////
    // FIELDS //
    ////////////

    // Static

    private static final Cursor openHandCursor;
    private static final Cursor grabHandCursor;


    ////////////////////
    // INITIALIZATION //
    ////////////////////

    static {
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        Image cursorImage = ImageUtil.getImage("openhand.gif").getImage();
        Point cursorHotSpot = new Point(7,7);
        openHandCursor = toolkit.createCustomCursor(cursorImage, cursorHotSpot, "OpenHand");
        cursorImage = ImageUtil.getImage("grabhand.gif").getImage();
        grabHandCursor = toolkit.createCustomCursor(cursorImage, cursorHotSpot, "GrabHand");
    }


    ///////////////
    // ACCESSORS //
    ///////////////

    public static Cursor getOpenhandcursor() {
        return openHandCursor;
    }
    public static Cursor getGrabhandcursor() {
        return grabHandCursor;
    }
}
