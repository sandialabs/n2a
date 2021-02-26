/*
Copyright 2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.ref;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.io.StringReader;

@SuppressWarnings("deprecation")
public class TransferableReference implements Transferable, ClipboardOwner
{
    public String data;      // BibTeX serialized version of references
    public String selection; // If non-null, ID of lead selection when a drag was initiated in either search or MRU panel. Used to re-order MRU.

    public static final DataFlavor referenceFlavor = new DataFlavor (TransferableReference.class, null);

    public TransferableReference (String data, String selection)
    {
        this.data      = data;
        this.selection = selection;
    }

    @Override
    public void lostOwnership (Clipboard clipboard, Transferable contents)
    {
    }

    @Override
    public DataFlavor[] getTransferDataFlavors ()
    {
        DataFlavor[] result = new DataFlavor[3];
        result[0] = DataFlavor.stringFlavor;
        result[1] = DataFlavor.plainTextFlavor;
        result[2] = referenceFlavor;
        return result;
    }

    @Override
    public boolean isDataFlavorSupported (DataFlavor flavor)
    {
        if (flavor.equals (DataFlavor.stringFlavor   )) return true;
        if (flavor.equals (DataFlavor.plainTextFlavor)) return true;
        if (flavor.equals (referenceFlavor           )) return true;
        return false;
    }

    @Override
    public Object getTransferData (DataFlavor flavor) throws UnsupportedFlavorException, IOException
    {
        if (flavor.equals (DataFlavor.stringFlavor   )) return data;
        if (flavor.equals (DataFlavor.plainTextFlavor)) return new StringReader (data);
        if (flavor.equals (referenceFlavor           )) return this;
        throw new UnsupportedFlavorException (flavor);
    }
}
