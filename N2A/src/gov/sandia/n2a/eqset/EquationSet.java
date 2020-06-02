/*
Copyright 2013-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.eqset;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MPersistent;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.OperatorBinary;
import gov.sandia.n2a.language.OperatorLogical;
import gov.sandia.n2a.language.ParseException;
import gov.sandia.n2a.language.Renderer;
import gov.sandia.n2a.language.Split;
import gov.sandia.n2a.language.Transformer;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.UnsupportedFunctionException;
import gov.sandia.n2a.language.UnitValue;
import gov.sandia.n2a.language.Visitor;
import gov.sandia.n2a.language.function.Input;
import gov.sandia.n2a.language.function.Output;
import gov.sandia.n2a.language.function.ReadMatrix;
import gov.sandia.n2a.language.operator.GE;
import gov.sandia.n2a.language.operator.GT;
import gov.sandia.n2a.language.operator.LE;
import gov.sandia.n2a.language.operator.LT;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.MatrixDense;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Text;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.plugins.extpoints.Backend.AbortRun;
import gov.sandia.n2a.ui.images.ImageUtil;
import tech.units.indriya.AbstractUnit;
import gov.sandia.n2a.parms.Parameter;
import gov.sandia.n2a.parms.ParameterDomain;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NavigableSet;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.ImageIcon;

public class EquationSet implements Comparable<EquationSet>
{
    public String                              name;
    public MNode                               source;
    public EquationSet                         container;
    public NavigableSet<Variable>              variables;
    public NavigableSet<EquationSet>           parts;
    public List<ConnectionBinding>             connectionBindings;     // non-null iff this is a connection
    public boolean                             connected;
    public NavigableSet<AccountableConnection> accountableConnections; // Connections which declare a $min or $max w.r.t. this part. Note: connected can be true even if accountableConnections is null.
    public List<ConnectionBinding>             dependentConnections;   // Connection bindings which include this equation set along their path. Used to update the paths during flattening.
    public boolean                             needInstanceTracking;   // Instance tracking is necessary due to a connection path that runs through this part.
    public EquationSet                         visited;                // Keeps track of path when visiting parts
    public int                                 priority;               // Used for sorting parts according to connection dependency.
    public List<EquationSet>                   orderedParts;           // According to connection dependency
    public MNode                               metadata;
    public List<Variable>                      ordered;
    public List<ArrayList<EquationSet>>        splits;                 // Enumeration of the $type splits this part can go through
    public HashSet<EquationSet>                splitSources;           // Equation sets that might create an instance of this equation set via a $type split. Can include ourself.
    public boolean                             lethalN;                // our population could shrink
    public boolean                             lethalP;                // we could have a non-zero probability of dying in some cycle 
    public boolean                             lethalType;             // we can be killed by a part split
    public boolean                             lethalConnection;       // indicates we are a connection, and one of the parts we connect to can die
    public boolean                             lethalContainer;        // our parent could die
    public boolean                             referenced;             // Some other equation set writes to one of our variables. If we can die, then exercise care not to reuse this part while other parts are still writing to it. Otherwise our reincarnated part might get written with values from our previous life.
    public ConnectionMatrix                    connectionMatrix;       // If non-null, this is a connection whose existence depends primarily on elements of a matrix.
    public Object                              backendData;            // holder for extra data associated with each equation set by a given backend

    public static class ConnectionBinding
    {
        public int         index;  // position in connectionBindings array
        public String      alias;
        public Variable    variable;  // The original variable from which this binding was derived. Other variables that resolve through this binding are recorded in Variable.usedBy.
        public EquationSet endpoint;
        /**
            Trail of objects followed to resolve the connection.
            Assumes that we start within the current instance, so the first entry will almost
            always be a step up to our container.
            See VariableReference.resolution for a closely related structure.
        **/
        public ArrayList<Object> resolution = new ArrayList<Object> ();

        public void addResolution (Object o)
        {
            resolution.add (o);
            if (o instanceof EquationSet)
            {
                EquationSet s = (EquationSet) o;
                if (s.dependentConnections == null) s.dependentConnections = new ArrayList<ConnectionBinding> ();
                s.dependentConnections.add (this);
            }
        }

        public void addDependencies ()
        {
            for (Object o : resolution)
            {
                if (o instanceof ConnectionBinding) variable.addDependencyOn (((ConnectionBinding) o).variable);
            }
        }
    }

    public static class AccountableConnection implements Comparable<AccountableConnection>
    {
        public EquationSet connection; // the connection, that is, the thing being accounted (the endpoint is the thing doing the accounting)
        public String      alias;      // name within the connection that refers to the endpoint
        public Variable    count;      // Phantom variable to satisfy RHS references to $count for this endpoint. May be null if there are no such references.
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
        public boolean equals (Object that)
        {
            if (that instanceof AccountableConnection) return compareTo ((AccountableConnection) that) == 0;
            return false;
        }
    }

    public class ConnectionMatrix
    {
        public ReadMatrix A;

        // Bindings for populations associated with the rows/columns of the matrix.
        public ConnectionBinding rows;
        public ConnectionBinding cols;

        // Mappings from row/column position to $index of respective population.
        public Equality rowMapping;
        public Equality colMapping;

        public ConnectionMatrix (ReadMatrix A)
        {
            this.A = A;
            if (A.operands.length < 3) return;
            AccessVariable av1 = endpoint (A.operands[1]);
            AccessVariable av2 = endpoint (A.operands[2]);
            if (av1 == null  ||  av2 == null) return;

            rows = findConnection (av1.reference);
            cols = findConnection (av2.reference);
            if (rows == null  ||  cols == null) return;

            rowMapping = new Equality (A.operands[1], av1);
            rowMapping.solve ();
            if (rowMapping.lhs != rowMapping.target) rowMapping = null;
            colMapping = new Equality (A.operands[2], av2);
            colMapping.solve ();
            if (colMapping.lhs != colMapping.target) colMapping = null;
        }

        /**
            Utility function for identifying connection endpoints implied in ReadMatrix parameters.
        **/
        public AccessVariable endpoint (Operator op)
        {
            class IndexVisitor implements Visitor
            {
                public AccessVariable found;
                public boolean visit (Operator op)
                {
                    if (op instanceof AccessVariable)
                    {
                        AccessVariable av = (AccessVariable) op;
                        if (av.name.contains ("$index"))
                        {
                            found = av;
                            return false;
                        }
                    }
                    return true;
                }
            }
            IndexVisitor iv = new IndexVisitor ();
            op.visit (iv);
            return iv.found;  // can be null
        }

        public ConnectionBinding findConnection (VariableReference r)
        {
            // As a connection, the first ConnectionBinding we encounter in the resolution path should be
            // our own reference to the endpoint.
            for (Object o : r.resolution)
            {
                if (o instanceof ConnectionBinding) return (ConnectionBinding) o;
            }
            return null;
        }
    }

    /**
        Create a lightweight object for doing queries.
        We avoid allocating any structures that aren't strictly necessary for comparing two equation sets.
        That's basically just the name.
    **/
    public EquationSet (String name)
    {
        this.name = name;
    }

    /**
        Create an object suitable for manually building extra parts at compile-time.
        The minimal set of members are initialized so that middle-end routines won't crash.
        The caller is responsible for adding this object to container.
    **/
    public EquationSet (EquationSet container, String name)
    {
        this.name      = name;
        this.container = container;
        variables      = new TreeSet<Variable> ();
        parts          = new TreeSet<EquationSet> ();
        metadata       = new MVolatile ();
    }

    /**
        Construct a hierarchical tree of parts from a fully-resolved model.
    **/
    public EquationSet (MNode part) throws Exception
    {
        this (null, part);
    }

    public EquationSet (EquationSet container, MNode source) throws Exception
    {
        if (container == null)  // top-level model, so pay special attention to name
        {
            name = source.getOrDefault ("Model", "$inherit").split (",", 2)[0].replace ("\"", "");
        }
        else
        {
            name = source.key ();
        }

        this.source    = source;
        this.container = container;
        variables      = new TreeSet<Variable> ();
        parts          = new TreeSet<EquationSet> ();
        metadata       = new MVolatile ();

        // Sort equations by object-oriented operation
        boolean exception = false;
        for (MNode e : source)
        {
            String index = e.key ();
            if (index.equals ("$inherit")) continue;
            if (index.equals ("$reference")) continue;
            if (index.equals ("$metadata"))
            {
                metadata.merge (e);
                continue;
            }

            // That leaves only parts and variables
            try
            {
                if (MPart.isPart (e))
                {
                    parts.add (new EquationSet (this, e));
                }
                else
                {
                    Variable v = new Variable (this, e);
                    if (v.equations.size () > 0) variables.add (v);

                    // Add a watch expression, if requested.
                    MNode p = getTopDocumentNode ();
                    if (p != null)
                    {
                        MNode watch = p.child (e.key (), "$metadata", "watch");
                        if (watch != null)
                        {
                            // Determine dummy variable name
                            String dummy = "x0";
                            int suffix = 1;
                            while (find (new Variable (dummy)) != null  ||  source.child (dummy) != null) dummy = "x" + suffix++;

                            // Check for timeScale
                            EquationSet root = this;
                            while (root.container != null) root = root.container;
                            String timeScale = root.source.get ("$metadata", "watch", "timeScale");
                            String scale = "";
                            if (v.order > 0  &&  ! timeScale.isEmpty ())
                            {
                                try
                                {
                                    UnitValue uv = new UnitValue (timeScale);
                                    if (uv.value == 0) uv.value = 1;
                                    if (uv.unit == null) uv.unit = AbstractUnit.ONE;
                                    uv.unit = uv.unit.pow (-v.order);
                                    uv.value = Math.pow (uv.value, -v.order);
                                    scale = uv.bareUnit ();
                                }
                                catch (Exception ex) {}
                            }

                            // Create output expression
                            String expression = "output(\"\"," + v.nameString ();
                            if (! timeScale.isEmpty ())
                            {
                                expression += ",\"\",\"timeScale=" + timeScale;
                                if (! scale.isEmpty ()) expression += ",scale=" + scale;
                                expression += "\"";
                            }
                            expression += ")";
                            Variable o = new Variable (this, new MVolatile (expression, dummy));
                            variables.add (o);
                        }
                    }
                }
            }
            catch (ParseException pe)
            {
                exception = true;
                pe.print (Backend.err.get ());
            }
            catch (UnsupportedFunctionException ufe)
            {
                exception = true;
                Backend.err.get ().println (ufe.message);
            }
        }
        if (exception) throw new Backend.AbortRun ();
    }

    public ConnectionBinding findConnection (String alias)
    {
        if (connectionBindings == null) return null;
        for (ConnectionBinding c : connectionBindings)
        {
            if (c.alias.equals (alias)) return c;
        }
        return null;
    }

    /**
        Search for the given variable within this specific equation set. If not found, return null.
    **/
    public Variable find (Variable v)
    {
        Variable result = variables.floor (v);
        if (result != null  &&  result.compareTo (v) == 0) return result;
        return null;
    }

    public static Variable find (Variable query, List<Variable> list)
    {
        for (Variable v : list) if (v.equals (query)) return v;
        return null;
    }

    public boolean add (Variable v)
    {
        v.container = this;
        return variables.add (v);
    }

    public void override (Variable v)
    {
        variables.remove (v);  // Removes matching value, not exact object identity.
        variables.add (v);
    }

    public void override (String equation) throws Exception
    {
        String[] pieces = equation.split ("=", 2);
        String key = pieces[0];
        String value = "";
        if (pieces.length > 1) value = pieces[1];
        override (new Variable (this, new MPersistent (null, key, value)));
    }

    /**
        Compute the fully-qualified name of this equation set.
        Used mainly in error reporting and other cosmetic purposes.
    **/
    public String prefix ()
    {
        if (container == null) return "";  // Omit the top-level model name, as it just clutters outputs.
        String temp = container.prefix ();
        if (temp.length () > 0) return temp + "." + name;
        return name;
    }

    public List<String> getKeyPath ()
    {
        if (container == null) return new ArrayList<String> ();  // Empty. We don't include the key for the root document.
        List<String> result = container.getKeyPath ();
        result.add (name);
        return result;
    }

    public Object getObject (List<String> keyPath)
    {
        if (keyPath == null  ||  keyPath.isEmpty ()) return this;
        EquationSet result = this;
        for (String key : keyPath)
        {
            // Search for part with exact key match. findPart() has a different use-case, not suitable for this.
            EquationSet eqForKey = null;
            for (EquationSet p : result.parts)
            {
                if (p.name.equals (key))
                {
                    eqForKey = p;
                    break;
                }
            }
            if (eqForKey != null)
            {
                result = eqForKey;
                continue;
            }

            // At this point, we've failed to match an equation set. The only other option is a variable.
            // Either one matches or we fail. Either way, we are done.
            return result.find (Variable.fromLHS (key));
        }
        return result;
    }

    /**
        Returns the top-level document node associated with this equation set,
        assuming that $inherit was hacked to point to the name of the model in the database.
        It is possible that the associated node is created by inheritance and thus does not appear in top document.
        In that case we return null.
    **/
    public MNode getTopDocumentNode ()
    {
        if (container == null) return AppData.models.child (name);
        MNode p = container.getTopDocumentNode ();
        if (p == null) return null;
        return p.child (name);
    }

    /**
        Finds the part whose name matches the longest prefix of the given query string, if such a part exists.
        Otherwise, returns null. The matched prefix must be a proper name path, that is, either the full query
        string or else a portion delimited by dot.
    **/
    public EquationSet findPartPrefix (String query)
    {
        EquationSet result = null;
        int queryLength = query.length ();
        for (EquationSet p : parts)
        {
            if (query.startsWith (p.name))  // prefix matches
            {
                int length = p.name.length ();
                if (length == queryLength  ||  query.charAt (length) == '.')  // This is a legitimate name path.
                {
                    if (result == null  ||  length > result.name.length ()) result = p;
                }
            }
        }
        return result;
    }

    /**
        Finds the part whose name exactly matches the given query string. Returns null if no such part exists.
    **/
    public EquationSet findPart (String query)
    {
        for (EquationSet p : parts) if (p.name.equals (query)) return p;
        return null;
    }

    /**
        Find instance variables (that in other languages might be called pointers) and move them
        into the connectionBindings structure.
        Dependencies: This function must be the very first thing run after constructing the full
        equation set hierarchy. Other resolve functions depend on it.
    **/
    public void resolveConnectionBindings () throws Exception
    {
        LinkedList<String> unresolved = new LinkedList<String> ();
        resolveConnectionBindings (unresolved);
        if (unresolved.size () > 0)
        {
            PrintStream ps = Backend.err.get ();
            ps.println ("Unresolved connection targets:");
            for (String v : unresolved) ps.println ("  " + v);
            throw new Backend.AbortRun ();
        }
    }

    /**
        Scan for equations that look and smell like connection bindings.
        A binding equation has these characteristics:
        <ul>
        <li>Only one equation on the variable.
        <li>Unconditional (conditional bindings are not permitted)
        <li>No operators, only a name on RHS that appears like a variable name.
        <li>Both LHS and RHS are order 0 (not derivatives)
        <li>No variable in the current equation set matches the name.
        </ul>
        $up is permitted. The explicit name of a higher container may also be used.
        connect() should not appear. It should have been overwritten during construction.
        If connect() does appear, bindings are incomplete, which is an error.
    **/
    public void resolveConnectionBindings (LinkedList<String> unresolved) throws Exception
    {
        Iterator<Variable> it = variables.iterator ();  // need to use an iterator here, so we can remove variables from the set
        while (it.hasNext ())
        {
            Variable v = it.next ();

            // Detect instance variables
            if (v.order > 0) continue;
            if (v.equations.size () != 1) continue;
            if (v.assignment != Variable.REPLACE) continue;
            EquationEntry ee = v.equations.first ();
            if (ee.condition != null) continue;
            if (! (ee.expression instanceof AccessVariable)) continue;
            AccessVariable av = (AccessVariable) ee.expression;
            if (av.getOrder () > 0) continue;
            if (find (new Variable (av.getName ())) != null) continue;

            // Resolve connection endpoint to a specific equation set
            ConnectionBinding result = new ConnectionBinding ();
            if (! resolveConnectionBinding (av.name, result))
            {
                // If the target name contains a ".", then it is more likely to be a variable reference.
                // That reference may or may not be resolved later (for example, if it is a $variable that hasn't been added yet).
                // Only report simple names here, to minimize confusion.
                if (! av.name.contains (".")) unresolved.add (v.fullName () + " --> " + av.name);
                continue;
            }
            if (result.endpoint == null) continue;  // Could be a variable or prefix referring to an already-found connection.

            // Store connection binding
            if (connectionBindings == null) connectionBindings = new ArrayList<ConnectionBinding> ();
            result.alias = v.name;
            result.variable = v;
            result.index = connectionBindings.size ();
            connectionBindings.add (result);
            result.endpoint.connected = true;
            v.container = null;  // Prevent variable from interacting with other analysis routines.
            it.remove ();  // Should no longer be in the equation list, as there is nothing further to compute.
        }

        // Evaluate if any equation set along any of the connection paths needs to track instances.
        // Generally, this is a descent followed immediately by another descent.
        // The population reached in the first descent needs to be tracked.
        if (connectionBindings != null)
        {
            for (ConnectionBinding c : connectionBindings)
            {
                int last = c.resolution.size () - 1;
                for (int i = 1; i < last; i++)
                {
                    Object o0 = c.resolution.get (i-1);
                    if (! (o0 instanceof EquationSet)) continue;
                    Object o1 = c.resolution.get (i);
                    if (! (o1 instanceof EquationSet)) continue;
                    Object o2 = c.resolution.get (i+1);
                    if (! (o2 instanceof EquationSet)) continue;
                    EquationSet s0 = (EquationSet) o0;
                    EquationSet s1 = (EquationSet) o1;
                    EquationSet s2 = (EquationSet) o2;
                    if (s1.container == s0  &&  s2.container == s1)  // double descent
                    {
                        s1.needInstanceTracking = true;
                    }
                }
            }
        }

        // Descend to child parts after resolving parent. This order is necessary to support nested connections.
        for (EquationSet s : parts)
        {
            s.resolveConnectionBindings (unresolved);
        }
    }

    /**
        Returns reference to the named equation set, based on a search starting in the
        current equation set and applying all the N2A name-resolution rules.
        This is similar to resolveEquationSet(), but customized for connections rather than variables.
        @return true if the name was resolved. However, it might be resolved to a variable rather than a part.
        Further test by checking if result.endpoint is set.
    **/
    public boolean resolveConnectionBinding (String query, ConnectionBinding result)
    {
        if (query.isEmpty ())
        {
            result.endpoint = this;
            return true;
        }
        String[] pieces = query.split ("\\.", 2);
        String ns = pieces[0];

        if (ns.equals ("$up"))  // Don't bother with local checks if we know we are going up
        {
            if (container == null) return false;
            result.addResolution (container);
            if (pieces.length > 1) return container.resolveConnectionBinding (pieces[1], result);
            result.endpoint = container;
            return true;
        }

        EquationSet p = findPartPrefix (query);
        if (p != null)
        {
            List<Object> resolution = result.resolution;
            int last = resolution.size () - 1;
            if (last > 0  &&  resolution.get (last) == this  &&  resolution.get (last - 1) == p)  // We doubled back: came up from p to this container and then back down to p.
            {
                resolution.remove (last);  // Pop the extra step off the resolution path.
                // TODO: Should we remove the entry in this.dependentConnections?
            }
            else
            {
                result.addResolution (p);
            }
            int length = p.name.length ();
            if (length == query.length ())
            {
                result.endpoint = p;
                return true;
            }
            return p.resolveConnectionBinding (query.substring (length + 1), result);
        }

        ConnectionBinding c = findConnection (ns);
        if (c != null)  // ns names an existing (already found) connection binding.
        {
            result.addResolution (c);
            if (pieces.length > 1) return c.endpoint.resolveConnectionBinding (pieces[1], result);
            result.endpoint = c.endpoint;  // The query is an alias for the connection.
            return true;
        }

        Variable v = find (new Variable (ns));
        if (v != null) return true;  // The match was found, but it turns out to be a variable, not an equation set. result.endpoint should still be null.
        // Treat undefined $variables as local. This will never include $up, because that case is eliminated above.
        if (ns.startsWith ("$")) return true;  // Assert it is a variable, regardless. There is no legal way for this to be a part name, even if it contains a dot.

        if (container == null) return false;
        result.addResolution (container);
        return container.resolveConnectionBinding (query, result);
    }

    /**
        Order parts according to dependency on connection targets.
        Most connections have regular populations as endpoints. A connection (CC) can also target another connection population (C).
        In that case, CC needs C to be instantiated before C can form connections. Under normal simulation, this can happen via polling.
        At startup, it helps to instantiate C before CC. The goal of this function is to sort the parts collection so that CC will come after C.
        These kind of dependencies should form a directed acyclic graph (DAG), and the depth is arbitrary.
        This function follows the example of determineOrder() for variables.
        Depends on results of: resolveConnectionBindings(), flatten()
    **/
    public void sortParts ()
    {
        if (parts.size () == 0) return;  // Nothing to do. We want to avoid requesting a zero-length PriorityQueue below, because that is illegal.

        for (EquationSet p : parts)
        {
            p.sortParts ();  // Apply this process recursively.
            p.priority = 0;  // Should not generally be needed, since we do this process only once, and priority is initialize to zero.
        }

        for (EquationSet p : parts)
        {
            p.setPriority (1, null);
        }

        // Assemble dependency tree into flat list
        // This queue reverses the order, so larger priority numbers come first.
        PriorityQueue<EquationSet> queuePriority = new PriorityQueue<EquationSet> (parts.size (), new Comparator<EquationSet> ()
        {
            public int compare (EquationSet a, EquationSet b)
            {
                return b.priority - a.priority;
            }
        });
        queuePriority.addAll (parts);
        orderedParts = new ArrayList<EquationSet> ();
        for (EquationSet e = queuePriority.poll (); e != null; e = queuePriority.poll ())
        {
            orderedParts.add (e);
        }
    }

    public void setPriority (int value, EquationSet from)
    {
        // Prevent infinite recursion
        EquationSet p = from;
        while (p != null)
        {
            if (p == this) return;
            p = p.visited;
        }
        visited = from;

        // Ripple-increment priority
        if (value <= priority) return;
        priority = value++;  // note the post-increment here
        if (connectionBindings == null) return;
        for (ConnectionBinding c : connectionBindings)
        {
            EquationSet e = c.endpoint;
            if (e.container != container) continue;  // Don't exit the current equation set when setting priority.
            e.setPriority (value, this);
        }
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
            if (v.name.startsWith ("$up."))  // Probably not be a true $variable, just an up-reference.
            {
                if (container == null) return null;  // Unresolved! We can't go up any farther.
                v.name = v.name.substring (4);
                v.reference.resolution.add (container);
                return container.resolveEquationSet (v, create);
            }

            // $variables are always treated as local. For example, you would never want to inherit $n from a container!
            if (variables.contains (v)) return this;
            if (! create) return null;

            // Usually, the important $variables have already been created by addSpecials(),
            // so v is probably an unusual derivative or a user-defined variable
            // (which really shouldn't have a $ prefix).
            Variable cv = new Variable (v.name, v.order);
            add (cv);
            cv.reference = new VariableReference ();
            cv.reference.variable = cv;
            cv.equations = new TreeSet<EquationEntry> ();
            cv.assignment = v.assignment;  // hinted combiner type
            return this;
        }
        if (   v.name.endsWith (".$k")
            || v.name.endsWith (".$max")
            || v.name.endsWith (".$min")
            || v.name.endsWith (".$project")
            || v.name.endsWith (".$radius"))
        {
            return this;
        }

        // Check namespace references. These take precedence over variable names.
        String[] ns = v.name.split ("\\.", 2);
        if (ns.length > 1)
        {
            ConnectionBinding c = findConnection (ns[0]);
            if (c != null)
            {
                v.name = ns[1];
                v.reference.resolution.add (c);
                return c.endpoint.resolveEquationSet (v, create);
            }

            int vlength = v.name.length ();
            EquationSet down = findPartPrefix (v.name);
            if (down != null)
            {
                int length = down.name.length ();
                if (length == vlength) return null;  // This is a naked reference to a child part which has been flattened. Naked references are forbidden (see below for similar trap when dot is absent).

                v.name = v.name.substring (length + 1);  // skip the dot
                v.reference.resolution.add (down);
                return down.resolveEquationSet (v, create);
            }

            // Check if prefix matches this current equation set name.
            // This is equivalent to referring the variable up to our container, which in turn finds that the prefix matches us.
            // However, we don't want those extra steps in the resolution path.
            if (v.name.startsWith (name))  // prefix matches
            {
                int length = name.length ();
                if (length == vlength) return null;  // Naked reference to this equation set. Forbidden.
                if (v.name.charAt (length) == '.')  // Legitimate name path.
                {
                    v.name = v.name.substring (length + 1);
                    // Don't add this to the resolution path!
                    return resolveEquationSet (v, create);
                }
            }

            // Check for reference to variable in peer equation set.
            // Since this is effectively a down-reference into the peer, the peer must be a singleton.
            if (container != null)
            {
                int last = v.reference.resolution.size ();
                v.reference.resolution.add (container);
                EquationSet peer = container.resolveEquationSet (v, false);
                if (peer != null) return peer;
                while (v.reference.resolution.size () > last) v.reference.resolution.remove (last);  // Restore resolution path, since container didn't contain what we're looking for.
            }
        }

        // Check connections
        ConnectionBinding c = findConnection (v.name);
        if (c != null)
        {
            v.reference.resolution.add (c);  // same kind of resolution path as if we went into connected part, but ...
            v.name = "";                     // don't match any variables within the connected part
            v.addAttribute ("instance");
            return c.endpoint;               // and terminate as if we found a variable there
        }

        // Check variable names
        if (variables.contains (v)) return this;  // found it!

        // Guard against direct reference to a child part.
        // Similar to a "down" reference, except without a variable.
        EquationSet part = parts.floor (new EquationSet (v.name));
        if (part != null  &&  part.name.equals (v.name)) return null;

        if (create)
        {
            // Create a self-referencing variable with no equations
            // TODO: what attributes or equations should this have?
            Variable cv = new Variable (v.name, v.order);
            add (cv);
            cv.reference = new VariableReference ();
            cv.reference.variable = cv;
            cv.equations = new TreeSet<EquationEntry> ();
            cv.assignment = v.assignment;
            return this;
        }

        // Look up the containment hierarchy
        if (container == null) return null;  // unresolved!!
        v.reference.resolution.add (container);
        return container.resolveEquationSet (v, create);
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
            query.assignment = v.assignment;  // If referent is created in target eqset, then this hints the correct combiner type.
            EquationSet dest = resolveEquationSet (query, true);
            if (dest != null) query.reference.variable = dest.find (query);
            v.reference = query.reference;
            Variable target = v.reference.variable;
            if (target != v  &&  target != null)
            {
                v.addAttribute ("reference");
                target.addAttribute ("externalWrite");
                if (target.hasAttribute ("temporary"))
                {
                    //Backend.err.get ().println ("WARNING: Variable " + target.container.prefix () + "." + target.nameString () + " receives an external write, so cannot be temporary.");
                    target.removeAttribute ("temporary");
                }
                target.addDependencyOn (v);  // v.reference.variable receives an external write from v, and therefore its value depends on v
                v.reference.addDependencies (v);  // A variable depends on its connection bindings. This isn't necessary for execution, but does support analysis during GUI editing.
                target.container.referenced = true;
                if (   target.assignment != v.assignment
                    && ! (   (target.assignment == Variable.MULTIPLY  &&  v.assignment == Variable.DIVIDE)  // This line and the next say that * and / are compatible with each other, so ignore that case.
                          || (target.assignment == Variable.DIVIDE    &&  v.assignment == Variable.MULTIPLY)))
                {
                    Backend.err.get ().println ("WARNING: Reference " + prefix () + "." + v.nameString () + " has different combining operator than target variable (" + target.container.prefix () + "." + target.nameString () + "). Resolving in favor of higher-precedence operator.");
                    v.assignment = target.assignment = Math.max (v.assignment, target.assignment);
                }
            }
        }
    }

    public static class UnresolvedVariable
    {
        public String name;
        public String referencedBy;

        public UnresolvedVariable (String name, String referencedBy)
        {
            this.name         = name;
            this.referencedBy = referencedBy;
        }

        public static String pad (String name, int width)
        {
            String result = "";
            for (int i = name.length (); i < width; i++) result += " ";
            return name + result;
        }
    }

    /**
        Attach the appropriate Variable to each AccessVariable operator.
        Depends on results of:
            resolveConnectionBindings -- to follow connection references
            resolveLHS() -- to create indirect variables and thus avoid unnecessary failure of resolution
    **/
    public void resolveRHS () throws Exception
    {
        LinkedList<UnresolvedVariable> unresolved = new LinkedList<UnresolvedVariable> ();
        resolveRHS (unresolved);
        if (unresolved.size () > 0)
        {
            int width = 0;
            for (UnresolvedVariable uv : unresolved) width = Math.max (width, uv.name.length ());
            PrintStream ps = Backend.err.get ();
            ps.println ("Unresolved variables:");
            ps.println ("  " + UnresolvedVariable.pad ("(name)", width) + "\t(referenced by)");
            for (UnresolvedVariable uv : unresolved) ps.println ("  " + UnresolvedVariable.pad (uv.name, width) + "\t" + uv.referencedBy);
            throw new AbortRun ();
        }
    }

    public void resolveRHS (LinkedList<UnresolvedVariable> unresolved)
    {
        for (EquationSet s : parts)
        {
            s.resolveRHS (unresolved);
        }
    
        class Resolver implements Transformer
        {
            public Variable from;
            public LinkedList<UnresolvedVariable> unresolved;

            public String fromName ()
            {
                String result = from.container.prefix ();
                if (! result.isEmpty ()) result += ".";
                return result + from.nameString ();
            }

            public Operator transform (Operator op)
            {
                if (op instanceof AccessVariable)
                {
                    AccessVariable av = (AccessVariable) op;
                    Variable query = new Variable (av.getName (), av.getOrder ());
                    query.reference = new VariableReference ();
                    EquationSet dest = resolveEquationSet (query, false);
                    if (dest == null)
                    {
                        unresolved.add (new UnresolvedVariable (av.name, fromName ()));
                    }
                    else
                    {
                        query.reference.variable = dest.find (query);
                        if (query.reference.variable == null)
                        {
                            if (query.hasAttribute ("instance"))
                            {
                                // Configure reference to destination container itself.
                                query.reference.variable = query;  // Recycle the query variable as a pseudo target (one that doesn't actually exist in the container).
                                query.container = dest;
                                query.equations = new TreeSet<EquationEntry> ();
                                query.type = new Instance ();
                                query.readIndex = -2;  // Only for use by Internal backend. It's easier to set this here than to scan for "instance" variables in InternalBackendData.analyze().
                            }
                            else if (query.name.equals ("$count"))  // accountable endpoint
                            {
                                if (dest.accountableConnections == null) dest.accountableConnections = new TreeSet<AccountableConnection> ();
                                String alias = av.name.split ("\\.", 2)[0];
                                AccountableConnection ac = new AccountableConnection (EquationSet.this, alias);
                                if (! dest.accountableConnections.add (ac)) ac = dest.accountableConnections.floor (ac);
                                if (ac.count == null)
                                {
                                    // Create a fully-functional variable.
                                    // However, it never gets formally added to dest, because dest should never evaluate it.
                                    // Rather, it is maintained by the backend's connection system.
                                    ac.count = new Variable (prefix () + ".$count");
                                    ac.count.type = new Scalar (0);
                                    ac.count.container = dest;
                                    ac.count.equations = new TreeSet<EquationEntry> ();
                                    ac.count.reference = query.reference;
                                }
                                query.reference.variable = ac.count;
                            }
                            else
                            {
                                unresolved.add (new UnresolvedVariable (av.name, fromName ()));
                            }
                        }
                        else
                        {
                            Variable target = query.reference.variable;
                            from.addDependencyOn (target);
                            if (from.container != target.container)
                            {
                                target.addAttribute ("externalRead");
                                if (target.hasAttribute ("temporary"))
                                {
                                    //Backend.err.get ().println ("WARNING: Variable " + target.container.prefix () + "." + target.nameString () + " has an external read, so cannot be temporary.");
                                    target.removeAttribute ("temporary");
                                }
                            }
                        }
                    }
                    av.reference = query.reference;
                    av.reference.addDependencies (from);
                    return av;
                }
                if (op instanceof Split)
                {
                    Split split = (Split) op;
                    split.parts = new ArrayList<EquationSet> (split.names.length);
                    EquationSet self = from.reference.variable.container;
                    EquationSet family = self.container;  // Could be null, if self is top-level model.
                    for (String partName : split.names)
                    {
                        EquationSet part;
                        if (partName.equals (self.name)) part = self;  // This allows for $type in top-level model, where no higher container is available to search in.
                        else                             part = family.parts.floor (new EquationSet (partName));
                        if (part != null  &&  part.name.equals (partName))
                        {
                            split.parts.add (part);

                            Variable query = new Variable ("$type", 0);
                            Variable type = part.find (query);
                            if (type == null)
                            {
                                type = query;
                                part.add (type);
                                type.addAttribute ("externalWrite");  // double-buffer it
                                type.unit = AbstractUnit.ONE;
                                type.equations = new TreeSet<EquationEntry> ();
                                type.reference = new VariableReference ();
                                type.reference.variable = type;
                            }

                            if (type != from) type.addDependencyOn (from);
                        }
                        else
                        {
                            unresolved.add (new UnresolvedVariable (partName, fromName ()));
                        }
                    }
                    return split;
                }
                return null;
            }
        }
        Resolver resolver = new Resolver ();
        resolver.unresolved = unresolved;
    
        for (Variable v : variables)
        {
            resolver.from = v;
            v.transform (resolver);
        }
    }

    public void determineTraceVariableName ()
    {
        for (EquationSet s : parts)
        {
            s.determineTraceVariableName ();
        }

        class TraceVisitor implements Visitor
        {
            public Variable v;
            public boolean visit (Operator op)
            {
                if (op instanceof Output)
                {
                    ((Output) op).determineVariableName (v);
                    return false;
                }
                return true;
            }
        }
        TraceVisitor visitor = new TraceVisitor ();

        for (Variable v : variables)
        {
            visitor.v = v;
            v.visit (visitor);
        }
    }

    /**
        Render the equation set.
        Depends on results of: resolveConnectionBindings(), resolveLHS(), resolveRHS()
        @param showNamespace Indicates to add a prefix in front of each variable, showing where it resolves.
    **/
    public String dump (boolean showNamespace)
    {
        return dump (showNamespace, "");
    }

    public String dump (boolean showNamespace, String prefix)
    {
        Renderer renderer;
        if (showNamespace)
        {
            class Prefixer extends Renderer
            {
                public boolean render (Operator op)
                {
                    if (! (op instanceof AccessVariable)) return false;
                    AccessVariable av = (AccessVariable) op;
                    if (av.reference == null  ||  av.reference.variable == null)
                    {
                        result.append ("<unresolved!>" + av.name);
                    }
                    else
                    {
                        result.append ("<" + av.reference.variable.container.prefix () + ">" + av.reference.variable.nameString ());
                    }
                    return true;
                }
            }
            renderer = new Prefixer ();
        }
        else
        {
            renderer = new Renderer ();
        }

        renderer.result.append (prefix + name + "\n");
        prefix = prefix + " ";
        if (connectionBindings != null)
        {
            for (ConnectionBinding c : connectionBindings)
            {
                renderer.result.append (prefix + c.alias + " = ");
                EquationSet s = c.endpoint;
                if (showNamespace)
                {
                    renderer.result.append ("<");
                    if (s.container != null)
                    {
                        renderer.result.append (s.container.prefix ());
                    }
                    renderer.result.append (">");
                }
                renderer.result.append (s.name + "\n");
            }
        }
        for (Variable v : variables)
        {
            if (v.equations.size () > 0)  // If no equations, then this is an implicit variable, so no need to list here.
            {
                renderer.result.append (prefix + v.nameString ());
                renderer.result.append (" =" + v.combinerString ());
                if (v.equations.size () == 1)
                {
                    renderer.result.append (" ");
                    v.equations.first ().render (renderer);
                    renderer.result.append ("\n");
                }
                else
                {
                    renderer.result.append ("\n");
                    for (EquationEntry e : v.equations)
                    {
                        renderer.result.append (prefix + " ");
                        e.render (renderer);
                        renderer.result.append ("\n");
                    }
                }
            }
        }

        for (EquationSet e : parts)
        {
            renderer.result.append (e.dump (showNamespace, prefix));
        }

        return renderer.result.toString ();
    }

    public String dumpVariableAttributes ()
    {
        StringBuilder result = new StringBuilder ();
        dumpVariableAttributes (result);
        return result.toString ();
    }

    public void dumpVariableAttributes (StringBuilder result)
    {
        result.append (name + "\n");
        for (Variable v : variables)
        {
            String className = "";
            if (v.type != null) className = " " + v.type.getClass ().getSimpleName ();
            result.append ("  " + v.nameString () + " " + v.attributeString () + className + "\n");
        }
        for (EquationSet s : parts) s.dumpVariableAttributes (result);
    }

    /**
        Determines if this equation set has a fixed size of 1.
        @param strict false indicates to make an exception for the top-level part, allowing
        it to be a singleton even though it (most likely) uses $p to terminate simulation.
    **/
    public boolean isSingleton (boolean strict)
    {
        // No connections
        // The population size of a connection depends on other objects, so can't be a singleton.
        if (connectionBindings != null) return false;

        // Limit sources of structural dynamics
        // These tests are good heuristics, but they're actually too strict. Some relaxations would be:
        // * If $p is constant 1, then part won't die. (But then, why write that?)
        // * If $type always has exactly one instance of original part, then part remains a singleton. (Again, why write that?)
        if (find (new Variable ("$p")) != null  &&  (strict  ||  container != null)) return false;
        if (find (new Variable ("$type")) != null) return false;
        Variable n = new Variable ("$n", 0);
        Variable nn = variables.higher (n);
        if (nn != null  &&  nn.name.equals ("$n")) return false;  // higher orders means $n is dynamic

        n = find (n);
        if (n == null) return true;  // We only do more work if $n exists. Non-existent $n is the same as $n==1

        // check contents of $n
        if (n.assignment != Variable.REPLACE) return false;
        if (n.uses != null  &&  n.uses.size () > 0) return false;  // If $n depends on other variables, we won't be able to evaluate it now.
        if (n.equations.size () != 1) return false;
        EquationEntry ne = n.equations.first ();

        // Ideally we would evaluate the expression, but that's not possible when isSingleton() is called
        // before resolveRHS(). Instead, we do a simple check for constant 1.
        if (! ne.ifString.isEmpty ()  &&  ! ne.ifString.equals ("$init")) return false;  // Any condition besides $init indicates the potential to change during run.
        if (ne.expression == null) return true;  // Treat missing expression as 1.
        return ne.expression.getDouble () == 1;
    }

    public boolean isConnection ()
    {
        return connectionBindings != null;
    }

    /**
        Convert this equation set into an equivalent object where each included part with $n==1
        (and satisfying a few other conditions) is merged into its containing part.
        Equations with combiners (=+, =*, and so on) are joined together into one long equation
        with the appropriate operator.
        @param backend Prefix for metadata keys specific to the engine selected to execute this model.
        Where such keys exists, the parts should not be flattened.
    **/
    public void flatten (String backend)
    {
        TreeSet<EquationSet> temp = new TreeSet<EquationSet> (parts);
        for (final EquationSet s : temp)
        {
            s.flatten (backend);

            // Check if connection or endpoint. They must remain separate equation sets for code-generation purposes.
            if (s.connectionBindings != null) continue;
            if (s.connected) continue;

            // For similar reasons, if the part contains backend-related metadata, it should remain separate.
            if (s.metadata.child ("backend", backend) != null) continue;

            // Check if $n==1
            if (! s.isSingleton (true)) continue;
            s.variables.remove (new Variable ("$n", 0));  // We don't want $n in the merged set. (Because s is singleton, no higher orders of $n exist in it.)

            // Don't merge if there are any conflicting $variables.
            boolean conflict = false;
            for (Variable v : s.variables)
            {
                if (! v.name.startsWith ("$")  ||  v.name.startsWith ("$up")) continue;
                Variable d = find (v);
                if (d != null  &&  d.name.equals (v.name))  // for this match we don't care about order; that is, any differential order on either side causes a conflict
                {
                    conflict = true;
                    break;
                }
            }
            if (conflict) continue;

            // Merge

            final String prefix = s.name;
            parts.remove (s);

            //   Variables
            final TreeSet<String> names = new TreeSet<String> ();
            for (Variable v : s.variables) names.add (v.name);  // Naked name will match any order.

            class Prefixer implements Transformer
            {
                public Operator transform (Operator op)
                {
                    if (! (op instanceof AccessVariable)) return null;
                    AccessVariable av = (AccessVariable) op;
                    if (av.name.startsWith ("$"))
                    {
                        if (av.name.startsWith ("$up."))
                        {
                            av.name = av.name.substring (4);
                        }
                        // otherwise, don't modify references to $variables
                    }
                    else if (names.contains (av.getName ()))  // Naked name, so matches any order.
                    {
                        av.name = prefix + "." + av.name;  // Full name, including order.
                    }
                    return av;
                }
            }
            Prefixer prefixer = new Prefixer ();

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
                v.transform (prefixer);
                Variable v2 = find (v);
                if (v2 == null)
                {
                    add (v);
                }
                else
                {
                    v2.flattenExpressions (v);
                }
            }

            //   Parts
            for (EquationSet sp : s.parts)
            {
                sp.container = this;  // s was the former container for sp, but s is going away
                sp.name = prefix + "." + sp.name;
                parts.add (sp);
            }

            //   Metadata
            if (s.metadata.size () > 0) metadata.set (s.metadata, prefix);

            //   Dependent connections (paths that pass through s)
            if (s.dependentConnections != null)
            {
                if (dependentConnections == null) dependentConnections = new ArrayList<ConnectionBinding> ();
                for (ConnectionBinding c : s.dependentConnections)
                {
                    int i = c.resolution.indexOf (s);  // By construction, this element must exist.
                    c.resolution.set (i, this);  // replace s with this
                    if (i+1 < c.resolution.size ()  &&  c.resolution.get (i+1) == this) c.resolution.remove (i+1);
                    if (i   > 0                     &&  c.resolution.get (i-1) == this) c.resolution.remove (i-1);
                    if (! dependentConnections.contains (c)) dependentConnections.add (c);
                }
            }
        }
    }

    /**
        Assembles list of all variables that can be used in an output expression.
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
            if (v.hasAttribute ("reference")) continue;

            String defaultValue = "";
            if (v.equations.size () > 0) defaultValue = v.equations.first ().expression.render ();
            result.addParameter (new Parameter (v.nameString (), defaultValue));
        }
        for (EquationSet s : parts)
        {
            result.addSubdomain (s.getOutputParameters ());
        }

        return result;
    }

    public void addGlobalConstants () throws Exception
    {
        String key = AppData.state.getOrDefault ("Constants", "General", "constants");
        MNode constants = AppData.models.child (key);
        if (constants == null) return;
        for (MNode c : constants)
        {
            String value = c.get ();
            if (value.isEmpty ()) continue;
            Variable v = new Variable (c.key (), 0);
            if (add (v))
            {
                v.addAttribute ("constant");
                EquationEntry e = new EquationEntry (v, "");
                e.expression = Operator.parse (value);
                v.add (e);
            }
        }
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

        // Force phase indicators to exist.
        setConnect (0);
        setInit    (0);
        Variable v = new Variable ("$live", 0);  // $live does not require a set method, so create it directly
        if (add (v))
        {
            v.unit = AbstractUnit.ONE;
            v.addAttribute ("constant");  // default. Actual values should be set by setAttributeLive()
            EquationEntry e = new EquationEntry (v, "");
            e.expression = new Constant (new Scalar (1));
            e.expression.unit = AbstractUnit.ONE;
            v.add (e);
        }
        else
        {
            v = find (v);
            EquationEntry e = v.equations.first ();
            if (! e.expression.isScalar ())
            {
                Backend.err.get ().println ("Illegal assignment to " + prefix () + ".$live");
                throw new AbortRun ();
            }
        }

        v = new Variable ("$t", 0);
        if (add (v))
        {
            v.unit = UnitValue.seconds;
            v.equations = new TreeSet<EquationEntry> ();
        }
        else
        {
            v = find (v);
            if (! v.equations.isEmpty ())
            {
                Backend.err.get ().println ("Illegal assignment to " + prefix () + ".$t");
                throw new AbortRun ();
            }
        }

        v = new Variable ("$t", 1);  // $t'
        if (add (v))
        {
            v.unit = UnitValue.seconds;  // seconds per cycle, but cycle is not a unit
            v.equations = new TreeSet<EquationEntry> ();
        }

        if (container == null)           // top-level model
        {
            v = new Variable ("$p", 0);  // must have a termination condition
            if (add (v))                 // but it doesn't
            {
                v.unit = AbstractUnit.ONE;
                try
                {
                    String duration = metadata.getOrDefault ("1s", "duration");  // limit sim time to 1 second, if not otherwise specified
                    v.add (new EquationEntry ("$t<" + duration));
                }
                catch (Exception parseError)
                {
                    try {v.add (new EquationEntry ("$t<1s"));}
                    catch (Exception parseError2) {} // This exception should never happen. We simply want to silence Java about it.
                }
            }
        }

        if (connectionBindings == null  ||  connected)  // Either a compartment, or a connection that also happens to be the endpoint of another connection.
        {
            boolean singleton = isSingleton (true);

            v = new Variable ("$index", 0);
            if (add (v))
            {
                v.unit = AbstractUnit.ONE;
                v.equations = new TreeSet<EquationEntry> ();
                if (singleton)
                {
                    v.addAttribute ("constant");
                    EquationEntry e = new EquationEntry (v, "");
                    e.expression = new Constant (new Scalar (0));
                    e.expression.unit = AbstractUnit.ONE;
                    v.add (e);
                }
                else
                {
                    v.addAttribute ("initOnly");  // most backends will set $index before processing init equations
                }
            }
            else
            {
                v = find (v);
            }
            if (connected  &&  ! singleton) v.addUser (this);  // Force $index to exist for connection targets. Used for anti-indexing into list of instances.
        }

        if (connectionBindings == null)
        {
            v = new Variable ("$n", 0);
            if (add (v))
            {
                v.unit = AbstractUnit.ONE;
                v.addAttribute ("constant");  // default. Actual values set by client code.
                EquationEntry e = new EquationEntry (v, "");
                e.expression = new Constant (new Scalar (1));
                e.expression.unit = AbstractUnit.ONE;
                v.add (e);
            }
        }
    }

    /**
        Remove any variables (particularly $variables) that are not referenced by some
        equation. These values do not input to any other calculation, and they are not
        displayed. Therefore they are a waste of time and space.
        Depends on results of:
            resolveLHS(), resolveRHS(), fillIntegratedVariables()
            addSpecials() -- so we can remove any $variables added unnecessarily
            deteremineTraceVariableName() -- in case it establishes a dependency on $index
    **/
    public void removeUnused ()
    {
        while (removeUnusedEval ());
    }

    public boolean removeUnusedEval ()
    {
        boolean changed = false;
        for (EquationSet s : parts)
        {
            if (s.removeUnusedEval ()) changed = true;
        }

        TreeSet<Variable> temp = new TreeSet<Variable> (variables);
        for (Variable v : temp)
        {
            if (v.hasUsers ()  ||  v.hasAttribute ("externalWrite")) continue;

            // Even if a $variable has no direct users, we must respect any statements about it.
            // However, remove $index if it was created constant by addSpecials().
            if (v.equations.size () > 0  &&  (v.name.startsWith ("$")  ||  v.name.contains (".$"))  &&  ! v.name.equals ("$index")) continue;

            // Scan AST for any special output functions.
            boolean output = false;
            for (EquationEntry e : v.equations)
            {
                if (e.expression.isOutput ())
                {
                    output = true;
                    break;
                }
            }
            if (output)  // outputs must always exist!
            {
                v.addAttribute ("dummy");  // we only get the "dummy" attribute when we are not otherwise referenced (usedBy==false)
                continue;
            }

            v.removeDependencies ();
            variables.remove (v);
            changed = true;
        }

        return changed;
    }

    /**
        Add a variable for the lower-order integrated form of each derivative, if it does not already exist.
        Depends on results of: findLHS() -- to create any implicitly-defined derivatives
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
                if (ns.length > 1  &&  findConnection (ns[0]) != null) continue;
            }

            // This may seem inefficient, but in general there will only be one order to process.
            int lowestOrder = 0;
            if (v.name.equals ("$t")) lowestOrder = 1;  // don't let $t depend on $t', as this is handled entirely within the simulator
            Variable last = v;
            for (int o = v.order - 1; o >= lowestOrder; o--)
            {
                Variable vo = new Variable (v.name, o);
                Variable found = find (vo);
                if (found == null)
                {
                    add (vo);
                    vo.equations = new TreeSet<EquationEntry> ();
                    vo.reference = new VariableReference ();
                    vo.reference.variable = vo;
                    found = vo;
                }
                found.addDependencyOn (last);
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
            init = new Variable ("$init", 0);
            init.unit = AbstractUnit.ONE;
            init.addAttribute ("constant");  // TODO: should really be "initOnly", since it changes value during (at the end of) the init cycle.
            EquationEntry e = new EquationEntry (init, "");
            e.expression = new Constant (new Scalar (value));
            e.expression.unit = AbstractUnit.ONE;
            init.add (e);
            add (init);
        }
        else
        {
            EquationEntry e = init.equations.first ();
            if (! e.expression.isScalar ())
            {
                Backend.err.get ().println ("Illegal assignment to " + prefix () + ".$init");
                throw new AbortRun ();
            }
            ((Scalar) ((Constant) e.expression).value).value = value;
        }
    }

    public boolean getInit ()
    {
        Variable init = find (new Variable ("$init"));
        if (init == null) return false;
        EquationEntry e = init.equations.first ();
        return ((Scalar) ((Constant) e.expression).value).value != 0;
    }

    public void setConnect (float value)
    {
        Variable connect = find (new Variable ("$connect"));
        if (connect == null)
        {
            connect = new Variable ("$connect", 0);
            connect.unit = AbstractUnit.ONE;
            connect.addAttribute ("constant");
            EquationEntry e = new EquationEntry (connect, "");
            e.expression = new Constant (new Scalar (value));
            e.expression.unit = AbstractUnit.ONE;
            connect.add (e);
            add (connect);
        }
        else
        {
            EquationEntry e = connect.equations.first ();
            if (! e.expression.isScalar ())
            {
                Backend.err.get ().println ("Illegal assignment to " + prefix () + ".$connect. Try $p=<expression>@$connect instead.");
                throw new AbortRun ();
            }
            ((Scalar) ((Constant) e.expression).value).value = value;
        }
    }

    public boolean getConnect ()
    {
        Variable connect = find (new Variable ("$connect"));
        if (connect == null) return false;
        EquationEntry e = connect.equations.first ();
        return ((Scalar) ((Constant) e.expression).value).value != 0;
    }

    /**
        Scans all conditional forms of $type, and stores the patterns in the splits field.
        Depends on results of: resolveLHS(), resolveRHS()
    **/
    public void collectSplits ()
    {
        for (EquationSet s : parts)
        {
            s.collectSplits ();
        }

        if (splits       == null) splits       = new ArrayList<ArrayList<EquationSet>> ();
        if (splitSources == null) splitSources = new HashSet<EquationSet> ();
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
                if (! (e.expression instanceof Split))
                {
                    Backend.err.get ().println ("Unexpected expression for $type");
                    throw new Backend.AbortRun ();
                }
                ArrayList<EquationSet> split = ((Split) e.expression).parts;
                if (! container.splits.contains (split))
                {
                    container.splits.add (split);
                    for (EquationSet t : split)
                    {
                        if (t.splitSources == null) t.splitSources = new HashSet<EquationSet> ();
                        t.splitSources.add (container);
                    }
                }
            }
        }
    }

    public class Conversion
    {
        public EquationSet from;
        public EquationSet to;
        public Conversion (EquationSet from, EquationSet to)
        {
            this.from = from;
            this.to   = to;
        }
    }

    /**
        Convenience function to assemble any splits that occur within this container
        into (from,to) pairs for type conversion.
        Depends on results of: collectSplits()
    **/
    public Set<Conversion> getConversions ()
    {
        Set<Conversion> result = new TreeSet<Conversion> ();
        for (EquationSet p : parts)
        {
            for (ArrayList<EquationSet> split : p.splits)
            {
                for (EquationSet s : split)
                {
                    result.add (new Conversion (p, s));
                }
            }
        }
        return result;
    }

    /**
        Convenience function to assemble a list of types that this equation set
        can change into. This is somewhat like the inner loop of getConversions().
    **/
    public Set<EquationSet> getConversionTargets ()
    {
        Set<EquationSet> result = new TreeSet<EquationSet> ();
        for (ArrayList<EquationSet> split : splits)
        {
            for (EquationSet s : split)
            {
                result.add (s);
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
        flags are set to indicate the causes.
        Depends on results of: addSpecials(), findConstants(), collectSplits()
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
                if (e.condition != null  &&  ! (e.condition instanceof Constant))
                {
                    lethalN = true;
                    break;
                }
                if (! (e.expression instanceof Constant))
                {
                    lethalN = true;
                    break;
                }
            }
        }

        // Determine if $p has an assignment less than 1
        Variable p = find (new Variable ("$p"));
        if (p != null)
        {
            // Determine if any equation is capable of setting $p to something besides 1
            ReplacePhaseIndicators replacePhase = new ReplacePhaseIndicators ();
            for (EquationEntry e : p.equations)
            {
                if (e.expression.isScalar ()  &&  e.expression.getDouble () >= 1) continue;

                // Now we have an equation that evaluates to something other than 1.
                // If this occurs anywhere but connect, then $p is lethal.
                if (e.condition != null)
                {
                    replacePhase.init = 1;
                    Operator test = e.condition.deepCopy ().transform (replacePhase).simplify (p);
                    if (test.isScalar ()  &&  test.getDouble () == 0)  // Does not fire during init phase
                    {
                        replacePhase.init = 0;
                        test = e.condition.deepCopy ().transform (replacePhase).simplify (p);
                        if (test.isScalar ()  &&  test.getDouble () == 0) continue;  // Does not fire during update phase
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
            if (live != null) live.addUser (this);
        }

        if (connectionBindings != null)
        {
            for (ConnectionBinding c : connectionBindings)
            {
                EquationSet s = c.endpoint;
                if (s.canDie ())
                {
                    Variable live = s.find (new Variable ("$live"));
                    if (live != null) live.addUser (this);
                    if (! lethalConnection) somethingChanged = true;
                    lethalConnection = true;
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
        return lethalN  ||  lethalP  ||  lethalType  ||  lethalConnection  ||  lethalContainer;
    }

    /**
        Determine if population can grow by a means other than setting $n, specifically by
        $type splits that produce more than 1 offspring of our current type.
    **/
    public boolean canGrow ()
    {
        // Do we have a $type split that produces two or more offspring of our own type?
        for (ArrayList<EquationSet> split : splits)
        {
            int count = 0;
            for (EquationSet s : split)
            {
                if (s == this) count++;  // Direct object identity is OK here.
            }
            if (count >= 2) return true;
        }

        // Are we the target of a $type split in another part which produces at least one offspring of this type?
        int count = splitSources.size ();
        return  count > 1  ||  count == 1  &&  ! splitSources.contains (this);
    }

    public boolean canGrow (EquationSet target)
    {
        return false;
    }

    /**
        Determines the attributes of $live, based on whether other parts depend on it.
        $live is either constant, accessor, or stored.
        constant (the default) if we can't die or no part depends on us.
        accessor               if we only die in response to the death of our container or a referenced part.
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

            if (canDie ()  &&  live.hasUsers ())
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
                live.addAttribute ("constant");
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
        determineTypesInit ();
        while (determineTypesEval ()) {}
    }

    public void determineTypesInit ()
    {
        for (EquationSet s : parts)
        {
            s.determineTypesInit ();
        }

        for (Variable v : variables)
        {
            if (v.hasAttribute ("constant"))
            {
                v.type = ((Constant) v.equations.first ().expression).value;
            }
            else if (v.name.equals ("$xyz")  ||  v.name.endsWith (".$project"))  // are there always dots before $project?
            {
                v.type = new MatrixDense (3, 1);
            }
            else if (v.name.equals ("$init")  ||  v.name.equals ("$live")  ||  v.name.equals ("$p")  ||  v.name.equals ("$n")  ||  (v.name.equals ("$t")  &&  v.order == 1))
            {
                v.type = new Scalar (1);
            }
            else
            {
                v.type = new Scalar (0);
            }
        }
    }

    public boolean determineTypesEval ()
    {
        boolean changed = false;
        for (final Variable v : variables)
        {
            if (v.hasAttribute ("constant")) continue;

            // Don't change type for certain $variables.
            if (v.name.equals ("$init")  ||  v.name.equals ("$live")  ||  v.name.equals ("$p")  ||  v.name.equals ("$n")  ||  (v.name.equals ("$t")  &&  v.order == 1)) continue;

            Type value = null;
            Instance instance = new Instance ()
            {
                // all AccessVariable objects will reach here first, and get back the Variable.type field
                public Type get (VariableReference r) throws EvaluationException
                {
                    return r.variable.type;
                }
            };
            for (EquationEntry e : v.equations)
            {
                // We should only need to evaluate one equation, since they should all return the same type.
                // However, scanning them all increase our chance of propagating correct values.
                try
                {
                    Type temp = e.expression.eval (instance);
                    if (value == null  ||  temp.betterThan (value)) value = temp;
                }
                catch (Exception x) {}  // This can happen due to type mismatch, and may be a temporary condition while values are propagating.
            }

            if (v.derivative != null)
            {
                if (value == null)
                {
                    value = v.derivative.type;
                }
                else if (v.derivative.type == null  ||  value.betterThan (v.derivative.type))
                {
                    v.derivative.type = value;
                    changed = true;
                }
            }

            if (value == null)
            {
                value = v.reference.variable.type;
            }
            else if (v.reference.variable.type == null  ||  value.betterThan (v.reference.variable.type))
            {
                v.reference.variable.type = value;
                changed = true;
            }

            if (v != v.reference.variable  &&  value != null  &&  value.betterThan (v.type))
            {
                v.type = value;
                changed = true;
            }
        }
        for (EquationSet s : parts)
        {
            if (s.determineTypesEval ()) changed = true;
        }
        return changed;
    }

    public void determineExponents (double duration)
    {
        ExponentVisitor ev = new ExponentVisitor (duration);
        determineExponentsInit (ev);
        int limit = ev.depth * 2 + 2;  // One cycle for each variable to get initial exponent, and another cycle for each var to influence itself, plus a couple more for good measure. 

        boolean all = false;
        List<Variable> overflows = new ArrayList<Variable> ();
        while (true)
        {
            System.out.println ("-----------------------------------------------------------------");
            System.out.println ("top of loop " + all + " " + limit);
            int exponentTime = ev.updateTime ();
            boolean changed = ev.updateInputs ();
            boolean finalPass = limit-- == 0;
            if (determineExponentsEval (exponentTime, finalPass, overflows)) changed = true;
            if (finalPass  ||  ! changed)
            {
                PrintStream err = Backend.err.get ();
                if (overflows.size () > 0)  // Variable.exponent changed during the final cycle.
                {
                    err.println ("WARNING: Dubious magnitude. Add hint (median=median_absolute_value).");
                    for (Variable v : overflows)
                    {
                        err.println ("  " + v.container.prefix () + "." + v.nameString ());
                    }
                }
                else if (changed)  // Some equation changed, but it did not produce a change to Variable.exponent.
                {
                    err.println ("WARNING: Fixed-point analysis did not converge.");
                }
                break;
            }
            dumpExponents ();
        }
        determineExponentNext ();
        dumpExponents ();

        // List results of analysis to error stream
        PrintStream ps = Backend.err.get ();
        ps.println ("Results of fixed-point analysis (expected median, reported as 10^n):");
        dumpMedians (ps);
    }

    /**
        Stores lists of objects needed for global agreement on time exponents.
    **/
    public static class ExponentVisitor
    {
        public int depth;  // Largest number of equations in any equation set. It's unlikely that any dependency cycle will exceed this.
        public int exponentTime;  // of overall simulation
        public List<Variable> dt = new ArrayList<Variable> ();  // All distinct occurrences of $t' in the model (zero or one per equation set)
        public HashMap<Object,ArrayList<Input>> inputs = new HashMap<Object,ArrayList<Input>> ();  // Inputs that have "time" flag must agree on exponentTime.

        public ExponentVisitor (double duration)
        {
            if (duration == 0) exponentTime = Operator.UNKNOWN;
            else               exponentTime = (int) Math.floor (Math.log (duration) / Math.log (2));
        }

        public int updateTime ()
        {
            // If duration was specified, simply return it.
            if (exponentTime != Operator.UNKNOWN) return exponentTime;

            int min = Integer.MAX_VALUE;
            for (Variable v : dt)
            {
                for (EquationEntry e : v.equations)
                {
                    if (e.expression != null  &&  e.expression.exponent != Operator.UNKNOWN)
                    {
                        min = Math.min (min, e.expression.centerPower ());
                    }
                }
            }
            if (min == Integer.MAX_VALUE) return 0;  // If no value of $t' has been set yet, estimate duration as 1s, and $t has exponent=0.

            return min + 20;  // +20 allows one million minimally-sized timesteps, each with 10 bit resolution
        }

        public boolean updateInputs ()
        {
            boolean changed = false;
            for (ArrayList<Input> list : inputs.values ())
            {
                int count = 0;
                int pow = 0;
                for (Input i : list)
                {
                    Operator operand1 = i.operands[1];
                    if (operand1.exponent != Operator.UNKNOWN)
                    {
                        count++;
                        pow += operand1.exponent;
                    }
                }
                if (count > 0)
                {
                    pow /= count;
                    for (Input i : list)
                    {
                        if (i.exponentTime != pow)
                        {
                            i.exponentTime = pow;
                            changed = true;
                        }
                    }
                }
            }
            return changed;
        }
    }

    public void determineExponentsInit (ExponentVisitor ev)
    {
        ev.depth = Math.max (ev.depth, variables.size ());

        for (Variable v : variables)
        {
            // Collect all $t'
            Variable r = v.reference.variable;
            if (r.name.equals ("$t")  &&  r.order == 1  &&  ! ev.dt.contains (r)) ev.dt.add (r);

            // Set v as the parent of all its equations
            for (EquationEntry e : v.equations)
            {
                if (e.expression != null) e.expression.parent = v;
            }

            // Collect all input() calls which use "time" mode.
            v.visit (new Visitor ()
            {
                public boolean visit (Operator op)
                {
                    if (op instanceof Input)
                    {
                        Input i = (Input) op;
                        if (i.operands.length >= 4)
                        {
                            if (i.operands[3].getString ().contains ("time"))
                            {
                                Object key = null;
                                Operator path = i.operands[0];
                                if (path instanceof Constant)
                                {
                                    key = path.getString ();
                                }
                                else if (path instanceof AccessVariable)
                                {
                                    key = ((AccessVariable) path).reference.variable;
                                }
                                // TODO: should also find a way to turn more complicated expressions into keys.
                                // For example, "bob"+$index should compare equal if it appears in two separate
                                // input() statements.
                                if (key != null)
                                {
                                    ArrayList<Input> list = ev.inputs.get (key);
                                    if (list == null)
                                    {
                                        list = new ArrayList<Input> ();
                                        ev.inputs.put (key, list);
                                    }
                                    list.add (i);
                                }
                            }
                        }
                    }
                    return true;
                }
            });
        }
        for (EquationSet s : parts) s.determineExponentsInit (ev);
    }

    public boolean determineExponentsEval (int exponentTime, boolean finalPass, List<Variable> overflows)
    {
        boolean changed = false;
        for (int i = ordered.size () - 1; i >= 0; i--)  // Process variables in reverse of dependency order, to maximize propagation of information in each pass.
        {
            Variable v = ordered.get (i);
            int centerLast   = v.center;
            int exponentLast = v.exponent;
            if (v.determineExponent (exponentTime))
            {
                changed = true;
                System.out.println ("  " + v.container.prefix () + "." + v.nameString ());
                if (finalPass)
                {
                    v.center = centerLast;
                    if (v.exponent != exponentLast)
                    {
                        v.exponent = exponentLast;
                        if (! overflows.contains (v)) overflows.add (v);
                    }
                }
            }
        }
        for (EquationSet s : parts)
        {
            if (s.determineExponentsEval (exponentTime, finalPass, overflows)) changed = true;
        }
        return changed;
    }

    public void determineExponentNext ()
    {
        for (EquationSet s : parts) s.determineExponentNext ();
        for (Variable v : variables) v.determineExponentNext ();
    }

    /**
        For debugging fixed-point analysis
    **/
    public void dumpExponents ()
    {
        for (Variable v : variables) v.dumpExponents ();
        for (EquationSet s : parts) s.dumpExponents ();
    }

    public void dumpMedians (PrintStream ps)
    {
        double b2d = Math.log (10) / Math.log (2);  // bits per decimal digit
        for (Variable v : variables)
        {
            if (v.hasAttribute ("dummy")) continue;

            // Convert center power to an approximate decimal value.
            int centerPower = v.exponent - Operator.MSB + v.center;
            int base10 = (int) Math.floor (centerPower / b2d);

            String fullName = prefix ();
            if (! fullName.isEmpty ()) fullName += ".";
            fullName += v.nameString ();

            ps.println ("  " + base10 + "\t" + fullName);
        }
        for (EquationSet s : parts) s.dumpMedians (ps);
    }

    /**
        Convenience function to implement user preferences about dimension checking during compilation.
        Depends on results of: resolveLHS, resolveRHS -- All variable references must be resolved.
        @throws AbortRun if an inconsistency is found and user wants this to be fatal.
    **/
    public void checkUnits () throws AbortRun
    {
        String dimension = AppData.state.getOrDefault ("Warning", "General", "dimension");
        if (! dimension.equals ("Warning")  &&  ! dimension.equals ("Error")) return;  // "Don't check"

        determineUnits ();
        LinkedList<String> mismatches = new LinkedList<String> ();
        checkUnitsEval (mismatches);
        if (mismatches.size () > 0)
        {
            boolean error = dimension.equals ("Error");
            PrintStream err = Backend.err.get ();
            err.println ((error ? "ERROR" : "WARNING") + ": Inconsistent dimensions. For each variable, this report shows the mismatched dimensions around the first offending operator.");
            for (String m : mismatches) err.println (m);
            if (error) throw new AbortRun ();
        }
    }

    public void determineUnits ()
    {
        int depth = determineUnitsDepth () * 2 + 2;  // See comments on determineExponents(). Similar reasoning on depth limit applies here.
        for (int i = 0; i < depth; i++)
        {
            if (! determineUnitsEval ()) break;
        }
    }

    public int determineUnitsDepth ()
    {
        int result = 0;
        for (EquationSet s : parts) result = Math.max (result, s.determineUnitsDepth ());
        result = Math.max (result, variables.size ());
        return result;
    }

    public boolean determineUnitsEval ()
    {
        boolean changed = false;
        for (EquationSet s : parts)
        {
            if (s.determineUnitsEval ()) changed = true;
        }
        for (Variable v : variables)
        {
            try
            {
                if (v.determineUnit (false)) changed = true;
            }
            catch (Exception e)
            {
                return false;  // An exception during this pass is fatal. Try to stop as soon as possible.
            }
        }
        return changed;
    }

    public void checkUnitsEval (LinkedList<String> mismatches)
    {
        for (EquationSet s : parts) s.checkUnitsEval (mismatches);
        for (Variable v : variables)
        {
            try
            {
                v.determineUnit (true);
            }
            catch (Exception error)
            {
                String name = prefix ();
                if (! name.isEmpty ()) name += ".";
                name += v.nameString ();
                mismatches.add ("  " + name + ": " + error.getMessage ());
            }
        }
    }

    /**
        Set initial value in Variable.type, so we can use it as backup when stored value is null.
    **/
    public void clearVariables ()
    {
        for (EquationSet p : parts) p.clearVariables ();
        for (Variable v : variables)
        {
            if (! v.hasAttribute ("constant"))
            {
                if (v.name.equals ("$p")  ||  v.name.equals ("$live"))
                {
                    v.type = new Scalar (1);
                }
                else
                {
                    v.type = v.type.clear ();
                }
            }
        }
    }

    /**
        Infers length of simulation based on contents of equation set, particularly
        an expression for top-level $p that involves $t.
        Depends on results of: determineTypes() -- to set up Variable.type member with usable values
    **/
    public void determineDuration ()
    {
        Variable p = find (new Variable ("$p", 0));
        if (p != null  &&  p.equations.size () == 1)
        {
            Operator expression = p.equations.first ().expression;
            Operator variable = null;
            Operator value    = null;
            if (expression instanceof LT  ||  expression instanceof LE)  // only true if expression is not null
            {
                OperatorBinary comparison = (OperatorBinary) expression;
                variable = comparison.operand0;
                value    = comparison.operand1;
            }
            else if (expression instanceof GT  ||  expression instanceof GE)
            {
                OperatorBinary comparison = (OperatorBinary) expression;
                variable = comparison.operand1;
                value    = comparison.operand0;
            }

            if (variable instanceof AccessVariable  &&  ((AccessVariable) variable).name.equals ("$t"))
            {
                Instance instance = new Instance ()
                {
                    // all AccessVariable objects will reach here first, and get back the Variable.type field
                    public Type get (VariableReference r) throws EvaluationException
                    {
                        return r.variable.type;
                    }
                };
                Type result = value.eval (instance);
                if (result instanceof Scalar) metadata.set (((Scalar) result).value, "duration");
            }
        }
    }

    /**
        @param attribute The string to add to the tags associated with each given variable.
        @param connection Tri-state: 1 = must be a connection, -1 = must be a compartment, 0 = can be either one
        @param withOrder Restricts name matching to exactly the same order of derivative,
        that is, how many "prime" marks are appended to the variable name.
        When false, matches any variable with the same base name.
        @param names A set of variable names to search for and tag.
    **/
    public void addAttribute (String attribute, int connection, boolean withOrder, String... names)
    {
        for (EquationSet s : parts)
        {
            s.addAttribute (attribute, connection, withOrder, names);
        }

        if (connectionBindings == null)
        {
            if (connection == 1) return;
        }
        else
        {
            if (connection == -1) return;
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
        As part of the process, removes arithmetic operations that have no effect.
        Depends on results of:
            resolveRHS() -- so that named constants can be found during evaluation
    **/
    public void findConstants ()
    {
        while (findConstantsEval ());
    }

    protected boolean findConstantsEval ()
    {
        boolean changed = false;
        for (EquationSet s : parts)
        {
            if (s.findConstantsEval ()) changed = true;
        }

        for (Variable v : variables)
        {
            if (v.simplify ()) changed = true;

            // Check if we have a constant
            if (v.hasAttribute ("constant")) continue;  // If this already has a "constant" tag, it was specially added so presumably correct.
            if (v.hasAttribute ("externalWrite")) continue;  // Regardless of the local math, a variable that gets written is not constant.
            if (v.equations.size () != 1) continue;
            EquationEntry e = v.equations.first ();
            if (e.condition != null) continue;
            if (e.expression instanceof Constant)
            {
                v.addAttribute ("constant");
                changed = true;
            }
        }
        return changed;
    }

    /**
        Check for special variables that we wish not to store in connections.
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
            if (connectionBindings != null  &&  v.order == 0)
            {
                if (v.name.equals ("$p"))
                {
                    if (! v.hasAny ("externalRead", "externalWrite")  &&  v.derivative == null)
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
        }
    }

    /**
        Populates the order field with the sequence of variable evaluations that minimizes
        the need for buffering. If there are no cyclic dependencies, then this problem can
        be solved exactly. If there are cycles, then this method uses a simple heuristic:
        prioritize variables with the largest number of dependencies.
        Depends on results of: resolveRHS(), findTemporary(),
                               addSpecials() -- to put $variables in the correct order along with everything else
    **/
    public void determineOrder ()
    {
        for (EquationSet p : parts) p.determineOrder ();

        // Reset variables for analysis
        ordered = new ArrayList<Variable> ();
        for (Variable v : variables)
        {
            v.before   = new ArrayList<Variable> ();
            v.priority = 0;
        }

        // Determine order constraints for each variable separately
        for (Variable v : variables) v.setBefore (false);

        // Assign depth in dependency tree, processing variables with the most ordering constraints first
        PriorityQueue<Variable> queueDependency = new PriorityQueue<Variable> (variables.size (), new Comparator<Variable> ()
        {
            public int compare (Variable a, Variable b)
            {
                return b.before.size () - a.before.size ();
            }
        });
        queueDependency.addAll (variables);
        for (Variable v = queueDependency.poll (); v != null; v = queueDependency.poll ())
        {
            v.setPriority (1, null);
        }

        // Assemble dependency tree into flat list
        PriorityQueue<Variable> queuePriority = new PriorityQueue<Variable> (variables.size (), new Comparator<Variable> ()
        {
            public int compare (Variable a, Variable b)
            {
                return a.priority - b.priority;
            }
        });
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
            if (v.uses == null) continue;
            for (Variable u : v.uses.keySet ())
            {
                if (   u.container == this  // must be in same equation set for order to matter
                    && ! (u.name.equals (v.name)  &&  u.order == v.order + 1)  // must not be my derivative
                    && ! u.hasAttribute ("temporary")  // temporaries follow the opposite rule on ordering, so don't consider them here
                    &&  ordered.indexOf (u) < index)  // and finally, is it actually ahead of me in the ordering?
                {
                    Backend.err.get ().println ("Cyclic dependency: " + v.fullName () + " with " + u.name);
                    u.addAttribute ("cycle");  // must be buffered; otherwise we will get the "after" value rather than "before"
                }
            }
        }
    }

    /**
        Special version of determineOrder() that treats all variables like temporaries,
        so that their order maximizes propagation of information.
    **/
    public static void determineOrderInit (List<Variable> list)
    {
        if (list.isEmpty ()) return;

        for (Variable v : list)
        {
            v.before   = new ArrayList<Variable> ();
            v.priority = 0;
        }
        for (Variable v : list) v.setBefore (true);

        PriorityQueue<Variable> queueDependency = new PriorityQueue<Variable> (list.size (), new Comparator<Variable> ()
        {
            public int compare (Variable a, Variable b)
            {
                return b.before.size () - a.before.size ();
            }
        });
        queueDependency.addAll (list);
        for (Variable v = queueDependency.poll (); v != null; v = queueDependency.poll ())
        {
            v.setPriority (1, null);
        }

        PriorityQueue<Variable> queuePriority = new PriorityQueue<Variable> (list.size (), new Comparator<Variable> ()
        {
            public int compare (Variable a, Variable b)
            {
                return a.priority - b.priority;
            }
        });
        queuePriority.addAll (list);
        list.clear ();
        for (Variable v = queuePriority.poll (); v != null; v = queuePriority.poll ()) list.add (v);
    }

    public void simplify (String phase, List<Variable> list)
    {
        simplify (phase, list, null);
    }

    /**
        Optimizes a given subset of variables with the assumption that specified phase indicator is true.
        Starts by replacing the variables with deep copies so that any changes do not
        damage the original equation set.
    **/
    public void simplify (String phase, List<Variable> list, Variable bless)
    {
        Variable.deepCopy (list);
        if (bless != null)
        {
            int i = list.indexOf (bless);
            if (i >= 0) list.get (i).addUser (this);
        }

        boolean init        = phase.equals ("$init");
        boolean splitTarget = ! splitSources.isEmpty ();

        class ReplaceConstants implements Transformer
        {
            public Variable self;
            public List<String> phases = Arrays.asList ("$connect", "$init", "$live");
            public Operator transform (Operator op)
            {
                if (op instanceof AccessVariable)
                {
                    AccessVariable av = (AccessVariable) op;
                    Operator result = null;
                    if      (phase .equals   (av.name)) result = new Constant (1);
                    else if (phases.contains (av.name)) result = new Constant (0);
                    // Self-reference returns 0 at init time, but references to other variables may return nonzero
                    // if they are initialized before this one. Self might also be initialized by a type split.
                    // TODO: check if individual variable is target of split, not simply the equation set as a whole.
                    else if (av.reference.variable == self  &&  init  &&  ! splitTarget) result = new Constant (0);
                    if (result != null)
                    {
                        result.parent = av.parent;
                        return result;
                    }
                }
                return null;
            }
        };
        ReplaceConstants replace = new ReplaceConstants ();
        for (Variable v : list)
        {
            replace.self = v;
            v.transform (replace);
        }

        boolean changed = true;
        while (changed)
        {
            changed = false;
            Iterator<Variable> it = list.iterator ();
            while (it.hasNext ())
            {
                Variable v = it.next ();
                if (v.simplify ()) changed = true;
                if (v.equations.isEmpty ()  ||  v.hasAttribute ("temporary")  &&  ! v.hasUsers ())
                {
                    it.remove ();
                    changed = true;
                }
            }
        }
    }

    /**
        Identifies variables that are integrated from their derivative.
        Depends on results of:
          resolveLHS() -- to create implicitly-defined derivatives
          fillIntegratedVariables() -- to create implicitly-defined integrands
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
            if (v.name.equals ("$t")  &&  v.order == 0) continue;  // don't integrate $t, because the simulator itself handles this.
            // Check if there is another equation that is exactly one order higher than us.
            // If so, then we must be integrated from it.
            v.derivative = find (new Variable (v.name, v.order + 1));  // returns null if the derivative does not exist
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
            if (v.name.equals ("$t"))
            {
                // Don't make $t' a derivative of $t, because integration is strictly internal to simulator.
                if (v.order > 1) v.tagDerivativeOrDependency ();
            }
            else
            {
                if (v.order > 0) v.tagDerivativeOrDependency ();
            }
        }
    }

    /**
        Convert $dt from "constant" to "initOnly" so it will be evaluated during init().
    **/
    public void makeConstantDtInitOnly ()
    {
        for (EquationSet p : parts) p.makeConstantDtInitOnly ();

        for (Variable v : variables)
        {
            if (v.name.equals ("$t")  &&  v.order == 1  &&  v.hasAttribute ("constant"))
            {
                v.removeAttribute ("constant");
                v.addAttribute    ("initOnly");
            }
        }
    }

    /**
        Identifies variables that only change during init.
        Also marks variables that change by both integration and update equations, so they will be processed in both ways.
        The additional attribute "updates" is necessary in this case, because an integrated value can never be "initOnly",
        so we can't use "initOnly" to distinguish whether or not update equations fire.

        The criteria for "initOnly" are:
        <ul>
        <li>not "constant"
        <li>not integrated
        <li>one of:
            <ul>
            <li>all equations are conditional, and no condition is true when $init=0 (that is, during update)
            <li>the same equation always fires during both init and update, and it depends only on "constant" or "initOnly" variables.
            Why only one equation? Multiple equations imply the value could change via conditional selection.
            </ul>
        </ul>
        Depends on results of: findConstants(), makeConstantDtInitOnly()
    **/
    public void findInitOnly ()
    {
        while (findInitOnlyRecursive ()) {}
    }

    public static class ReplacePhaseIndicators implements Transformer
    {
        public double init;
        public double connect;
        public double type;
        // Don't really need $live, since it's not used as a phase indicator. It is user-accessible but not documented.
        public Operator transform (Operator op)
        {
            if (op instanceof AccessVariable)
            {
                AccessVariable av = (AccessVariable) op;
                if (av.name.equals ("$init"   )) return new Constant (init);
                if (av.name.equals ("$connect")) return new Constant (connect);
                if (av.name.equals ("$type"   )) return new Constant (type);
            }
            return null;
        }
    };

    public static class VisitInitOnly implements Visitor
    {
        boolean isInitOnly = true;  // until something falsifies it
        public boolean visit (Operator op)
        {
            if (! isInitOnly) return false;

            if (op instanceof AccessVariable)
            {
                AccessVariable av = (AccessVariable) op;

                // Since constants have already been located (via simplify), we can be certain that any symbolic
                // constant has already been replaced. Therefore, only the "initOnly" attribute matters here.
                if (av.reference == null  ||  av.reference.variable == null)  // guard against failed resolution. TODO: is this check really necessary?
                {
                    isInitOnly = false;
                }
                else  // successful resolution
                {
                    Variable r = av.reference.variable;
                    if (! r.hasAttribute ("initOnly")) isInitOnly = false;
                }
            }
            else if (op instanceof Function)
            {
                Function f = (Function) op;
                if (! f.canBeInitOnly ()) isInitOnly = false;
            }

            return isInitOnly;
        }
    }

    public boolean findInitOnlyRecursive ()
    {
        boolean changed = false;

        for (EquationSet s : parts)
        {
            if (s.findInitOnlyRecursive ()) changed = true;
        }

        ReplacePhaseIndicators replacePhase = new ReplacePhaseIndicators ();
        VisitInitOnly visitor = new VisitInitOnly ();

        for (final Variable v : variables)
        {
            // Note: some variables get tagged "initOnly" by other means.
            // Note: "externalWrite" implies not "initOnly", even if the external source is initOnly, unless it can be established
            //       that both the external part and this part always go through init at the same time.
            if (v.hasAny (new String[] {"initOnly", "constant", "dummy", "externalWrite"})) continue;

            // Count equations
            int firesDuringInit   = 0;
            int firesDuringUpdate = 0;
            EquationEntry update = null;  // If we have a single update equation, then we may still be initOnly if it depends only on constants or other initOnly variables. Save the update equation for analysis.
            EquationEntry init   = null;
            for (EquationEntry e : v.equations)
            {
                // In the following tests, we make the conservative assumption that an equation fires
                // unless we can be absolutely certain it will not.
                if (e.condition == null)
                {
                    firesDuringInit++;
                    firesDuringUpdate++;
                    update = e;
                    init   = e;
                }
                else
                {
                    // The following tests do simplification of the expression, rather than substitution
                    // with a fake Instance. We don't really know the values of variables until runtime.
                    // To be completely certain of an expression's value, it must reduce to a Scalar using only
                    // values that we can know now (specifically, the state of the phase indicators).

                    // init
                    replacePhase.init = 1;
                    Operator test = e.condition.deepCopy ().transform (replacePhase).simplify (v);
                    if (! test.isScalar ()  ||  test.getDouble () != 0)
                    {
                        firesDuringInit++;
                        init = e;
                    }

                    // update
                    replacePhase.init = 0;
                    test = e.condition.deepCopy ().transform (replacePhase).simplify (v);
                    if (! test.isScalar ()  ||  test.getDouble () != 0)
                    {
                        firesDuringUpdate++;
                        update = e;
                    }
                }
            }

            if (firesDuringUpdate == 0)
            {
                if (firesDuringInit > 0  &&  v.derivative == null)
                {
                    v.addAttribute ("initOnly");
                    changed = true;
                }
            }
            else if (firesDuringUpdate == 1  &&  firesDuringInit == 1  &&  update == init  &&  v.assignment == Variable.REPLACE)  // last chance to be "initOnly": must be exactly one equation that is not a combining operator
            {
                // Determine if our single update equation depends only on constants and initOnly variables
                visitor.isInitOnly = true;
                if (update.condition != null) update.condition.visit (visitor);
                if (visitor.isInitOnly) update.expression.visit (visitor);
                if (visitor.isInitOnly)
                {
                    v.addAttribute ("initOnly");
                    changed = true;
                }
            }

            if (firesDuringUpdate > 0  &&  v.derivative != null  &&  ! v.hasAttribute ("initOnly")) v.addAttribute    ("updates");
            else                                                                                    v.removeAttribute ("updates");
        }

        return changed;
    }

    /**
        findInitOnly() propagates the "initOnly" attribute through temporaries, but the final
        attribute set of a variable cannot contain both, as they are mutually contradictory.
        "initOnly" requires storage, while "temporary" forbids it. We give "temporary"
        precedence, because we prioritize memory efficiency over time efficiency.
    **/
    public void purgeInitOnlyTemporary ()
    {
        for (EquationSet p : parts) p.purgeInitOnlyTemporary ();

        for (Variable v : variables)
        {
            if (v.hasAll ("temporary", "initOnly"))
            {
                v.removeAttribute ("initOnly");

                // If there are no regular update equations, then convert init equations into
                // regular update equations, because their values would continue to hold if the
                // variable were stored.

                // Test for presence of regular update equations.
                // See findInitOnlyRecursive() for similar code.
                boolean firesDuringUpdate = false;
                ReplacePhaseIndicators replacePhase = new ReplacePhaseIndicators ();
                replacePhase.init = 0;
                for (EquationEntry e : v.equations)
                {
                    if (e.condition == null)
                    {
                        firesDuringUpdate = true;
                        break;
                    }
                    else
                    {
                        Operator test = e.condition.deepCopy ().transform (replacePhase).simplify (v);
                        if (! test.isScalar ()  ||  test.getDouble () != 0)
                        {
                            firesDuringUpdate = true;
                            break;
                        }
                    }
                }

                // Adjust conditions, if necessary
                // See Variable.simplify() for similar code.
                if (! firesDuringUpdate)
                {
                    class ReplaceInit implements Transformer
                    {
                        public Operator transform (Operator op)
                        {
                            if (op instanceof AccessVariable)
                            {
                                AccessVariable av = (AccessVariable) op;
                                if (av.name.equals ("$init")) return new Constant (1);
                            }
                            return null;
                        }
                    };
                    ReplaceInit replaceInit = new ReplaceInit ();
                    TreeSet<EquationEntry> nextEquations = new TreeSet<EquationEntry> ();
                    for (EquationEntry e : v.equations)
                    {
                        e.condition = e.condition.transform (replaceInit).simplify (v);
                        e.ifString = e.condition.render ();
                        if (e.condition.isScalar ())
                        {
                            // Coming into this loop, there is no other default equation.
                            // Nothing prevents more than one default equation from being created here,
                            // but only one will remain after adding to nextEquations.
                            if (e.condition.getDouble () != 0)  // Will always fire.
                            {
                                e.condition = null;
                                e.ifString = "";
                                nextEquations.add (e);
                            }
                        }
                        else
                        {
                            nextEquations.add (e);
                        }
                    }
                    v.equations = nextEquations;
                }
            }
        }
    }

    /**
        Tag variables that must be set via a function call.
        We add either the "cycle" or "externalRead" attribute, to force the value into
        temporary storage.
        Depends on results of: findConstants(), makeConstantDtInitOnly()
    **/
    public void forceTemporaryStorageForSpecials ()
    {
        for (EquationSet p : parts) p.forceTemporaryStorageForSpecials ();

        for (Variable v : variables)
        {
            if (v.name.equals ("$t")  &&  v.order == 1)
            {
                if (v.hasAttribute ("initOnly")) v.addAttribute ("cycle");
                else                             v.addAttribute ("externalRead");
            }
            else if (v.name.equals ("$n")  &&  v.order == 0)
            {
                boolean canGrowOrDie =  lethalN  ||  lethalP  ||  canGrow ();
                boolean stored = ! v.hasAttribute ("constant");  // TODO: need a better way to decide if the variable is (or should be) stored
                // Issues with deciding whether $n is stored:
                // * setting "externalRead" below forces $n to be stored, if it weren't already
                // * the reason for "externalRead" is to allow us to detect changes in $n due to direct dynamics
                // * canGrowOrDie implies $n will change, but not necessarily that it should be stored
                // * $n should be stored if it is used by other equations or if it has direct dynamics
                // * if $n is merely used by other equations, and doesn't have direct dynamics, it may be possible to get its value from the size of the relevant parts collection, rather than storing the value separately.
                // All this suggests we should specifically check for direct dynamics rather than storage.
                // This would be either "externalWrite", in which case we don't need to add an attribute,
                // or it would be equations in $n itself.
                if (stored  &&  canGrowOrDie  &&  v.derivative == null) v.addAttribute ("externalRead");
            }
        }
    }

    public double determinePoll ()
    {
        if (connectionBindings == null) return -1;
        Variable p = find (new Variable ("$p", 0));
        if (p == null) return -1;

        List<EquationEntry> fires = new ArrayList<EquationEntry> ();
        ReplacePhaseIndicators replacePhase = new ReplacePhaseIndicators ();
        replacePhase.connect = 1;  // And other indicators are 0
        for (EquationEntry e : p.equations)
        {
            // Assume a condition always fires, unless we can prove it does not.
            if (e.condition != null)
            {
                Operator test = e.condition.deepCopy ().transform (replacePhase).simplify (p);
                if (test.isScalar ()  &&  test.getDouble () == 0) continue;
            }
            fires.add (e);
        }
        if (fires.isEmpty ()) return -1;  // $p=1 at connect, always

        boolean needsPoll = fires.size () > 1;  // Multiple connect conditions means unpredictable, so needs polling.
        if (! needsPoll)
        {
            // Determine if the expression for $p requires polling.
            // The possibilities for NOT polling are:
            // * a Scalar that is either 1 or 0
            // * a boolean expression that depends on nothing more than initOnly variables
            // Everything else requires polling. For example:
            // * a Scalar in (0,1) -- requires random draw
            // * a boolean expression that can vary during regular updates

            EquationEntry e = fires.get (0);
            if (e.expression.isScalar ())
            {
                double value = e.expression.getDouble ();
                needsPoll = value > 0  &&  value < 1;
            }
            else
            {
                needsPoll = true;  // The default is to poll, unless we can prove that we don't need to.
                VisitInitOnly visitor = new VisitInitOnly ();
                if (e.condition != null) e.condition.visit (visitor);
                if (visitor.isInitOnly)
                {
                    e.expression.visit (visitor);
                    if (visitor.isInitOnly)
                    {
                        // We have an initOnly equation. Now determine if the result is in (0,1).
                        // The only values we can be sure about are logical results, which are exactly 1 or 0, and therefore not in (0,1).
                        needsPoll = ! (e.expression instanceof OperatorLogical);
                    }
                }
            }
        }

        if (! needsPoll) return -1;
        // Look up metadata to determine polling period.
        String poll = p.metadata.getOrDefault ("0", "poll");
        return new UnitValue (poll).get ();
    }

    /**
        Provide each part with a list of connections which access it and which define a $min or $max on the number of connections.
        Note that resolveRHS() handles any accesses to $count
    **/
    public void findAccountableConnections ()
    {
        for (EquationSet s : parts)
        {
            s.findAccountableConnections ();
        }

        if (connectionBindings == null) return;
        for (ConnectionBinding c : connectionBindings)
        {
            Variable max = find (new Variable (c.alias + ".$max"));
            Variable min = find (new Variable (c.alias + ".$min"));
            if (max == null  &&  min == null) continue;
            if (c.endpoint.accountableConnections == null) c.endpoint.accountableConnections = new TreeSet<AccountableConnection> ();
            c.endpoint.accountableConnections.add (new AccountableConnection (this, c.alias));  // Only adds if it is not already there.
        }
    }

    /**
        Detects if $p depends on a sparse matrix.
        Depends on results of: determineTypes() and clearVariables() -- To provide fake values.
    **/
    public void findConnectionMatrix ()
    {
        for (EquationSet s : parts)
        {
            s.findConnectionMatrix ();
        }

        if (connectionBindings == null) return;  // Only do this on connections
        if (connectionBindings.size () != 2) return;  // Only check binary connections
        Variable p = find (new Variable ("$p", 0));
        if (p == null) return;

        // Determine which equation fires during connect phase
        Instance instance = new Instance ()
        {
            public Type get (Variable v)
            {
                if (v.name.equals ("$connect")) return new Scalar (1);
                if (v.name.equals ("$init"   )) return new Scalar (0);
                if (v.name.equals ("$live"   )) return new Scalar (0);
                return v.type;
            }
        };
        Operator predicate = null;
        for (EquationEntry e : p.equations)  // Scan for first equation whose condition is nonzero
        {
            if (e.condition == null)
            {
                predicate = e.expression;
                break;
            }
            Object doit = e.condition.eval (instance);
            if (doit instanceof Scalar  &&  ((Scalar) doit).value != 0)
            {
                predicate = e.expression;
                break;
            }
        }
        if (predicate == null) return;

        // Detect if equation or direct dependency contains a ReadMatrix function
        class ContainsTransformer implements Transformer
        {
            public ReadMatrix found;
            public int        countFound;
            public int        countVariable;
            public Operator transform (Operator op)
            {
                if (op instanceof ReadMatrix)
                {
                    found = (ReadMatrix) op;
                    countFound++;
                    return op;
                }
                if (op instanceof AccessVariable)
                {
                    // It is possible to be a little more liberal by recursively descending a tree
                    // of temporary variables, but right now there is no use-case for it.

                    countVariable++;
                    AccessVariable av = (AccessVariable) op;
                    Variable v = av.reference.variable;
                    if (v.container != p.container) return op;  // Stop descent. We only examine one level of dependencies, regardless of whether one matches our criteria or not.
                    if (! v.hasAttribute ("temporary")) return op;
                    if (v.equations.size () != 1) return op;
                    EquationEntry e = v.equations.first ();
                    if (e.condition != null) return op;
                    if (e.expression instanceof ReadMatrix)
                    {
                        found = (ReadMatrix) e.expression;
                        countFound++;
                        countVariable--;
                        return found;  // Replace temporary variable with its equivalent ReadMatrix call.
                    }
                    return op;
                }
                return null;
            }
        }
        ContainsTransformer ct = new ContainsTransformer ();
        Operator p2 = predicate.deepCopy ();
        p2.transform (ct);
        if (ct.countFound != 1  ||  ct.countVariable != 0) return;  // Must have exactly one ReadMatrix surrounded by only constants.
        if (ct.found.operands.length < 3) return;  // Must have a file name, a row and a column specifier
        if (! (ct.found.operands[0] instanceof Constant)) return;  // File name must be constant
        if (! (((Constant) ct.found.operands[0]).value instanceof Text)) return;  // File name must be a string

        // Check if zero elements in matrix prevent connection.
        // During analysis (like now), there is no simulator object available. This causes ReadMatrix to return 0.
        // If that results in $p evaluating to constant 0, and $p is a sufficiently simple expression,
        // then only non-zero elements will produce connections.
        try
        {
            Type result = p2.eval (instance);
            if (! (result instanceof Scalar)) return;
            if (((Scalar) result).value != 0) return;
        }
        catch (EvaluationException e)
        {
            return;
        }

        // Construct
        // It is necessary to scan predicate itself, not merely its deep copy.
        // This ensures that object identity holds in the finished model, so that
        // values set in ReadMatrix.name or fileName will be available.
        predicate.visit (new Visitor ()
        {
            public boolean visit (Operator op)
            {
                if (op instanceof ReadMatrix)
                {
                    ConnectionMatrix cm = new ConnectionMatrix ((ReadMatrix) op);
                    if (cm.rowMapping != null  &&  cm.colMapping != null) connectionMatrix = cm;
                    return false;
                }
                return true;
            }
        });
    }

    public int compareTo (EquationSet that)
    {
        return name.compareTo (that.name);
    }

    @Override
    public boolean equals (Object that)
    {
        if (this == that) return true;
        if (that instanceof EquationSet) return compareTo ((EquationSet) that) == 0;
        return false;
    }
}
