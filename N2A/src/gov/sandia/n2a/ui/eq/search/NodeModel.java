/*
Copyright 2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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
import gov.sandia.n2a.db.MDir;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.Schema;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.eq.EquationTreeCellRenderer;
import gov.sandia.n2a.ui.eq.TransferableNode;
import gov.sandia.n2a.ui.eq.undo.ChangeDoc;
import gov.sandia.n2a.ui.eq.undo.DeleteDoc;
import gov.sandia.n2a.ui.images.ImageUtil;

@SuppressWarnings("serial")
public class NodeModel extends NodeBase
{
    public           String    key;
    protected static ImageIcon icon = ImageUtil.getImage ("document.png");

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
        return icon;
    }

    @Override
    public Color getColor (boolean selected)
    {
        if (allowEdit ()) return super.getColor (selected);

        Color result = null;
        String colorName = "";
        MNode repo = null;
        MNode mdir = AppData.models.containerFor (key);
        if (mdir != null) repo = AppData.repos.child (mdir.key ());  // This can return null if multirepo structure changes and this panel is repainted before the change notification arrives.
        if (repo != null) colorName = repo.get ("color");
        if (! colorName.isEmpty ())
        {
            try
            {
                result = Color.decode (colorName);
                if (result.equals (Color.black)) result = null;  // Treat black as always default. Thus, the user can't explicitly set black, but they can set extremely dark (R=G=B=1).
            }
            catch (NumberFormatException e) {}
        }
        if (result != null) return result;

        if (selected) return EquationTreeCellRenderer.colorSelectedInherit;
        return               EquationTreeCellRenderer.colorInherit;
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
        MNode doc = AppData.models.child (key);
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
        return AppData.models.isWriteable (key);
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
        MNode models = AppData.models;
        MNode existingDocument = models.child (input);
        while (existingDocument != null)
        {
            suffix++;
            input = stem + " " + suffix;
            existingDocument = models.child (input);
        }

        MainFrame.instance.undoManager.apply (new ChangeDoc (key, input));
    }

    public void delete (JTree tree, boolean cancelled)
    {
        MainFrame.instance.undoManager.apply (new DeleteDoc ((MDoc) AppData.models.child (key)));
    }
}
