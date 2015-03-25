/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.eqset;

import gov.sandia.n2a.language.EvaluationContext;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.operator.AND;
import gov.sandia.n2a.language.operator.Add;
import gov.sandia.n2a.language.operator.Divide;
import gov.sandia.n2a.language.operator.Multiply;
import gov.sandia.n2a.language.operator.OR;
import gov.sandia.n2a.language.operator.Subtract;
import gov.sandia.n2a.language.parse.ASTConstant;
import gov.sandia.n2a.language.parse.ASTFunNode;
import gov.sandia.n2a.language.parse.ASTListNode;
import gov.sandia.n2a.language.parse.ASTMatrixNode;
import gov.sandia.n2a.language.parse.ASTNodeBase;
import gov.sandia.n2a.language.parse.ASTNodeRenderer;
import gov.sandia.n2a.language.parse.ASTNodeTransformer;
import gov.sandia.n2a.language.parse.ASTOpNode;
import gov.sandia.n2a.language.parse.ASTRenderingContext;
import gov.sandia.n2a.language.parse.ASTTransformationContext;
import gov.sandia.n2a.language.parse.ASTVarNode;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.ui.ensemble.domains.Parameter;
import gov.sandia.umf.platform.ui.ensemble.domains.ParameterDomain;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.swing.ImageIcon;

public class EquationSet implements Comparable<EquationSet>
{
    public NDoc                                source;
    public String                              name;
    public EquationSet                         container;
    public NavigableSet<Variable>              variables;
    public NavigableSet<EquationSet>           parts;
    public NavigableMap<String, EquationSet>   connectionBindings;     // non-null iff this is a connection
    public boolean                             connected;
    public NavigableSet<AccountableConnection> accountableConnections; // Connections which declare a $min or $max w.r.t. this part. Note: connected can be true even if accountableConnections is null.
    /** @deprecated Better to refer metadata requests to source. Part/NDoc should implement a getNamedValue() function that refers requests up the inheritance chain. **/
    public Map<String, String>                 metadata;
    public List<Variable>                      ordered;
    public List<ArrayList<EquationSet>>        splits;                 // Enumeration of the $type splits this part can go through
    public boolean                             lethalN;                // our population could shrink
    public boolean                             lethalP;                // we could have a non-zero probability of dying in some cycle 
    public boolean                             lethalType;             // we can be killed by a part split
    public boolean                             lethalConnection;       // indicate we are a connection, and one of the parts we connect can die
    public boolean                             lethalContainer;        // our parent could die
    public boolean                             referenced;             // Some other equation set writes to one of our variables. If we can die, then exercise care not to reuse this part while other parts are still writing to it. Otherwise our reincarnated part might get written with values from our previous life.
    public Object                              backendData;            // holder for extra data associated with each equation set by a given backend

    public class AccountableConnection implements Comparable<AccountableConnection>
    {
        public EquationSet connection; // the connection, that is, the thing being accounted (the endpoint is the thing doing the accounting)
        public String      alias;      // name within the connection that refers to the endpoint
        public AccountableConnection (EquationSet connection, String alias)
        {
            this.connection = connection;
            this.alias      = alias;
        }
        public int compareTo (AccountableConnection that)
        {
            int result = connection.compareTo (that.connection);
            if (result != 0) return result;
            return alias.compareTo (that.alias);
        }
    }

    public EquationSet (String name)
    {
        this.name = name;
    }

    public EquationSet (NDoc part) throws Exception
    {
        this ("", null, part);
    }

    public EquationSet (String name, EquationSet container, NDoc source) throws Exception
    {
        EquationSet c = container;
        while (c != null)
        {
            if (c.source == source)  // TODO: should this be based on Id's instead of object references?
            {
                throw new Exception ("Self-referential loop in part: " + source);
            }
            c = c.container;
        }

        this.name      = name;
        this.container = container;
        this.source    = source;
        variables      = new TreeSet<Variable> ();
        parts          = new TreeSet<EquationSet> ();
        metadata       = new HashMap<String, String> ();

        // TODO: Includes, Bridges and Layers should all be stored the same way in the DB.
        // They should all refer to a Part, and they should all have an alias.
        // "Bridges" have additional attributes to name the aliases of the parts they connect.

        // Includes
        List<NDoc> associations = source.getValid ("associations", new ArrayList<NDoc> (), List.class);
        for (NDoc a : associations)
        {
            if (((String) a.get ("type")).equalsIgnoreCase ("include"))
            {
                String aname = a.get ("name");  // TODO: default alias name should be assigned at creation time, not filled in here!
                if (aname == null)
                {
                    throw new Exception ("Need to set include name in DB");
                }
                NDoc dest = a.get ("dest");
                parts.add (new EquationSet (aname, this, dest));
            }
        }

        // Layers
        List<NDoc> layers = source.getValid ("layers", new ArrayList<NDoc> (), List.class);
        for (NDoc l : layers)
        {
            parts.add (new EquationSet ((String) l.get ("name"), this, (NDoc) l.get ("derivedPart")));
        }

        // Bridges
        List<NDoc> bridges = source.getValid ("bridges", new ArrayList<NDoc> (), List.class);
        for (NDoc b : bridges)
        {
            NDoc connection = b.get ("derivedPart");
            EquationSet s = new EquationSet ((String) b.get ("name"), this, connection);
            parts.add (s);

            // Configure the connection bindings in s (the "bridge" equation set)

            //   Collect "connect" associations from connection part. These indicate the alias and type
            //   of parts that get connected. We will match the types of parts that this "bridge"
            //   connects in order to infer which alias refers to each one.
            //   TODO: This approach is fragile in multiple ways, and should be changed ASAP.
            List<NDoc> connectionAssociations = new ArrayList<NDoc> ();
            NDoc parent = connection.get ("parent");
            associations = parent.getValid ("associations", new ArrayList<NDoc> (), List.class);
            for (NDoc a : associations)
            {
                if (((String) a.get ("type")).equalsIgnoreCase ("connect"))
                {
                    connectionAssociations.add (a);
                }
            }

            //   Scan the list of parts connected by this "bridge"
            List<NDoc> connected = b.getValid ("layers", new ArrayList<NDoc> (), List.class);
            for (NDoc l : connected)
            {
                // Retrieve the equation set associated with the connected part
                String lname = l.get ("name");
                EquationSet e = parts.floor (new EquationSet (lname));
                if (e == null  ||  ! e.name.equals (lname))
                {
                    // We should NEVER get here, since a "bridge" directly references layers which should already be added. 
                    throw new Exception ("Connection references a Part that does not exist.");
                }

                // Determine the alias by scanning associations of the connection part
                NDoc layerType = ((NDoc) l.get ("derivedPart")).get ("parent");
                for (NDoc a : connectionAssociations)
                {
                    NDoc connectionType = a.get ("dest");
                    if (layerType.getId ().equals (connectionType.getId ()))
                    {
                        // Stored the binding
                        if (s.connectionBindings == null)
                        {
                            s.connectionBindings = new TreeMap<String, EquationSet> ();
                        }
                        String aname = a.get ("name");
                        if (! s.connectionBindings.containsKey (aname))
                        {
                            s.connectionBindings.put (aname, e);
                        }
                    }
                }

                e.connected = true;
            }
        }

        // Local equations
        List<NDoc> eqs = source.getValid ("eqs", new ArrayList<NDoc> (), List.class);
        for (NDoc e : eqs)
        {
            EquationEntry ee = new EquationEntry (e);
            Variable v = variables.floor (ee.variable);
            if (v == null  ||  ! v.equals (ee.variable))
            {
                add (ee.variable);
            }
            else
            {
                v.replace (ee);
            }
        }
        //   Treat model output equations (in the old system) exactly the same as local equations.
        eqs = source.getValid ("outputEqs", new ArrayList<NDoc> (), List.class);
        for (NDoc e : eqs)
        {
            EquationEntry ee = new EquationEntry (e);
            if (ee.variable.name.length () == 0) throw new Exception ("Output equations which lack a variable assignment are no longer permitted.");
            Variable v = variables.floor (ee.variable);
            if (v == null  ||  ! v.equals (ee.variable))
            {
                add (ee.variable);
            }
            else
            {
                v.replace (ee);
            }
        }

        // Metadata
        Map<String, String> namedValues = source.getValid ("$metadata", new TreeMap<String, String> (), Map.class);
        metadata.putAll (namedValues);

        // Inherits
        NDoc parent = source.get ("parent");  // TODO: should be any number of parents (multiple-inheritance)
        if (parent != null)
        {
            merge (new EquationSet ("", this, parent));
        }

        pushDown ();
    }

