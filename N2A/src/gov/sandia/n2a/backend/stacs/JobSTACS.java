/*
Copyright 2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.stacs;

import gov.sandia.n2a.backend.internal.InternalBackend;
import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.eqset.Equality;
import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.EquationSet.ConnectionBinding;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.host.Host;
import gov.sandia.n2a.host.Remote;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.UnitValue;
import gov.sandia.n2a.language.function.Sphere;
import gov.sandia.n2a.language.function.Uniform;
import gov.sandia.n2a.language.operator.Add;
import gov.sandia.n2a.language.operator.EQ;
import gov.sandia.n2a.language.operator.Multiply;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.linear.MatrixDense;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.plugins.extpoints.Backend.AbortRun;
import gov.sandia.n2a.ui.jobs.NodeJob;
import tech.units.indriya.AbstractUnit;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.measure.Unit;
import javax.measure.UnitConverter;

public class JobSTACS extends Thread
{
    public    MNode       job;
    protected EquationSet digestedModel;

    public    Host env;
    protected Path localJobDir;
    protected Path jobDir;     // remote or local

    protected long seed;
    protected int  processes;

    public static UnitValue unitless = new UnitValue ();
    static
    {
        unitless.unit = AbstractUnit.ONE;
    }

    public JobSTACS (MNode job)
    {
        super ("STACS Job");
        this.job = job;
    }

    public void run ()
    {
        localJobDir = Host.getJobDir (Host.getLocalResourceDir (), job);
        Path errPath = localJobDir.resolve ("err");
        try {Backend.err.set (new PrintStream (new FileOutputStream (errPath.toFile (), true), false, "UTF-8"));}
        catch (Exception e) {}

        try
        {
            Files.createFile (localJobDir.resolve ("started"));
            MNode model = NodeJob.getModel (job);

            env              = Host.get (job);
            Path resourceDir = env.getResourceDir ();  // remote or local
            jobDir           = Host.getJobDir (resourceDir, job);  // Unlike localJobDir (which is created by MDir), this may not exist until we explicitly create it.

            Files.createDirectories (jobDir);  // digestModel() might write to a remote file (params), so we need to ensure the dir exists first.
            digestedModel = new EquationSet (model);
            ensureProperties (digestedModel);
            InternalBackend.digestModel (digestedModel);
            if (digestedModel.metadata == null) digestedModel.metadata = new MVolatile ();

            String duration = digestedModel.metadata.getOrDefault ("1s", "duration");
            job.set (duration, "duration");

            seed = model.getOrDefault (System.currentTimeMillis () & 0x7FFFFFFF, "$metadata", "seed");
            job.set (seed, "seed");

            int cores = 1;
            if (! (env instanceof Remote)) cores = env.getProcessorTotal ();
            cores     = job.getOrDefault (cores, "host", "cores");
            int nodes = job.getOrDefault (1,     "host", "nodes");
            processes = cores * nodes;

            generate ();

            // The simulator could append to the same error file, so we need to close the file before submitting.
            PrintStream ps = Backend.err.get ();
            if (ps != System.err)
            {
                ps.close ();
                Backend.err.remove ();
                job.set (Host.size (errPath), "errSize");
            }

            List<List<String>> commands = new ArrayList<List<String>> ();

            List<String> charmrun = new ArrayList<String> ();
            charmrun.add ("charmrun");
            charmrun.add ("+p" + processes);

            List<String> command = new ArrayList<String> (charmrun);
            command.add (env.config.getOrDefault ("genet", "backend", "stacs", "genet"));
            command.add ("config.yml");
            commands.add (command);

            command = new ArrayList<String> (charmrun);
            command.add (env.config.getOrDefault ("stacs", "backend", "stacs", "stacs"));
            command.add ("config.yml");
            commands.add (command);

            env.submitJob (job, true, commands);
        }
        catch (Exception e)
        {
            if (! (e instanceof AbortRun)) e.printStackTrace (Backend.err.get ());

            try {Files.copy (new ByteArrayInputStream ("failure".getBytes ("UTF-8")), localJobDir.resolve ("finished"));}
            catch (Exception f) {}
        }

        // If an exception occurred, the error file will still be open.
        PrintStream ps = Backend.err.get ();
        if (ps != System.err) ps.close ();
    }

    public void ensureProperties (EquationSet s)
    {
        for (EquationSet p : s.parts) ensureProperties (p);

        for (Variable v : s.variables)
        {
            if (v.getMetadata ().child ("backend", "stacs") != null) v.addUser (s);
        }
    }

    public void generate () throws IOException
    {
        try (BufferedWriter writer = Files.newBufferedWriter (jobDir.resolve ("config.yml")))
        {
            writer.write ("# simulation\n");
            writer.write ("runmode : \"simulate\"\n");
            writer.write ("randseed: " + seed + "\n");
            writer.write ("plastic : yes\n");
            writer.write ("episodic: no\n");
            writer.write ("\n");

            writer.write ("# network\n");
            writer.write ("netwkdir: \"network\"\n");
            writer.write ("netparts: " + processes + "\n");
            writer.write ("netfiles: " + processes + "\n");
            writer.write ("filebase: \"config\"\n");
            writer.write ("fileload: \"\"\n");
            writer.write ("filesave: \".out\"\n");
            writer.write ("recordir: \"record\"\n");
            writer.write ("\n");

            writer.write ("# timing\n");

            double tstep = 0.001;
            Variable v = digestedModel.find (new Variable ("$t", 1));
            if (v != null  &&  v.equations.size () == 1)
            {
                EquationEntry ee = v.equations.first ();
                if (ee.condition instanceof Constant) tstep = ee.condition.getDouble ();
            }
            writer.write ("tstep   : " + Scalar.print (tstep * 1000) + "\n");

            double teventq = new UnitValue (digestedModel.metadata.getOrDefault ("20ms", "backend", "stacs", "teventq")).get ();
            writer.write ("teventq : " + Scalar.print (teventq * 1000) + "\n");

            double tdisplay = new UnitValue (digestedModel.metadata.getOrDefault ("1s", "backend", "stacs", "tdisplay")).get ();
            writer.write ("tdisplay: " + Scalar.print (tdisplay * 1000) + "\n");

            double trecord = new UnitValue (digestedModel.metadata.getOrDefault ("1s", "backend", "stacs", "trecord")).get ();
            writer.write ("trecord : " + Scalar.print (trecord * 1000) + "\n");

            double tsave = new UnitValue (digestedModel.metadata.getOrDefault ("60s", "backend", "stacs", "tsave")).get ();
            writer.write ("tsave   : " + Scalar.print (tsave * 1000) + "\n");

            double tmax = new UnitValue (job.get ("duration")).get ();
            writer.write ("tmax    : " + Scalar.print (tmax * 1000) + "\n");
        }

        // Create basic directory structure
        Path networkDir = jobDir .resolve ("network");
        Path recordDir  = networkDir.resolve ("record");
        Path graphPath  = networkDir.resolve ("config.graph");
        Path modelPath  = networkDir.resolve ("config.model");
        Files.createDirectories (recordDir);
        Files.createSymbolicLink (jobDir.resolve ("record"),       recordDir);
        Files.createSymbolicLink (jobDir.resolve ("config.graph"), graphPath);
        Files.createSymbolicLink (jobDir.resolve ("config.model"), modelPath);

        // Collect part and network configurations
        MNode graph = new MVolatile ();
        MNode model = new MVolatile ();
        for (EquationSet p : digestedModel.parts) generate (p, graph, model);
        System.out.println (graph);
        System.out.println (model);

        try (BufferedWriter writer = Files.newBufferedWriter (graphPath))
        {
            for (MNode category : graph)
            {
                writer.write (category.key () + ":\n");
                for (MNode mod : category) dumpWithKey ("", "modname", mod, writer);
                writer.write ("\n");
            }
        }

        try (BufferedWriter writer = Files.newBufferedWriter (modelPath))
        {
            for (MNode mod : model)
            {
                writer.write ("---\n");
                writer.write ("modname: " + mod.key () + "\n");
                for (MNode config : mod)
                {
                    writer.write (config.key () + ": " + config.get () + "\n");
                    for (MNode v : config) dumpWithKey ("", "name", v, writer);
                }
                writer.write ("...\n");
                writer.write ("\n");
            }
        }
    }

    public void generate (EquationSet s, MNode graph, MNode model)
    {
        for (EquationSet p : s.parts) generate (p, graph, model);

        String modname = s.prefix ();
        MNode part = model.childOrCreate (modname);
        boolean isVertex = s.connectionBindings == null;
        part.set (isVertex ? "vertex" : "edge", "type");
        String modtype = s.source.get ("$metadata", "backend", "stacs", "part");
        modtype = modtype.split (",")[0];
        if (modtype.equals ("13"))  // Check for STDP
        {
            for (MNode c : s.source)
            {
                if (! MPart.isPart (c)) continue;
                String inherit = c.get ("$inherit");
                if (inherit.startsWith ("Plasticity")) modtype = "12";
            }
        }
        part.set (modtype, "modtype");

        for (Variable v : s.variables)
        {
            MNode stacs = v.getMetadata ().child ("backend", "stacs");
            if (stacs == null  ||  stacs.child ("keep") != null) continue;

            String key = v.nameString ();
            String name = key;
            name = stacs.getOrDefault (name, "param");
            name = stacs.getOrDefault (name, "state");
            name = stacs.getOrDefault (name, "stick");
            boolean isParam = stacs.child ("param") != null;
            boolean isTick  = stacs.child ("stick") != null;
            MNode entry = part.childOrCreate (isParam ? "param" : "state", name);
            if (isTick) entry.set ("tick", "rep");

            //   Determine base unit
            UnitValue uv = null;
            String unitName = isTick ? "ms" : "";
            unitName = stacs.getOrDefault (unitName, "unit");
            if (! unitName.isEmpty ()) uv = new UnitValue (unitName);
            if (uv == null)
            {
                MPart c = (MPart) s.source.child (key);  // Only works for variables that are direct members of part, not for variables in flattened parts.
                if (c != null)
                {
                    MNode originalPart = c.getOriginal ().parent ();
                    MNode base = findBasePart (originalPart);
                    if (base != null)
                    {
                        MNode originalVariable = base.child (key);
                        if (originalVariable != null)
                        {
                            Variable.ParsedValue pv = new Variable.ParsedValue (originalVariable.get ());
                            try
                            {
                                Operator op = Operator.parse (pv.expression);
                                if (op instanceof Constant) uv = ((Constant) op).unitValue;
                            }
                            catch (Exception e) {}
                        }
                    }
                }
                if (uv == null) uv = unitless;
            }
            if (uv.unit == null) uv = unitless;
            Unit<?> baseUnit = uv.unit.getSystemUnit ();
            @SuppressWarnings({"unchecked", "rawtypes"})
            UnitConverter converter = baseUnit.getConverterTo ((Unit) uv.unit);

            //   Determine type and value
            double value = 0;
            String type  = "constant";
            EquationEntry regular = null;
            EquationEntry init    = null;
            for (EquationEntry ee : v.equations)
            {
                if (ee.ifString.equals ("$init")) init    = ee;
                else                              regular = ee;
            }
            if (init == null) init = regular;
            if (init.expression instanceof Constant)
            {
                value = init.expression.getDouble ();
                if (modtype.equals ("10")  &&  name.equals ("I_app"))
                {
                    if (regular != init  &&  regular != null)
                    {
                        // Output I on the side
                        // TODO: analyze type the same way as any other variable. It might not be constant.
                        double valueInit = value;
                        value = regular.expression.getDouble ();
                        valueInit = converter.convert (valueInit - value);
                        add (part, "state", "I", "constant", null, Scalar.print (valueInit));
                    }
                    value -= 140e-12;  // 140pA
                }
            }
            else
            {
                double factor = 1;
                if (init.expression instanceof Multiply)
                {
                    Multiply m = (Multiply) init.expression;
                    if (m.operand0 instanceof Constant)
                    {
                        factor = m.operand0.getDouble ();
                        init.expression = m.operand1;
                    }
                    else if (m.operand1 instanceof Constant)
                    {
                        factor = m.operand1.getDouble ();
                        init.expression = m.operand0;
                    }
                }

                if (init.expression instanceof Uniform)
                {
                    // Determine type of distribution
                    Uniform u = (Uniform) init.expression;
                    if (u.operands.length >= 2)
                    {
                        type = "uniform interval";
                        double min = converter.convert (u.operands[0].getDouble () * factor);
                        double max = converter.convert (u.operands[1].getDouble () * factor);
                        double step = 1;
                        if (u.operands.length > 2) step = u.operands[2].getDouble ();
                        step = converter.convert (step * factor);
                        entry.set (min,  "min");
                        entry.set (max,  "max");
                        entry.set (step, "int");
                    }
                }
            }

            if (type.equals ("constant"))
            {
                value = converter.convert (value);
                entry.set (Scalar.print (value), "value");
            }
            if (! isParam) entry.set (type, "type");  // param values are always constant, so only need to specify type for state values
        }

        // Add part-specific STACS variables which are not included in N2A base models.
        if (modtype.equals ("10"))
        {
            if (part.child ("I") == null) add (part, "state", "I", "constant", null, "0");
        }
        else if (modtype.equals ("12")  ||  modtype.equals ("13"))
        {
            // Determine initial weight
            Variable v = s.find (new Variable ("weight"));
            EquationEntry ee = v.equations.first ();
            double weight = ee.expression.getDouble ();
            v = s.find (new Variable ("Imax"));
            ee = v.equations.first ();
            double Imax = ee.expression.getDouble () * 1e12;  // pA. Ideally we should retrieve the units for Imax, but it's not worth the complexity here.
            weight *= Imax;

            add (part, "state", "weight", "constant", null, Scalar.print (weight));
            if (modtype.equals ("12"))
            {
                add (part, "param", "wmax",   null,       null,   Scalar.print (Imax));
                add (part, "param", "update", null,       null,   "1000");  // hard-coded 1s update cycle
                add (part, "state", "wdelta", "constant", null,   "0");
                add (part, "state", "ptrace", "constant", null,   "0");
                add (part, "state", "ntrace", "constant", null,   "0");
                add (part, "state", "ptlast", "constant", "tick", "0");
                add (part, "state", "ntlast", "constant", "tick", "0");
            }
        }

        // Network structure
        if (isVertex)
        {
            MNode vertex = graph.childOrCreate ("vertex", modname);

            // Determine count
            Variable n = s.find (new Variable ("$n"));
            if (n == null)
            {
                vertex.set (1, "order");
            }
            else
            {
                // $n is generally non-conditional, so just take first equation. Also assume it is constant.
                EquationEntry ee = n.equations.first ();
                int value = (int) Math.floor (ee.expression.getDouble ());
                vertex.set (value, "order");
            }

            // Determine spatial distribution
            vertex.set ("[0,0,0]", "coord");  // Default. May get overridden below ...
            Variable xyz = s.find (new Variable ("$xyz"));
            if (xyz == null)
            {
                vertex.set ("point", "shape");
            }
            else
            {
                // Assume $xyz is non-conditional. Form is generally: $xyz = function(radius) + offset
                EquationEntry ee = xyz.equations.first ();
                if (ee.expression instanceof Add)
                {
                    Add a = (Add) ee.expression;
                    Type offset = null;
                    if (a.operand0 instanceof Constant)
                    {
                        offset = ((Constant) a.operand0).value;
                        ee.expression = a.operand1;
                    }
                    else if (a.operand1 instanceof Constant)
                    {
                        ee.expression = a.operand0;
                        offset = ((Constant) a.operand1).value;
                    }

                    Matrix A = null;
                    if (offset instanceof Scalar)
                    {
                        A = new MatrixDense (1, 3, ((Scalar) offset).value);
                    }
                    else if (offset instanceof Matrix)
                    {
                        A = (Matrix) offset;
                        if (A.columns () == 1) A = A.transpose ();
                    }
                    if (A != null) vertex.set (A, "coord");
                }

                if (ee.expression instanceof Constant)
                {
                    Type offset = ((Constant) ee.expression).value;
                    if (offset instanceof Matrix)
                    {
                        Matrix A = (Matrix) offset;
                        if (A.columns () == 1) A = A.transpose ();
                        vertex.set (A,       "coord");
                        vertex.set ("point", "shape");
                    }
                }
                else if (ee.expression instanceof Sphere)
                {
                    Sphere sp = (Sphere) ee.expression;
                    Matrix A = (Matrix) ((Constant) sp.operands[0]).value;
                    int dimension = A.columns ();
                    if      (dimension == 2) vertex.set ("circle", "shape");
                    else if (dimension == 3) vertex.set ("sphere", "shape");
                    vertex.set (Scalar.print (A.get (0, 0)), "radius");
                }
            }
        }
        else  // edge
        {
            ConnectionBinding A = s.findConnection ("A");
            ConnectionBinding B = s.findConnection ("B");

            MNode edge = graph.childOrCreate ("edge", modname);
            edge.set (      A.endpoint.prefix (),       "source");
            edge.set ("[" + B.endpoint.prefix () + "]", "target");

            String cutoff = "0";
            cutoff = s.source.getOrDefault (cutoff, "A.$radius");
            cutoff = s.source.getOrDefault (cutoff, "B.$radius");
            edge.set (cutoff, "cutoff");

            // Determine connection pattern
            Variable p = s.find (new Variable ("$p"));
            if (p != null)
            {
                EquationEntry ee = null;
                for (EquationEntry e2 : p.equations)
                {
                    if (e2.ifString.equals ("$connect"))
                    {
                        ee = e2;
                        break;
                    }
                    if (e2.condition == null) ee = e2;
                }

                if (ee.expression instanceof Constant)
                {
                    double prob = ee.expression.getDouble ();
                    edge.set (Scalar.print (prob), "connect", "prob");
                    edge.set ("uniform",           "connect", "type");
                }
                else if (ee.expression instanceof EQ)  // Assume to be an index mapping
                {
                    // Determine the relationship between source and destination indices.
                    Variable Aindex = A.endpoint.find (new Variable ("$index"));
                    Variable Bindex = B.endpoint.find (new Variable ("$index"));
                    Equality equality = new Equality ((EQ) ee.expression, Bindex);
                    try
                    {
                        double[] parms = equality.extractLinear (Aindex);
                        edge.set ("index",                 "connect", "type");
                        edge.set (Scalar.print (parms[0]), "connect", "srcmul");
                        edge.set (Scalar.print (parms[1]), "connect", "srcoff");
                    }
                    catch (EvaluationException e) {}
                }
            }
        }
    }

    /**
        Searches ancestors of given part, including part itself, for one that declares itself to be a STACS part.
    **/
    public MNode findBasePart (MNode part)
    {
        if (part.child ("$metadata", "backend", "stacs", "part") != null) return part;

        // In the case of triangle inheritance, this implementation is inefficient because
        // it could examine the same part several times. However, the usual case is single
        // inheritance, so we won't worry about that.
        for (String inherit : part.get ("$inherit").split (","))
        {
            inherit = inherit.replace ("\"", "");
            if (inherit.isEmpty ()) continue;
            MNode parent = AppData.models.child (inherit);
            if (parent == null) continue;
            MNode result = findBasePart (parent);
            if (result != null) return result;
        }
        return null;
    }

    public void add (MNode part, String category, String name, String type, String rep, String value)
    {
        MNode entry = part.childOrCreate (category, name);
        if (type != null) entry.set (type, "type");
        if (rep  != null) entry.set (rep,  "rep");
        entry.set (value, "value");
    }

    public void dumpWithKey (String prefix, String key, MNode node, Writer writer) throws IOException
    {
        writer.write (prefix + "  - " + key + ": " + node.key () + "\n");
        for (MNode c : node)
        {
            writer.write (prefix + "    " + c.key () + ": " + c.get () + "\n");
            if (c.size () > 0) dump (prefix + "    ", c, writer);
        }
    }

    public void dump (String prefix, MNode node, Writer writer) throws IOException
    {
        String prefix2 = prefix + "  - ";
        for (MNode c : node)
        {
            writer.write (prefix2 + c.key () + ": " + c.get () + "\n");
            prefix2 = prefix + "    ";
        }
    }
}
