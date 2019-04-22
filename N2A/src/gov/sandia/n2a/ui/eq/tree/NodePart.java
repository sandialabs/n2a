/*
Copyright 2016-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


package gov.sandia.n2a.ui.eq.tree;

import java.awt.Font;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MDir;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.GraphNode;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.undo.AddAnnotation;
import gov.sandia.n2a.ui.eq.undo.AddInherit;
import gov.sandia.n2a.ui.eq.undo.AddPart;
import gov.sandia.n2a.ui.eq.undo.AddReference;
import gov.sandia.n2a.ui.eq.undo.AddVariable;
import gov.sandia.n2a.ui.eq.undo.ChangeInherit;
import gov.sandia.n2a.ui.eq.undo.DeleteDoc;
import gov.sandia.n2a.ui.eq.undo.DeletePart;
import gov.sandia.n2a.ui.eq.undo.ChangeDoc;
import gov.sandia.n2a.ui.eq.undo.ChangePart;
import gov.sandia.n2a.ui.images.ImageUtil;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

@SuppressWarnings("serial")
public class NodePart extends NodeContainer
{
    protected static ImageIcon iconCompartment = ImageUtil.getImage ("comp.gif");
    protected static ImageIcon iconConnection  = ImageUtil.getImage ("connection.png");

    protected String               inheritName = "";
    public    Map<String,NodePart> connectionBindings;  // non-null if this is a connection
    public    GraphNode            graph;

    public NodePart ()
    {
    }

    public NodePart (MPart source)
    {
        this.source = source;
    }

    public void setUserObject ()
    {
        setUserObject (source.key ());  // This won't actually be used in editing, but it does prevent editingCancelled() from getting a null object.

        inheritName = "";
        if (! isRoot ())
        {
            MNode inherit = source.child ("$inherit");
            if (inherit != null) inheritName = inherit.get ().split (",", 2)[0].replace ("\"", "");
        }
    }

    @Override
    public void build ()
    {
        setUserObject ();
        removeAllChildren ();

        String order = source.get ("$metadata", "gui", "order");
        Set<String> sorted = new HashSet<String> ();
        String[] subkeys = order.split (",");  // comma-separated list
        for (String k : subkeys)
        {
            MNode c = source.child (k);
            if (c != null)
            {
                buildTriage ((MPart) c);
                sorted.add (k);
            }
        }
        // Build everything else. Sort all subparts to the end.
        ArrayList<MNode> subparts = new ArrayList<MNode> ();
        for (MNode c : source)
        {
            if (sorted.contains (c.key ())) continue;
            if (MPart.isPart (c)) subparts.add (c);
            else                  buildTriage ((MPart) c);
        }
        for (MNode c : subparts) buildTriage ((MPart) c);
    }

    public void buildTriage (MPart line)
    {
        String key = line.key ();
        if (key.equals ("$inherit"))
        {
            NodeInherit i = new NodeInherit (line);
            add (i);
            return;
        }
        if (key.equals ("$metadata"))
        {
            NodeAnnotations a = new NodeAnnotations (line);
            add (a);
            a.build ();
            return;
        }
        if (key.equals ("$reference"))
        {
            NodeReferences r = new NodeReferences (line);
            add (r);
            r.build ();
            return;
        }

        if (line.isPart ())
        {
            NodePart p = new NodePart (line);
            add (p);
            p.build ();
            return;
        }

        NodeVariable v = new NodeVariable (line);
        add (v);
        v.build ();
        // Note: connection bindings will be marked later, after full tree is assembled.
        // This allows us to take advantage of the work done to identify sub-parts.
    }

    @Override
    public boolean visible (int filterLevel)
    {
        if (filterLevel >= FilteredTreeModel.LOCAL) return source.isFromTopDocument ();
        return true;  // Almost always visible, except for most stringent filter mode.
    }

    @Override
    public Icon getIcon (boolean expanded)
    {
        if (connectionBindings == null) return iconCompartment;
        else                            return iconConnection;
    }

    @Override
    public String getText (boolean expanded, boolean editing)
    {
        String key = toString ();  // This allows us to set editing text to "" for new objects, while showing key for old objects.
        if (expanded  ||  editing  ||  inheritName.isEmpty ()) return key;
        return key + "  (" + inheritName + ")";
    }

    @Override
    public float getFontScale ()
    {
        if (isRoot ()) return 2f;
        return 1;
    }

    @Override
    public int getFontStyle ()
    {
        return Font.BOLD;
    }

    /**
        Examines a fully-built tree to determine the value of the isConnection member.
    **/
    public void findConnections ()
    {
        connectionBindings = null;
        Enumeration<?> i = children ();
        while (i.hasMoreElements ())
        {
            Object o = i.nextElement ();
            if      (o instanceof NodePart)     ((NodePart)     o).findConnections ();  // Recurses down to sub-parts, so everything gets examined.
            else if (o instanceof NodeVariable) ((NodeVariable) o).findConnections ();  // Checks if variable is a connection binding. If so, sets isBinding on the variable and also sets our isConnection member.
        }
    }

    public NodeBase resolveName (String name)
    {
        if (name.isEmpty ()) return this;
        String[] pieces = name.split ("\\.", 2);
        String ns = pieces[0];
        String nextName;
        if (pieces.length > 1) nextName = pieces[1];
        else                   nextName = "";

        NodePart parent = (NodePart) getParent ();
        if (ns.equals ("$up"))  // Don't bother with local checks if we know we are going up
        {
            if (parent == null) return null;
            return parent.resolveName (nextName);
        }

        Enumeration<?> i = children ();
        while (i.hasMoreElements ())
        {
            Object o = i.nextElement ();
            if (o instanceof NodeVariable)
            {
                NodeVariable v = (NodeVariable) o;
                if (v.source.key ().equals (ns)) return v;
            }
            else if (o instanceof NodePart)
            {
                NodePart p = (NodePart) o;
                if (p.source.key ().equals (ns)) return p.resolveName (nextName);
            }
        }

        if (parent == null) return null;
        return parent.resolveName (name);
    }

    public NodeBase add (String type, JTree tree, MNode data)
    {
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();
        if (tree.isCollapsed (new TreePath (getPath ()))  &&  model.getChildCount (this) > 0  &&  ! isRoot ())  // The node is deliberately closed to indicate user intent.
        {
            // The only thing that can contain a NodePart is another NodePart. (If that ever changes, the following code will break.)
            if (type.isEmpty ()) return ((NodePart) getParent ()).add ("Part", tree, data);
            return ((NodePart) getParent ()).add (type, tree, data);
        }

        int variableIndex = -1;
        int subpartIndex  = -1;
        int metadataIndex = 0;
        int count = getChildCount ();  // unfiltered, so we can insert at the correct place in the underlying collection
        for (int i = 0; i < count; i++)
        {
            TreeNode t = getChildAt (i);
            if (t instanceof NodeInherit)
            {
                metadataIndex = i + 1;
            }
            if (t instanceof NodePart)
            {
                if (variableIndex < 0) variableIndex = i;
                subpartIndex = i + 1;
            }
        }
        if (variableIndex < 0) variableIndex = count;
        if (subpartIndex  < 0) subpartIndex  = count;

        TreePath path = tree.getSelectionPath ();
        if (path != null)
        {
            NodeBase selected = (NodeBase) path.getLastPathComponent ();
            if (selected.getParent () == this)
            {
                // When we have a specific item selected, the user expects the new item to appear directly below it.
                int selectedIndex = getIndex (selected);  // unfiltered
                variableIndex = selectedIndex + 1;
                subpartIndex  = selectedIndex + 1;
            }
        }

        if (type.equals ("Annotation"))
        {
            AddAnnotation aa = new AddAnnotation (this, metadataIndex, data);
            PanelModel.instance.undoManager.add (aa);  // aa will automagically insert a $metadata block if needed
            return aa.createdNode;
        }
        else if (type.equals ("Reference"))
        {
            AddReference ar = new AddReference (this, metadataIndex, data);
            PanelModel.instance.undoManager.add (ar);
            return ar.createdNode;
        }
        else if (type.equals ("Part"))
        {
            AddPart ap = new AddPart (this, subpartIndex, data);
            PanelModel.instance.undoManager.add (ap);
            return ap.createdNode;
        }
        else if (type.equals ("Inherit"))
        {
            Undoable un = null;
            NodeInherit inherit = (NodeInherit) child ("$inherit");
            String value = "";
            if (data != null) value = data.get ();
            if (inherit == null)
            {
                un = new AddInherit (this, value);
            }
            else if (! value.isEmpty ())
            {
                un = new ChangeInherit (inherit, value);
            }
            if (un != null) PanelModel.instance.undoManager.add (un);
            return child ("$inherit");
        }
        else  // treat all other requests as "Variable"
        {
            if (data != null  &&  type.equals ("Equation"))
            {
                data = new MVolatile ("", data.get () + data.key ());  // convert equation into nameless variable
            }
            AddVariable av = new AddVariable (this, variableIndex, data);
            PanelModel.instance.undoManager.add (av);
            return av.createdNode;
        }
    }

    public static String validIdentifierFrom (String name)
    {
        if (name.length () == 0) return "";

        StringBuilder result = new StringBuilder ();
        char c = name.charAt (0);
        if (Character.isJavaIdentifierStart (c)) result.append (c);
        else                                     result.append ('_');
        for (int i = 1; i < name.length (); i++)
        {
            c = name.charAt (i);
            if (Character.isJavaIdentifierPart (c)) result.append (c);
            else                                    result.append ('_');
        }
        return result.toString ();
    }

    public static boolean isIdentifierPath (String name)
    {
        if (name.length () == 0) return false;

        char c = name.charAt (0);
        if (! Character.isJavaIdentifierStart (c)) return false;

        for (int i = 1; i < name.length (); i++)
        {
            c = name.charAt (i);
            if (! Character.isJavaIdentifierPart (c)  &&  c != '.') return false;
        }

        return true;
    }

    @Override
    public void applyEdit (JTree tree)
    {
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();

        String input = (String) getUserObject ();
        String[] pieces = input.split ("=", 2);
        String name = pieces[0].trim ();
        String oldKey = source.key ();
        if (name.equals (oldKey))
        {
            setUserObject ();
            model.nodeChanged (this);
            return;
        }

        PanelModel mep = PanelModel.instance;
        if (isRoot ())  // Edits to root cause a rename of the document on disk
        {
            if (name.isEmpty ())
            {
                setUserObject ();
                model.nodeChanged (this);
                return;
            }

            name = MDir.validFilenameFrom (name);
            name = name.replace (",", "-");  // In addition to filename constraints, we also forbid comma, because these names are used in list expressions.

            String stem = name;
            int suffix = 0;
            MNode models = AppData.models;
            MNode existingDocument = models.child (name);
            while (existingDocument != null)
            {
                suffix++;
                name = stem + " " + suffix;
                existingDocument = models.child (name);
            }

            mep.undoManager.add (new ChangeDoc (oldKey, name));
            // MDir promises to maintain object identity during the move, so "source" is still valid.
            return;
        }

        if (input.isEmpty ())
        {
            delete (tree, true);
            return;
        }

        name = validIdentifierFrom (name);

        NodeBase parent = (NodeBase) getParent ();
        NodeBase sibling = parent.child (name);
        if (sibling != null  &&  (sibling.source.isFromTopDocument ()  ||  ! (sibling instanceof NodePart)))  // the name already exists in top document, so reject rename
        {
            setUserObject ();
            model.nodeChanged (this);
            return;
        }

        mep.undoManager.add (new ChangePart (this, oldKey, name));
    }

    @Override
    public void delete (JTree tree, boolean canceled)
    {
        if (! source.isFromTopDocument ()) return;  // This should be true of root, as well as any other node we might try to delete.
        PanelModel mep = PanelModel.instance;
        if (isRoot ()) mep.undoManager.add (new DeleteDoc ((MDoc) source.getSource ()));
        else           mep.undoManager.add (new DeletePart (this, canceled));
    }
}
