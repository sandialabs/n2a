/*
Copyright 2013-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.eqset;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
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
import gov.sandia.n2a.language.function.Draw;
import gov.sandia.n2a.language.function.Event;
import gov.sandia.n2a.language.function.Exp;
import gov.sandia.n2a.language.function.Gaussian;
import gov.sandia.n2a.language.function.Input;
import gov.sandia.n2a.language.function.Output;
import gov.sandia.n2a.language.function.Uniform;
import gov.sandia.n2a.language.operator.GE;
import gov.sandia.n2a.language.operator.GT;
import gov.sandia.n2a.language.operator.LE;
import gov.sandia.n2a.language.operator.LT;
import gov.sandia.n2a.language.operator.Power;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix.IteratorNonzero;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.linear.MatrixDense;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.plugins.extpoints.Backend.AbortRun;
import tech.units.indriya.AbstractUnit;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;

public class EquationSet implements Comparable<EquationSet>
{
    public String                              name;
    public MNode                               source;                 // Node from collated model that this equation set is based on. Could be null if this eqset is constructed by the middle-end as part of analysis or optimization.
    public EquationSet                         container;
    public NavigableSet<Variable>              variables;
    public List<EquationSet>                   parts;
    public MNode                               pinIn;                  // partial collection of input pins (does not include inner input pins which are exported)
    public MNode                               pinOut;                 // collection of output pins
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
    public Set<EquationSet>                    splitSources;           // Equation sets that might create an instance of this equation set via a $type split. Can include ourself.
    public boolean                             lethalN;                // our population could shrink
    public boolean                             lethalP;                // we could have a non-zero probability of dying in some cycle 
    public boolean                             lethalType;             // we can be killed by a part split
    public boolean                             lethalConnection;       // indicates we are a connection, and one of the parts we connect to can die
    public boolean                             lethalContainer;        // our parent could die
    public boolean                             referenced;             // Some other equation set writes to one of our variables. If we can die, then exercise care not to reuse this part while other parts are still writing to it. Otherwise our reincarnated part might get written with values from our previous life.
    public ConnectionMatrix                    connectionMatrix;       // If non-null, this is a connection whose existence depends primarily on elements of a matrix.
    public Object                              backendData;            // holder for extra data associated with each equation set by a given backend

    public static final List<String> endpointSpecials = Arrays.asList ("$count", "$k", "$max", "$min", "$project", "$radius");  // $variables that appear after an endpoint identifier
    public static final List<String> phases           = Arrays.asList ("$connect", "$init", "$live");

    public static final double b2d = Math.log (10) / Math.log (2);  // bits per decimal digit

    /**
        Connection terminology:
        connection -- a type of equation set that references into (reads and writes) other equation sets
        instance variable -- a type of variable that can point to equation-set instances; used to express references
        alias -- name of the instance variable
        endpoint -- the destination equation set; at run-time this can also refer to the specific instance
        binding -- association between instance variable and endpoint
        bound -- when an instance variable is assigned a path to an actual equation set
        unbound -- when an instance variable is tagged as such via the "connect()" notation
    **/
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

    /**
        Marks an operator as capable of iterating through a matrix-like collection of values,
        skipping elements with value zero. This makes the process of forming connection more
        efficient for sparse collections. In that case, the compiler optimizes how the connection
        is formed.
        The operator promises to evaluate to exactly zero if no simulator is available.
        This helps with the analysis.
    **/
    public interface NonzeroIterable
    {
        Operator        operandA ();
        Operator        operandB ();
        boolean         hasCorrectForm ();
        IteratorNonzero getIteratorNonzero (Instance context);
    }

    public class ConnectionMatrix
    {
        public NonzeroIterable A;

        // Bindings for populations associated with the rows/columns of the matrix.
        public ConnectionBinding rows;
        public ConnectionBinding cols;

        // Mappings from row/column position to $index of respective population.
        public Equality rowMapping;
        public Equality colMapping;
        public boolean  needsMapping;  // Indicates that at least one of rowMapping or colMapping requires some calculation.

        public ConnectionMatrix (NonzeroIterable A)
        {
            this.A = A;
            Operator op1 = A.operandA ();
            Operator op2 = A.operandB ();
            if (op1 == null  ||  op2 == null) return;
            AccessVariable av1 = endpoint (op1);
            AccessVariable av2 = endpoint (op2);
            if (av1 == null  ||  av2 == null) return;

            rows = findConnection (av1.reference);
            cols = findConnection (av2.reference);
            if (rows == null  ||  cols == null) return;

            rowMapping = new Equality (op1, av1);
            rowMapping.solve ();
            if (rowMapping.lhs != rowMapping.target) rowMapping = null;  // failed to solve for "target"
            colMapping = new Equality (op2, av2);
            colMapping.solve ();
            if (colMapping.lhs != colMapping.target) colMapping = null;
            needsMapping =  colMapping.rhs != colMapping.rc  ||  rowMapping.rhs != rowMapping.rc;
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
                        if (av.name.endsWith ("$index"))
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
        parts          = new ArrayList<EquationSet> ();
        metadata       = new MVolatile ();
    }

    /**
        Construct a hierarchical tree of parts from a fully-resolved model.
        The given part must have a proper key.
    **/
    public EquationSet (MNode part) throws Exception
    {
        this (null, part);
    }

    public EquationSet (EquationSet container, MNode source) throws Exception
    {
        name           = source.key ();
        this.source    = source;
        this.container = container;
        variables      = new TreeSet<Variable> ();
        parts          = new ArrayList<EquationSet> ();
        metadata       = new MVolatile ();

        // Sort equations by object-oriented operation
        boolean exception = false;
        for (MNode e : source)
        {
            if (e.getFlag ("$kill")) continue;

            String index = e.key ();
            if (index.equals ("$inherit")) continue;
            if (index.equals ("$ref")) continue;
            if (index.equals ("$meta"))
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
                    variables.add (v);

                    // Add a watch expression, if requested.
                    MNode vmeta = v.getMetadata ();
                    if (vmeta.getFlag ("watch"))
                    {
                        boolean spike = vmeta.get ("watch").equals ("spike");

                        // Determine dummy variable name
                        String dummy = "x0";
                        int suffix = 1;
                        while (find (new Variable (dummy, -1)) != null  ||  source.child (dummy) != null) dummy = "x" + suffix++;

                        // Check for timeScale
                        EquationSet root = getRoot ();
                        String timeScale = root.metadata.get ("watch", "timeScale");  // typically the horizontal axis
                        String scale = "";                                            // typically the vertical axis
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

                        // Check for interval (output at less than every timestep)
                        String interval = root.metadata.get ("watch", "interval");

                        // Create output expression
                        String expression = dummy + "=output(\"\",";
                        if (spike) expression += "1";
                        else       expression += v.nameString ();
                        if (! timeScale.isEmpty ())
                        {
                            expression += ",\"\",timeScale=" + timeScale;
                            if (! scale.isEmpty ()) expression += ",scale=" + scale;
                        }
                        expression += ")";
                        if (spike) expression += "@" + v.nameString ();
                        else if (! interval.isEmpty ()) expression += "@$t%" + interval + "<$t'/2";
                        override (expression);
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
        Search for a variable within this equation set that matches the query's name and (if specified) order.
        Order can be made no-care by setting negative.
        Returns null if match is not found.
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

    public List<Variable> collectTemporaries (Variable v)
    {
        List<Variable> result = new ArrayList<Variable> ();
        for (Variable t : ordered)
        {
            if (t.hasAttribute ("temporary")  &&  v.dependsOn (t) != null)
            {
                result.add (t);
            }
        }
        return result;
    }

    /**
        @return The nearest variable $t' in the containment hierarchy that has explicit equations.
        Could be the $t' on this current equation set. Could be null.
    **/
    public Variable findDt ()
    {
        Variable query = new Variable ("$t", 1);
        EquationSet p = this;
        while (p != null)
        {
            Variable dt = p.find (query);
            if (dt != null  &&  ! dt.equations.isEmpty ()) return dt;
            p = p.container;
        }
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
        v.container = this;
        variables.add (v);
    }

    /**
        Add or replace a variable by parsing an equation in string form.
        Since this function should be under complete program control, it is more convenient to trap
        any possible exceptions from the Variable class, rather than asking the caller to handle
        them. This implies that the caller must be careful to never produce malformed equations.
    **/
    public Variable override (String equation)
    {
        try
        {
            Variable v = Variable.from (equation);
            override (v);
            return v;
        }
        catch (Exception e)
        {
            return null;
        }
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
            // Search for part with exact key match.
            EquationSet eqForKey = result.findPart (key);
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
        Returns the highest ancestor of this equation set.
    **/
    public EquationSet getRoot ()
    {
        if (container != null) return container.getRoot ();
        return this;
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
        Collect the keys of all db parts that are somewhere in our inheritance tree.
        Includes our own key.
    **/
    public Set<String> collectAncestors ()
    {
        return collectAncestors (source);
    }

    /**
        Collect the keys of all db parts that are somewhere in the given child's
        inheritance tree. Includes the child's own key.
    **/
    public static Set<String> collectAncestors (MNode child)
    {
        Set<String> result = new HashSet<String> ();
        result.add (child.key ());
        collectAncestors (child, result);
        return result;
    }

    /**
        Collect the keys of all db parts that are somewhere in the given child's
        inheritance tree.
    **/
    public static void collectAncestors (MNode child, Set<String> ancestors)
    {
        String inherit = child.get ("$inherit");
        if (inherit.isEmpty ()) return;
        String[] inherits = inherit.split (",");
        for (String i : inherits)
        {
            i = i.trim ().replace ("\"", "");
            if (i.isEmpty ()) continue;
            if (ancestors.add (i))
            {
                MNode parent = AppData.models.child (i);
                if (parent != null) collectAncestors (parent, ancestors);
            }
        }
    }

    /**
        Finds the last common ancestor (LCA) between this equation set and the given one.
        If the equation tree is coherent and both this and that are members of it, then
        this will always return a valid result. It may be the root of the tree.
    **/
    public EquationSet findLCA (EquationSet that)
    {
        // Mark our own path.
        EquationSet p = this;
        while (p != null)
        {
            p.visited = null;
            p = p.container;
        }

        // Mark the other path.
        p = that;
        while (p != null)
        {
            p.visited = this;
            p = p.container;
        }

        // Check our own path.
        p = this;
        while (p != null)
        {
            if (p.visited == this) return p;
            p = p.container;
        }

        return null;  // This case should never be reached.
    }

    /**
        Determines if this equation set has a fixed size of 1.
    **/
    public boolean isSingleton ()
    {
        // No connections
        // The population size of a connection depends on other objects, so can't be a singleton.
        if (connectionBindings != null) return false;

        // Limit sources of structural dynamics
        // These tests are good heuristics, but they're actually too strict. Some relaxations would be:
        // * If $p is constant 1, then part won't die. (But then, why write that?)
        // * If $type always has exactly one instance of original part, then part remains a singleton. (Again, why write that?)
        if (find (new Variable ("$p", -1)) != null  &&  container != null) return false;
        if (find (new Variable ("$type")) != null) return false;
        Variable n = new Variable ("$n");
        Variable nn = variables.higher (n);
        if (nn != null  &&  nn.name.equals ("$n")) return false;  // higher orders means $n is dynamic

        n = find (n);
        if (n == null) return true;  // We only do more work if $n exists. Non-existent $n is the same as $n==1

        // check contents of $n
        if (n.hasAny ("externalWrite", "cli")) return false;  // "cli" indicates the C backend will read $n from command-line. In that case, an equation with constant 1 is only the default. The final runtime value is unknown.
        if (n.uses != null  &&  n.uses.size () > 0) return false;  // If $n depends on other variables, we won't be able to evaluate it now.
        if (n.equations.size () != 1) return false;
        EquationEntry ne = n.equations.first ();

        // Ideally we would evaluate the expression, but that's not possible when isSingleton()
        // is called before resolveRHS(). Instead, we do a simple check for constant 1.
        if (! ne.ifString.isEmpty ()  &&  ! ne.ifString.equals ("$init")) return false;  // Any condition besides $init indicates the potential to change during run.
        if (ne.expression == null) return true;  // Treat missing expression as 1.
        return ne.expression.getDouble () == 1;
    }

    /**
        Determines whether this equation set is a singleton within the context determined by the
        given "cousin". An equation set may have multiple instances within the context of the model
        as a whole, and yet only have one instance within a given sub-part. To determine the sub-part,
        we find the LCA of this equation set and the given part. We use the term "cousin" loosely to
        mean any part that is not an ancestor or descendant.
        Notice that this function is more strict than isSingleton(), because we are taking
        into account not only instances created by the immediate part, but also those created by
        some subset of its ancestors. Put another way, isSingleton() is roughly equivalent to
        this.isSingletonRelativeTo(this).
    **/
    public boolean isSingletonRelativeTo (EquationSet that)
    {
        EquationSet LCA = findLCA (that);
        EquationSet p = this;
        while (true)
        {
            if (! isSingleton ()) return false;
            if (p == LCA) break;
            p = p.container;
        }
        return true;
    }

    public boolean isConnection ()
    {
        return connectionBindings != null;
    }

    /**
        Tests if this part or any of its ancestors is contained in the proposed parent.
        The childOf relationship does not include self. If the proposed parent equals this part,
        then the result is false.
    **/
    public boolean isChildOf (EquationSet proposedParent)
    {
        EquationSet p = container;
        while (p != null)
        {
            if (p == proposedParent) return true;
            p = p.container;
        }
        return false;
    }

    public boolean usesRandom ()
    {
        class RandomVisitor implements Visitor
        {
            boolean found;
            public boolean visit (Operator op)
            {
                if (op instanceof Uniform  ||  op instanceof Gaussian) found = true;
                return ! found;
            }
        }
        RandomVisitor visitor = new RandomVisitor ();

        for (Variable v : variables)
        {
            v.visit (visitor);
            if (visitor.found) return true;
        }

        for (EquationSet p : parts) if (p.usesRandom ()) return true;

        return false;
    }

    /**
        Collects pin exports and exposures into lists for use by fillAutoPins(), resolvePins() and purgePins().
        Compare this code with NodePart.analyzePins()
        Depends on: none -- This function must be the very first thing run after constructing the
        full equation set hierarchy. However, if it is known that no equation set uses pins,
        then it is possible to skip straight to resolveConnections().
    **/
    public void collectPins ()
    {
        // Process everything below this level of the hierarchy first.
        for (EquationSet s : parts) s.collectPins ();

        pinIn  = new MVolatile ();
        pinOut = new MVolatile ();

        // Collect pins from sub-parts
        for (EquationSet s : parts)
        {
            MNode pin = s.metadata.child ("gui", "pin");
            if (pin == null) continue;

            // Forwarded input
            MNode in = pin.child ("in");
            if (in != null)
            {
                for (MNode i : in)
                {
                    String pinName = i.get ("bind", "pin");
                    if (pinName.isEmpty ()) continue;
                    if (i.get ("bind").isEmpty ()) pinIn.set ("", pinName, s.name);  // A blank value indicates a forwarded input.
                }
            }

            // Exposure
            // If more than one part supplies the same topic for a given output pin, then it is completely
            // arbitrary which one is selected.
            if (pin.get ().isEmpty ()  &&  (in != null  ||  pin.child ("out") != null)) continue;  // pin must be explicit, or both "in" and "out" must be absent.

            // Determine if this is a connection (since resolveConnectionBindings() has not been called yet).
            // The pin interface requires that at least one alias be unbound and thus tagged with "connect()".
            // The simplest way to find this is to scan the source code.
            for (MNode c : s.source)
            {
                if (Operator.containsConnect (c.get ()))
                {
                    s.connectionBindings = new ArrayList<ConnectionBinding> ();  // Use existence of empty list as flag. This should be filled later by resolveConnectionBindings()
                    s.metadata.set (c.key (), "gui", "pin", "alias");  // Remember the chosen alias for this connection.
                    break;
                }
            }

            String pinName = pin.getOrDefault (s.name);
            String topic = pin.getOrDefault ("data", "topic");
            if (s.connectionBindings == null)  // population (output)
            {
                pinOut.set (s.name, pinName, "topic", topic);
            }
            else  // connection (input)
            {
                pinIn.set ("", pinName, s.name);  // pinIn is just a collection of subscribers to each pin

                // pass-through to output
                MNode pass = pin.child ("pass");
                if (pass != null)
                {
                    pinName = pass.getOrDefault (pinName);  // "pass" can override the exposure name
                    pinOut.set (s.name, pinName, "topic", topic);
                }
            }
        }

        // Forwarded outputs
        for (MNode pin : metadata.childOrEmpty ("gui", "pin", "out"))
        {
            String bind = pin.get ("bind");
            if (bind.isEmpty ()) continue;
            String bindPin = pin.get ("bind", "pin");
            if (bindPin.isEmpty ()) continue;
            EquationSet s = findPart (bind);
            if (s == null) continue;
            if (s.pinOut == null  ||  s.pinOut.child (bindPin) == null) continue;

            String pinName = pin.key ();
            pinOut.set (bind,    pinName, "bind");
            pinOut.set (bindPin, pinName, "bind", "pin");
        }
    }

    /**
        If this part exposes an auto pin, then duplicate the dependent sub-parts for each bound input.
        Finally, this function recurses into all child parts, whether or not they are the result of duplication.
        The duplication takes place only at the EquationSet level. The corresponding source field is
        simply a reference to the original source. This is slightly inconsistent with the contents
        of the resulting EquationSet objects. For example, the key of the source will not match the name
        of the EquationSet. If this breaks any middle-end code, then we can create a fake
        source, or even update the true source. In the last case, it would be necessary for the true
        source to be volatile, so the database record is not changed.
        Depends on: collectPins()
    **/
    public void fillAutoPins ()
    {
        for (MNode autopin : pinIn)
        {
            String pinName = autopin.key ();
            if (! pinName.endsWith ("#")) continue;
            // Found an auto pin; now process it ...

            int length = pinName.length () - 1;
            String pinBase = pinName.substring (0, length);

            // Scan for instances
            // These only exist in the part's original metadata, since they are not backed by actual sub-parts.
            TreeSet<Integer> instances = new TreeSet<Integer> ();  // automatically sorted, which is useful for iteration below
            for (MNode pin2 : metadata.childOrEmpty ("gui", "pin", "in"))
            {
                String pinBaseI = pin2.key ();
                if (! pinBaseI.startsWith (pinBase)) continue;
                String suffix = pinBaseI.substring (length);  // remove prefix
                // The suffix must be a proper integer.
                try
                {
                    int index = Integer.parseInt (suffix);
                    if (index > 0) instances.add (index);
                }
                catch (NumberFormatException e) {}
            }
            int lastIndex = 0;
            if (! instances.isEmpty ()) lastIndex = instances.last ();

            // Collect destination parts for connections
            Map<String,EquationSet> Bparts = new HashMap<String,EquationSet> ();
            for (MNode subscriber : autopin)
            {
                String baseName = subscriber.key ();
                EquationSet template = findPart (baseName);
                if (template.connectionBindings == null) continue;

                MNode  templatePin = template.metadata.child ("gui", "pin");
                String Aname       = templatePin.get ("alias");  // assigned by collectPins()
                // First connection binding that doesn't match "Aname" should be the destination part.
                // This is a compact form of the same code in resolveConnectionBindings()
                for (Variable v : template.variables)
                {
                    if (v.name.equals (Aname)) continue;
                    AccessVariable av = template.isConnectionBinding (v);
                    if (av == null) continue;
                    ConnectionBinding cb = new ConnectionBinding ();
                    if (! template.resolveConnectionBinding (av.name, cb)) continue;
                    if (cb.endpoint == null) continue;

                    Bparts.put (baseName, cb.endpoint);
                    templatePin.set (v.name, "aliasB");  // for an alias, v.name is sufficient
                    break;
                }
            }

            // Process each instance
            int previous = 0;  // previous index; zero is never a suffix, so this is sufficient to indicate none (start of list)
            for (int index : instances)
            {
                String pinBaseI = pinBase + index;

                // Duplicate each subscriber
                for (MNode subscriber : autopin)
                {
                    String baseName = subscriber.key ();
                    EquationSet template = findPart (baseName);
                    EquationSet duplicate = new EquationSet (this, baseName + index);
                    parts.add (duplicate);
                    duplicate.copyPinTemplate (template);

                    duplicate.override ("autoIndex=" + index);
                    duplicate.override ("autoCount=" + lastIndex);
                    MNode templatePin = template.metadata.child ("gui", "pin");
                    if (template.connectionBindings != null)  // connectionBindings got preliminary setting from collectPins()
                    {
                        MNode append = templatePin.child ("append");
                        String autoBase = templatePin.getOrDefault ("0", "append");

                        EquationSet Bpart = Bparts.get (baseName);
                        if (Bpart == null)
                        {
                            // It's possible for the user to set append mode without an actual downstream target.
                            // In that case, fake the autoBase in the duplicated part itself.
                            if (append != null) duplicate.override ("autoBase=" + autoBase);
                        }
                        else
                        {
                            String Aname = templatePin.get ("alias");   // assigned by collectPins()
                            String Bname = templatePin.get ("aliasB");  // assigned above

                            if (append != null)
                            {
                                duplicate.override ("autoBase=:" + Bname + ".autoBase" + index);
                                duplicate.override ("$all." + Bname + ".autoN" + index + "=" + Aname + ".$n");
                            }

                            // Set variables in B part
                            if (append != null)
                            {
                                if (previous == 0) Bpart.override ("$all.autoBase" + index + "=" + autoBase);
                                else               Bpart.override ("$all.autoBase" + index + "=autoBase" + previous + "+autoN" + previous);
                                previous = index;
                                if (index == lastIndex)
                                {
                                    String n = Bpart.source.get ("$n");
                                    if (n.isEmpty ()  ||  n.startsWith ("autoBase")) Bpart.override ("$n=autoBase" + index + "+autoN" + index);
                                }
                            }
                        }
                    }

                    // Update input bindings with actual instance name.
                    // Notice that pinIn does not need to be updated, because it is only used to get subscribers for the auto-pin template.
                    //   connections
                    if (templatePin.get ().equals (pinName))  // must match the auto-pin name
                    {
                        duplicate.metadata.set (pinBaseI, "gui", "pin");
                    }
                    //   parts that forward inputs
                    for (MNode b : templatePin.childOrEmpty ("in"))
                    {
                        if (b.get ("bind").isEmpty ()  &&  b.get ("bind", "pin").equals (pinName))  // must bind to IO block and match the auto-pin name
                        {
                            duplicate.metadata.set (pinBaseI, "gui", "pin", "in", b.key (), "bind", "pin");
                        }
                    }
                }
            }
        }

        // Fill auto pins in children.
        // Note that any children which depend on the auto pin at this current level are also processed,
        // even though they will eventually be deleted by purgePins(). This ensures they are fully
        // expanded, so they can be analyzed by other code. This is not useful for running the
        // model, but is useful for an export routine that understands auto pins.
        for (EquationSet s : parts) s.fillAutoPins ();
    }

    /**
        Recursively copies a part that is acting as an auto-pin template.
        Assumes that this (target) part has minimal construction, including name and parent part.
    **/
    public void copyPinTemplate (EquationSet template)
    {
        source = template.source;  // Direct reference, not copy. May need to do something more rigorous, depending on how source is used by middle-end functions.
        metadata.merge (template.metadata);
        pinIn = new MVolatile ();
        pinIn.merge (template.pinIn);
        pinOut = new MVolatile ();
        pinOut.merge (template.pinOut);
        if (template.connectionBindings != null) connectionBindings = new ArrayList<ConnectionBinding> ();  // see collectPins()
        for (Variable v : template.variables) add (v.deepCopy ());
        for (EquationSet s : template.parts)
        {
            EquationSet s2 = new EquationSet (this, s.name);
            parts.add (s2);
            s2.copyPinTemplate (s);
        }
    }

    /**
        Converts dangling connections exposed as pins into regular connections with well-defined
        bindings. Folds pass-through connections onto their downstream counterparts.
        The resulting model should be interpretable without reference to metadata or any knowledge
        of pin structure. However, auto pins and pass-through connections may still remain.
        These are eliminated in a separate step.
        Depends on results of: collectPins
    **/
    public void resolvePins () throws Exception
    {
        LinkedList<String> unresolved = new LinkedList<String> ();
        resolvePins (unresolved);
        if (unresolved.size () > 0)
        {
            PrintStream ps = Backend.err.get ();
            ps.println ("Unresolved pin links:");
            for (String v : unresolved) ps.println ("  " + v);
            throw new Backend.AbortRun ();
        }
    }

    public void resolvePins (List<String> unresolved) throws Exception
    {
        for (EquationSet s : parts)
        {
            // Process inner structure of part, regardless of type.
            // If this is a connection, then any internal pin structure should not ascend out of the part.
            s.resolvePins (unresolved);

            // Connections with an unbound alias.
            // For each unbound alias, walk the part hierarchy to find the right target population.
            if (s.connectionBindings == null) continue;  // Only connections allowed.
            if (s.metadata.child ("gui", "pin", "pass") != null) continue;  // Pass-through connections are not allowed.
            String alias = s.metadata.get ("gui", "pin", "alias");  // This value only exists if there is an unbound instance variable.
            if (alias.isEmpty ()) continue;  // Must be unbound.

            PinBinding result = new PinBinding ();
            result.resolve (s);
            if (result.unresolved != null)
            {
                unresolved.add (s.prefix () + "." + alias + " --> " + result.unresolved);
            }
            else if (result.endpoint != null)  // endpoint will only be defined for a proper pin, not an auto-pin template
            {
                s.override (alias + "=" + result.path ());
            }
        }
    }

    public static class PinBinding
    {
        EquationSet C;          // The connection part we are trying to resolve.
        String      pinName;    // current name of pin exposing the connection; can change during traversal
        String      topic;      // topic that connection requests; remains the same
        EquationSet endpoint;   // if non-null, then this is the target of the connection (and implicitly, resolution succeeded).
        String      unresolved; // if non-null, then this message describes how the resolution failed

        public void resolve (EquationSet s)
        {
            pinName = s.metadata.getOrDefault (s.name, "gui", "pin");
            if (pinName.endsWith ("#")) return;
            C       = s;
            topic   = s.metadata.getOrDefault ("data", "gui", "pin", "topic");
            s       = s.container;

            // Examine the given input pin of s
            // Possible outcomes: 1) ascend to container of s; 2) move to bound pin on peer of s
            while (true)  // Could ascend several times before reaching resolution.
            {
                String bindPin = s.metadata.get ("gui", "pin", "in", pinName, "bind", "pin");
                if (bindPin.endsWith ("#")) return;  // Auto-pin. Not an error, but not resolved either.
                if (bindPin.isEmpty ())
                {
                    unresolved = s.prefix () + " input pin '" + pinName + "' not bound";
                    return;
                }
                String bind = s.metadata.get ("gui", "pin", "in", pinName, "bind");
                if (bind.isEmpty ())  // ascend to container of s
                {
                    pinName = bindPin;
                    s = s.container;
                }
                else  // lateral move to peer of s
                {
                    // Examine the output pin of peer
                    // Possible outcomes: 1) match the topic with a population inside peer; 2) pass-through connection to an input of peer; 3) descend to an inner part
                    EquationSet container = s.container;
                    boolean passThrough = false;
                    while (! passThrough)  // Could descend several times before reaching resolution.
                    {
                        // Find the peer
                        EquationSet peer = container.findPart (bind);
                        if (peer == null)
                        {
                            unresolved = "'" + bind + "' is not a part in " + container.prefix ();
                            return;
                        }

                        // Find the specified pin
                        MNode pin = peer.pinOut.child (bindPin);
                        if (pin == null)
                        {
                            unresolved = "'" + bindPin + "' is not an output pin of " + peer.prefix ();
                            return;
                        }

                        // Search for topic
                        String targetName = pin.get ("topic", topic);
                        if (targetName.isEmpty ())  // No matching topic, so attempt to follow an exported inner pin.
                        {
                            String outputPinName = bindPin;  // for error reporting, if needed
                            bind    = pin.get ("bind");
                            bindPin = pin.get ("bind", "pin");
                            if (bind.isEmpty ()  ||  bindPin.isEmpty ())  // No options left
                            {
                                unresolved = "topc '" + topic + "' not available from part '" + peer.prefix () + "' output pin '" + outputPinName + "'";
                                return;
                            }
                            // else descend.
                            // The binding we just retrieved will be used in the context of peer to find the inner part.
                            container = peer;
                        }
                        else  // matched topic
                        {
                            EquationSet target = peer.findPart (targetName);
                            if (target.connectionBindings == null)  // Done! A true output population.
                            {
                                endpoint = target;
                                return;
                            }
                            else  // A pass-through connection to peer's inputs.
                            {
                                passThrough = true;
                                s = peer;
                                pinName = target.metadata.getOrDefault (targetName, "gui", "pin");

                                // The main purpose of a pass-through connection is to supply a weight
                                // matrix to an inner connection. As a generalization, we overwrite any
                                // variables which are explicitly defined in the pass-through part.
                                // This does not include anything that could change structure, such as
                                // $inherit or the connection bindings. It is best if the pass-through
                                // connection be of the same type as its downstream counterpart.
                                for (Variable v : target.variables)
                                {
                                    if (v.name.equals ("$inherit")) continue;
                                    if (target.isConnectionBinding (v) != null) continue;  // No connection bindings allowed.
                                    MPart vsource = (MPart) target.source.child (v.nameString ());
                                    if (vsource.isFromTopDocument ()) C.override (v.deepCopy ());
                                }
                            }
                        }
                    }
                }
            }
        }

        // Construct a binding path based on the source and destination parts.
        public String path ()
        {
            if (C == null  ||  endpoint == null) return "";

            // Since this is mainly for computing models rather than human viewing, it does not need
            // to be the simplest string possible. Instead, we use a simple-but-ugly generation method.

            EquationSet LCA = C.findLCA (endpoint);

            // Walk up the path from C to LCA
            String up = "";
            EquationSet p = C;
            while (p != LCA)
            {
                up += "$up.";
                p = p.container;
            }

            // Walk up the path from endpoint to LCA, reversing the steps.
            String down = "";
            p = endpoint;
            while (p != LCA)
            {
                down = p.name + "." + down;
                p = p.container;
            }
            if (! down.isEmpty ()) down = down.substring (0, down.length () - 1);  // remove trailing dot

            return up + down;
        }
    }

    /**
        Removes auto pins (those whose name ends with #) and all structures that subscribe to them.
        Each structure functions as a template for generating new pins, but itself is never bound.
        Thus it can't function at run time and would otherwise produce compiler errors.
        Also removes pass-through connections, for similar reasons.
        collectPins() could remove auto pins, but this is kept separate to allow partial compilation
        where the client code wishes to analyze the templates themselves.
        Pass-through pins should not be removed until after resolvePins() has run.
        Depends on results of: collectPins
    **/
    public void purgePins ()
    {
        for (MNode pin : pinIn)
        {
            if (pin.key ().endsWith ("#"))  // auto-pin
            {
                // Delete all parts that subscribe to auto pin.
                for (MNode p : pin) parts.remove (findPart (p.key ()));
            }
        }

        List<EquationSet> tempParts = new ArrayList<EquationSet> (parts);
        for (EquationSet s : tempParts)
        {
            MNode pass = s.metadata.child ("gui", "pin", "pass");
            if (pass == null) s.purgePins ();
            else              parts.remove (s);
        }
    }

    /**
        Find instance variables (that in other languages might be called pointers) and move them
        into the connectionBindings structure.
        Depends on results of
            resolvePins -- Provides the link path for each dangling connection that is exposed as an input pin.
            purgeAutoPins -- Removes template structures that can't be resolved.
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
            AccessVariable av = isConnectionBinding (v);
            if (av == null) continue;

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
        Given a variable v from this equation set, determine if it meets the heuristics for a connection
        binding. The only way to be 100% certain is to resolve it.
        @return The AccessVariable object that contains the actual reference to the desired endpoint.
        If this is not a connection binding, then the result is null.
    **/
    public AccessVariable isConnectionBinding (Variable v)
    {
        if (v.order > 0) return null;
        if (v.name.contains ("$")  ||  v.name.contains ("\\.")) return null;
        if (v.equations.size () != 1) return null;
        if (v.assignment != Variable.REPLACE) return null;
        EquationEntry ee = v.equations.first ();
        if (ee.condition != null) return null;
        if (! (ee.expression instanceof AccessVariable)) return null;
        AccessVariable av = (AccessVariable) ee.expression;
        if (av.getOrder () > 0) return null;
        if (find (new Variable (av.getName (), -1)) != null) return null;  // Not a binding if matching variable of any order exists.
        return av;
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

        Variable v = find (new Variable (ns, -1));
        if (v != null) return true;  // The match was found, but it turns out to be a variable, not an equation set. result.endpoint should still be null.
        // Treat undefined $variables as local. This will never include $up, because that case is eliminated above.
        if (ns.startsWith ("$")) return true;  // Assert it is a variable, regardless. There is no legal way for this to be a part name, even if it contains a dot.

        if (container == null)
        {
            if (pieces.length == 1  &&   ns.equals (name))  // Match current part as if it is the child of an imaginary container above the top-level.
            {
                result.endpoint = this;
                return true;
            }
            return false;
        }
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
            p.priority = 0;  // Should not generally be needed, since we do this process only once, and priority is initialized to zero.
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
        Resolution is done before flattening, so all names should have simple structure. In particular,
        neither equation set names nor LHS variable names should contain dots.
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
            if (v.name.startsWith ("$up."))  // Probably not a true $variable, just an up-reference.
            {
                if (container == null) return null;  // Unresolved! We can't go up any farther.
                v.name = v.name.substring (4);
                v.reference.resolution.add (container);
                return container.resolveEquationSet (v, create);
            }

            if (v.name.equals ("$count")) return this;  // "this" is the endpoint that accounts for the number of connections. Presumably, the connection itself was the previous part visited.

            // $variables are always treated as local. For example, you would never want to inherit $n from a container!
            if (variables.contains (v)) return this;
            if (! create) return null;

            // Usually, the important $variables have already been created by addSpecials(),
            // so v is probably an unusual derivative. The alternative is a user-defined variable,
            // which really shouldn't have a $ prefix.
            Variable cv = new Variable (v.name, v.order);
            add (cv);
            cv.reference = new VariableReference ();
            cv.reference.variable = cv;
            cv.equations = new TreeSet<EquationEntry> ();
            cv.assignment = v.assignment;  // hinted combiner type
            if (v.hasAttribute ("global")) cv.addAttribute ("global");
            return this;
        }
        if (   v.name.endsWith (".$k")
            || v.name.endsWith (".$max")
            || v.name.endsWith (".$min")
            || v.name.endsWith (".$project")
            || v.name.endsWith (".$radius"))
        {
            return this;  // "this" is the connection part itself, while the variable is munged to include the alias of the target part
        }

        // Check namespace references.
        String[] ns = v.name.split ("\\.", 2);
        if (ns.length > 1)  // Has a namespace prefix.
        {
            ConnectionBinding c = findConnection (ns[0]);
            if (c != null)
            {
                v.name = ns[1];
                v.reference.resolution.add (c);
                return c.endpoint.resolveEquationSet (v, create);
            }

            EquationSet down = findPart (ns[0]);
            if (down != null)
            {
                v.name = ns[1];
                v.reference.resolution.add (down);
                return down.resolveEquationSet (v, create);
            }

            // Check if prefix matches this current equation set name.
            // This is equivalent to referring the variable up to our container, which in turn finds that the prefix matches us.
            // However, we don't want those extra steps in the resolution path.
            if (name.equals (ns[0]))  // prefix matches
            {
                v.name = ns[1];
                // Don't add this to the resolution path!
                return resolveEquationSet (v, create);
            }
        }
        else  // This is a simple variable name, with no namespace prefix.
        {
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
            if (findPart (v.name) != null) return null;

            if (create)
            {
                // Create a self-referencing variable with no equations
                Variable cv = new Variable (v.name, v.order);
                add (cv);
                cv.reference = new VariableReference ();
                cv.reference.variable = cv;
                cv.equations = new TreeSet<EquationEntry> ();
                cv.assignment = v.assignment;
                if (v.hasAttribute ("global")) cv.addAttribute ("global");
                return this;
            }
        }

        // Look up the containment hierarchy
        if (container == null) return null;  // unresolved!!
        v.reference.resolution.add (container);
        return container.resolveEquationSet (v, create);
    }

    public VariableReference resolveReference (String variableName)
    {
        Variable query = new Variable (variableName, -1);
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
        LinkedList<UnresolvedVariable> unresolved = new LinkedList<UnresolvedVariable> ();
        resolveLHS (unresolved);
        if (unresolved.size () > 0)
        {
            int width = 0;
            for (UnresolvedVariable uv : unresolved) width = Math.max (width, uv.name.length ());
            PrintStream ps = Backend.err.get ();
            ps.println ("Unresolved left-hand-side references:");
            ps.println ("  " + UnresolvedVariable.pad ("(from part)", width) + "\t(reference)");
            for (UnresolvedVariable uv : unresolved) ps.println ("  " + UnresolvedVariable.pad (uv.name, width) + "\t" + uv.referencedBy);
            throw new AbortRun ();
        }
    }

    public void resolveLHS (LinkedList<UnresolvedVariable> unresolved)
    {
        for (EquationSet s : parts)
        {
            s.resolveLHS (unresolved);
        }

        for (Variable v : variables)
        {
            Variable query = new Variable (v.name, v.order);
            query.reference = new VariableReference ();
            query.assignment = v.assignment;  // If referent is created in target eqset, then this hints the correct combiner type.
            boolean global = v.hasAttribute ("global");
            if (global) query.addAttribute ("global");  // as a hint

            EquationSet dest = resolveEquationSet (query, true);  // Create the variable if needed. Does not create parts, only variables within parts.
            if (dest == null)  // The target part does not exist.
            {
                unresolved.add (new UnresolvedVariable (prefix (), v.nameString ()));
                continue;
            }

            query.reference.variable = dest.find (query);
            v.reference = query.reference;
            v.reference.removeLoops ();
            Variable target = v.reference.variable;
            if (target != v  &&  target != null)
            {
                target.addDependencyOn (v);  // v.reference.variable receives an external write from v, and therefore its value depends on v
                v.reference.addDependencies (v);  // A variable depends on its connection bindings. This isn't necessary for execution, but does support analysis during GUI editing.
                target.container.referenced = true;

                if (   target.assignment != v.assignment
                    && ! (   (target.assignment == Variable.MULTIPLY  &&  v.assignment == Variable.DIVIDE)  // This line and the next say that * and / are compatible with each other, so ignore that case.
                          || (target.assignment == Variable.DIVIDE    &&  v.assignment == Variable.MULTIPLY)))
                {
                    Backend.err.get ().println ("WARNING: Reference " + v.fullName () + " has different combining operator than target variable (" + target.fullName () + "). Resolving in favor of higher-precedence operator.");
                    v.assignment = target.assignment = Math.max (v.assignment, target.assignment);
                }

                if (global  &&  ! target.hasAttribute ("global"))
                {
                    Backend.err.get ().println ("WARNING: Reference " + v.fullName () + " cannot execute in global context, because its target variable (" + target.fullName () + ") is stored in local context.");
                    v.removeAttribute ("global");
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
            resolveConnectionBindings() -- to follow connection references
            resolveLHS() -- to create indirect variables and thus avoid unnecessary failure of resolution
            findIntegrated() -- to create implicit integrands
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

        class Resolver implements Visitor
        {
            public Variable from;
            public LinkedList<UnresolvedVariable> unresolved;

            public String fromName ()
            {
                String result = from.container.prefix ();
                if (! result.isEmpty ()) result += ".";
                return result + from.nameString ();
            }

            public boolean visit (Operator op)
            {
                if (op instanceof AccessVariable)
                {
                    AccessVariable av = (AccessVariable) op;
                    Variable query = new Variable (av.getName (), av.getOrder ());
                    VariableReference r = new VariableReference ();
                    query.reference = r;
                    av   .reference = r;
                    EquationSet dest = resolveEquationSet (query, false);  // modifies "r" with actual resolution path
                    r.removeLoops ();
                    r.addDependencies (from);  // dependencies from "from" to each part in the resolution path

                    if (dest == null)
                    {
                        unresolved.add (new UnresolvedVariable (av.name, fromName ()));
                    }
                    else
                    {
                        r.variable = dest.find (query);  // "query" contains the modified variable name, needed for lookup within "dest"
                        if (r.variable == null)
                        {
                            if (query.hasAttribute ("instance"))
                            {
                                // Configure reference to destination container itself.
                                r.variable = query;  // Recycle the query variable as a pseudo target (one that doesn't actually exist in the container).
                                query.container = dest;
                                query.equations = new TreeSet<EquationEntry> ();
                                query.type = new Instance ();
                                query.readIndex = -2;  // Only for use by Internal backend. It's easier to set this here than to scan for "instance" variables in InternalBackendData.analyze().
                            }
                            else if (query.name.equals ("$count"))  // accountable endpoint
                            {
                                int last = r.resolution.size () - 1;
                                Object o = null;
                                if (last >= 0) o = r.resolution.get (last);
                                if (! (o instanceof ConnectionBinding))
                                {
                                    unresolved.add (new UnresolvedVariable (av.name, fromName ()));
                                }
                                else
                                {
                                    ConnectionBinding cb = (ConnectionBinding) o;
                                    if (dest.accountableConnections == null) dest.accountableConnections = new TreeSet<AccountableConnection> ();
                                    AccountableConnection ac = new AccountableConnection (r.penultimateContainer (EquationSet.this), cb.alias);
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
                                        ac.count.reference = new VariableReference ();
                                        ac.count.reference.variable = ac.count;
                                    }
                                    r.variable = ac.count;
                                }
                            }
                            else
                            {
                                unresolved.add (new UnresolvedVariable (av.name, fromName ()));
                            }
                        }
                        else
                        {
                            from.addDependencyOn (r.variable);
                        }
                    }
                    return false;
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
                        else                             part = family.findPart (partName);
                        if (part != null)
                        {
                            split.parts.add (part);

                            Variable query = new Variable ("$type");
                            Variable type = part.find (query);
                            if (type == null)
                            {
                                // $type is not in target part, so we add it merely to inform that target part of how it was created.
                                // No need for buffering in this case. However, we need to explicitly clear $type at the end of init.
                                type = query;
                                part.add (type);
                                type.unit = AbstractUnit.ONE;
                                type.equations = new TreeSet<EquationEntry> ();
                                type.reference = new VariableReference ();
                                type.reference.variable = type;
                                type.addUser (part);  // Keep part.$type from being removed.
                                type.addAttribute ("state");
                            }

                            if (type != from) type.addDependencyOn (from); // Because part.$type value can be changed by from.
                        }
                        else
                        {
                            unresolved.add (new UnresolvedVariable (partName, fromName ()));
                        }
                    }
                    return false;
                }
                return true;
            }
        }
        Resolver resolver = new Resolver ();
        resolver.unresolved = unresolved;

        for (Variable v : variables)
        {
            resolver.from = v;
            v.visit (resolver);
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

    public String dump (boolean showNamespace, String pad)
    {
        Renderer renderer = new Renderer ()
        {
            public boolean render (Operator op)
            {
                if (! (op instanceof AccessVariable)) return false;

                AccessVariable av = (AccessVariable) op;
                if (av.reference == null  ||  av.reference.variable == null)
                {
                    if (showNamespace) result.append ("<unresolved!>");
                    result.append (av.name);
                }
                else
                {
                    if (showNamespace) result.append ("<" + av.reference.variable.container.prefix () + ">");
                    result.append (av.reference.variable.nameString ());
                }
                return true;
            }
        };

        renderer.result.append (pad + name + "\n");
        pad = pad + " ";
        if (connectionBindings != null)
        {
            for (ConnectionBinding c : connectionBindings)
            {
                renderer.result.append (pad + c.alias + " = ");
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
            if (v.equations.size () == 0) continue;  // If no equations, then this is an implicit variable, so no need to list here.
            if (v.name.equals ("$connect")  ||  v.name.equals ("$init")  ||  v.name.equals ("$live")) continue;  // Phase indicators are always present, and thus uninformative.

            renderer.result.append (pad + v.nameString ());
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
                    renderer.result.append (pad + " ");
                    e.render (renderer);
                    renderer.result.append ("\n");
                }
            }
        }

        for (EquationSet e : parts)
        {
            renderer.result.append (e.dump (showNamespace, pad));
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
        Convert this equation set into an equivalent object where each included part with $n==1
        (and satisfying a few other conditions) is merged into its containing part.
        Equations with combiners (=+, =*, and so on) are joined together into one long equation
        with the appropriate operator.
        Depends on results of: resolveLHS(), resolveRHS() -- Object identity of variables should already be established.
        @param backend Prefix for metadata keys specific to the engine selected to execute this model.
        Where such keys exist, the parts should not be flattened.
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
            if (! s.isSingleton ()) continue;
            s.variables.remove (new Variable ("$n"));  // We don't want to overwrite our own $n, so remove it from the sub-part. This won't change its singleton status.

            // Don't merge if there are any conflicting $variables.
            // Regular variables never conflict, because they get a unique prefix when flattened.
            // However, $variables cannot be prefixed. Their semantics are strongly bound to their
            // current equation set.
            boolean conflict = false;
            for (Variable v : s.variables)
            {
                if (! v.name.startsWith ("$")) continue;
                if (v.reference.variable.container == EquationSet.this) continue;  // Not a conflict if LHS is an up-reference to this equation set. These equations will get merged.
                Variable d = find (v);
                if (d == null) continue;  // Not a conflict unless v exists in this equation set.
                if (s.source.child (v.nameString ()) == null) continue;  // Not a conflict if v is a $variable added by processing.
                conflict = true;
                break;
            }
            if (conflict) continue;
            // No conflicts. However, there may still be overlapping $variables. Only $variables unique
            // to s will be moved up. For those that do overlap, any references will be redirected to
            // the equivalent variable in this container.

            // Merge

            final String prefix = s.name;
            parts.remove (s);

            //   Variables
            //   In addition to simply moving the variables up to this container, we need to do incremental maintenance of:
            //   * Display names, for debugging.
            //   * Resolution paths.
            //   Of those, resolution paths are the most difficult, because many different objects are affected:
            //   * References to variables in s
            //     - from this container or our parents
            //     - from children of s
            //   * References to variables in children of s
            //   * References from s to other sets
            //   * References from children of s
            //     - to s
            //     - to other sets
            //   And it is necessary to handle both LHS and RHS references.

            //     Pass 1 -- Change RHS references originating from variables within s so they function within this container instead.
            //     Need to do this before variables get moved, because we depend on the identity of their current container.
            Visitor prefixer = new Visitor ()
            {
                public boolean visit (Operator op)
                {
                    if (! (op instanceof AccessVariable)) return true;
                    AccessVariable av = (AccessVariable) op;

                    if (av.reference.variable.container == s)  // internal reference
                    {
                        if (! av.name.startsWith ("$")) av.name = prefix + "." + av.name;  // cosmetic change
                    }
                    else  // external reference
                    {
                        if (av.name.startsWith ("$up.")) av.name = av.name.substring (4);  // cosmetic change
                        List<Object> r = av.reference.resolution;
                        if (r.get (0) == EquationSet.this) r.remove (0);  // If first step of resolution is parent, then remove it, because variable is about to become part of parent.
                    }

                    return false;
                }
            };
            for (Variable v : s.variables)
            {
                v.visit (prefixer);
            }

            //     Pass 2 -- Move the variables up to this container.
            //     Requires changes in external references to the variables of s.
            //     Requires changes in resolution paths.

            //     Utility class: Modify references to variable "from" so they point to variable "to" instead.
            //     The expectation is that "from" will be forgotten.
            class Redirector implements Visitor
            {
                Variable from;  // original reference target, in child part
                Variable to;    // new reference target, in parent part
                Variable user;  // current variable being processed; the one that makes the reference

                public boolean visit (Operator op)
                {
                    if (! (op instanceof AccessVariable)) return true;
                    AccessVariable av = (AccessVariable) op;
                    if (av.reference.variable != from) return false;

                    if (from != to)
                    {
                        user.removeDependencyOn (from);
                        user.addDependencyOn (to);
                    }
                    adjustReference (av.reference);
                    return false;
                }

                public void adjustReference (VariableReference reference)
                {
                    reference.variable = to;

                    List<Object> r = reference.resolution;
                    int last = r.size () - 1;
                    if (last >= 0)  // The resolution path has something in it.
                    {
                        r.remove (last--);  // The last entry should always be from.container. The only other case, of a connection binding, is eliminated by early tests in the flatten() function.
                        // There are two possible cases.
                        // 1) Descending reference from parent or higher parents. The parent will be he last entry in the path, and thus no further change is needed.
                        // 2) Ascending reference from deeper children. parent needs to be added, effectively replacing child as the final destination.
                        // The problem is how to distinguish these cases ...
                        if (last >= 0)  // Path was more than 1 step, so check if parent is now last entry.
                        {
                            if (r.get (last) != to.container) r.add (to.container);  // parent was not last entry, so need to add it
                        }
                        else  // Path was 1-step, so check whether descending or ascending.
                        {
                            EquationSet p = user.container;  // The user's immediate equation set.
                            if (                p != null) p = p.container;  // The parent of the user's equation set. Conceptually, this is the value to check to see if we are ascending.
                            if (from == to  &&  p != null) p = p.container;  // If redirecting to the same variable, then "from" has already been moved up, so we need the grandparent instead.
                            if (p == from.container) r.add (to.container);  // ascending, so add parent
                        }
                    }
                }

                public void redirect (Variable from, Variable to)
                {
                    this.from = from;
                    this.to   = to;

                    // Copy list of users, so we can safely modify the original below.
                    List<Object> usedBy;
                    if (from.usedBy == null) usedBy = new ArrayList<Object> ();
                    else                     usedBy = new ArrayList<Object> (from.usedBy);

                    // Check variables that "from" uses. Some of these may be external writers to "from", which don't necessarily show up in usedBy.
                    if (from.uses != null)
                    {
                        List<Variable> uses = new ArrayList<Variable> (from.uses.keySet ());  // copy, in case it gets modified
                        for (Variable v : uses)
                        {
                            if (v.container == null) continue;  // Null container indicates a connection binding, which won't write to "from" and is not a member of the part being flattened.
                            if (v.reference.variable != from) continue;  // Skip variables that are not external writers of "from".
                            user = v;
                            if (from != to)
                            {
                                from.removeDependencyOn (user);
                                to.addDependencyOn (user);
                            }
                            adjustReference (v.reference);
                        }
                    }

                    // Check variables that use "from".
                    for (Object o : usedBy)
                    {
                        if (o instanceof Variable)
                        {
                            user = (Variable) o;
                            user.visit (this);  // RHS references
                            if (from.reference.variable == user)  // "user" has "from" as an external writer
                            {
                                if (from != to)
                                {
                                    user.removeDependencyOn (from);
                                    user.addDependencyOn (to);
                                }
                                // Since "from" is going away, we don't bother adjusting its reference to "user".
                            }
                        }
                        else if (o instanceof EquationSet)
                        {
                            // This case should not occur. Currently, only addSpecials() creates such a
                            // dependency, and it is for a non-singleton.
                            from.removeUser (o);
                            if (o == from.container) to.addUser (to.container);
                            else                     to.addUser (o);
                        }
                    }
                }
            }
            Redirector redirector = new Redirector ();

            //     For each variable v, one of 3 things will happen:
            //     1) v matches an existing $variable --> dependent references will be redirected, and v will be forgotten.
            //     2) v is a reference that matches an existing variable --> equations will be merged.
            //     3) v is unique --> v will be moved from s to this container. All dependent references remain valid, but resolution paths must be updated.
            //          Note that v may have the same name as a variable in this container. Prefixing will make the name unique.
            for (Variable v : s.variables)
            {
                // Adjust LHS references to work in this container.
                boolean couldNeedMerge = false;
                if (v.reference.variable == v)  // internal reference, which for LHS is exactly same as self-reference
                {
                    if (v.name.startsWith ("$"))
                    {
                        if (! v.hasUsers ()  &&  s.source.child (v.nameString ()) == null) continue;  // Ignore automatically-added $variables that are not used.

                        // Check if this is a conflicting $variable.
                        Variable d = find (v);
                        if (d != null)  // Already exists in this container, so redirect any references in v's dependents to d.
                        {
                            redirector.redirect (v, d);  // Only does something if v has users.
                            continue;  // Don't process v further
                        }
                    }
                    else
                    {
                        v.name = prefix + "." + v.name;
                    }
                }
                else  // external reference
                {
                    if (v.name.startsWith ("$up.")) v.name = v.name.substring (4);
                    else if (v.name.startsWith (name + ".")) v.name = v.name.substring (name.length () + 1);
                    List<Object> r = v.reference.resolution;
                    if (r.get (0) == EquationSet.this)
                    {
                        couldNeedMerge = true;
                        r.remove (0);
                    }
                }

                if (couldNeedMerge)  // An external reference whose first resolution step is this container.
                {
                    Variable v2 = find (v);
                    if (v2 != null)  // There is a matching variable, so must merge. The match could be (and often is) the direct target of the reference.
                    {
                        // Since this is an external reference, some other variable depends on v as an external writer.
                        // This user must be redirected appropriately.
                        if (v2 == v.reference.variable)  // direct up-reference
                        {
                            v2.removeDependencyOn (v);  // But don't replace with dependency from v2 to itself.
                        }
                        else  // Some other variable depends on v.
                        {
                            // v should only have one user.
                            redirector.redirect (v, v2);
                        }

                        v2.flattenExpressions (v);
                        continue;
                    }
                    // else fall through ...
                }

                // A distinct variable that needs to be moved up.
                // This is either an internal reference (always unique) or an external reference that does not overlap an existing variable.
                add (v);                     // Changes v.container, used by redirector.
                redirector.redirect (v, v);  // Adjust resolution paths of v's users.
            }

            //     Adjusts resolution paths that go through s.
            //     These paths do not target s itself, but rather its parents or deeper children.
            class Resolver implements Visitor
            {
                EquationSet child;  // The container being eliminated (same as "s" in outer code).
                EquationSet parent; // Holds "child". Remains after child is eliminated. (Same as EquationSet.this in outer code.)
                Variable    target; // The variable in "child" that should be the destination of the resolution path. If null, don't filter (process every reference).

                public boolean visit (Operator op)
                {
                    if (! (op instanceof AccessVariable)) return true;
                    AccessVariable av = (AccessVariable) op;
                    if (target != null  &&  av.reference.variable != target) return true;
                    adjustResolution (av.reference.resolution);
                    return false;
                }

                public void adjustResolution (List<Object> r)
                {
                    // If the path has been maintained in simplest form, then it passes through child only once.
                    // Our task is to find that point (if it exists) and elide child.
                    int last = r.size () - 1;
                    for (int i = 0; i <= last; i++)
                    {
                        Object o = r.get (i);
                        if (o != child) continue;
                        // Found child in path. Now update it.
                        r.remove (i);  // Always remove child
                        if (i == last) r.add (parent);  // The original path targeted child, so it should now target parent.
                        break;
                    }
                }

                public void resolve (EquationSet child, EquationSet parent)
                {
                    this.child  = child;
                    this.parent = parent;
                    // The variables of child itself do not need to be processed. They are handled in other ways by the outer code.
                    // Instead, we immediately descend to child parts.
                    for (EquationSet p : child.parts) resolveRecursive (p);
                }

                public void resolveRecursive (EquationSet current)
                {
                    for (Variable v : current.variables)
                    {
                        if (v.reference.variable != v) adjustResolution (v.reference.resolution);
                        target = null;
                        v.visit (this);
                        if (v.usedBy == null) continue;
                        target = v;
                        for (Object o : v.usedBy)
                        {
                            if (o instanceof Variable)
                            {
                                Variable user = (Variable) o;
                                if (user.reference.variable != user) adjustResolution (user.reference.resolution);
                                user.visit (this);
                            }
                            // else if (o instanceof EquationSet)  // should not be a relevant case.
                            //   A possibility is o==s. However, this should not occur if flatten() is run immediately after resolveRHS().
                        }
                    }
                    for (EquationSet p : current.parts) resolveRecursive (p);
                }
            }
            Resolver resolver = new Resolver ();
            resolver.resolve (s, this);

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
        Marks variables with "externalRead" and "externalWrite", as needed.
        Determines whether an external write should be evaluated in the local or global context.
        Depends on results of:
            resolveLHS(), resolveRHS() -- Establishes references and dependencies between variables.
            flatten() -- Changes references and dependencies. In some cases, folds expressions together so dependencies go away.
    **/
    public void findExternal ()
    {
        for (EquationSet p : parts) p.findExternal ();

        class Externalizer implements Visitor
        {
            Variable v;
            boolean allGlobal;

            public boolean visit (Operator op)
            {
                if (op instanceof AccessVariable)
                {
                    AccessVariable av = (AccessVariable) op;
                    Variable target = av.reference.variable;
                    if (target.container != v.container)  // v references something other than itself, which implies that the target is outside this equation set.
                    {
                        target.addAttribute ("externalRead");
                        target.removeAttribute ("temporary");
                    }
                    if (! target.hasAny ("global", "constant")) allGlobal = false;
                }
                return true;
            }
        }
        Externalizer externalizer = new Externalizer ();

        for (Variable v : variables)
        {
            externalizer.v = v;
            externalizer.allGlobal = true;
            v.visit (externalizer);

            Variable vr = v.reference.variable;  // for convenience
            if (vr != v)
            {
                v.addAttribute ("reference");
                vr.addAttribute ("externalWrite");
                vr.removeAttribute ("temporary");
                if (externalizer.allGlobal  &&  ! v.hasAttribute ("local")  &&  vr.hasAttribute ("global"))
                {
                    v.addAttribute ("global");
                    v.convertToGlobal ();
                }
            }
        }
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
            Variable v = new Variable (c.key ());
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
        Variable v = new Variable ("$live");  // $live does not require a set method, so create it directly
        if (add (v))
        {
            v.unit = AbstractUnit.ONE;
            v.addAttribute ("constant");  // default. Actual values should be set by setAttributeLive()
            EquationEntry e = new EquationEntry (v, "");
            e.expression = new Constant (1);
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

        v = new Variable ("$t");
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
            v.unit = UnitValue.seconds;  // seconds per cycle, except cycle is not a unit
            v.equations = new TreeSet<EquationEntry> ();
        }

        if (container == null)        // top-level model
        {
            v = new Variable ("$p");  // must have a termination condition
            if (add (v))              // but it doesn't
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
            boolean singleton = isSingleton ();

            v = new Variable ("$index");
            if (add (v))
            {
                v.unit = AbstractUnit.ONE;
                v.equations = new TreeSet<EquationEntry> ();
                if (singleton)
                {
                    v.addAttribute ("constant");
                    EquationEntry e = new EquationEntry (v, "");
                    e.expression = new Constant (0);
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
            v = new Variable ("$n");
            if (add (v))
            {
                v.unit = AbstractUnit.ONE;
                v.addAttribute ("constant");  // default. Actual values set by client code.
                EquationEntry e = new EquationEntry (v, "");
                e.expression = new Constant (1);
                e.expression.unit = AbstractUnit.ONE;
                v.add (e);
            }
        }
    }

    /**
        Remove any variables (particularly $variables) that are not referenced by some
        equation. These values do not input to any other calculation, and they are not
        displayed. Therefore they are a waste of time and space. Does not remove
        $variables with explicit assignments, because these declare something about
        the behavior of the model even if not directly referenced by other variables.
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
            // External combiners which have no effect on the target variable. This is a rare case.
            boolean emptyCombinerReference =  v.hasAttribute ("reference")  &&  v.isEmptyCombiner ();

            if ((v.hasUsers ()  ||  v.hasAttribute ("externalWrite"))  &&  ! emptyCombinerReference) continue;

            // Even if a $variable has no direct users, we must respect any statements about it.
            // Exceptions:
            // * $index -- if it was created constant by addSpecials()
            // * $t' -- if it is constant and matches constant $t' in container. This may have been created by findConstants().
            if (v.equations.size () > 0  &&  v.name.contains ("$")  &&  ! v.name.equals ("$index"))
            {
                // Check for constant $t'
                boolean sharedDt = false;
                if (container != null  &&  v.name.equals ("$t")  &&  v.order == 1  &&  v.hasAttribute ("constant"))
                {
                    Variable cdt = container.find (v);
                    if (cdt != null  &&  cdt.hasAttribute ("constant"))
                    {
                        double  value =   v.equations.first ().expression.getDouble ();
                        double cvalue = cdt.equations.first ().expression.getDouble ();
                        sharedDt =  value == cvalue;
                    }
                }

                if (! sharedDt)  // Check for general case
                {
                    if (v.name.startsWith ("$")) continue;
                    String[] pieces = v.name.split ("\\.");
                    if (pieces.length == 2  &&  endpointSpecials.contains (pieces[1])) continue;
                    // else fall through ...
                }
            }

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

            // This is inefficient because it iterates over all lower orders for each order found,
            // roughly O(n^2). However, there will rarely be more than one order to process.
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
                    if (last.hasAttribute ("global")) vo.addAttribute ("global");
                    found = vo;
                }
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
        Variable query = new Variable ("$init");
        Variable init  = find (query);
        if (init == null)
        {
            init = query;
            init.unit = AbstractUnit.ONE;
            init.addAttribute ("constant");  // TODO: should really be "initOnly", since it changes value during (at the end of) the init cycle.
            EquationEntry e = new EquationEntry (init, "");
            e.expression = new Constant (value);
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
        return e.expression.getDouble () != 0;
    }

    public void setConnect (float value)
    {
        Variable query   = new Variable ("$connect");
        Variable connect = find (query);
        if (connect == null)
        {
            connect = query;
            connect.unit = AbstractUnit.ONE;
            connect.addAttribute ("constant");
            EquationEntry e = new EquationEntry (connect, "");
            e.expression = new Constant (value);
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
        return e.expression.getDouble () != 0;
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
            Variable type = v.reference.variable;  // The actual variable. It is possible for an equation in one part to indicate a type split in another part.
            if (! type.name.equals ("$type")) continue;

            EquationSet container = type.container;
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

    public class Conversion implements Comparable<Conversion>
    {
        public EquationSet from;
        public EquationSet to;

        public Conversion (EquationSet from, EquationSet to)
        {
            this.from = from;
            this.to   = to;
        }

        @Override
        public int compareTo (Conversion that)
        {
            int result = from.name.compareTo (that.from.name);
            if (result != 0) return result;
            return to.name.compareTo (that.to.name);
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
        for (ArrayList<EquationSet> split : splits) result.addAll (split);
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
        Depends on results of: addSpecials(), fillIntegratedVariables(), findConstants(), collectSplits(), findInitOnly()
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
        if (find (new Variable ("$n", 1)) != null) lethalN = true;  // Conservatively, assume any order of derivative could decrease $n.

        // Determine if $p has an assignment less than 1
        lethalP = false;  // Because there is a use-case for re-running this analysis, specifically for lethalP.
        Variable p = find (new Variable ("$p"));
        if (p != null)
        {
            // Determine if any equation is capable of setting $p to something besides 1
            ReplacePhaseIndicators replacePhase = new ReplacePhaseIndicators ();
            for (EquationEntry e : p.equations)
            {
                if (e.expression.isScalar ()  &&  e.expression.getDouble () >= 1) continue;
                if (e.expression instanceof OperatorLogical  &&  p.hasAttribute ("initOnly")) continue;

                // Now we have an equation that evaluates to something other than 1.
                // If this occurs anywhere but connect, then $p is lethal.
                if (e.condition != null)
                {
                    replacePhase.init = 1;
                    Operator test = e.condition.deepCopy ().transform (replacePhase).simplify (p, true);
                    if (test.isScalar ()  &&  test.getDouble () == 0)  // Does not fire during init phase
                    {
                        replacePhase.init = 0;
                        test = e.condition.deepCopy ().transform (replacePhase).simplify (p, true);
                        if (test.isScalar ()  &&  test.getDouble () == 0) continue;  // Does not fire during update phase
                    }
                }
                lethalP = true;
                break;
            }
        }
        if (find (new Variable ("$p", 1)) != null) lethalP = true;

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
                // "accessor" is the lightest-weight representation of $live, requiring no storage space.
                // "initOnly" causes space to be allocated for $live.
                // Processing of some lethal conditions require storage. These take precedence.
                // Stored $live works for all lethal conditions.
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
            else if (v.name.equals ("$connect")  ||  v.name.equals ("$init")  ||  v.name.equals ("$live")  ||  v.name.equals ("$p")  ||  v.name.equals ("$n")  ||  (v.name.equals ("$t")  &&  v.order == 1))
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

    /**
        Assigns each variable as the parent of its equations.
        This is used by determineExponents() and by the Internal simulator.
        The assignment is only valid as long as no more optimization is done.
        Thus, it should be the last step before running one of those processes.
    **/
    public void assignParents ()
    {
        for (Variable v : variables)
        {
            // Set v as the parent of all its equations
            int inputMatrices = 0;
            for (EquationEntry e : v.equations)
            {
                if (e.expression != null)
                {
                    e.expression.parent = v;
                    if (e.expression.isMatrixInput ()) inputMatrices++;
                }
                // e.condition.parent should always be null, because it is not actually assigned to the variable.
            }
            if (inputMatrices > 0  &&  inputMatrices == v.equations.size ()  &&  v.assignment == Variable.REPLACE) v.addAttribute ("MatrixPointer");
        }
        for (EquationSet s : parts) s.assignParents ();
    }

    public void determineExponents ()
    {
        ExponentContext context = new ExponentContext (this);
        determineExponentsInit (context);
        int limit = context.depth * 2 + 2;  // One cycle for each variable to get initial exponent, and another cycle for each var to influence itself, plus a couple more for good measure. 

        while (true)
        {
            System.out.println ("-----------------------------------------------------------------");
            System.out.println ("top of loop " + limit);
            context.changed = false;
            context.updateTime ();
            context.updateInputs ();
            context.finalPass =  limit-- == 0;
            determineExponentsEval (context);
            if (context.finalPass  ||  ! context.changed)
            {
                PrintStream err = Backend.err.get ();
                if (context.changed  &&  context.overflows.isEmpty ())  // Some equation changed, but it did not produce a change to Variable.exponent.
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
        ps.println ("Results of fixed-point analysis. Column 1 is expected median absolute value as a power of 10. Column 2 is binary power of MSB.");
        if (dumpMedians (ps, context)) throw new AbortRun ();
    }

    /**
        Stores lists of objects needed for global agreement on time exponents.
    **/
    public static class ExponentContext
    {
        public int            depth;         // Largest number of equations in any equation set. It's unlikely that any dependency cycle will exceed this.
        public int            exponentTime;  // of overall simulation. Computed from available information such as $t' and exponentTimeExplicit.
        public List<Variable> overflows = new ArrayList<Variable> ();
        public Variable       from;          // Current variable under evaluation.
        public boolean        finalPass;
        public boolean        changed;       // Some operator changed somewhere in the model during the current pass.

        // Info used to determine exponentTime
        protected boolean                          haveDuration;
        protected List<Variable>                   dt     = new ArrayList<Variable> ();              // All distinct occurrences of $t' in the model (zero or one per equation set)
        protected HashMap<Object,ArrayList<Input>> inputs = new HashMap<Object,ArrayList<Input>> (); // Inputs that have "time" flag must agree on exponentTime.

        public ExponentContext (EquationSet root)
        {
            double duration = root.metadata.getOrDefault (0.0, "duration");
            haveDuration =  duration != 0;
            if (haveDuration) exponentTime = (int) Math.floor (Math.log (duration) / Math.log (2));
            else              exponentTime = Operator.UNKNOWN;
        }

        public ExponentContext (int exponentTime)
        {
            this.exponentTime = exponentTime;
        }

        public void updateTime ()
        {
            if (haveDuration) return;  // Don't override exponent based on duration. It is the most accurate.

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
            if (min == Integer.MAX_VALUE) exponentTime = 0;        // If no value of $t' has been set yet, estimate duration as 1s, and $t has exponent=0.
            else                          exponentTime = min + 20; // +20 allows one million minimally-sized timesteps, each with 10 bit resolution
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

    public void determineExponentsInit (ExponentContext context)
    {
        context.depth = Math.max (context.depth, variables.size ());

        for (Variable v : variables)
        {
            // Collect all $t'
            Variable r = v.reference.variable;
            if (r.name.equals ("$t")  &&  r.order == 1  &&  ! context.dt.contains (r)) context.dt.add (r);

            // Collect all input() calls which use "time" mode.
            v.visit (new Visitor ()
            {
                public boolean visit (Operator op)
                {
                    if (op instanceof Input)
                    {
                        Input i = (Input) op;
                        if (i.usesTime ())
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
                                ArrayList<Input> list = context.inputs.get (key);
                                if (list == null)
                                {
                                    list = new ArrayList<Input> ();
                                    context.inputs.put (key, list);
                                }
                                list.add (i);
                            }
                        }
                    }
                    return true;
                }
            });
        }
        for (EquationSet s : parts) s.determineExponentsInit (context);
    }

    public void determineExponentsEval (ExponentContext context)
    {
        for (int i = ordered.size () - 1; i >= 0; i--)  // Process variables in reverse of dependency order, to maximize propagation of information in each pass.
        {
            Variable v = ordered.get (i);
            int centerLast   = v.center;
            int exponentLast = v.exponent;
            v.determineExponent (context);
            if (v.changed)
            {
                context.changed = true;
                if (context.finalPass)
                {
                    if (v.exponent != exponentLast  &&  ! context.overflows.contains (v)) context.overflows.add (v);
                    v.center   = centerLast;
                    v.exponent = exponentLast;
                }
            }
        }
        for (EquationSet s : parts) s.determineExponentsEval (context);
    }

    public void determineExponentNext ()
    {
        for (EquationSet s : parts) s.determineExponentNext ();
        for (Variable v : variables) v.determineExponentNext ();
    }

    /**
        Update the newly-created operators in a subset of variables that has been simplified for a specific phase.
    **/
    public static void determineExponentsSimplified (List<Variable> list)
    {
        // Determine exponentTime.
        // This value is only used to update $t and $t', and it is available from either $t or $t',
        // if one of them exists in the list.
        int exponentTime = Operator.UNKNOWN;
        for (Variable v : list)
        {
            if (v.name.equals ("$t"))
            {
                exponentTime = v.exponent;
                break;
            }
        }
        ExponentContext context = new ExponentContext (exponentTime);  // Could be UNKNOWN, but if so it won't hurt anything, because it is only needed for $t.

        // Update any newly-created operators.
        for (Variable v : list)
        {
            int centerLast   = v.center;
            int exponentLast = v.exponent;
            v.determineExponent (context);
            if (v.changed)  // Force exponent and center to remain the same.
            {
                v.center   = centerLast;
                v.exponent = exponentLast;
            }
            v.determineExponentNext ();
        }
    }

    /**
        For debugging fixed-point analysis
    **/
    public void dumpExponents ()
    {
        for (Variable v : variables) v.dumpExponents ();
        for (EquationSet s : parts) s.dumpExponents ();
    }

    /**
        @return true if we should abort the run. false if it is OK to proceed.
    **/
    public boolean dumpMedians (PrintStream ps, ExponentContext context)
    {
        boolean result = false;

        class VisitorExponentSanity implements Visitor
        {
            boolean sane = true;
            boolean warningExp = false;
            boolean warningPow = false;

            public boolean visit (Operator op)
            {
                if (op instanceof AccessVariable) return sane;

                String name = op.toString ();
                if      (op instanceof Function) name += "()";
                else if (op instanceof Constant) name  = "constant " + name;
                else                             name  = "operator"  + name;

                if (op.center < 0  ||  op.center > Operator.MSB)
                {
                    sane = false;
                    String flow = op.center < 0 ? "underflow" : "overflow";
                    ps.println ("\t\t  ERROR: " + name + " produces " + flow + " (center = " + op.center + ")");
                }
                else if (Math.abs (op.exponent) > 128)
                {
                    ps.println ("\t\t  WARNING: " + name + " produces large exponent (" + op.exponent + ")");
                }
                else if (op instanceof Exp)
                {
                    Exp e = (Exp) op;
                    if (! warningExp  &&  (e.operands.length < 2  ||  ! e.operands[1].getString ().contains ("median")))
                    {
                        ps.println ("\t\t  WARNING: exp() is very sensitive. If input values vary far from 0, provide a hint for median output value.");
                        warningExp = true;
                    }
                }
                else if (op instanceof Power)
                {
                    Power p = (Power) op;
                    if (! warningPow  &&  p.median == null)
                    {
                        if (p.isFunction) ps.println ("\t\t  WARNING: pow() is very sensitive. For best results, provide a hint for median output value.");
                        else              ps.println ("\t\t  WARNING: operator^ is very sensitive. For best results, use pow() and provide a hint for median output value.");
                        warningPow = true;
                    }
                }

                return sane;
            }
        };
        VisitorExponentSanity visitor = new VisitorExponentSanity ();

        for (Variable v : variables)
        {
            if (v.hasAttribute ("dummy")) continue;

            if (v.center < 0  ||  v.center > Operator.MSB) result = true;  // Must abort run, because numbers will almost certainly go out of range.
            // No need to warn about large v.exponent, because the user will be able to view the list.

            // Convert center power to an approximate decimal value.
            int centerPower = v.exponent - Operator.MSB + v.center;
            String base10 = Integer.toString ((int) Math.floor (centerPower / b2d));
            String exponent = Integer.toString (v.exponent);
            if (v.exponent == Operator.UNKNOWN)
            {
                base10 = "???";
                exponent = "???";
            }

            ps.println ("  " + base10 + "\t" + exponent + "\t" + v.fullName ());

            if (context.overflows.contains (v)) ps.println ("\t\t  WARNING: Magnitude did not converge. Add hint (median=median_absolute_value).");

            visitor.sane       = true;
            visitor.warningExp = false;
            visitor.warningPow = false;
            v.visit (visitor);
            if (! visitor.sane) result = true;
        }
        for (EquationSet s : parts) if (s.dumpMedians (ps, context)) result = true;
        return result;
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
        Variable p = find (new Variable ("$p"));
        if (p == null) return;
        if (p.equations.size () != 1) return;

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

        if (! (variable instanceof AccessVariable)) return;
        if (! ((AccessVariable) variable).name.equals ("$t")) return;

        // Method 1 -- check for reference to simple value, for example a "duration" parameter
        if (value instanceof AccessVariable)
        {
            AccessVariable av = (AccessVariable) value;
            Variable v = av.reference.variable;
            if (v.equations.size () == 1)
            {
                EquationEntry e = v.equations.first ();
                if (e.condition == null  &&  e.expression instanceof Constant)
                {
                    metadata.set (e.expression.getDouble (), "duration");
                    return;
                }
            }
        }

        // Method 2 -- try to calculate the value
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

    /**
        @param attribute The string to add to the tags associated with each given variable.
        @param withOrder Restricts name matching to exactly the same order of derivative,
        that is, how many "prime" marks are appended to the variable name.
        When false, matches any variable with the same base name.
        @param withPrefix Matches variable names that end with dot followed by one of the target names.
        @param names A set of targets to search for and tag.
    **/
    public void addAttribute (String attribute, boolean withOrder, boolean withPrefix, String... names)
    {
        for (EquationSet s : parts)
        {
            s.addAttribute (attribute, withOrder, withPrefix, names);
        }

        for (Variable v : variables)
        {
            String vname = v.name;
            if (withOrder) vname = v.nameString ();
            for (String n : names)
            {
                if (n.equals (vname)  ||  withPrefix  &&  vname.endsWith ("." + n))
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
            findExternal() -- marks "externalWrite" variables, which cannot be "constant"
    **/
    public void findConstants ()
    {
        while (findConstantsEval ());
    }

    protected boolean findConstantsEval ()
    {
        boolean changed = false;
        for (Variable v : variables)
        {
            if (v.simplify ()) changed = true;

            // Check if we have a constant
            // "constant", "initOnly" -- The tag was specially added so presumably correct.
            // "externalWrite" -- Regardless of the local math, a variable that gets written is not constant.
            if (v.hasAny ("constant", "initOnly", "externalWrite")) continue;

            if (v.derivative != null)  // A variable with a derivative is usually not constant, with the following exceptions ...
            {
                // 1) The variable is assigned an unconditional constant. This overrides the derivative in every cycle.
                // 2) The derivative is constant zero. In that case, the derivative has no effect.
                //    a) Pure circular dependency with an implicit integrand, and derivative evaluates to zero given initial value (zero) of integrand.
                //       In this case, the integrand will never change, so the derivative will continue to evaluate to zero.

                // 1) Check for unconditional constant.
                if (v.equations.size () == 1)
                {
                    EquationEntry e = v.equations.first ();
                    if (e.expression instanceof Constant  &&  e.condition == null)  // Must be unconditional. No exception for $init. (Non-integrated variables do get an exception for $init; see below.)
                    {
                        changed = true;
                        v.addAttribute ("constant");
                        v.removeDependencyOn (v.derivative);
                        v.derivative = null;
                        continue;
                    }
                }

                // 2a) Check for pure circular dependency.
                Variable top = v;
                while (top.uses != null  &&  top.uses.size () == 1)  // This single dependency must be the derivative.
                {
                    if (top.derivative != null)
                    {
                        top = top.derivative;
                        continue;
                    }

                    // "top" is now the highest derivative.
                    // Its single dependency is not a derivative, but could be anything else.
                    if (top.uses.containsKey (v))  // This is a pure circular dependency.
                    {
                        // Evaluate top. Initial value of v must be zero, because otherwise it would depend on other variables or be trapped above as an unconditional constant.
                        // We can only make this assumption here.
                        Instance instance = new Instance ()
                        {
                            public Scalar zero = new Scalar (0);

                            // all AccessVariable objects will reach here first.
                            public Type get (VariableReference r) throws EvaluationException
                            {
                                return zero;
                            }
                        };
                        Type result = top.eval (instance);
                        if (result instanceof Scalar  &&  ((Scalar) result).value == 0)
                        {
                            changed = true;
                            top.addAttribute ("constant");
                            top.removeDependencies ();  // Should just be v, but there may be multiple references.
                            top.equations.clear ();
                            EquationEntry e = new EquationEntry (top, "");
                            e.expression = new Constant (0);
                            top.add (e);
                        }
                    }
                    break;  // And fall through to case 2. If top is immediately v.derivative, then it will be detached in this iteration.
                }

                // 2) Check if derivative is constant zero.
                if (! v.derivative.hasAttribute ("constant")) continue;
                EquationEntry e = v.derivative.equations.first ();
                if (! ((Constant) e.expression).value.isZero ()) continue;
                changed = true;
                v.removeDependencyOn (v.derivative);
                v.derivative = null;
                if (v.equations.isEmpty ())
                {
                    // An empty equation list indicates the variable was added as an integrated value.
                    // Since the derivative went away, the variable will now be constant zero.
                    // $t, $t', $index an $type are also added without equations.
                    if (v.name.equals ("$t")  &&  v.order < 2) continue;
                    v.addAttribute ("constant");
                    e = new EquationEntry (v, "");
                    e.expression = new Constant (0);
                    v.add (e);
                    continue;  // Already determined that v is constant, so done with it.
                }
                // v is now effectively an ordinary variable (no derivative) with equation(s), so fall through ...
            }

            if (v.equations.size () != 1)
            {
                if (v.equations.isEmpty ())  // Special cases for $variables
                {
                    if (v.name.equals ("$index"))
                    {
                        // Check if $n has become constant 1, that is, if we have detected a singleton in later processing.
                        // In this case, $index should become constant 0.
                        Variable n = find (new Variable ("$n"));
                        if (n != null  &&  n.hasAttribute ("constant"))
                        {
                            if (n.equations.first ().expression.getDouble () == 1)
                            {
                                changed = true;
                                v.removeAttribute ("initOnly");
                                v.addAttribute ("constant");
                                v.unit = AbstractUnit.ONE;
                                v.equations = new TreeSet<EquationEntry> ();
                                EquationEntry e = new EquationEntry (v, "");
                                e.expression = new Constant (0);
                                e.expression.unit = AbstractUnit.ONE;
                                v.add (e);
                            }
                        }
                    }
                    else if (v.name.equals ("$t")  &&  v.order == 1  &&  container != null)  // $t'
                    {
                        // Copy constant $t' from container.
                        Variable parentDt = container.find (v);
                        if (parentDt.hasAttribute ("constant"))
                        {
                            changed = true;
                            v.addAttribute ("constant");
                            EquationEntry e = new EquationEntry (v, "");
                            v.add (e);

                            EquationEntry parentE = parentDt.equations.first ();
                            e.expression = new Constant (parentE.expression.getDouble ());
                            e.expression.unit = AbstractUnit.ONE;
                        }
                    }
                }
                continue;
            }

            // At this point, the variable satisfies most of the requirements to be constant.
            // It has no external writers or derivatives, so only its single equation can change it.
            // The remaining question is whether the equation is an unconditional constant.
            EquationEntry e = v.equations.first ();
            if (e.condition != null  &&  ! e.ifString.equals ("$init")) continue;  // Notice that a constant equation conditioned on $init is effectively unconditional.
            if (e.expression instanceof Constant)
            {
                changed = true;
                v.addAttribute ("constant");
                e.condition = null;  // in case it was $init
                e.ifString  = "";
            }
        }

        for (EquationSet s : parts)
        {
            if (s.findConstantsEval ()) changed = true;
        }
        return changed;
    }

    /**
        Mark all variables that can possibly be temporary.
        Some variables cannot be temporary:
        <ul>
        <li>Derivatives and integrated values.
        <li>Reductions.
        <li>Read or written outside the equation set.
        <li>Has forbidden attributes. This effectively excludes certain $variables.
        </ul>

        <p>Temporaries are not allowed to have cycles among themselves. This routine
        breaks any cycles by iteratively making the variable with the most cycles
        back into a state variable.
        Uses algorithm from "Finding All The Elementary Circuits of a Directed Graph"
        by Donald B. Johnson, SIAM J. Comput., Vol. 4, No. 1, March 1975.

        <p>Depends on results of:
        <ul>
        <li>findIntegrated() -- marks integrated values
        <li>resolveRHS() -- establishes dependency graph
        <li>findExternal() -- marks targets of external references
        <li>flatten() - optional, allows better optimization 
        <li>findConstants() -- optional, simplifies the equation set
        <li>removeUnused() -- optional, simplifies the equation set
        </ul>
        findInitOnly() should not be run before this method. This helps ensure that
        any "initOnly" attribute is there explicitly, for example on $index.
    **/
    public void findTemporary ()
    {
        for (EquationSet s : parts)
        {
            s.findTemporary ();
        }

        // Determine which variables can be temporary.
        ArrayList<Variable> temps = new ArrayList<Variable> ();
        ReplacePhaseIndicators replacePhase = new ReplacePhaseIndicators ();
        for (Variable v : variables)
        {
            v.before = null;  // v is not in the working set (yet).
            if (v.assignment != Variable.REPLACE) continue;  // No reductions. Usually a reductions will have some external writer and so be eliminated by the attribute test below, but that is not necessarily the case.
            if (v.order > 0) continue;  // No derivatives
            if (v.derivative != null) continue;  // No integrated values
            if (v.hasAny ("state", "constant", "initOnly", "readOnly", "instance", "reference", "accessor", "preexistent", "externalRead", "externalWrite", "cycle", "dummy")) continue;

            // A general requirement is that v not depend on its own current value.
            // * Must not have a cycle that leads back to self. This will be handled by cycle breaking below.
            // * Must have an equation that will always fire during update. (Otherwise, default comes from previous value.)
            if (! v.hasAttribute ("temporary"))
            {
                // See findInitOnly() for similar code.
                boolean hasDefault = false;
                for (EquationEntry e : v.equations)
                {
                    if (e.condition == null)
                    {
                        hasDefault = true;
                    }
                    else
                    {
                        // We only count the equation as a default if we are absolutely certain it will fire.
                        Operator test = e.condition.deepCopy ().transform (replacePhase).simplify (v, true);
                        if (test.isScalar ()  &&  test.getDouble () != 0) hasDefault = true;
                    }
                    if (hasDefault) break;
                }
                if (! hasDefault) continue;
            }

            temps.add (v);
            v.before = new HashSet<Variable> ();  // Stores "B" from the paper.
            v.priority = 0;  // Holds count of cycles through this vertex.
        }
        for (Variable v : variables) if (v.before == null) v.removeAttribute ("temporary");  // Just in case the user marked some variables as temporary which don't meet the basic criteria above, for example a derivative.

        // Eliminate cycles.
        int count = temps.size ();
        if (count == 0) return;  // nothing to do

        // Get initial counts using Johnson algorithm.
        for (int i = count - 1; i >= 0; i--)
        {
            // Unlike A_K from the paper, we do not find a strongly-connected component (SCC)
            // for the working set. Instead, we remove vertices as they get processed.
            // This is less efficient, but also less complicated. The proof of correctness
            // still holds, as it does not depend on using an SCC.

            // Clear attributes associated with working set.
            for (int j = 0; j <= i; j++)
            {
                Variable w = temps.get (j);
                w.before.clear ();
                w.blocked = false;
                w.visited = null;
            }
            Variable v = temps.get (i);
            v.circuit (v, 1);
            v.before = null;  // v is no longer in working set.
        }

        // Eliminate variables until there are no more cycles.
        ArrayList<Variable> A = new ArrayList<Variable> (temps);
        for (Variable v : temps) if (v.before == null) v.before = new HashSet<Variable> ();  // Restore working set.
        while (count > 0)
        {
            // Find variable with greatest number of cycles.
            Variable most = A.get (0);
            for (int i = 1; i < count; i++)
            {
                Variable v = A.get (i);
                if (v.hasAttribute ("temporary"))  // v is hinted
                {
                    if (most.hasAttribute ("temporary")  &&  v.priority > most.priority) most = v;
                    // Otherwise, a non-hinted item in best is better than any possible item in v.
                }
                else  // v is non-hinted
                {
                    if (most.hasAttribute ("temporary")) most = v;  // Non-hinted item in v is better than any possible item in best.
                    else if (v.priority > most.priority) most = v;
                }
            }
            if (most.priority == 0) break;

            // Release cycles through variable.
            if (most.hasAttribute ("temporary"))
            {
                Backend.err.get ().println (most.fullName () + " -- Hinted as temporary, but must be a state variable in order to break a cyclic dependency.");
                most.removeAttribute ("temporary");
            }
            for (Variable w : A)
            {
                w.before.clear ();
                w.blocked = false;
                w.visited = null;
            }
            most.circuit (most, -1);
            A.remove (most);
            most.before = null;
            count--;
        }
        for (Variable v : A) v.addAttribute ("temporary");

        // Release resources
        for (Variable v : temps) v.before = null;
    }

    /**
        Makes each drawX() equation dependent on all generic draw() equations.
        This forces generic draw() to execute before drawX(), allowing the user
        to avoid setting canvas configurations on every single drawX() call.
        This only works for simple dummy variables, but there is little reason
        for the user to create complex expressions with drawX(). The dummy variable
        can have multiple conditions. A self-dependency will not be created, so
        we avoid creating unnecessary state variables.
    **/
    public void addDrawDependencies ()
    {
        for (EquationSet p : parts) p.addDrawDependencies ();
        addDrawDependencies (variables);
    }

    public static void addDrawDependencies (Collection<Variable> list)
    {
        // First pass -- collect lists of draw() and drawX() variables.
        class DrawVisitor implements Visitor
        {
            Variable v;
            List<Variable> draw  = new ArrayList<Variable> ();
            List<Variable> drawX = new ArrayList<Variable> ();

            public boolean visit (Operator op)
            {
                if (op instanceof Draw.Shape)
                {
                    drawX.add (v);
                    return false;
                }
                if (op instanceof Draw)
                {
                    draw.add (v);
                    return false;
                }
                return true;
            }
        }
        DrawVisitor visitor = new DrawVisitor ();

        for (Variable v : list)
        {
            visitor.v = v;
            v.visit (visitor);
        }

        // Second pass -- create dependencies.
        for (Variable dX : visitor.drawX)
        {
            for (Variable d : visitor.draw)
            {
                if (d == dX) continue;  // Eliminates direct self-cycle, but cannot eliminate longer cycles.
                dX.addDependencyOn (d);
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
            v.before   = new HashSet<Variable> ();
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
            v.before   = new HashSet<Variable> ();
            v.priority = 0;
        }
        for (Variable v : list) v.setBefore (true);

        PriorityQueue<Variable> queueDependency = new PriorityQueue<Variable> (list.size (), new Comparator<Variable> ()
        {
            public int compare (Variable a, Variable b)
            {
                // Break a circular dependency between a regular and a temporary in favor of the temporary.
                // That is, the temporary should be evaluated first, using zero for the regular variable.
                if (a.before.contains (b)  &&  b.before.contains (a))  // circular dependency
                {
                    if (a.hasAttribute ("temporary")) return -1;
                    if (b.hasAttribute ("temporary")) return  1;
                }

                // As a heuristic, the variable with the most downstream dependencies should be evaluated first.
                return b.before.size () - a.before.size ();
            }
        });
        queueDependency.addAll (list);
        Variable v;
        while ((v = queueDependency.poll ()) != null) v.setPriority (1, null);

        PriorityQueue<Variable> queuePriority = new PriorityQueue<Variable> (list.size (), new Comparator<Variable> ()
        {
            public int compare (Variable a, Variable b)
            {
                return a.priority - b.priority;
            }
        });
        queuePriority.addAll (list);
        list.clear ();
        while ((v = queuePriority.poll ()) != null) list.add (v);
    }

    public void simplify (String phase, List<Variable> list)
    {
        simplify (phase, list, null);
    }

    /**
        Optimizes a given subset of variables with the assumption that the specified phase indicator is true.
        Starts by replacing the variables with deep copies so that any changes do not
        damage the original equation set.
        @param bless Tag the given temporary variable as having a user, so that it does not get
        removed during optimization.
    **/
    public void simplify (String phase, List<Variable> list, Variable bless)
    {
        Variable.deepCopy (list);
        if (bless != null)
        {
            int i = list.indexOf (bless);
            if (i >= 0) list.get (i).addUser (this);
        }

        // Assign priority field, so that ReplaceConstants can know when to assume a variable reference is zero.
        // This optimization is only available during the init and connect phases.
        // In other phases, it is necessary to look up current value.
        if (phase.equals ("$init")  ||  phase.equals ("$connect"))
        {
            determineOrderInit (list);
            int i = 0;
            for (Variable v : list) v.priority = i++;
        }

        ReplaceConstants replace = new ReplaceConstants (phase);
        for (Variable v : list)
        {
            // Check for default equation
            TreeSet<EquationEntry> nextEquations = new TreeSet<EquationEntry> ();
            EquationEntry defaultImplicit = null;
            EquationEntry defaultExplicit = null;
            for (EquationEntry e : v.equations)
            {
                if (e.ifString.isEmpty ()) defaultImplicit = e;
                else if (e.ifString.equals (phase)) defaultExplicit = e;
                else nextEquations.add (e);
            }
            //   An equation conditioned only on the current phase must be treated as the default.
            //   It overrules an equation with no condition.
            if (defaultExplicit != null)
            {
                // Convert the explicit default into an implicit default.
                defaultExplicit.ifString = "";
                defaultExplicit.condition = null;
                nextEquations.add (defaultExplicit);
            }
            else if (defaultImplicit != null)  // If there was no explicit default, then add back the equation with no condition, if it was present.
            {
                nextEquations.add (defaultImplicit);
            }
            v.equations = nextEquations;

            // Replace variables known to be constant, particularly the current phase indicator.
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

    public class ReplaceConstants implements Transformer
    {
        public Variable self;
        public String   phase;
        public boolean  init;
        public boolean  priorityKnown = true;

        public ReplaceConstants (String phase)
        {
            this.phase = phase;
            init       = phase.equals ("$init");
        }

        public Operator transform (Operator op)
        {
            if (op instanceof AccessVariable)
            {
                AccessVariable av = (AccessVariable) op;
                Variable v = av.reference.variable;
                Operator result = null;
                if      (phase .equals   (av.name)) result = new Constant (1);
                else if (phases.contains (av.name)) result = new Constant (0);
                else if (v.name.equals ("$index"))
                {
                    if (v.container.isSingleton ()) result = new Constant (0);
                }
                else if (v.name.equals ("$type"))
                {
                    // $type is usually 0, except after a type split. We can't know
                    // when that will happen. Also, $type can be nonzero during update
                    // as well as init, so phase doesn't change the answer here.
                    return null;
                }
                else if (init  &&  v.container == self.container  &&  priorityKnown  &&  v.priority >= self.priority)  // Reference to a variable that has not yet been assigned by init.
                {
                    // Ordinary variables have default value 0 until they are assigned by init.
                    // If a variable could be initialized to something besides 0 before init, then we must treat it as unknown.
                    // * Variables could be assigned by a type split before init runs.
                    // * $p defaults to 1 until explicitly assigned. Only unknown if this is the result of a split.
                    // * $n will return the current size of the population. If it shows up here, it is unknown.
                    // * $t and $t' will have values from the current event, and thus are unknown.
                    // Other $variables will either be constant and therefore not show up here,
                    // or they will not yet be assigned. Treat them as ordinary variables with value 0.
                    if (v.name.equals ("$n")  &&  v.order == 0) return null;
                    if (v.name.equals ("$t")  &&  v.order <= 1) return null;

                    // Was it assigned by a type split?
                    boolean splitTarget = false;
                    for (EquationSet s : splitSources)
                    {
                        if (s.find (v) != null)
                        {
                            // This does not filter forbidden attributes.
                            // The effect is that we treat more variables as having unknown value.
                            // This is the conservative thing to do.
                            splitTarget = true;
                            break;
                        }
                    }
                    if (! splitTarget)
                    {
                        if (v.name.equals ("$p")  &&  v.order == 0) result = new Constant (1);
                        else                                        result = new Constant (0);
                    }
                }
                if (result != null) result.parent = av.parent;
                return result;
            }
            if (op instanceof Event)  // Events never fire during init or connect phases
            {
                if (phase.equals ("$live")) return null;
                Constant result = new Constant (0);
                result.parent = op.parent;
                return result;
            }
            return null;
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
            if (v.derivative != null) v.addDependencyOn (v.derivative);
        }
    }

    /**
        Identifies variables that have differential order higher than 0.
        Also tags any temporary variables that a derivative depends on.
        Depends on results of:
            fillIntegratedVariables() to get lower-order derivatives
            resolveRHS() to establish dependencies
            findTeporary() to identify temporaries
            removeUnused() to possibly avoid tagging some temporaries as dependencies
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
        Identifies variables that only change during init.
        Also marks variables that change by both integration and update equations, so they will be processed in both ways.
        The additional attribute "updates" is necessary in this case, because an integrated value can never be "initOnly",
        so we can't use "initOnly" to distinguish whether or not update equations fire.

        The criteria for "initOnly" are:
        <ul>
        <li>not "constant"
        <li>not integrated
        <li>not a reduction -- This is a bit conservative. It might be possible to do a reduction just once if all the sources are initOnly.
            For example, if the contributors are all children of the target part and their population sizes are constant,
            then the reduction could be completed during the init cycle of the target part.
        <li>one of:
            <ul>
            <li>no condition is true when $init=0 (that is, during update). Implies that all equations have a non-empty condition.
            <li>all equations and their conditions depend only on "constant" or "initOnly" variables.
            <li>the same equation always fires during both init and update, and it depends only on "constant" or "initOnly" variables.
                Why only one equation? Multiple equations imply the value could change via conditional selection.
            </ul>
        </ul>
        Depends on results of: findConstants()
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

                if (av.reference == null  ||  av.reference.variable == null)  // guard against failed resolution. TODO: is this check really necessary?
                {
                    isInitOnly = false;
                }
                else  // successful resolution
                {
                    Variable r = av.reference.variable;
                    if (! r.hasAny ("initOnly", "constant")) isInitOnly = false;
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
            //       Similarly, an external reference needs to fire every cycle to ensure its target accumulates the correct value,
            //       unless it can be established that the value does not change after the init cycle of the target.
            if (v.hasAny ("initOnly", "constant", "dummy", "externalWrite", "reference")) continue;

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
                    Operator test = e.condition.deepCopy ().transform (replacePhase).simplify (v, true);
                    if (! test.isScalar ()  ||  test.getDouble () != 0)
                    {
                        firesDuringInit++;
                        init = e;
                    }

                    // update
                    replacePhase.init = 0;
                    test = e.condition.deepCopy ().transform (replacePhase).simplify (v, true);
                    if (! test.isScalar ()  ||  test.getDouble () != 0)
                    {
                        firesDuringUpdate++;
                        update = e;
                    }
                }
            }

            int count = v.equations.size ();  // Used in the last case below.
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
            else if (count > 0  &&  firesDuringInit == count  &&  firesDuringUpdate == count)
            {
                visitor.isInitOnly = true;
                for (EquationEntry e : v.equations) e.visit (visitor);
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
                        Operator test = e.condition.deepCopy ().transform (replacePhase).simplify (v, true);
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
                        e.condition = e.condition.transform (replaceInit).simplify (v, false);
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
        Depends on results of: findConstants()
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
                // * setting "externalRead" below forces $n to be stored, if it isn't already
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
        Checks if connection requires polling.
        The result is a metadata tag on $p. If the tag exists, polling is required and the tag gives the time period
        (0 for every cycle, >0 for quantity of time to complete one poll).
        The user can specify this tag ahead of time. Specifying -1 will suppress polling even if it is required.
        Specifying poll >= 0 will not force polling if it is not required. Instead, the tag will be cleared by this function.
        Depends on results of: findInitOnly()
        Must be run before purgeInitOnlyTemporary(), because that function removes information critical for our processing.
        findConnectionMatrix() will clear the metadata tag if it succeeds, because that kind of connection is a one-time process,
        no need for polling.
    **/
    public void determinePoll ()
    {
        for (EquationSet s : parts) s.determinePoll ();

        if (connectionBindings == null) return;
        Variable p = find (new Variable ("$p"));
        if (p == null) return;

        // Look up metadata to determine polling period.
        String pollString = p.metadata.getOrDefault ("1s", "poll");  // Default is one full poll per second. After determinePoll() finishes, the default will be no polling. This simplifies later processing.
        double pollValue  = new UnitValue (pollString).get ();
        if (pollValue < 0)  // Don't do analysis if polling is explicitly suppressed.
        {
            p.metadata.clear ("poll");
            return;
        }

        List<EquationEntry> fires        = new ArrayList<EquationEntry> ();
        boolean             firesBoolean = true;  // All equations in "fires" return 0 or 1.
        ReplacePhaseIndicators replacePhase = new ReplacePhaseIndicators ();
        replacePhase.connect = 1;  // And other indicators are 0
        for (EquationEntry e : p.equations)
        {
            // Assume a condition always fires, unless we can prove it does not.
            boolean couldFire   = true;
            boolean alwaysFires = true;
            if (e.condition != null)
            {
                Operator test = e.condition.deepCopy ().transform (replacePhase).simplify (p, true);
                if (test.isScalar ()) couldFire = alwaysFires = test.getDouble () != 0;
                else                  alwaysFires = false;
            }
            if (couldFire)
            {
                fires.add (e);
                Operator expression = e.expression.deepCopy ().transform (replacePhase).simplify (p, true);
                if (expression.isScalar ())
                {
                    double value = expression.getDouble ();
                    if (value != 0  &&  value != 1) firesBoolean = false;
                }
                else
                {
                    firesBoolean = false;
                }
            }
            if (alwaysFires) break;
        }
        if (fires.isEmpty ())  // $p has default value (1 at connect)
        {
            p.metadata.clear ("poll");
            return;
        }

        boolean needsPoll;
        if (fires.size () > 1)
        {
            // Multiple connect conditions means unpredictable, so needs polling.
            // The exception is if $p is initOnly and all equations are either 0 or 1.
            // In that case, polling is unneeded because the existence of the part is
            // already known at init time.
            System.out.println ("fires " + fires.size () + " " + prefix ());
            needsPoll =  ! p.hasAttribute ("initOnly")  ||  ! firesBoolean;
        }
        else
        {
            // Determine if the single expression for $p requires polling.
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
                if (visitor.isInitOnly) e.expression.visit (visitor);
                if (visitor.isInitOnly)
                {
                    // We have an initOnly equation. Now determine if the result is in (0,1).
                    // The only values we can be sure about are logical results, which are exactly 1 or 0, and therefore not in (0,1).
                    needsPoll = ! (e.expression instanceof OperatorLogical);
                }
            }
        }

        if (needsPoll)
        {
            if (p.metadata.child ("poll") == null) p.metadata.set ("1s", "poll");  // If poll is not otherwise specified, then do one full poll per second.
        }
        else
        {
            p.metadata.clear ("poll");
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
            Variable max = find (new Variable (c.alias + ".$max", -1));
            Variable min = find (new Variable (c.alias + ".$min", -1));
            if (max == null  &&  min == null) continue;
            if (c.endpoint.accountableConnections == null) c.endpoint.accountableConnections = new TreeSet<AccountableConnection> ();
            c.endpoint.accountableConnections.add (new AccountableConnection (this, c.alias));  // Only adds if it is not already there.
        }
    }

    /**
        Detects if $p depends on a NonzeroIterable operator.
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
        Variable p = find (new Variable ("$p"));
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
            Type doit = e.condition.eval (instance);
            if (doit instanceof Scalar  &&  ((Scalar) doit).value != 0)
            {
                predicate = e.expression;
                break;
            }
        }
        if (predicate == null) return;

        // TODO: Verify that predicate and all its dependencies are constant w.r.t. external information.
        // For example, the file name for loading a matrix should not be calculated from $index of either endpoint.
        // These are unlikely use-cases, but we should eliminate them in order to be strictly correct.

        // Detect if equation or dependency contains a NonzeroIterable.
        class ContainsTransformer implements Transformer
        {
            public NonzeroIterable found;
            public int             count;  // Number of times a nonzero iterable was found
            public boolean         substituted;
            public Operator transform (Operator op)
            {
                if (op instanceof NonzeroIterable)
                {
                    found = (NonzeroIterable) op;
                    count++;
                    return op;
                }
                if (op instanceof AccessVariable)
                {
                    // Check if this is a local reference to a single equation.
                    AccessVariable av = (AccessVariable) op;
                    Variable v = av.reference.variable;
                    if (v.container != p.container) return op;  // We only examine local dependencies.
                    if (v.equations.size () != 1) return op;
                    EquationEntry e = v.equations.first ();
                    if (e.condition != null) return op;

                    // Substitute the equation into the predicate.
                    substituted = true;
                    Operator result = e.expression.deepCopy ();
                    result.parent = op.parent;
                    return result;
                }
                return null;  // continue descent
            }
        }
        ContainsTransformer ct = new ContainsTransformer ();
        Operator p2 = predicate.deepCopy ();
        int depthLimit = variables.size ();  // to prevent infinite recursion
        do
        {
            ct.count = 0;
            ct.substituted = false;
            p2 = p2.transform (ct);
        }
        while (ct.substituted  &&  depthLimit-- > 0);
        if (ct.count != 1) return;
        if (! ct.found.hasCorrectForm ()) return;

        // Check if zero elements in matrix prevent connection.
        // During analysis (like now), there is no simulator object available. This causes NonzeroIterable to return 0.
        // If that results in $p evaluating to constant 0, and $p is a sufficiently simple expression,
        // then only non-zero elements will produce connections.
        try
        {
            Type result = p2.eval (instance);
            if (! (result instanceof Scalar)) return;  // Any type other than Scalar is treated as "true", so p2 fails the test.
            if (((Scalar) result).value != 0) return;  // Any nonzero value is treated as "true".
        }
        catch (EvaluationException e)
        {
            return;
        }

        // Construct
        // The NonzeroIterable we found above was a deep-copy, not the original.
        // We need to locate and work with the original in order to maintain object
        // identity in the finished model.
        predicate.visit (new Visitor ()
        {
            public int depthLimit = variables.size ();
            public boolean visit (Operator op)
            {
                if (op instanceof NonzeroIterable)
                {
                    ConnectionMatrix cm = new ConnectionMatrix ((NonzeroIterable) op);
                    if (cm.rowMapping != null  &&  cm.colMapping != null)
                    {
                        connectionMatrix = cm;
                        p.metadata.clear ("poll");  // Somewhat of a hack. cm is a one-time process, so we shouldn't do polling.
                    }
                    return false;
                }
                if (op instanceof AccessVariable)
                {
                    AccessVariable av = (AccessVariable) op;
                    Variable v = av.reference.variable;
                    if (v.container != p.container) return false;
                    if (v.equations.size () != 1) return false;
                    EquationEntry e = v.equations.first ();
                    if (e.condition != null) return false;
                    depthLimit--;
                    if (depthLimit >= 0) e.expression.visit (this);
                    depthLimit++;
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
