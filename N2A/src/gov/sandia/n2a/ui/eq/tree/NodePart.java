/*
Copyright 2016-2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


package gov.sandia.n2a.ui.eq.tree;

import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MDir;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.GraphNode;
import gov.sandia.n2a.ui.eq.PanelEquationGraph.GraphPanel;
import gov.sandia.n2a.ui.eq.PanelEquationTree;
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
import gov.sandia.n2a.ui.eq.undo.ChangeVariable;
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

    protected String                      inheritName = "";
    protected Set<String>                 ancestors;           // All parts we inherit from, whether directly or indirectly. Used to propose connections.
    public    Map<String,NodePart>        connectionBindings;  // non-null if this is a connection
    protected List<UnsatisfiedConnection> unsatisfiedConnections;
    protected boolean                     connectionTarget;    // Some other part connects to us.
    public    GraphNode                   graph;
    public    PanelEquationTree           pet;                 // If this part is not bound to a graph node, it may be bound to a full-view tree. If not bound to either, then no tree operations are necessary.
    protected NodePart                    trueParent;
    public    boolean                     hide;                // visible() should return false. Used to temporarily suppress node when adding to graph.  Allows us to avoid tampering with "parent" field.

    public NodePart (MPart source)
    {
        this.source = source;
    }

    public void setUserObject ()
    {
        setUserObject (source.key ());  // This won't actually be used in editing, but it does prevent editingCancelled() from getting a null object.

        inheritName = "";
        if (! isTrueRoot ())
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
        ancestors = null;

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

    /**
        Makes this node a temporary root, for displaying sub-trees.
        This is also safe to call on the true root node. In that case, this has no effect.
        Subsequent calls should always alternate between activate and deactivate, or information will be lost.
        Only the true root can receive multiple calls with the same value of activate, since in that case this has no effect.
    **/
    public void fakeRoot (boolean activate)
    {
        if (activate)
        {
            trueParent = (NodePart) parent;
            parent = null;
        }
        else  // deactivate
        {
            parent = trueParent;
            trueParent = null;
        }
    }

    @Override
    public NodePart getTrueParent ()
    {
        if (parent == null) return trueParent;
        return (NodePart) parent;
    }

    public boolean isTrueRoot ()
    {
        return parent == null  &&  trueParent == null;
    }

    @Override
    public boolean visible (int filterLevel)
    {
        // Under "parent" graph node, don't display child parts, as these will have separate graph nodes.
        if (hide  ||  parent == null) return false;

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
        if (editing) return key;
        if (expanded  ||  inheritName.isEmpty ()) return "<html><b>" + key + "</b></html>";
        if (graph == null) return "<html><b>" + key + "</b>  <i>(" + inheritName + ")</i></html>";
        return "<html><b>" + key + "</b><br/><i>" + inheritName + "</i></html>";
    }

    @Override
    public Font getPlainFont (Font base)
    {
        float size = base.getSize2D ();
        if (PanelModel.instance.panelEquations.viewTree  &&  isTrueRoot ()) size *= 2;
        return base.deriveFont (Font.PLAIN, size);
    }

    @Override
    public Font getStyledFont (Font base)
    {
        float size = base.getSize2D ();
        if (PanelModel.instance.panelEquations.viewTree  &&  isTrueRoot ()) size *= 2;
        return base.deriveFont (Font.BOLD, size);
    }

    /**
        Examines a fully-built tree to determine the value of the isConnection member.
    **/
    public void findConnections ()
    {
        connectionBindings = null;
        unsatisfiedConnections = null;
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

        NodePart parent = (NodePart) getTrueParent ();
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

    public Set<String> getAncestors ()
    {
        if (ancestors != null) return ancestors;
        ancestors = new HashSet<String> ();
        findAncestors (source);
        return ancestors;
    }

    public void findAncestors (MNode child)
    {
        String inherit = child.get ("$inherit");
        if (inherit.isEmpty ()) return;
        String[] inherits = inherit.split (",");
        for (String i : inherits)
        {
            i = i.replace ("\"", "").trim ();
            if (ancestors.add (i))
            {
                MNode parent = AppData.models.child (i);
                if (parent != null) findAncestors (parent);
            }
        }
    }

    public List<UnsatisfiedConnection> getUnsatisfiedConnections ()
    {
        if (unsatisfiedConnections != null) return unsatisfiedConnections;
        unsatisfiedConnections = new ArrayList<UnsatisfiedConnection> ();
        if (connectionBindings == null) return unsatisfiedConnections;
        for (Entry<String,NodePart> e : connectionBindings.entrySet ())
        {
            if (e.getValue () != null) continue;
            String key = e.getKey ();
            String line = source.get (key);
            if (! Operator.containsConnect (line)) continue;
            UnsatisfiedConnection u = new UnsatisfiedConnection (key);
            line = line.trim ().split ("connect", 2)[1];
            line = line.replace ("(", "");
            line = line.replace (")", "");
            for (String p : line.split (","))
            {
                p = p.replace ("\"", "").trim ();
                u.classes.add (p);
            }
            if (u.classes.size () > 0) unsatisfiedConnections.add (u);
        }
        return unsatisfiedConnections;
    }
 
    /**
        Locates likely endpoints for unsatisfied connections.
        Subroutine of equation tree transfer handler. Should be called while a compound edit is open.
    **/
    public static void suggestConnections (List<NodePart> fromParts, List<NodePart> toParts)
    {
        // Set connectionTarget flags
        for (NodePart fromPart : toParts  ) fromPart.connectionTarget = false;
        for (NodePart toPart   : fromParts) toPart  .connectionTarget = false;
        for (NodePart toPart : toParts)
        {
            if (toPart.connectionBindings == null) continue;
            for (Entry<String,NodePart> e : toPart.connectionBindings.entrySet ())
            {
                NodePart p = e.getValue ();
                if (p != null) p.connectionTarget = true;
            }
        }
        for (NodePart fromPart : fromParts)
        {
            if (fromPart.connectionBindings == null) continue;
            for (Entry<String,NodePart> e : fromPart.connectionBindings.entrySet ())
            {
                NodePart p = e.getValue ();
                if (p != null) p.connectionTarget = true;
            }
        }

        // Scan for matches
        PanelModel pm = PanelModel.instance;
        for (NodePart fromPart : fromParts)
        {
            // Collect candidates for each unsatisfied connection.
            List<UnsatisfiedConnection> unsatisfied = fromPart.getUnsatisfiedConnections ();
            for (UnsatisfiedConnection u : unsatisfied)
            {
                u.candidates = new ArrayList<NodePart> ();
                for (NodePart toPart : toParts)
                {
                    // Is toPart a descendant of any of the classes acceptable to the connection?
                    boolean match = false;
                    Set<String> ancestors = toPart.getAncestors ();
                    for (String c : u.classes)
                    {
                        if (ancestors.contains (c))
                        {
                            match = true;
                            break;
                        }
                    }
                    if (match) u.candidates.add (toPart);
                }
            }

            // If distance is known, sort candidates in descending order.
            // That way, more distant candidates will be eliminated first.
            if (fromPart.graph != null)
            {
                Point fromPoint = fromPart.graph.getLocation ();
                Map<Double,NodePart> sorted = new TreeMap<Double,NodePart> ();
                for (UnsatisfiedConnection u : unsatisfied)
                {
                    for (NodePart toPart : u.candidates)
                    {
                        double distance = Double.MAX_VALUE;
                        if (toPart.graph != null)
                        {
                            Point toPoint = toPart.graph.getLocation ();
                            int dx = fromPoint.x - toPoint.x;
                            int dy = fromPoint.y - toPoint.y;
                            distance = Math.sqrt (dx * dx + dy * dy);
                        }
                        sorted.put (distance, toPart);
                    }
                    u.candidates.clear ();
                    for (NodePart p : sorted.values ()) u.candidates.add (0, p);
                }
            }

            // Narrow down lists of candidates to one best choice for each endpoint.
            // The complicated tests below are merely heuristics for better connection choices,
            // not absolute rules.
            for (UnsatisfiedConnection u : unsatisfied)
            {
                // Minimize connections to the same target.
                while (u.candidates.size () > 1)
                {
                    NodePart duplicate = null;
                    for (NodePart c : u.candidates)
                    {
                        // Check candidates for peer endpoints.
                        for (UnsatisfiedConnection v : unsatisfied)
                        {
                            if (v == u) continue;  // Don't check ourselves.
                            if (v.candidates.contains (c))
                            {
                                duplicate = c;
                                break;
                            }
                        }
                        if (duplicate != null) break;

                        // Also scan satisfied endpoints, if any.
                        for (Entry<String,NodePart> e : fromPart.connectionBindings.entrySet ())
                        {
                            if (e.getValue () == c)
                            {
                                duplicate = c;
                                break;
                            }
                        }
                        if (duplicate != null) break;
                    }
                    if (duplicate == null) break;
                    u.candidates.remove (duplicate);
                }

                // Minimize connections to a target that already receives connections.
                while (u.candidates.size () > 1)
                {
                    NodePart taken = null;
                    for (NodePart c : u.candidates)
                    {
                        if (c.connectionTarget)
                        {
                            taken = c;
                            break;
                        }
                    }
                    if (taken == null) break;
                    u.candidates.remove (taken);
                }
            }

            // If all endpoints go to nodes that area already connected, then don't connect at all.
            // (Instead, assume that user is started a new constellation, and simply inserted the connection object first.)
            boolean targetsFull = true;
            for (UnsatisfiedConnection u : unsatisfied)
            {
                if (u.candidates.size () != 1  ||  ! u.candidates.get (0).connectionTarget)
                {
                    targetsFull = false;
                    break;
                }
            }
            if (targetsFull) continue;

            // Assign the last candidate, as it should be sorted closest to fromNode.
            for (UnsatisfiedConnection u : unsatisfied)
            {
                if (u.candidates.size () == 0) continue;
                NodePart toPart = u.candidates.get (u.candidates.size () - 1);
                NodeVariable v = (NodeVariable) fromPart.child (u.alias);  // This must exist.
                pm.undoManager.add (new ChangeVariable (v, u.alias, toPart.source.key ()));
                toPart.connectionTarget = true;
            }
        }
    }

    public NodeBase add (String type, JTree tree, MNode data, Point location)
    {
        if (tree == null)
        {
            // The only thing we can add is a part in the current graph view.
            if (type.isEmpty ()) type = "Part";
            if (! type.equals ("Part")) return null;
        }
        else
        {
            boolean graphClosed =  graph != null  &&  ! graph.open;
            boolean collapsed   = tree.isCollapsed (new TreePath (getPath ()));
            boolean hasChildren = ((FilteredTreeModel) tree.getModel ()).getChildCount (this) > 0;
            if (graphClosed  ||  (collapsed  &&  hasChildren))  // The node is deliberately closed to indicate user intent.
            {
                if (type.isEmpty ()) type = "Part";
                if (graphClosed)
                {
                    tree = null;  // Create a peer graph node.
                    if (location == null)
                    {
                        GraphPanel gp = (GraphPanel) graph.getParent ();
                        location = graph.getLocation ();
                        location.x += 100 - gp.offset.x;
                        location.y += 100 - gp.offset.y;
                    }
                }
                return ((NodePart) getTrueParent ()).add (type, tree, data, location);
            }
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

        TreePath path = null;
        if (tree != null) path = tree.getSelectionPath ();
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
            AddPart ap = new AddPart (this, subpartIndex, data, location);
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
                data = new MVolatile (data.get () + data.key (), "");  // convert equation into nameless variable
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
            revert (model);
            return;
        }

        PanelModel mep = PanelModel.instance;
        if (isTrueRoot ())  // Edits to root cause a rename of the document on disk
        {
            if (name.isEmpty ())
            {
                revert (model);
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
            // A part may appear in the form of a graph node, rather than an entry in an equation tree.
            // In that case, delete during the current event can mess up focus control (due to sequence of other code).
            // Therefore, put the delete action onto the event queue. This is OK to do, even for tree editing.
            EventQueue.invokeLater (new Runnable ()
            {
                public void run ()
                {
                    boolean canceled = mep.undoManager.getPresentationName ().equals ("AddPart");
                    delete (tree, canceled);
                }
            });
            return;
        }

        name = validIdentifierFrom (name);

        NodeBase parent = getTrueParent ();
        NodeBase sibling = parent.child (name);
        if (sibling != null  &&  (sibling.source.isFromTopDocument ()  ||  ! (sibling instanceof NodePart)))  // the name already exists in top document, so reject rename
        {
            revert (model);
            return;
        }

        mep.undoManager.add (new ChangePart (this, oldKey, name));
    }

    public void revert (FilteredTreeModel model)
    {
        setUserObject ();
        model.nodeChanged (this);
        if (graph != null) graph.updateTitle ();
    }

    @Override
    public void delete (JTree tree, boolean canceled)
    {
        if (! source.isFromTopDocument ()) return;  // This should be true of root, as well as any other node we might try to delete.
        PanelModel mep = PanelModel.instance;
        if (isTrueRoot ()) mep.undoManager.add (new DeleteDoc ((MDoc) source.getSource ()));
        else               mep.undoManager.add (new DeletePart (this, canceled));
    }

    @Override
    public PanelEquationTree getTree ()
    {
        if (pet   != null) return pet;
        if (graph != null) return graph.panelEquations;  // Graph nodes don't set "pet", because their tree is single-use only, so they don't need to call loadPart().
        if (parent == null) return null;  // True root. If this were instead a fake root, then at least one of {pet, graph} would be non-null.
        return ((NodeBase) parent).getTree ();
    }

    public class UnsatisfiedConnection
    {
        String         alias;
        List<String>   classes = new ArrayList<String> ();  // Parts (or their children) that could satisfy this endpoint.
        List<NodePart> candidates;

        public UnsatisfiedConnection (String alias)
        {
            this.alias = alias;
        }
    }
}
