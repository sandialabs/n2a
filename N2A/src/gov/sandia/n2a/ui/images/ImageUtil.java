/*
Copyright 2013,2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
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
