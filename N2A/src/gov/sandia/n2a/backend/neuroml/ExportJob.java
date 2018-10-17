/*
Copyright 2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.neuroml;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import javax.measure.Dimension;
import javax.measure.IncommensurableException;
import javax.measure.UnconvertibleException;
import javax.measure.Unit;
import javax.measure.UnitConverter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import gov.sandia.n2a.backend.neuroml.PartMap.NameMap;
import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MPersistent;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.eqset.VariableReference;
import gov.sandia.n2a.eqset.EquationSet.ConnectionBinding;
import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.Comparison;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.ParseException;
import gov.sandia.n2a.language.Renderer;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.Visitor;
import gov.sandia.n2a.language.function.Uniform;
import gov.sandia.n2a.language.operator.GE;
import gov.sandia.n2a.language.operator.GT;
import gov.sandia.n2a.language.operator.LE;
import gov.sandia.n2a.language.operator.LT;
import gov.sandia.n2a.language.operator.NE;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.MatrixDense;
import gov.sandia.n2a.language.type.Scalar;

public class ExportJob extends XMLutility
{
    public PartMap     partMap;
    public Sequencer   sequencer;
    public Document    doc;
    public String      modelName;
    public EquationSet equations;

    public List<Element>       elements       = new ArrayList<Element> ();
    public List<IonChannel>    channels       = new ArrayList<IonChannel> ();
    public List<Synapse>       synapses       = new ArrayList<Synapse> ();
    public List<AbstractCell>  cells          = new ArrayList<AbstractCell> ();
    public List<Network>       networks       = new ArrayList<Network> ();
    public List<ComponentType> componentTypes = new ArrayList<ComponentType> ();
    public int                 countConcentration;
    public int                 countInput;
    public Map<Unit<?>,String> unitsUsed      = new HashMap<Unit<?>,String> ();
    public boolean             requiresNML    = false;  // Indicates that no NeuroML parts were emitted.

    public static Unit<?> um        = UCUM.parse ("um");            // micrometers, used for morphology
    public static double  baseRatio = Math.log (10) / Math.log (2); // log_2 (10), how many binary digits it takes to represent one decimal digit

    public ExportJob (PartMap partMap, Sequencer sequencer)
    {
        this.partMap   = partMap;
        this.sequencer = sequencer;
    }

    public void process (MNode source, File destination)
    {
        try
        {
            MPart mpart = new MPart ((MPersistent) source);
            modelName = source.key ();
            equations = new EquationSet (mpart);
            makeExecutable (equations);
            analyze (equations);

            DocumentBuilderFactory factoryBuilder = DocumentBuilderFactory.newInstance ();
            DocumentBuilder builder = factoryBuilder.newDocumentBuilder ();
            doc = builder.newDocument ();

            process (mpart);  // Convert top-level N2A part into top-level NeuroML elements

            DOMSource dom = new DOMSource (doc);
            StreamResult stream = new StreamResult (new OutputStreamWriter (new FileOutputStream (destination), "UTF-8"));
            TransformerFactory factoryXform = TransformerFactory.newInstance ();
            factoryXform.setAttribute ("indent-number", 4);
            Transformer xform = factoryXform.newTransformer ();
            xform.setOutputProperty (OutputKeys.INDENT, "yes");
            xform.transform (dom, stream);
        }
        catch (Exception e)
        {
            e.printStackTrace ();
        }
    }

    /**
        Find references to $index in connection endpoints, and set up info for ConnectionContext.
    **/
    public void analyze (EquationSet s)
    {
        for (EquationSet p : s.parts) analyze (p);

        class IndexVisitor extends Visitor
        {
            Variable v;
            public boolean visit (Operator op)
            {
                if (op instanceof AccessVariable)
                {
                    VariableReference r = ((AccessVariable) op).reference;
                    if (r == null) return true;  // It is possible that some variables were not resolved.
                    Variable rv = r.variable;
                    if (rv.container != v.container  &&  ! r.resolution.isEmpty ())
                    {
                        Object o = r.resolution.get (r.resolution.size () - 1);
                        if (o instanceof ConnectionBinding)
                        {
                            // This is somewhat of a hack, but ConnectionContext assumes the mappings A->0 and B->1.
                            switch (((ConnectionBinding) o).alias)
                            {
                                case "A": r.index = 0; break;
                                case "B": r.index = 1; break;
                            }
                        }
                    }
                }
                return true;
            }
        };
        IndexVisitor visitor = new IndexVisitor ();
        for (final Variable v : s.variables)
        {
            visitor.v = v;
            v.visit (visitor);

            // Check for dummy variables.
            // Normally, this is done in EquationSet.removeUnused(). However, our simplified analysis
            // does not call that function.
            if (v.hasUsers ()  ||  v.hasAttribute ("externalWrite")) continue;
            if (v.name.startsWith ("$")  ||  v.name.contains (".$")) continue;
            for (EquationEntry e : v.equations)
            {
                if (e.expression.isOutput ())
                {
                    v.addAttribute ("dummy");
                    break;
                }
            }
        }
    }

    /**
        Adapter to provide basic evaluation environment for extracting values of variables.
        Some variables are given explicit values, while all others return their default values.
    **/
    public static class SimpleContext extends Instance
    {
        public int index;
        public Type get (VariableReference r)
        {
            return get (r.variable);
        }
        public Type get (Variable v)
        {
            if (v.name.equals ("$index")) return new Scalar (index);
            if (v.name.equals ("$init" )) return new Scalar (1);
            return v.type;
        }
    };
    public SimpleContext context = new SimpleContext ();

    public static class ConnectionContext extends SimpleContext
    {
        public int Aindex;
        public int Bindex;
        public Type get (VariableReference r)
        {
            if (r.index == 0) return new Scalar (Aindex);
            if (r.index == 1) return new Scalar (Bindex);
            return get (r.variable);
        }
    };

    public void process (MPart source)
    {
        if (source.get ("$metadata", "backend.lems.part").isEmpty ())
        {
            for (EquationSet p : equations.parts) topLevelPart ((MPart) p.source);
        }
        else
        {
            topLevelPart (source);
        }

        appendUnits (requiresNML);

        // Collate
        Element root;
        if (componentTypes.isEmpty ())
        {
            root = doc.createElement ("neuroml");
            root.setAttribute ("xmlns",              "http://www.neuroml.org/schema/neuroml2");
            root.setAttribute ("xmlns:xsi",          "http://www.w3.org/2001/XMLSchema-instance");
            root.setAttribute ("xsi:schemaLocation", "http://www.neuroml.org/schema/neuroml2 ../Schemas/NeuroML2/NeuroML_v2beta4.xsd");
            root.setAttribute ("id", modelName);
        }
        else
        {
            root = doc.createElement ("Lems");
            if (requiresNML)
            {
                // TODO: Include NeuroML definition files for LEMS
            }
        }
        sequencer.append (root, elements);
        doc.appendChild (root);
    }

    public void topLevelPart (MPart source)
    {
        String type    = source.get ("$metadata", "backend.lems.part");
        String inherit = source.get ("$inherit");
        if (type.equals ("network"))
        {
            requiresNML = true;
            Network network = new Network (source);
            networks.add (network);
        }
        else if (type.equals ("cell")  ||  type.equals ("segment")  ||  type.contains ("Cell"))
        {
            requiresNML = true;
            AbstractCell cell = addCell (source, true);
            if (cell.populationSize > 1)  // Wrap cell in a network
            {
                Network network = new Network (source.key (), cell);
                networks.add (network);
            }
        }
        else if (type.contains ("Input")  ||  type.contains ("Generator")  ||  type.contains ("Clamp")  ||  type.contains ("spikeArray")  ||  type.contains ("PointCurrent"))
        {
            requiresNML = true;
            input (source, elements, null);
        }
        else if (type.contains ("Synapse"))
        {
            requiresNML = true;
            Synapse s = addSynapse (source, false);
            s.id = source.key ();
        }
        else if (inherit.contains ("Coupling"))
        {
            requiresNML = true;
            Synapse s = addSynapse (source, true);
            s.id = source.key ();
        }
        else
        {
            if (! type.isEmpty ()) requiresNML = true;  // backend.lems.part only refers to an NML base part.
            genericPart (source, elements);
        }
    }

    public class Network
    {
        String                 id;
        List<Element>          networkElements = new ArrayList<Element> ();
        Map<String,Population> populations     = new TreeMap<String,Population> ();
        List<Space>            spaces          = new ArrayList<Space> ();

        public Network (String populationID, AbstractCell cell)
        {
            id = "N2A_Network" + networks.size ();

            Element network = addElement ("network", elements);
            network.setAttribute ("id", id);

            Element population = doc.createElement ("population");
            network.appendChild (population);
            population.setAttribute ("id",        populationID);
            population.setAttribute ("component", cell.id);
            population.setAttribute ("size",      String.valueOf (cell.populationSize));  // size is always greater than 1 here, because of the way this ctor is called.
        }

        public Network (MPart source)
        {
            id = source.key ();
            String lemsID = source.get ("$metadata", "backend.lems.id");
            if (! lemsID.isEmpty ()) id = lemsID;

            // Collect populations first, because they contain info needed by projections and inputs.
            for (MNode c : source)
            {
                MPart p = (MPart) c;
                if (! p.isPart ()) continue;
                String type = p.get ("$metadata", "backend.lems.part");
                if (type.equals ("cell")  ||  type.equals ("segment")  ||  type.endsWith ("Cell")  ||  type.contains ("baseCell"))
                {
                    Population pop = new Population (p);
                    populations.put (pop.id, pop);
                }
            }

            // Projections and inputs
            for (MNode c : source)
            {
                MPart p = (MPart) c;
                if (! p.isPart ()) continue;
                String type = p.get ("$metadata", "backend.lems.part");
                String inherit = p.get ("$inherit").replace ("\"", "");
                if (type.contains ("Synapse"))
                {
                    Element result = addElement ("projection", networkElements);
                    result.setAttribute ("synapse", addSynapse (p, false).id);
                    connections (p, result, "connection", "", "");
                }
                else if (type.contains ("Projection"))  // gap junctions and split synapses. These use the special projection types.
                {
                    projectionSplit (p);
                }
                else if (inherit.equals ("Coupling"))
                {
                    // TODO: convert electricalProjection to Coupling during import
                    Element result = addElement ("electricalProjection", networkElements);
                    String synapseID = addSynapse (p, true).id;
                    connections (source, result, "electricalConnection", synapseID, synapseID);
                }
                else if (type.contains ("Input")  ||  type.contains ("Generator")  ||  type.contains ("Clamp")  ||  type.contains ("spikeArray")  ||  type.contains ("PointCurrent"))  // unary connection with embedded input
                {
                    String inputID = input (p, elements, null);
                    if (! Operator.containsConnect (p.get ("B")))  // There are NeuroML files that create an input without incorporating it into a network, yet our importer wraps the whole result in a network. This guard prevents a malformed element in the output.
                    {
                        Element result = addElement ("inputList", networkElements);
                        result.setAttribute ("component", inputID);
                        connections (p, result, "input", "", "");
                    }
                }
                else if (type.isEmpty ())  // binary connection which references a shared input generator
                {
                    Element result = addElement ("inputList", networkElements);
                    result.setAttribute ("component", p.get ("A"));
                    connections (p, result, "input", "", "");
                }
            }

            for (Space s : spaces) s.append ();

            Element network = addElement ("network", elements);
            network.setAttribute ("id", id);
            standalone (source, network, networkElements);
            String temperature = source.get ("temperature");
            if (! temperature.isEmpty ())
            {
                network.setAttribute ("type", "networkWithTemperature");
                network.setAttribute ("temperature", biophysicalUnits (temperature));
            }
            sequencer.append (network, networkElements);
        }

        public class Population
        {
            public String       id;
            public AbstractCell cell;

            public Population (MPart source)
            {
                id = source.key ();
                cell = addCell (source, false);

                EquationSet part = getEquations (source);
                Variable n = part.find (new Variable ("$n", 0));
                int size = 1;
                if (n != null) size = (int) Math.floor (((Scalar) n.eval (context)).value);

                // Determine type of spatial layout
                MNode xyzNode = source.child ("$xyz");
                boolean list = false;
                if (xyzNode != null)
                {
                    if (xyzNode.size () > 0)
                    {
                        list = true;
                    }
                    else
                    {
                        String value = xyzNode.get ();
                        if (! value.contains ("grid")  &&  ! value.contains ("uniform")) list = true;
                    }
                }

                // Create population element
                Element population = addElement ("population", networkElements);
                population.setAttribute ("id", id);
                population.setAttribute ("component", cell.id);
                if (list) population.setAttribute ("type", "populationList");
                if (size > 1) population.setAttribute ("size", String.valueOf (size));

                // Output 3D structure
                if (list)
                {
                    Variable xyz = part.find (new Variable ("$xyz", 0));
                    Variable ijk = part.find (new Variable ("ijk",  0));
                    for (int index = 0; index < size; index++)
                    {
                        context.index = index;

                        Element instance = doc.createElement ("instance");
                        population.appendChild (instance);
                        instance.setAttribute ("id", String.valueOf (index));
                        if (ijk != null)
                        {
                            Matrix ijkVector = (Matrix) ijk.eval (context);
                            instance.setAttribute ("i", print (ijkVector.get (0)));
                            instance.setAttribute ("j", print (ijkVector.get (1)));
                            instance.setAttribute ("k", print (ijkVector.get (2)));
                        }

                        Element location = doc.createElement ("location");
                        instance.appendChild (location);
                        Matrix xyzVector = (Matrix) xyz.eval (context);
                        location.setAttribute ("x", print (xyzVector.get (0) * 1e6));
                        location.setAttribute ("y", print (xyzVector.get (1) * 1e6));
                        location.setAttribute ("z", print (xyzVector.get (2) * 1e6));
                    }
                }
                else if (xyzNode != null)
                {
                    Element layout = doc.createElement ("layout");
                    population.appendChild (layout);

                    String value = xyzNode.get ();
                    if (value.contains ("grid"))
                    {
                        value = value.split ("(", 2)[1];
                        String[] pieces = value.split (")", 2);
                        String parms = pieces[0];
                        value = pieces[1];

                        pieces = parms.split (",");
                        Element grid = doc.createElement ("grid");
                        layout.appendChild (grid);
                        grid.setAttribute ("xSize", pieces[1]);
                        grid.setAttribute ("ySize", pieces[2]);
                        grid.setAttribute ("zSize", pieces[3]);

                        value = value.trim ();
                        if (! value.isEmpty ())
                        {
                            Matrix scale = null;
                            if (value.startsWith ("&"))
                            {
                                pieces = value.split ("+", 2);
                                scale = parseVector (pieces[0]);
                                value = pieces[1];
                            }

                            Matrix offset = null;
                            if (! value.isEmpty ())
                            {
                                offset = parseVector (value);
                            }

                            if (scale != null  ||  offset != null)
                            {
                                Space space = new Space (scale, offset);
                                int index = spaces.indexOf (space);
                                if (index >= 0) space = spaces.get (index);
                                else            spaces.add (space);
                                layout.setAttribute ("space", space.name);
                            }
                        }
                    }
                    else if (value.contains ("uniform"))
                    {
                        Element random = doc.createElement ("grid");
                        layout.appendChild (random);
                        random.setAttribute ("number", String.valueOf (size));

                        value = value.split ("(", 2)[1];
                        String[] pieces = value.split (")", 2);
                        String parms = pieces[0];
                        value = pieces[1];

                        Matrix scale = null;
                        parms = parms.trim ();
                        if (! parms.isEmpty ()) parseVector (parms);

                        Matrix offset = null;
                        value = value.trim ();
                        if (! value.isEmpty ()) offset = parseVector (value);

                        if (scale != null   ||  offset != null)
                        {
                            Space space = new Space (scale, offset);
                            int index = spaces.indexOf (space);
                            if (index >= 0) space = spaces.get (index);
                            else            spaces.add (space);
                            layout.setAttribute ("space", space.name);
                        }
                    }
                    // else this is something weird
                }
            }

            public Matrix parseVector (String input)
            {
                input = input.split ("[", 2)[1].split ("]", 2)[0];
                String[] pieces = input.split (";");
                Matrix result = new MatrixDense (3, 1);
                result.set (0, Scalar.convert (pieces[0]));
                result.set (1, Scalar.convert (pieces[1]));
                result.set (2, Scalar.convert (pieces[2]));
                return result;
            }
        }

        public class Space
        {
            public String name;
            public Matrix scale;
            public Matrix offset;

            public Space (Matrix scale, Matrix offset)
            {
                name = "N2A_Space" + spaces.size ();
                this.scale  = scale;
                this.offset = offset;
            }

            public void append ()
            {
                Element space = addElement ("space", networkElements);
                Element structure = doc.createElement ("structure");
                space.appendChild (structure);
                if (scale != null)
                {
                    structure.setAttribute ("xSpacing", print (scale.get (0) * 1e6));
                    structure.setAttribute ("ySpacing", print (scale.get (1) * 1e6));
                    structure.setAttribute ("zSpacing", print (scale.get (2) * 1e6));
                }
                if (offset != null)
                {
                    structure.setAttribute ("xStart", print (offset.get (0) * 1e6));
                    structure.setAttribute ("yStart", print (offset.get (1) * 1e6));
                    structure.setAttribute ("zStart", print (offset.get (2) * 1e6));
                }
            }

            public boolean equals (Object o)
            {
                if (! (o instanceof Space)) return false;
                Space that = (Space) o;
                if (scale != that.scale)
                {
                    if (scale == null  ||  that.scale == null) return false;
                    if (! scale.equals (that.scale)) return false;
                }
                if (offset != that.offset)
                {
                    if (offset == null  ||  that.offset == null) return false;
                    if (! offset.equals (that.offset)) return false;
                }
                return true;
            }
        }

        public void projectionSplit (MPart source)
        {
            // Determine the two sides of the connection
            MPart preNode  = null;
            MPart postNode = null;
            boolean preGap  = false;
            boolean postGap = false;
            for (MNode c : source)
            {
                MPart p = (MPart) c;
                String type = c.get ("$metadata", "backend.lems.part");
                if (! type.contains ("Synapse")) continue;
                if (c.get ("V").contains ("A"))
                {
                    preNode = p;
                    preGap  = type.contains ("gap");
                }
                else
                {
                    postNode = p;
                    postGap  = type.contains ("gap");
                }
            }

            boolean electrical =  preGap  &&  postGap;
            String projectionType = electrical ? "electricalProjection" : "continuousProjection";
            String connectionType = electrical ? "electricalConnection" : "continuousConnection";

            String preComponent  = addSynapse (preNode, electrical).id;
            String postComponent = addSynapse (postNode, electrical).id;

            Element result = addElement (projectionType, networkElements);
            connections (source, result, connectionType, preComponent, postComponent);
        }

        public void connections (MPart source, Element result, String type, String preComponent, String postComponent)
        {
            result.setAttribute ("id", source.key ());

            String[] pieces = source.get ("A").split ("\\.");
            String prePopulation = pieces[0];
            AbstractCell preCell = null;
            Population pop = populations.get (prePopulation);
            if (pop != null) preCell = pop.cell;
            String preSegment = "";
            if (pieces.length > 1) preSegment = pieces[1];

            pieces = source.get ("B").split ("\\.");
            String postPopulation = pieces[0];
            AbstractCell postCell = null;
            pop = populations.get (postPopulation);
            if (pop != null) postCell = pop.cell;
            String postSegment = "";
            if (pieces.length > 1) postSegment = pieces[1];

            boolean electrical   = type.contains ("electrical");
            boolean inputList    = type.equals ("input");
            boolean isConnection = type.equals ("connection");
            if (inputList)
            {
                result.setAttribute ("population", postPopulation);
            }
            else
            {
                result.setAttribute ("presynapticPopulation", prePopulation);
                result.setAttribute ("postsynapticPopulation", postPopulation);
            }

            // Prepare list of conditions
            MNode originalP = source.child ("$p");
            MVolatile p = new MVolatile ();
            if (originalP == null)  // no $p, so all-to-all connection
            {
                // Generate every possible combination
                // Even self-connection is implied by an absent $p
                int preN = 1;
                if (preCell != null) preN = preCell.populationSize;
                int postN = 1;
                if (postCell != null) postN = postCell.populationSize;
                for (int i = 0; i < preN; i++)
                {
                    String A;
                    if (preSegment.isEmpty ()) A = "A.$index==" + i;
                    else                       A = "A.$up.$index==" + i;

                    for (int j = 0; j < postN; j++)
                    {
                        String B;
                        if (postSegment.isEmpty ()) B = "B.$index==" + j;
                        else                        B = "B.$up.$index==" + j;

                        p.set ("@" + A + "&&" + B, "1");
                    }
                }
            }
            else
            {
                p.set (originalP);
                String condition = p.get ();
                if (! condition.isEmpty ()  &&  ! condition.equals ("0"))
                {
                    p.set ("");
                    p.set ("@" + condition, "1");
                }
            }

            // Scan conditions and emit connection for each one
            List<Element> projectionElements = new ArrayList<Element> ();
            int count = 0;
            for (MNode c : p)
            {
                if (! c.get ().equals ("1")) continue;
                String condition = c.key ();

                String indexA   = "0";
                String indexAup = "0";
                String indexB   = "0";
                String indexBup = "0";
                String[] clauses = condition.substring (1).split ("&&");
                for (String clause : clauses)
                {
                    pieces = clause.split ("==");
                    switch (pieces[0])
                    {
                        case "A.$index"    : indexA   = pieces[1]; break;
                        case "A.$up.$index": indexAup = pieces[1]; break;
                        case "B.$index"    : indexB   = pieces[1]; break;
                        case "B.$up.$index": indexBup = pieces[1]; break;
                    }
                }

                double weight       = conditionalParameter (source, "weight",       condition, 1);
                double delay        = conditionalParameter (source, "delay",        condition, 0);
                double preFraction  = conditionalParameter (source, "preFraction",  condition, 0.5);
                double postFraction = conditionalParameter (source, "postFraction", condition, 0.5);

                String typeWD = type;
                if (isConnection  &&  !(weight == 1  &&  delay == 0)) typeWD += "WD";
                Element connection = addElement (typeWD, projectionElements);
                connection.setAttribute ("id", String.valueOf (count++));

                String Cell    = "Cell";
                String Segment = "Segment";
                if (isConnection  ||  inputList)
                {
                    Cell    += "Id";
                    Segment += "Id";
                }

                if (! inputList)
                {
                    String index;
                    if (preSegment.isEmpty ())  // point cell
                    {
                        index = indexA;
                    }
                    else  // multi-compartment cell
                    {
                        index = indexAup;
                        // If we get here, preCell should never be null, but we have to defend against bad user input.
                        if (preCell != null)
                        {
                            String mappedID = ((Cell) preCell).mapID (preSegment, indexA);
                            if (! mappedID.equals ("0")) connection.setAttribute ("pre" + Segment, mappedID);
                        }
                    }
                    connection.setAttribute ("pre" + Cell, index);
                }

                String index;
                if (postSegment.isEmpty ())
                {
                    index = indexB;
                }
                else
                {
                    index = indexBup;
                    if (postCell != null)
                    {
                        String mappedID = ((Cell) postCell).mapID (postSegment, indexB);
                        if (! mappedID.equals ("0"))  // Strictly speaking, inputList does not specify a default for segmentId, so we might need to emit it in any case.
                        {
                            if (inputList) connection.setAttribute ("segmentId",      mappedID);
                            else           connection.setAttribute ("post" + Segment, mappedID);
                        }
                    }
                }
                if (inputList) connection.setAttribute ("target",      index);
                else           connection.setAttribute ("post" + Cell, index);

                if (inputList)
                {
                    if (postFraction != 0.5) connection.setAttribute ("fractionAlong", print (postFraction));
                }
                else
                {
                    if (weight       != 1  ) connection.setAttribute ("weight",            print (weight));
                    if (delay        != 0  ) connection.setAttribute ("delay",             print (delay));
                    if (preFraction  != 0.5) connection.setAttribute ("preFractionAlong",  print (preFraction));
                    if (postFraction != 0.5) connection.setAttribute ("postFractionAlong", print (postFraction));
                }

                if (electrical)
                {
                    connection.setAttribute ("synapse", preComponent);
                }
                else if (inputList)
                {
                    connection.setAttribute ("destination", "synapses");
                }
                else
                {
                    if (! preComponent .isEmpty ()) connection.setAttribute ("preComponent",  preComponent);
                    if (! postComponent.isEmpty ()) connection.setAttribute ("postComponent", postComponent);
                }
            }
            sequencer.append (result, projectionElements);
        }

        public double conditionalParameter (MPart source, String name, String condition, double defaultValue)
        {
            String value = source.get (name, condition);
            if (value.isEmpty ()) value = source.get (name);
            if (! value.isEmpty ()) return Scalar.convert (value);
            return defaultValue;
        }
    }

    public Synapse addSynapse (MPart source, boolean electrical)
    {
        Synapse s = new Synapse (source, electrical);
        int index = synapses.indexOf (s);
        if (index >= 0)
        {
            s = synapses.get (index);
        }
        else
        {
            synapses.add (s);
            s.append ();
        }

        // Check for chained synapse (embedded input)
        MPart A = (MPart) source.child ("A");
        if (A == null  ||  A.size () == 0) return s;
        Synapse s2 = new Synapse (s.id, A);

        index = synapses.indexOf (s2);
        if (index >= 0)
        {
            s2 = synapses.get (index);
        }
        else
        {
            synapses.add (s2);
            s2.append ();
        }
        return s2;
    }

    public class Synapse
    {
        String  id;
        String  chainID = "";
        MPart   source;
        MNode   base    = new MVolatile ();
        boolean electrical;  // A hint about context. Shouldn't change identity.
        boolean usedInCell;  // This is a Coupling between segments in a single cell model, and thus should not be emitted. (Parameter overrides, such as to G, are not supported in this case.)

        public Synapse (MPart source, boolean electrical)
        {
            this.source     = source;
            this.electrical = electrical;

            // Assemble a part that best recreates the underlying synapse before it got incorporated into a projection
            base.merge (source);
            for (String key : new String[] {"A", "B", "$p", "weight", "delay", "preFraction", "postFraction"}) base.clear (key);
            String type = source.get ("$metadata", "backend.lems.part");
            if (type.contains ("gapJunction")  ||  type.contains ("silentSynapse")  ||  type.contains ("gradedSynapse"))
            {
                for (String key : new String[] {"A.I", "B.I", "V", "Vpeer"}) base.clear (key);
            }

            String inherit = source.get ("$inherit").replace ("\"", "");
            if (inherit.startsWith (modelName))
            {
                id = inherit.substring (modelName.length ()).trim ();
            }
            else
            {
                id = source.get ("$metadata", "backend.lems.id");
                if (id.isEmpty ()) id = "N2A_Synapse" + synapses.size ();
            }
        }

        public Synapse (String chainID, MPart source)
        {
            this.chainID = chainID;
            this.source  = source;

            String lemsID = source.get ("$metadata", "backend.lems.id");
            if (! lemsID.isEmpty ()) id = lemsID;
        }

        public void append ()
        {
            if (usedInCell) return;

            if (! chainID.isEmpty ())  // We have embedded input, so the main synapse will be emitted elsewhere.
            {
                input (source, elements, this);
                return;
            }

            if (source.get ("$inherit").contains ("Coupling"))
            {
                EquationSet part = getEquations (source);
                Variable G = part.find (new Variable ("G", 0));
                double conductance = ((Scalar) G.eval (context)).value;

                Element synapse = addElement ("gapJunction", elements);
                synapse.setAttribute ("id", id);
                synapse.setAttribute ("conductance", print (conductance));
                return;
            }

            String type = source.get ("$metadata", "backend.lems.part").split (",")[0];
            if (type.startsWith ("gap")  &&  ! electrical) type = "linearGradedSynapse";

            List<Element> synapseElements = new ArrayList<Element> ();
            ArrayList<String> skip = new ArrayList<String> ();
            for (MNode c : source)
            {
                MPart p = (MPart) c;
                String key = p.key ();
                String partType = p.get ("$metadata", "backend.lems.part").split (",")[0];
                if (partType.startsWith ("tsodyksMarkramDep"))
                {
                    skip.add (key);
                    Element plasticity = addElement ("plasticityMechanism", synapseElements);
                    genericPart (p, plasticity);

                    NameMap nameMap = partMap.importMap (partType);
                    String tauFac = nameMap.importName ("tauFac");
                    if (Scalar.convert (p.get (tauFac)) == 0) plasticity.setAttribute ("type", "tsodyksMarkramDepMechanism");
                    else                                      plasticity.setAttribute ("type", "tsodyksMarkramDepFacMechanism");
                }
                else if (partType.contains ("BlockMechanism"))
                {
                    skip.add (key);
                    Element block = addElement ("blockMechanism", synapseElements);
                    block.setAttribute ("type", "voltageConcDepBlockMechanism");
                    block.setAttribute ("species", p.get ("$metadata", "species"));
                    genericPart (p, block);
                }
            }
            if (skip.size () > 0) type = "blockingPlasticSynapse";
            skip.add ("A");

            Element s = addElement (type, elements);
            s.setAttribute ("id", id);
            sequencer.append (s, synapseElements);
            genericPart (source, s, skip);
        }

        public boolean equals (Object o)
        {
            if (! (o instanceof Synapse)) return false;
            Synapse that = (Synapse) o;
            if (! chainID.isEmpty ()  ||  ! that.chainID.isEmpty ())
            {
                return chainID.equals (that.chainID)  &&  source.equals (that.source);
            }
            return base.equals (that.base);
        }
    }

    public String input (MPart source, List<Element> parentElements, Synapse synapse)
    {
        String type = source.get ("$metadata", "backend.lems.part");

        List<Element> inputElements = new ArrayList<Element> ();
        List<String> skip = new ArrayList<String> ();
        for (MNode c : source)
        {
            MPart p = (MPart) c;
            if (p.isPart ())
            {
                skip.add (p.key ());
                input (p, inputElements, null);
            }
            else if (p.key ().equals ("times"))
            {
                skip.add ("times");
                String[] pieces = p.get ().split ("\\[", 2)[1].split ("]", 2)[0].split (";");
                for (int i = 0; i < pieces.length; i++)
                {
                    if (Scalar.convert (pieces[i]) == Double.POSITIVE_INFINITY) continue;
                    Element spike = addElement ("spike", inputElements);
                    spike.setAttribute ("id", String.valueOf (i));
                    spike.setAttribute ("time", biophysicalUnits (pieces[i]));
                }
            }
        }

        if (type.contains ("ramp")  &&  type.contains ("pulse"))  // decide between them
        {
            NameMap nameMap = partMap.importMap ("rampGenerator");
            EquationSet sourceEquations = getEquations (source);
            Variable high1 = sourceEquations.find (new Variable (nameMap.importName ("startAmplitude"), 0));
            Variable high2 = sourceEquations.find (new Variable (nameMap.importName ("finishAmplitude"), 0));
            try
            {
                double value1 = ((Scalar) high1.eval (context)).value;
                double value2 = ((Scalar) high2.eval (context)).value;
                if (value1 == value2) type = "pulseGenerator";
                else                  type = "rampGenerator";
            }
            catch (Exception e)  // eval can fail if eqset contains fatal errors
            {
                if (source.get ("high2").equals ("high1")) type = "pulseGenerator";
                else                                       type = "rampGenerator";
            }
        }
        else if (type.contains (","))  // more action is needed
        {
            if (synapse != null)
            {
                String[] types = type.split (",");
                for (String t : types)
                {
                    if (t.contains ("Synap"))  // prefix of both "Synapse" and "Synaptic"
                    {
                        type = t;
                        break;
                    }
                }
            }
            type = type.split (",")[0];  // remove any remaining ambiguity
        }

        Element input = addElement (type, parentElements);
        String id;
        if (synapse == null)
        {
            id = source.get ("$metadata", "backend.lems.id");
            if (id.isEmpty ()) id = source.key ();
        }
        else
        {
            id = synapse.id;
            input.setAttribute ("synapse",            synapse.chainID);
            input.setAttribute ("spikeTarget", "./" + synapse.chainID);
        }
        input.setAttribute ("id", id);
        standalone (source, input, inputElements);
        genericPart (source, input, skip);
        sequencer.append (input, inputElements);
        return id;
    }

    public AbstractCell addCell (MPart source, boolean topLevel)
    {
        AbstractCell cell = null;
        String type = source.get ("$metadata", "backend.lems.part");
        if (type.equals ("cell")  ||  type.equals ("segment"))  // multi-compartment cell, or HH segment pretending to be a point cell
        {
            cell = new Cell (source);
        }
        else if (type.endsWith ("Cell")  ||  type.contains ("baseCell"))  // Standard point cell, or custom LEMS cell.
        {
            cell = new AbstractCell (source);
        }
        if (cell == null) throw new RuntimeException ("Not a cell type");

        int index = cells.indexOf (cell);
        if (index >= 0) return cells.get (index);
        cells.add (cell);
        if (topLevel  &&  cell.populationSize == 1)
        {
            cell.id = source.key ();  // Use final name as ID, since this is purely a cell model
        }
        cell.append ();
        return cell;
    }

    public class AbstractCell
    {
        public String id;
        public MPart  source;
        public MNode  base;
        public int    populationSize;  // Ideally this would be a member of Population, but cells can be created outside a network, and N2A (but not NeuroML) allows them to have $n > 1.

        public AbstractCell (MPart source)
        {
            this.source = source;

            id = source.get ("$metadata", "backend.lems.id");
            if (id.isEmpty ())
            {
                String inherit = source.get ("$inherit").replace ("\"", "");
                if (inherit.startsWith (modelName)) id = inherit.substring (modelName.length ()).trim ();
            }
            if (id.isEmpty ()) id = "N2A_Cell" + cells.size ();  // Can't be source.key(), because that is most likely reserved for population id. If this is at top level, then id will be replaced with key elsewhere.

            base = new MVolatile ();
            base.merge (source);
            base.clear ("$n");
            base.clear ("$xyz");
            base.clear ("ijk");

            EquationSet part = getEquations (source);
            populationSize = 1;
            Variable n = part.find (new Variable ("$n", 0));
            if (n != null) populationSize = (int) Math.floor (((Scalar) n.eval (context)).value);
        }

        public void append ()
        {
            String               type = source.get ("$metadata", "backend.lems.name");
            if (type.isEmpty ()) type = source.get ("$metadata", "backend.lems.part").split (",")[0];
            List<String> skip = new ArrayList<String> ();
            if (type.equals ("izhikevichCell"))
            {
                // Check if crucial variables have been touched
                if (anyOverride (source, "C", "k", "vr", "vt")) type = "izhikevich2007Cell";
            }
            else if (type.startsWith ("iaf"))
            {
                // There are four types of IAF, depending on whether it is refractory, and whether it uses tau.
                NameMap nameMap = partMap.importMap (type);
                String tau = nameMap.importName ("tau");
                MPart p = (MPart) source.child (tau);
                boolean hasTau =  p != null  &&  p.isFromTopDocument ();

                String refract = nameMap.importName ("refract");
                p = (MPart) source.child (refract);
                boolean hasRefract =  p != null  &&  p.isFromTopDocument ();

                if (hasTau)
                {
                    if (hasRefract) type = "iafTauRefCell";
                    else            type = "iafTauCell";
                }
                else
                {
                    if (hasRefract) type = "iafRefCell";
                    else            type = "iafCell";
                }
            }
            else if (type.startsWith ("fitzHughNagumo"))
            {
                // Since TS is a hard-coded constant, there's really nothing we can do.
                // In general, prefer FN1969 over the more limited FN.
                if (source.get ("TS").equals ("1s")) type = "fitzHughNagumoCell";
                else                                 type = "fitzHughNagumo1969Cell";
                skip.add ("TS");
            }

            Element cell = addElement (type, elements);
            cell.setAttribute ("id", id);
            standalone (source, cell);
            genericPart (source, cell, skip);

            if (type.equals ("izhikevichCell"))  // strip units
            {
                for (String v : new String[] {"a", "b", "c", "d"})
                {
                    MPart p = (MPart) source.child (v);
                    String value = p.get ();
                    int index = findUnits (value);
                    if (index < value.length ())
                    {
                        value = value.substring (0, index);
                        cell.setAttribute (v, value);
                    }
                }
            }
        }

        public boolean anyOverride (MPart source, String... names)
        {
            for (String n : names)
            {
                MPart p = (MPart) source.child (n);
                if (p == null  ||  p.isFromTopDocument ()) return true;
            }
            return false;
        }

        public boolean equals (Object o)
        {
            if (! (o instanceof AbstractCell)) return false;
            AbstractCell that = (AbstractCell) o;
            return base.equals (that.base);
        }
    }

    public class Cell extends AbstractCell
    {
        public Element             cell;
        public List<Element>       cellElements   = new ArrayList<Element> ();
        public List<Element>       morphology     = new ArrayList<Element> ();  // break out sub-element for easy access
        public List<Element>       membrane       = new ArrayList<Element> ();  // sub-element of biophysical properties 
        public List<Element>       intra          = new ArrayList<Element> ();  // ditto
        public List<Element>       extra          = new ArrayList<Element> ();  // ditto
        public List<Property>      properties     = new ArrayList<Property> ();
        public List<Segment>       segments       = new ArrayList<Segment> ();
        public List<SegmentBlock>  blocks         = new ArrayList<SegmentBlock> ();  // entries map to rows of P
        public MatrixBoolean       P              = new MatrixBoolean ();
        public MatrixBoolean       G              = new MatrixBoolean ();            // Maps blocks (rows) to added combo groups (columns)
        public List<String>        Gnames         = new ArrayList<String> ();        // Group name associated with each column of G
        public Map<String,Element> groups         = new TreeMap<String,Element> ();  // The elements associated with every segmentGroup
        // TODO: deal with ca2 variants

        public Cell (MPart source)
        {
            super (source);

            if (source.get ("$metadata", "backend.lems.part").equals ("segment"))  // This is a standalone segment, pretending to be a cell.
            {
                populationSize = 1;  // Since SegmentBlock also interprets $n, and that is the right place to do it in this case.
            }
        }

        public void append ()
        {
            cell = addElement ("cell", elements);
            standalone (source, cell, cellElements);
            cell.setAttribute ("id", id);

            // Collect Segments and transform them into distinct property sets.
            // This reverses the import process, which converts property sets into distinct segment populations.
            EquationSet part = getEquations (source);
            if (source.get ("$metadata", "backend.lems.part").equals ("segment"))  // segment pretending to be a cell (such as HH)
            {
                Map<String,SegmentBlock> blockNames = new TreeMap<String,SegmentBlock> ();  // for easy lookup by name
                SegmentBlock sb = new SegmentBlock (part);
                blocks.add (sb);
                blockNames.put (part.name, sb);
                segments.addAll (sb.segments);

                // Process peer Coupling parts into segment parent-child relationships
                for (EquationSet c : part.container.parts)
                {
                    if (c == part) continue;
                    if (c.connectionBindings == null  ||  c.connectionBindings.size () != 2) continue;
                    if (! c.source.get ("$inherit").contains ("Coupling")) continue;
                    if (! c.source.get ("A").equals (part.name)) continue;
                    if (! c.source.get ("B").equals (part.name)) continue;
                    Synapse s = addSynapse ((MPart) c.source, true);
                    s.usedInCell = true;
                    connectSegments (c, blockNames);
                }
            }
            else  // conventional cell
            {
                Map<String,SegmentBlock> blockNames = new TreeMap<String,SegmentBlock> ();  // for easy lookup by name
                for (EquationSet s : part.parts)
                {
                    if (! s.source.get ("$metadata", "backend.lems.part").equals ("segment")) continue;  // Generally, the only other component will be a "Coupling", which we must ignore.
                    SegmentBlock sb = new SegmentBlock (s);
                    blocks.add (sb);
                    blockNames.put (s.name, sb);
                    segments.addAll (sb.segments);
                }

                // Collect connections and convert them into parent-child relationships
                for (EquationSet c : part.parts)
                {
                    if (c.connectionBindings == null  ||  c.connectionBindings.size () != 2) continue;
                    if (! c.source.get ("$inherit").contains ("Coupling")) continue;
                    connectSegments (c, blockNames);
                }
            }

            // Assign segment IDs based on parent-child relationships
            int index = 0;
            for (Segment s : segments)
            {
                if (s.parent == null) index = s.assignID (index);
            }

            // Sort segments by ID, so they get appended in a pleasing manner.
            TreeMap<Integer,Segment> sorted = new TreeMap<Integer,Segment> ();
            for (Segment s : segments) sorted.put (s.id, s);
            int i = 0;
            for (Segment s : sorted.values ()) segments.set (i++, s);

            // Emit segments and groups
            for (SegmentBlock sb : blocks) sb.append ();
            for (Property p : properties) p.append ();
            for (Segment s : segments) s.append ();
            if (! morphology.isEmpty ())
            {
                Element e = addElement ("morphology", cellElements);
                e.setAttribute ("id", "morphology");
                sequencer.append (e, morphology);
            }

            // Assemble biophysical properties
            List<Element> biophysical = new ArrayList<Element> ();
            if (! membrane.isEmpty ())
            {
                Element e = addElement ("membraneProperties", biophysical);
                sequencer.append (e, membrane);
            }
            if (! intra.isEmpty ())
            {
                Element e = addElement ("intracellularProperties", biophysical);
                sequencer.append (e, intra);
            }
            if (! extra.isEmpty ())
            {
                Element e = addElement ("extracellularProperties", biophysical);
                sequencer.append (e, extra);
            }
            if (! biophysical.isEmpty ())
            {
                Element e = addElement ("biophysicalProperties", cellElements);
                e.setAttribute ("id", "properties");
                sequencer.append (e, biophysical);
            }

            // Collate
            sequencer.append (cell, cellElements);
        }

        public void connectSegments (EquationSet c, Map<String,SegmentBlock> blockNames)
        {
            SegmentBlock A = blockNames.get (c.source.get ("A"));  // parent
            SegmentBlock B = blockNames.get (c.source.get ("B"));  // child
            if (A == null  ||  B == null) return;  // This should never happen for imported models, but user-made models could be ill-formed.

            Variable p = c.find (new Variable ("$p", 0));
            if (p == null)
            {
                // Absent $p indicates all-to-all, which NeuroML can only represent if A is a singleton
                Segment a = A.segments.get (0);
                for (int Bindex = 0; Bindex < B.segments.size (); Bindex++)
                {
                    Segment b = B.segments.get (Bindex);
                    a.addChild (b);
                }
            }
            else
            {
                ConnectionContext cc = new ConnectionContext ();
                for (cc.Aindex = 0; cc.Aindex < A.segments.size (); cc.Aindex++)
                {
                    Segment a = A.segments.get (cc.Aindex);
                    for (cc.Bindex = 0; cc.Bindex < B.segments.size (); cc.Bindex++)
                    {
                        Segment b = B.segments.get (cc.Bindex);
                        if (((Scalar) p.eval (cc)).value == 1) a.addChild (b);
                    }
                }
            }
        }

        public String mapID (String blockName, String index)
        {
            for (SegmentBlock sb : blocks)
            {
                if (blockName.equals (sb.block.key ()))
                {
                    int count = sb.segments.size ();
                    int i = Integer.valueOf (index);
                    if (count == 1  ||  i >= count) return String.valueOf (sb.segments.get (0).id);
                    return String.valueOf (sb.segments.get (i).id);
                }
            }
            return "0";
        }

        public class Segment
        {
            public String        name;
            public Segment       parent;
            public List<Segment> children = new ArrayList<Segment> ();
            public int           id; // NeuroML id

            public double fractionAlong    = 1;
            public Matrix proximal         = new MatrixDense (3, 1);
            public Matrix distal           = new MatrixDense (3, 1);
            public double proximalDiameter = 0;
            public double distalDiameter   = 0;

            public String neuroLexID = "";

            public Segment (MNode source, int index)
            {
                name       = source.get ("$metadata", "backend.lems.id" + index);
                neuroLexID = source.get ("$metadata", "neuroLexID"      + index);
            }

            public void addChild (Segment child)
            {
                child.parent = this;
                children.add (child);
            }

            public int assignID (int nextID)
            {
                id = nextID++;
                for (Segment c : children) nextID = c.assignID (nextID);  // Depth-first assignment of IDs
                return nextID;
            }

            public void append ()
            {
                Element segment = addElement ("segment", morphology);
                segment.setAttribute ("id", String.valueOf (id));
                segment.setAttribute ("name", name);
                if (! neuroLexID.isEmpty ()) segment.setAttribute ("neuroLexId", neuroLexID);
                List<Element> segmentElements = new ArrayList<Element> ();

                double fractionAlong = 1;
                double parentDiameter = proximalDiameter;
                if (parent != null)
                {
                    Element p = addElement ("parent", segmentElements);
                    p.setAttribute ("segment", String.valueOf (parent.id));
                    fractionAlong = ((Matrix) proximal.subtract (parent.proximal)).norm (2) / ((Matrix) parent.distal.subtract (parent.proximal)).norm (2);
                    if (1 - fractionAlong > epsilon)
                    {
                        p.setAttribute ("fractionAlong", print (fractionAlong));
                    }
                    else
                    {
                        fractionAlong = 1;
                    }
                    parentDiameter = (parent.distalDiameter - parent.proximalDiameter) * fractionAlong + parent.proximalDiameter;
                }

                if (parent == null  ||  Math.abs (proximalDiameter - parentDiameter) > epsilon)
                {
                    Element p = addElement ("proximal", segmentElements);
                    // Convert all morphology to micrometers.
                    p.setAttribute ("x",        print (proximal.get (0) * 1e6));
                    p.setAttribute ("y",        print (proximal.get (1) * 1e6));
                    p.setAttribute ("z",        print (proximal.get (2) * 1e6));
                    p.setAttribute ("diameter", print (proximalDiameter * 1e6));
                }

                Element d = addElement ("distal", segmentElements);
                d.setAttribute ("x",        print (distal.get (0) * 1e6));
                d.setAttribute ("y",        print (distal.get (1) * 1e6));
                d.setAttribute ("z",        print (distal.get (2) * 1e6));
                d.setAttribute ("diameter", print (distalDiameter * 1e6));

                sequencer.append (segment, segmentElements);
            }
        }

        public class SegmentBlock
        {
            public EquationSet   part;
            public MNode         block;
            public int           row;  // in P.
            public List<Segment> segments   = new ArrayList<Segment> ();
            public List<String>  inhomo     = new ArrayList<String> ();

            public SegmentBlock (EquationSet part)
            {
                this.part = part;
                block     = part.source;
                row       = blocks.size ();  // We have not been added to blocks yet, so blocks.size() gives our (future) index.

                // Find inhomogeneousParameters
                for (MNode c : block)
                {
                    if (c.get ().contains ("pathLength")) inhomo.add (c.key ());
                }

                // Analyze
                Variable n         = part.find (new Variable ("$n", 0));
                Variable xyz0      = part.find (new Variable ("xyz0"));
                Variable xyz       = part.find (new Variable ("$xyz"));
                Variable diameter0 = part.find (new Variable ("diameter0"));
                Variable diameter  = part.find (new Variable ("diameter"));

                // Extract segments
                int count = 1;
                if (n != null) count = (int) Math.floor (((Scalar) n.eval (context)).value);
                for (int index = 0; index < count; index++)
                {
                    Segment s = new Segment (block, index);
                    segments.add (s);
                    if (s.name.isEmpty ())
                    {
                        if (count == 1) s.name = block.key ();
                        else            s.name = block.key () + index;
                    }

                    context.index = index;
                    if (xyz0      != null) s.proximal         =  (Matrix) xyz0     .eval (context);
                    if (xyz       != null) s.distal           =  (Matrix) xyz      .eval (context);
                    if (diameter0 != null) s.proximalDiameter = ((Scalar) diameter0.eval (context)).value;
                    if (diameter  != null) s.distalDiameter   = ((Scalar) diameter .eval (context)).value;
                }

                // Extract properties

                //   neuroLexId
                String neuroLexID = block.get ("$metadata", "neuroLexID");
                if (! neuroLexID.isEmpty ())
                {
                    for (String nlid : neuroLexID.split (",")) addUnique (new PropertyNeuroLexID (nlid));
                }

                //   initMembPotential
                String value = getLocalProperty ("V", block);
                int position = value.indexOf ("@$init");
                if (position >= 0)  // This also tests whether the string is empty.
                {
                    value = value.substring (0, position);
                    addUnique (new PropertyMembrane (membrane, "initMembPotential", value));
                }

                //   resistivity
                value = getLocalProperty ("ρ", block);
                if (! value.isEmpty ())
                {
                    addUnique (new PropertyMembrane (intra, "resistivity", value));
                }

                //   specificCapacitance
                value = getLocalProperty ("Cspecific", block);
                if (! value.isEmpty ())
                {
                    addUnique (new PropertyMembrane (membrane, "specificCapacitance", value));
                }

                //   spikeThresh
                value = getLocalProperty ("Vspike", block);
                if (! value.isEmpty ())
                {
                    addUnique (new PropertyMembrane (membrane, "spikeThresh", value));
                }

                //   channels and concentration models
                for (EquationSet p : part.parts)
                {
                    MPart ps = (MPart) p.source;
                    String type = ps.get ("$metadata", "backend.lems.part");
                    if (type.contains ("ConcentrationModel")) addUnique (new PropertyConcentration (ps));
                    else                                      addUnique (new PropertyChannel (ps));
                }
            }

            public String getLocalProperty (String name, MNode source)
            {
                MPart c = (MPart) source.child (name);
                if (c.isFromTopDocument ()) return c.get ();
                return "";
            }

            public void addUnique (Property p)
            {
                int index = properties.indexOf (p);
                if (index >= 0) p = properties.get (index);
                else            properties.add (p);
                P.set (row, p.column);
            }

            public void append ()
            {
                int size = segments.size ();
                if (size == 0) return;  // This could only happen if user explicitly sets $n=0, a very unusual situation.

                // Check if any property is a neuroLexID that belongs exclusively to this group
                PropertyNeuroLexID pnlid = null;
                int pnlidCount = 0;
                for (int c = 0; c < P.columns (); c++)
                {
                    if (! P.get (row, c)  ||  P.columnNorm0 (c) > 1) continue;
                    Property property = properties.get (c);
                    if (property instanceof PropertyNeuroLexID)
                    {
                        pnlid = (PropertyNeuroLexID) property;
                        pnlidCount++;
                    }
                }
                if (pnlidCount == 1  &&  size == 1  &&  segments.get (0).neuroLexID.isEmpty ()) pnlid = null;

                if (size == 1  &&  inhomo.size () == 0  &&  pnlid == null) return;

                Element group = addElement ("segmentGroup", morphology);
                String id = block.key ();
                group.setAttribute ("id", id);
                groups.put (id, group);
                if (pnlid != null)
                {
                    group.setAttribute ("neuroLexId", pnlid.value);
                    pnlid.done = true;
                }
                List<Element> groupElements = new ArrayList<Element> ();

                // Output inhomogeneousParameters
                for (String key : inhomo)
                {
                    MNode v = block.child (key);
                    Element parameter = addElement ("inhomogeneousParameter", groupElements);
                    parameter.setAttribute ("id", id + "_" + key);
                    parameter.setAttribute ("variable", key);
                    parameter.setAttribute ("metric", "Path Length from root");

                    String[] pieces = v.get ().split ("\\+");
                    if (pieces.length > 1)
                    {
                        Element proximal = doc.createElement ("proximal");
                        parameter.appendChild (proximal);
                        proximal.setAttribute ("translationStart", pieces[1]);
                    }
                    pieces = pieces[0].split ("\\*");
                    if (pieces.length > 1)
                    {
                        Element distal = doc.createElement ("distal");
                        parameter.appendChild (distal);
                        distal.setAttribute ("normalizationEnd", pieces[1]);
                    }
                }

                // Sort segments by ID, so we can find contiguous paths for compact output
                TreeMap<Integer,Segment> sorted = new TreeMap<Integer,Segment> ();
                for (Segment s : segments) sorted.put (s.id, s);
                int i = 0;
                for (Segment s : sorted.values ()) segments.set (i++, s);

                // Output
                int from = segments.get (0).id;
                int to   = from;
                for (Segment s : segments)
                {
                    if (s.id - to <= 1)
                    {
                        to = s.id;
                    }
                    else  // a break in the contiguous list of IDs
                    {
                        appendPath (from, to, groupElements);
                        from = to = s.id;
                    }
                }
                appendPath (from, to, groupElements);

                sequencer.append (group, groupElements);
            }

            public void appendPath (int from, int to, List<Element> groupElements)
            {
                int count = to - from + 1;
                if (count < 4)
                {
                    for (int i = from; i <= to; i++)
                    {
                        Element member = addElement ("member", groupElements);
                        member.setAttribute ("segment", String.valueOf (i));
                    }
                }
                else
                {
                    Element path = addElement ("path", groupElements);
                    Element f = doc.createElement ("from");
                    f.setAttribute ("from", String.valueOf (from));
                    path.appendChild (f);
                    Element t = doc.createElement ("to");
                    t.setAttribute ("to", String.valueOf (to));
                    path.appendChild (t);
                }
            }
        }

        public class Property
        {
            public int    column           = properties.size ();  // in P
            public String segmentName      = "";
            public String segmentGroupName = "";

            public void append ()
            {
            }

            public void generateSegmentGroup (boolean doAll)
            {
                // Count individual segments associated with this property
                int count = 0;
                SegmentBlock lastFound = null;
                boolean all = true;
                int rows = blocks.size ();
                for (int r = 0; r < rows; r++)
                {
                    if (P.get (r, column))
                    {
                        lastFound = blocks.get (r);
                        count += lastFound.segments.size ();
                    }
                    else
                    {
                        all = false;
                    }
                }
                // count will always be at least 1

                if (all  &&  ! doAll) return;  // Don't emit any kind of segment selection, as the default is all.
                if (count == 1)  // use individual segment reference
                {
                    Segment s = lastFound.segments.get (0);
                    segmentName = String.valueOf (s.id);
                }
                else if (lastFound.segments.size () == count)  // Single group associated with SegmentBlock
                {
                    segmentGroupName = lastFound.block.key ();
                }
                else  // Assemble multiple groups
                {
                    int index = G.matchColumn (P.column (column));
                    if (index >= 0)
                    {
                        segmentGroupName = Gnames.get (index);
                    }
                    else
                    {
                        index = G.columns ();
                        G.set (index, P.column (column));

                        segmentGroupName = "Group" + index++;
                        while (groups.containsKey (segmentGroupName)) segmentGroupName = "Group" + index++;
                        Gnames.add (segmentGroupName);

                        Element group = addElement ("segmentGroup", morphology);
                        group.setAttribute ("id", segmentGroupName);
                        groups.put (segmentGroupName, group);
                        for (int r = 0; r < rows; r++)
                        {
                            if (P.get (r, column))
                            {
                                SegmentBlock b = blocks.get (r);
                                String name = b.block.key ();

                                if (b.segments.size () == 1)
                                {
                                    Segment s = b.segments.get (0);
                                    Element member = doc.createElement ("member");
                                    member.setAttribute ("segment", String.valueOf (s.id));
                                    group.appendChild (member);
                                }
                                else
                                {
                                    Element include = doc.createElement ("include");
                                    include.setAttribute ("segmentGroup", name);
                                    group.appendChild (include);
                                }
                            }
                        }
                    }
                }
            }

            public void setSegment (Element e)
            {
                if      (! segmentName     .isEmpty ()) e.setAttribute ("segment",      segmentName);
                else if (! segmentGroupName.isEmpty ()) e.setAttribute ("segmentGroup", segmentGroupName);
            }
        }

        public class PropertyNeuroLexID extends Property
        {
            String  value;
            boolean done;  // Indicates if this property has already been appended

            public PropertyNeuroLexID (String value)
            {
                this.value = value;
            }

            public void append ()
            {
                if (done) return;
                done = true;  // Has no real effect, since done is about pre-emptive emission of property.

                generateSegmentGroup (true);
                if (! segmentName.isEmpty ())  // Single specific segment.
                {
                    int index = Integer.valueOf (segmentName);
                    Segment s = segments.get (index);
                    if (s.neuroLexID.isEmpty ())
                    {
                        s.neuroLexID = value;
                    }
                    else
                    {
                        int i = G.columns ();
                        segmentGroupName = segmentName + "_Group" + i++;
                        while (groups.containsKey (segmentGroupName)) segmentGroupName = segmentName + "_Group" + i++;

                        Element group = addElement ("segmentGroup", morphology);
                        group.setAttribute ("id", segmentGroupName);
                        group.setAttribute ("neuroLexId", value);
                        groups.put (segmentGroupName, group);
                    }
                }
                else if (! segmentGroupName.isEmpty ())
                {
                    Element group = groups.get (segmentGroupName);
                    group.setAttribute ("neuroLexId", value);
                }
            }

            public boolean equals (Object o)
            {
                if (! (o instanceof PropertyNeuroLexID)) return false;
                PropertyNeuroLexID that = (PropertyNeuroLexID) o;
                return that.value.equals (value);
            }
        }

        public class PropertyMembrane extends Property
        {
            String        name;
            String        value;
            List<Element> target;

            public PropertyMembrane (List<Element> target, String name, String value)
            {
                this.target = target;
                this.name   = name;
                this.value  = value;
            }

            public void append ()
            {
                Element e = addElement (name, target);
                e.setAttribute ("value", biophysicalUnits (value));
                if (target == membrane)
                {
                    generateSegmentGroup (false);
                    setSegment (e);
                }
            }

            public boolean equals (Object that)
            {
                if (! (that instanceof PropertyMembrane)) return false;
                PropertyMembrane pm = (PropertyMembrane) that;
                return pm.target == target  &&  pm.name.equals (name)  &&  pm.value.equals (value);
            }
        }

        public class PropertyChannel extends Property
        {
            MPart source;
            boolean ca2;

            public PropertyChannel (MPart source)
            {
                this.source = source;
                ca2         = source.get ("c").equals ("ca2");
            }

            public void append ()
            {
                // Check for variableParameters
                List<String> skipList = new ArrayList<String> ();
                Map<String,String> variableParameters = new TreeMap<String,String> ();
                class ParameterVisitor extends Visitor
                {
                    List<String> inhomo = new ArrayList<String> ();
                    String found;
                    public ParameterVisitor ()
                    {
                        int rows = blocks.size ();
                        for (int r = 0; r < rows; r++)
                        {
                            if (P.get (r, column))
                            {
                                inhomo.addAll (blocks.get (r).inhomo);
                            }
                        }
                    }
                    public boolean visit (Operator op)
                    {
                        if (op instanceof AccessVariable)
                        {
                            AccessVariable av = (AccessVariable) op;
                            if (inhomo.contains (av.name)) found = av.name;
                            return false;
                        }
                        return true;
                    }
                }
                ParameterVisitor visitor = new ParameterVisitor ();
                if (visitor.inhomo.size () > 0)
                {
                    for (MNode c : source)
                    {
                        MPart p = (MPart) c;
                        if (! p.isFromTopDocument ()) continue;
                        try
                        {
                            Operator op = Operator.parse (p.get ());
                            visitor.found = "";
                            op.visit (visitor);
                            if (! visitor.found.isEmpty ())
                            {
                                String key = p.key ();
                                skipList.add (key);
                                variableParameters.put (key, visitor.found);
                            }
                        }
                        catch (ParseException e) {}
                    }
                }
                boolean nonuniform = skipList.size () > 0;

                IonChannel ic = addChannel (source, nonuniform, ca2);
                skipList.addAll (ic.skipList);
                Element channel = ic.appendWrapper (membrane, skipList);

                generateSegmentGroup (false);
                if (nonuniform)
                {
                    for (Entry<String,String> e : variableParameters.entrySet ())
                    {
                        String key = e.getKey ();
                        MNode v = source.child (key);
                        String parameter = key;
                        String fieldNames = v.get ("$metadata", "backend.lems.param");
                        if (! fieldNames.isEmpty ()) parameter = sequencer.bestFieldName (channel, fieldNames);

                        Element vp = doc.createElement ("variableParameter");
                        channel.appendChild (vp);
                        vp.setAttribute ("segmentGroup", segmentGroupName);
                        vp.setAttribute ("parameter", parameter);

                        Element value = doc.createElement ("inhomogeneousValue");
                        vp.appendChild (value);
                        value.setAttribute ("inhomogeneousParameter", segmentGroupName + "_" + e.getValue ());  // This is imperfect, as there is no guarantee that this segment group is not constructed.
                        value.setAttribute ("value", v.get ());
                    }
                }
                else
                {
                    setSegment (channel);
                }
            }

            public boolean equals (Object that)
            {
                if (! (that instanceof PropertyChannel)) return false;
                return source.equals (((PropertyChannel) that).source);  // deep comparison of MNodes
            }
        }

        public class PropertyConcentration extends Property
        {
            MPart source;

            public PropertyConcentration (MPart source)
            {
                this.source = source;
            }

            public void append ()
            {
                String  ion      = source.key ();  // Concentration models are always named after their ion.
                String  type     = source.get ("$metadata", "backend.lems.part").split (",")[0];
                String  inherit  = source.get ("$inherit").replace ("\"", "");
                NameMap nameMap  = partMap.exportMap (inherit);
                String  inside0  = nameMap.importName ("initialConcentration");
                String  outside0 = nameMap.importName ("initialExtConcentration");

                Element concentration = addElement (type, elements);
                String concentrationID = source.get ("$metadata", "backend.lems.id");
                if (concentrationID.isEmpty ()) concentrationID = "N2A_Concentration" + countConcentration++;
                concentration.setAttribute ("id",  concentrationID);
                concentration.setAttribute ("ion", ion);
                genericPart (source, concentration, inside0, outside0);

                Element species = addElement ("species", intra);
                species.setAttribute ("id",                      ion);
                species.setAttribute ("ion",                     ion);
                species.setAttribute ("concentrationModel",      concentrationID);
                species.setAttribute ("initialConcentration",    biophysicalUnits (source.get (inside0)));
                species.setAttribute ("initialExtConcentration", biophysicalUnits (source.get (outside0)));
            }

            public boolean equals (Object that)
            {
                if (! (that instanceof PropertyConcentration)) return false;
                return source.equals (((PropertyConcentration) that).source);
            }
        }
    }

    public IonChannel addChannel (MPart source, boolean nonuniform, boolean ca2)
    {
        IonChannel result = new IonChannel (source);
        int index = channels.indexOf (result);
        if (index >= 0) return channels.get (index);
        channels.add (result);
        result.determineType (nonuniform, ca2);
        result.append ();
        return result;
    }

    public class IonChannel
    {
        public String        id;
        public MPart         source;    // from original document
        public MNode         base;      // pseudo-document with modifications to factor out changes made by Segment
        public String        inherit;   // model name of parent channel, without the Potential
        public String        potential; // model name for the Potential, if specified. null if no Potential is given.
        public String        type;
        public List<Element> channelElements = new ArrayList<Element> ();
        public List<String>  skipList        = new ArrayList<String> ();  // A hint to wrapper element about which attributes it should skip.

        public IonChannel (MPart source)
        {
            this.source = source;

            String[] inherits = source.get ("$inherit").split (",");
            if (inherits.length > 2  ||  inherits.length == 0) throw new RuntimeException ("Channel $inherit has unexpected form");
            if (inherits.length == 1)
            {
                inherit = inherits[0].replace ("\"", "");
            }
            else
            {
                potential = inherits[0].replace ("\"", "");
                inherit   = inherits[1].replace ("\"", "");
            }

            // Assemble a part that best recreates the underlying channel before it got incorporated into a property
            base = new MVolatile ();
            base.set ("$inherit", inherit);  // skip potential, if it existed
            List<String> forbidden = Arrays.asList ("$inherit", "c", "E", "Gall", "Gdensity", "population", "permeability");
            skipList.add ("Gall");  // "Gall" should not appear in either wrapper or ion channel.
            for (MNode c : source)
            {
                String key = c.key ();
                if (forbidden.contains (key)) continue;
                base.set (key, c);
                skipList.add (key);
            }

            // Suggest an id
            if (inherit.startsWith (modelName))
            {
                id = inherit.substring (modelName.length ()).trim ();
            }
            else
            {
                id = source.get ("$metadata", "backend.lems.id");
                if (id.isEmpty ()) id = "N2A_Channel" + channels.size ();
            }
        }

        public void determineType (boolean nonuniform, boolean ca2)
        {
            type = "channelPopulation";
            if (! source.get ("Gall").contains ("population"))
            {
                String potential = findPotential (source);
                if (potential.startsWith ("Potential "))
                {
                    potential = potential.substring (10);
                    switch (potential)
                    {
                        case "Nernst":
                            if (nonuniform) type = "channelDensityNonUniformNernst";
                            else            type = "channelDensityNernst";
                            if (ca2) type += "Ca2";
                            break;
                        case "GHK":
                            if (nonuniform) type = "channelDensityNonUniformGHK";
                            else            type = "channelDensityGHK";
                            break;
                        case "GHK 2":
                            type = "channelDensityGHK2";
                            break;
                    }
                }
                else  // potential is empty
                {
                    if (nonuniform) type = "channelDensityNonUniform";
                    else            type = "channelDensity";
                }
            }
        }

        public String findPotential (MNode source)
        {
            String[] parents = source.get ("$inherit").split (",");
            for (String p : parents)
            {
                p = p.replace ("\"", "");
                if (p.startsWith ("Potential ")) return p;
                MNode c = AppData.models.child (p);
                if (c != null)
                {
                    String result = findPotential (c);
                    if (! result.isEmpty ()) return result;
                }
            }
            return "";
        }

        public Element appendWrapper (List<Element> containerElements, List<String> skipList)
        {
            Element result = addElement (type, containerElements);
            genericPart (source, result, skipList);
            result.setAttribute ("id", source.key ());
            result.setAttribute ("ionChannel", id);
            String ion = source.get ("$metadata", "species");
            if (! ion.isEmpty ()) result.setAttribute ("ion", ion);
            return result;
        }

        public void append ()
        {
            // If we contain a kinetic scheme, type is "ionChannelKS". Otherwise it is simply "ionChannel".
            String type = "ionChannel";  // prefer over "ionChannelHH", because NeuroML_v2beta4.xsd hints that it is deprecated
            for (MNode c : source)
            {
                if (c.get ("$inherit").contains ("Kinetic"))
                {
                    type = "ionChannelKS";
                    break;
                }
            }

            Element channel = addElement (type, elements);
            channel.setAttribute ("id", id);

            // Attributes
            if (inherit.contains ("Passive")) channel.setAttribute ("type", "ionChannelPassive");
            String G1 = source.get ("G1");
            if (! G1.isEmpty ()) channel.setAttribute ("conductance", biophysicalUnits (G1));
            MPart species = (MPart) source.child ("c");
            if (species == null)
            {
                String s = source.get ("$metadata", "species");
                if (! s.isEmpty ()) channel.setAttribute ("species", s);
            }
            else
            {
                if (species.isFromTopDocument ()) channel.setAttribute ("species", species.get ());
            }

            // Subparts
            for (MNode c : source)
            {
                MPart p = (MPart) c;
                if (! p.isPart ()) continue;
                type = p.get ("$metadata", "backend.lems.part");
                if      (type.contains ("Q10"))  q10 (p, "q10ConductanceScaling", channelElements);
                else if (type.contains ("gate")) gate (p, channelElements);
                else                             genericPart (p, channelElements);
            }
            sequencer.append (channel, channelElements);
        }

        public void q10 (MPart part, String type, List<Element> parentElements)
        {
            // Trap fixedQ10
            String subtype = "q10ExpTemp";
            NameMap nameMap = partMap.exportMap (part);
            String name = nameMap.importName ("fixedQ10");
            String fixedQ10 = part.get (name);
            if (! fixedQ10.equals ("*scaling")) subtype = "q10Fixed";  // Hack to check for override, which almost certainly is a constant.

            Element q10 = addElement (type, parentElements);
            if (! type.equals ("q10ConductanceScaling")) q10.setAttribute ("type", subtype);
            genericPart (part, q10);
        }

        public static final int alpha      = 0x1;  // forward
        public static final int beta       = 0x2;  // reverse
        public static final int tau        = 0x4;
        public static final int inf        = 0x8;
        public static final int kinetic    = 0x10;
        public static final int fractional = 0x20;

        public void gate (MPart part, List<Element> parentElements)
        {
            // Rather than depend on backend.lems.part value, we directly determine the gate type.
            // This allows fewer distinct parts in the base repo.
            int flags = 0;
            Map<String,Integer> typeMap = new HashMap<String,Integer> ();
            flags |= check (part, "α",         alpha, typeMap);
            flags |= check (part, "β",         beta,  typeMap);
            flags |= check (part, "τUnscaled", tau,   typeMap);
            flags |= check (part, "inf",       inf,   typeMap);
            flags |= check (part, "q",         inf,   typeMap);

            List<Element> gateElements = new ArrayList<Element> ();
            flags |= checkTau (part, "τUnscaled", gateElements);
            for (MNode c : part)
            {
                MPart p = (MPart) c;
                if (! p.isPart ()) continue;

                String type = p.get ("$metadata", "backend.lems.part");
                if (type.contains ("Q10"))
                {
                    q10 (p, "q10Settings", gateElements);
                }
                else if (type.contains ("State"))
                {
                    kineticState (p, gateElements);
                    flags = kinetic;
                }
                else if (type.contains ("Transition"))
                {
                    kineticTransition (p, gateElements);
                    flags = kinetic;
                }
                else if (type.contains ("gate"))
                {
                    gate (p, gateElements);
                    flags = fractional;
                }
                else
                {
                    rate (p, typeMap, gateElements);
                }
            }

            MPart notes = (MPart) part.child ("$metadata", "notes");
            if (notes == null  ||  ! notes.isFromTopDocument ())
            {
                notes = (MPart) part.child ("$metadata", "note");
            }
            if (notes != null  &&  notes.isFromTopDocument ())
            {
                Element n = addElement ("notes", gateElements);
                n.setTextContent (notes.get ());
            }

            String type;
            if (parentElements == channelElements)
            {
                type = "gateHHrates";  // default if no combination of rates match
                switch (flags)
                {
                    case alpha | beta             : type = "gateHHrates";         break;
                    case alpha | beta | tau       : type = "gateHHratesTau";      break;
                    case alpha | beta       | inf : type = "gateHHratesInf";      break;
                    case alpha | beta | tau | inf : type = "gateHHratesTauInf";   break;
                    case                tau | inf : type = "gateHHtauInf";        break;
                    case                      inf : type = "gateHHInstantaneous"; break;
                }
                if ((flags & kinetic)    > 0) type = "gateKS";
                if ((flags & fractional) > 0) type = "gateFractional";
            }
            else
            {
                type = "gateSub";
            }
            Element gate = addElement (type, parentElements);
            gate.setAttribute ("id", part.key ());
            for (String a : new String[] {"instances", "fractionalConductance"})
            {
                String v = part.get (a);
                if (! v.isEmpty ()) gate.setAttribute (a, biophysicalUnits (v));
            }
            sequencer.append (gate, gateElements);
        }

        public int check (MPart part, String variableName, int flag, Map<String,Integer> typeMap)
        {
            String value = new Variable.ParsedValue (part.get (variableName)).expression;
            if (! value.endsWith (".x")) return 0;
            value = value.substring (0, value.length () - 2);
            typeMap.put (value, flag);
            return flag;
        }

        // Check for tau with constant value.
        public int checkTau (MPart part, String variable, List<Element> gateElements)
        {
            MNode c = part.child (variable);
            if (c == null) return 0;
            String value = new Variable.ParsedValue (c.get ()).expression;
            if (! value.endsWith (".x"))  // Does not reference an HHVariable
            {
                try
                {
                    Operator op = Operator.parse (value);
                    if (op instanceof Constant)
                    {
                        Element t = addElement ("timeCourse", gateElements);
                        t.setAttribute ("type", "HHTime");
                        t.setAttribute ("tau", biophysicalUnits (value));
                        return tau;
                    }
                }
                catch (ParseException e) {}
            }
            return 0;
        }

        public void rate (MPart part, Map<String,Integer> typeMap, List<Element> gateElements)
        {
            String name = part.key ();
            Integer i = typeMap.get (name);
            if (i == null) i = 0;
            switch (i)
            {
                case alpha: name = "forwardRate"; break;
                case beta : name = "reverseRate"; break;
                case tau  : name = "timeCourse";  break;
                case inf  : name = "steadyState"; break;
            }

            String type = part.get ("$metadata", "backend.lems.name");
            if (type.isEmpty ())
            {
                type = "unknown";
                String[] types = part.get ("$metadata", "backend.lems.part").split (",");
                String search = "Variable";
                if (name.contains ("Rate")) search = "Rate";
                for (String t : types)
                {
                    if (t.contains (search))
                    {
                        type = t;
                        break;
                    }
                }
            }

            Element r = addElement (name, gateElements);
            r.setAttribute ("type", type);
            genericPart (part, r);
        }

        public void kineticState (MPart part, List<Element> parentElements)
        {
            String type = "openState";
            if (part.get ("relativeConductance").equals ("0")) type = "closedState";
            Element state = addElement (type, parentElements);
            state.setAttribute ("id", part.key ());
        }

        public void kineticTransition (MPart part, List<Element> parentElements)
        {
            int flags = 0;
            Map<String,Integer> typeMap = new HashMap<String,Integer> ();
            flags |= check (part, "forward", alpha, typeMap);
            flags |= check (part, "reverse", beta,  typeMap);
            flags |= check (part, "τ",       tau,   typeMap);
            flags |= check (part, "inf",     inf,   typeMap);

            List<Element> transitionElements = new ArrayList<Element> ();
            flags |= checkTau (part, "τ", transitionElements);
            for (MNode c : part)
            {
                MPart p = (MPart) c;
                if (p.isPart ()) rate (p, typeMap, transitionElements);
            }

            String type = part.get ("$metadata", "backend.lems.part");  // allows for vHalfTransition
            switch (flags)
            {
                case alpha     : type = "forwardTransition"; break;
                case beta      : type = "reverseTransition"; break;
                case tau | inf : type = "tauInfTransition";  break;
            }

            Element transition = addElement (type, parentElements);
            transition.setAttribute ("id",   part.key ());
            transition.setAttribute ("from", part.get ("from"));
            transition.setAttribute ("to",   part.get ("to"));
            sequencer.append (transition, transitionElements);
        }

        public boolean equals (Object that)
        {
            if (! (that instanceof IonChannel)) return false;
            IonChannel ic = (IonChannel) that;
            return ic.base.equals (base);  // deep compare
        }
    }

    public void addComponentType (MNode source, MNode base)
    {
        ComponentType ct = new ComponentType (source, base);
        if (! componentTypes.contains (ct))
        {
            componentTypes.add (ct);
            ct.append ();
            addComponentTypeDependencies (source);
        }
    }

    /**
        Add all component types that source depends on.
    **/
    public void addComponentTypeDependencies (MNode source)
    {
        String[] inherits = source.get ("$inherit").split (",");
        for (String inherit : inherits)
        {
            inherit = inherit.replace ("\"", "");
            MNode part = AppData.models.child (inherit);
            if (part == null) continue;
            if (! part.get ("$metadata", "backend.lems.part").isEmpty ()) continue;  // Don't add base parts.
            addComponentType (part, part);
        }
        // TODO: add children and other dependencies
    }

    public class ComponentType
    {
        public String name;
        public MNode  source;
        public MNode  base;  // for comparisons

        public Map<String,String> rename;

        public ComponentType (MNode source, MNode base)
        {
            this (source.key (), source, base);
        }

        public ComponentType (String name, MNode source, MNode base)
        {
            if (name.startsWith (modelName)) name = name.substring (modelName.length ()).trim ();
            if (name.isEmpty ())             name = source.get ("$metadata", "backend.lems.name");
            if (name.isEmpty ())             name = "N2A_ComponentType" + componentTypes.size ();
            this.name = name;

            this.source = source;
            this.base   = base;
        }

        public void append ()
        {
            List<Element> componentTypeElements = new ArrayList<Element> ();
            List<Element> dynamicsElements      = new ArrayList<Element> ();

            // Assemble a working EquationSet
            EquationSet equations;
            if (source instanceof MPart)
            {
                equations = getEquations ((MPart) source);
            }
            else  // source is from db, so MPersistent
            {
                try
                {
                    equations = new EquationSet ((MPersistent) source);
                    makeExecutable (equations);
                }
                catch (Exception e)
                {
                    equations = new EquationSet ("");
                }
            }

            // Declarations from $metadata
            String extension = base.get ("$metadata", "backend.lems.extends");
            if (extension.isEmpty ()) extension = base.get ("$metadata", "backend.lems.part").split (",")[0];

            String description = base.get ("$metadata", "description");
            if (description.isEmpty ()) description = base.get ("$metadata", "notes");

            MNode metadata = base.child ("$metadata");
            if (metadata != null)
            {
                String prefix = "backend.lems.";
                int prefixLength = prefix.length ();
                for (MNode m : metadata)
                {
                    String key = m.key ();
                    if (! key.startsWith (prefix)) continue;
                    key = key.substring (prefixLength);
                    String value = m.get ();

                    if (key.startsWith ("children."))
                    {
                        key = key.substring (9);
                        String[] types = value.split (",");
                        String type;
                        if (types.length > 1) type = types[1];
                        else                  type = partMap.exportName (types[0]);
                        Element children = addElement ("Children", componentTypeElements);
                        children.setAttribute ("name", key);
                        children.setAttribute ("type", type);
                    }
                }
            }

            // Build name mapping
            rename = new HashMap<String,String> ();
            if (! extension.isEmpty ())
            {
                NameMap nameMap = partMap.importMap (extension);
                for (Entry<String,ArrayList<String>> e : nameMap.outward.entrySet ())
                {
                    String key = e.getKey ();
                    ArrayList<String> outNames = e.getValue ();
                    if (outNames.size () == 1) rename.put (key, outNames.get (0));
                    else
                    {
                        // TODO: Handle special cases where we care about choice of output variable based on context.
                        rename.put (key, outNames.get (0));
                    }
                }
            }

            // Variables
            for (MNode m : source)  // source, not base, because we want to include parameters which were excluded from comparison.
            {
                String key = m.key ();
                if (key.startsWith ("$")) continue;  // Should only be $inherit and $metadata
                if (MPart.isPart (m)) continue;  // There shouldn't be any of these.

                // Eliminate non-local items
                if (m instanceof MPart)  // Only applies to embedded LEMS
                {
                    MPart p = (MPart) m;
                    if (! p.isFromTopDocument ()  &&  p.getSource ().getParent () instanceof MDoc) continue;
                }

                String value = m.get ().trim ();

                Element element = null;
                Variable v = equations.find (Variable.fromLHS (key));
                boolean constant = v != null  &&  v.hasAttribute ("constant");
                boolean parameter = m.child ("$metadata", "param") != null;
                Unit<?> unit = null;
                if (v != null) unit = v.unit;
                if (constant  ||  parameter)
                {
                    UnitValue uv = new UnitValue (value);
                    if (unit == null) unit = uv.unit;

                    // TODO: Evaluate constant expressions, such as simple references to other variables.
                    String type;
                    if (parameter) type = uv.value == 0 ? "Parameter" : "Property";
                    else           type = "Constant";

                    element = addElement (type, componentTypeElements);
                    if (uv.value != 0)
                    {
                        if (constant) element.setAttribute ("value",        uv.print ());
                        else          element.setAttribute ("defaultValue", uv.print ());
                    }
                }
                else if (value.startsWith (":"))
                {
                    element = addElement ("DerivedVariable", dynamicsElements);
                }

                if (element != null)
                {
                    element.setAttribute ("name", key);

                    if (unit != null) element.setAttribute ("dimension", printDimension (unit));

                    String vdescription = m.get ("$metadata", "desription");
                    if (vdescription.isEmpty ()) vdescription = m.get ("$metadata", "notes");
                    if (! vdescription.isEmpty ()) element.setAttribute ("description", vdescription);
                }
            }

            if (! dynamicsElements.isEmpty ())
            {
                Element dynamics = addElement ("Dynamics", componentTypeElements);
                sequencer.append (dynamics, dynamicsElements);
            }

            Element componentType = addElement ("ComponentType", elements);
            componentType.setAttribute ("name", name);
            if (! extension.isEmpty ()) componentType.setAttribute ("extends", extension);
            if (! description.isEmpty ()) componentType.setAttribute ("description", description);
            sequencer.append (componentType, componentTypeElements);
        }

        public class RendererLEMS extends Renderer
        {
            public boolean render (Operator op)
            {
                if (op instanceof Comparison)
                {
                    Comparison c = (Comparison) op;
                    String                     middle = " .eq. ";
                    if      (op instanceof NE) middle = " .ne. ";
                    else if (op instanceof GT) middle = " .gt. ";
                    else if (op instanceof LT) middle = " .lt. ";
                    else if (op instanceof GE) middle = " .ge. ";
                    else if (op instanceof LE) middle = " .le. ";
                    c.render (this, middle);
                    return true;
                }
                if (op instanceof AccessVariable)
                {
                    AccessVariable av = (AccessVariable) op;
                    String name = rename.get (av.name);
                    if (name == null) name = av.name;
                    result.append (name);
                    return true;
                }
                if (op instanceof Uniform)
                {
                    Uniform u = (Uniform) op;
                    result.append ("random(");
                    u.operands[0].render (this);
                    result.append (")");
                    return true;
                }
                return false;
            }
        }

        public boolean equals (Object that)
        {
            if (! (that instanceof ComponentType)) return false;
            ComponentType ct = (ComponentType) that;
            return ct.base.equals (base);
        }
    }

    /**
        Handles all Components, whether or not they are NML-defined types.
    **/
    public Element genericPart (MPart part, List<Element> elements)
    {
        String id   = part.key ();
        String type = part.get ("$metadata", "backend.lems.part").split (",")[0];  // first part should be preferred output type
        if (type.isEmpty ()  ||  ! sequencer.hasID (type))
        {
            type = id;
            id = "";
        }
        Element e = addElement (type, elements);
        if (! id.isEmpty ()) e.setAttribute ("id", id);
        genericPart (part, e);
        if (! e.hasAttributes ()  &&  ! e.hasChildNodes ()) elements.remove (e);  // For aesthetic reasons, don't insert empty elements.
        return e;
    }

    public void genericPart (MPart part, Element result, String... skip)
    {
        genericPart (part, result, Arrays.asList (skip));
    }

    public void genericPart (MPart part, Element result, List<String> skipList)
    {
        EquationSet partEquations = getEquations (part);
        List<Element> resultElements = new ArrayList<Element> ();

        MNode componentType = new MVolatile ();
        boolean needsComponentType = false;
        boolean inheritedOnly = true;

        for (MNode c : part)
        {
            MPart p = (MPart) c;

            String key = p.key ();
            if (partEquations.findConnection (key) != null) continue;  // Skip connection bindings. They are unpacked elsewhere.
            if (skipList.contains (key)) continue;
            if (key.startsWith ("$"))
            {
                // Store special values in componentType, in case we need to emit it.
                if (key.equals ("$inherit"))
                {
                    componentType.set (key, p);
                }
                else if (key.equals ("$metadata"))
                {
                    String prefix = "backend.lems.";
                    int prefixLength = prefix.length ();
                    for (MNode m : p)
                    {
                        String mkey = m.key ();
                        if (! mkey.startsWith (prefix)) continue;
                        String suffix = mkey.substring (prefixLength);
                        String value = m.get ();

                        if (suffix.equals ("id")) continue;
                        if (suffix.startsWith ("children"))
                        {
                            MPart mp = (MPart) m;
                            if (! mp.isFromTopDocument ())
                            {
                                // As explained below, we always emit an embedded LEMS type.
                                // We also emit types from the database if they are on an inheritance path
                                // between the base part and the embedded part. However, those are selected elsewhere.
                                MNode parent = mp.getSource ().getParent ();  // get source $metadata node
                                if (! parent.get ("backend.lems.part").isEmpty ()) continue;  // base part, which we never emit
                                if (((MPersistent) parent).getParent () instanceof MDoc) continue;  // not embedded
                            }
                        }
                        componentType.set ("$metadata", mkey, value);
                    }
                }

                continue;
            }

            MNode parent = p.getSource ().getParent ();
            boolean overridden = p.isFromTopDocument ()  ||  parent.get ("$metadata", "backend.lems.part").isEmpty ();
            if (p.isPart ())
            {
                if (! overridden) continue;
                if (p.get ("$metadata", "backend.lems.part").contains ("ionChannel"))
                {
                    IonChannel ic = addChannel (p, false, false);
                    ic.appendWrapper (resultElements, ic.skipList);
                }
                else
                {
                    genericPart (p, resultElements);
                }
            }
            else
            {
                // We need to check two things:
                // * Has the variable been overridden after it was declared in the base part?
                // * Is it an expression or a constant?
                // A constant that is either overridden or required should be emitted here.
                // An override that is an expression should trigger a LEMS extension part.

                Variable v = partEquations.find (Variable.fromLHS (key));
                boolean constant = v != null  &&  v.hasAttribute ("constant");  // v could be null if it is revoked (MPart still contains the variable, but EquationSet eliminates it).

                String name = sequencer.bestFieldName (result, p.get ("$metadata", "backend.lems.param"));
                if (name.isEmpty ()) name = key;
                boolean required = sequencer.isRequired (result, name);

                boolean param = p.child ("$metadata", "param") != null;

                if (required  ||  (overridden  &&  constant  &&  param))
                {
                    String value = p.get ();
                    if (constant)
                    {
                        try
                        {
                            Operator op = v.equations.first ().expression;  // Simplified expression
                            if (! (op instanceof Constant))  // This is a more complicated expression, so render the equivalent constant.
                            {
                                value = op.eval (context).toString ();
                            }
                        }
                        catch (Exception e) {}
                    }
                    result.setAttribute (name, biophysicalUnits (value));  // biophysicalUnits() should return strings (non-numbers) unmodified
                }
                if (overridden  &&  (! constant  ||  ! param)  &&  ! v.hasAttribute ("dummy"))
                {
                    needsComponentType = true;  // Only an actual equation creates the need to emit a ComponentType.
                    // Emit an embedded LEMS type separately from its parent, regardless of whether
                    // it is defined locally or in some other part we inherit from.
                    if (p.isFromTopDocument ()  ||  ! (parent instanceof MDoc))
                    {
                        inheritedOnly = false;
                        componentType.set (key, p);
                    }
                }

                // TODO: Scan for output() calls
            }
        }
        sequencer.append (result, resultElements);

        if (needsComponentType)
        {
            if (inheritedOnly) addComponentTypeDependencies (part);
            else               addComponentType             (part, componentType);
        }
    }

    public void standalone (MPart source, Element node)
    {
        List<Element> standaloneElements = new ArrayList<Element> ();
        standalone (source, node, standaloneElements);
        sequencer.append (node, standaloneElements);
    }

    /**
        Process child elements common to all Standalone elements.
    **/
    public void standalone (MPart source, Element node, List<Element> elements)
    {
        MNode metadata = source.child ("$metadata");
        if (metadata != null)
        {
            for (MNode m : metadata)
            {
                if (! ((MPart) m).isFromTopDocument ()) continue;
                String key = m.key ();
                if (key.startsWith ("backend")) continue;
                if (key.startsWith ("gui")) continue;
                if (key.equals ("id")) continue;  // our internal id, not NeuroML id
                switch (key)
                {
                    case "description":
                        node.setAttribute ("description", m.get ());
                        break;
                    case "note":
                    case "notes":
                        Element notes = addElement ("notes", elements);
                        notes.setTextContent (m.get ());
                        break;
                    default:
                        Element property = addElement ("property", elements);
                        property.setAttribute ("tag",   key);
                        property.setAttribute ("value", m.get ());
                }
            }
        }

        MNode reference = source.child ("$reference");
        if (reference != null)
        {
            // TODO: emit annotation elements
        }
    }

    /**
        Make eqset minimally executable.
    **/
    public static void makeExecutable (EquationSet equations)
    {
        try
        {
            equations.resolveConnectionBindings ();
        }
        catch (Exception e) {}  // Still try to finish rest of compilation. Maybe only one or two minor parts were affected.
        try
        {
            equations.addGlobalConstants ();
            equations.addSpecials ();  // $connect, $index, $init, $n, $t, $t', $type
            equations.fillIntegratedVariables ();
            equations.findIntegrated ();
            equations.resolveLHS ();
            equations.resolveRHS ();
            equations.findConstants ();  // This could hurt the analysis. It simplifies expressions and substitutes constants, breaking some dependency chains.
            equations.determineTraceVariableName ();
            equations.determineTypes ();
            equations.determineUnits ();
            equations.clearVariables ();
        }
        catch (Exception e) {}  // It may still be possible to complete the export.
    }

    public EquationSet getEquations (MPart p)
    {
        List<String> path = new ArrayList<String> ();
        MPart parent = p;
        while (parent != null)
        {
            path.add (parent.key ());
            parent = parent.getParent ();
        }

        EquationSet result = equations;
        for (int i = path.size () - 2; i >= 0; i--)
        {
            String name = path.get (i);
            result = result.findPart (name);
            if (result == null) return null;
            if (! result.name.equals (name)) return null;
        }
        return result;
    }

    public Element addElement (String name, List<Element> elements)
    {
        Element result = doc.createElement (name);
        elements.add (result);
        return result;
    }

    /**
        Returns the first, but not necessarily only, element with the given name.
        If such an element doesn't exist, adds one and returns it.
    **/
    public Element getElement (String name, List<Element> elements)
    {
        for (Element e : elements)
        {
            if (e.getNodeName ().equals (name)) return e;
        }
        Element result = doc.createElement (name);
        elements.add (result);
        return result;
    }

    public String morphologyUnits (String value)
    {
        value = value.trim ();
        int unitIndex = findUnits (value);
        if (unitIndex == 0) return "0" + value;  // no number
        if (unitIndex >= value.length ()) return value;  // no unit

        String unitString = value.substring (unitIndex).trim ();
        value             = value.substring (0, unitIndex);
        Unit<?> unit = UCUM.parse (unitString);

        double v = 0;
        try
        {
            v = Double.valueOf (value);
            UnitConverter converter = unit.getConverterToAny (um);
            v = converter.convert (v);
        }
        catch (Exception error)
        {
        }
        unitsUsed.put (um, "um");

        return print (v);  // no unit string at end of number, because morphology units are always in um
    }

    public String biophysicalUnits (String value)
    {
        UnitValue uv = new UnitValue (value);
        if (uv.unit == null) return value;
        return uv.print ();
    }

    public class UnitValue
    {
        public double value;
        public Unit<?> unit;
        public String nml;  // NeuroML declared name for this unit.

        public UnitValue (String source)
        {
            source = source.trim ();
            int unitIndex = findUnits (source);
            if (unitIndex == 0  ||  unitIndex >= source.length ()) return;  // no number or no units, so probably something else

            String unitString   = source.substring (unitIndex).trim ();
            String numberString = source.substring (0, unitIndex);

            try
            {
                unit = UCUM.parse (unitString);
            }
            catch (Exception e)
            {
                return;
            }

            try
            {
                value = Double.valueOf (numberString);
            }
            catch (NumberFormatException error)
            {
                return;
            }

            // Determine power in numberString itself
            double power = 1;
            if (value != 0) power = Math.pow (10, Math.floor ((Math.getExponent (value) + 1) / baseRatio));

            // Find closest matching unit
            Unit<?> closest      = null;
            Unit<?> closestAbove = null;  // like closest, but only ratios >= 1
            double  closestRatio      = Double.POSITIVE_INFINITY;
            double  closestAboveRatio = Double.POSITIVE_INFINITY;
            String  closestString      = "";
            String  closestAboveString = "";
            for (Entry<Unit<?>,String> e : unitsNML.entrySet ())
            {
                Unit<?> u = e.getKey ();
                if (u.isCompatible (unit))
                {
                    try
                    {
                        UnitConverter converter = unit.getConverterToAny (u);
                        double ratio = converter.convert (power);
                        if (ratio < 1)
                        {
                            ratio = 1 / ratio;
                        }
                        else
                        {
                            if (ratio < closestAboveRatio)
                            {
                                closestAboveRatio  = ratio;
                                closestAbove       = e.getKey ();
                                closestAboveString = e.getValue ();
                            }
                        }
                        if (ratio < closestRatio)
                        {
                            closestRatio  = ratio;
                            closest       = e.getKey ();
                            closestString = e.getValue ();
                        }
                    }
                    catch (UnconvertibleException | IncommensurableException e1)
                    {
                    }
                }
            }
            if (closest == null)
            {
                closest       = unit;
                closestString = unitString;
            }
            else if (closestAboveRatio <= 1000 + epsilon)
            {
                closest       = closestAbove;
                closestString = closestAboveString;
            }
            unitsUsed.put (closest, closestString);

            try
            {
                UnitConverter converter = unit.getConverterToAny (closest);
                value = converter.convert (value);
            }
            catch (Exception error) {}

            unit = closest;
            nml  = closestString;
        }

        public String print ()
        {
            return ExportJob.print (value) + nml;
        }
    }

    public String printDimension (Unit<?> unit)
    {
        Dimension dimension = unit.getDimension ();
        String result = dimensionsNML.get (dimension);
        if (result == null) result = dimension.toString ();  // Not a standard NML dimension
        return result;
    }

    public void appendUnits (boolean assumeNML)
    {
        Map<String,Dimension> dimensionsUsed = new HashMap<String,Dimension> ();
        for (Entry<Unit<?>,String> e : unitsUsed.entrySet ())
        {
            Unit<?> key = e.getKey ();
            if (assumeNML  &&  unitsNML.containsKey (key)) continue;

            String    symbol        = e.getValue ();
            Dimension dimension     = key.getDimension ();
            String    dimensionName = dimensionsNML.get (dimension);
            if (dimensionName == null)  // Not a standard NML dimension
            {
                dimensionName = dimension.toString ();
                dimensionsUsed.put (dimensionName, dimension);
            }
            else if (! assumeNML)  // Is a standard NML dimension, so only add if don't have NML dimensions available.
            {
                dimensionsUsed.put (dimensionName, dimension);
            }

            Element unit = addElement ("Unit", elements);
            unit.setAttribute ("symbol",    symbol);
            unit.setAttribute ("dimension", dimensionName);

            // Determine offset
            Unit systemUnit = key.getSystemUnit ();
            UnitConverter converter = key.getConverterTo (systemUnit);
            double offset = converter.convert (new Integer (0)).doubleValue ();
            if (offset != 0) unit.setAttribute ("offset", String.valueOf (offset));

            // Determine power*scale
            double scale = converter.convert (new Integer (1)).doubleValue () - offset;
            int power = (int) Math.round (Math.log (scale) / Math.log (10));
            if (Math.abs (scale - Math.pow (10, power)) < epsilon)
            {
                unit.setAttribute ("power", String.valueOf (power));
            }
            else
            {
                unit.setAttribute ("scale", String.valueOf (scale));
            }
        }

        for (Entry<String,Dimension> d : dimensionsUsed.entrySet ())
        {
            String    name  = d.getKey ();
            Dimension value = d.getValue ();

            Element dimension = addElement ("Dimension", elements);
            dimension.setAttribute ("name", name);
            Map<? extends Dimension,Integer> bases = value.getBaseDimensions ();
            if (bases == null)
            {
                Map<Dimension,Integer> temp = new HashMap<Dimension,Integer> ();
                temp.put (value, 1);
                bases = temp;
            }
            for (Entry<? extends Dimension,Integer> e : bases.entrySet ())
            {
                String base = e.getKey ().toString ().substring (1, 2).toLowerCase ();
                if (base.equals ("θ")) base = "k";
                dimension.setAttribute (base, e.getValue ().toString ());
            }
        }
    }
}
