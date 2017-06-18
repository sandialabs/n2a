/*
Copyright 2013-2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.eqset;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.OperatorBinary;
import gov.sandia.n2a.language.ParseException;
import gov.sandia.n2a.language.Renderer;
import gov.sandia.n2a.language.Split;
import gov.sandia.n2a.language.Transformer;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.Visitor;
import gov.sandia.n2a.language.function.Gaussian;
import gov.sandia.n2a.language.function.Output;
import gov.sandia.n2a.language.function.Uniform;
import gov.sandia.n2a.language.operator.GE;
import gov.sandia.n2a.language.operator.GT;
import gov.sandia.n2a.language.operator.LE;
import gov.sandia.n2a.language.operator.LT;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.ui.images.ImageUtil;
import gov.sandia.n2a.parms.Parameter;
import gov.sandia.n2a.parms.ParameterDomain;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
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
    public String                              name;
    public EquationSet                         container;
    public NavigableSet<Variable>              variables;
    public NavigableSet<EquationSet>           parts;
    public NavigableMap<String, EquationSet>   connectionBindings;     // non-null iff this is a connection
    public boolean                             connected;
    public NavigableSet<AccountableConnection> accountableConnections; // Connections which declare a $min or $max w.r.t. this part. Note: connected can be true even if accountableConnections is null.
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

    public static class AccountableConnection implements Comparable<AccountableConnection>
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

    /**
        Construct the hierarchical tree of parts implied by the N2A code in the given model.
        This involves reading in other models as indicated by $inherit or $include, and
        placing variables in the correct set.
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
                if (MPart.isPart (e)) parts    .add (new EquationSet (this, e));
                else                  variables.add (new Variable    (this, e));
            }
            catch (ParseException pe)
            {
                exception = true;
                pe.print (Backend.err.get ());
            }
            catch (Exception ee)
            {
                exception = true;
            }
        }
        if (exception) throw new Backend.AbortRun ();
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

    public boolean add (Variable v)
    {
        v.container = this;
        return variables.add (v);
    }

    /**
        Merge given equation set into this, where contents of that always take precedence.
        @param that No longer usable after the merge, as certain contents may be extracted,
        modified, and incorporated into this.
    **/
    public void merge (EquationSet that)
    {
        // Merge variables, and collate equations within each variable
        for (Variable v : that.variables)
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
        for (EquationSet p : that.parts)
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

        metadata.putAll (that.metadata);
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
        Find instance variables (what in other languages might be called pointers) and move them
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

    public void resolveConnectionBindings (LinkedList<String> unresolved) throws Exception
    {
        for (EquationSet s : parts)
        {
            s.resolveConnectionBindings (unresolved);
        }

        // Scan for equations that look and smell like connection bindings.
        // A binding equation has these characteristics:
        // * Only one equation on the variable.
        // * Unconditional (conditional bindings are not permitted)
        // * No operators, only a name on RHS that appears like a variable name.
        // * RHS name is order 0 (not a derivative)
        // * No variable in the current equation set matches the name.
        // $up is permitted. The explicit name of a higher container may also be used,
        // provided nothing matches the name on the way up.
        // $connect should not appear. It should have been overwritten during construction.
        // If $connect does appear, bindings are incomplete, which is an error.
        Iterator<Variable> i = variables.iterator ();  // need to use an iterator here, so we can remove variables from the set
        while (i.hasNext ())
        {
            Variable v = i.next ();

            // Detect instance variables
            if (v.equations.size () != 1) continue;
            EquationEntry ee = v.equations.first ();
            if (ee.condition != null) continue;
            if (! (ee.expression instanceof AccessVariable)) continue;

            // Resolve connection endpoint to a specific equation set
            String targetName = ((AccessVariable) ee.expression).name;
            EquationSet s = findEquationSet (targetName);
            if (s == null)
            {
                unresolved.add (prefix () + "." + v.nameString () + " --> " + targetName);
                continue;
            }

            // Store connection binding
            if (connectionBindings == null) connectionBindings = new TreeMap<String, EquationSet> ();
            connectionBindings.put (v.name, s);
            s.connected = true;
            i.remove ();  // Should no longer be in the equation list, as there is nothing further to compute.
        }
    }

    /**
        Returns reference to the named equation set, based on a search starting in the
        current equation set and applying all the N2A name-resolution rules.
    **/
    public EquationSet findEquationSet (String query)
    {
        if (query.isEmpty ()) return this;
        String[] pieces = query.split ("\\.", 2);
        String ns = pieces[0];
        String nextName;
        if (pieces.length > 1) nextName = pieces[1];
        else                   nextName = "";

        if (ns.equals ("$up"))  // Don't bother with local checks if we know we are going up
        {
            if (container == null) return null;
            return container.findEquationSet (nextName);
        }

        EquationSet p = parts.floor (new EquationSet (ns));
        if (p != null  &&  p.name.equals (ns)) return p.findEquationSet (nextName);

        if (find (new Variable (ns)) != null) return null;  // not allowed to match any of our variables

        if (container == null) return null;
        return container.findEquationSet (query);
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
                v.reference.variable.addDependency (v);  // v.reference.variable receives an external write from v, and therefore its value depends on v
                v.reference.variable.container.referenced = true;
                if (   v.reference.variable.assignment != v.assignment
                    && ! (   (v.reference.variable.assignment == Variable.MULTIPLY  &&  v.assignment == Variable.DIVIDE)  // This line and the next say that * and / are compatible with each other, so ignore that case.
                          || (v.reference.variable.assignment == Variable.DIVIDE    &&  v.assignment == Variable.MULTIPLY)))
                {
                    Variable target = v.reference.variable;
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
                        unresolved.add (av.name + "\t" + from.container.prefix () + "." + from.nameString ());
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
                                unresolved.add (av.name + "\t" + from.container.prefix () + "." + from.nameString ());
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
                        else unresolved.add (temp + "\t" + from.container.prefix () + "." + from.nameString ());
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
            for (Entry<String, EquationSet> e : connectionBindings.entrySet ())
            {
                renderer.result.append (prefix + e.getKey () + " = ");
                EquationSet s = e.getValue ();
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

    /**
        Determines if this equation set has a fixed size of 1.
    **/
    public boolean isSingleton ()
    {
        Variable n = find (new Variable ("$n", 0));
        if (n == null) return true;  // We only do more work if $n exists. Non-existent $n is the same as $n==1

        // make sure no other orders of $n exist
        Variable n2 = variables.higher (n);
        if (n2.name.equals ("$n")) return false;  // higher orders means $n is dynamic

        // check contents of $n
        if (n.assignment != Variable.REPLACE) return false;
        if (n.equations.size () != 1) return false;

        EquationEntry ne = n.equations.first ();
        if (ne.expression == null) return true;  // If we can't evaluate $n as a number, then we treat it as 1

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
            if (s.connectionBindings != null) continue;
            if (s.connected) continue;

            // For similar reasons, if the part contains backend-related metadata, it should remain separate.
            boolean hasBackendMetadata = false;
            for (Entry<String,String> m : s.metadata.entrySet ())
            {
                if (m.getKey ().startsWith ("backend"))  // this is only a convention, but one that should be followed
                {
                    hasBackendMetadata = true;
                    break;
                }
            }
            if (hasBackendMetadata) continue;

            // Check if $n==1
            if (! s.isSingleton ()) continue;
            Variable n = s.find (new Variable ("$n", 0));
            if (n != null) s.variables.remove (n);  // We don't want $n in the merged set.

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
            for (Variable v : s.variables)
            {
                names.add (v.name);
            }

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

        Variable v = new Variable ("$t", 0);
        if (add (v))
        {
            v.equations = new TreeSet<EquationEntry> ();
        }

        v = new Variable ("$t", 1);  // $t'
        if (add (v))
        {
            v.equations = new TreeSet<EquationEntry> ();
        }

        if (container == null)           // top-level model
        {
            v = new Variable ("$p", 0);  // must have a termination condition
            if (add (v))                 // but it doesn't
            {
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

        v = new Variable ("$live", 0);  // $live functions much the same as $init. See setInit().
        if (add (v))
        {
            v.addAttribute ("constant");  // default. Actual values should be set by setAttributeLive()
            EquationEntry e = new EquationEntry (v, "");
            e.expression = new Constant (new Scalar (1));
            v.add (e);
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
        for (EquationSet s : parts)
        {
            s.removeUnused ();
        }

        TreeSet<Variable> temp = new TreeSet<Variable> (variables);
        for (Variable v : temp)
        {
            if (v.hasUsers ()  ||  v.hasAttribute ("externalWrite")) continue;
            if (v.equations.size () > 0  &&  (v.name.startsWith ("$")  ||  v.name.contains (".$"))) continue;  // even if a $variable has no direct users, we must respect any statements about it

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
            init = new Variable ("$init", 0);
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
        return ((Scalar) ((Constant) e.expression).value).value == 1.0;
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
            ReplaceInit replaceInit = new ReplaceInit ();
            for (EquationEntry e : p.equations)
            {
                if (e.expression instanceof Constant)
                {
                    Type value = ((Constant) e.expression).value;
                    if (value instanceof Scalar)
                    {
                        if (((Scalar) value).value == 1.0) continue;
                    }
                }

                // Now we have an equation that evaluates to something other than 1.
                // If this occurs anywhere but pre-init, then $p is lethal.

                // Check if condition fires during init phase
                if (e.condition != null)
                {
                    replaceInit.init = 1;
                    replaceInit.live = 1;
                    Operator test = e.condition.deepCopy ().transform (replaceInit).simplify (p);
                    if (test instanceof Constant)
                    {
                        Constant c = (Constant) test;
                        if (c.value instanceof Scalar  &&  ((Scalar) c.value).value == 0)  // Does not fire during init phase
                        {
                            // Check if condition fires during update phase
                            replaceInit.init = 0;
                            test = e.condition.deepCopy ().transform (replaceInit).simplify (p);
                            if (test instanceof Constant)
                            {
                                c = (Constant) test;
                                if (c.value instanceof Scalar  &&  ((Scalar) c.value).value == 0) continue;  // Does not fire during update phase
                            }
                        }
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
            for (Entry<String,EquationSet> e : connectionBindings.entrySet ())
            {
                EquationSet s = e.getValue ();
                if (s.canDie ())
                {
                    Variable live = s.find (new Variable ("$live"));
                    if (live != null) live.addUser (this);
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
            if (v.name.contains ("$")  &&  ! v.name.startsWith ("$up."))
            {
                if (v.name.equals ("$xyz")  ||  v.name.endsWith (".$projectFrom")  ||  v.name.endsWith (".$projectTo"))  // are there always dots before $projectFrom/To?
                {
                    v.type = new Matrix (3, 1);
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
            else if (v.hasAttribute ("constant"))
            {
                v.type = ((Constant) v.equations.first ().expression).value;
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
        for (EquationSet s : parts)
        {
            s.findConstants ();
        }

        for (Variable v : variables)
        {
            v.simplify ();

            // Check if we have a constant
            // Don't remove an existing "constant" tag. Such specially added tags are presumed to be correct.
            if (v.hasAttribute ("externalWrite")) continue;  // Regardless of the local math, a variable that gets written is not constant.
            if (v.equations.size () != 1) continue;
            EquationEntry e = v.equations.first ();
            if (e.condition != null) continue;
            if (e.expression instanceof Constant) v.addAttribute ("constant");
        }
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
                    if (! v.hasAny (new String [] {"externalRead", "externalWrite"})  &&  v.derivative == null)
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
                               addSpecials() -- to $variables in the correct order along with everything else
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
        for (Variable v : variables) v.setBefore ();

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
            v.visited = null;
            v.setPriority (1);
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
            for (Variable u : v.uses)
            {
                if (   u.container == this  // must be in same equation set for order to matter
                    && ! (u.name.equals (v.name)  &&  u.order == v.order + 1)  // must not be my derivative
                    && ! u.hasAttribute ("temporary")  // temporaries follow the opposite rule on ordering, so don't consider them here
                    &&  ordered.indexOf (u) < index)  // and finally, is it actually ahead of me in the odering?
                {
                    Backend.err.get ().println ("Cyclic dependency: " + v.name + " comes after " + u.name);
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

    public static class ReplaceInit extends Transformer
    {
        public float init;
        public float live;
        public Operator transform (Operator op)
        {
            if (op instanceof AccessVariable)
            {
                AccessVariable av = (AccessVariable) op;
                if (av.name.equals ("$init")) return new Constant (new Scalar (init));
                if (av.name.equals ("$live")) return new Constant (new Scalar (live));
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

        ReplaceInit replaceInit = new ReplaceInit ();

        for (final Variable v : variables)
        {
            if (v.hasAny (new String[] {"initOnly", "constant", "dummy"})) continue;  // Note: some variables get tagged "initOnly" by other means, so don't re-process

            // Count equations
            int firesDuringInit   = 0;
            int firesDuringUpdate = 0;
            EquationEntry update = null;  // If we have a single update equation, then we may still be initOnly if it depends only on constants or other initOnly variables. Save the update equation for analysis.
            for (EquationEntry e : v.equations)
            {
                if (e.condition == null)
                {
                    firesDuringInit++;
                    firesDuringUpdate++;
                    update = e;
                }
                else
                {
                    // init
                    replaceInit.init = 1;
                    replaceInit.live = 1;
                    Operator test = e.condition.deepCopy ().transform (replaceInit).simplify (v);
                    boolean fires = true;
                    if (test instanceof Constant)
                    {
                        Constant c = (Constant) test;
                        if (c.value instanceof Scalar  &&  ((Scalar) c.value).value == 0) fires = false;
                    }
                    if (fires) firesDuringInit++;

                    // update
                    replaceInit.init = 0;
                    replaceInit.live = 1;
                    test = e.condition.deepCopy ().transform (replaceInit).simplify (v);
                    fires = true;
                    if (test instanceof Constant)
                    {
                        Constant c = (Constant) test;
                        if (c.value instanceof Scalar  &&  ((Scalar) c.value).value == 0) fires = false;
                    }
                    if (fires)
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
                                    if (v.name.startsWith ("$"))  // we are a $variable, so we can only depend on $index and $init
                                    {
                                        if (! r.name.equals ("$index")  &&  ! r.name.equals ("$init")) isInitOnly = false;
                                    }
                                    else  // we are a regular variable, so can only depend on $variables
                                    {
                                        if (! r.name.startsWith ("$")) isInitOnly = false;
                                    }
                                }
                            }
                            else if (op instanceof Gaussian) isInitOnly = false;  // A random number generator cannot be classified constant, even if its parameters are constant.
                            else if (op instanceof Uniform ) isInitOnly = false;
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
            if (v.hasAttribute ("temporary")  &&  v.hasAttribute ("initOnly")) v.removeAttribute ("initOnly");
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
                if (stored  &&  canGrowOrDie  &&  v.derivative == null)
                {
                    v.addAttribute ("externalRead");
                }
            }
        }
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