    public boolean add (Variable v)
    {
        v.container = this;
        return variables.add (v);
    }

    public void replace (Variable v)
    {
        variables.remove (v);
        variables.add (v);
        v.container = this;
    }

    /**
        Merge given equation set into this, where contents of this always take precedence.
    **/
    public void merge (EquationSet s)
    {
        // Merge variables, and collate equations within each variable
        for (Variable v : s.variables)
        {
            Variable r = variables.floor (v);
            if (r == null  ||  ! r.equals (v))
            {
                add (v);
            }
            else
            {
                r.merge (v);
            }
        }

        // Merge parts, and collate contents of any that are already present
        for (EquationSet p : s.parts)
        {
            EquationSet r = parts.floor (p);
            if (r == null  ||  ! r.equals (p))
            {
                parts.add (p);
                p.container = this;
            }
            else
            {
                r.merge (p);
            }
        }

        // Merge connection bindings
        // In theory, connection bindings are only created by the level above a given part, so none of the following should be necessary.
        // TODO: connectionBindings must maintain object identity after parts are merged!!!
        if (connectionBindings == null)
        {
            connectionBindings = s.connectionBindings;
        }
        else if (s.connectionBindings != null)
        {
            s.connectionBindings.putAll (connectionBindings);  // putAll() replaces entries in the receiving map, so we must merge into s first to preserve precedence
            connectionBindings = s.connectionBindings;
        }

        // Merge metadata
        s.metadata.putAll (metadata);
        metadata = s.metadata;
    }

    /**
        Move any equation that refers to a sub-namespace down into the associated equation list.
    **/
    public void pushDown ()
    {
        Set<Variable> temp = new TreeSet<Variable> (variables);
        variables.clear ();
        for (Variable v : temp)
        {
            pushDown (v);
        }
    }

    /**
        Place the given Variable in an appropriate sub-part, unless it has a $up or no dot operator.
    **/
    public void pushDown (Variable v)
    {
        int index = v.name.indexOf (".");
        if (index < 0  ||  v.name.startsWith ("$up."))
        {
            // Store at the current level.
            replace (v);
        }
        else
        {
            final String prefix = v.name.substring (0, index);
            EquationSet p = parts.floor (new EquationSet (prefix));
            if (p == null  ||  ! p.name.equals (prefix))
            {
                replace (v);
            }
            else
            {
                class Defixer implements ASTNodeTransformer
                {
                    public ASTNodeBase transform (ASTNodeBase node)
                    {
                        String result = node.toString ();
                        if (result.startsWith (prefix + "."))
                        {
                            node.setValue (result.substring (prefix.length () + 1));
                        }
                        return node;
                    }
                }

                ASTTransformationContext context = new ASTTransformationContext ();
                context.add (ASTVarNode.class, new Defixer ());

                v.transform (context);
                if (v.name.startsWith (prefix + "."))
                {
                    v.name = v.name.substring (prefix.length () + 1);
                }
                p.pushDown (v);
            }
        }
    }

    /**
        Compute the fully-qualified name of this equation set.
    **/
    public String prefix ()
    {
        if (container == null)
        {
            return name;
        }
        String temp = container.prefix ();
        if (temp.length () > 0)
        {
            return temp + "." + name;
        }
        return name;
    }

    /**
        Determine which equation set actually contains the value of the given variable.
        This applies all the name-resolution rules in the N2A language. In particular:
        <ul>
        <li>All name-resolution is referred up the part hierarchy until a match is found, or we run out of hierarchy.
        <li>$up skips one level.
        <li>A prefix that references the endpoint of a connection will be referred to that part.
        </ul>
        @param v Variable.name will be modified until it matches the name in the resolved EquationSet.
        Variable.reference must already exist.
        Any EquationSets visited after this one will be appended to Variable.reference.resolution.
        @param create If the container can be resolved but the variable does not exist in it, then create one.
    **/
    public EquationSet resolveEquationSet (Variable v, boolean create)
    {
        // Check $variables
        if (v.name.startsWith ("$"))
        {
            if (v.name.startsWith ("$up."))
            {
                if (container == null) return null;  // Unresolved! We can't go up any farther.
                v.name = v.name.substring (4);
                v.reference.resolution.add (container);
                return container.resolveEquationSet (v, create);
            }
            return this;  // Other $variables are always treated as local, even if they are undefined. For example: you would never want to inherit $n from a container!
        }
        if (   v.name.endsWith (".$k")
            || v.name.endsWith (".$max")
            || v.name.endsWith (".$min")
            || v.name.endsWith (".$projectFrom")
            || v.name.endsWith (".$projectTo")
            || v.name.endsWith (".$radius"))
        {
            return this;
        }

        // Check namespace references. These take precedence over variable names.
        String[] ns = v.name.split ("\\.", 2);
        if (ns.length > 1)
        {
            if (connectionBindings != null)
            {
                EquationSet alias = connectionBindings.get (ns[0]);
                if (alias != null)
                {
                    v.name = ns[1];
                    v.reference.resolution.add (connectionBindings.floorEntry (ns[0]));  // We need to add an Entry<> rather than simply the EquationSet in "alias".
                    return alias.resolveEquationSet (v, create);
                }
            }
            EquationSet down = parts.floor (new EquationSet (ns[0]));
            if (down != null  &&  down.name.equals (ns[0]))
            {
                v.name = ns[1];
                v.reference.resolution.add (down);
                return down.resolveEquationSet (v, create);
            }
        }

        // Check connections
        if (connectionBindings != null)
        {
            EquationSet alias = connectionBindings.get (v.name);
            if (alias != null)
            {
                v.reference.resolution.add (connectionBindings.floorEntry (v.name));  // same kind of resolution path as if we went into connected part, but ...
                v.name = "(connection)";                                              // don't match any variables within the connected part
                return alias;                                                         // and terminate as if we found a variable there
            }
        }

        // Check variable names
        if (variables.contains (v)) return this;  // found it!

        // Check if this is a direct reference to a child part.
        // Similar to a "down" reference, except without a variable.
        EquationSet part = parts.floor (new EquationSet (v.name));
        if (part != null  &&  part.name.equals (v.name))
        {
            return null; // formally, we don't allow a bare reference to a child population
        }

        if (create)
        {
            // Create a self-referencing variable with no equations
            // TODO: make sure we can handle an empty set of equations in C Backend
            // TODO: what attributes or equations should this have?
            Variable c = new Variable (v.name, v.order);
            add (c);
            c.reference = new VariableReference ();
            c.reference.variable = c;
            c.equations = new TreeSet<EquationEntry> ();
            return this;
        }

        // Look up the containment hierarchy
        if (container == null) return null;  // unresolved!!
        v.reference.resolution.add (container);
        return container.resolveEquationSet (v, create);
    }

