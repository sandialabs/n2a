/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.plugins.extpoints;


import java.awt.Image;
import java.util.List;

import javax.swing.ImageIcon;

import replete.plugins.ExtensionPoint;

public interface ProductCustomization extends ExtensionPoint {
    public String getProductLongName();
    public String getProductShortName();
    public String getProductVersion();
    public String getProductDevelopedBy();
    public String getCopyright();
    public String getSupportEmail();
    public String getLicense();
    public List<Image> getWindowIcons();
    public ImageIcon getAboutImage();
}
