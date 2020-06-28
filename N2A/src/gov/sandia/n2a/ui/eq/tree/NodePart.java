/*
Copyright 2016-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.tree;

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Image;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
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
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.UndoManager;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.GraphNode;
import gov.sandia.n2a.ui.eq.PanelEquationTree;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.undo.AddAnnotation;
import gov.sandia.n2a.ui.eq.undo.AddPart;
import gov.sandia.n2a.ui.eq.undo.AddReference;
import gov.sandia.n2a.ui.eq.undo.AddVariable;
import gov.sandia.n2a.ui.eq.undo.ChangeAnnotations;
import gov.sandia.n2a.ui.eq.undo.DeleteDoc;
import gov.sandia.n2a.ui.eq.undo.DeletePart;
import gov.sandia.n2a.ui.eq.undo.ChangeDoc;
import gov.sandia.n2a.ui.eq.undo.ChangePart;
import gov.sandia.n2a.ui.eq.undo.ChangeReferences;
import gov.sandia.n2a.ui.eq.undo.ChangeVariable;
import gov.sandia.n2a.ui.images.ImageUtil;

import javax.imageio.ImageIO;
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
    public           ImageIcon iconCustom;   // User-supplied icon for this part. If present, it is used in node titles.
    public           ImageIcon iconCustom16; // Version of iconCustom that is no larger than 16x16, used for tree rendering.

    protected String                      inheritName = "";
    protected Set<String>                 ancestors;           // All parts we inherit from, whether directly or indirectly. Used to propose connections.
    public    Map<String,NodePart>        connectionBindings;  // non-null if this is a connection
    public    Set<NodePart>               transitConnections;  // connections which pass up through this node to a peer node to begin descent
    protected List<UnsatisfiedConnection> unsatisfiedConnections;
    protected boolean                     connectionTarget;    // Some other part connects to us.
    public    GraphNode                   graph;
    public    PanelEquationTree           pet;                 // If non-null, this part is the root of a currently-displayed tree. If null, then no tree operations are necessary.
    protected NodePart                    trueParent;
    public    boolean                     hide;                // visible() should return false. Used to temporarily suppress node when adding to graph. Allows us to avoid tampering with "parent" field.
    public    MNode                       pinOut;              // Deep copy of gui.pin.out. Null if no out pins.
    public    MNode                       pinIn;               // ditto for in pins
    public    List<MNode>                 pinOutOrder;         // Maps from position down right side to associated pin info. Null if no out pins.
    public    List<MNode>                 pinInOrder;          // ditto for in pins

    public NodePart (MPart source)
    {
        this.source = source;
    }

    protected NodePart ()
    {
    }

    @Override
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

    /**
        Extracts icon from $metadata.gui.icon, if it exists.
        Otherwise, iconCustom is left null and standard icons are used.
    **/
    public void setIcon ()
    {
        iconCustom   = null;
        iconCustom16 = null;
        String base64 = source.get ("$metadata", "gui", "icon");
        if (base64.isEmpty ()) return;

        byte[] bytes = Base64.getDecoder ().decode (base64);
        try
        {
            BufferedImage image = ImageIO.read (new ByteArrayInputStream (bytes));
            iconCustom = new ImageIcon (image);

            // Create scaled instance, if needed.
            double w = image.getWidth ();
            double h = image.getHeight ();
            if (w > 16)
            {
                h *= 16 / w;
                w  = 16;
            }
            if (h > 16)
            {
                w *= 16 / h;
                h  = 16;
            }
            int width  = (int) Math.round (w);
            int height = (int) Math.round (h);
            iconCustom16 = new ImageIcon (image.getScaledInstance (width, height, Image.SCALE_SMOOTH));
        }
        catch (IOException e) {}
    }

    @Override
    public void build ()
    {
        setUserObject ();
        setIcon ();
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
    public boolean visible ()
    {
        // Under "parent" graph node, don't display child parts, as these will have separate graph nodes.
        if (hide  ||  parent == null) return false;

        if (isRevoked ()) return FilteredTreeModel.showRevoked;  // TODO: implement a way to revoke parts
        if (FilteredTreeModel.showParam) return true;  // Even if we don't contain parameters.
        if (isLocal ())   return FilteredTreeModel.showLocal;
        else              return FilteredTreeModel.showInherited;
    }

    @Override
    public Icon getIcon (boolean expanded)
    {
        if (iconCustom16 != null) return iconCustom16;
        if (connectionBindings == null) return iconCompartment;
        else                            return iconConnection;
    }

    @Override
    public List<String> getColumns (boolean selected, boolean expanded)
    {
        List<String> result = new ArrayList<String> (1);
        String key = source.key ();
        if (expanded  ||  inheritName.isEmpty ()) result.add ("<html><b>" + key + "</b></html>");
        else if (graph == null)                   result.add ("<html><b>" + key + "</b>  <i>(" + inheritName + ")</i></html>");
        else                                      result.add ("<html><b>" + key + "</b><br/><i>" + inheritName + "</i></html>");
        return result;
    }

    @Override
    public Font getPlainFont (Font base)
    {
        return base.deriveFont (Font.PLAIN);
    }

    @Override
    public Font getStyledFont (Font base)
    {
        return base.deriveFont (Font.BOLD);
    }

    /**
        Examines a fully-built tree to determine the value of the isConnection member.
    **/
    public void findConnections ()
    {
        connectionBindings = null;
        unsatisfiedConnections = null;
        if (children == null) return;
        for (Object o : children) if (o instanceof NodeVariable) ((NodeVariable) o).findConnections ();  // Checks if variable is a connection binding. If so, sets isBinding on the variable and also sets our connectionBindings member.
        for (Object o : children) if (o instanceof NodePart)     ((NodePart)     o).findConnections ();  // Recurses down to sub-parts, so everything gets examined.
    }

    /**
        If one of our child variables updates its connection-binding state, then update all child parts as well.
        This locates inner connections that pass through the connection binding.
    **/
    public void updateConnections ()
    {
        for (Object o : children) if (o instanceof NodePart) ((NodePart) o).findConnections ();
    }

    /**
        Walks to part hierarchy while unpacking the given name path.
        Subroutine of NodeVariable.findConnections()
        @param from The NodeVariable that originated this search.
        @param sibling If this call came from child part, then reference to that part. Otherwise null.
        @return The tree node, or null if the path does not resolve exactly.
    **/
    public NodeBase resolveName (NodeVariable from, NodePart upFrom, String name)
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
            return parent.resolveName (from, this, nextName);
        }

        Enumeration<?> i = children ();
        while (i.hasMoreElements ())
        {
            NodeBase n = (NodeBase) i.nextElement ();
            if (! n.source.key ().equals (ns)) continue;
            if (n instanceof NodeVariable)
            {
                if (((NodeVariable) n).isBinding  &&  connectionBindings != null)
                {
                    NodePart p = connectionBindings.get (ns);
                    if (p != null) return p.resolveName (from, null, nextName);  // Tunnel through connection binding.
                }
                if (nextName.isEmpty ()) return n;  // fully resolved
                return null;  // failed to resolve
            }
            else if (n instanceof NodePart)
            {
                // Descend into child part.
                if (upFrom != null  &&  upFrom != from.getParent ())
                {
                    if (upFrom.transitConnections == null) upFrom.transitConnections = new HashSet<NodePart> ();
                    // If there are multiple climbing connections to the same target, one will overwrite the others.
                    upFrom.transitConnections.add ((NodePart) n);
                }
                return ((NodePart) n).resolveName (from, null, nextName);
            }
        }
        // Treat undefined $variables as local. This will not include $up, because that case is eliminated above.
        if (nextName.isEmpty ()  &&  ns.startsWith ("$")) return new NodeVariable (null);  // Fake it, just enough to finish NodeVariable.findConnections().

        if (parent == null) return null;
        return parent.resolveName (from, this, name);
    }

    public Set<String> getAncestors ()
    {
        if (ancestors != null) return ancestors;
        ancestors = new HashSet<String> ();
        EquationSet.collectAncestors (source, ancestors);
        return ancestors;
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
                p = p.trim ().replace ("\"", "");
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
        Dimension viewSize = PanelModel.instance.panelEquations.panelEquationGraph.getExtentSize ();
        double limit = viewSize.width + viewSize.height;  // Anything beyond this is too far out of sight to allow automatic connection.
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
                    for (Entry<Double,NodePart> e : sorted.entrySet ())
                    {
                        double distance = e.getKey ();
                        if (distance < Double.MAX_VALUE  &&  distance > limit) continue;
                        u.candidates.add (0, e.getValue ());
                    }
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

            // If all endpoints go to nodes that are already connected, then don't connect at all.
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
                MainFrame.instance.undoManager.apply (new ChangeVariable (v, u.alias, toPart.source.key ()));
                toPart.connectionTarget = true;
            }
        }
    }

    /**
        Determines the pins in each child part, then the pins in this part. This call
        recurses down the tree to every leaf.
    **/
    public void findPins ()
    {
        if (children != null)
        {
            for (Object o : children) if ((o instanceof NodePart)) ((NodePart) o).findPins ();
        }
        analyzePins ();
    }

    /**
        Determines the pins in this part, assuming that children are current, then notifies
        container to update itself. This call ripples all the way up to true root.
    **/
    public void updatePins ()
    {
        analyzePins ();
        NodePart p = getTrueParent ();
        if (p != null) p.updatePins ();
    }

    /**
        Convenience method that does all pin processing needed after build() is called for
        incremental maintenance of a tree.
    **/
    public void rebuildPins ()
    {
        findPins ();
        NodePart p = getTrueParent ();
        if (p != null) p.updatePins ();
    }

    /**
        Determines the set of pins exposed by this part, assuming that child parts are already analyzed.
        Exposed pins are specifically those displayed down the side of a part as an external interface.
        A part that flags itself as an exposed population or connection does not generally display a pin on its side.
        That is, an inner part exposed as a pin is not the same thing as a part that exposes such a pin. The part that
        exposes a pin is usually a container with several sub-populations, some of which are tagged to be exposed.
        findConnections() must be called first, as we use the results to decide if a given tagged connection
        has an unbound endpoint that can be exposed.
    **/
    public void analyzePins ()
    {
        pinIn       = new MVolatile ();
        pinOut      = new MVolatile ();
        pinInOrder  = null;
        pinOutOrder = null;

        if (children != null)
        {
            // Forwarded outputs. These take lowest precedence, so process them first.
            MNode out = source.child ("$metadata", "gui", "pin", "out");
            if (out != null)
            {
                for (MNode op : out)
                {
                    String partName = op.get ("bind");  // Name of inner part whose pin gets forwarded as an output.
                    if (partName.isEmpty ()) continue;
                    String bindPin = op.get ("bind", "pin");
                    if (bindPin.isEmpty ()) continue;
                    NodeBase o = child (partName);
                    if (! (o instanceof NodePart)) continue;

                    NodePart n = (NodePart) o;
                    MNode pin = n.source.child ("$metadata", "gui", "pin", "out", bindPin);
                    if (pin == null) continue;
                    String pinName = op.key ();  // The pin name is determined by our out list, not the inner part's out list.

                    pinOut.set ("", pinName, "part", partName);  // List this part as a subscriber to the pin.
                    // Forward a limited subset of pin attributes.
                    if (pin.data ("notes")) pinOut.set (pin.get ("notes"), pinName, "notes");
                    if (pin.data ("color")) pinOut.set (pin.get ("color"), pinName, "color");
                }
            }

            // Collect exposures.
            for (Object o : children)
            {
                if (! (o instanceof NodePart)) continue;
                NodePart n = (NodePart) o;
                String partName = n.source.key ();

                // Forwarded inputs. These also take lowest precedence, and do not overlap with forwarded outputs.
                if (n.pinIn != null)
                {
                    for (MNode pin : n.pinIn)
                    {
                        String bind = pin.get ("bind");
                        if (! bind.isEmpty ()) continue;  // A blank value indicates a forwarded input. Skip everything else.
                        String pinName = pin.get ("bind", "pin");
                        if (pinName.isEmpty ()) continue;
                        pinIn.set ("", pinName, "part", partName);
                        if (pin.data ("notes")) pinIn.set (pin.get ("notes"), pinName, "notes");
                        if (pin.data ("color")) pinIn.set (pin.get ("color"), pinName, "color");

                        // Forwarding is a kind of binding. This forwarded input could be a regular pin
                        // or a specific copy of an auto pin. It can't be the auto pin itself.

                        // In the other direction, if an inner part forwards its input to an outer pin that is marked auto,
                        // then the inner part will be duplicated, just like a connection part would be.
                    }
                }

                // Regular parts
                MNode pin = n.source.child ("$metadata", "gui", "pin");
                if (pin != null  &&  (! pin.get ().isEmpty ()  ||  pin.child ("in") == null  &&  pin.child ("out") == null))  // pin must be explicit, or both "in" and "out" must be absent.
                {
                    MNode side = null;
                    MNode pass = null;
                    if (n.connectionBindings == null)  // compartment
                    {
                        side = pinOut;
                    }
                    else if (n.connectionBindings.values ().contains (null))  // connection with unbound endpoint
                    {
                        side = pinIn;
                        pass = pin.child ("pass");
                    }
                    if (side != null)
                    {
                        String pinName = pin.getOrDefault (partName);
                        side.set ("", pinName, "part", partName);

                        // Pass-through pins
                        if (side == pinIn  &&  pass != null)
                        {
                            pinName = pass.getOrDefault (pinName);  // Using input pinName as default for output pinName.
                            pinOut.set ("", pinName, "part", partName);
                        }
                    }
                }
            }
        }

        // Post-process pins
        //   Apply overrides from our own pin metadata.
        MNode in = source.child ("$metadata", "gui", "pin", "in");
        if (in != null) pinIn.merge (in);
        //   Establish order down side of part.
        if (pinIn.size () == 0)
        {
            pinIn = null;
        }
        else
        {
            pinInOrder   = new ArrayList<MNode> ();
            MNode sorted = new MVolatile ();
            for (MNode c : pinIn)
            {
                String name = c.key ();
                String order = c.getOrDefault (name, "order");
                sorted.set (name, order);
            }
            for (MNode c : sorted)
            {
                String name = c.get ();
                pinIn.set (pinInOrder.size (), name, "order");
                pinInOrder.add (pinIn.child (name));
            }
        }
        //   Ditto for output pins
        MNode out = source.child ("$metadata", "gui", "pin", "out");
        if (out != null) pinOut.merge (out);
        if (pinOut.size () == 0)
        {
            pinOut = null;
        }
        else
        {
            pinOutOrder  = new ArrayList<MNode> ();
            MNode sorted = new MVolatile ();
            for (MNode c : pinOut)
            {
                String name = c.key ();
                String order = c.getOrDefault (name, "order");
                sorted.set (name, order);
            }
            for (MNode c : sorted)
            {
                String name = c.get ();
                pinOut.set (pinOutOrder.size (), name, "order");
                pinOutOrder.add (pinOut.child (name));
            }
        }
    }

    @Override
    public NodeBase containerFor (String type)
    {
        return this;
    }

    @Override
    public Undoable makeAdd (String type, JTree tree, MNode data, Point location)
    {
        if (tree == null)
        {
            // The only thing we can add is a part in the current graph view.
            if (! type.equals ("Part")) return null;
        }
        else
        {
            boolean collapsed   = tree.isCollapsed (new TreePath (getPath ()));
            boolean hasChildren = ((FilteredTreeModel) tree.getModel ()).getChildCount (this) > 0;
            if (collapsed  &&  hasChildren)  // The node is deliberately closed to indicate user intent.
            {
                if (type.isEmpty ()) type = "Part";
                return ((NodePart) parent).makeAdd (type, tree, data, location);
            }
            // else this is an open node, so anything can be inserted under it.
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

        if (tree != null)
        {
            TreePath path = tree.getLeadSelectionPath ();
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
        }

        if (type.equals ("Annotation"))
        {
            return new AddAnnotation (this, metadataIndex, data);  // will automagically insert a $metadata block if needed
        }
        else if (type.equals ("Annotations"))
        {
            return new ChangeAnnotations (this, data);
        }
        else if (type.equals ("Reference"))
        {
            return new AddReference (this, metadataIndex, data);
        }
        else if (type.equals ("References"))
        {
            return new ChangeReferences (this, data);
        }
        else if (type.equals ("Part"))
        {
            return new AddPart (this, subpartIndex, data, location);
        }
        else  // treat all other requests as "Variable"
        {
            if (data != null  &&  type.equals ("Equation"))
            {
                data = new MVolatile (data.get () + data.key (), "");  // convert equation into nameless variable
            }
            return new AddVariable (this, variableIndex, data);
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

        UndoManager um = MainFrame.instance.undoManager;
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

            um.apply (new ChangeDoc (oldKey, name));
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
                    boolean canceled = um.getPresentationName ().equals ("AddPart");
                    delete (canceled);
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

        um.apply (new ChangePart (this, oldKey, name));
    }

    public void revert (FilteredTreeModel model)
    {
        setUserObject ();
        model.nodeChanged (this);
        if (graph != null) graph.updateTitle ();
    }

    @Override
    public Undoable makeDelete (boolean canceled)
    {
        // Graph nodes are handled by GraphNode.TitleRenderer. A delete on the title of a graph node never reaches here.
        if (! source.isFromTopDocument ()) return null;
        if (isTrueRoot ()) return new DeleteDoc ((MDoc) source.getSource ());  // Root will always be from top document.
        return new DeletePart (this, canceled);
    }

    @Override
    public PanelEquationTree getTree ()
    {
        if (pet != null) return pet;
        if (parent == null) return null;  // True root, or no tree operations required. If this were instead a fake root that needs tree operations, then pet would be non-null.
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
