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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.TransferHandler;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.db.Schema;

/**
    Supports basic CCP and DnD for JTextComponents while rejecting any drop/paste which contains N2A data.
**/
@SuppressWarnings("serial")
public class SafeTextTransferHandler extends TransferHandler
{
    public List<String> safeTypes;

    public void setSafeTypes (String... safeTypes)
    {
        if (safeTypes.length == 0) this.safeTypes = null;
        else                       this.safeTypes = Arrays.asList (safeTypes);
    }

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
            if (data.startsWith ("N2A.schema"))
            {
                if (safeTypes == null) return false;

                BufferedReader br     = new BufferedReader (new StringReader (data));
                Schema         schema = Schema.read (br);
                if (schema.type.startsWith ("Clip")) schema.type = schema.type.substring (4);
                if (! safeTypes.contains (schema.type))
                {
                    br.close ();
                    return false;
                }

                MNode nodes = new MVolatile ();
                schema.read (nodes, br);
                br.close ();

                // Process into a suitable string
                for (MNode n : nodes)
                {
                    String key   = n.key ();
                    String value = n.get ();
                    if      (key.startsWith ("@")) data = value + key;
                    else if (value.isEmpty ())     data = key;
                    else                           data = key + "=" + value;
                    break;  // Only process the first node
                }
            }

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
