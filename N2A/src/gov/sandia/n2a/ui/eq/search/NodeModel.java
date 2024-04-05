/*
Copyright 2020-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.search;

import java.awt.Color;
import java.io.IOException;
import java.io.StringWriter;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeModel;
import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MCombo;
import gov.sandia.n2a.db.MDir;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.Schema;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.Utility;
import gov.sandia.n2a.ui.eq.EquationTreeCellRenderer;
import gov.sandia.n2a.ui.eq.TransferableNode;
import gov.sandia.n2a.ui.eq.undo.ChangeDoc;
import gov.sandia.n2a.ui.eq.undo.DeleteDoc;
import gov.sandia.n2a.ui.images.ImageUtil;

@SuppressWarnings("serial")
public class NodeModel extends NodeBase
{
    public           String    key;
    protected static ImageIcon overlayLocked      = ImageUtil.getImage ("locked-16.png");
    protected static ImageIcon iconDocument       = ImageUtil.getImage ("file_obj.gif");
    protected static ImageIcon iconDocumentLocked = Utility.overlay (overlayLocked, iconDocument);
    // TODO: Find a way to load custom icons without loading entire DB into memory. Perhaps build an icon cache on a background thread (in PanelSearch).

    public NodeModel (String key)
    {
        setUserObject (key);
        this.key = key;  // key is redundant with user object, except while editing
    }

    @Override
    public boolean isLeaf ()
    {
        return true;
    }

    @Override
    public Icon getIcon (boolean expanded)
    {
        if (allowEdit ()) return iconDocument;
        return iconDocumentLocked;
    }

    @Override
    public Color getColor (boolean selected)
    {
        String repoName = "";
        MNode mdir = ((MCombo) AppData.docs.child ("models")).containerFor (key);
        if (mdir != null) repoName = mdir.key ();
        Color fg = selected ? EquationTreeCellRenderer.colorSelectedOverride : EquationTreeCellRenderer.colorOverride;
        return getColor (repoName, fg);
    }

    public static Color getColor (String repoName, Color fg)
    {
        Color c = null;
        try {c = Color.decode (AppData.repos.get (repoName, "color"));}
        catch (NumberFormatException e) {}
        if (c != null)
        {
            float[] hsl = Utility.HSLfromColor (fg);
            boolean lightLF = hsl[2] > EquationTreeCellRenderer.lightThreshold;
            hsl = Utility.HSLfromColor (c);
            boolean lightUser = hsl[2] > EquationTreeCellRenderer.lightThreshold;

            if (lightUser == lightLF) return c;
            hsl[2] = 1 - hsl[2];
            return Utility.HSLtoColor (hsl);
        }
        return fg;
    }

    @Override
    public String getCategory ()
    {
        return ((NodeBase) parent).getCategory ();
    }

    public NodeModel findModel (String key)
    {
        if (this.key.equals (key)) return this;
        return null;
    }

    public boolean purge (String key, DefaultTreeModel model)
    {
        return this.key.equals (key);
    }

    public void replaceDoc (String oldKey, String newKey, DefaultTreeModel model)
    {
        if (! oldKey.equals (key)) return;
        key = newKey;
        setUserObject (newKey);
        model.nodeChanged (this);
    }

    public TransferableNode createTransferable ()
    {
        MNode doc = AppData.docs.child ("models", key);
        Schema schema = Schema.latest ();
        schema.type = "Part";
        StringWriter writer = new StringWriter ();
        try
        {
            schema.write (writer);
            writer.write (key + String.format ("%n"));
            for (MNode c : doc) schema.write (c, writer, " ");
            writer.close ();
            return new TransferableNode (writer.toString (), null, false, key);
        }
        catch (IOException e)
        {
        }

        return null;
    }

    public boolean allowEdit ()
    {
        return ((MCombo) AppData.docs.child ("models")).isWritable (key);
    }

    public void applyEdit (JTree tree)
    {
        // Similar to NodePart.applyEdit()

        String input = (String) getUserObject ();
        if (input.equals (key)) return;

        DefaultTreeModel model = (DefaultTreeModel) tree.getModel ();
        if (input.isEmpty ())
        {
            setUserObject (key);  // restore original value
            model.nodeChanged (this);
            return;
        }

        // Make name valid
        input = MDir.validFilenameFrom (input);
        input = input.replace (",", "-");

        // Make name unique
        String stem = input;
        int suffix = 0;
        MNode models = AppData.docs.child ("models");
        MNode existingDocument = models.child (input);
        while (existingDocument != null)
        {
            suffix++;
            input = stem + " " + suffix;
            existingDocument = models.child (input);
        }

        MainFrame.undoManager.apply (new ChangeDoc (key, input));
    }

    public void delete (JTree tree, boolean cancelled)
    {
        MainFrame.undoManager.apply (new DeleteDoc ((MDoc) AppData.docs.child ("models", key)));
    }
}
