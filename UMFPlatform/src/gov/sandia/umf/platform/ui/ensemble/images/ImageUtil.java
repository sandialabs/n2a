/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.umf.platform.ui.ensemble.images;

import javax.swing.ImageIcon;

import replete.util.GUIUtil;

public class ImageUtil {
    public static ImageIcon getImage(String name) {
        return GUIUtil.getImageLocal(name);
    }
}
