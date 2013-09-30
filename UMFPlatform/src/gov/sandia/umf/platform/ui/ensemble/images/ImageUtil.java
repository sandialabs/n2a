/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.ensemble.images;

import javax.swing.ImageIcon;

import replete.util.GUIUtil;

public class ImageUtil {
    public static ImageIcon getImage(String name) {
        return GUIUtil.getImageLocal(name);
    }
}