    /**
        Search for the given variable within this specific equation set. If not found, return null.
    **/
    public Variable find (Variable v)
    {
        Variable result = variables.floor (v);
        if (result != null  &&  result.compareTo (v) == 0)
        {
            return result;
        }
        return null;
    }

    public VariableReference resolveReference (String variableName)
    {
        Variable query = new Variable (variableName);
        query.reference = new VariableReference ();
        EquationSet dest = resolveEquationSet (query, false);
        if (dest != null) query.reference.variable = dest.find (query);
        return query.reference;
    }

    /**
        Fill in the Variable.reference field of the given variable with the actual location of the data.
        Typically, the location is the same as the variable itself, unless it is a proxy for a variable in
        another equation set.
    **/
    public void resolveLHS ()
    {
        for (EquationSet s : parts)
        {
            s.resolveLHS ();
        }

        for (Variable v : variables)
        {
            Variable query = new Variable (v.name, v.order);
            query.reference = new VariableReference ();
            EquationSet dest = resolveEquationSet (query, true);
            if (dest != null) query.reference.variable = dest.find (query);
            v.reference = query.reference;
            if (v.reference.variable != v  &&  v.reference.variable != null)
            {
                v.addAttribute ("reference");
                v.reference.variable.addAttribute ("externalWrite");
                v.reference.variable.addDependency (v);  // v.reference.variable receives an external write from v, and therefore depends on it
                v.reference.variable.container.referenced = true;
                for (EquationEntry e : v.reference.variable.equations)  // because the equations of v.reference.variable must share its storage with us, they must respect unknown ordering and not simply write the value
                {
                    e.assignment = "+=";
                }
            }
        }
    }

    /**
        Attach the appropriate Variable to each ASTVarNode.
        Depends on results of: resolveLHS() -- to create indirect variables and thus avoid unnecessary failure of resolution
    **/
    public void resolveRHS () throws Exception
    {
        LinkedList<String> unresolved = new LinkedList<String> ();
        resolveRHSrecursive (unresolved);
        if (unresolved.size () > 0)
        {
            StringBuilder message = new StringBuilder ();
            message.append ("Unresolved variables:\n");
            ListIterator<String> it = unresolved.listIterator ();
            while (it.hasNext ()) message.append ("  " + it.next () + "\n");
            throw new Exception (message.toString ());
        }
    }

    public void resolveRHSrecursive (LinkedList<String> unresolved)
    {
        for (EquationSet s : parts)
        {
            s.resolveRHSrecursive (unresolved);
        }
    
        class Resolver implements ASTNodeTransformer
        {
            public Variable from;
            public LinkedList<String> unresolved;
            public ASTNodeBase transform (ASTNodeBase node)
            {
                ASTVarNode vn = (ASTVarNode) node;
                Variable query = new Variable (vn.getVariableName (), vn.getOrder ());
                query.reference = new VariableReference ();
                EquationSet dest = resolveEquationSet (query, false);
                if (dest == null)
                {
                    unresolved.add (vn.getVariableNameWithOrder ());
                }
                else
                {
                    query.reference.variable = dest.find (query);
                    if (query.reference.variable == null)
                    {
                        if (query.name.equals ("(connection)"))
                        {
                            query.reference.variable = new Variable ("(connection)");  // create a phantom variable.  TODO: should this be an attribute instead?
                            query.reference.variable.container = dest;  // the container itself is really the target
                            query.reference.variable.addAttribute ("initOnly"); // because instance variables are bound before the part is put into service, and remain constant for its entire life
                            query.reference.variable.reference = query.reference;  // TODO: when $connect() is implemented, instances should become first class variables in the equation set, and this circular reference will be created by resolveLHS()
                        }
                        else
                        {
                            unresolved.add (vn.getVariableNameWithOrder ());
                        }
                    }
                    else
                    {
                        from.addDependency (query.reference.variable);
                        if (from.container != query.reference.variable.container)
                        {
                            query.reference.variable.addAttribute ("externalRead");
                        }
                    }
                }
                vn.reference = query.reference;
                return vn;
            }
        }
        Resolver resolver = new Resolver ();
        ASTTransformationContext context = new ASTTransformationContext ();
        context.add (ASTVarNode.class, resolver);
    
        for (Variable v : variables)
        {
            resolver.from = v;
            resolver.unresolved = unresolved;
            v.transform (context);
        }
    }

    public String flatList (boolean showNamespace)
    {
        StringBuilder result = new StringBuilder ();

        ASTRenderingContext context;
        if (showNamespace)
        {
            class Prefixer implements ASTNodeRenderer
            {
                public String render (ASTNodeBase node, ASTRenderingContext context)
                {
                    ASTVarNode vn = (ASTVarNode) node;
                    String result = vn.toString ();
                    if (vn.reference == null  ||  vn.reference.variable == null)
                    {
                        return "<unresolved!>" + result;
                    }
                    else
                    {
                        return "<" + vn.reference.variable.container.prefix () + ">" + result;
                    }
                }
            }

            context = new ASTRenderingContext (true);
            context.add (ASTVarNode.class, new Prefixer ());
        }
        else
        {
            context = new ASTRenderingContext (true);
        }

        String prefix = prefix ();
        if (connectionBindings != null)
        {
            for (Entry<String, EquationSet> e : connectionBindings.entrySet ())
            {
                result.append (prefix + "." + e.getKey () + " = ");
                EquationSet s = e.getValue ();
                if (showNamespace)
                {
                    result.append ("<");
                    if (s.container != null)
                    {
                        result.append (s.container.prefix ());
                    }
                    result.append (">");
                }
                result.append (s.name + "\n");
            }
        }
        for (Variable v : variables)
        {
            for (EquationEntry e : v.equations)
            {
                result.append (prefix + "." + e.render (context) + "\n");
            }
        }

        for (EquationSet e : parts)
        {
            result.append (e.flatList (showNamespace));
        }

        return result.toString ();
    }

