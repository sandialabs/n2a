/*
Copyright 2017-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.neuroml;

import gov.sandia.n2a.backend.neuroml.PartMap.NameMap;
import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.eqset.Variable.ParsedValue;
import gov.sandia.n2a.language.AccessElement;
import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Renderer;
import gov.sandia.n2a.language.Transformer;
import gov.sandia.n2a.language.UnitValue;
import gov.sandia.n2a.language.parse.ExpressionParser;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.linear.MatrixDense;
import gov.sandia.n2a.ui.eq.tree.NodePart;
import gov.sandia.n2a.ui.eq.undo.AddDoc;
import systems.uom.ucum.internal.format.TokenException;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.measure.Unit;
import javax.measure.format.MeasurementParseException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import tech.units.indriya.AbstractUnit;
import tech.units.indriya.function.MultiplyConverter;
import tech.units.indriya.unit.TransformedUnit;
import tech.units.indriya.unit.Units;

public class ImportJob extends XMLutility
{
    PartMap                     partMap;
    LinkedList<Path>            sources         = new LinkedList<Path> ();
    Set<Path>                   alreadyIncluded = new HashSet<Path> ();         // Similar to "sources", but keeps all history.
    MNode                       models          = new MVolatile ();
    String                      modelName       = "";
    String                      primaryModel    = "";                           // A part tagged to be elevated to main model. When set, all other parts should be pushed out to independent models, and this one raised one level to assume the identity of the prime model.
    Set<MNode>                  dependents      = new HashSet<MNode> ();        // Nodes which contain a $inherit that refers to a locally defined part rather than a standard part. The local part must either be copied in or converted into a global model.
    Map<String,Node>            morphologies    = new HashMap<String,Node> ();  // Map from IDs to top-level morphology blocks.
    Map<String,Node>            biophysics      = new HashMap<String,Node> ();  // Map from IDs to top-level biophysics blocks.
    Map<String,Node>            properties      = new HashMap<String,Node> ();  // Map from IDs to top-level intra- or extra-cellular property blocks.
    Map<String,Cell>            cells           = new HashMap<String,Cell> ();
    Map<String,Network>         networks        = new HashMap<String,Network> ();
    List<Output>                outputs         = new ArrayList<Output> ();
    Map<String,ComponentType>   components      = new HashMap<String,ComponentType> ();
    Map<String,TreeSet<String>> aliases         = new HashMap<String,TreeSet<String>> ();
    Map<String,Unit<?>>         dimensions      = new TreeMap<String,Unit<?>> ();  // Declared dimension names

    public ImportJob (PartMap partMap)
    {
        this.partMap = partMap;

        // Pre-load standard NML dimensions and units. Any dimensions or units defined by the input files will overlay these.
        // This provides a fallback for the likely case that NeuroMLCoreDimensions is included by not available in the local directory.
        dimensions.putAll (nmlDimensions);
        ExpressionParser.namedUnits = new HashMap<String,Unit<?>> ();
        for (Entry<Unit<?>,String> e : unitsNML.entrySet ()) ExpressionParser.namedUnits.put (e.getValue (), e.getKey ());
    }

    public void process (Path source)
    {
        // Guard against repeat include
        if (alreadyIncluded.contains (source)) return;
        alreadyIncluded.add (source);

        sources.push (source);
        try
        {
            // Open and parse XML document
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance ();
            factory.setCoalescing (true);
            factory.setIgnoringComments (true);
            factory.setIgnoringElementContentWhitespace (true);
            factory.setXIncludeAware (true);  // Doesn't seem to actually include other files, at least on the samples I've tried so far. Must be missing something.
            DocumentBuilder builder = factory.newDocumentBuilder ();
            Document doc = builder.parse (source.toFile ());

            // Extract models
            process (doc);
        }
        catch (IOException e)
        {
        }
        catch (ParserConfigurationException e)
        {
        }
        catch (SAXException e)
        {
        }
        sources.pop ();
    }

    public void process (Node node)
    {
        if (node.getNodeType () != Node.ELEMENT_NODE)  // For all other node types, recurse into structure, looking for ELEMENT nodes.
        {
            for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ()) process (child);
            return;
        }

        // Triage the element type
        switch (node.getNodeName ())
        {
            case "morphml":
            case "channelml":
            case "networkml":
            case "neuroml":
            case "Lems":
                neuroml (node);
                break;
        }
    }

    public void neuroml (Node node)
    {
        Path source = sources.getLast ();
        if (modelName.isEmpty ())
        {
            modelName = getAttribute (node, "id");
            if (modelName.isEmpty ())  // then get it from the filename
            {
                modelName = source.getFileName ().toString ();
                int index = modelName.lastIndexOf ('.');
                if (index > 0) modelName = modelName.substring (0, index);
            }
            modelName = NodePart.validIdentifierFrom (modelName);
            // Preemptively scan for unique name, so our references to any sibling parts remain valid.
            // IE: a name collision in the main part implies that sibling parts will also have name collisions.
            modelName = AddDoc.uniqueName (modelName);
        }
        MNode model = models.childOrCreate (modelName);

        String description = getAttribute (node, "description");
        if (! description.isEmpty ()) model.set (description, "$metadata", "description");

        for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
        {
            if (child.getNodeType () != Node.ELEMENT_NODE) continue;
            switch (child.getNodeName ())
            {
                case "include":  // NeuroML include
                    // TODO: what if href actually references a web document?
                    Path nextSource = source.getParent ().resolve (getAttribute (child, "href"));
                    process (nextSource);
                    break;
                case "Include":  // LEMS include
                    nextSource = source.getParent ().resolve (getAttribute (child, "file"));
                    process (nextSource);
                    break;

                // NeuroML ---------------------------------------------------
                case "intracellularProperties":
                case "extracellularProperties":
                    properties.put (getAttribute (child, "id"), child);
                    break;
                case "morphology":
                    morphologies.put (getAttribute (child, "id"), child);
                    break;
                case "ionChannel":
                case "ionChannelHH":
                case "ionChannelKS":
                    ionChannel (child);
                    break;
                case "decayingPoolConcentrationModel":
                case "fixedFactorConcentrationModel":
                case "fixedFactorConcentrationModelTraub":  // This tag appears in Cells.xml, but not in NeuroML_v2beta4.xsd
                    concentrationModel (child);
                    break;
                case "blockingPlasticSynapse":
                    blockingPlasticSynapse (child);
                    break;
                case "biophysicalProperties":
                    biophysics.put (getAttribute (child, "id"), child);
                    break;
                case "cell":
                    Cell cell = new Cell (child);
                    cells.put (cell.id, cell);
                    break;
                case "fitzHughNagumoCell":
                    MNode FN = genericPart (child, model);
                    FN.set ("1s", "TS");  // Force time-scale to match the hard-coded value in LEMS definitions.
                    break;
                case "poissonFiringSynapse":
                case "transientPoissonFiringSynapse":
                case "spikeArray":
                case "timedSynapticInput":
                    spikingSynapse (child);
                    break;
                case "compoundInput":
                    compoundInput (child);
                    break;
                case "network":
                    Network network = new Network (child);
                    networks.put (network.id, network);
                    break;
                case "Simulation":
                    simulation (child);
                    break;

                // LEMS ------------------------------------------------------
                case "Target":
                    target (child);
                    break;
                case "Dimension":
                    dimension (child);
                    break;
                case "Unit":
                    unit (child);
                    break;
                case "Constant":
                    new ComponentType (model).genericVariable (child, "");  // Create a bogus component to wrap the top level, so we can add a LEMS constant to it.
                    break;
                case "ComponentType":
                    ComponentType component = new ComponentType (child);
                    components.put (component.part.key (), component);
                    break;
                case "Component":
                default:
                    genericPart (child, model);  // Any NeuroML part not specifically processed above will fall through to here.
                    break;
            }
        }
    }

    public void addDependencyFromConnection (MNode part, String inherit)
    {
        addDependency (part, inherit);
        models.set ("1", modelName, inherit, "$connected");
    }

    public void addDependencyFromLEMS (MNode part, String inherit)
    {
        addDependency (part, inherit);
        models.set ("1", modelName, inherit, "$lemsUses");
    }

    public void addDependency (MNode part, String inherit)
    {
        dependents.add (part);

        MNode component = models.child (modelName, inherit);
        if (component == null)
        {
            component = models.childOrCreate (modelName, inherit);
            component.set ("1", "$count");

            // Assume that local part names don't duplicate repo part names.
            // Thus, if there is a match in the repo that is also tagged as a NeuroML/LEMS part,
            // we are probably a proxy.
            // TODO: map name to neuroml parts
            if (AppData.models.child (inherit) != null)
            {
                // Setting our inherit line supports analysis in genericPart().
                component.set (inherit, "$inherit");
                // IDs will be filled during postprocessing.
            }
        }
        else
        {
            int count = component.getInt ("$count");
            component.set (count + 1, "$count");
        }
    }

    public void removeDependency (MNode part, String inherit)
    {
        dependents.remove (part);

        MNode component = models.child (modelName, inherit);
        if (component == null) return;
        int count = component.getInt ("$count") - 1;
        if (count > 0)
        {
            component.set (count, "$count");
        }
        else
        {
            component.clear ("$count");
            component.clear ("$connected");
            component.clear ("$lemsUses");
            // Don't clear $lems, as it indicates the type of part regardless of dependencies.
            if (component.size () == 0) models.clear (modelName, inherit);  // This is a proxy part created because a model is missing from the repo.
        }
    }

    /**
        Perform data manipulations that must wait until all nodes are read.
    **/
    public void postprocess ()
    {
        for (Entry<String,ComponentType> e : components.entrySet ()) e.getValue ().finish1 ();
        for (Entry<String,ComponentType> e : components.entrySet ()) e.getValue ().finish2 ();
        for (Entry<String,Cell>          e : cells     .entrySet ()) e.getValue ().finish ();
        for (Entry<String,Network>       e : networks  .entrySet ()) e.getValue ().finish1 ();
        for (Entry<String,Network>       e : networks  .entrySet ()) e.getValue ().finish2 ();
        for (Output                      o : outputs               ) o            .finish ();

        // (Obscure case) Find spike sources that reference a target synapse but did not get used.
        // Need to construct fused parts so they are functional on their own.
        List<String> kill = new ArrayList<String> ();
        for (MNode spikeSource : models.child (modelName))
        {
            String synapseName = spikeSource.get ("$metadata", "backend", "lems", "synapse");
            if (synapseName.isEmpty ()) continue;  // not a spike source
            spikeSource.clear ("$metadata", "backend", "lems", "synapse");  // this memo is no longer needed
            if (spikeSource.getInt ("$count") > 0) continue;  // It got used somewhere, so we're done.

            MNode synapse = models.child (modelName, synapseName);
            if (synapse == null) continue;

            MNode fused = new MVolatile ();
            fused.merge (synapse);  // Leave the original synapse part alone. Just duplicate it.
            fused.set (synapseName, "$metadata", "backend", "lems", "id");
            MNode A = fused.childOrCreate ("A");
            A.merge (spikeSource);
            A.set (spikeSource.key (), "$metadata", "backend", "lems", "id");

            removeDependency (spikeSource, spikeSource.get ("$inherit").replace ("\"", ""));
            spikeSource.clear ();
            spikeSource.merge (fused);
            addDependency (spikeSource, spikeSource.get ("$inherit").replace ("\"", ""));
            A = spikeSource.child ("A");
            addDependency (A, A.get ("$inherit").replace ("\"", ""));  // Don't do "FromConnection", because we've already injected the part.

            if (synapse.getInt ("$count") == 0  &&  synapse.child ("$lems") == null) kill.add (synapseName);
        }
        for (String k : kill) models.clear (modelName, k);

        // Select the prime model
        if (primaryModel.isEmpty ())
        {
            if (networks.size () == 1) primaryModel = networks.entrySet ().iterator ().next ().getValue ().id;
            else if (models.child (modelName, modelName) != null) primaryModel = modelName;  // A part has the same name as the overall input file, so probably the main payload.
            // Otherwise there is no prime model. Could pick one arbitrarily (with preference for higher-level parts).
            // It may be cleaner to keep them all bundled as subparts and let the user pull them out as needed.
        }

        // Resolve referenced parts
        //System.out.println (models);  // Most useful for debugging structure.
        while (dependents.size () > 0) resolve (dependents.iterator ().next ());

        // Move heavy-weight parts into separate models
        Iterator<MNode> it = models.child (modelName).iterator ();
        while (it.hasNext ())
        {
            MNode part = it.next ();
            String  key        = part.key ();
            int     count      = part.getInt ("$count");
            boolean lems       = part.child ("$lems") != null;
            boolean proxyFound = part.get ("$proxy").equals ("found");
            part.clear ("$count");
            part.clear ("$connected");
            part.clear ("$lems");
            part.clear ("$lemsUses");
            part.clear ("$proxy");
            if (count == 0  &&  lems  &&  ! key.equals (primaryModel)) count = -3;
            if (count < 0)
            {
                if (count == -3  &&  ! proxyFound) models.set (part, modelName + " " + key);
                if (count > -4) it.remove ();
            }
        }

        // Move primary model up to top level
        if (! primaryModel.isEmpty ())
        {
            MNode source = models.child (modelName, primaryModel);
            if (source == null) return;
            models.clear (modelName, primaryModel);
            for (MNode p : source)
            {
                MNode dest = models.childOrCreate (modelName, p.key ());
                dest.merge (p);
            }
            if (! primaryModel.equals (modelName)) models.set (primaryModel, modelName, "$metadata", "backend", "lems", "id");
        }

        if (models.child (modelName).size () == 0) models.clear (modelName);

        ExpressionParser.namedUnits = null;
    }

    public void resolve (MNode dependent)
    {
        dependents.remove (dependent);

        boolean isConnect = dependent.get ().contains ("connect(");
        boolean isChildrenType = false;
        MNode dparent = dependent.parent ();
        if (dparent != null)
        {
            String dkey = dparent.key ();
            if (dkey.equals ("children")  ||  dkey.equals ("attachments"))
            {
                dparent = dparent.parent ();
                isChildrenType =  dparent != null  &&  dparent.key ().equals ("lems");
            }
        }
        String childrenExternalName = "";

        String dependentInherit = dependent.get ("$inherit");
        if (dependentInherit.isEmpty ())
        {
            dependentInherit = dependent.get ();
            if (isChildrenType)
            {
                String[] children = dependentInherit.split (",");
                dependentInherit = children[0];
                if (children.length > 1) childrenExternalName = children[1];
            }
            else if (isConnect)  // An explicit connection will have a direct value, while an abstract connection will have "connect(part name)".
            {
                dependentInherit = dependentInherit.replace ("connect(", "");
                dependentInherit = dependentInherit.replace (")",        "");
            }
        }

        String[]     sourceNames = dependentInherit.split (",");
        List<String> sourceIDs   = Arrays.asList (dependent.get ("$inherit", "$metadata", "id").split (",", -1));
        while (sourceIDs.size () < sourceNames.length) sourceIDs.add ("");
        for (int sourceIndex = 0; sourceIndex < sourceNames.length; sourceIndex++)
        {
            String sourceName = sourceNames[sourceIndex].replace ("\"", "");
            MNode source = models.child (modelName, sourceName);
            if (source == null) continue;

            resolveChildren (source);  // Ensure that the source part has no unresolved dependencies of its own before using it.

            boolean proxy;
            if (source.child ("$proxy") == null)
            {
                proxy = true;
                String inherit = source.key ();
                for (MNode c : source)
                {
                    String key = c.key ();
                    if (key.equals ("$count"    )) continue;
                    if (key.equals ("$connected")) continue;
                    if (key.equals ("$lems"     )) continue;
                    if (key.equals ("$lemsUses" )) continue;
                    if (key.equals ("$inherit")  &&  c.get ().replace ("\"", "").equals (inherit)) continue;  // Inheriting our own name is characteristic of being a proxy.
                    proxy = false;  // key is something other than our temporary special keys, so not shallow
                    break;
                }
                if (proxy)
                {
                    source.set (inherit, "$inherit");  // proxies only have single inheritance
                    MNode parent = AppData.models.child (inherit);
                    if (parent == null)
                    {
                        source.set ("1", "$proxy");
                    }
                    else
                    {
                        source.set ("found", "$proxy");
                        String id = parent.get ("$metadata", "id");
                        if (! id.isEmpty ()) source.set (id, "$inherit", "$metadata", "id");
                    }
                }
                else
                {
                    source.set ("0", "$proxy");
                }
            }
            else
            {
                proxy = ! source.get ("$proxy").equals ("0");
            }

            // Triage
            // -1 == source has exactly one user. Merge and delete immediately.
            // -2 == source is lightweight, but has multiple users. Merge and wait to delete.
            // -3 == source is heavyweight. Keep reference and move out to independent model.
            // -4 == source is the endpoint of a connection. Leave in place.
            // Note that a "lightweight" part could also be a proxy, in which case it is an external reference.
            // A proxy is configured (above) to inherit the external model, so when the part is merged,
            // the dependent will end up referring directly to the external model.
            int count = source.getInt ("$count");
            boolean connected = source.child ("$connected") != null;
            boolean lems      = source.child ("$lems"     ) != null;
            boolean lemsUses  = source.child ("$lemsUses" ) != null;
            if (count > 0)  // triage is necessary
            {
                if (lemsUses  &&  ! proxy)
                {
                    // Anything a LEMS component depends on must be even more abstract, and thus should be an independent model.
                    count = -3;
                }
                else if (connected)
                {
                    if (dependent.getOrDefault (1, "$n") == 1  &&  count == 1) count = -1;  // One dependent, with only one connection target, so OK to embed.
                    else                                                       count = -4;  // Otherwise, the source should be separate from the connection part.
                }
                else if (count == 1)
                {
                    count = -1;
                }
                else  // count > 1 and not connected, so could be moved out to independent model
                {
                    // Criterion: If a part has subparts, then it is heavy-weight and should be moved out.
                    // A part that merely sets some parameters on an inherited model is lightweight, and
                    // should simply be merged everywhere it is used.
                    // A LEMS part presumably does more than just set parameters. When it has multiple users,
                    // it should always be treated as heavy-weight.
                    boolean heavy = lems;
                    if (! lems)
                    {
                        for (MNode s : source)
                        {
                            if (MPart.isPart (s))
                            {
                                heavy = true;
                                break;
                            }
                        }
                    }
                    if (heavy) count = -3;
                    else       count = -2;
                }
                source.set (count, "$count");
                if (count >= -3  &&  lems) source.set (source.key (), "$metadata", "backend", "lems", "name");  // Save original ComponentType name.
                if (count == -3)
                {
                    MNode id = source.childOrCreate ("$metadata", "id");
                    if (id.get ().isEmpty ()) id.set (AddDoc.generateID ());
                }
            }
            if (count == -1  ||  count == -2)
            {
                if (! isChildrenType  &&  ! isConnect)  // Those two node types don't receive part injection, and don't change the name they use to reference the part.
                {
                    dependent.set ("");

                    sourceNames[sourceIndex] = source.get ("$inherit");
                    String inherit = sourceNames[0];
                    for (int i = 1; i < sourceNames.length; i++) inherit += "," + sourceNames[i];
                    if (inherit.isEmpty ())  // This can happen if source is a Cell, which currently lacks a base class since it is merely a container for segments.
                    {
                        dependent.clear ("$inherit");
                    }
                    else
                    {
                        dependent.set (inherit, "$inherit");

                        sourceIDs.set (sourceIndex, source.get ("$inherit", "$metadata", "id"));  // Assume single-inheritance in sources
                        String id = sourceIDs.get (0);
                        for (int i = 1; i < sourceIDs.size (); i++) id += "," + sourceIDs.get (i);
                        dependent.set (id, "$inherit", "$metadata", "id");
                    }

                    for (MNode n : source)
                    {
                        String key = n.key ();
                        if (key.equals ("$count"    )) continue;
                        if (key.equals ("$connected")) continue;
                        if (key.equals ("$lems"     )) continue;
                        if (key.equals ("$lemsUses" )) continue;
                        if (key.equals ("$proxy"    )) continue;
                        if (key.equals ("$inherit"  )) continue;  // already handled above

                        MNode c = dependent.child (key);
                        if (c == null) dependent.set (n, key);
                        else           c.mergeUnder (n);
                    }

                    if (! proxy  &&  ! lems) dependent.set (sourceName, "$metadata", "backend", "lems", "id");
                }
                if (count == -1) models.clear (modelName, sourceName);
            }
            else if (count == -3)
            {
                String inherit = modelName + " " + sourceName;
                String id      = source.get ("$metadata", "id");
                if (isChildrenType)
                {
                    if (! childrenExternalName.isEmpty ()) inherit += "," + childrenExternalName;
                    dependent.set (inherit);
                    dependent.set (id, "id");
                }
                else if (isConnect)
                {
                    dependent.set ("connect(" + inherit + ")");
                    dependent.set (id, "$metadata", "id");
                }
                else
                {
                    sourceNames[sourceIndex] = inherit;
                    sourceIDs.set (sourceIndex, id);

                    inherit = sourceNames[0];
                    for (int i = 1; i < sourceNames.length; i++) inherit += "," + sourceNames[i];
                    dependent.set (inherit, "$inherit");

                    id = sourceIDs.get (0);
                    for (int i = 1; i < sourceIDs.size (); i++) id += "," + sourceIDs.get (0);
                    dependent.set (id, "$inherit", "$metadata", "id");
                }
            }
        }
    }

    public void resolveChildren (MNode source)
    {
        List<MNode> children = new ArrayList<MNode> (source.size ());
        for (MNode c : source) children.add (c);  // This level of indirection is necessary because the resolution process can change the contents of source while we are iterating over it.
        for (MNode c : children) resolveChildren (c);
        if (dependents.contains (source)) resolve (source);
    }

    public void ionChannel (Node node)
    {
        String id      = getAttribute (node, "id");
        String type    = getAttribute (node, "type");
        String species = getAttribute (node, "species");

        String inherit;
        if (type.isEmpty ()) inherit = node.getNodeName ();
        else                 inherit = type;
        NameMap nameMap = partMap.importMap (inherit);
        inherit = nameMap.internal;

        MNode part = models.childOrCreate (modelName, id);  // Expect to always create this part rather than fetch an existing child.
        part.set (inherit, "$inherit");
        addDependency (part, inherit);
        if (! species.isEmpty ()) part.set (species, "$metadata", "species");

        addAttributes (node, part, nameMap, "id", "type", "species");

        for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
        {
            if (child.getNodeType () != Node.ELEMENT_NODE) continue;
            String name = child.getNodeName ();
            if      (name.startsWith ("q10" )) q10         (child, part);
            else if (name.startsWith ("gate")) gate        (child, part);
            else                               genericPart (child, part);
        }
    }

    public void q10 (Node node, MNode container)
    {
        String id = "Q10Parameters";
        int suffix = 2;
        while (container.child (id) != null) id = "Q10Parameters" + suffix++;  // This seems pointless, but the NeuroML XSD says the number of elements is unbounded.

        MNode part = container.childOrCreate (id);
        NameMap nameMap = partMap.importMap ("baseQ10Settings");  // This isn't the correct name for use with ion channel, but it will still work.
        String inherit = nameMap.internal;
        part.set (inherit, "$inherit");
        addDependency (part, inherit);

        NamedNodeMap attributes = node.getAttributes ();
        int count = attributes.getLength ();
        for (int i = 0; i < count; i++)
        {
            Node a = attributes.item (i);
            String name = a.getNodeName ();
            if (name.equals ("type")) continue;  // probably switches between fixed and exponential, but there is no example/guidance on its use

            String value = biophysicalUnits (a.getNodeValue ());
            if (name.equals ("fixedQ10")) value = "*" + value;

            name = nameMap.importName (name);
            part.set (value, name);
        }
    }

    public void gate (Node node, MNode container)
    {
        String id   = getAttribute (node, "id");
        String type = getAttribute (node, "type");
        boolean instantaneous = type.contains ("Instantaneous");
        String inherit;
        if (type.isEmpty ()) inherit = node.getNodeName ();
        else                 inherit = type;
        NameMap nameMap = partMap.importMap (inherit);
        inherit = nameMap.internal;
        MNode part = container.childOrCreate (id);
        part.set (inherit, "$inherit");
        addDependency (part, inherit);

        addAttributes (node, part, nameMap, "id", "type");

        for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
        {
            if (child.getNodeType () != Node.ELEMENT_NODE) continue;
            String name = child.getNodeName ();
            switch (name)
            {
                case "subGate":
                    gate (child, part);
                    break;
                case "notes":
                    part.set (getText (child), "$metadata", "notes");
                    break;
                case "q10Settings":
                    q10 (child, part);
                    break;
                case "openState":
                case "closedState":
                    id = getAttribute (child, "id");
                    part.set ("Kinetic State",                       id, "$inherit");
                    part.set (name.equals ("openState") ? "1" : "0", id, "relativeConductance");
                    break;
                case "forwardTransition":
                case "reverseTransition":
                case "tauInfTransition":
                case "vHalfTransition":
                    transition (child, part);
                    break;
                default:
                    rate (child, part, false, instantaneous);
            }
        }
    }

    public void transition (Node node, MNode container)
    {
        String id = getAttribute (node, "id");
        MNode part = container.childOrCreate (id);
        NameMap nameMap = partMap.importMap (node.getNodeName ());
        part.set (nameMap.internal, "$inherit");

        addAttributes (node, part, nameMap, "id");

        for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
        {
            if (child.getNodeType () == Node.ELEMENT_NODE) rate (child, part, true, false);
        }
    }

    public void rate (Node node, MNode container, boolean KS, boolean instantaneous)
    {
        String name = node.getNodeName ();
        String var = "";
        if (name.equals ("forwardRate"))
        {
            if (KS) var = "forward";
            else    var = "α";
        }
        else if (name.equals ("reverseRate"))
        {
            if (KS) var = "reverse";
            else    var = "β";
        }
        else if (name.equals ("steadyState"))
        {
            if (instantaneous) var = "q";  // Because the Gate model is written so that inf only initializes q once, while "instantaneous" requires continually changing q.
            else               var = "inf";
        }
        else if (name.equals ("timeCourse"))
        {
            if (KS) var = "τ";
            else    var = "τUnscaled";

            // If the node defines tau, then that overrides any other HHVariable parameters.
            String tau = getAttribute (node, "tau");
            if (! tau.isEmpty ())
            {
                container.set (biophysicalUnits (tau), var);
                return;
            }
        }

        String inherit = getAttribute (node, "type");
        MNode part = container.childOrCreate (name);
        NameMap nameMap = partMap.importMap (inherit);
        inherit = nameMap.internal;
        part.set (inherit, "$inherit");
        addDependency (part, inherit);

        addAttributes (node, part, nameMap, "type");
        if (! var.isEmpty ()) container.set (":" + name + ".x", var);
    }

    public void blockingPlasticSynapse (Node node)
    {
        String id = getAttribute (node, "id");
        MNode part = models.childOrCreate (modelName, id);
        NameMap nameMap = partMap.importMap (node.getNodeName ());
        part.set (nameMap.internal, "$inherit");

        addAttributes (node, part, nameMap, "id");

        for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
        {
            if (child.getNodeType () != Node.ELEMENT_NODE) continue;
            MNode c = genericPart (child, part);
            String name = child.getNodeName ();
            if (name.endsWith ("Mechanism"))  // This handles both block and plasticity mechanisms.
            {
                String species = c.get ("species");
                c.clear ("species");
                if (! species.isEmpty ()) c.set (species, "$metadata", "species");
            }
        }
    }

    public void concentrationModel (Node node)
    {
        MNode part = genericPart (node, models.child (modelName));
        part.clear ("ion");  // According to comment in NeuroML.xsd, this field should be the same as id.
        String inherit = node.getNodeName ();
        if (inherit.endsWith ("Traub"))  // Gets mapped into base concentration model. Here, we fix up the parameters so it will work.
        {
            String beta = part.get ("beta");
            String phi  = part.get ("phi");
            part.clear ("beta");
            part.clear ("phi");
            part.set ("1/" + beta,   "tau");
            part.set (phi + "*1e-9", "rho");
        }
    }

    /**
        Auxiliary structures in cell MNode:
        $group
            each child is a named group, complete with equation set that will become a segment directly under cell
                special group names:
                    [all] is the group of all segments
                    [segment index] is an individual segment
                $G = column in matrix G
                $inhomo
                    inhomogeneousParameter id
                        {variable name before} = {variable name after} -- remap variable name because there is a collision in the segment equations
                $parent -- temporary used when connecting groups
                $properties -- number of properties that reference this group
        $properties
            {arbitrary integer index} = {group name} -- allows multiple properties to be associated with same group
                single child, which is a complete equation set implementing the property

        G matrix     G = MO
            columns are groups as detected in original input file
                also includes [segment index] groups created for segment-specific properties
                but does not include [all]
            rows are segment indices
        M matrix  -- "membership"
            columns are groups with unique combinations of properties
            rowas are segment indices
        O matrix -- "optimized"
            columns are groups as detected in original input file
            rows are groups with unique combinations of properties
    **/
    public class Cell
    {
        String               id;
        MNode                cell;
        List<Node>           cellularProperties = new ArrayList<Node> ();
        Map<Integer,Segment> segments           = new HashMap<Integer,Segment> ();
        MatrixBoolean        G                  = new MatrixBoolean ();
        MatrixBoolean        O                  = new MatrixBoolean ();
        MatrixBoolean        M                  = new MatrixBoolean ();
        Map<Integer,String>  groupIndex         = new HashMap<Integer,String> ();

        public Cell (Node node)
        {
            id   = getAttribute (node, "id");
            cell = models.childOrCreate (modelName, id);
            cell.set ("cell", "$metadata", "backend", "lems", "part");
            // At present, cell is merely a container. It inherits nothing.
            // Every cell must have at least one segment to be useful.

            // Include a top-level morphology node
            // Must come first so all morphology sections are completed before any biophysics sections.
            String include = getAttribute (node, "morphology");
            if (! include.isEmpty ())
            {
                Node child = morphologies.get (include);
                if (child != null) morphology (child);
            }

            // Direct children
            for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
            {
                if (child.getNodeType () != Node.ELEMENT_NODE) continue;
                switch (child.getNodeName ())
                {
                    case "morphology"           : morphology            (child); break;
                    case "biophysicalProperties": biophysicalProperties (child); break;
                    default                     : genericPart           (child, cell);  // Also handles generic metadata elements.
                }
            }

            // Include top-level biophysics node
            // Must come last
            include = getAttribute (node, "biophysicalProperties");
            if (! include.isEmpty ())
            {
                Node child = biophysics.get (include);
                if (child != null) biophysicalProperties (child);
            }
        }

        /**
            Add the contents of an intracellularProperties or extracellularProperties node.
        **/
        public void addCellularProperties (Node node)
        {
            for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
            {
                if (child.getNodeType () == Node.ELEMENT_NODE)
                {
                    cellularProperties.add (child);
                }
            }
        }
  
        public void morphology (Node node)
        {
            for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
            {
                if (child.getNodeType () != Node.ELEMENT_NODE) continue;
                switch (child.getNodeName ())
                {
                    case "segment":
                        Segment s = new Segment (child);
                        segments.put (s.id, s);
                        break;
                    case "segmentGroup":
                        segmentGroup (child);
                        break;
                }
            }
        }

        public void segmentGroup (Node node)
        {
            MNode groups = cell.childOrCreate ("$group");
            int c = groups.size ();
            String groupName = getAttribute (node, "id");
            MNode part = groups.childOrCreate (groupName);
            part.set (c, "$G");
            groupIndex.put (c, groupName);

            String neuroLexID = getAttribute (node, "neuroLexId");
            if (! neuroLexID.isEmpty ())
            {
                MNode properties = cell.childOrCreate ("$properties");
                MNode property = properties.set (groupName, properties.size ());
                property.set (neuroLexID, "$metadata", "neuroLexID");
                MNode count = cell.child ("$group", groupName, "$properties");
                if (count == null) cell.set ("1", "$group", groupName, "$properties");
                else               count.set (count.getInt () + 1);
            }

            for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
            {
                if (child.getNodeType () != Node.ELEMENT_NODE) continue;
                switch (child.getNodeName ())
                {
                    case "member":
                        G.set (getAttribute (child, "segment", 0), c);
                        break;
                    case "include":
                        String include = part.get ("$include");  // not $inherit
                        if (! include.isEmpty ()) include += ",";
                        include += getAttribute (child, "segmentGroup");
                        part.set (include, "$include");
                        break;
                    case "path":
                    case "subTree":  // TODO: not sure of the semantics of subTree
                        segmentPath (child, part);
                        break;
                    case "inhomogeneousParameter":
                        inhomogeneousParameter (child, part);
                        break;
                }
            }
        }

        public void segmentPath (Node node, MNode group)
        {
            int from = -1;
            int to   = -1;
            for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
            {
                if (child.getNodeType () != Node.ELEMENT_NODE) continue;
                switch (child.getNodeName ())
                {
                    case "from": from = getAttribute (child, "segment", 0); break; 
                    case "to"  : to   = getAttribute (child, "segment", 0); break; 
                }
            }
            if (from >= 0)
            {
                MNode paths = group.childOrCreate ("$paths");
                int index = paths.size ();
                if (to >= 0) paths.set (from + "," + to, index);
                else         paths.set (from,            index);
            }
        }

        public void applyPaths (MNode group)
        {
            MNode paths = group.child ("$paths");
            if (paths == null) return;

            int c = group.getInt ("$G");
            for (MNode p : paths)
            {
                String[] pieces = p.get ().split (",");
                if (pieces.length == 1)  // subtree
                {
                    int from = Integer.valueOf (pieces[0]);
                    applyPath (segments.get (from), c);
                }
                else  // path
                {
                    int from = Integer.valueOf (pieces[0]);
                    int to   = Integer.valueOf (pieces[1]);
                    if (from > to)
                    {
                        for (int r = to; r <= from; r++) G.set (r, c);
                    }
                    else
                    {
                        for (int r = from; r <= to; r++) G.set (r, c);
                    }
                }
            }

            group.clear ("$paths");
        }

        public void applyPath (Segment s, int c)
        {
            G.set (s.id, c);
            for (Segment t : s.children) applyPath (t, c);
        }

        public void includeGroups (MNode groups, MNode g)
        {
            String include = g.get ("$include");
            if (include.isEmpty ()) return;
            g.clear ("$include");

            int columnG = g.getInt ("$G");
            String[] pieces = include.split (",");
            for (String i : pieces)
            {
                MNode p = groups.child (i);
                if (p == null) continue;
                includeGroups (groups, p);
                // Merge segment members
                int columnP = p.getInt ("$G");
                G.OR (columnG, columnP);
                // Groups require further analysis before we are ready to merge properties.
            }
        }

        public void inhomogeneousParameter (Node node, MNode group)
        {
            String id       = getAttribute (node, "id");
            String variable = getAttribute (node, "variable");

            // Ensure there is no name collision
            NameMap nameMap = partMap.importMap ("segment");
            MNode parent = AppData.models.child (nameMap.internal);
            String v = variable;
            int suffix = 2;
            while (parent.child (v) != null) v = variable + suffix++;
            if (! v.equals (variable)) group.set (v, "$inhomo", id, variable);  // When processing inhomogeneousParameter with id, remap from variable to v

            // Collect scaling parameters
            String translationStart = "0";
            String normalizationEnd = "1";
            for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
            {
                if (child.getNodeType () != Node.ELEMENT_NODE) continue;
                switch (child.getNodeName ())
                {
                    case "proximal": translationStart = getAttribute (child, "translationStart"); break;
                    case "distal"  : normalizationEnd = getAttribute (child, "normalizationEnd"); break;
                }
            }
            group.set (normalizationEnd, v, "$metadata", "backend", "lems", "a");
            group.set (translationStart, v, "$metadata", "backend", "lems", "b");
        }

        public void biophysicalProperties (Node node)
        {
            for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
            {
                if (child.getNodeType () != Node.ELEMENT_NODE) continue;
                String name = child.getNodeName ();
                if (name.startsWith ("membrane"))
                {
                    membraneProperties (child);
                }
                else if (name.endsWith ("Properties"))
                {
                    addCellularProperties (child);
                }
            }
        }

        public void membraneProperties (Node node)
        {
            for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
            {
                if (child.getNodeType () != Node.ELEMENT_NODE) continue;

                MNode property = allocateProperty (child);
                String name = child.getNodeName ();
                if (name.startsWith ("channel"))
                {
                    channel (child, property);
                }
                else
                {
                    String value = biophysicalUnits (getAttribute (child, "value"));
                    switch (name)
                    {
                        case "spikeThresh":
                            property.set (value, "Vspike");
                            break;
                        case "specificCapacitance":
                            property.set (value, "Cspecific");
                            break;
                        case "initMembPotential":
                            property.set (value + "@$init", "V");
                            break;
                    }
                }
            }
        }

        public MNode allocateProperty (Node node)
        {
            MNode  properties = cell.childOrCreate ("$properties");
            String id         = getAttribute (node, "id");
            String group      = getAttribute (node, "segmentGroup");
            if (group.isEmpty ())
            {
                group = getAttribute (node, "segment");
                if (group.isEmpty ())
                {
                    group = "[all]";
                }
                else
                {
                    int r = Integer.parseInt (group);
                    group = "[" + group + "]";  // proper name will be assigned later, during post-processing
                    if (cell.child ("$group", group) == null)
                    {
                        MNode groups = cell.childOrCreate ("$group");
                        int c = groups.size ();
                        G.set (r, c);
                        groups.set (c, group, "$G");
                        groupIndex.put (c, group);
                    }
                }
            }

            MNode result = properties.set (group, properties.size ());
            MNode count = cell.child ("$group", group, "$properties");
            if (count == null) cell.set ("1", "$group", group, "$properties");
            else               count.set (count.getInt () + 1);

            if (id.isEmpty ()) return result;

            // Create a subpart with the given name
            MNode subpart = result.childOrCreate (id);
            addAttributes (node, subpart, new NameMap (""), "id", "segment", "segmentGroup", "value", "ion");
            return result;
        }

        public void channel (Node node, MNode property)
        {
            MNode subpart = property.iterator ().next ();  // retrieve the first (and only) subpart
            ImportJob.this.channel (node, subpart, false);

            String parentGroup = property.get ();
            Map<String,MNode> groupProperty = new TreeMap<String,MNode> ();
            groupProperty.put (parentGroup, property);
            for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
            {
                if (child.getNodeType () != Node.ELEMENT_NODE  ||  ! child.getNodeName ().equals ("variableParameter")) continue;
                String parameter    = getAttribute (child, "parameter");
                String segmentGroup = getAttribute (child, "segmentGroup");

                MNode clone = property;
                if (! segmentGroup.equals (parentGroup))
                {
                    if (parentGroup.equals ("[all]"))  // segment group was not or should not be assigned at level of channel part
                    {
                        groupProperty.remove (parentGroup);
                        groupProperty.put (segmentGroup, property);
                        property.set (segmentGroup);  // assumes that the target group will exist when needed
                        parentGroup = segmentGroup;
                    }
                    else  // different than previously assigned group, so may need to clone the property
                    {
                        clone = groupProperty.get (segmentGroup);
                        if (clone == null)
                        {
                            MNode properties = cell.child ("$properties");
                            clone = properties.set (segmentGroup, properties.size ());
                            clone.merge (property);
                            clone.set (segmentGroup);
                            groupProperty.put (segmentGroup, clone);
                        }
                    }
                    subpart = clone.iterator ().next ();
                    variableParameter (child, subpart, parameter, segmentGroup);
                }
            }
        }

        public void variableParameter (Node node, MNode subpart, String parameter, String segmentGroup)
        {
            String inherit = subpart.get ("$inherit").replace ("\"", "");
            NameMap nameMap = exportMap (inherit);  // could also do importMap() on lems.part
            parameter = nameMap.importName (parameter);

            for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
            {
                if (child.getNodeType () == Node.ELEMENT_NODE  &&  child.getNodeName ().equals ("inhomogeneousValue"))
                {
                    String id    = getAttribute (child, "inhomogeneousParameter");
                    String value = getAttribute (child, "value");

                    MNode remap = cell.child ("$group", segmentGroup, "$inhomo", id);  // We can safely assume that segmentGroup has already been created, and thus has all necessary $inhomo entries.
                    if (remap != null)
                    {
                        remap = remap.iterator ().next ();
                        final String before = remap.key ();
                        final String after  = remap.get ();
                        try
                        {
                            Operator op = Operator.parse (value);
                            op.transform (new Transformer ()
                            {
                                public Operator transform (Operator op)
                                {
                                    if (op instanceof AccessVariable)
                                    {
                                        AccessVariable av = (AccessVariable) op;
                                        if (av.name.equals (before)) av.name = after;
                                    }
                                    return null;
                                }
                            });
                            value = op.render ().replace (" ", "");
                        }
                        catch (Exception e) {}
                    }
                    subpart.set (value, parameter);
                }
            }
        }

        public void finish ()
        {
            // Add intra- and extra-cellular properties
            // TODO: Does it matter whether it is intra or extra? It seems all Species have internal and external concentrations.
            for (Node p : cellularProperties)
            {
                MNode property = allocateProperty (p);
                String name = p.getNodeName ();
                switch (name)
                {
                    case "species":
                        MNode subpart = property.iterator ().next ();
                        String concentrationModel = subpart.get ("concentrationModel");
                        subpart.clear ("concentrationModel");
                        subpart.set (concentrationModel, "$inherit");  // Dependency will be tagged when this property is added to segments.
                        NameMap nameMap = exportMap (concentrationModel);
                        remap (subpart, nameMap);
                        if (subpart.child ("z") == null)
                        {
                            int z = 1;  // The most common charge on an ion.
                            String ion = subpart.key ();
                            switch (ion)
                            {
                                case "ca":
                                case "ca2":
                                case "mg":
                                    z = 2;
                                    break;
                                case "cl":
                                    z = -1;
                                    break;
                            }
                            subpart.set (z, "z");
                        }
                        break;
                    case "resistivity":
                        property.set (biophysicalUnits (getAttribute (p, "value")), "ρ");
                        break;
                }
            }

            // Finalize segment structures
            for (Entry<Integer,Segment> e : segments.entrySet ())
            {
                Segment s = e.getValue ();
                if (s.parentID >= 0  &&  s.parent == null)
                {
                    s.parent = segments.get (s.parentID);
                    s.parent.children.add (s);
                }
            }
            for (Entry<Integer,Segment> e : segments.entrySet ()) e.getValue ().resolveProximal ();
            MNode groups = cell.child ("$group");
            if (groups != null)
            {
                for (MNode g : groups) applyPaths (g);
                for (MNode g : groups) includeGroups (groups, g);
            }

            // If no segment groups have been defined, create a single catch-all group.
            if (groups == null  &&  segments.size () > 0)
            {
                int smallestID = Integer.MAX_VALUE;
                for (Entry<Integer,Segment> e : segments.entrySet ())
                {
                    int ID = Integer.valueOf (e.getKey ());
                    smallestID = Math.min (ID, smallestID);
                    G.set (ID, 0);
                }
                String name = segments.get (smallestID).name;  // Name the group after the first segment, if it has a name.
                if (name.isEmpty ()) name = "segments";
                cell.set (0, "$group", name, "$G");
                groups = cell.child ("$group");
                groupIndex.put (0, name);
            }

            MNode properties = cell.child ("$properties");
            if (properties != null)
            {
                for (MNode p : properties)
                {
                    String groupName = p.get ();
                    if (groupName.startsWith ("[")  &&  ! groupName.equals ("[all]"))
                    {
                        // Determine better name for segment-specific part.
                        int length = groupName.length ();
                        int r = Integer.parseInt (groupName.substring (1, length-1));
                        int currentColumn = cell.getInt ("$group", groupName, "$G");
                        String newName = segments.get (r).name;
                        if (newName.isEmpty ()  ||  cell.child ("$group", newName) != null)
                        {
                            int bestColumn = currentColumn;
                            int bestCount  = 1;
                            for (int c = 0; c < G.columns (); c++)
                            {
                                if (c == currentColumn) continue;
                                if (! G.get (r, c)) continue;

                                int count = G.columnNorm0 (c);
                                if (count > bestCount)
                                {
                                    bestColumn = c;
                                    bestCount  = count;
                                }
                            }
                            if (bestColumn == currentColumn) newName = "segment_" + r;
                            else                             newName = groupIndex.get (bestColumn) + "_" + r;
                        }
                        cell.child ("$group").move (groupName, newName);
                        groupIndex.put (currentColumn, newName);
                        p.set (newName);
                        groupName = newName;
                    }

                    // Distribute properties to original segment groups
                    if (groupName.equals ("[all]"))
                    {
                        for (MNode part : cell.child ("$group"))
                        {
                            part.merge (p);
                            part.set ("");
                        }
                    }
                    else
                    {
                        MNode part = cell.child ("$group", groupName);
                        if (part != null)  // Missing group is a symptom of an ill-formed file.
                        {
                            part.merge (p);
                            part.set ("");
                        }
                    }
                }
            }


            // Create subparts for all combinations of parameters.
            // Each subpart will hold a set of segments that is mutually exclusive of the other sets.
            // Ideally, the sets exactly match the segment groups, but in general there may be more sets.
            // If a set exactly matches an original segment group, it gets that group's name.
            // Then, the set that has the largest overlap with an unclaimed segment group, without exceeding it, gets its name.
            // All remaining sets get a generated name.

            MatrixBoolean mask = new MatrixBoolean ();
            for (MNode g : cell.child ("$group"))
            {
                mask.set (0, g.getInt ("$G"), g.getInt ("$properties") > 0);
            }
            G.foldRows (mask, M, O);

            // Scan for exact matches
            int columnsG = G.columns ();
            int columnsM = M.columns ();
            Set<Integer> newIndices = new HashSet<Integer>  (columnsM);
            Map<Integer,String> finalNames = new HashMap<Integer,String> (columnsM);
            for (int i = 0; i < columnsM; i++)
            {
                MatrixBoolean A = M.column (i);
                boolean found = false;
                for (int j = 0; j < columnsG; j++)
                {
                    if (A.equals (G.column (j)))
                    {
                        found = true;
                        String name = groupIndex.get (j);  // maps index to group name
                        cell.childOrCreate (name);
                        finalNames.put (i, name);
                        break;
                    }
                }
                if (! found) newIndices.add (i);
            }

            // Scan for largest overlaps
            Iterator<Integer> it = newIndices.iterator ();
            while (it.hasNext ())
            {
                int i = it.next ();
                boolean[] columnA = M.data.get (i);
                int bestCount = 0;
                int bestIndex = -1;
                for (int j = 0; j < columnsG; j++)
                {
                    int count = 0;
                    boolean subset = true;
                    boolean[] columnG = G.data.get (j);
                    int rows = Math.min (columnA.length, columnG.length);
                    int r = 0;
                    for (; r < rows; r++)
                    {
                        if (columnA[r])
                        {
                            if (columnG[r])
                            {
                                count++;
                            }
                            else
                            {
                                subset = false;
                                break;
                            }
                        }
                    }
                    for (; r < columnA.length  &&  subset; r++) if (columnA[r]) subset = false;
                    if (subset  &&  count > bestCount)
                    {
                        bestCount = count;
                        bestIndex = j;
                    }
                }
                if (bestCount > 0)
                {
                    it.remove ();

                    String name = groupIndex.get (bestIndex);
                    cell.childOrCreate (name);
                    finalNames.put (i, name);
                }
            }

            // Build concatenated parts
            for (int i : newIndices)
            {
                int suffix = i;
                String name = "Segments";
                while (finalNames.values ().contains (name)) name = "Segments" + suffix++;
                cell.childOrCreate (name);
                finalNames.put (i, name);
            }

            // Add segments and properties to the parts
            String inheritSegment = partMap.importName ("segment");
            for (Entry<Integer,String> e : finalNames.entrySet ())
            {
                int c = e.getKey ();  // column of M, the mapping from segments to this new group
                String currentName = e.getValue ();
                MNode part = cell.child (currentName);

                // Rename any part with a single segment to the name of that segment, provided it is unique.
                if (M.columnNorm0 (c) == 1)
                {
                    int r = M.firstNonzeroRow (c);
                    String newName = segments.get (r).name;
                    // Note that all named parts have already been added to cell, so we can do a lookup there for uniqueness.
                    if (! newName.isEmpty ()  &&  ! newName.equals (currentName)  &&  cell.child (newName) == null)
                    {
                        cell.move (currentName, newName);
                        e.setValue (newName);
                        currentName = newName;
                    }
                }

                // Merge original groups into new part
                //   rows of O are new parts (columns of M); columns of O are original segment groups
                for (int i = 0; i < columnsG; i++)
                {
                    if (! O.get (c, i)) continue;
                    String groupName = groupIndex.get (i);  // Name from the original set of groups, not the new groups
                    MNode group = cell.child ("$group", groupName);

                    // neuroLexID is a minor property, but it takes some heavy code to process correctly
                    List<String> neuroLexIDs = Arrays.asList (part.get ("$metadata", "neuroLexID").split (","));
                    if (neuroLexIDs.size () == 1  &&  neuroLexIDs.get (0).isEmpty ()) neuroLexIDs = new ArrayList<String> ();

                    // This is the important code
                    part.merge (group);

                    // merge nlid
                    String neuroLexID = part.get ("$metadata", "neuroLexID");
                    if (! neuroLexID.isEmpty ()  &&  ! neuroLexIDs.contains (neuroLexID))
                    {
                        for (String nlid : neuroLexIDs) neuroLexID += "," + nlid;
                        part.set (neuroLexID, "$metadata", "neuroLexID");
                    }
                }
                part.set (inheritSegment, "$inherit");
                addDependency (part, inheritSegment);
                for (MNode property : part)
                {
                    String inherit = property.get ("$inherit").replace ("\"", "");
                    if (! inherit.isEmpty ()) addDependency (property, inherit);
                    for (MNode m : property)
                    {
                        inherit = m.get ("$inherit").replace ("\"", "");
                        if (inherit.isEmpty ()) continue;
                        addDependency (m, inherit);
                    }
                }

                // Collect inhomogeneous variables
                // TODO: What is the correct interpretation of translationStart and normalizationEnd?
                // Here we assume they are a linear transformation of the path length: variable = pathLength * normalizatonEnd + translationStart
                String pathLength = "";
                for (MNode v : part)
                {
                    if (v.child ("$metadata", "backend", "lems", "a") == null) continue;
                    if (pathLength.isEmpty ())
                    {
                        pathLength = "pathLength";
                        // There is only one variable that actually stores path length, but it
                        // is possible for a model to define the same name for some other purpose.
                        int count = 2;
                        while (part.child (pathLength) != null) pathLength = "pathLength" + count++;
                    }
                    double a = v.getDouble ("$metadata", "backend", "lems", "a");
                    double b = v.getDouble ("$metadata", "backend", "lems", "b");
                    String value = pathLength;
                    if (a != 1) value += "*" + a;
                    if (b != 0) value += "+" + b;
                    v.set (value);
                }

                // Link ion currents to concentration models
                for (MNode v : part)
                {
                    String inherit = v.get ("$inherit").replace ("\"", "");
                    String species = models.get (modelName, inherit, "$metadata", "species");
                    if (species.isEmpty ()) continue;
                    if (part.child (species) == null) continue;  // Don't add connection if the user has not provided the concentration model.
                    v.set (species, "c");  // connection to peer object, generally singleton to singleton
                    v.set ("+I",    "c.I");
                }

                // Add segments
                int n = M.columnNorm0 (c);
                if (n > 1) part.set (n, "$n");
                List<String> neuroLexIDs = Arrays.asList (part.get ("$metadata", "neuroLexID").split (","));
                if (neuroLexIDs.size () == 1  &&  neuroLexIDs.get (0).isEmpty ()) neuroLexIDs = new ArrayList<String> ();
                int index = 0;
                for (int r = 0; r < M.rows (); r++)
                {
                    if (! M.get (r, c)) continue;
                    Segment s = segments.get (r);

                    if (! s.neuroLexID.isEmpty ()  &&  ! neuroLexIDs.contains (s.neuroLexID))
                    {
                        part.set (s.neuroLexID, "$metadata", "neuroLexID" + index);
                    }

                    if (n > 1)
                    {
                        if (! s.name.isEmpty ()) part.set (s.name, "$metadata", "backend", "lems", "id" + index);
                        if (! pathLength.isEmpty ()) part.set (print (s.pathLength (0.5) * 1e6) + "um", pathLength, "@$index==" + index);  // mid-point method. TODO: add way to subdivide segments for more precise modeling of varying density
                        s.output (part, index++);
                    }
                    else
                    {
                        if (! s.name.isEmpty ()  &&  ! s.name.equals (currentName)) part.set (s.name, "$metadata", "backend", "lems", "id0");
                        if (! pathLength.isEmpty ()) part.set (print (s.pathLength (0.5) * 1e6) + "um", pathLength);
                        s.output (part, -1);
                        break;  // Since we've already processed the only segment.
                    }
                }
            }

            // Create connections to complete the cables
            // N2A segment populations are based on shared properties rather than structure, so each
            // population may contain instances from several different NeuroML segment groups.
            // All connections are explicit, even within the same group.
            // In each connection, endpoint A refers to the segment closest to the root (soma), while B
            // refers to further segment. In NeuroML terms, B is the segment which has a "parent" element.
            for (Entry<Integer,Segment> e : segments.entrySet ())
            {
                Segment s = e.getValue ();
                if (s.parent == null) continue;

                String connectionName = s.parent.part.key () + "_to_" + s.part.key ();
                MNode connection = cell.child (connectionName);
                if (connection == null)
                {
                    connection = cell.childOrCreate (connectionName);
                    connection.set ("Coupling", "$inherit");  // Explicit non-NeuroML part, so no need for mapping
                    connection.set (s.parent.part.key (), "A");
                    connection.set (s       .part.key (), "B");
                }

                String condition = "";
                int parentN = s.parent.part.getOrDefault (1, "$n");
                int childN  = s       .part.getOrDefault (1, "$n");
                if (parentN > 1)
                {
                    condition = "A.$index==" + s.parent.index;
                    if (childN > 1) condition += "&&";
                }
                if (childN > 1) condition += "B.$index==" + s.index;
                if (condition.isEmpty ()) continue;

                MNode p = connection.child ("$p");
                if (p == null)
                {
                    connection.set (condition, "$p");
                }
                else
                {
                    String existing = p.get ();
                    if (! existing.isEmpty ())
                    {
                        p.set ("");
                        p.set ("0", "@");
                        p.set ("1", "@" + existing);
                    }
                    p.set ("1", "@" + condition);
                    if (parentN == 1  &&  p.size () == childN + 1)  // Every member of child group connects to the same singleton parent
                    {
                        connection.clear ("$p");
                    }
                }
            }

            // Clean up temporary nodes.
            cell.clear ("$properties");
            cell.clear ("$group");
            for (MNode part : cell)
            {
                part.clear ("$G");
                part.clear ("$inhomo");
                part.clear ("$properties");
                for (MNode v : part)
                {
                    v.clear ("$metadata", "backend", "lems", "a");
                    v.clear ("$metadata", "backend", "lems", "b");
                }
            }
            groupIndex = finalNames;
        }
    }

    public class Segment implements Comparable<Segment>
    {
        int          id;  // NeuroML id; row number in G matrix
        String       name;
        int          parentID           = -1;
        Segment      parent;
        Set<Segment> children           = new TreeSet<Segment> ();

        MNode        part;   // compartment of which this segment is an instance
        int          index;  // $index within part

        double       fractionAlong      = 1;
        Matrix       proximal;
        Matrix       distal;
        double       proximalDiameter   = -1;
        double       distalDiameter     = -1;
        double       proximalPathLength = -1;  // Path length from proximal end to root. Used to calculate pathLength().

        String       neuroLexID         = "";

        public Segment (Node node)
        {
            id = Integer.parseInt (getAttribute (node, "id", "0"));
            name       = getAttribute (node, "name");
            neuroLexID = getAttribute (node, "neuroLexId");

            for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
            {
                if (child.getNodeType () != Node.ELEMENT_NODE) continue;
                switch (child.getNodeName ())
                {
                    case "parent":
                        parentID      = getAttribute (child, "segment",       -1);
                        fractionAlong = getAttribute (child, "fractionAlong", 1.0);
                        break;
                    case "proximal":
                        proximal = new MatrixDense (3, 1);
                        proximal.set   (0, Scalar.convert (morphologyUnits (getAttribute (child, "x"))));
                        proximal.set   (1, Scalar.convert (morphologyUnits (getAttribute (child, "y"))));
                        proximal.set   (2, Scalar.convert (morphologyUnits (getAttribute (child, "z"))));
                        proximalDiameter = Scalar.convert (morphologyUnits (getAttribute (child, "diameter")));
                        break;
                    case "distal":
                        distal = new MatrixDense (3, 1);
                        distal.set   (0, Scalar.convert (morphologyUnits (getAttribute (child, "x"))));
                        distal.set   (1, Scalar.convert (morphologyUnits (getAttribute (child, "y"))));
                        distal.set   (2, Scalar.convert (morphologyUnits (getAttribute (child, "z"))));
                        distalDiameter = Scalar.convert (morphologyUnits (getAttribute (child, "diameter")));
                        break;
                }
            }
        }

        /**
            Requires that parent be resolved already.
        **/
        public void resolveProximal ()
        {
            if (proximal != null  ||  parent == null) return;
            if (fractionAlong == 1)
            {
                proximal = parent.distal;
                proximalDiameter = parent.distalDiameter;
            }
            else
            {
                parent.resolveProximal ();
                Matrix A = parent.proximal;
                Matrix B = parent.distal;
                proximal = (Matrix) B.subtract (A).multiply (new Scalar (fractionAlong)).add (A);

                double a = parent.proximalDiameter;
                double b = parent.distalDiameter;
                proximalDiameter = (b - a) * fractionAlong + a;
            }
        }

        public double pathLength (double fraction)
        {
            if (proximalPathLength < 0)
            {
                if (parent == null) proximalPathLength = 0;
                else                proximalPathLength = parent.pathLength (fractionAlong);
            }
            return proximalPathLength + ((Matrix) distal.subtract (proximal)).norm (2) * fraction;
        }

        public void output (MNode part, int index)
        {
            this.part = part;
            if (index < 0)  // only one instance, so make values unconditional
            {
                this.index = 0;
                if (distal           != null) part.set (format     (distal),           "$xyz");
                if (proximal         != null) part.set (format     (proximal),         "xyz0");
                if (distalDiameter   >= 0   ) part.set (formatUnit (distalDiameter),   "diameter");
                if (proximalDiameter >= 0   ) part.set (formatUnit (proximalDiameter), "diameter0");
            }
            else  // multiple instances
            {
                this.index = index;
                if (distal           != null) part.set (format     (distal),           "$xyz",      "@$index==" + index);
                if (proximal         != null) part.set (format     (proximal),         "xyz0",      "@$index==" + index);
                if (distalDiameter   >= 0   ) part.set (formatUnit (distalDiameter),   "diameter",  "@$index==" + index);
                if (proximalDiameter >= 0   ) part.set (formatUnit (proximalDiameter), "diameter0", "@$index==" + index);
            }
        }

        public String formatUnit (double d)
        {
            d *= 1e6;
            long l = Math.round (d);
            if (Math.abs (l - d) < epsilon)  // This is an integer
            {
                if (l == 0) return "0";
                return l + "um";
            }
            return d + "um";
        }

        public String format (Matrix A)
        {
            double x = A.get (0);
            double y = A.get (1);
            double z = A.get (2);
            if (x == 0  &&  y == 0  &&  z == 0) return "[0;0;0]";
            return "[" + print (x * 1e6) + ";" + print (y * 1e6) + ";" + print (z * 1e6) + "]*1um";
        }

        public int compareTo (Segment o)
        {
            return id - o.id;
        }
    }

    public void channel (Node node, MNode part, boolean doDependency)
    {
        String name       = node.getNodeName ();
        String number     = getAttribute (node, "number");  // part contains renamed value. This is just to test for presence of attribute in original node, which indicate population object.
        String ionChannel = part.get ("ionChannel");

        part.clear ("ionChannel");
        NameMap nameMap = partMap.importMap ("ionChannel");  // There is only one internal part for all ion-channel-related components from NeuroML. It includes both individual channels and populations/densities.
        remap (part, nameMap);

        // multiple inheritance, combining the ion channel with the given mix-in
        String potential = "";
        if      (name.contains ("Nernst")) potential = "Potential Nernst";
        else if (name.contains ("GHK2"  )) potential = "Potential GHK 2";
        else if (name.contains ("GHK"   )) potential = "Potential GHK";
        if (potential.isEmpty ())
        {
            part.set (ionChannel, "$inherit");
        }
        else
        {
            // For now, assume that ion channel is already defined
            String species = "ca";
            if (name.contains ("Ca2")) species = "ca2";
            species = models.getOrDefault (species, modelName, ionChannel, "$metadata", "species");
            part.set (species,                      "c");  // connect to species/concentration model, which the user is responsible to create 
            part.set (potential + "," + ionChannel, "$inherit");
            if (doDependency) addDependency (part, potential);
        }
        if (doDependency) addDependency (part, ionChannel);

        if (! number.isEmpty ())
        {
            part.set ("G1*population", "Gall");  // The default is Gdensity*surfaceArea
        }
    }

    public String biophysicalUnits (String value)
    {
        return biophysicalUnits (value, "");
    }

    /**
        Convert the given value to be in appropriate units, in the context of a physiology section.
    **/
    public String biophysicalUnits (String value, String defaultUnit)
    {
        value = value.trim ();
        int unitIndex = UnitValue.findUnits (value);
        if (unitIndex == 0) return value;  // no number
        if (unitIndex >= value.length ()) return value + defaultUnit;  // no unit, so use default (which may be empty)

        String units = value.substring (unitIndex).trim ();
        value        = value.substring (0, unitIndex);

        return value + cleanupUnits (units);
    }

    /**
        Convert the given value to be in appropriate units, in the context of a morphology section.
    **/
    public String morphologyUnits (String value)
    {
        value = value.trim ();
        int unitIndex = UnitValue.findUnits (value);
        if (unitIndex == 0) return value;  // no number
        if (unitIndex >= value.length ()) return value + "um";  // default morphology units are micrometers

        String units = value.substring (unitIndex).trim ();
        value        = value.substring (0, unitIndex);

        return value + cleanupUnits (units);
    }

    /**
        Create a spike generator, which may be associated with its intended synapse.
    **/
    public void spikingSynapse (Node node)
    {
        String id      = getAttribute (node, "id");
        String synapse = getAttribute (node, "synapse");
        // "spikeTarget" appears to be redundant with "synapse". Probably exists due to some oddity in LEMS.

        MNode part = models.childOrCreate (modelName, id);

        String inherit = node.getNodeName ();
        NameMap nameMap = partMap.importMap (inherit);
        inherit = nameMap.internal;
        if (! inherit.isEmpty ())
        {
            part.set (inherit, "$inherit");
            addDependency (part, inherit);
        }

        // Leave a hint that this spike generator should be the source side of the given synapse.
        // "spikeArray" does not have an associated synapse.
        if (! synapse.isEmpty ()) part.set (synapse, "$metadata", "backend", "lems", "synapse");

        addAttributes (node, part, nameMap, "id", "synapse", "spikeTarget");

        Map<Double,String> sorted = new TreeMap<Double,String> ();
        for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
        {
            if (child.getNodeType () == Node.ELEMENT_NODE  &&  child.getNodeName ().equals ("spike"))
            {
                String time = biophysicalUnits (getAttribute (child, "time"));
                sorted.put (Scalar.convert (time), time);
            }
        }
        if (sorted.size () > 0)  // generate spikeArray-like code
        {
            String times = "";
            for (String s : sorted.values ()) times += ";" + s;
            times += ";∞";  // This shuts down spiking after last specified time.
            times = "[" + times.substring (1) + "]";
            part.set (times, "times");
        }
    }

    public void compoundInput (Node node)
    {
        MNode part = genericPart (node, models.child (modelName));
        String inherit = part.get ("$inherit").replace ("\"", "");
        part.clear ("$inherit");
        if (! inherit.isEmpty ()) removeDependency (part, inherit);
        part.set ("compoundInput", "$metadata", "backend", "lems", "part");
        for (MNode c : part)
        {
            if (c.child ("$inherit") == null) continue;  // not a sub-part
            c.set ("$kill", "B");  // force sub-part to get its connection binding from us
        }
    }

    public class Network
    {
        String id;
        MNode network;
        List<Node>   extracellularProperties = new ArrayList<Node> ();
        List<Node>   projections             = new ArrayList<Node> ();
        List<Node>   explicitInputs          = new ArrayList<Node> ();
        Set<String>  explicitInputRecheck    = new TreeSet<String> ();

        public Network (Node node)
        {
            id                 = getAttribute (node, "id");
            String temperature = getAttribute (node, "temperature");

            network = models.childOrCreate (modelName, id);
            network.set ("network", "$metadata", "backend", "lems", "part");
            if (! temperature.isEmpty ()) network.set (biophysicalUnits (temperature), "temperature");

            for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
            {
                if (child.getNodeType () != Node.ELEMENT_NODE) continue;
                switch (child.getNodeName ())
                {
                    case "space":
                        space (child);
                        break;
                    case "region":
                        String spaceID = getAttribute (child, "space");
                        network.set (spaceID, "$region", child.getNodeValue ());  // Region is little more than an alias of a space, as of NeuroML 2 beta 4.
                        break;
                    case "extracellularProperties":
                        extracellularProperties.add (child);
                        break;
                    case "population":
                        population (child);
                        break;
                    // TODO: cellSet -- Ignoring. Has one attribute and no elements. Attribute is ill-defined.
                    // TODO: synapticConnection -- Appears to be deprecated.
                    case "projection":
                    case "continuousProjection":
                    case "electricalProjection":
                    case "inputList":
                        projections.add (child);
                        break;
                    case "explicitInput":
                        explicitInputs.add (child);
                        break;
                }
            }
        }

        public void space (Node node)
        {
            String id = getAttribute (node, "id");

            for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
            {
                // The standard as written allows more than one structure element, but not sure how to make sense of that.
                // This code simply overwrites the data if more than one such element exists.
                if (child.getNodeType () == Node.ELEMENT_NODE  &&  child.getNodeName ().equals ("structure"))
                {
                    double sx = getAttribute (child, "xSpacing", 1.0);
                    double sy = getAttribute (child, "ySpacing", 1.0);
                    double sz = getAttribute (child, "zSpacing", 1.0);
                    double ox = getAttribute (child, "xStart",   0.0);
                    double oy = getAttribute (child, "yStart",   0.0);
                    double oz = getAttribute (child, "zStart",   0.0);
                    MNode p = network.childOrCreate ("$space", id);
                    p.set ("[" + print (sx) + ";" + print (sy) + ";" + print (sz) + "]*1um", "scale");
                    p.set ("[" + print (ox) + ";" + print (oy) + ";" + print (oz) + "]*1um", "offset");
                }
            }
        }

        public void population (Node node)
        {
            String id        = getAttribute (node, "id");
            int    n         = getAttribute (node, "size", 0);
            String component = getAttribute (node, "component");  // Should always be defined.

            MNode part = network.childOrCreate (id);
            part.set (component, "$inherit");
            addDependency (part, component);
            if (n > 1) part.set (n, "$n");

            // Add intra/extra cellular properties to inherited cell
            Cell cell = cells.get (component);
            if (cell != null)
            {
                List<Node> localProps = new ArrayList<Node> ();
                localProps.addAll (extracellularProperties);
                String exID = getAttribute (node, "extracellularProperties");
                if (! exID.isEmpty ())
                {
                    Node ex = properties.get (exID);
                    if (ex != null) localProps.add (ex);
                }
                for (Node p : localProps) cell.addCellularProperties (p);
            }

            for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
            {
                if (child.getNodeType () != Node.ELEMENT_NODE) continue;
                switch (child.getNodeName ())
                {
                    case "layout"  : populationLayout   (child, part); break;
                    case "instance": populationInstance (child, part); break;
                }
            }

            // Post-process instances, hopefully matching their IDs to their $index values.
            MNode instances = part.child ("$instance");
            if (instances != null)
            {
                int count = instances.size ();
                if (count > 1) part.set (count, "$n");
                int index = 0;
                for (MNode i : instances)
                {
                    String xyz = i.get ("$xyz");
                    String ijk = i.get ("ijk");
                    if (count == 1)
                    {
                        if (! xyz.isEmpty ()) part.set (xyz, "$xyz");
                        if (! ijk.isEmpty ()) part.set (ijk, "ijk");
                    }
                    else
                    {
                        if (! xyz.isEmpty ()) part.set (xyz, "$xyz", "@$index==" + index);
                        if (! ijk.isEmpty ()) part.set (ijk, "ijk",  "@$index==" + index);
                    }
                    i.set (index++, "$index");
                }
            }
        }

        public void populationLayout (Node node, MNode part)
        {
            String spaceID = getAttribute (node, "space");

            for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
            {
                if (child.getNodeType () == Node.ELEMENT_NODE)
                {
                    String regionID = getAttribute (child, "region");
                    if (! regionID.isEmpty ()) spaceID = network.get ("$region", regionID);
                    MNode space = null;
                    if (! spaceID.isEmpty ()) space = network.child ("$space", spaceID);

                    switch (child.getNodeName ())
                    {
                        case "random":
                            part.set (getAttribute (child, "number", 1), "$n");
                            if (space != null) part.set ("uniform(" + space.get ("scale") + ")+" + space.get ("offset"), "$xyz");
                            break;
                        case "grid":
                            int x = getAttribute (child, "xSize", 1);
                            int y = getAttribute (child, "ySize", 1);
                            int z = getAttribute (child, "zSize", 1);
                            part.set (x * y * z, "$n");
                            if (space == null) part.set ("grid($index," + x + "," + y + "," + z + ")",                                                             "$xyz");
                            else               part.set ("grid($index," + x + "," + y + "," + z + ",\"raw\")&" + space.get ("scale") + "+" + space.get ("offset"), "$xyz");
                            break;
                        case "unstructured":
                            part.set (getAttribute (child, "number", 1), "$n");
                            break;
                    }
                }
            }
        }

        public void populationInstance (Node node, MNode part)
        {
            int id = getAttribute (node, "id", 0);
            int i  = getAttribute (node, "i", -1);
            int j  = getAttribute (node, "j", -1);
            int k  = getAttribute (node, "k", -1);
            double x = 0;
            double y = 0;
            double z = 0;
            for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
            {
                // As written, the standard allows multiple xyz positions. Not sure what that means.
                // This code overwrites the position if more than one are specified.
                if (child.getNodeType () == Node.ELEMENT_NODE  &&  child.getNodeName ().equals ("location"))
                {
                    x = getAttribute (child, "x", 0.0);
                    y = getAttribute (child, "y", 0.0);
                    z = getAttribute (child, "z", 0.0);
                }
            }

            String suffix = "*1um";
            if (x == 0  &&  y == 0  &&  z == 0) suffix = "";
            part.set ("[" + print (x) + ";" + print (y) + ";" + print (z) + "]" + suffix, "$instance", id, "$xyz");
            if (i >= 0  ||  j >= 0  ||  k >= 0)
            {
                if (i < 0) i = 0;
                if (j < 0) j = 0;
                if (k < 0) k = 0;
                part.set ("[" + i + ";" + j + ";" + k + "]",                              "$instance", id, "ijk");
            }
        }

        /**
            Handles the 3 projection types, and also contains minor hacks to handle inputList.
        **/
        public void projection (Node node)
        {
            String id        = getAttribute  (node, "id");
            String inherit   = getAttribute  (node, "synapse");
            String component = getAttribute  (node, "component");
            String A         = getAttribute  (node, "presynapticPopulation");
            String B         = getAttributes (node, "postsynapticPopulation", "population");

            MNode base = new MVolatile ();
            if (inherit.isEmpty ()) inherit = node.getNodeName ();
            boolean inputList = ! component.isEmpty ();
            if (inputList)
            {
                // There are several different modes in which an inputList makes connections:
                // * A current-pattern generator connects to a single target. The pattern generator works
                //   in its normal mode, which is a unary connection directly to the target part.
                // * A current-pattern generator connects to multiple targets. The pattern generator must be
                //   modified to act as a source part for a connection part.
                // * A spike source with associated synapse. The synapse becomes the connection part,
                //   and the spike source simply acts as an endpoint for it.
                String synapse = models.get (modelName, component, "$metadata", "backend", "lems", "synapse");
                if (synapse.isEmpty ())  // assume a current-pattern generator
                {
                    int childCount = 0;
                    for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
                    {
                        if (child.getNodeType () == Node.ELEMENT_NODE) childCount++;
                    }

                    if (childCount == 1)  // single direct connection, equivalent to explicitInput
                    {
                        inherit = component;
                        explicitInputRecheck.add (id);
                        // A remains empty
                    }
                    else  // multiple connections
                    {
                        // Create modified part
                        MNode c = models.childOrCreate (modelName, component);
                        c.set ("$kill", "B");
                        c.set ("$kill", "B.I");

                        // Create connection
                        base.set ("+A.I", "B.I");
                        inherit = "";
                        A = component;
                    }
                }
                else  // spike source with associated synapse
                {
                    inherit = synapse;
                    A = component;
                }
            }
            if (! A.isEmpty()) base.set (A, "A");
            base.set (B, "B");

            NameMap nameMap = partMap.importMap (inherit);
            inherit = nameMap.internal;

            addAttributes (node, base, nameMap, "id", "synapse", "presynapticPopulation", "postsynapticPopulation", "component", "population");

            // Children are specific connections.
            // In the case of "continuous" connections, there are pre- and post-synaptic components which can vary
            // from one entry to the next. These must be made into separate connection objects, so try to fold
            // them as much as possible.
            // For other connection types, attributes can be set up as conditional constants.

            MNode instancesA = network.child (A, "$instance");
            MNode instancesB = network.child (B, "$instance");

            boolean preCellSingleton  = network.getOrDefault (1, A, "$n") == 1;  // For inputList, A might not be a network node, but the answer (true) will still be correct.
            boolean postCellSingleton = network.getOrDefault (1, B, "$n") == 1;

            List<Connection> connections = new ArrayList<Connection> ();
            for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
            {
                if (child.getNodeType () != Node.ELEMENT_NODE) continue;

                // Collect data and assemble query
                Connection connection = new Connection ();
                int childID = getAttribute (child, "id", 0);

                connection.preComponent  = getAttributes (child, "preComponent", "synapse");
                String preCell           = getAttributes (child, "preCell", "preCellId");
                String preSegmentString  = getAttributes (child, "preSegment", "preSegmentId");
                String preFractionString = getAttribute  (child, "preFractionAlong");

                connection.postComponent  = getAttributes (child, "postComponent", "synapse");  // Notice that "synapse" occurs twice, specifically for electrical (gap) connection, since pre and post component are the same in that case.
                String postCell           = getAttributes (child, "postCell", "postCellId", "target");
                String postSegmentString  = getAttributes (child, "postSegment", "postSegmentId", "segmentId");
                String postFractionString = getAttributes (child, "postFractionAlong", "fractionAlong");

                double weight = getAttribute (child, "weight", 1.0);
                String delay  = getAttribute (child, "delay");

                String[] pieces = preCell.split ("/");
                if (pieces.length >= 3) preCell = pieces[2];
                pieces = postCell.split ("/");
                if (pieces.length >= 3) postCell = pieces[2];

                if (instancesA != null) preCell  = instancesA.getOrDefault (preCell,  "$index", preCell);  // Map NeuroML ID to assigned N2A $index, falling back on ID if $index has not been assigned.
                if (instancesB != null) postCell = instancesB.getOrDefault (postCell, "$index", postCell);

                SegmentFinder finder = new SegmentFinder ();
                if (! inputList) finder.find (preSegmentString, A);
                int preSegmentIndex = finder.index;
                connection.preGroup = finder.group;
                finder.find (postSegmentString, B);
                int postSegmentIndex = finder.index;
                connection.postGroup = finder.group;

                double preFraction = 0.5;
                if (! preFractionString.isEmpty ()) preFraction = Double.valueOf (preFractionString);
                double postFraction = 0.5;
                if (! postFractionString.isEmpty ()) postFraction = Double.valueOf (postFractionString);

                // Choose part
                int match = connections.indexOf (connection);
                if (match >= 0)  // Use existing part.
                {
                    connection = connections.get (match);
                }
                else  // Create new part, cloning relevant info.
                {
                    connections.add (connection);
                    if (network.child (id) == null) connection.part = network.set (base, id);
                    else                            connection.part = network.set (base, id + childID);  // Another part has already consumed the base name, so augment it with some index. Any index will do, but childID is convenient.

                    if (! inherit.isEmpty ())
                    {
                        connection.part.set (inherit, "$inherit");
                        addDependency (connection.part, inherit);
                    }
                    if (! component.isEmpty()  &&  ! A.isEmpty()) addDependencyFromConnection (connection.part.child ("A"), A);

                    if (! connection.preGroup .isEmpty ()) connection.part.set (A + "." + connection.preGroup,  "A");
                    if (! connection.postGroup.isEmpty ()) connection.part.set (B + "." + connection.postGroup, "B");
                    if (! connection.preComponent.isEmpty ())
                    {
                        connection.part.set (connection.preComponent, "preComponent", "$inherit");
                        addDependency (connection.part.child ("preComponent"), connection.preComponent);
                    }
                    if (! connection.postComponent.isEmpty ())
                    {
                        connection.part.set (connection.postComponent, "postComponent", "$inherit");
                        addDependency (connection.part.child ("postComponent"), connection.postComponent);
                    }
                }
               
                // Add conditional info

                String condition = "";
                if (! preCellSingleton)
                {
                    if (connection.preGroup.isEmpty ()) condition = "A.$index=="     + preCell;
                    else                                condition = "A.$up.$index==" + preCell;
                }
                if (! postCellSingleton)
                {
                    if (! condition.isEmpty ()) condition += "&&";
                    if (connection.postGroup.isEmpty ()) condition += "B.$index=="     + postCell;
                    else                                 condition += "B.$up.$index==" + postCell;
                }
                if (preSegmentIndex >= 0  &&  ! connection.preGroup.isEmpty ())
                {
                    if (! condition.isEmpty ()) condition += "&&";
                    condition += "A.$index==" + preSegmentIndex;
                }
                if (postSegmentIndex >= 0  &&  ! connection.postGroup.isEmpty ())
                {
                    if (! condition.isEmpty ()) condition += "&&";
                    condition += "B.$index==" + postSegmentIndex;
                }
                if (! condition.isEmpty ())
                {
                    MNode p = connection.part.child ("$p");
                    if (p == null)
                    {
                        connection.part.set (condition, "$p");
                    }
                    else
                    {
                        if (p.size () == 0)  // There is exactly one condition already existing, and we transition to multi-part equation.
                        {
                            p.set ("0", "@");
                            p.set ("1", "@" + p.get ());
                            p.set ("");
                        }
                        p.set ("1", "@" + condition);
                    }
                }

                if (preFraction  != 0.5) connection.add (connection.preFractions,  preFraction,  condition);
                if (postFraction != 0.5) connection.add (connection.postFractions, postFraction, condition);
                if (weight       != 1.0) connection.add (connection.weights,       weight,       condition);
                if (! delay.isEmpty ())  connection.add (connection.delays,        delay,        condition);
            }
            for (Connection c : connections) c.injectConditionalValues ();
            // TODO: detect all-to-all case and clear $p?

            if (connections.size () == 0)  // No connections were added, so add a minimalist projection part.
            {
                MNode part = network.set (base, id);
                if (! inherit.isEmpty ())
                {
                    part.set (inherit, "$inherit");
                    addDependency (part, inherit);
                }
                part.set ("0", "$p");  // No connections at all

                SegmentFinder finder = new SegmentFinder ();
                if (! inputList) finder.find ("0", A);
                if (! finder.group.isEmpty ()) part.set (A + "." + finder.group, "A");
                // Because the input file is un-specific, we don't care about filtering by index.

                finder.find ("0", B);
                if (! finder.group.isEmpty ()) part.set (B + "." + finder.group, "B");
            }
        }

        class SegmentFinder
        {
            public String group = "";
            public int    index = -1;
            public MNode  segment;

            public void find (String ID, String endpoint)
            {
                group = "";
                index = -1;

                int id = 0;  // By convention, 0 is closest to (or actually is) the soma.
                if (! ID.isEmpty ()) id = Integer.valueOf (ID);

                // ID is the row in the segment*group matrix.
                // We must find the column associated with it, so we can map to a group part.
                String cellID = network.get (endpoint, "$inherit").replace ("\"", "");
                Cell cell = cells.get (cellID);
                if (cell == null) return;  // This could be a point cell, rather than multi-compartment.
                MatrixBoolean M = cell.M;
                if (M == null) return;
                int count = M.columns ();
                for (int c = 0; c < count; c++)
                {
                    if (M.get (id, c))
                    {
                        group = cell.groupIndex.get (c);
                        segment = models.child (modelName, cellID, group);
                        if (segment != null  &&  segment.getOrDefault (1, "$n") > 1) index = M.indexInColumn (id, c);
                        break;
                    }
                }
            }
        }

        public void explicitInput (Node node)
        {
            String input  = getAttribute (node, "input");
            String target = getAttribute (node, "target");

            int index = 0;
            int i = target.indexOf ('[');
            if (i >= 0)
            {
                String suffix = target.substring (i + 1);
                target = target.substring (0, i);
                i = suffix.indexOf (']');
                if (i >= 0) suffix = suffix.substring (0, i);
                index = Integer.valueOf (suffix);
            }
            MNode targetPart = network.child (target);
            MNode sourcePart = models.child (modelName, input);

            String name = input + "_to_" + target;
            MNode part = network.childOrCreate (name);

            String synapse = sourcePart.get ("$metadata", "backend", "lems", "synapse");
            if (synapse.isEmpty ())
            {
                part.set (input, "$inherit");
                addDependency (part, input);
                // The unresolved question here is whether sourcePart is shared with any other input.
                // The only way to be certain is to check after all inputs have been created.
                // If this is the case, then sourcePart should be changed into a connection endpoint,
                // and this connection object should get some added equations to move the values.
                explicitInputRecheck.add (name);
            }
            else
            {
                part.set (synapse, "$inherit");
                addDependency (part, synapse);
                MNode connection = part.set (input, "A");
                addDependencyFromConnection (connection, input);
            }

            // If this is a multi-compartment cell, it cannot be the direct target of a connection.
            // Find the first segment and use that instead.
            SegmentFinder finder = new SegmentFinder ();
            finder.find ("0", target);
            if (finder.segment != null) target += "." + finder.segment.key ();

            part.set (target, "B");

            String p = "";
            if (targetPart == null  ||  targetPart.getOrDefault (1, "$n") != 1)  // We only have to set $p explicitly if the target part has more than one instance.
            {
                if (finder.segment == null) p = "B.$index=="     + index;
                else                        p = "B.$up.$index==" + index;
            }
            if (finder.index >= 0)
            {
                if (! p.isEmpty ()) p += "&&";
                p += "B.$index==" + finder.index;
            }
            if (! p.isEmpty ()) part.set (p, "$p");
        }

        public void finish1 ()
        {
            for (Node p : projections)    projection (p);
            for (Node e : explicitInputs) explicitInput (e);
            network.clear ("$space");
            network.clear ("$region");
            for (MNode p : network) p.clear ("$instance");
        }

        public void finish2 ()
        {
            for (String name : explicitInputRecheck)
            {
                MNode part = network.child (name);
                String inherit = part.get ("$inherit").replace ("\"", "");
                MNode sourcePart = models.child (modelName, inherit);
                if (sourcePart.getInt ("$count") <= 1) continue;  // We are the only user, so can directly embed input. That's the current arrangement, so nothing to do.

                removeDependency (part, inherit);
                part.clear ("$inherit");
                MNode connection = part.set (inherit, "A");
                addDependencyFromConnection (connection, inherit);
                sourcePart.set ("$kill", "B");
                sourcePart.set ("$kill", "B.I");
                part.set ("+A.I", "B.I");
            }
        }
    }

    public static class Connection
    {
        public String preGroup      = "";
        public String postGroup     = "";
        public String preComponent  = "";
        public String postComponent = "";
        public MNode  part;

        public TreeMap<Double,TreeSet<String>> preFractions  = new TreeMap<Double,TreeSet<String>> ();
        public TreeMap<Double,TreeSet<String>> postFractions = new TreeMap<Double,TreeSet<String>> ();
        public TreeMap<Double,TreeSet<String>> weights       = new TreeMap<Double,TreeSet<String>> ();
        public TreeMap<Double,TreeSet<String>> delays        = new TreeMap<Double,TreeSet<String>> ();

        public void add (TreeMap<Double,TreeSet<String>> collection, String value, String condition)
        {
            add (collection, Scalar.convert (value), condition);
        }

        public void add (TreeMap<Double,TreeSet<String>> collection, double value, String condition)
        {
            TreeSet<String> conditions = collection.get (value);
            if (conditions == null)
            {
                conditions = new TreeSet<String> ();
                collection.put (value, conditions);
            }
            conditions.add (condition);
        }

        public void injectConditionalValues ()
        {
            injectConditionalValue ("preFraction",  preFractions,  0.5, false);
            injectConditionalValue ("postFraction", postFractions, 0.5, false);
            injectConditionalValue ("weight",       weights,       1.0, false);
            injectConditionalValue ("delay",        delays,        0.0, true);
        }

        public void injectConditionalValue (String name, TreeMap<Double,TreeSet<String>> collection, double defaultValue, boolean outputSeconds)
        {
            if (collection.size () == 0) return;
            if (collection.size () == 1)
            {
                double value = collection.firstKey ();
                if (value == defaultValue) return;  // Default value should be inherited, so no need to set it.
                TreeSet<String> conditions = collection.firstEntry ().getValue ();
                if (conditions.size () == 1  &&  conditions.first ().isEmpty ())  // Unconditional value. This occurs when both source and destination populations are singletons.
                {
                    if (outputSeconds)
                    {
                        if (value >= 1) part.set (value + "s",         name);
                        else            part.set (value * 1000 + "ms", name);
                    }
                    else
                    {
                        part.set (value, name);
                    }
                    return;
                }
            }

            MNode v = part.childOrCreate (name);
            v.set (defaultValue, "@");
            for (Entry<Double,TreeSet<String>> e : collection.entrySet ())
            {
                String condition = "";
                for (String c : e.getValue ())
                {
                    if (! condition.isEmpty ()) condition += "||";
                    condition += c;
                }
                double value = e.getKey ();
                if (outputSeconds)
                {
                    if (value >= 1) part.set (value + "s",         name);
                    else            part.set (value * 1000 + "ms", name);
                }
                else
                {
                    v.set (value, "@" + condition);
                }
            }
        }

        @Override
        public boolean equals (Object o)
        {
            if (! (o instanceof Connection)) return false;
            Connection that = (Connection) o;

            if (! preGroup     .equals (that.preGroup     )) return false;
            if (! postGroup    .equals (that.postGroup    )) return false;
            if (! preComponent .equals (that.preComponent )) return false;
            if (! postComponent.equals (that.postComponent)) return false;
            return true;
        }
    }

    public void simulation (Node node)
    {
        String id     = getAttribute (node, "id");
        String length = getAttribute (node, "length");
        String step   = getAttribute (node, "step");
        String target = getAttribute (node, "target");

        if (primaryModel.equals (id)) primaryModel = target;  // redirect, since "Simulation" itself is not a proper object.

        MNode part = models.childOrCreate (modelName, target);
        part.set (step,           "$t'");
        part.set ("$t<" + length, "$p");

        for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
        {
            if (child.getNodeType () == Node.ELEMENT_NODE) outputs.add (new Output (child, part));
        }
    }

    public class Output
    {
        MNode      part;
        String     fileName;
        String     chartParameters;
        List<Line> lines = new ArrayList<Line> ();

        public Output (Node node, MNode part)
        {
            this.part = part;

            String path      = getAttribute (node, "path");     // Is this a path that precedes fileName, or an XPath to some runtime instance?
            fileName         = getAttribute (node, "fileName");
            //String format    = getAttribute (node, "format");   // Spike raster format.
            String title     = getAttribute (node, "title");
            String timeScale = getAttribute (node, "timeScale");
            String xmin      = getAttribute (node, "xmin");
            String xmax      = getAttribute (node, "xmax");
            String ymin      = getAttribute (node, "ymin");
            String ymax      = getAttribute (node, "ymax");

            if (fileName.isEmpty ()) fileName = title;
            if (! path.isEmpty ()) fileName = path + "/" + fileName;

            chartParameters = "";
            if (! timeScale.isEmpty ()) chartParameters  = "timeScale=" + timeScale;
            if (! xmin     .isEmpty ()) chartParameters += ",xmin="     + xmin;
            if (! xmax     .isEmpty ()) chartParameters += ",xmax="     + xmax;
            if (! ymin     .isEmpty ()) chartParameters += ",ymin="     + ymin;
            if (! ymax     .isEmpty ()) chartParameters += ",ymax="     + ymax;
            if (chartParameters.startsWith (",")) chartParameters = chartParameters.substring (1);

            for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
            {
                if (child.getNodeType () != Node.ELEMENT_NODE) continue;
                lines.add (new Line (child));
            }
        }

        public void finish ()
        {
            for (Line l : lines) l.finish ();
        }

        public class Line
        {
            String id;
            String quantity;
            String mode;
            String event;

            public Line (Node node)
            {
                id                   = getAttribute (node, "id");  // Presumably determines data series name, or equivalently, column heading.
                quantity             = getAttribute (node, "quantity");
                String select        = getAttribute (node, "select");
                event                = getAttribute (node, "eventPort");
                String scale         = getAttribute (node, "scale");
                String lineTimeScale = getAttribute (node, "timeScale");
                String color         = getAttribute (node, "color");

                if (quantity.isEmpty ()  &&  ! select.isEmpty ()) quantity = select + "/ignored";

                mode = "";
                if (! scale        .isEmpty ()) mode  = "scale="          + scale;
                if (! color        .isEmpty ()) mode += ",color="         + color;
                if (! lineTimeScale.isEmpty ()) mode += ",lineTimeScale=" + lineTimeScale;
                if (mode.startsWith (",")) mode = mode.substring (1);
                if (! chartParameters.isEmpty ())
                {
                    if (! mode.isEmpty ()) mode += ",";
                    mode += chartParameters;
                    chartParameters = "";  // Only output once
                }
            }

            public void finish ()
            {
                // Resolve XPath to determine final location of output() statement, and identity of target variable.
                XPath variablePath = new XPath (quantity);
                variablePath.resolve (part);
                MNode container = variablePath.container ();
                if (container == null) return;
                String variable = variablePath.target ();

                String dummy = "x0";
                int index = 1;
                while (container.child (dummy) != null) dummy = "x" + index++;

                String condition = variablePath.condition ();
                if (! event.isEmpty ())
                {
                    if (! condition.isEmpty ()) condition += "&&";
                    condition += "event(" + event + ")";
                    variable = "1";
                }

                String output = "output(";
                if (! fileName.isEmpty ()) output += "\"" + fileName + "\",";
                output += variable;
                if (! id.isEmpty ()) output += ",\"" + id + "\"";
                if (! mode.isEmpty ()) output += ",\"" + mode + "\"";
                output += ")";
                if (! condition.isEmpty ()) output += "@" + condition;
                container.set (output, dummy);
            }
        }
    }

    /**
        Handles elements in a generic manner, including metadata elements.
        Generic elements get processed into parts under the given container.
        Metadata elements get added to the $metadata node of the given container.
    **/
    public MNode genericPart (Node node, MNode container)
    {
        String nodeName = node.getNodeName ();
        if (nodeName.equals ("notes"))
        {
            container.set (getText (node), "$metadata", "notes");
            return container.child ("$metadata", "notes");
        }
        if (nodeName.equals ("property"))
        {
            String tag   = getAttribute (node, "tag");
            String value = getAttribute (node, "value");
            container.set (value, "$metadata", tag);
            return container.child ("$metadata", tag);
        }
        if (nodeName.equals ("annotation"))
        {
            return Annotation (node, container);
        }

        String id = getAttribute (node, "id", nodeName);
        String stem = id;
        int suffix = 2;
        while (container.child (id) != null) id = stem + suffix++;
        MNode part = container.childOrCreate (id);

        nodeName = getAttribute (node, "type", nodeName);

        List<MNode> parents = collectParents (container);
        String inherit = typeFor (nodeName, parents);
        NameMap nameMap;
        if (inherit.isEmpty ())
        {
            nameMap = partMap.importMap (nodeName);
            inherit = nameMap.internal;
        }
        else
        {
            // Because typeFor() finds internal names, we need to retrieve a map from the opposite direction.
            // It will still map parameter names correctly for import.
            nameMap = partMap.exportMap (inherit);
        }
        part.set (inherit, "$inherit");
        addDependency (part, inherit);

        parents = collectParents (part);  // Now we follow our own inheritance chain, not our container's.
        NamedNodeMap attributes = node.getAttributes ();
        int count = attributes.getLength ();
        for (int i = 0; i < count; i++)
        {
            Node a = attributes.item (i);
            String name  = a.getNodeName ();
            String value = a.getNodeValue ();

            if (name.equals ("id"  )) continue;
            if (name.equals ("type")) continue;
            if (name.equals ("neuroLexId"))
            {
                part.set (value, "$metadata", "neuroLexID");
                continue;
            }

            name = nameMap.importName (name);
            if (isPart (name, parents))
            {
                inherit = value;
                part.set (inherit, name, "$inherit");
                addDependency (part.child (name), inherit);
                addAlias (inherit, name);
            }
            else
            {
                String defaultUnit = nameMap.defaultUnit (name);
                part.set (biophysicalUnits (value, defaultUnit), name);
            }
        }

        // Some components require special processing, either because they are extensions
        // of component types that require special processing, or because they are standard
        // component types that appear as child nodes. IE: every child under a generic instantiation
        // also gets processed via genericPart(), but not every child is necessarily generic.
        // Thus, we add hacks here to detect these special cases and do the extra processing.
        if (nodeName.startsWith ("channel")) channel (node, part, true);


        for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
        {
            if (child.getNodeType () == Node.ELEMENT_NODE) genericPart (child, part);
        }

        return part;
    }

    public MNode Annotation (Node node, MNode container)
    {
        StringWriter resultStream = new StringWriter ();
        DOMSource dom = new DOMSource (node);
        StreamResult stream = new StreamResult (resultStream);
        TransformerFactory factoryXform = TransformerFactory.newInstance ();
        factoryXform.setAttribute ("indent-number", 4);
        try
        {
            javax.xml.transform.Transformer xform = factoryXform.newTransformer ();
            xform.setOutputProperty (OutputKeys.INDENT, "yes");
            xform.transform (dom, stream);
        }
        catch (Exception e) {}

        String result = resultStream.toString ();
        result = result.split ("<annotation>", 2)[1];
        result = result.split ("</annotation>", 2)[0];

        container.set (result, "$metadata", "annotation");
        return container.child ("$metadata", "annotation");
    }

    public void remap (MNode part, NameMap nameMap)
    {
        MNode temp = new MVolatile ();
        temp.merge (part);
        part.clear ();
        for (MNode v : temp)
        {
            String key = nameMap.importName (v.key ());
            part.set (v, key);
        }
    }

    public void addAttributes (Node node, MNode part, NameMap nameMap, String... forbidden)
    {
        NamedNodeMap attributes = node.getAttributes ();
        int count = attributes.getLength ();
        List<String> forbiddenList = Arrays.asList (forbidden);
        for (int i = 0; i < count; i++)
        {
            Node a = attributes.item (i);
            String name  = a.getNodeName ();
            String value = a.getNodeValue ();
            if (forbiddenList.contains (name)) continue;
            if (name.equals ("neuroLexId"))
            {
                part.set (value, "$metadata", "neuroLexID");
                continue;
            }

            name = nameMap.importName (name);
            String defaultUnit = nameMap.defaultUnit (name);
            part.set (biophysicalUnits (value, defaultUnit), name);  // biophysicalUnits() will only modify text if there is a numeric value
        }
    }

    public void addAlias (String from, String to)
    {
        TreeSet<String> alternates = aliases.get (from);
        if (alternates == null)
        {
            alternates = new TreeSet<String> ();
            aliases.put (from, alternates);
        }
        alternates.add (to);
    }

    /**
        Locates all the accessible parents of the given node and lists them in ascending
        order by distance from child. That is, most direct ancestor comes first in the list.
        In addition to terminating at the root parent, also terminates when a parent can't
        be found, so the list may be incomplete.
    **/
    public List<MNode> collectParents (MNode child)
    {
        List<MNode> result = new ArrayList<MNode> ();

        String childInherit = child.get ("$inherit");
        String[] inherits = childInherit.split (",");
        for (String inherit : inherits)
        {
            inherit = inherit.replace ("\"", "");
            if (inherit.isEmpty ()) continue;
            MNode parent = models.child (modelName, inherit);
            if (parent == child) parent = null;  // Prevent infinite loop on proxies.
            if (parent == null) parent = AppData.models.child (inherit);
            if (parent == null) continue;
            result.add (parent);
            result.addAll (collectParents (parent));
        }
        return result;
    }

    /**
        Locate the first parent in the inheritance hierarchy that resides in the models database rather than
        the current import.
     **/
    public MNode findBasePart (MNode part)
    {
        String inherit = part.get ("$inherit").replace ("\"", "");

        MNode result = models.child (modelName, inherit);
        if (result == part) result = null;  // Prevent infinite loop on proxies.
        if (result != null) return findBasePart (result);

        return AppData.models.child (inherit);  // could be null
    }

    /**
        Similar to PartMap.exportMap(MNode), but looks for parent in the current import
        before referring up to the main database.
    **/
    public NameMap exportMap (String name)
    {
        MNode base = models.child (modelName, name);
        if (base == null) base = AppData.models.child (name);
        else              base = findBasePart (base);
        if (base == null) return new NameMap (name);
        return partMap.exportMap (base);
    }

    /**
        Converts an element name to an internal part name, if one is available.
    **/
    public String typeFor (String nodeName, List<MNode> parents)
    {
        for (MNode parent : parents)
        {
            // TODO: for lookup of direct child, may need to map node name in context of parent.
            // This is only necessary if the subpart has a different internal name.
            String type = parent.get (nodeName, "$inherit").replace ("\"", "");  // Assumes single inheritance
            if (! type.isEmpty ()) return type;
            MNode c = parent.child ("$metadata", "backend", "lems", "children", nodeName);
            if (c != null) return c.getOrDefault (nodeName).split (",")[0];
            c = parent.child ("$metadata", "backend", "lems", "attachments", nodeName);
            if (c != null) return c.getOrDefault (nodeName).split (",")[0];
        }
        return "";
    }

    public MNode definitionFor (String name, List<MNode> parents)
    {
        for (MNode parent : parents)
        {
            MNode result = parent.child (name);
            if (result != null) return result;
        }
        return null;
    }

    public boolean isPart (String name, List<MNode> parents)
    {
        MNode result = definitionFor (name, parents);
        if (result == null) return false;
        return MPart.isPart (result);
    }

    public void target (Node node)
    {
        primaryModel      = getAttribute (node, "component");
        String reportFile = getAttribute (node, "reportFile");
        String timesFile  = getAttribute (node, "timesFile");
        if (! reportFile.isEmpty ()) models.set (reportFile, modelName, "$metadata", "backend", "lems", "reportFile");
        if (! timesFile .isEmpty ()) models.set (timesFile,  modelName, "$metadata", "backend", "lems", "timesFile");
    }

    public void dimension (Node node)
    {
        String name = getAttribute (node, "name");
        int    m    = getAttribute (node, "m", 0);
        int    l    = getAttribute (node, "l", 0);
        int    t    = getAttribute (node, "t", 0);
        int    i    = getAttribute (node, "i", 0);
        int    k    = getAttribute (node, "k", 0);
        int    n    = getAttribute (node, "n", 0);
        int    j    = getAttribute (node, "j", 0);

        // We construct a new unit out of concrete SI units, rather than simply constructing a dimension,
        // because the described dimension may not have a predefined entry in the default system of units.
        // The dimension will of course be available as a property of the unit.
        Unit<?> result = AbstractUnit.ONE;
        if (m != 0) result = result.multiply (Units.KILOGRAM.pow (m));
        if (l != 0) result = result.multiply (Units.METRE   .pow (l));
        if (t != 0) result = result.multiply (Units.SECOND  .pow (t));
        if (i != 0) result = result.multiply (Units.AMPERE  .pow (i));
        if (k != 0) result = result.multiply (Units.KELVIN  .pow (k));
        if (n != 0) result = result.multiply (Units.MOLE    .pow (n));
        if (j != 0) result = result.multiply (Units.CANDELA .pow (j));

        // Find an existing match in the unit system, if possible
        for (Unit<?> u : systemOfUnits.getUnits (result.getDimension ()))
        {
            result = u.getSystemUnit ();
            break;
        }
        int symbolLength = UCUM.format (result).length ();
        for (Unit<?> u : systemOfUnits.getUnits (result.inverse ().getDimension ()))
        {
            Unit<?> temp = u.getSystemUnit ().inverse ();
            int tempLength = UCUM.format (temp).length ();
            if (tempLength < symbolLength)
            {
                result = u.getSystemUnit ().inverse ();
                break;
            }
        }

        dimensions.put (name, result);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void unit (Node node)
    {
        String symbol    = getAttribute (node, "symbol");
        String dimension = getAttribute (node, "dimension");
        int    power     = getAttribute (node, "power",  0);
        double scale     = getAttribute (node, "scale",  1.0);
        double offset    = getAttribute (node, "offset", 0.0);

        Unit unit = dimensions.get (dimension);
        if (unit == null) unit = nmlDimensions.get (dimension);
        if (unit == null) unit = AbstractUnit.ONE;  // fall back, but in general something is broken about the file
        if      (power > 0) unit = unit.transform (MultiplyConverter.ofRational (BigInteger.TEN.pow (power), BigInteger.ONE));
        else if (power < 0) unit = unit.transform (MultiplyConverter.ofRational (BigInteger.ONE,             BigInteger.TEN.pow (-power)));
        else
        {
            if (scale == 1.0)
            {
                unit = unit.shift (offset);
            }
            else
            {
                // UCUM only allows rational numbers, so convert scale
                MultiplyConverter ratio = null;
                if (scale < 1.0)
                {
                    // Attempt to find a simple ratio of 1/integer
                    double inverse = 1.0 / scale;
                    long integer = Math.round (inverse);
                    if (Math.abs (inverse - integer) < epsilon) ratio = MultiplyConverter.ofRational (1, integer);
                }
                if (ratio == null)
                {
                    String s = getAttribute (node, "scale").toLowerCase ();
                    String[] pieces = s.split ("e");
                    int shift = 0;
                    if (pieces.length > 1) shift = Integer.valueOf (pieces[1]);
                    pieces = pieces[0].split (".");
                    if (pieces.length > 1)
                    {
                        shift -= pieces[1].length ();
                        s = pieces[0] + pieces[1];
                    }
                    BigInteger numerator   = new BigInteger (s);
                    BigInteger denominator = new BigDecimal (10).pow (shift).toBigInteger ();
                    ratio = MultiplyConverter.ofRational (numerator, denominator);
                }

                unit = unit.transform (ratio).shift (offset);
            }
        }

        // Since LEMS and NeuroML tend to follow certain naming practices, we may be able to retrieve
        // a more parsimonious unit based on direct name translation.
        String tempName = symbol;
        tempName = tempName.replace ("_per_", "/");
        tempName = tempName.replace ("per_",  "/");
        tempName = tempName.replace ("_",     ".");
        tempName = tempName.replace ("ohm",   "Ohm");
        tempName = tempName.replace ("hour",  "h");
        Unit temp = null;
        try {temp = UCUM.parse (tempName);}
        catch (MeasurementParseException | TokenException e) {}
        if (temp != null  &&  temp.isCompatible (unit))  // found a unit with matching dimension ...
        {
            Number tempScale = temp.getConverterTo (temp.getSystemUnit ()).convert (new Integer (1));
            Number unitScale = temp.getConverterTo (unit.getSystemUnit ()).convert (new Integer (1));
            if (tempScale.equals (unitScale))  // ... and matching scale ...
            {
                int unitLength = UCUM.format (unit).length ();
                int tempLength = UCUM.format (temp).length ();
                if (tempLength <= unitLength)  // ... and at least as parsimonious
                {
                    unit = temp;
                    // Update dimension if this is directly equivalent, but strictly more parsimonious
                    if (power == 0  &&  scale == 1  &&  offset == 0  &&  tempLength < unitLength)
                    {
                        dimensions.put (dimension, temp);
                    }
                }
            }
        }

        ExpressionParser.namedUnits.put (symbol, unit);
    }

    public class ComponentType
    {
        MNode            part;
        MNode            regime;
        int              nextRegimeIndex = 1;  // Never allocate regime 0, because that is the default initial value for all variables. We only want to enter an initial regime explicitly.
        Set<EventChild>  eventChildren   = new HashSet<EventChild> ();   // Events that try to reference a parent port. Attempt to resolve these in postprocessing.
        List<XPath>      paths           = new ArrayList<XPath> ();
        Set<String>      childInstances  = new HashSet<String> ();

        public ComponentType (MNode part)
        {
            this.part = part;
        }

        public ComponentType (Node node)
        {
            String name        = getAttribute (node, "name");
            String inherit     = getAttribute (node, "extends");
            String description = getAttribute (node, "description");
            part = models.childOrCreate (modelName, name);
            part.set ("1", "$lems");
            NameMap nameMap = null;
            if (! inherit.isEmpty ())
            {
                nameMap = partMap.importMap (inherit);
                // In general, we want to give priority to newly defined parts over db parts.
                // Also, in general, they should never overlap.
                // Here we make one feeble attempt to check new part definitions first, but this may
                // fail because parts may not appear in the file in dependency order.
                // Similar comments apply to other name mapping below.
                if (models.child (modelName, inherit) == null)
                {
                    part.set (inherit, "$metadata", "backend", "lems", "extends");  // Remember the original "extends" value, because inherited backend.lems.part usually conflates several base types.
                    inherit = nameMap.internal;
                }
                part.set (inherit, "$inherit");
                addDependencyFromLEMS (part, inherit);
            }
            if (! description.isEmpty ()) part.set (description, "$metadata", "description");

            for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
            {
                if (child.getNodeType () != Node.ELEMENT_NODE) continue;

                String nodeName = child.getNodeName ();
                switch (nodeName)
                {
                    case "Constant":
                    case "Fixed":
                    case "DerivedParameter":
                    case "Parameter":
                    case "IndexParameter":
                    case "Property":
                    case "Exposure":
                        genericVariable (child, "");
                        break;
                    case "Text":
                        name        = getAttribute (child, "name");
                        description = getAttribute (child, "description");
                        part.set ("\"\"", name);
                        if (! description.isEmpty ()) part.set (description, name, "$metadata", "description");
                        break;
                    case "Requirement":
                        name             = getAttribute (child, "name");
                        description      = getAttribute (child, "description");
                        String dimension = getAttribute (child, "dimension");
                        String value = "";
                        if (nameMap != null)
                        {
                            String internalName = nameMap.importName (name);
                            if (! internalName.equals (name))
                            {
                                value = name;
                                name = internalName;
                            }
                        }
                        if (dimension.isEmpty ())
                        {
                            value += "()";
                        }
                        else
                        {
                            Unit<?> unit = dimensions.get (dimension);
                            if (unit == null) value += "(" + dimension + ")";
                            else              value += "(" + UCUM.format (unit) + ")";
                        }
                        if (! description.isEmpty ())
                        {
                            value += " " + description;
                        }
                        part.set (value, "$metadata", "backend", "lems", "requirement", name);
                        break;
                    case "EventPort":
                        name             = getAttribute (child, "name");
                        description      = getAttribute (child, "description");
                        String direction = getAttribute (child, "direction");
                        if (direction.equals ("in")) part.set ("0@" + name, name);  // This should be overridden by a proper event() expression.
                        else                         part.set ("0",         name);   // This should be replaced by a multi-conditional expression
                        part.set (direction, name, "$metadata", "backend", "lems", "port");
                        if (! description.isEmpty ()) part.set (description, name, "$metadata", "description");
                        break;
                    case "Child":
                        name    = getAttribute (child, "name");
                        inherit = getAttribute (child, "type");
                        MNode childPart = part.childOrCreate (name);
                        if (models.child (modelName, inherit) == null) inherit = partMap.importName (inherit);
                        childPart.set (inherit, "$inherit");
                        addDependencyFromLEMS (childPart, inherit);
                        break;
                    case "Children":
                    case "Attachments":
                        // (My best guess is ...)
                        // A named collection of instances that is built at runtime and used mainly for XPath select statements.
                        // The collection has a given type.
                        // A "children" collection receives any subpart that matches the given type. The subparts are
                        // declared ahead of time.
                        // An "attachments" collection is a list of remote parts which connect to this one.
                        name       = getAttribute (child, "name");
                        inherit    = getAttribute (child, "type");
                        String min = getAttribute (child, "min");
                        String max = getAttribute (child, "max");
                        if (inherit.isEmpty ()) inherit = name;
                        String rawInherit = inherit;
                        if (models.child (modelName, inherit) == null) inherit = partMap.importName (inherit);
                        nodeName = nodeName.toLowerCase ();  // This shouldn't hurt the switch statement, because we've already selected this case.
                        if (rawInherit.equals (partMap.exportName (inherit)))  // If our default external name matches the original (raw) external name, then only store the internal part name.
                        {
                            part.set (inherit,                    "$metadata", "backend", "lems", nodeName, name);
                        }
                        else  // Store both the internal name and the original external name, to facilitate more precise re-export.
                        {
                            part.set (inherit + "," + rawInherit, "$metadata", "backend", "lems", nodeName, name);
                        }
                        addDependencyFromLEMS (part.child ("$metadata", "backend", "lems", nodeName, name), inherit);
                        if (! min.isEmpty ()) part.set (min, "$metadata", "backend", "lems", nodeName, name, "min");
                        if (! max.isEmpty ()) part.set (max, "$metadata", "backend", "lems", nodeName, name, "max");
                        break;
                    case "ComponentReference":    // alias of a referenced part; jLEMS does resolution; "local" means the referenced part is a sibling, otherwise resolution includes the entire hierarchy
                    case "Link":                  // equivalent to "ComponentReference" with local flag set to true
                    case "InstanceRequirement":   // Only existing example is the peer of a gap junction. Seems to be unary connection that gets resolved by Tunnel.
                    case "Path":
                        name        = getAttribute (child, "name");
                        inherit     = getAttribute (child, "type");
                        description = getAttribute (child, "description");
                        if (models.child (modelName, inherit) == null) inherit = partMap.importName (inherit);
                        part.set ("connect(" + inherit + ")", name);
                        if (! inherit    .isEmpty ()) addDependencyFromLEMS (part.child (name), inherit);
                        if (! description.isEmpty ()) part.set (description, name, "$metadata", "description");
                        break;
                    case "Dynamics":
                        dynamics (child);
                        break;
                    // "ComponentRequirement" -- Based on examples, seems to name a ComponentReference declared in container of this part, for use by Tunnel to create a continuous (non-event) connection.
                    // Note: "Tunnel" and associated terms would translate to some ugly structures, just to support a fairly specific use-case. Instead, construct a nicer structure in the NeuroML network handler above.
                    case "Structure":
                        structure (child);
                        break;
                    // "Simulation" -- Supports glue-code for jLEMS, much of which appears in the include file Simulation.xml
                }
            }
        }

        public MNode genericVariable (Node node, String condition1)
        {
            String name = getAttributes (node, "name", "parameter", "variable");
            String nodeName = node.getNodeName ();
            if (nodeName.equals ("TimeDerivative")) name += "'";

            String exposure    = getAttribute (node, "exposure");
            String description = getAttribute (node, "description");
            String dimension   = getAttribute (node, "dimension");
            String reduce      = getAttribute (node, "reduce");
            String required    = getAttribute (node, "required");
            String select      = getAttribute (node, "select");

            MNode result = part.childOrCreate (name);

            String combiner = "";
            if (nodeName.contains ("DerivedVariable")  &&  (exposure.isEmpty ()  ||  ! exposure.equals (name))) combiner = ":";

            String value = cleanupExpression (getAttributes (node, "value", "defaultValue"));
            if (value.isEmpty ()  &&  ! select.isEmpty ())
            {
                XPath variablePath = new XPath (select);
                variablePath.reduceTo = name;
                variablePath.required = required.equals ("true");
                variablePath.resolve (part);
                if (variablePath.isDirect) value = variablePath.directName ();
                else if (! dimension.isEmpty ()) result.set (dimension, "$metadata", "backend", "lems", "dimension");  // Need to stash dimension, since there's no way to compute it.

                // At this point we don't know enough about the path, because some requisite parts may
                // not be defined yet. Re-evaluate during postprocessing.
                paths.add (variablePath);

                switch (reduce)
                {
                    case "multiply": combiner = "*"; break;
                    case "add"     : combiner = "+"; break;
                }
            }

            int equationCount = Variable.equationCount (result);
            String existingValue = result.get ();
            if (! existingValue.isEmpty ()  &&  ! Variable.isCombiner (existingValue))
            {
                // Move existing value into multi-conditional list
                ParsedValue pv = new ParsedValue (existingValue);
                result.set (pv.combiner);
                if (! pv.condition.isEmpty ()  ||  ! pv.expression.startsWith ("0"))  // Only add the equation if not a placeholder
                {
                    result.set (pv.expression, "@" + pv.condition);
                    equationCount++;
                }
            }

            if (! combiner.isEmpty ()) result.set (combiner);
            if (! value.isEmpty ())
            {
                if (equationCount == 0)
                {
                    String newValue = result.get () + value;
                    if (! condition1.isEmpty ()) newValue += "@" + condition1;
                    result.set (newValue);
                }
                else
                {
                    result.set (value, "@" + condition1);
                }
                equationCount++;
            }
            if (! description.isEmpty ()) result.set (description, "$metadata", "description");
            if (! exposure.isEmpty ())
            {
                if (exposure.equals (name))
                {
                    result.childOrCreate ("$metadata", "backend", "lems", "expose");
                }
                else
                {
                    result.set (exposure, "$metadata", "backend", "lems", "expose");
                    part.set (name, exposure);  // Create an alias so that other parts can find the value.
                }
            }

            if (   nodeName.equals ("Parameter")
                || nodeName.equals ("IndexParameter")
                || nodeName.equals ("Path")
                || nodeName.equals ("Text"))
            {
                result.childOrCreate ("$metadata", "param");  // Intended as public interface to this component
            }

            for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
            {
                if (child.getNodeType () == Node.ELEMENT_NODE  &&  child.getNodeName ().equals ("Case")) equationCount++;
            }
            for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
            {
                if (child.getNodeType () == Node.ELEMENT_NODE  &&  child.getNodeName ().equals ("Case"))
                {
                    value             = cleanupExpression (getAttribute (child, "value"));
                    String condition2 = cleanupExpression (getAttribute (child, "condition"));
                    if (! condition1.isEmpty ())
                    {
                        if (condition2.isEmpty ()) condition2 = condition1;
                        else                       condition2 = condition1 + "&&" + condition2;
                    }
                    if (equationCount > 1)
                    {
                        result.set (value, "@" + condition2);
                    }
                    else
                    {
                        String newValue = result.get () + value;
                        if (! condition2.isEmpty ()) newValue += "@" + condition2;
                        result.set (newValue);
                    }
                }
            }

            // Force metadata-only variable to have a value, so it is not interpreted as a part.
            if (equationCount == 0  &&  result.get ().isEmpty ())
            {
                value = "0";
                if (! dimension.isEmpty ())
                {
                    Unit<?> unit = dimensions.get (dimension);
                    if (unit != null) value += UnitValue.safeUnit (unit);
                }
                result.set (value);
            }

            return result;
        }

        public String cleanupExpression (String expression)
        {
            expression = expression.replace (".gt.",  ">");
            expression = expression.replace (".lt.",  "<");
            expression = expression.replace (".geq.", ">=");
            expression = expression.replace (".leq.", "<=");
            expression = expression.replace (".eq.",  "==");
            expression = expression.replace (".neq.", "!=");
            expression = expression.replace (".and.", "&&");
            expression = expression.replace (".or.",  "||");
            expression = expression.replace (" ",     "");
            return expression;
        }

        public void dynamics (Node node)
        {
            for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
            {
                if (child.getNodeType () != Node.ELEMENT_NODE) continue;
                switch (child.getNodeName ())
                {
                    case "StateVariable":
                    case "DerivedVariable":
                    case "ConditionalDerivedVariable":
                    case "TimeDerivative":
                        genericVariable (child, "");
                        break;
                    case "OnStart":
                        onStart (child, "$init");
                        break;
                    case "OnEvent":
                    case "OnCondition":
                        onCondition (child, "");
                        break;
                    case "Regime":
                        regime (child);
                        break;
                    case "KineticScheme":
                        kineticScheme (child);
                        break;
                }
            }
        }

        public void onStart (Node node, String condition)
        {
            for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
            {
                if (child.getNodeType () != Node.ELEMENT_NODE) continue;
                genericVariable (child, condition);
            }
        }

        public void onCondition (Node node, String condition1)
        {
            String condition2 = cleanupExpression (getAttribute (node, "test"));
            if (condition2.isEmpty ()) condition2 = getAttribute (node, "port");  // TODO: process port to resolve source part
            if (! condition1.isEmpty ()) condition2 = condition1 + "&&" + condition2;

            for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
            {
                if (child.getNodeType () != Node.ELEMENT_NODE) continue;
                switch (child.getNodeName ())
                {
                    case "StateAssignment":
                        genericVariable (child, condition2);
                        break;
                    case "EventOut":
                        String portName = getAttribute (child, "port");
                        part.set ("1",   portName, "@" + condition2);
                        // The following lines will likely be redundant, if the port variable has already been set up.
                        part.set ("",    portName);  // Clear any placeholder value from "EventPort" declaration.
                        part.set ("0",   portName, "@");
                        part.set ("out", portName, "$metadata", "backend", "lems", "port");
                        break;
                    case "Transition":
                        String regimeName = getAttribute (child, "regime");
                        regime.set (regimeName, "@" + condition2);
                        break;
                }
            }
        }

        public void allocateRegime (String name)
        {
            if (regime == null)
            {
                regime = part.childOrCreate ("$regime");
                regime.set ("-1", "@$init");  // No active regime at startup
            }
            MNode regimeValue = part.child (name);
            if (regimeValue == null) part.set (nextRegimeIndex++, name);
        }

        public void regime (Node node)
        {
            String name    = getAttribute (node, "name");
            String initial = getAttribute (node, "initial");

            allocateRegime (name);
            if (initial.equals ("true")) regime.set (name, "@$init");

            for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
            {
                if (child.getNodeType () != Node.ELEMENT_NODE) continue;
                switch (child.getNodeName ())
                {
                    case "TimeDerivative":
                        MNode d = genericVariable (child, "$regime==" + name);
                        // Ensure that a default of zero is created
                        if (Variable.equationCount (d) == 0)
                        {
                            Variable.ParsedValue pv = new Variable.ParsedValue (d.get ());
                            d.set (pv.combiner);  // This is unlikely to contain any value.
                            d.set (pv.expression, "@" + pv.condition);
                            d.set (0,             "@");
                        }
                        break;
                    case "OnEntry":
                        onStart (child, "event($regime==" + name + ")");
                        break;
                    case "OnCondition":
                        onCondition (child, "$regime==" + name);
                        break;
                }
            }
        }

        /**
            Add necessary dynamics to implement a kinetic scheme.
        **/
        public void kineticScheme (Node node)
        {
            String stateVariable = getAttribute (node, "stateVariable");
            String nodes         = getAttribute (node, "nodes");
            String edges         = getAttribute (node, "edges");
            String edgeSource    = getAttribute (node, "edgeSource");
            String edgeTarget    = getAttribute (node, "edgeTarget");
            String forwardRate   = getAttribute (node, "forwardRate");
            String reverseRate   = getAttribute (node, "reverseRate");

            // The comments in the LEMS examples suggest that a kinetic scheme is a Markov model,
            // so it might make sense to enforce a probability distribution (that is, sum to 1)
            // across the states. However, nothing in the LEMS models gives an initial state,
            // so they must all start at 0. That being the case, don't worry about normalization.

            String inherit = part.get ("$metadata", "backend", "lems", "children", nodes).split (",")[0];  // No need to also check attachments, because kinetic scheme only works with children parts
            if (! inherit.isEmpty ())
            {
                MNode parent = AppData.models.child (inherit);
                if (parent == null)  // Newly imported part, so we can modify it.
                {
                    parent = models.childOrCreate (modelName, inherit);
                    if (parent != null)
                    {
                        Variable.ParsedValue pv = new Variable.ParsedValue (parent.get (stateVariable));
                        if (pv.expression.isEmpty ()) pv.expression = "0";
                        if (pv.condition .isEmpty ()) pv.condition  = "$init";
                        parent.set (pv, stateVariable);
                    }
                }
            }

            inherit = part.get ("$metadata", "backend", "lems", "children", edges).split (",")[0];
            if (! inherit.isEmpty ())
            {
                MNode parent = AppData.models.child (inherit);
                if (parent == null)
                {
                    parent = models.childOrCreate (modelName, inherit);
                    if (parent != null)
                    {
                        parent.set ("+-" + reverseRate, edgeSource + "." + stateVariable + "'");
                        parent.set ("+"  + forwardRate, edgeTarget + "." + stateVariable + "'");
                    }
                }
            }
        }

        public void structure (Node node)
        {
            Map<String,String> with = new HashMap<String,String> ();

            for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
            {
                if (child.getNodeType () != Node.ELEMENT_NODE) continue;
                switch (child.getNodeName ())
                {
                    case "ChildInstance":
                        String component = getAttribute (child, "component");
                        childInstances.add (component);
                        break;
                    case "With":  // Can be a Path, "this" or "parent"
                        String instance = getAttribute (child, "instance");
                        String as       = getAttribute (child, "as");
                        // Prepare the value to be used directly in an event expression.
                        if      (instance.equals ("this"  )) instance = "";
                        else if (instance.equals ("parent")) instance = "$up.";
                        else                                 instance += ".";
                        with.put (as, instance);
                        break;
                    case "EventConnection":
                        eventConnection (child, with);
                        break;
                    // "MultiInstantiate" -- Turns this part into a population. In practice, only used to define the Population component type.
                    // "Tunnel" -- Seems to establish a continuous (non-event) connection, similar to a regular connection in N2A. Also injects a child instance on each side of the connection. Child instances may be of different types.
                }
            }
        }

        public void eventConnection (Node node, Map<String,String> with)
        {
            String from       = getAttribute (node, "from");
            String to         = getAttribute (node, "to");
            String sourcePort = getAttribute (node, "sourcePort");
            String targetPort = getAttribute (node, "targetPort");
            String delay      = getAttribute (node, "delay");

            String property = "";
            String value    = "";
            for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
            {
                if (child.getNodeType () != Node.ELEMENT_NODE) continue;
                if (child.getNodeName ().equals ("Assign"))
                {
                    property = getAttribute (child, "property");
                    value    = getAttribute (child, "value");
                }
            }

            // LEMS resolves EventConnection at runtime.
            // The best we can do now is leave a hint for the user to resolve it by hand.
            // However, in some special cases it is possible to fully translate.

            from = with.get (from); 
            to   = with.get (to);

            boolean portsKnown = true;
            if (sourcePort.isEmpty ())
            {
                if (from.isEmpty ())
                {
                    for (MNode c : part)
                    {
                        if (c.get ("$metadata", "backend", "lems", "port").equals ("out"))
                        {
                            sourcePort = c.key ();
                            break;
                        }
                    }
                }
                if (sourcePort.isEmpty ())
                {
                    sourcePort = "out";
                    portsKnown = false;
                }
            }
            if (targetPort.isEmpty ())
            {
                if (to.isEmpty ())
                {
                    for (MNode c : part)
                    {
                        if (c.get ("$metadata", "backend", "lems", "port").equals ("in"))
                        {
                            targetPort = c.key ();
                            break;
                        }
                    }
                }
                if (targetPort.isEmpty ())
                {
                    targetPort = "in";
                    portsKnown = false;
                }
            }

            String event = "";
            if (delay.isEmpty ())
            {
                event = from + sourcePort;
            }
            else
            {
                event = "event(" + from + sourcePort + "," + delay + ")";
            }

            MNode v = part.childOrCreate (to + targetPort);
            if (to.isEmpty ())
            {
                v.set (":" + event);  // Let local port be a temporary
            }
            else
            {
                v.set ("1@" + event);  // Connection port must be a regular variable.
                v.set ("Consider moving this event into the target part, where the port variable can be temporary.", "$metadata", "warning1");
            }
            v.childOrCreate ("$metadata", "backend", "lems", "event");
            if (! portsKnown)
            {
                v.set ("The identity of the source or target port could not be fully determined.", "$metadata", "warning2");
                // For events that references ports in their prospective container, check during post-processing
                // to see if they have been inserted in a container.
                if (to  .startsWith ("$up")) eventChildren.add (new EventChild (part, v, targetPort));
                if (from.startsWith ("$up")) eventChildren.add (new EventChild (part, v, sourcePort));
            }
            if (! property.isEmpty ()) part.set (value, to + property);
        }

        /**
            Store events to be post-processed for possible resolution of port in container.
            This is an expensive and desperate measure.
        **/
        public class EventChild
        {
            public MNode  part;      // that directly contains the event
            public MNode  event;     // The variable that processes the event.
            public String portName;  // Should be "in" or "out", which also indicates direction.

            public EventChild (MNode part, MNode event, String portName)
            {
                this.part     = part;
                this.event    = event;
                this.portName = portName;
            }

            public void process ()
            {
                // Collect the entire parent hierarchy for the part.
                // Anything that contains the part, or even an ancestor of the part, counts as a container
                // and should be searched for a candidate port.
                Set<String> family = new HashSet<String> ();
                List<MNode> parents = collectParents (part);
                for (MNode p : parents) family.add (p.key ());
                family.add (part.key ());  // The part itself also belongs in the family, as we are most interested in a direct container.

                // Scan all imported models to see if they contain any member of the family.
                // TODO: should also scan existing models in main db.
                for (MNode potentialContainer : models.child (modelName))
                {
                    if (! containsChild (potentialContainer, family)) continue;

                    // Search container for a port of the given direction.
                    for (MNode c : potentialContainer)
                    {
                        if (c.get ("$metadata", "backend", "lems", "port").equals (portName))
                        {
                            String newPortName = c.key ();
                            if (portName.equals ("in"))
                            {
                                part.move (event.key (), "$up." + newPortName);
                            }
                            else  // "out"
                            {
                                event.set (event.get ().replace (portName, newPortName));
                            }
                            return;  // As soon as we find any port of the correct direction, we're done.
                        }
                    }
                }
            }

            public boolean containsChild (MNode container, Set<String> family)
            {
                MNode children = container.child ("$metadata", "backend", "lems", "children");
                if (children != null)
                {
                    for (MNode m : children)
                    {
                        // If the child type happens to be any member of the family,
                        // then the candidate container is indeed the kind we are seeking.
                        if (family.contains (m.get ().split (",")[0])) return true;
                    }
                }

                MNode attachments = container.child ("$metadata", "backend", "lems", "attachments");
                if (attachments != null)
                {
                    for (MNode m : attachments)
                    {
                        if (family.contains (m.get ().split (",")[0])) return true;
                    }
                }

                return false;
            }
        }

        public void finish1 ()
        {
            // Parts that were explicitly instantiated by a structure element should be
            // converted from connections into subparts (if they were in fact created as connections).
            for (String component : childInstances)
            {
                MNode sourceNode = part.child (component);
                if (sourceNode == null)
                {
                    // Search for definition in some parent
                    List<MNode> parents = collectParents (part);
                    sourceNode = definitionFor (component, parents);
                    if (sourceNode == null) sourceNode = part.childOrCreate (component);
                }
                MNode targetNode = part.childOrCreate (component);
                String inherit = sourceNode.get ();  // Will either be blank or a connect() line
                if (inherit.startsWith ("connect("))
                {
                    inherit = inherit.replace ("connect(", "");
                    inherit = inherit.replace (")",        "");
                    targetNode.set (null);
                    targetNode.set (inherit, "$inherit");
                    if (sourceNode != targetNode) addDependencyFromLEMS (targetNode, inherit);
                    // No need to call addDependency() if sourcePart is local, because it was already called when the connect() line was created.
                }
            }
        }

        public void finish2 ()
        {
            for (EventChild ec : eventChildren) ec.process ();

            // Clean ups:
            // * Prevent placeholder from overriding its time derivative. (LEMS doesn't appear to support higher derivatives.)
            // * Prevent inherited conditions from defeating regime-specific conditions in child part
            List<MNode> parents = collectParents (part);
            Iterator<MNode> it = part.iterator ();
            while (it.hasNext ())
            {
                MNode v = it.next ();
                MNode vprime = part.child (v.key () + "'");  // retrieve associated time derivative, if it exists
                if (vprime != null)
                {
                    String value = v.get ();
                    if (! value.isEmpty ()  &&  ! Variable.isCombiner (value))
                    {
                        ParsedValue pv = new ParsedValue (value);
                        if (pv.condition.isEmpty ()  &&  pv.expression.startsWith ("0"))  // v is a placeholder
                        {
                            // If v has no children, and only a local definition, it can be removed.
                            if (v.size () == 0  &&  definitionFor (v.key (), parents) == null)
                            {
                                it.remove ();
                                continue;
                            }
                            pv.condition = "$init";
                            v.set (pv);
                        }
                    }
                }

                List<String> kill = new ArrayList<String> ();
                for (MNode e : v)
                {
                    String condition = e.key ();
                    if (condition.startsWith ("@")  &&  condition.contains ("$regime"))
                    {
                        // Scan parents for matching sub-condition
                        condition = condition.substring (1);
                        for (MNode p : parents)
                        {
                            MNode pv = p.child (v.key ());
                            if (pv == null) continue;
                            for (MNode pe : pv)
                            {
                                String pc = pe.key ();
                                if (pc.equals ("@")) continue;  // Retain the default equation
                                if (! pc.startsWith ("@")) continue;
                                pc = pc.substring (1);
                                if (pc.length () < condition.length ()  &&  condition.contains (pc))
                                {
                                    kill.add (pc);
                                }
                            }
                        }
                    }
                }
                for (String pc : kill) v.set ("", "@" + pc);  // For conditional equations, used "" rather than "$kill".
            }

            // Pretty print and name mapping

            PrettyPrinter pp = new PrettyPrinter ();

            if (regime != null)
            {
                if (regime.size () == 1)  // One conditional entry, so convert to single-line.
                {
                    MNode c = regime.iterator ().next ();
                    String key = c.key ();
                    regime.set (c.get () + key);
                    regime.clear (key);
                }

                // Allocate safe name
                String regimeName = "regime";
                int suffix = 2;
                while (part.child (regimeName) != null) regimeName = "regime" + suffix++;
                if (! regimeName.equals ("regime")) regime.childOrCreate ("$metadata", "backend", "lems", "regime");
                pp.rename.put ("$regime", regimeName);
            }

            // Inject name mappings from parent part into pretty printer
            MNode parent = findBasePart (part);
            if (parent != null)
            {
                MPart mparent = new MPart (parent);

                NameMap nameMap = partMap.outward.get (parent.key ());
                if (nameMap == null)
                {
                    nameMap = new NameMap (mparent);
                    nameMap.inheritContainers (partMap);
                    nameMap.buildContainerMappings ();
                }
                //nameMap.dump ();
                pp.rename.putAll (nameMap.inward);

                // Avoid hiding names from containers
                Set<String> targets = new TreeSet<String> (pp.rename.values ());
                for (MNode c : part)
                {
                    String key = c.key ();
                    if (mparent.child (key) != null) continue;  // If the new part declares a variable with the same name as one in the part it is extending, then the intent is probably to override.
                    if (! targets.contains (key)) continue;

                    int suffix = 2;
                    String newName = key + suffix++;
                    while (targets.contains (newName)  ||  part.child (newName) != null  ||  mparent.child (newName) != null) newName = key + suffix++;
                    pp.rename.put (key, newName);
                    targets.add (newName);
                }
            }
            if (part.child ("t") == null) pp.rename.put ("t", "$t");  // If t is not locally defined, then it is the same as $t.

            pp.process (part);

            // Paths
            for (XPath path : paths)
            {
                String name = pp.mapName (path.reduceTo);
                path.resolve (part);
                if (path.isDirect)
                {
                    path.finishDirectPath (part, name);
                }
                else
                {
                    String combiner = part.get (name);
                    if (combiner.length () > 1) combiner = combiner.substring (0, 1);
                    if (! Variable.isCombiner (combiner)  ||  combiner.equals (":")) combiner = "";
                    part.set (combiner, name);

                    MNode  container = path.container ();
                    String up        = path.up ();
                    String target    = path.target ();
                    String condition = path.condition ();
                    if (condition.isEmpty ()) container.set (combiner + target,                   up + name);
                    else                      container.set (combiner + target + "@" + condition, up + name);
                    if (path.required) container.childOrCreate (up + name, "$metadata", "backend", "lems", "required");
                }
            }
        }
    }

    public class XPathPart
    {
        public String partName;
        public String condition = "";
        public MNode  part;

        public XPathPart (String part)
        {
            String[] pieces = part.split ("\\[", 2);
            partName = pieces[0];
            if (pieces.length > 1)
            {
                String temp = pieces[1].split ("]")[0];
                if (! temp.equals ("*"))
                {
                    // LEMS strives to follow the XPath standard. The following is a very crude and limited parser.
                    if (temp.contains ("="))
                    {
                        condition = temp.replace ("=", "==");
                        condition = condition.replace ("'", "\"");
                    }
                    else
                    {
                        int index = 0;
                        try {index = Integer.valueOf (temp);}
                        catch (NumberFormatException e) {}
                        condition = "$index==" + index;
                    }
                }
            }
        }
    }

    public class XPath
    {
        public List<XPathPart> parts    = new ArrayList<XPathPart> ();
        public boolean         isDirect = true;  // Indicates that a direct reference is possible. Each element of the path must be a single subpart of the one above it.
        // For reductions ...
        public boolean         required;  // There must be at least one item in the reduction.
        public String          reduceTo;  // Name of variable that receives the reduction.

        public XPath (String path)
        {
            String[] pieces = path.split ("/");
            for (String p : pieces) parts.add (new XPathPart (p));
        }

        /**
            Determine the parts along the path.
        **/
        public void resolve (MNode root)
        {
            MNode current = root;
            int i = 0;
            for (XPathPart p : parts)
            {
                if (! p.condition.isEmpty ()) isDirect = false;

                List<MNode> lineage = collectParents (current);
                lineage.add (current);

                // Name-map the last path entry (the variable)
                if (i == parts.size () - 1)
                {
                    MNode base = findBasePart (current);
                    if (base != null)
                    {
                        NameMap nameMap = partMap.exportMap (base);
                        p.partName = nameMap.importName (p.partName);
                    }
                }

                // Try members of root or its parents
                MNode next = definitionFor (p.partName, lineage);
                // If that fails, try aliases
                if (next == null)
                {
                    Set<String> names = aliases.get (p.partName);
                    if (names != null)
                    {
                        for (String n : names)
                        {
                            next = definitionFor (n, lineage);
                            if (next != null) break;
                        }
                    }
                }
                // If that fails, try child part
                if (next == null)
                {
                    String typeName = typeFor (p.partName, lineage);
                    if (! typeName.isEmpty ())
                    {
                        next = models.child (modelName, typeName);
                        isDirect = false;
                    }
                }
                // If that fails, check for a folded part
                if (next == null)
                {
                    String inherit = current.get ("$inherit").replaceAll ("\"", "");
                    if (p.partName.equals (inherit))  // When an ionChannel gets folded into a channelPopulation, and made into a standalone part, the standalone part is named after the embedded ionChannel.
                    {
                        next = current;
                    }
                }
                // If that fails, check for folded connection
                if (next == null)
                {
                    // Scan for connections that target current, and check if the other target matches the given name.
                    MNode previous;
                    if (i >= 2) previous = parts.get (i - 2).part;
                    else        previous = root;
                    String key = current.key ();
                    for (MNode c : previous)  // c should be a peer of current
                    {
                        if (c == current) continue;
                        if (! c.get ("B").equals (key)) continue;
                        // Now we have a part that appears to name current as a connection target.
                        String inherit = c.get ("$inherit").replace ("\"", "");
                        if (p.partName.equals (inherit))
                        {
                            next = c;
                            break;
                        }
                    }
                }

                // Check if connection
                if (next != null)
                {
                    String connection = next.get ();
                    if (connection.startsWith ("connect("))
                    {
                        connection = connection.replace ("connect(", "");
                        connection = connection.replace (")",        "");
                        if (! connection.isEmpty ()) next = models.child (modelName, connection);
                    }
                }

                p.part  = next;
                current = next;
                if (current == null) break;
                i++;
            }
        }

        /**
            Assembles condition string to filter down to exactly the instance indicated by the path.
            Depends on resolveComponent(MNode) or resolveSimulation(MNode).
        **/
        public String condition ()
        {
            String result = "";
            String up = "";
            for (int i = parts.size () - 2; i >= 0; i--)
            {
                XPathPart p = parts.get (i);
                String condition = p.condition;
                if (! condition.isEmpty ())
                {
                    // Only add index if this population is not a singleton.
                    if (condition.equals ("$index==0")  &&  (p.part == null  ||  p.part.getOrDefault (1, "$n") == 1))
                    {
                        condition = "";
                    }
                    if (! condition.isEmpty ())
                    {
                        if (! result.isEmpty ()) result += "&&";
                        result += up + condition;
                    }
                }
                up += "$up.";
            }
            return result;
        }

        public MNode container ()
        {
            int size = parts.size ();
            if (size < 2) return null;
            return parts.get (size - 2).part;
        }

        public String up ()
        {
            String result = "";
            for (int i = 0; i < parts.size () - 1; i++) result = "$up." + result;
            return result;
        }

        public String target ()
        {
            XPathPart p = parts.get (parts.size () - 1);
            return p.partName;
        }

        public String directName ()
        {
            String result = "";
            for (XPathPart p : parts)
            {
                if (! result.isEmpty ()) result += ".";
                if      (  p.partName.equals ("..")) result += "$up";
                else if (! p.partName.equals ("." )) result += p.partName;
            }
            return result;
        }

        public void finishDirectPath (MNode root, String name)
        {
            MNode source = root;
            for (int i = 0; i < parts.size () - 1; i++)
            {
                XPathPart p = parts.get (i);
                if (source.get (p.partName).startsWith ("connect(")) return;  // We only modify the variable if it does not go through a connection, that is, only if the variable is fully contained under us.

                name = "$up." + name;
                MNode child = source.child (p.partName);
                if (child == null)
                {
                    List<MNode> parents = collectParents (source);
                    child = definitionFor (p.partName, parents);
                }
                root = root.childOrCreate (p.partName);
                if (child == null) source = root;
                else               source = child;
            }

            // Check if variable exists in final source part
            MNode child = source.child (name);
            if (child == null)
            {
                List<MNode> parents = collectParents (source);
                child = definitionFor (name, parents);
            }
            if (child != null) root.set ("$kill", name);
        }
    }

    public class PrettyPrinter extends Renderer
    {
        Map<String,String> rename = new TreeMap<String,String> ();

        public String mapName (String name)
        {
            String result = rename.get (name);
            if (result != null) return result;
            return name;
        }

        @SuppressWarnings("rawtypes")
        public boolean render (Operator op)
        {
            if (op instanceof Constant)
            {
                Constant c = (Constant) op;
                if (c.value instanceof Scalar)
                {
                    UnitValue uv    = c.unitValue;
                    Unit<?>   unit  = uv.unit;
                    double    value = uv.value;  // The original given value, without scaling.
                    if (unit == null)
                    {
                        result.append (ImportJob.print (value));
                    }
                    else
                    {
                        if (unit instanceof TransformedUnit)
                        {
                            TransformedUnit tu = (TransformedUnit) unit;
                            Unit parent = tu.getParentUnit ();
                            String parentString = UCUM.format (parent);
                            if (parentString.contains (".")  ||  parentString.contains ("/"))
                            {
                                unit  = parent;
                                value = tu.getConverter ().convert (value);
                            }
                        }

                        result.append (ImportJob.print (value));
                        result.append (UnitValue.safeUnit (unit));
                    }
                    return true;
                }
            }
            else if (op instanceof AccessVariable)
            {
                AccessVariable av = (AccessVariable) op;
                result.append (mapName (av.name));
                return true;
            }
            else if (op instanceof AccessElement)  // This is how an unknown function shows up.
            {
                AccessElement ae = (AccessElement) op;
                String name = ((AccessVariable) ae.operands[0]).name;
                if (name.equals ("random"))
                {
                    result.append ("uniform(");
                    ae.operands[1].render (this);
                    result.append (")");
                    return true;
                }
            }
            return false;
        }

        public String print (String line)
        {
            try
            {
                result.setLength (0);  // clear any previous output
                Operator.parse (line).render (this);
                return result.toString ().replace (" ", "");
            }
            catch (Exception e)
            {
                return line;
            }
        }

        public void process (MNode node)
        {
            List<String> keys = new ArrayList<String> ();
            for (MNode c : node)
            {
                String key = c.key ();
                keys.add (key);
                Variable.ParsedValue pv = new Variable.ParsedValue (c.get ());
                if (! pv.expression.isEmpty ()) pv.expression = print (pv.expression);
                if (! pv.condition .isEmpty ()) pv.condition  = print (pv.condition);
                c.set (pv);

                if (key.equals ("$metadata" )) continue;
                if (key.equals ("$reference")) continue;
                process (c);
            }
            for (String key : keys)
            {
                String newKey = key;
                if (key.startsWith ("@"))
                {
                    if (key.length () > 1) newKey = "@" + print (key.substring (1));
                }
                else
                {
                    newKey = "";
                    String[] pieces = key.split ("\\.");
                    for (String p : pieces)
                    {
                        String name = rename.get (p);
                        if (name != null) p = name;
                        if (! newKey.isEmpty ()) newKey += ".";
                        newKey += p;
                    }
                }
                if (! newKey.equals (key)) node.move (key, newKey);
            }
        }
    }
}
