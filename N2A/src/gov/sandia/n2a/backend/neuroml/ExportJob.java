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
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
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

    public List<Element>    elements = new ArrayList<Element> ();
    public List<IonChannel> channels = new ArrayList<IonChannel> ();
    public List<Synapse>    synapses = new ArrayList<Synapse> ();
    public List<Cell>       cells    = new ArrayList<Cell> ();
    public int              countPointCell;
    public int              countConcentration;
    public int              countInput;

    public static Map<Unit<?>,String> nmlUnits = new HashMap<Unit<?>,String> ();
    public static Unit<?> um = UCUM.parse ("um");
    public static double baseRatio = Math.log (10) / Math.log (2);  // log_2 (10), how many binary digits it takes to represent one decimal digit

    static
    {
        // Units specified in NeuroMLCoreDimensions. With a little massaging, these can be converted to UCUM.
        String[] nmlDefined =
        {
            "s", "ms",
            "per_s", "per_ms", "Hz",
            "min", "per_min", "hour", "per_hour",
            "m", "cm", "um",
            "m2", "cm2", "um2",
            "m3", "cm3", "litre", "um3",
            "V", "mV",
            "per_V", "per_mV",
            "ohm", "kohm", "Mohm",
            "S", "mS", "uS", "nS", "pS",
            "S_per_m2", "mS_per_m2", "S_per_cm2",
            "F", "uF", "nF", "pF",
            "F_per_m2", "uF_per_cm2",
            "ohm_m", "kohm_cm", "ohm_cm",
            "C",
            "C_per_mol", "nA_ms_per_mol",
            "m_per_s",
            "A", "uA", "nA", "pA",
            "A_per_m2", "uA_per_cm2", "mA_per_cm2",
            "mol_per_m3", "mol_per_cm3", "M", "mM",
            "mol",
            "m_per_s", "cm_per_s", "um_per_s", "cm_per_ms",
            "degC",
            "K",
            "J_per_K_per_mol",
            "S_per_V", "nS_per_mV",
            "mol_per_m_per_A_per_s", "mol_per_cm_per_uA_per_ms"
        };
        for (String u : nmlDefined) nmlUnits.put (UCUM.parse (cleanupUnits (u)), u);
    }

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

            // Make eqset minimally executable ...
            try
            {
                equations.resolveConnectionBindings ();
                equations.addGlobalConstants ();
                equations.addSpecials ();  // $index, $init, $live, $n, $t, $t', $type
                equations.fillIntegratedVariables ();
                equations.findIntegrated ();
                equations.resolveLHS ();
                equations.resolveRHS ();
                equations.findConstants ();  // This could hurt the analysis. It simplifies expressions and substitutes constants, breaking some dependency chains.
                equations.determineTypes ();
                equations.clearVariables ();
            }
            catch (Exception e)
            {
                System.out.println ("WARNING: Model compiled with errors. Export may not be correct.");
            }

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

        for (IonChannel ic : channels) ic.append ();
        for (Synapse s: synapses) s.append ();

        // Collate
        Element root = doc.createElement ("neuroml");
        root.setAttribute ("id", modelName);
        root.setAttribute ("xmlns",              "http://www.neuroml.org/schema/neuroml2");
        root.setAttribute ("xmlns:xsi",          "http://www.w3.org/2001/XMLSchema-instance");
        root.setAttribute ("xsi:schemaLocation", "http://www.neuroml.org/schema/neuroml2 ../Schemas/NeuroML2/NeuroML_v2beta4.xsd");
        sequencer.append (root, elements);
        doc.appendChild (root);
    }

    public void topLevelPart (MPart source)
    {
        String type    = source.get ("$metadata", "backend.lems.part");
        String inherit = source.get ("$inherit");
        if (type.equals ("network"))
        {
            new Network (source);
        }
        else if (type.equals ("cell"))
        {
            Cell cell = new Cell (source);
            cells.add (cell);
            if (cell.populationSize == 1)
            {
                cell.cell.setAttribute ("id", cell.name);  // Use final name as ID, since this is purely a cell model
            }
            else
            {
                // Wrap cell in a network
                Element network = addElement ("network", elements);
                network.setAttribute ("id", "N2A_Network0");
                Element population = doc.createElement ("population");
                network.appendChild (population);
                population.setAttribute ("id", cell.name);
                population.setAttribute ("component", cell.id);
                population.setAttribute ("size", String.valueOf (cell.populationSize));
            }
        }
        else if (type.isEmpty ())
        {
            new LEMS (source);
        }
        else if (type.contains ("Generator")  ||  type.contains ("Clamp")  ||  type.contains ("spikeArray")  ||  type.contains ("PointCurrent"))
        {
            input (source, elements, null);
        }
        else if (type.contains ("Synapse"))
        {
            addSynapse (source, false);
        }
        else if (inherit.contains ("Coupling"))
        {
            addSynapse (source, true);
        }
        else
        {
            genericPart (source, elements);
        }
    }

    /**
        Adapter to provide basic evaluation environment for extracting values of variables.
        Some variables are given explicit values, while all others return their default values.
    **/
    public static class SimpleContext extends Instance
    {
        public int index;
        public Type get (Variable v)
        {
            if (v.name.equals ("$index")) return new Scalar (index);
            if (v.name.equals ("$init" )) return new Scalar (1);
            return v.type;
        }
    };
    public SimpleContext context = new SimpleContext ();

    public class Network
    {
        String           name;
        List<Element>    networkElements = new ArrayList<Element> ();
        Map<String,Cell> populations     = new TreeMap<String,Cell> ();
        List<Space>      spaces          = new ArrayList<Space> ();

        public Network (MPart source)
        {
            name = source.key ();

            // Collect populations first, because they contain info needed by projections and inputs.
            for (MNode c : source)
            {
                MPart p = (MPart) c;

                String component = "";
                String type = c.get ("$metadata", "backend.lems.part");
                if (type.equals ("cell")  ||  type.equals ("segment"))  // multi-compartment cell, or HH segment pretending to be a point cell
                {
                    Cell cell = new Cell (p);
                    cells.add (cell);
                    populations.put (cell.name, cell);
                    component = cell.id;
                }
                else if (type.endsWith ("Cell"))  // standard point cell
                {
                    Element cell = addElement (type, elements);
                    genericPart (p, cell);
                    component = "N2A_PointCell" + countPointCell++;
                    cell.setAttribute ("id", component);
                }

                if (! component.isEmpty ())
                {
                    EquationSet part = getEquations (p);
                    Variable n = part.find (new Variable ("$n", 0));
                    int size = 1;
                    if (n != null) size = (int) Math.floor (((Scalar) n.eval (context)).value);

                    // Determine type of spatial layout
                    MNode xyzNode = p.child ("$xyz");
                    boolean list = xyzNode != null  &&  xyzNode.size () > 0;
                    type = "population";
                    if (list) type += "List";

                    // Create population element
                    Element population = addElement (type, networkElements);
                    population.setAttribute ("id", p.key ());
                    population.setAttribute ("component", component);
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
            }

            // Projections and inputs
            for (MNode c : source)
            {
                MPart p = (MPart) c;
                if (! p.isPart ()) continue;
                String type = p.get ("$metadata", "backend.lems.part");
                String inherit = p.get ("$inherit");
                if (type.contains ("Synapse"))
                {
                    Element result = addElement ("projection", networkElements);
                    result.setAttribute ("synapse", addSynapse (p, false));
                    connections (p, result, "connection", "", "");
                }
                else if (type.contains ("Projection"))  // gap junctions and split synapses. These use the special projection types.
                {
                    projectionSplit (p);
                }
                else if (inherit.contains ("Coupling"))
                {
                    // TODO: convert electricalProjection to Coupling during import
                    Element result = addElement ("electricalProjection", networkElements);
                    String synapseID = addSynapse (p, true);
                    connections (source, result, "electricalConnection", synapseID, synapseID);

                }
                else if (type.contains ("Generator")  ||  type.contains ("Clamp")  ||  type.contains ("PointCurrent"))  // unary connection with embedded input
                {
                    Element result = addElement ("inputList", networkElements);
                    result.setAttribute ("component", input (p, elements, null));
                    connections (p, result, "input", "", "");
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
            network.setAttribute ("id", name);
            standalone (source, network, networkElements);
            String temperature = source.get ("temperature");
            if (! temperature.isEmpty ())
            {
                network.setAttribute ("type", "networkWithTemperature");
                network.setAttribute ("temperature", biophysicalUnits (temperature));
            }
            sequencer.append (network, networkElements);
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

            String preComponent  = addSynapse (preNode, electrical);
            String postComponent = addSynapse (postNode, electrical);

            Element result = addElement (projectionType, networkElements);
            connections (source, result, connectionType, preComponent, postComponent);
        }

        public void connections (MPart source, Element result, String type, String preComponent, String postComponent)
        {
            result.setAttribute ("id", source.key ());

            String[] pieces = source.get ("A").split ("\\.");
            String prePopulation = pieces[0];
            Cell preCell = populations.get (prePopulation);
            String preSegment = "";
            if (pieces.length > 1) preSegment = pieces[1];

            pieces = source.get ("B").split ("\\.");
            String postPopulation = pieces[0];
            Cell postCell = populations.get (postPopulation);
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
            if (originalP == null)
            {
                p.set ("@", "1");
            }
            else
            {
                p.set (originalP);
                String condition = p.get ();
                if (! condition.isEmpty ())
                {
                    p.set ("");
                    p.set ("@" + condition, "1");
                }
            }

            // Scan conditions and emit connection for each one
            List<Element> projectionElements = new ArrayList<Element> ();
            for (MNode c : p)
            {
                if (! c.get ().equals ("1")) continue;
                String condition = c.key ();

                String indexA   = "";
                String indexAup = "";
                String indexB   = "";
                String indexBup = "";
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

                String Cell    = "Cell";
                String Segment = "Segment";
                if (isConnection  ||  inputList)
                {
                    Cell    += "Id";
                    Segment += "Id";
                }

                if (! inputList)
                {
                    String path = prePopulation;
                    String index;
                    if (preSegment.isEmpty ())  // point cell
                    {
                        index = indexA;
                    }
                    else  // multi-compartment cell
                    {
                        index = indexAup;
                        connection.setAttribute ("pre" + Segment, preCell.mapID (preSegment, indexA));
                    }
                    if (preCell.populationSize > 1  &&  ! index.isEmpty ())
                    {
                        path = "../" + prePopulation + "/" + index + "/" + preCell.id;
                    }
                    connection.setAttribute ("pre" + Cell, path);
                }

                String path = postPopulation;
                String index;
                if (postSegment.isEmpty ())
                {
                    index = indexB;
                }
                else
                {
                    index = indexBup;
                    if (inputList) connection.setAttribute ("segmentId",      postCell.mapID (postSegment, indexB));
                    else           connection.setAttribute ("post" + Segment, postCell.mapID (postSegment, indexB));
                }
                if (postCell != null  &&  postCell.populationSize > 1  &&  ! index.isEmpty ())
                {
                    path = "../" + postPopulation + "/" + index + "/" + postCell.id;
                }
                if (inputList) connection.setAttribute ("target",      path);
                else           connection.setAttribute ("post" + Cell, path);

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

    public String addSynapse (MPart source, boolean electrical)
    {
        Synapse s = new Synapse (source, electrical);
        int index = synapses.indexOf (s);
        if (index >= 0) s = synapses.get (index);
        else                synapses.add (s);

        // Check for chained synapse (embedded input)
        MPart A = (MPart) source.child ("A");
        if (A == null  ||  A.size () == 0) return s.id;
        Synapse s2 = new Synapse (s.id, A);

        index = synapses.indexOf (s2);
        if (index >= 0) s2 = synapses.get (index);
        else                 synapses.add (s2);
        return s2.id;
    }

    public class Synapse
    {
        String  id      = "N2A_Synapse" + synapses.size ();
        String  chainID = "";
        MPart   source;
        MPart   sourceA;  // Only non-null if we have an embedded input.
        MNode   base    = new MVolatile ();
        boolean electrical;  // A hint about context. Shouldn't change identity.

        public Synapse (MPart source, boolean electrical)
        {
            this.source     = source;
            this.electrical = electrical;

            // Assemble a part that best recreates the underlying synapse before it got incorporated into a projection
            for (MNode c : source)
            {
                String key = c.key ();
                if ("A|B|$p|weight|delay|preFraction|postFraction".contains (key)) continue;
                base.set (key, c);
            }
        }

        public Synapse (String chainID, MPart sourceA)
        {
            this.chainID = chainID;
            this.sourceA = sourceA;
        }

        public void append ()
        {
            if (! chainID.isEmpty ())  // We have embedded input, so the main synapse will be emitted elsewhere.
            {
                input (sourceA, elements, this);
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
                    block.setAttribute ("species", c.get ("$metadata", "species"));
                    genericPart (p, block);
                }
            }
            if (skip.size () > 0) type = "blockingPlasticSynapse";
            skip.add ("A");

            Element s = addElement (type, elements);
            s.setAttribute ("id", id);
            sequencer.append (s, synapseElements);
            genericPart (source, s, skip.toArray (new String[] {}));
        }

        public boolean equals (Object o)
        {
            if (! (o instanceof Synapse)) return false;
            Synapse that = (Synapse) o;
            if (! chainID.isEmpty ()  ||  ! that.chainID.isEmpty ())
            {
                return chainID.equals (that.chainID)  &&  sourceA.equals (that.sourceA);
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
            id = source.key ();
        }
        else
        {
            id = synapse.id;
            input.setAttribute ("synapse",            synapse.chainID);
            input.setAttribute ("spikeTarget", "./" + synapse.chainID);
        }
        input.setAttribute ("id", id);
        standalone (source, input, inputElements);
        genericPart (source, input, skip.toArray (new String[] {}));
        sequencer.append (input, inputElements);
        return id;
    }

    public class Cell
    {
        public String             name;
        public String             id;
        public Element            cell;
        public int                populationSize;
        public List<Element>      cellElements    = new ArrayList<Element> ();
        public List<Element>      morphology      = new ArrayList<Element> ();  // break out sub-element for easy access
        public List<Element>      membrane        = new ArrayList<Element> ();  // sub-element of biophysical properties 
        public List<Element>      intra           = new ArrayList<Element> ();  // ditto
        public List<Element>      extra           = new ArrayList<Element> ();  // ditto
        public List<Property>     properties      = new ArrayList<Property> ();
        public List<SegmentBlock> blocks          = new ArrayList<SegmentBlock> ();  // entries map to rows of P
        public MatrixBoolean      P               = new MatrixBoolean ();
        public MatrixBoolean      multiGroup      = new MatrixBoolean ();
        // TODO: deal with ca2 variants

        public Cell (MPart source)
        {
            name = source.key ();
            id = "N2A_Cell" + cells.size ();
            cell = addElement ("cell", elements);
            standalone (source, cell, cellElements);
            cell.setAttribute ("id", id);  // May get changed to "name" if this is a non-networked cell

            EquationSet part = getEquations (source);
            populationSize = 1;
            Variable n = part.find (new Variable ("$n", 0));
            if (n != null) populationSize = (int) Math.floor (((Scalar) n.eval (context)).value);

            // Collect Segments and transform them into distinct property sets.
            // This reverses the import process, which converts property sets into distinct segment populations.
            if (source.get ("$metadata", "backend.lems.part").equals ("segment"))  // This is a standalone segment, pretending to be a cell.
            {
                blocks.add (new SegmentBlock (part));
                populationSize = 1;  // Since SegmentBlock also interprets $n, and that is the right place to do it in this case.
                // TODO: process peer Coupling parts into segment parent-child relationships
            }
            else  // conventional cell
            {
                List<Segment> segments = new ArrayList<Segment> ();
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
                    SegmentBlock A = blockNames.get (c.source.get ("A"));  // parent
                    SegmentBlock B = blockNames.get (c.source.get ("B"));  // child
                    if (A == null  ||  B == null) continue;  // This should never happen for imported models, but user-made models could be ill-formed.

                    MNode p = c.source.child ("$p");
                    if (p == null)  // connect all to all
                    {
                        for (Segment a : A.segments)
                        {
                            for (Segment b : B.segments)
                            {
                                a.addChild (b);
                            }
                        }
                    }
                    else
                    {
                        String value = p.get ();
                        if (value.startsWith ("A.$index==B."))  // explicit parent indices
                        {
                            String parentName = value.substring (12);
                            MNode parentNode = B.block.child (parentName);
                            value = parentNode.get ();
                            if (value.isEmpty ())
                            {
                                for (MNode m : parentNode)
                                {
                                    String key = m.key ();
                                    if (key.equals ("@")) continue;
                                    int Bindex = Integer.valueOf (key.split ("==", 2)[1]);
                                    int Aindex = Integer.valueOf (m.get ());
                                    A.segments.get (Aindex).addChild (B.segments.get (Bindex));
                                }
                            }
                            else  // singleton child
                            {
                                int index = Integer.valueOf (value);
                                A.segments.get (index).addChild (B.segments.get (0));
                            }
                        }
                        else  // singleton parent with explicit child indices in $p
                        {
                            Segment parent = A.segments.get (0);
                            if (value.isEmpty ())
                            {
                                for (MNode m : p)
                                {
                                    String key = m.key ();
                                    if (key.equals ("@")) continue;
                                    int index = Integer.valueOf (key.split ("==", 2)[1]);
                                    parent.addChild (B.segments.get (index));
                                }
                            }
                            else
                            {
                                int index = Integer.valueOf (value.split ("==", 2)[1]);
                                parent.addChild (B.segments.get (index));
                            }
                        }
                    }
                }

                // Assign segment IDs based on parent-child relationships
                int index = 0;
                for (Segment s : segments)
                {
                    if (s.parent == null) index = s.assignID (index);
                }
            }

            // Emit segments and groups
            for (SegmentBlock sb : blocks) sb.append ();
            for (Property p : properties) p.append ();
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

        public String mapID (String blockName, String index)
        {
            for (SegmentBlock sb : blocks)
            {
                if (blockName.equals (sb.block.key ()))
                {
                    int count = sb.segments.size ();
                    if (count == 1) return String.valueOf (sb.segments.get (0).id);
                    int i = Integer.valueOf (index);
                    if (i < count) return String.valueOf (sb.segments.get (i).id);
                }
            }
            return index;
        }

        public class Segment
        {
            public String        name;
            public Segment       parent;
            public List<Segment> children = new ArrayList<Segment> ();
            public int           id; // NeuroML id

            public double fractionAlong    = 1;
            public Matrix proximal;
            public Matrix distal;
            public double proximalDiameter = -1;
            public double distalDiameter   = -1;

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
                // TODO: handle obscure use-cases: neuroLexId, notes, property, annotation

                double fractionAlong = 1;
                double parentDiameter = proximalDiameter;
                if (parent != null)
                {
                    Element p = doc.createElement ("parent");
                    segment.appendChild (p);
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
                    Element p = doc.createElement ("proximal");
                    segment.appendChild (p);
                    // Convert all morphology to micrometers.
                    p.setAttribute ("x",        print (proximal.get (0) * 1e6));
                    p.setAttribute ("y",        print (proximal.get (1) * 1e6));
                    p.setAttribute ("z",        print (proximal.get (2) * 1e6));
                    p.setAttribute ("diameter", print (proximalDiameter * 1e6));
                }

                Element d = doc.createElement ("distal");
                segment.appendChild (d);
                d.setAttribute ("x",        print (distal.get (0) * 1e6));
                d.setAttribute ("y",        print (distal.get (1) * 1e6));
                d.setAttribute ("z",        print (distal.get (2) * 1e6));
                d.setAttribute ("diameter", print (distalDiameter * 1e6));
            }
        }

        // TODO: handle inhomogeneous parameters

        public class SegmentBlock
        {
            public EquationSet   part;
            public MNode         block;
            public List<Segment> segments = new ArrayList<Segment> ();

            public SegmentBlock (EquationSet part)
            {
                this.part = part;
                block     = part.source;

                // Analyze
                Variable n          = part.find (new Variable ("$n", 0));
                Variable xyz0       = part.find (new Variable ("xyz0"));
                Variable xyz        = part.find (new Variable ("$xyz"));
                Variable diameter0  = part.find (new Variable ("diameter0"));
                Variable diameter   = part.find (new Variable ("diameter"));

                // Extract segments
                int count = (int) Math.floor (((Scalar) n.eval (context)).value);
                if (count == 1)
                {
                    Segment s = new Segment ();
                    segments.add (s);
                    s.name = block.key ();
                }
                else
                {
                    for (int i = 0; i < count; i++)
                    {
                        Segment s = new Segment ();
                        segments.add (s);
                        s.name = block.get ("$metadata", "name" + i);
                        if (s.name.isEmpty ()) s.name = block.key () + i;
                    }
                }
                for (int index = 0; index < count; index++)
                {
                    Segment s = segments.get (index);
                    context.index = index;
                    s.proximal         =  (Matrix) xyz0     .eval (context);
                    s.distal           =  (Matrix) xyz      .eval (context);
                    s.proximalDiameter = ((Scalar) diameter0.eval (context)).value;
                    s.distalDiameter   = ((Scalar) diameter .eval (context)).value;
                }

                // Extract properties

                //   initMembPotential
                String value = getLocalProperty ("V", block);
                int position = value.indexOf ("@$init");
                if (position >= 0)  // This also tests whether the string is empty.
                {
                    value = value.substring (0, position);
                    addUnique (new PropertyMembrane (membrane, "initMembPotential", value));
                }

                //   resistivity
                value = getLocalProperty ("resistivity", block);
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
                P.set (blocks.size (), p.column);  // this SegmentBlock has not been added to blocks yet, so blocks.size() gives our (future) index.
            }

            public void append ()
            {
                for (Segment s : segments) s.append ();
                int size = segments.size ();
                if (size > 1)
                {
                    Element group = addElement ("segmentGroup", morphology);
                    group.setAttribute ("id", block.key ());
                    String neuroLexId = block.get ("$metadata", "neuroLexId");
                    if (! neuroLexId.isEmpty ()) group.setAttribute ("neuroLexId", neuroLexId);
                    List<Element> groupElements = new ArrayList<Element> ();

                    // detect opportunities to use path or subTree rather than member
                    // TODO: a better approach is to sort by id, then scan for contiguous blocks, while also emitting isolated IDs as members
                    if (size > 4)
                    {
                        int from = Integer.MAX_VALUE;
                        int to   = Integer.MIN_VALUE;
                        for (Segment s : segments)
                        {
                            from = Math.min (from, s.id);
                            to   = Math.max (to,   s.id);
                        }

                        if (to - from + 1 == size)
                        {
                            Element path = addElement ("path", groupElements);
                            Element f = doc.createElement ("from");
                            f.setAttribute ("from", String.valueOf (from));
                            path.appendChild (f);
                            Element t = doc.createElement ("to");
                            t.setAttribute ("to", String.valueOf (to));
                            path.appendChild (t);
                            return;
                        }
                    }

                    for (Segment s : segments)
                    {
                        Element member = addElement ("member", groupElements);
                        member.setAttribute ("segment", String.valueOf (s.id));
                    }

                    sequencer.append (group, groupElements);
                }
            }
        }

        public class Property
        {
            public int column = properties.size ();  // in P

            public void append ()
            {
            }

            public void setSegment (Element e)
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

                if (all) return;  // Don't emit any kind of segment selection, as the default is all.
                if (count == 1)  // use individual segment reference
                {
                    Segment s = lastFound.segments.get (0);
                    e.setAttribute ("segment", String.valueOf (s.id));
                }
                else if (lastFound.segments.size () == count)  // Single group associated with Segment
                {
                    e.setAttribute ("segmentGroup", lastFound.block.key ());
                }
                else  // Assemble multiple groups
                {
                    String groupName = "Group";
                    int match = multiGroup.matchColumn (P.column (column));
                    if (match >= 0)
                    {
                        groupName += match;
                    }
                    else
                    {
                        match = multiGroup.columns ();
                        multiGroup.set (match, P.column (column));
                        groupName += match;
                        Element group = addElement ("segmentGroup", morphology);
                        group.setAttribute ("name", groupName);
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
                    e.setAttribute ("segmentGroup", groupName);
                }
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
                if (target == membrane) setSegment (e);
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
                // Select element type
                String type = "channelPopulation";
                if (source.get ("population").isEmpty ())
                {
                    boolean hasDensity = ! source.get ("Gdensity").isEmpty ();
                    String potential = findPotential (source);
                    if (potential.startsWith ("Potential "))
                    {
                        potential = potential.substring (10);
                        switch (potential)
                        {
                            case "Nernst":
                                if (hasDensity) type = "channelDensityNernst";
                                else            type = "channelDensityNonUniformNernst";
                                if (ca2) type += "Ca2";
                                break;
                            case "GHK":
                                // If permeability fails to parse into a number, then it is (probably) an
                                // expression that involves a variableParameter, indicating non-uniform.
                                double permeability = Scalar.convert (source.get ("permeability"));
                                if (permeability == 0) type = "channelDensityNonUniformGHK";
                                else                   type = "channelDensityGHK";
                                break;
                            case "GHK 2":
                                type = "channelDensityGHK2";
                                break;
                        }
                    }
                    else  // potential is empty
                    {
                        if (hasDensity) type = "channelDensity";
                        else            type = "channelDensityNonUniform";
                    }
                }

                IonChannel ic = new IonChannel (source);
                boolean found = false;
                for (IonChannel o : channels)
                {
                    if (o.equals (ic))
                    {
                        ic = o;
                        found = true;
                        break;
                    }
                }
                if (! found) channels.add (ic);

                Element e = addElement (type, membrane);
                e.setAttribute ("id", source.key ());
                e.setAttribute ("ionChannel", ic.id);
                setSegment (e);

                String ion = source.get ("$metadata", "species");
                if (! ion.isEmpty ()) e.setAttribute ("ion", ion);

                for (String attributeName : new String[] {"E", "population", "Gdensity", "permeability"})
                {
                    MPart attribute = (MPart) source.child (attributeName);
                    if (attribute != null  &&  attribute.isFromTopDocument ()) setNumber (e, attribute);
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
                String ion = source.key ();  // Concentration models are always named after their ion.
                String type = source.get ("$metadata", "backend.lems.part").split (",")[0];
                String inherit = source.get ("$inherit").replace ("\"", "");
                NameMap nameMap = partMap.exportMap (inherit);
                String inside0  = nameMap.importName ("initialConcentration");
                String outside0 = nameMap.importName ("initialExtConcentration");

                Element concentration = addElement (type, elements);
                String concentrationID = "N2A_Concentration" + countConcentration++;
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

    public class LEMS
    {
        String  name;
        Element componentType;

        public LEMS (MPart source)
        {
            name = source.key ();
            componentType = addElement ("ComponentType", elements);
            componentType.setAttribute ("name", name);
        }
    }

    public class IonChannel
    {
        public String        id;
        public MPart         source;    // from original document
        public MPart         base;      // pseudo-document with modifications to factor out changes made by Segment
        public String        inherit;   // model name of parent channel, without the Potential
        public String        potential; // model name for the Potential, if specified. null if no Potential is given.
        public List<Element> channelElements = new ArrayList<Element> ();

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

            // Assemble a part that best recreates the underlying channel before it got incorporated into a Segment
            base = new MPart (new MDoc ("/"));  // Path doesn't matter, because we will never save this doc.
            base.set ("$inherit", inherit);  // skip potential, if it existed
            for (MNode c : source)
            {
                String key = c.key ();
                if ("$inherit|c|E|Gall|Gdensity|population|permeability".contains (key)) continue;
                base.set (key, c);
            }

            // Suggest an id
            if (inherit.startsWith (modelName))
            {
                id = inherit.substring (modelName.length ()).trim ();
            }
            else
            {
                id = "N2A_Channel" + channels.size ();
            }
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
            Element q10 = addElement (type, parentElements);
            genericPart (part, q10);
            // TODO: trap fixedQ10. May need to fully implement this method, rather than using genericPart().
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
            List<Element> gateElements = new ArrayList<Element> ();
            for (MNode c : part)
            {
                MPart p = (MPart) c;
                if (p.isPart ())
                {
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
                        flags |= rate (p, gateElements);
                    }
                }
                else
                {
                    if (p.key ().equals ("tau")  &&  p.isFromTopDocument ())  // explicit tau
                    {
                        flags |= tau;
                        Element t = addElement ("timeCourse", gateElements);
                        t.setAttribute ("type", "fixeTimeCourse");  // TODO: verify this is the right type
                        t.setAttribute ("tau", biophysicalUnits (p.get ()));
                    }
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

        public int rate (MPart part, List<Element> gateElements)
        {
            int result = 0;
            if      (part.child ("$up.α"        ) != null) result = alpha;
            else if (part.child ("$up.forward"  ) != null) result = alpha;
            else if (part.child ("$up.β"        ) != null) result = beta;
            else if (part.child ("$up.reverse"  ) != null) result = beta;
            else if (part.child ("$up.τUnscaled") != null) result = tau;
            else if (part.child ("$up.inf"      ) != null) result = inf;
            else if (part.child ("$up.q"        ) != null) result = inf;

            String name = part.key ();
            switch (result)
            {
                case alpha: name = "forwardRate"; break;
                case beta : name = "reverseRate"; break;
                case tau  : name = "timeCourse";  break;
                case inf  : name = "steadyState"; break;
            }

            String[] types = part.get ("$metadata", "backend.lems.part").split (",");
            String search = "Variable";
            if (name.contains ("Rate")) search = "Rate";
            String type = "unknown";
            for (String t : types) if (t.contains (search)) type = t;

            Element r = addElement (name, gateElements);
            r.setAttribute ("type", type);
            for (String a : new String[] {"rate", "midpoint", "scale"})
            {
                r.setAttribute (a, biophysicalUnits (part.get (a)));
            }

            return result;
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
            List<Element> kineticElements = new ArrayList<Element> ();
            for (MNode c : part)
            {
                MPart p = (MPart) c;
                if (p.isPart ()) flags |= rate (p, kineticElements);
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
        }

        public boolean equals (Object that)
        {
            if (! (that instanceof IonChannel)) return false;
            IonChannel ic = (IonChannel) that;
            return ic.base.equals (base);  // deep compare
        }
    }

    public Element genericPart (MPart part, List<Element> elements, String... skip)
    {
        // TODO: detect when it's necessary to emit a LEMS part
        String id   = part.key ();
        String type = part.get ("$metadata", "backend.lems.part").split (",")[0];  // first part should be preferred output type
        if (type.isEmpty ()  ||  ! sequencer.hasID (type))
        {
            type = id;
            id = "";
        }
        Element e = addElement (type, elements);
        if (! id.isEmpty ()) e.setAttribute ("id", id);
        genericPart (part, e, skip);
        return e;
    }

    public void genericPart (MPart part, Element result, String... skip)
    {
        EquationSet partEquations = getEquations (part);
        List<Element> resultElements = new ArrayList<Element> ();
        List<String> skipList = Arrays.asList (skip);
        for (MNode c : part)
        {
            MPart p = (MPart) c;

            String key = p.key ();
            if (partEquations.findConnection (key) != null) continue;  // Skip connection bindings. They are unpacked elsewhere.
            if (key.startsWith ("$")) continue;
            if (skipList.contains (key)) continue;

            if (p.isPart ())
            {
                genericPart (p, resultElements);
            }
            else
            {
                // We need to check two things:
                // * Has the variable been overridden after it was declared in the base part?
                // * Is it an expression or a constant?
                // An override that is an expression should trigger a LEMS extension part.
                // A constant that is either overridden or required should be emitted here.

                boolean expression = true;
                String value = p.get ();
                Variable v = partEquations.find (new Variable (key));
                try
                {
                    Type evalue = v.eval (context);  // This could convert an expression to a constant.
                    // TODO: if it is a simple AccessVariable, then it shouldn't be viewed as an expression.
                    Operator op = Operator.parse (value);
                    if (op instanceof Constant)
                    {
                        Type dvalue = ((Constant) op).value;  // "direct" value
                        expression = ! evalue.equals (dvalue);
                    }
                }
                catch (Exception e) {}

                boolean overridden = p.isFromTopDocument ()  ||  isOverride (part.get ("$inherit").replace ("\"", ""), key);

                String name = sequencer.bestFieldName (result, p.get ("$metadata", "backend.lems.param"));
                if (name.isEmpty ()) name = key;
                boolean required = sequencer.isRequired (result, name);

                if (required  ||  (overridden  &&  ! expression))
                {
                    result.setAttribute (name, biophysicalUnits (p.get ()));  // biophysicalUnits() should return strings (non-numbers) unmodified
                }
            }
        }
        sequencer.append (result, resultElements);
    }

    public boolean isOverride (String inherit, String key)
    {
        MNode part = AppData.models.child (inherit);
        if (part == null) return false;
        if (! part.get ("$metadata", "backend.lems.part").isEmpty ()) return false;  // Once we get to the base part, fields no longer count as override. IE: it's either a ComponentType or a Component instance, not both.
        if (part.child (key) != null) return true;
        return isOverride (part.get ("$inherit").replace ("\"", ""), key);
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

    public void setNumber (Element e, MNode source)
    {
        String name = source.get ("$metadata", "backend.lems.param").split (",", 2)[0];
        if (name.isEmpty ()) name = source.key ();
        setNumber (e, name, source.get ());
    }

    public void setNumber (Element e, String name, String value)
    {
        value = biophysicalUnits (value);
        e.setAttribute (name, value);
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

        return print (v);  // no unit string at end of number, because morphology units are always in um
    }

    public String biophysicalUnits (String value)
    {
        value = value.trim ();
        int unitIndex = findUnits (value);
        if (unitIndex == 0  ||  unitIndex >= value.length ()) return value;  // no number or no units, so probably something else

        String unitString   = value.substring (unitIndex).trim ();
        String numberString = value.substring (0, unitIndex);

        Unit<?> unit;
        try
        {
            unit = UCUM.parse (unitString);
        }
        catch (Exception e)
        {
            return value;
        }

        double v = 0;
        try
        {
            v = Double.valueOf (numberString);
        }
        catch (NumberFormatException error)
        {
            return value;
        }

        // Determine power in numberString itself
        double power = 1; 
        if (v != 0) power = Math.pow (10, Math.floor ((Math.getExponent (v) + 1) / baseRatio));
        System.out.println (value + " = " + power);

        // Find closest matching unit
        Entry<Unit<?>,String> closest      = null;
        Entry<Unit<?>,String> closestAbove = null;  // like closest, but only ratios >= 1
        double closestRatio      = Double.POSITIVE_INFINITY;
        double closestAboveRatio = Double.POSITIVE_INFINITY;
        for (Entry<Unit<?>,String> e : nmlUnits.entrySet ())
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
                            closestAboveRatio = ratio;
                            closestAbove = e;
                        }
                    }
                    if (ratio < closestRatio)
                    {
                        closestRatio = ratio;
                        closest = e;
                    }
                }
                catch (UnconvertibleException | IncommensurableException e1)
                {
                }
            }
        }
        if (closest == null)
        {
            // TODO: Add to LEMS Dimension/Unit sections
            return value;  // completely give up on conversion
        }
        if (closestAboveRatio <= 1000 + epsilon) closest = closestAbove;

        try
        {
            UnitConverter converter = unit.getConverterToAny (closest.getKey ());
            v = converter.convert (v);
        }
        catch (Exception error)
        {
        }
        return print (v) + closest.getValue ();
    }
}