    /**
        Convert this equation list into an equivalent object where every included
        part with $n==1 is merged into its containing part.  Append (+=) equations
        are joined together into one long equation.
        flatten() is kind of like a dual of pushDown().
    **/
    public void flatten ()
    {
        TreeSet<EquationSet> temp = new TreeSet<EquationSet> (parts);
        for (final EquationSet s : temp)
        {
            s.flatten ();

            // Check if connection. They must remain a separate equation set for code-generation purposes.
            if (s.connectionBindings != null)
            {
                continue;
            }
            if (s.connected)
            {
                continue;
            }

            // Check if $n==1
            Variable n = s.find (new Variable ("$n", 0));
            if (n != null)  // We only do more work if $n exists. Non-existent $n is the same as $n==1
            {
                // make sure no other orders of $n exist
                Variable n2 = s.variables.higher (n);
                if (n2.name.equals ("$n"))
                {
                    continue;
                }
                // check contents of $n
                if (n.equations.size () != 1)
                {
                    continue;
                }
                EquationEntry ne = n.equations.first ();
                if (! ne.assignment.equals ("="))
                {
                    continue;
                }
                // If we can't evaluate $n as a number, then we treat it as 1
                // Otherwise, we check the actual value.
                if (ne.expression != null)
                {
                    Object value = ne.expression.eval ();
                    if (value instanceof Scalar  &&  ((Scalar) value).value != 1)
                    {
                        continue;
                    }
                }
                s.variables.remove (n);  // We don't want $n in the merged set.
            }

            // Don't merge if there are any conflicting $variables.
            boolean conflict = false;
            for (Variable v : s.variables)
            {
                if (! v.name.startsWith ("$")  ||  v.name.startsWith ("$up"))
                {
                    continue;
                }
                Variable d = find (v);
                if (d != null  &&  d.name.equals (v.name))  // for this match we don't care about order; that is, any differential order on either side causes a conflict
                {
                    conflict = true;
                    break;
                }
            }
            if (conflict)
            {
                continue;
            }

            // Merge

            final String prefix = s.name;
            parts.remove (s);

            //   Variables
            final TreeSet<String> names = new TreeSet<String> ();
            for (Variable v : s.variables)
            {
                names.add (v.name);
            }

            class Prefixer implements ASTNodeTransformer
            {
                public ASTNodeBase transform (ASTNodeBase node)
                {
                    String result = node.toString ();
                    if (result.startsWith ("$"))
                    {
                        if (result.startsWith ("$up."))
                        {
                            node.setValue (result.substring (4));
                        }
                        // otherwise, don't modify references to $variables
                    }
                    else if (names.contains (result))
                    {
                        node.setValue (prefix + "." + result);
                    }
                    return node;
                }
            }

            ASTTransformationContext context = new ASTTransformationContext ();
            context.add (ASTVarNode.class, new Prefixer ());

            for (Variable v : s.variables)
            {
                if (v.name.startsWith ("$"))
                {
                    if (v.name.startsWith ("$up."))
                    {
                        v.name = v.name.substring (4);
                    }
                    // otherwise merge all $variables with containing set
                }
                else
                {
                    v.name = prefix + "." + v.name;
                }
                v.transform (context);
                Variable v2 = find (v);
                if (v2 == null)
                {
                    add (v);
                }
                else
                {
                    v2.mergeExpressions (v);
                }
            }

            //   Parts
            for (EquationSet sp : s.parts)
            {
                sp.name = prefix + "." + sp.name;
                parts.add (sp);
            }

            //   Metadata
            for (Entry<String, String> e : s.metadata.entrySet ())
            {
                metadata.put (prefix + "." + e.getKey (), e.getValue ());
            }
        }
    }

    /**
        Assembles that list of all variables that can be used in an output expression.
        Depends on results of: resolveLHS() (optional, enables us to remove "reference" variables)
    **/
    public ParameterDomain getOutputParameters ()
    {
        ImageIcon icon;
        if (connectionBindings == null)
        {
            icon = ImageUtil.getImage ("layer.gif");
        }
        else
        {
            icon = ImageUtil.getImage ("bridge.gif");
        }
        ParameterDomain result = new ParameterDomain (name, icon);  // TODO: should we return the empty string, or replace it with something?

        for (Variable v : variables)
        {
            if (! v.hasAttribute ("reference"))
            {
                result.addParameter (new Parameter (v.nameString (), ""));
            }
        }
        for (EquationSet s : parts)
        {
            result.addSubdomain (s.getOutputParameters ());
        }

        return result;
    }

    /**
        Add variables to equation set that are needed, but that the user should not normally define.
        Depends on results of: none
    **/
    public void addSpecials ()
    {
        for (EquationSet s : parts)
        {
            s.addSpecials ();
        }

        setInit (0);  // force $init to exist

        Variable v = new Variable ("$dt", 0);
        if (add (v))
        {
            v.equations = new TreeSet<EquationEntry> ();
        }

        v = new Variable ("$live", 0);  // $live functions much the same as $init. See setInit().
        if (add (v))
        {
            v.addAttribute ("constant");  // default. Actual values should be set by setAttributeLive()
            v.equations = new TreeSet<EquationEntry> ();
            EquationEntry e = new EquationEntry (v, "");
            v.equations.add (e);
            e.assignment = "=";
            e.expression = new ASTConstant (new Scalar (1));
        }

        v = new Variable ("$t", 0);
        if (add (v))
        {
            v.equations = new TreeSet<EquationEntry> ();
        }

        v = new Variable ("$type", 0);
        if (add (v))
        {
            v.equations = new TreeSet<EquationEntry> ();
        }

        if (connectionBindings == null)  // Compartment
        {
            v = new Variable ("$index", 0);
            if (add (v))
            {
                v.addAttribute ("initOnly");  // most backends will set $index before processing init equations
                v.equations = new TreeSet<EquationEntry> ();
            }

            v = new Variable ("$n", 0);
            if (add (v))
            {
                v.addAttribute ("constant");  // default. Actual values set by client code.
                v.equations = new TreeSet<EquationEntry> ();
                EquationEntry e = new EquationEntry (v, "");
                v.equations.add (e);
                e.assignment = "=";
                e.expression = new ASTConstant (new Scalar (1));
            }
        }
    }

