/*
Copyright 2013,2017 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.images;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.swing.ImageIcon;

public class ImageUtil
{
    public static Map<URL,ImageIcon> loadedImages = new HashMap<URL,ImageIcon> ();

    public static ImageIcon getImage (String name)
    {
        URL imageURL = ImageUtil.class.getResource (name);
        if (imageURL == null) return null;  // May cause NPE in caller, which is a fine way to reveal broken internal logic.
        ImageIcon icon = loadedImages.get (imageURL);
        if (icon == null)
        {
            icon = new ImageIcon (imageURL);
            loadedImages.put (imageURL, icon);
        }
        return icon;
    }
}
