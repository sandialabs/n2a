/*
Copyright 2013-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.eqset;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.OperatorBinary;
import gov.sandia.n2a.language.ParseException;
import gov.sandia.n2a.language.Renderer;
import gov.sandia.n2a.language.Split;
import gov.sandia.n2a.language.Transformer;
import gov.sandia.n2a.language.Type;
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
import tec.uom.se.AbstractUnit;
import gov.sandia.n2a.parms.Parameter;
import gov.sandia.n2a.parms.ParameterDomain;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableSet;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
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
    public Map<String, String>                 metadata;
    public List<Variable>                      ordered;
    public List<ArrayList<EquationSet>>        splits;                 // Enumeration of the $type splits this part can go through
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
            class IndexVisitor extends Visitor
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

    public EquationSet (String name)
    {
        this.name = name;
    }

    /**
        Construct a hierarchical tree of parts from a fully-resolved model.
        @param part Generally this should be the MPart constructed from a given model, as MPart
        handles all inheritance resolution.
    **/
    public EquationSet (MNode part) throws Exception
    {
        this (null, part);
    }

    public EquationSet (EquationSet container, MNode source) throws Exception
    {
        if (container == null)  // top-level model, so pay special attention to name
        {
            name = source.getOrDefault ("$inherit", "Model").split (",", 2)[0].replace ("\"", "");
        }
        else
        {
            name = source.key ();
        }

        this.source    = source;
        this.container = container;
        variables      = new TreeSet<Variable> ();
        parts          = new TreeSet<EquationSet> ();
        metadata       = new TreeMap<String, String> ();

        // Sort equations by object-oriented operation
        boolean exception = false;
        for (MNode e : source)
        {
            String index = e.key ();
            if (index.equals ("$inherit")) continue;
            if (index.equals ("$reference")) continue;
            if (index.equals ("$metadata"))
            {
                for (MNode m : e) metadata.put (m.key (), m.get ());
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
                    // A variable with no equations and no accumulating combiner is likely to be a revocation.
                    if (v.equations.size () > 0) variables.add (v);
                }
            }
            catch (ParseException pe)
            {
                exception = true;
                pe.print (Backend.err.get ());
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

    /**
        Finds the part whose name matches the longest prefix of the given query string, if such a part exists.
        Otherwise, returns null. The matched prefix must be a proper name path, that is, either the full query
        string or else a portion delimited by dot.
    **/
    public EquationSet findPart (String query)
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
                if (! av.name.contains (".")) unresolved.add (prefix () + "." + v.nameString () + " --> " + av.name);
                continue;
            }
            if (result.endpoint == null) continue;  // Could be a variable or prefix referring to an already-found connection.

            // Store connection binding
            if (connectionBindings == null) connectionBindings = new ArrayList<ConnectionBinding> ();
            result.alias = v.name;
            result.index = connectionBindings.size ();
            connectionBindings.add (result);
            result.endpoint.connected = true;
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

        EquationSet p = findPart (query);
        if (p != null)
        {
            result.addResolution (p);
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

        if (container == null) return false;
        result.addResolution (container);
        return container.resolveConnectionBinding (query, result);
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
            EquationSet down = findPart (v.name);
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
        }

        // Check connections
        ConnectionBinding c = findConnection (v.name);
        if (c != null)
        {
            v.reference.resolution.add (c);  // same kind of resolution path as if we went into connected part, but ...
            v.name = "(connection)";         // don't match any variables within the connected part
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

    /**
        Attach the appropriate Variable to each AccessVariable operator.
        Depends on results of: resolveLHS() -- to create indirect variables and thus avoid unnecessary failure of resolution
    **/
    public void resolveRHS () throws Exception
    {
        LinkedList<String> unresolved = new LinkedList<String> ();
        resolveRHS (unresolved);
        if (unresolved.size () > 0)
        {
            PrintStream ps = Backend.err.get ();
            ps.println ("Unresolved variables:");
            ps.println ("  (name)\t(referenced by)");
            for (String v : unresolved) ps.println ("  " + v);
            throw new Backend.AbortRun ();
        }
    }

    public void resolveRHS (LinkedList<String> unresolved)
    {
        for (EquationSet s : parts)
        {
            s.resolveRHS (unresolved);
        }
    
        class Resolver extends Transformer
        {
            public Variable from;
            public LinkedList<String> unresolved;

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
                        unresolved.add (av.name + "\t" + fromName ());
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
                                query.reference.variable.reference = query.reference;  // TODO: when connect() is implemented, instances should become first class variables in the equation set, and this circular reference will be created by resolveLHS()
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
                                    query.reference.variable = ac.count;
                                    query.reference.variable.reference = query.reference;
                                }
                                else
                                {
                                    query.reference.variable = ac.count;
                                }
                            }
                            else
                            {
                                unresolved.add (av.name + "\t" + fromName ());
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
                    return av;
                }
                if (op instanceof Split)
                {
                    Split split = (Split) op;
                    int count = split.names.length;
                    split.parts = new ArrayList<EquationSet> (count);
                    for (int i = 0; i < count; i++)
                    {
                        String temp = split.names[i];
                        EquationSet part = container.parts.floor (new EquationSet (temp));
                        if (part.name.equals (temp)) split.parts.add (part);
                        else unresolved.add (temp + "\t" + fromName ());
                    }
                }
                return null;
            }
        }
        Resolver resolver = new Resolver ();
    
        for (Variable v : variables)
        {
            resolver.from = v;
            resolver.unresolved = unresolved;
            v.transform (resolver);
        }
    }

    public void determineTraceVariableName ()
    {
        for (EquationSet s : parts)
        {
            s.determineTraceVariableName ();
        }

        class TraceVisitor extends Visitor
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

        // No structural dynamics
        // These tests are good heuristics, but they're actually too strict.
        // If $p is constant 1, then part won't die. (But then, why write that?)
        // If $type always has exactly one instance of original part, then part remains a singleton.
        if (find (new Variable ("$p")) != null  &&  (strict  ||  container != null)) return false;
        if (find (new Variable ("$type")) != null) return false;
        Variable n = new Variable ("$n", 0);
        Variable nn = variables.higher (n);
        if (nn != null  &&  nn.name.equals ("$n")) return false;  // higher orders means $n is dynamic

        n = find (n);
        if (n == null) return true;  // We only do more work if $n exists. Non-existent $n is the same as $n==1

        // check contents of $n
        if (n.assignment != Variable.REPLACE) return false;
        if (n.equations.size () != 1) return false;

        EquationEntry ne = n.equations.first ();
        if (ne.expression == null) return true;  // If we can't evaluate $n, then we treat it as 1

        Instance bypass = new Instance ()
        {
            public Type get (VariableReference r) throws EvaluationException
            {
                if (r.variable.name.equals ("$init")) return new Scalar (1);  // we evaluate $n in init cycle
                return new Scalar (0);  // During init all other vars are 0, even if they have an initialization conditioned on $init. IE: those values won't be seen until after the init cycle.
            }
        };
        Type value = n.eval (bypass);
        if (value instanceof Scalar  &&  ((Scalar) value).value != 1) return false;
        // Notice that we could fall through if $n is not a Scalar. That is an error, but since $n can't be evaluated properly, we treat is as 1.
        return true;
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
            boolean hasBackendMetadata = false;
            for (Entry<String,String> m : s.metadata.entrySet ())
            {
                if (m.getKey ().startsWith (backend))
                {
                    hasBackendMetadata = true;
                    break;
                }
            }
            if (hasBackendMetadata) continue;

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
            for (Variable v : s.variables) names.add (v.nameString ());

            class Prefixer extends Transformer
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
                    else if (names.contains (av.name))
                    {
                        av.name = prefix + "." + av.name;
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
            for (Entry<String, String> e : s.metadata.entrySet ())
            {
                metadata.put (prefix + "." + e.getKey (), e.getValue ());
            }

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

    public void addGlobalConstants () throws ParseException
    {
        String key = AppData.state.getOrDefault ("General", "constants", "Constants");
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

        setInit (0);     // force $init to exist
        setConnect (0);  // and $connect

        Variable v = new Variable ("$t", 0);
        if (add (v))
        {
            v.unit = UnitValue.seconds;
            v.equations = new TreeSet<EquationEntry> ();
        }

        v = new Variable ("$t", 1);  // $t'
        if (add (v))
        {
            v.unit = UnitValue.seconds;
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
                    String duration = getNamedValue ("duration", "1");  // limit sim time to 1 second, if not otherwise specified
                    v.add (new EquationEntry ("$t<" + duration));
                }
                catch (Exception parseError)
                {
                    try {v.add (new EquationEntry ("$t<1"));}
                    catch (Exception parseError2) {} // This exception should never happen. We simply want to silence Java about it.
                }
            }
        }

        v = new Variable ("$live", 0);  // $live functions much the same as $init or $connect
        if (add (v))
        {
            v.unit = AbstractUnit.ONE;
            v.addAttribute ("constant");  // default. Actual values should be set by setAttributeLive()
            EquationEntry e = new EquationEntry (v, "");
            e.expression = new Constant (new Scalar (1));
            v.add (e);
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
            init.add (e);
            add (init);
        }
        else
        {
            EquationEntry e = init.equations.first ();
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
            connect.add (e);
            add (connect);
        }
        else
        {
            EquationEntry e = connect.equations.first ();
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
                if (! (e.expression instanceof Split))
                {
                    Backend.err.get ().println ("Unexpected expression for $type");
                    throw new Backend.AbortRun ();
                }
                ArrayList<EquationSet> split = ((Split) e.expression).parts;
                if (! container.splits.contains (split))
                {
                    container.splits.add (split);
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
        return lethalN  ||  lethalP  ||  lethalType  || lethalConnection  || lethalContainer;
    }

    /**
        Determine if population can grow by a means other than setting $n, specifically by
        $type splits that produce more than 1 offspring of our current type.
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
            else if (v.name.contains ("$")  &&  ! v.name.startsWith ("$up."))
            {
                if (v.name.equals ("$xyz")  ||  v.name.endsWith (".$project")  ||  v.name.endsWith (".$project"))  // are there always dots before $project?
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
            if ((v.name.startsWith ("$")  ||  v.name.contains (".$"))  &&  ! v.name.startsWith ("$up.")) continue;

            Type value;
            if (v.derivative != null)  // and v is not "constant", but that is covered above
            {
                value = find (new Variable (v.name, v.order + 1)).type;  // this should exist, so no need to verify result
            }
            else
            {
                Instance instance = new Instance ()
                {
                    // all AccessVariable objects will reach here first, and get back the Variable.type field
                    public Type get (VariableReference r) throws EvaluationException
                    {
                        return r.variable.type;
                    }
                };
                value = v.eval (instance);  // can return null if no equation's condition is true
            }
            if (value != null  &&  value.betterThan (v.reference.variable.type))
            {
                v.reference.variable.type = value;
                v.type = value;  // convenience, so that a reference knows its type, not merely its target
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

    public void determineUnits ()
    {
        while (determineUnitsEval ());
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
            try {if (v.determineUnit (false)) changed = true;}
            catch (Exception e) {}  // No exceptions should occur during this pass.
        }
        return changed;
    }

    /**
        Convenience function to implement user preferences about dimension checking during compilation.
        Depends on results of: resolveLHS, resolveRHS -- All variable references must be resolved.
        @throws AbortRun if an inconsistency is found and user wants this to be fatal.
    **/
    public void checkUnits () throws AbortRun
    {
        String dimension = AppData.state.getOrDefault ("General", "dimension", "Warning");
        if (! dimension.equals ("Warning")  &&  ! dimension.equals ("Error")) return;  // "Don't check"

        determineUnits ();
        LinkedList<String> mismatches = new LinkedList<String> ();
        checkUnitsEval (mismatches);
        if (mismatches.size () > 0)
        {
            boolean error = dimension.equals ("Error");
            PrintStream err = Backend.err.get ();
            err.println ((error ? "ERROR" : "WARNING") + ": Inconsistent dimensions. Report shows operator and mismatched units from operands.");
            for (String m : mismatches) err.println (m);
            if (error) throw new AbortRun ();
        }
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
                mismatches.add ("  " + name + " : " + error.getMessage ());
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
                if (result instanceof Scalar) setNamedValue ("duration", new Double (((Scalar) result).value).toString ());
            }
        }
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
                    Backend.err.get ().println ("Cyclic dependency: " + v.name + " comes after " + u.name);
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
            <li>all equations are conditional, and none are true when $init=0 (that is, during update)
            <li>only one equation could fire during update, and it depends only on "constant" or "initOnly" variables.
            Why only one equation? Multiple equations imply the value could change via conditional selection.
            </ul>
        </ul>
        Depends on results of: findConstants(), makeConstantDtInitOnly()
    **/
    public void findInitOnly ()
    {
        while (findInitOnlyRecursive ()) {}
    }

    public static class ReplacePhaseIndicators extends Transformer
    {
        public float init;
        public Operator transform (Operator op)
        {
            if (op instanceof AccessVariable)
            {
                AccessVariable av = (AccessVariable) op;
                if (av.name.equals ("$init"   )) return new Constant (init);
                if (av.name.equals ("$connect")) return new Constant (0);
            }
            return null;
        }
    };

    public boolean findInitOnlyRecursive ()
    {
        boolean changed = false;

        for (EquationSet s : parts)
        {
            if (s.findInitOnlyRecursive ()) changed = true;
        }

        ReplacePhaseIndicators replacePhase = new ReplacePhaseIndicators ();

        for (final Variable v : variables)
        {
            if (v.hasAny (new String[] {"initOnly", "constant", "dummy"})) continue;  // Note: some variables get tagged "initOnly" by other means, so don't re-process

            // Count equations
            int firesDuringInit   = 0;
            int firesDuringUpdate = 0;
            EquationEntry update = null;  // If we have a single update equation, then we may still be initOnly if it depends only on constants or other initOnly variables. Save the update equation for analysis.
            for (EquationEntry e : v.equations)
            {
                // In the following tests, we make the conservative assumption that an equation fires
                // unless we can be absolutely certain it will not.
                if (e.condition == null)
                {
                    firesDuringInit++;
                    firesDuringUpdate++;
                    update = e;
                }
                else
                {
                    // init
                    replacePhase.init = 1;
                    Operator test = e.condition.deepCopy ().transform (replacePhase).simplify (v);
                    if (! test.isScalar ()  ||  test.getDouble () != 0)  firesDuringInit++;

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
            else if (firesDuringUpdate == 1  &&  v.assignment == Variable.REPLACE)  // last chance to be "initOnly": must be exactly one equation that is not a combining operator
            {
                // Determine if our single update equation depends only on constants and initOnly variables
                class VisitInitOnly extends Visitor
                {
                    boolean isInitOnly = true;  // until something falsifies it
                    public boolean visit (Operator op)
                    {
                        if (isInitOnly)
                        {
                            if (op instanceof AccessVariable)
                            {
                                AccessVariable av = (AccessVariable) op;

                                // Since constants have already been located (via simplify), we can be certain that any symbolic
                                // constant has already been replaced. Therefore, only the "initOnly" attribute matters here.
                                if (av.reference == null  ||  av.reference.variable == null) isInitOnly = false;
                                Variable r = av.reference.variable;
                                if (! r.hasAttribute ("initOnly")) isInitOnly = false;

                                // Also verify that the variables we depend on are available during the appropriate phase of init
                                if (isInitOnly  &&  ! r.hasAttribute ("temporary"))  // Note that temporaries are always available.
                                {
                                    if (v.name.startsWith ("$"))  // we are a $variable, so we can only depend on $index and phase indicators
                                    {
                                        if (! "$index,$connect,$init,$live".contains (r.name)) isInitOnly = false;
                                    }
                                    else  // we are a regular variable, so can only depend on $variables
                                    {
                                        if (! r.name.startsWith ("$")) isInitOnly = false;
                                    }
                                }
                            }
                            else if (op instanceof Function)
                            {
                                Function f = (Function) op;
                                if (! f.canBeInitOnly ()) isInitOnly = false;
                            }
                        }
                        return isInitOnly;
                    }
                }
                VisitInitOnly visitor = new VisitInitOnly ();

                if (update.condition != null) update.condition.visit (visitor);
                if (visitor.isInitOnly)
                {
                    update.expression.visit (visitor);
                    if (visitor.isInitOnly)
                    {
                        v.addAttribute ("initOnly");
                        changed = true;
                    }
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
            if (v.hasAttribute ("temporary")  &&  v.hasAttribute ("initOnly"))
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
                    class ReplaceInit extends Transformer
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
        class ContainsTransformer extends Transformer
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
        ConnectionMatrix cm = new ConnectionMatrix (ct.found);
        if (cm.rowMapping != null  &&  cm.colMapping != null) connectionMatrix = cm;
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

    public Set<Entry<String,String>> getMetadata ()
    {
        if (metadata == null) metadata = new TreeMap<String, String> ();
        return metadata.entrySet ();
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