    /**
        Remove any variables (particularly $variables) that are not referenced by some
        equation. These values do not input to any other calculation, and they are not
        displayed. Therefore they are a waste of time and space.
        Depends on results of:
            resolveLHS(), resolveRHS(), fillIntegratedVariables(),
            addSpecials() -- so we can remove any $variables added unnecessarily
    **/
    public void removeUnused ()
    {
        for (EquationSet s : parts)
        {
            s.removeUnused ();
        }

        TreeSet<Variable> temp = new TreeSet<Variable> (variables);
        for (Variable v : temp)
        {
            if (v.hasUsers  ||  v.hasAttribute ("externalWrite")) continue;
            if (v.equations.size () > 0  &&  (v.name.startsWith ("$")  ||  v.name.contains (".$"))) continue;  // even if a $variable has no direct users, we must respect any statements about it

            // Scan AST for any special output functions.
            boolean output = false;
            for (EquationEntry e : v.equations)
            {
                if (e.expression.containsOutput ())
                {
                    output = true;
                    break;
                }
            }
            if (output)  // outputs must always exist!
            {
                v.addAttribute ("dummy");  // we only get the "dummy" attribute when we are not otherwise referenced (hasUsers==false)
                continue;
            }

            variables.remove (v);
            // In theory, removing variables may reduce the dependencies on some other variable to 0.
            // Then we could remove that variable as well. This would require multiple passes or some
            // other scheme to recheck everything affected. We don't really need that much accounting
            // because most unused variables will be $variables we added preemptively.
        }
    }

    /**
        Add a variable for the lower-order integrated form of each derivative, if it does not already exist.
        Depends on results of: none
    **/
    public void fillIntegratedVariables ()
    {
        for (EquationSet s : parts)
        {
            s.fillIntegratedVariables ();
        }

        Set<Variable> temp = new TreeSet<Variable> (variables);
        for (Variable v : temp)
        {
            // Detect "reference" variables.
            // This code duplicates a lot of logic in resolveEquationSet(). It is useful to do this here
            // because resolveLHS() is overkill. 
            if (v.name.startsWith ("$up.")) continue;
            if (connectionBindings != null)
            {
                String[] ns = v.name.split ("\\.", 2);
                if (ns.length > 1  &&  connectionBindings.get (ns[0]) != null) continue;
            }

            // This may seem inefficient, but in general there will only be one order to process.
            Variable last = v;
            for (int o = v.order - 1; o >= 0; o--)
            {
                Variable vo = new Variable (v.name, o);
                Variable found = find (vo);
                if (found == null)
                {
                    add (vo);
                    vo.equations = new TreeSet<EquationEntry> ();
                    found = vo;
                }
                found.addDependency (last);
                last = found;
            }
        }
    }

    /**
        Change the value of the constant $init in the current equation set.
        Used to indicate if we are in the init phase or not.
    **/
    public void setInit (float value)
    {
        Variable init = find (new Variable ("$init"));
        if (init == null)
        {
            EquationEntry e = new EquationEntry ("$init", 0);
            e.variable.addAttribute ("constant");  // TODO: should really be "initOnly", since it changes value during (at the end of) the init cycle.
            e.assignment = "=";
            e.expression = new ASTConstant (new Scalar (value));
            add (e.variable);
        }
        else
        {
            EquationEntry e = init.equations.first ();
            ASTConstant c = (ASTConstant) e.expression;
            c.setValue (new Scalar (value));
        }
    }

    public boolean getInit ()
    {
        Variable init = find (new Variable ("$init"));
        if (init == null) return false;
        EquationEntry e = init.equations.first ();
        ASTConstant c = (ASTConstant) e.expression;
        Object o = c.getValue ();
        if (! (o instanceof Scalar)) return false;
        return ((Scalar) o).value == 1.0;
    }

    public static ArrayList<EquationSet> getSplitFrom (ASTNodeBase node) throws Exception
    {
        ArrayList<EquationSet> result = new ArrayList<EquationSet> ();

        if (! (node instanceof ASTListNode))
        {
            throw new Exception ("$type expects a list of part names");
        }
        ASTListNode list = (ASTListNode) node;
        int count = list.getCount ();
        for (int i = 0; i < count; i++)
        {
            ASTNodeBase c = list.getChild (i);
            if (! (c instanceof ASTVarNode))
            {
                throw new Exception ("$type may only be assigned the name of a part");
            }
            ASTVarNode v = (ASTVarNode) c;
            if (v.reference == null  ||  v.reference.variable == null)
            {
                throw new Exception ("$type assigned fom an unresolved part name");
            }
            result.add (v.reference.variable.container);
        }

        return result;
    }

    /**
        Scans all conditional forms of $type, and stores the patterns in the splits field.
        Depends on results of: resolveLHS(), resolveRHS()
    **/
    public void collectSplits () throws Exception
    {
        for (EquationSet s : parts)
        {
            s.collectSplits ();
        }

        if (splits == null) splits = new ArrayList<ArrayList<EquationSet>> ();
        for (Variable v : variables)
        {
            if (v.reference == null  ||  v.reference.variable == null  ||  ! v.reference.variable.name.equals ("$type")) continue;

            EquationSet container = v.reference.variable.container;
            if (container.splits == null)  // in case we are referencing $type in another equation set that has not yet been processed
            {
                container.splits = new ArrayList<ArrayList<EquationSet>> ();
            }
            for (EquationEntry e : v.equations)
            {
                ArrayList<EquationSet> split = getSplitFrom (e.expression);
                if (! container.splits.contains (split))
                {
                    container.splits.add (split);
                }
            }
        }
    }

    /**
        Convenience function to assemble splits into (from,to) pairs for type conversion.
        Depends on results of: collectSplits()
    **/
    public Set<ArrayList<EquationSet>> getConversions ()
    {
        Set<ArrayList<EquationSet>> result = new TreeSet<ArrayList<EquationSet>> ();
        for (EquationSet p : parts)
        {
            for (ArrayList<EquationSet> split : p.splits)
            {
                for (EquationSet s : split)
                {
                    ArrayList<EquationSet> pair = new ArrayList<EquationSet> ();
                    pair.add (p);
                    pair.add (s);
                    result.add (pair);
                }
            }
        }
        return result;
    }

    /**
        Determine which equation sets are capable of dying during structural dynamics.
        An equation set can die under the following circumstances:
        <ul>
        <li>It has an assignment to $n that can decrease during normal simulation.
        <li>It has an assignment to $p which can be less than 1 during normal simulation.
        <li>It has a $type split which does not include the part as offspring.
        <li>It references parts that can die.
        <li>It is in a container that can die.
        </ul>
        Each of these may call for different processing in the simulator, so various
        flags are set on each equation set to indicate the causes.
        Depends on results of: addSpecials(), findConstants(), collectSplits(), findInitOnly()
    **/
    public void findDeath ()
    {
        findLethalVariables ();
        while (findLethalContainers ()) {}
    }

    public void findLethalVariables ()
    {
        for (EquationSet s : parts)
        {
            s.findLethalVariables ();
        }

        // Determine if $n can decrease
        Variable n = find (new Variable ("$n"));
        if (n != null)
        {
            for (EquationEntry e : n.equations)
            {
                // Even if each expression is constant, $n could still change during operation if it is a multi-conditional.
                if (e.conditional != null  &&  ! (e.conditional instanceof ASTConstant))
                {
                    lethalN = true;
                    break;
                }
                if (! (e.expression instanceof ASTConstant))
                {
                    lethalN = true;
                    break;
                }
            }
        }

        // Determine if $p has an assignment less than 1
        Variable p = find (new Variable ("$p"));
        if (p != null  &&  ! p.hasAttribute ("initOnly"))
        {
            // Determine if any equation is capable of setting $p to something besides 1
            for (EquationEntry e : p.equations)
            {
                ASTNodeBase expression = e.expression;
                if (expression instanceof ASTConstant)
                {
                    Object value = ((ASTConstant) expression).getValue ();
                    if (value instanceof Scalar)
                    {
                        if (((Scalar) value).value == 1.0) continue;
                    }
                }
                lethalP = true;
                break;
            }
        }

        // Determine if any splits kill this part
        for (ArrayList<EquationSet> split : splits)  // my splits are the parts I can split into
        {
            if (! split.contains (this))
            {
                lethalType = true;
                break;
            }
        }
    }

