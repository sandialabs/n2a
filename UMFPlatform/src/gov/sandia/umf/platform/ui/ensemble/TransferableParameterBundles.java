/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.umf.platform.ui.ensemble;

import gov.sandia.n2a.parms.ParameterBundle;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.util.List;


public class TransferableParameterBundles implements Transferable {


    ////////////
    // FIELDS //
    ////////////

    public final static DataFlavor BUNDLE_FLAVOR =
        new DataFlavor(ParameterBundle.class, "Parameter Bundle");
    protected static DataFlavor[] supportedFlavors = {BUNDLE_FLAVOR};
    private List<ParameterBundle> bundles;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public TransferableParameterBundles(List<ParameterBundle> b) {
        bundles = b;
    }


    ///////////////
    // ACCESSORS //
    ///////////////

    public DataFlavor[] getTransferDataFlavors() {
        return supportedFlavors;
    }

    public boolean isDataFlavorSupported(DataFlavor flavor) {
        if(flavor.equals(BUNDLE_FLAVOR)) {
            return true;
        }
        return false;
    }

    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
        if(flavor.equals(BUNDLE_FLAVOR)) {
            return bundles;
        }
        throw new UnsupportedFlavorException(flavor);
    }
}
