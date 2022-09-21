/*
Copyright 2013-2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.internal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;

import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.EquationSet.ConnectionBinding;
import gov.sandia.n2a.eqset.EquationSet.ConnectionMatrix;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.eqset.VariableReference;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix.IteratorNonzero;
import gov.sandia.n2a.linear.MatrixDense;
import gov.sandia.n2a.language.type.Scalar;

/**
    An Instance which contains the global variables for a given kind of part,
    and which manages the group of instances as a whole.
**/
public class Population extends Instance
{
    public int n;  // current number of live members

    protected Population (EquationSet equations, Part container)
    {
        this.equations = equations;
        this.container = container;
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        allocate (bed.countGlobalFloat, bed.countGlobalObject);

        if (bed.singleton)
        {
            Part p = new Part (equations, container);
            valuesObject[bed.instances] = p;
            n = 1;
            if (equations.connected) p.valuesFloat[bed.newborn] = 1;
        }
        else if (bed.instances >= 0)
        {
            valuesObject[bed.instances] = new ArrayList<Part> ();
        }

        if (bed.poll >= 0) valuesObject[bed.pollSorted] = new HashSet<Part> ();
    }

    public double getDt ()
    {
        return ((Part) container).event.dt;
    }

    public void init (Simulator simulator)
    {
        InstanceTemporaries temp = new InstanceInit (this, simulator);
        InternalBackendData bed = temp.bed;
        resolve (bed.globalReference);

        Part instance = null;
        if (bed.singleton)
        {
            instance = (Part) valuesObject[bed.instances];
            ((Part) container).event.enqueue (instance);
            instance.resolve ();
        }

        for (Variable v : bed.globalInit)
        {
            Type result = v.eval (temp);
            if (result != null  &&  v.writeIndex >= 0) temp.setFinal (v, result);
            // No need to handle references to external variables. These should all be classified as local rather than global equations.
        }
        // However, there may be external references to our variables. For example, instances of another part might adjust our $n'.
        // zero external buffered variables that may be written before first finish()
        clearExternalWriteBuffers (bed.globalBufferedExternalWrite);
        for (Variable v : bed.globalBufferedExternalWrite) if (v.assignment == Variable.REPLACE) temp.set (v, temp.get (v));

        if (bed.index != null  &&  ! bed.singleton)
        {
            valuesFloat[bed.indexNext] = 0;  // Using floats directly as index counter limits us to 24 bits, or about 16 million. Internal is not intended for large simulations, so this limitation is acceptable.
            // indexAvailable is initially null
        }

        if (bed.singleton)
        {
            instance.init (simulator);
        }
        else
        {
            // Note: A connection is forbidden from setting its own population size.
            // Even if a connection is the target of another connection, and thus functions as a compartment,
            // it still cannot set its own population size.
            if (equations.connectionBindings == null)
            {
                int requestedN = 1;
                if (bed.n.hasAttribute ("constant")) requestedN = (int) ((Scalar) bed.n.eval (this)).value;  // n should be a constant holding a scalar. eval() just retrieves this.
                else                                 requestedN = (int) ((Scalar) get (bed.n)).value;
                resize (simulator, requestedN);
            }
            else
            {
                simulator.connect (this);  // queue to evaluate our connections
            }
        }
    }