    /**
        @return true if something changed; false if no new ways to die were found
    **/
    public boolean findLethalContainers ()
    {
        boolean somethingChanged = false;

        if (! lethalContainer  &&  container != null  &&  container.canDie ())
        {
            lethalContainer = true;
            somethingChanged = true;
            Variable live = container.find (new Variable ("$live"));
            if (live != null) live.hasUsers = true;
        }

        if (connectionBindings != null)
        {
            for (Entry<String,EquationSet> e : connectionBindings.entrySet ())
            {
                EquationSet s = e.getValue ();
                if (s.canDie ())
                {
                    Variable live = s.find (new Variable ("$live"));
                    if (live != null) live.hasUsers = true;
                    if (! lethalConnection)
                    {
                        lethalConnection = true;
                        somethingChanged = true;
                    }
                }
            }
        }

        // tail recursion is better, because container death propagates downward
        for (EquationSet s : parts)
        {
            if (s.findLethalContainers ()) somethingChanged = true;
        }

        return somethingChanged;
    }

    public boolean canDie ()
    {
        return lethalN  ||  lethalP  ||  lethalType  || lethalConnection  || lethalContainer;
    }

    /**
        Determine if population can grow by a means other than setting $n, specifically by
        $type splits that produce more than 1 offspring of our current type.
        Depends on the results of: 
    **/
    public boolean canGrow ()
    {
        for (ArrayList<EquationSet> split : splits)
        {
            int count = 0;
            for (EquationSet s : split)
            {
                if (s == this) count++;  // Direct object identity is OK here.
            }
            if (count >= 2) return true;
        }
        return false;
    }

    /**
        Determines the attributes of $live, based on whether other parts depend on it.
        $live is either constant, transient, or stored.
        constant (the default) if we can't die or no part depends on us.
        transient              if we only die in response to the death of our container or a referenced part.
        stored                 if we can die from $n, $p or $type, that is, if the fact that we died is local knowledge.
        Depends on results of: findDeath() (and indirectly on findInitOnly())
    **/
    public void setAttributesLive ()
    {
        for (EquationSet s : parts)
        {
            s.setAttributesLive ();
        }

        Variable live = find (new Variable ("$live"));
        if (live != null)
        {
            live.removeAttribute ("constant");
            live.removeAttribute ("initOnly");
            live.removeAttribute ("accessor");

            if (canDie ()  &&  live.hasUsers)
            {
                if (lethalN  ||  lethalP  ||  lethalType)
                {
                    live.addAttribute ("initOnly");  // Not exactly true. $live can change after init(), but only indirectly. This forces $live to be set during init().
                }
                else  // lethalConnection  ||  lethalContainer
                {
                    live.addAttribute ("accessor");
                }
            }
            else
            {
                live.addAttribute ("constant");  // should already be set constant, but no harm in doing it again
            }
        }
    }

    /**
        Assign a Type to each variable.
        Evaluates the entire equation set as if during the init phase. It
        should be possible to assign a concrete value to each variable at that
        time. If not, then the equation set has a bug at the user level.
        Depends on results of: addSpecials(), fillIntegratedVariables(), resolveRHS(), resolveLHS()
    **/
    public void determineTypes ()
    {
        EvaluationContext context = new EvaluationContext ();
        determineTypesInit (context);
        while (determineTypesEval (context)) {}
    }

    public void determineTypesInit (EvaluationContext context)
    {
        for (EquationSet s : parts)
        {
            s.determineTypesInit (context);
        }

        for (Variable v : variables)
        {
            if (v.hasAttribute ("dummy")) continue;

            if (v.name.contains ("$"))
            {
                if (v.name.equals ("$xyz")  ||  v.name.endsWith (".$projectFrom")  ||  v.name.endsWith (".$projectTo"))  // are there always dots before $projectFrom/To?
                {
                    v.reference.variable.type = new Matrix (3, 1);
                }
                else if (v.name.equals ("$init")  ||  v.name.equals ("$live"))
                {
                    v.reference.variable.type = new Scalar (1);
                }
                else
                {
                    v.reference.variable.type = new Scalar (0);
                }
            }
            else if (v.hasAttribute ("constant"))
            {
                v.reference.variable.type = (Type) ((ASTConstant) v.equations.first ().expression).getValue ();
            }
            else
            {
                v.reference.variable.type = new Scalar (0);
            }

            context.set (v.reference.variable, v.reference.variable.type);
        }
    }

    public boolean determineTypesEval (EvaluationContext context)
    {
        boolean changed = false;
        for (Variable v : variables)
        {
            if (v.hasAny (new String[] {"constant", "dummy"})) continue;
            if (v.name.startsWith ("$")  ||  v.name.contains (".$")) continue;

            Type value;
            if (v.hasAttribute ("integrated"))
            {
                value = find (new Variable (v.name, v.order + 1)).type;  // this should exist, so no need to verify result
            }
            else
            {
                value = context.get (v.reference.variable, false);
            }
            if (value.betterThan (v.type))  // v.type could be null, but betterThan() still works
            {
                v.type = value;
                changed = true;
            }
        }
        for (EquationSet s : parts)
        {
            if (s.determineTypesEval (context)) changed = true;
        }
        return changed;
    }

    public void addAttribute (String attribute, int connection, boolean withOrder, String[] names)
    {
        addAttribute (attribute, connection, withOrder, new TreeSet<String> (Arrays.asList (names)));
    }

    /**
        @param attribute The string to add to the tags associated with each given variable.
        @param connection Tri-state: 1 = must be a connection, -1 = must be a compartment, 0 = can be either one
        @param withOrder Restricts name matching to exactly the same order of derivative,
        that is, how many "prime" marks are appended to the variable name.
        When false, matches any variable with the same base name.
        @param names A set of variable names to search for and tag.
    **/
    public void addAttribute (String attribute, int connection, boolean withOrder, Set<String> names)
    {
        for (EquationSet s : parts)
        {
            s.addAttribute (attribute, connection, withOrder, names);
        }

        if (connectionBindings == null)
        {
            if (connection == 1)
            {
                return;
            }
        }
        else
        {
            if (connection == -1)
            {
                return;
            }
        }
        for (Variable v : variables)
        {
            String vname = v.name;
            if (withOrder)
            {
                vname = v.nameString ();
            }
            for (String n : names)
            {
                if (n.equals (vname)  ||  vname.endsWith ("." + n))
                {
                    v.addAttribute (attribute);
                    break;
                }
            }
        }
    }

