/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform;

import gov.sandia.umf.platform.plugins.extpoints.ProductCustomization;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.awt.Image;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ImageIcon;

public class PlatformProductCustomization implements ProductCustomization
{
    public String getProductLongName ()
    {
        return "Unified Modeling Framework";
    }

    public String getProductShortName ()
    {
        return "UMF";
    }

    public String getProductVersion ()
    {
        return PlatformPlugin.getInstance ().getVersion ();
    }

    public String getProductDevelopedBy ()
    {
        return "Derek Trumbo";
    }

    public String getCopyright ()
    {
        return "Copyright &copy; 2013 Sandia Corporation. Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation, the U.S. Government retains certain rights in this software.";
    }

    @Override
    public String getSupportEmail ()
    {
        return "n2a@sandia.gov";
    }

    public String getLicense ()
    {
        return "This software is released under the BSD license.  Please refer to the license information provided in this distribution for the complete text.";
    }

    public List<Image> getWindowIcons ()
    {
        ArrayList<Image> result = new ArrayList<Image> ();
        result.add (ImageUtil.getImage ("model.gif").getImage ());
        return result;
    }

    public ImageIcon getAboutImage ()
    {
        return ImageUtil.getImage ("umf.png");
    }
}