    public void integrate (Simulator simulator, double dt)
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        for (Variable v : bed.globalIntegrated)
        {
            double a  = ((Scalar) get (v           )).value;
            double aa = ((Scalar) get (v.derivative)).value;
            setFinal (v, new Scalar (a + aa * dt));
        }
    }

    public void update (Simulator simulator)
    {
        InstanceTemporaries temp = new InstanceTemporaries (this, simulator);
        for (Variable v : temp.bed.globalUpdate)
        {
            Type result = v.eval (temp);
            if (v.reference.variable.writeIndex < 0) continue;  // this is a "dummy" variable, so calling eval() was all we needed to do
            if (result != null)
            {
                temp.applyResult (v, result);
            }
            else if (v.reference.variable == v  &&  v.equations.size () > 0)  // No condition fired, and we need to provide some default value.
            {
                if (v.readIndex == v.writeIndex)  // not buffered
                {
                    if (v.readTemp) temp.set (v, v.type);  // This is a pure temporary, so set value to default for use by later equations. Note that readTemp==writeTemp==true.
                }
                else  // buffered
                {
                    if (! v.externalWrite) temp.set (v, temp.get (v));  // Not an accumulator, so copy its value
                }
            }
        }
        for (Variable v : temp.bed.globalBufferedInternalUpdate)
        {
            temp.setFinal (v, temp.getFinal (v));
        }
    }

    public boolean finish (Simulator simulator)
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;

        // Capture $n before finalize, so we can compare it for changes.
        double oldN = 0;
        if (bed.populationCanResize) oldN = ((Scalar) get (bed.n)).value;

        // Finalize
        for (Variable v : bed.globalBufferedExternal) setFinal (v, getFinal (v));
        clearExternalWriteBuffers (bed.globalBufferedExternalWrite);

        // Structural dynamics
        if (bed.populationCanResize)
        {
            int newN = Math.max (0, (int) ((Scalar) get (bed.n)).value);  // This is the finalized value of $n.
            if (bed.populationCanGrowOrDie)  // $n shares control with other specials, so coordinate them.
            {
                if (bed.n.derivative == null)
                {
                    if (newN != oldN) simulator.resize (this, (int) newN);  // $n was explicitly changed, so its value takes precedence
                    else              simulator.resize (this, -1);  // -1 means to update $n from this.n. This can only be done after other parts are finalized, as they may impose structural dynamics via $p or $type.
                }
                else  // the rate of change in $n is pre-determined, so it relentlessly overrides any other structural dynamics
                {
                    simulator.resize (this, newN);
                }
            }
            else  // $n is the only kind of structural dynamics, so only do a resize() when needed
            {
                if (newN != n) simulator.resize (this, newN);
            }
        }

        if (equations.connectionBindings != null)
        {
            // Check poll deadline.
            boolean shouldConnect = false;
            if (bed.poll >= 0)
            {
                double pollDeadline = valuesFloat[bed.pollDeadline];
                double t = simulator.currentEvent.t;
                if (t >= pollDeadline) shouldConnect = true;
            }

            // Check for newly-created parts in endpoint populations.
            // To limit work, only do this for shallow structures that don't require enumerating sub-populations.
            // TODO: More thorough evaluation of new instances. See C backend for example.
            if (! shouldConnect)
            {
                for (ConnectionBinding target : equations.connectionBindings)
                {
                    InternalBackendData cbed = (InternalBackendData) target.endpoint.backendData;
                    if (cbed.singleton) continue;

                    // Resolve binding
                    Instance current = this;
                    for (Object o : target.resolution)
                    {
                        Resolver r = (Resolver) o;
                        if (r.shouldEnumerate (current)) break;
                        current = r.resolve (current);
                    }
                    if (current.equations != target.endpoint) continue;

                    int firstborn = (int) current.valuesFloat[cbed.firstborn];
                    @SuppressWarnings("unchecked")
                    int size      = (int) ((List<Part>) current.valuesObject[cbed.instances]).size ();
                    if (firstborn < size)
                    {
                        shouldConnect = true;
                        break;
                    }
                }
            }

            if (shouldConnect) simulator.connect (this);
        }

        return true;
    }

    public interface ConnectIterator
    {
        public boolean setProbe (Part probe);  // Return value is used primarily by ConnectPopulation to implement $max
        public boolean next ();                // Fills endpoints of probe with next combination. Returns false if no more combinations are available.
    }

    public class ConnectPopulation implements ConnectIterator
    {
        public int                 index;
        public boolean             newOnly;        // Filter out old instances. Only an iterator with index==0 can set this true.
        public boolean             poll;           // Indicates that every combination should be visited, so never set newOnly.
        public ConnectPopulation   permute;
        public boolean             contained;      // Another iterator holds us in its permute reference.
        public int                 max;
        public int                 connectedCount; // position in p.valuesFloat of $count for this connection
        public InternalBackendData pbed;
        public int                 firstborn;      // index in instances of first new entry
        public ArrayList<Part>     instances;
        public ArrayList<Part>     filtered;       // A subset of instances selected by spatial filtering.
        public Part                c;              // The connection instance being built.
        public Part                p;              // Our current part, contributed as an endpoint of c.
        public Simulator           simulator;      // For evaluating equations

        public int size;   // Cached value of instances.size(). Does not change.
        public int count;  // Size of current subset of instances we are iterating through.
        public int offset;
        public int i;
        public int stop;

        public int                 k;
        public double              radius;
        public KDTree              NN;
        public List<KDTree.Entry>  entries;
        public Variable            project;
        public double[]            xyz;  // query value, shared across all iterators
        public InternalBackendData cbed;
        public double              rank;  // heuristic value indicating how good a candidate this endpoint is to determine C.$xyz

        public ConnectPopulation (int index, ConnectionBinding target, InternalBackendData cbed, Simulator simulator, boolean poll)
        {
            this.index     = index;
            this.poll      = poll;
            this.cbed      = cbed;
            this.simulator = simulator;
            pbed           = (InternalBackendData) target.endpoint.backendData;
            firstborn      = Integer.MAX_VALUE;
            assemble (Population.this, target.resolution, 0);
            size           = instances.size ();

            if (cbed.max[index] != null)
            {
                max = (int) ((Scalar) get (cbed.max[index])).value;
                connectedCount = cbed.count[index];
            }

            project = cbed.project[index];
            if (project != null) rank += 1;

            if (cbed.k     [index] != null) k      = (int) ((Scalar) get (cbed.k     [index])).value;
            if (cbed.radius[index] != null) radius =       ((Scalar) get (cbed.radius[index])).value;
            if (k > 0  ||  radius > 0) rank -= 2;
        }

        /**
            Recursively constructs the list of instances from sub-populations.
            Traverses over the resolution path for our given connection binding.
            Any time the path descends into a population, we enumerate the instances.
            If the path terminates, we simply append current instances to the result.
            In the special case of a simple path with no enumeration, we reference the
            target instance list rather than copying it. 
        **/
        @SuppressWarnings("unchecked")
        public void assemble (Instance current, ArrayList<Object> resolution, int depth)
        {
            int size = resolution.size ();
            for (int i = depth; i < size; i++)
            {
                Resolver r = (Resolver) resolution.get (i);
                if (r.shouldEnumerate (current))
                {
                    InternalBackendData bbed = (InternalBackendData) current.equations.backendData;  // "branch" bed
                    if (bbed.singleton)
                    {
                        current = (Instance) current.valuesObject[bbed.instances];
                    }
                    else
                    {
                        if (instances == null) instances = new ArrayList<Part> ();
                        ArrayList<Part> branchInstances = (ArrayList<Part>) current.valuesObject[bbed.instances];
                        for (Part j : branchInstances) assemble (j, resolution, i);
                        return;
                    }
                }
                current = r.resolve (current);
            }

            // "current" is now a population or part of the endpoint type, so pbed is the correct backend data
            if (current instanceof Part)  // This happens if last move was an $up or ConnectionBinding.
            {
                if (instances == null) instances = new ArrayList<Part> ();
                if (firstborn == Integer.MAX_VALUE  &&  current.valuesFloat[pbed.newborn] != 0) firstborn = instances.size ();
                instances.add ((Part) current);
            }
            else  // population
            {
                if (pbed.singleton)
                {
                    Part instance = (Part) current.valuesObject[pbed.instances];
                    if (instances == null) instances = new ArrayList<Part> ();
                    if (firstborn == Integer.MAX_VALUE  &&  instance.valuesFloat[pbed.newborn] != 0) firstborn = instances.size ();
                    instances.add (instance);
                }
                else  // regular population
                {
                    int             leafFirstborn = (int)             current.valuesFloat [pbed.firstborn];
                    ArrayList<Part> leafInstances = (ArrayList<Part>) current.valuesObject[pbed.instances];
                    if (instances == null)  // No enumerations occurred during the resolution, so simply reference the existing list of instances.
                    {
                        instances = leafInstances;
                        firstborn = leafFirstborn;
                    }
                    else
                    {
                        if (firstborn == Integer.MAX_VALUE  &&  leafFirstborn < leafInstances.size ()) firstborn = instances.size () + leafFirstborn;
                        instances.addAll (leafInstances);
                    }
                }
                simulator.clearNew ((Population) current);  // Queue to clear after current cycle.
            }
        }

        public void prepareNN ()
        {
            NN = new KDTree ();
            NN.k      = k      > 0 ? k      : Integer.MAX_VALUE;
            NN.radius = radius > 0 ? radius : Double.POSITIVE_INFINITY;

            entries = new ArrayList<KDTree.Entry> (size);
            if (project != null) c = new Part (equations, (Part) container);  // Necessary to use c for getProject(). However, this function should be called before first call to setProbe(), so should produce no side-effects.
            for (int i = 0; i < size; i++)
            {
                p = instances.get (i);
                if (p == null) continue;

                KDTree.Entry e = new KDTree.Entry ();
                if (project == null)
                {
                    e.point = p.getXYZ (simulator, false);
                }
                else
                {
                    c.setPart (index, p);
                    e.point = getProject ();
                }
                e.item = p;
                entries.add (e);
            }
            
            if (entries.isEmpty ()) NN = null;  // This line is necessary because a population could drop to zero without ending simulation.
            else                    NN.set (entries);
        }

        @SuppressWarnings("unchecked")
        public double[] getProject ()
        {
            if (cbed.projectReferences[index] != null)
            {
                c.resolve ((TreeSet<VariableReference>) cbed.projectReferences[index]);
            }

            InstanceConnect temp = new InstanceConnect (c, simulator);
            for (Object o : (ArrayList<Variable>) cbed.projectDependencies[index])
            {
                Variable v = (Variable) o;
                Type result = v.eval (temp);
                if (result != null  &&  v.writeIndex >= 0) temp.set (v, result);
            }
            return ((MatrixDense) project.eval (temp)).getData ();
        }

        /**
            @return true If we need to advance to the next instance. This happens when p
            has reached its max number of connections.
        **/
        public boolean setProbe (Part probe)
        {
            c = probe;
            boolean result = false;
            if (p != null)
            {
                // A new connection was just made, so counts (if they are used) have been updated.
                // Step to next endpoint instance if current instance is full.
                if (max > 0  &&  p.valuesFloat[connectedCount] >= max) result = true;
                else c.setPart (index, p);
            }
            if (permute != null  &&  permute.setProbe (c))
            {
                i = stop;  // next() will trigger a reset
                result = true;
            }
            return result;
        }

        /**
            Restarts this iterator at a random point.
            Called multiple times, depending on how many times permute.next() returns true.
        **/
        public void reset (boolean newOnly)
        {
            this.newOnly = newOnly;
            if (NN != null)
            {
                List<KDTree.Entry> result = NN.find (xyz);
                count = result.size ();
                filtered = new ArrayList<Part> (count);
                if (newOnly)
                {
                    for (KDTree.Entry e : result)
                    {
                        Part ep = (Part) e.item;
                        if (ep.valuesFloat[pbed.newborn] == 0) continue;
                        filtered.add (ep);
                    }
                }
                else
                {
                    for (KDTree.Entry e : result) filtered.add ((Part) e.item);
                }
                i = 0;
            }
            else
            {
                if (newOnly) count = size - firstborn;
                else         count = size;
                if (count > 1) i = (int) Math.round (simulator.random.nextDouble () * (count - 1));
                else           i = 0;
            }
            stop = i + count;
        }

        /**
            Indicates that all iterators from this level down returned a part that is old.
        **/
        public boolean old ()
        {
            if (p.valuesFloat[pbed.newborn] != 0) return false;
            if (permute != null) return permute.old ();
            return true;
        }

        /**
            Advances to next part that meets criteria, and sets the appropriate endpoint in probe.
            @return false If no more parts are available.
        **/
        public boolean next ()
        {
            while (true)
            {
                if (i >= stop)  // Need to either reset or terminate, depending on whether we have something left to permute with.
                {
                    if (permute == null)
                    {
                        if (stop > 0) return false;  // We already reset once, so done.
                        // A unary connection (indicated by !contained) should only iterate over new instances, unless we're polling.
                        // The innermost (slowest) iterator of a multi-way connection should iterate over all instances.
                        reset (! contained  &&  ! poll);
                    }
                    else
                    {
                        if (! permute.next ()) return false;
                        if (contained  ||  poll) reset (false);
                        else                     reset (permute.old ());
                    }
                }

                if (NN != null)
                {
                    for (; i < stop; i++)
                    {
                        p = filtered.get (i);
                        // newborn filter is handled by reset(), at same time as spatial filter
                        if (max == 0  ||  p.valuesFloat[connectedCount] < max) break;
                    }
                }
                else if (newOnly)
                {
                    for (; i < stop; i++)
                    {
                        p = instances.get (i % count + firstborn);
                        if (p == null  ||  p.valuesFloat[pbed.newborn] == 0) continue;
                        if (max == 0  ||  p.valuesFloat[connectedCount] < max) break;
                    }
                }
                else
                {
                    for (; i < stop; i++)
                    {
                        p = instances.get (i % count);
                        if (p == null) continue;
                        if (max == 0  ||  p.valuesFloat[connectedCount] < max) break;
                    }
                }

                i++;
                if (p != null  &&  i <= stop)
                {
                    c.setPart (index, p);
                    if (permute == null  &&  xyz != null)  // Spatial filtering is on, and we are the endpoint that determines the query
                    {
                        // Obtain C.$xyz, the best way we can
                        double[] t;
                        if (cbed.xyz != null)  // C explicitly defines $xyz
                        {
                            // This is a minimal evaluation. No reference resolution. No temporaries. TODO: deeper eval of C.$xyz ?
                            t = c.getXYZ (simulator, true);
                        }
                        else if (project == null)
                        {
                            t = p.getXYZ (simulator, false);
                        }
                        else
                        {
                            t = getProject ();
                        }
                        for (int j = 0; j < 3; j++) xyz[j] = t[j];  // Necessary to copy values, so that xyz can be shared across all iterators.
                    }
                    return true;
                }
            }
        }
    }

    public class ConnectMatrix implements ConnectIterator
    {
        public ConnectionMatrix    cm;
        public ArrayList<Part>     rows;
        public ArrayList<Part>     cols;
        public Part                c;
        public Part                dummy;  // Pre-loaded with arbitrary instance from rows and cols. Used to evaluate mappings back to indices. Provides access to population variables.
        public InstanceConnect     dummyContext;
        public IteratorNonzero     it;
        public int                 rowCount;
        public int                 colCount;
        public InternalBackendData rowBed;
        public InternalBackendData colBed;

        public ConnectMatrix (ConnectionMatrix cm, ConnectPopulation rowIterator, ConnectPopulation colIterator, Simulator simulator)
        {
            this.cm  = cm;
            rows     = rowIterator.instances;
            cols     = colIterator.instances;
            rowCount = rows.size ();
            colCount = cols.size ();

            Part row = rows.get (0);
            Part col = cols.get (0);
            rowBed = (InternalBackendData) row.equations.backendData;
            colBed = (InternalBackendData) col.equations.backendData;

            // Prepare "dummy" part.
            // This is the context in which the connect iterator evaluates.
            dummy = new Part (equations, (Part) container);
            dummy.setPart (cm.rows.index, row);
            dummy.setPart (cm.cols.index, col);
            dummy.resolve ();
            dummyContext = new InstanceConnect (dummy, simulator);
            for (Variable v : dummyContext.bed.Pdependencies)
            {
                Type result = v.eval (dummyContext);
                // v will not be an external reference, because EquationSet.findConnectionMatrix() excludes that case.
                // Thus there is no need to handle combiners (Instance.applyResultInit()).
                // Pdependencies should be in init order (maximal propagation of info) rather than update order.
                if (result == null) dummyContext.setFinal (v, v.type);
                else                dummyContext.setFinal (v, result);
            }

            it = cm.A.getIteratorNonzero (dummyContext);  // If A can't open, we're dead anyway, so don't bother checking for null pointer.
        }

        public boolean setProbe (Part probe)
        {
            c = probe;
            return false;
        }

        public boolean next ()
        {
            while (it.next () != null)
            {
                int a = cm.rowMapping.getIndex (dummyContext, it.getRow ());
                int b = cm.colMapping.getIndex (dummyContext, it.getColumn ());
                if (a < 0  ||  a >= rowCount  ||  b < 0  ||  b >= colCount) continue;
                Part row = rows.get (a);
                Part col = cols.get (b);
                if (row.valuesFloat[rowBed.newborn] != 0  ||  col.valuesFloat[colBed.newborn] != 0)
                {
                    c.setPart (cm.rows.index, row);
                    c.setPart (cm.cols.index, col);
                    return true;
                }
            }
            return false;
        }
    }

    public ConnectIterator getIterators (Simulator simulator, boolean poll)
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;

        int count = equations.connectionBindings.size ();
        ArrayList<ConnectPopulation> iterators = new ArrayList<ConnectPopulation> (count);
        boolean nothingNew = true;
        boolean spatialFiltering = false;
        for (int i = 0; i < count; i++)
        {
            ConnectionBinding target = equations.connectionBindings.get (i);
            ConnectPopulation it = new ConnectPopulation (i, target, bed, simulator, poll);
            if (it.instances == null  ||  it.instances.size () == 0) return null;  // Nothing to connect. This can happen if $n is not known until init and turns out to be zero.
            iterators.add (it);
            if (it.firstborn < it.size) nothingNew = false;
            if (it.k > 0  ||  it.radius > 0) spatialFiltering = true;
        }
        if (nothingNew  &&  ! poll) return null;

        ConnectionMatrix cm = equations.connectionMatrix;
        if (cm != null)  // Use sparse-matrix optimization
        {
            // TODO: Guard against deep paths to populations. The row and column collections should each be a simple list from a single population.
            return new ConnectMatrix (cm, iterators.get (cm.rows.index), iterators.get (cm.cols.index), simulator);  // cm takes precedence over any other iteration method.
        }

        // Sort so that population with the most old entries is the outermost iterator.
        // That allows the most number of old entries to be skipped.
        if (! poll)
        {
            // This is a simple insertion sort ...
            for (int i = 1; i < count; i++)
            {
                for (int j = i; j > 0; j--)
                {
                    ConnectPopulation A = iterators.get (j-1);
                    ConnectPopulation B = iterators.get (j);
                    if (A.firstborn >= B.firstborn) break;
                    iterators.set (j-1, B);
                    iterators.set (j,   A);
                }
            }
        }

        // For spatial filtering, make the innermost iterator be the one that best defines C.$xyz
        if (spatialFiltering)
        {
            double[] xyz = new double[3];
            for (int i = 0; i < count; i++) iterators.get (i).xyz = xyz;

            if (bed.xyz == null  ||  bed.xyz.equations.size () == 0)  // connection's own $xyz is not defined, so must get it from some $project
            {
                int last = count - 1;
                ConnectPopulation A = iterators.get (last);
                int    bestIndex = last;
                double bestRank  = A.rank;
                for (int i = 0; i < last; i++)
                {
                    A = iterators.get (i);
                    if (A.rank > bestRank)
                    {
                        bestIndex = i;
                        bestRank  = A.rank;
                    }
                }
                if (bestIndex != last)
                {
                    A = iterators.remove (bestIndex);
                    iterators.add (A);
                }
            }
        }

        for (int i = 1; i < count; i++)
        {
            ConnectPopulation A = iterators.get (i-1);
            ConnectPopulation B = iterators.get (i);
            A.permute   = B;
            B.contained = true;
            if (A.k > 0  ||  A.radius > 0) A.prepareNN ();
        }

        return iterators.get (0);
    }

    @SuppressWarnings("unchecked")
    public void connect (Simulator simulator)
    {
        boolean poll = false;  // If true, check all latent connections. If false, only check for new connections.
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        if (bed.poll >= 0)
        {
            double t = simulator.currentEvent.t;
            double pollDeadline = valuesFloat[bed.pollDeadline];
            if (t >= pollDeadline)
            {
                poll = true;
                valuesFloat[bed.pollDeadline] = (float) (pollDeadline + bed.poll);
            }
        }

        // TODO: implement $min, or consider eliminating it from the language
        // $max is easy, but $min requires one or more forms of expensive accounting to do correctly.
        // Problems include:
        // 1) need to prevent duplicate connections
        // 2) should pick the highest probability connections
        // A list of connections held by each target could solve #1.
        // Such an approach may be necessary for ongoing maintenance of connections, beyond just this new-connection process.
        // A temporary list of connections that were rejected, sorted by probability, could solve issue #2.
        // However, this is more difficult to implement for any but the outer loop. Could implement an
        // outer loop for each of the other populations, just for fulfilling $min.

        ConnectIterator outer = getIterators (simulator, poll);
        if (outer == null)
        {
            checkInactive ();
            return;
        }

        HashSet<Part> pollSorted;
        if (poll) pollSorted = (HashSet<Part>) valuesObject[bed.pollSorted];
        else      pollSorted = null;

        Part c = new Part (equations, (Part) container);
        outer.setProbe (c);
        while (outer.next ())
        {
            c.resolve ();
            double create = c.getP (simulator);
            if (create <= 0  ||  create < 1  &&  create < simulator.random.nextDouble ()) continue;  // Yes, we need all 3 conditions. If create is 0 or 1, we do not do a random draw, since it should have no effect.

            // In poll mode, prevent duplicates.
            // This filter could also come any time before getP(). However, connections tend to be
            // sparse in general, so most candidates will call getP() in any case.
            // The ideal situation would be if duplicate detection cost nearly nothing, for example if we could
            // iterate through a sorted list of existing connections and only check candidates in the gaps.
            // In that case, testing for duplicates first would make sense.
            if (poll  &&  pollSorted.contains (c)) continue;

            ((Part) container).event.enqueue (c);
            c.init (simulator);
            c = new Part (equations, (Part) container);
            outer.setProbe (c);
        }

        checkInactive ();
    }

    public void checkInactive ()
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        if (! bed.populationCanBeInactive) return;
        if (n != 0) return;
        // Now we have an inactive population, so clear it from its parent part.
        container.valuesObject[bed.populationIndex] = null;
        ((Part) container).checkInactive ();
    }

    @SuppressWarnings("unchecked")
    public void clearNew ()
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        if (bed.singleton)
        {
            Part p = (Part) valuesObject[bed.instances];
            p.valuesFloat[bed.newborn] = 0;
        }
        else
        {
            ArrayList<Part> instances = (ArrayList<Part>) valuesObject[bed.instances];
            int count     = instances.size ();
            int firstborn = (int) valuesFloat[bed.firstborn];
            for (int i = firstborn; i < count; i++)
            {
                Part p = instances.get (i);
                if (p == null) continue;
                p.valuesFloat[bed.newborn] = 0;
            }
            valuesFloat[bed.firstborn] = count;
        }
    }

    @SuppressWarnings("unchecked")
    public void insert (Part p)
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        if (bed.singleton) return;

        n++;
        if (bed.index != null)
        {
            int index;
            if (valuesObject[bed.indexAvailable] == null)
            {
                index = (int) valuesFloat[bed.indexNext]++;
            }
            else
            {
                ArrayList<Integer> availableIndex = (ArrayList<Integer>) valuesObject[bed.indexAvailable];
                index = availableIndex.remove (availableIndex.size () - 1);
                if (availableIndex.size () < 1) valuesObject[bed.indexAvailable] = null;
            }
            p.valuesFloat[bed.index.writeIndex] = index;

            if (bed.instances >= 0)
            {
                ArrayList<Part> instances = (ArrayList<Part>) valuesObject[bed.instances];
                for (int size = instances.size (); size <= index; size++) instances.add (null);
                instances.set (index, p);

                if (equations.connected)
                {
                    p.valuesFloat[bed.newborn] = 1;
                    valuesFloat[bed.firstborn] = Math.min (valuesFloat[bed.firstborn], index);
                }
            }
        }

        if (bed.poll >= 0) ((HashSet<Part>) valuesObject[bed.pollSorted]).add (p);
    }

    @SuppressWarnings("unchecked")
    public void remove (Part p)
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        if (bed.singleton)  // This should never happen except for top-level part.
        {
            n = 0;
            return;
        }

        n--;  // presuming that p is actually here
        if (bed.index != null)
        {
            int index = (int) p.valuesFloat[bed.index.readIndex];

            ArrayList<Integer> availableIndex = (ArrayList<Integer>) valuesObject[bed.indexAvailable];
            if (availableIndex == null)
            {
                availableIndex = new ArrayList<Integer> ();
                valuesObject[bed.indexAvailable] = availableIndex;
            }
            availableIndex.add (index);

            if (bed.instances >= 0)
            {
                ArrayList<Part> instances = (ArrayList<Part>) valuesObject[bed.instances];
                instances.set (index, null);
            }
        }

        if (bed.poll >= 0) ((HashSet<Part>) valuesObject[bed.pollSorted]).remove (p);
    }

    @SuppressWarnings("unchecked")
    public void resize (Simulator simulator, int requestedN)
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;

        if (requestedN < 0)  // indicates to update $n from actual part count
        {
            int currentN = (int) ((Scalar) get (bed.n)).value;
            // In general, $n can be fractional, which allows gradual growth over many cycles.
            // Only change $n if it does not truncate to same as actual n.
            if (currentN != n) setFinal (bed.n, new Scalar (n));
            return;
        }

        while (n < requestedN)
        {
            Part p = new Part (equations, (Part) container);
            ((Part) container).event.enqueue (p);
            p.resolve ();
            p.init (simulator);
        }

        if (n > requestedN)
        {
            ArrayList<Part> instances = (ArrayList<Part>) valuesObject[bed.instances];
            for (int i = instances.size () - 1; i >= 0  &&  n > requestedN; i--)
            {
                Part p = instances.get (i);
                if (p == null) continue;
                p.die ();  // Part.die() is responsible to call remove(), which decreases n. p itself won't dequeue until next simulator cycle.
            }
        }
    }
}