    public void removeAttribute (String attribute, int connection, boolean withOrder, String[] names)
    {
        removeAttribute (attribute, connection, withOrder, new TreeSet<String> (Arrays.asList (names)));
    }

    public void removeAttribute (String attribute, int connection, boolean withOrder, Set<String> names)
    {
        for (EquationSet s : parts)
        {
            s.removeAttribute (attribute, connection, withOrder, names);
        }

        if (connectionBindings == null)
        {
            if (connection == 1)
            {
                return;
            }
        }
        else
        {
            if (connection == -1)
            {
                return;
            }
        }
        for (Variable v : variables)
        {
            String name = v.name;
            if (withOrder)
            {
                name = v.nameString ();
            }
            if (names.contains (name))
            {
                v.removeAttribute (attribute);
            }
        }
    }

    /**
        Identifies variables that have a known value before code generation.
        Also removes arithmetic operations that have no effect:
        <ul>
        <li>evaluate (node) == constant --> constant node
        <li>node + 0  --> node
        <li>node || 0 --> node
        <li>node || 1 --> 1 (constant node)
        <li>node - 0  --> node
        <li>node * 1  --> node
        <li>node * 0  --> 0
        <li>node && 1 --> node (note that * and && have the same effect, except && always outputs 0 or 1)
        <li>node && 0 --> 0
        <li>node / 1  --> node
        </ul>
        Depends on results of: resolveRHS()  (so that named constants can be found during evaluation)
    **/
    public void findConstants ()
    {
        class CollapseConstants implements ASTNodeTransformer
        {
            EvaluationContext ec = new EvaluationContext ();

            public ASTNodeBase transform (ASTNodeBase node)
            {
                try
                {
                    Type o = node.eval (ec);
                    if (o != null)
                    {
                        if (node instanceof ASTVarNode)
                        {
                            // Don't let $init get replaced by a simple constant
                            if (((ASTVarNode) node).getValue ().equals ("$init")) return node;
                        }
                        return new ASTConstant (o);
                    }
                }
                catch (EvaluationException exception)
                {
                }
                if (! (node instanceof ASTOpNode)) return node;

                // Otherwise try arithmetic simplifications
                Object operation = node.getValue ();
                if (operation instanceof Add)
                {
                    Object c = node.getChild (0).getValue ();
                    if (c instanceof Scalar  &&  ((Scalar) c).value == 0) return node.getChild (1);
                    c = node.getChild (1).getValue ();
                    if (c instanceof Scalar  &&  ((Scalar) c).value == 0) return node.getChild (0);
                }
                if (operation instanceof Subtract)
                {
                    Object c = node.getChild (1).getValue ();
                    if (c instanceof Scalar  &&  ((Scalar) c).value == 0) return node.getChild (0);
                }
                if (operation instanceof Divide)
                {
                    Object c = node.getChild (1).getValue ();
                    if (c instanceof Scalar  &&  ((Scalar) c).value == 1) return node.getChild (0);
                }
                if (operation instanceof Multiply)
                {
                    Object c = node.getChild (0).getValue ();
                    if (c instanceof Scalar)
                    {
                        double value = ((Scalar) c).value;
                        if (value == 0) return new ASTConstant (new Scalar (0));
                        if (value == 1) return node.getChild (1);
                    }
                    c = node.getChild (1).getValue ();
                    if (c instanceof Scalar)
                    {
                        double value = ((Scalar) c).value;
                        if (value == 0) return new ASTConstant (new Scalar (0));
                        if (value == 1) return node.getChild (0);
                    }
                }
                if (operation instanceof AND)
                {
                    Object c = node.getChild (0).getValue ();
                    if (c instanceof Scalar)
                    {
                        double value = ((Scalar) c).value;
                        if (value == 0) return new ASTConstant (new Scalar (0));
                        else            return node.getChild (1);
                    }
                    c = node.getChild (1).getValue ();
                    if (c instanceof Scalar)
                    {
                        double value = ((Scalar) c).value;
                        if (value == 0) return new ASTConstant (new Scalar (0));
                        else            return node.getChild (0);
                    }
                }
                if (operation instanceof OR)
                {
                    Object c = node.getChild (0).getValue ();
                    if (c instanceof Scalar)
                    {
                        double value = ((Scalar) c).value;
                        if (value == 0) return node.getChild (1);
                        else            return new ASTConstant (new Scalar (1));
                    }
                    c = node.getChild (1).getValue ();
                    if (c instanceof Scalar)
                    {
                        double value = ((Scalar) c).value;
                        if (value == 0) return node.getChild (0);
                        else            return new ASTConstant (new Scalar (1));
                    }
                }

                return node;
            }
        }

        ASTTransformationContext context = new ASTTransformationContext ();
        CollapseConstants c = new CollapseConstants ();
        context.add (ASTOpNode    .class, c);
        context.add (ASTFunNode   .class, c);
        context.add (ASTMatrixNode.class, c);
        context.add (ASTListNode  .class, c);
        context.add (ASTVarNode   .class, c);

        while (findConstantsRecursive (context)) {}
    }

    public boolean findConstantsRecursive (ASTTransformationContext context)
    {
        boolean result = false;
        for (EquationSet s : parts)
        {
            if (s.findConstantsRecursive (context)) result = true;
        }

        for (Variable v : variables)
        {
            if (v.hasAny (new String[] {"constant", "initOnly"})) continue;

            v.transform (context);

            // Check if we have a constant
            if (v.equations.size () != 1) continue;
            EquationEntry e = v.equations.first ();
            if (e.conditional != null) continue;
            if (e.expression instanceof ASTConstant)
            {
                v.addAttribute ("constant");
                result = true;
            }
        }
        return result;
    }

    /**
        Identifies variables that act as subexpressions, and thus are not stored in the part's state.
        Depends on results of: none
    **/
    public void findTemporary () throws Exception
    {
        for (EquationSet s : parts)
        {
            s.findTemporary ();
        }

        for (Variable v : variables)
        {
            // Check for special variables that we wish not to store in connections.
            if (connectionBindings != null  &&  v.order == 0)
            {
                if (v.name.equals ("$p"))
                {
                    if (! v.hasAny (new String [] {"externalRead", "externalWrite", "integrated"}))
                    {
                        v.addAttribute ("temporary");
                        continue;
                    }
                }
                else if (v.name.contains ("$project"))
                {
                    if (! v.hasAttribute ("constant"))
                    {
                        v.addAttribute ("temporary");
                        continue;
                    }
                }
            }

            if (v.equations.size () == 0) continue;
            EquationEntry f = v.equations.first ();
            boolean hasTemporary = f.assignment != null  &&  f.assignment.equals (":=");
            for (EquationEntry e : v.equations)
            {
                boolean foundTemporary = e.assignment != null  &&  e.assignment.equals (":=");  
                if (foundTemporary != hasTemporary)
                {
                    throw new Exception ("Inconsisten use of ':=' by " + v.container.prefix () + "." + v.name);
                }
                if (hasTemporary)
                {
                    e.assignment = "=";  // replace := with = for use in code generation
                }
            }
            if (hasTemporary)
            {
                v.addAttribute ("temporary");
            }
        }
    }

