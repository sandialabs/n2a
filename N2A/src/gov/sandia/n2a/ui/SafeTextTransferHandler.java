/*
Copyright 2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


package gov.sandia.n2a.ui;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.im.InputContext;
import java.io.IOException;

import javax.swing.JComponent;
import javax.swing.TransferHandler;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

/**
    Supports basic CCP and DnD for JTextComponents while rejecting any drop/paste which contains N2A data.
**/
public class SafeTextTransferHandler extends TransferHandler
{
    public boolean canImport (TransferSupport support)
    {
        JTextComponent c = (JTextComponent) support.getComponent ();
        if (! (c.isEditable ()  &&  c.isEnabled ())) return false;

        // A subtle gotcha is that canImport() can't actually read DnD data, because it is called
        // before the drop is accepted. Instead, we have to reject N2A-sourced data in importData().
        // Here, we simply check if the right flavor is available.
        return support.isDataFlavorSupported (DataFlavor.stringFlavor);
    }

    public boolean importData (TransferSupport support)
    {
        try
        {
            String data = (String) support.getTransferable ().getTransferData (DataFlavor.stringFlavor);
            if (data.startsWith ("N2A.schema")) return false;

            JTextComponent comp = (JTextComponent) support.getComponent ();
            InputContext ic = comp.getInputContext ();
            if (ic != null) ic.endComposition ();
            comp.replaceSelection (data);
            return true;
        }
        catch (UnsupportedFlavorException | IOException e)
        {
        }
        return false;
    }

    public int getSourceActions (JComponent c)
    {
        return NONE;
    }

    public void exportToClipboard (JComponent comp, Clipboard clipboard, int action) throws IllegalStateException
    {
        JTextComponent text = (JTextComponent) comp;
        int p0 = text.getSelectionStart ();
        int p1 = text.getSelectionEnd ();
        if (p0 != p1)
        {
            try
            {
                Document doc = text.getDocument ();
                String srcData = doc.getText (p0, p1 - p0);
                StringSelection contents = new StringSelection (srcData);
                clipboard.setContents (contents, null);
                if (action == TransferHandler.MOVE) doc.remove (p0, p1 - p0);
            }
            catch (BadLocationException ble)
            {
            }
        }
    }
}