    /**
        Populates the order field with the sequence of variable evaluations that minimizes
        the need for buffering. If there are no cyclic dependencies, then this problem can
        be solved exactly. If there are cycles, then this method uses a simple heuristic:
        prioritize variables with the largest number of dependencies.
        Depends on results of: resolveRHS(), findTemporary()
    **/
    public void determineOrder ()
    {
        for (EquationSet s : parts)
        {
            s.determineOrder ();
        }

        // Reset variables for analysis
        ordered = new ArrayList<Variable> ();
        for (Variable v : variables)
        {
            v.before   = new ArrayList<Variable> ();
            v.priority = 0;
        }

        // Determine order constraints for each variable separately
        for (Variable v : variables)
        {
            v.setBefore ();
        }

        // Assign depth in dependency tree, processing variables with the most ordering constraints first
        class CompareDependency implements Comparator<Variable>
        {
            public int compare (Variable a, Variable b)
            {
                return b.before.size () - a.before.size ();
            }
        }
        PriorityQueue<Variable> queueDependency = new PriorityQueue<Variable> (variables.size (), new CompareDependency ());
        queueDependency.addAll (variables);
        for (Variable v = queueDependency.poll (); v != null; v = queueDependency.poll ())
        {
            v.visited = null;
            v.setPriority (1);
        }

        // Assemble dependency tree into flat list
        class ComparePriority implements Comparator<Variable>
        {
            public int compare (Variable a, Variable b)
            {
                return a.priority - b.priority;
            }
        }
        PriorityQueue<Variable> queuePriority = new PriorityQueue<Variable> (variables.size (), new ComparePriority ());
        queuePriority.addAll (variables);
        for (Variable v = queuePriority.poll (); v != null; v = queuePriority.poll ())
        {
            ordered.add (v);
        }

        // Tag any circular dependencies
        int count = ordered.size ();
        for (int index = 0; index < count; index++)
        {
            Variable v = ordered.get (index);
            if (v.uses == null)
            {
                continue;
            }
            for (Variable u : v.uses)
            {
                if (   u.container == this  // must be in same equation set for order to matter
                    && ! (u.name.equals (v.name)  &&  u.order == v.order + 1)  // must not be my derivative
                    && ! u.hasAttribute ("temporary")  // temporaries follow the opposite rule on ordering, so don't consider them here
                    &&  ordered.indexOf (u) < index)  // and finally, is it actually ahead of me in the odering?
                {
                    System.out.println ("cyclic dependency: " + v.name + " comes after " + u.name);
                    u.addAttribute ("cycle");  // must be buffered; otherwise we will get the "after" value rather than "before"
                }
            }
        }
    }

    /**
        Identifies variables that are integrated from their derivative.
        Depends on results of: resolveLHS()  (to identify references)
    **/
    public void findIntegrated ()
    {
        for (EquationSet s : parts)
        {
            s.findIntegrated ();
        }

        for (Variable v : variables)
        {
            if (v.hasAttribute ("reference")) continue;
            // Check if there is another equation that is exactly one order higher than us.
            // If so, then we must be integrated from it.
            if (find (new Variable (v.name, v.order + 1)) != null)
            {
                v.addAttribute ("integrated");
            }
        }
    }

    /**
        Identifies variables that have differential order higher than 0.
        Also tags any temporary variables that a derivative depends on.
        Depends on results of:
            fillIntegratedVariables() to get lower-order derivatives
            resolveRHS() to establish dependencies
            findTeporary() to identify temporaries
            All of these are optional.
    **/
    public void findDerivative ()
    {
        for (EquationSet s : parts)
        {
            s.findDerivative ();
        }

        for (Variable v : variables)
        {
            if (v.order > 0)
            {
                v.visitTemporaries ();  // sets attribute "derivativeOrDependency"
            }
        }
    }

    /**
        Identify variables that only change during init.
        For now, the criteria are:
        <ul>
        <li>only one equation -- Multiple equations imply the value could change
        via conditional selection. This is merely a heuristic, as the actual logic
        could work out such that the value doesn't change after init anyway.
        <li>not any of {"integrated", "constant"}
        <li>EITHER the conditional expression is 0 when $init=0 -- Only possible if there
        actually is a conditional expression.
        <li>OR the equation only depends on constants and other initOnly variables
        </ul>
    **/
    public void findInitOnly ()
    {
        while (findInitOnlyRecursive ()) {}
    }

    public boolean findInitOnlyRecursive ()
    {
        boolean changed = false;

        for (EquationSet s : parts)
        {
            if (s.findInitOnlyRecursive ()) changed = true;
        }

        for (Variable v : variables)
        {
            if (v.hasAny (new String[] {"initOnly", "constant", "integrated", "dummy"})) continue;  // Note: some variables get tagged "initOnly" by other means, so don't re-process
            if (v.equations.size () != 1) continue;  // TODO: should a variable with no equations be considered initOnly?

            // Determine if our single equation is guaranteed not to fire after the init step
            EquationEntry e = v.equations.first ();
            if (e.conditional != null)
            {
                setInit (0);  // $init should be 0 in general
                try
                {
                    Object result = e.conditional.eval ();
                    if (result != null  &&  result instanceof Scalar  &&  ((Scalar) result).value == 0)
                    {
                        v.addAttribute ("initOnly");
                        changed = true;
                        continue;
                    }
                }
                catch (EvaluationException exception)
                {
                }
            }

            // Determine if variable depends only on constants and initOnly variables
            if (e.expression.isInitOnly ())
            {
                v.addAttribute ("initOnly");
                changed = true;
            }
        }

        return changed;
    }

    /**
        Provide each part with a list of connections which access it and which define a $min or $max on the number of connections.
    **/
    public void findAccountableConnections ()
    {
        for (EquationSet s : parts)
        {
            s.findAccountableConnections ();
        }

        if (connectionBindings == null) return;
        for (Entry<String, EquationSet> c : connectionBindings.entrySet ())
        {
            String alias = c.getKey ();
            Variable max = find (new Variable (alias + ".$max"));
            Variable min = find (new Variable (alias + ".$min"));
            if (max == null  &&  min == null) continue;
            EquationSet endpoint = c.getValue ();
            if (endpoint.accountableConnections == null) endpoint.accountableConnections = new TreeSet<AccountableConnection> ();
            endpoint.accountableConnections.add (new AccountableConnection (this, alias));
        }
    }

    public String getNamedValue (String name)
    {
        return getNamedValue (name, "");
    }

    public String getNamedValue (String name, String defaultValue)
    {
        if (metadata.containsKey (name)) return metadata.get (name);
        return defaultValue;
    }

    public void setNamedValue (String name, String value)
    {
        metadata.put (name, value);
    }

    public int compareTo (EquationSet that)
    {
        return name.compareTo (that.name);
    }

    @Override
    public boolean equals (Object that)
    {
        if (this == that)
        {
            return true;
        }
        EquationSet s = (EquationSet) that;
        if (s == null)
        {
            return false;
        }
        return compareTo (s) == 0;
    }
}
