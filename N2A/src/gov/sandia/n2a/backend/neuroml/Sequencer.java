/*
Copyright 2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.neuroml;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
    Enforces the order of XML elements.
**/
public class Sequencer extends XMLutility
{
    Map<String,SequencerElement> elements = new HashMap<String,SequencerElement> ();
    Map<String,String>           alias    = new HashMap<String,String> ();

    public SequencerElement getSequencerElement (Element parent)
    {
        String tag = parent.getTagName ();
        return getSequencerElement (tag);
    }

    public SequencerElement getSequencerElement (String tag)
    {
        String a = alias.get (tag);
        if (a == null) a = tag;
        return elements.get (a);
    }

    /**
        Applies ordering to unsorted nodes and adds them to a parent node.
        Chooses type based parent's tag.
    **/
    public void append (Element parent, Collection<Element> unsorted)
    {
        SequencerElement se = getSequencerElement (parent);
        if (se == null)
        {
            for (Element c : unsorted) parent.appendChild (c);
        }
        else
        {
            se.append (parent, unsorted);
        }
    }

    /**
        Applies ordering to unsorted nodes and adds them to a parent node.
        Type is specified explicitly by caller.
    **/
    public void append (String type, Element parent, Collection<Element> unsorted)
    {
        SequencerElement s = elements.get (type);
        if (s == null)
        {
            for (Element c : unsorted) parent.appendChild (c);
        }
        else
        {
            s.append (parent, unsorted);
        }
    }

    public boolean hasID (String tag)
    {
        SequencerElement se = getSequencerElement (tag);
        if (se == null) return true;  // Most likely this is a user-defined LEMS Component, which does use ID.
        return se.attributes.contains ("id");
    }

    public boolean isRequired (Element parent, String param)
    {
        SequencerElement se = getSequencerElement (parent);
        if (se == null) return false;
        return se.required.contains (param);
    }

    public String bestFieldName (Element parent, String fieldNames)
    {
        String[] pieces = fieldNames.split (",");
        SequencerElement se = getSequencerElement (parent);
        if (se == null) return pieces[0];

        String result = pieces[0];
        int bestRank = Integer.MAX_VALUE;
        for (String f : pieces)
        {
            int rank = se.attributes.indexOf (f);
            if (rank >= 0  &&  rank < bestRank)
            {
                bestRank = rank;
                result = f;
            }
        }
        return result;
    }

    public void loadXSD (String source)
    {
        try
        {
            // Open and parse the XSD document
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance ();
            factory.setCoalescing (true);
            factory.setIgnoringComments (true);
            factory.setIgnoringElementContentWhitespace (true);
            factory.setXIncludeAware (true);
            DocumentBuilder builder = factory.newDocumentBuilder ();
            Document doc = builder.parse (Sequencer.class.getResource (source).openStream ());

            // Extract element definitions
            process (doc);

            // Post-process to collate groups and base elements
            Iterator<Entry<String,SequencerElement>> it = elements.entrySet ().iterator ();
            while (it.hasNext ())
            {
                Entry<String,SequencerElement> e = it.next ();
                SequencerElement se = e.getValue ();
                se.resolve ();
            }

            // Clean up extraneous entries
            it = elements.entrySet ().iterator ();
            while (it.hasNext ())
            {
                SequencerElement se = it.next ().getValue ();
                if (se.name.startsWith (">")  ||  (se.children.isEmpty ()  &&  se.attributes.isEmpty ())) it.remove ();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace ();
        }
    }

    public void process (Node node)
    {
        if (node.getNodeType () != Node.ELEMENT_NODE)  // For all other node types, recurse into structure, looking for ELEMENT nodes.
        {
            for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ()) process (child);
            return;
        }

        if (node.getNodeName ().contains ("schema")) schema (node);
    }

    public void schema (Node node)
    {
        for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
        {
            String name = child.getNodeName ();
            if (name.contains ("complexType")  ||  name.contains ("group"))
            {
                SequencerElement se = new SequencerElement ();
                se.name = getAttribute (child, "name");
                if (name.contains ("group")) se.name = ">" + se.name;
                elements.put (se.name, se);
                se.complexType (child);
            }
            else if (name.contains ("element"))  // a top-level element
            {
                // Treat as an alias for its type, so inherit from type.
                String tag  = getAttribute (child, "name");
                String type = getAttribute (child, "type");
                alias.put (tag, type);
            }
        }
    }

    public class SequencerElement
    {
        public String       name;  // of the container type
        public String       base;  // of type that we inherit from; null if none or already processed. Used to assemble full sequences during post-processing of XSD.
        public List<String> children   = new ArrayList<String> ();
        public List<String> attributes = new ArrayList<String> ();
        public List<String> required   = new ArrayList<String> ();  // subset of attributes that must be output with this element
        public boolean      resolved;

        public void append (Element parent, Collection<Element> unsorted)
        {
            Map<Integer,ArrayList<Element>> sorted = new TreeMap<Integer,ArrayList<Element>> ();
            for (Element c : unsorted)
            {
                String tag = c.getTagName ();
                int index = children.indexOf (tag);
                if (index < 0) index = children.size ();
                ArrayList<Element> bucket = sorted.get (index);
                if (bucket == null)
                {
                    bucket = new ArrayList<Element> ();
                    sorted.put (index, bucket);
                }
                bucket.add (c);
            }
            for (ArrayList<Element> bucket : sorted.values ())
            {
                for (Element c : bucket)
                {
                    parent.appendChild (c);
                }
            }
        }

        public void complexType (Node node)
        {
            for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
            {
                String name = child.getNodeName ();
                if      (name.contains ("sequence"      )) sequence    (child);
                else if (name.contains ("complexContent")) complexType (child);
                else if (name.contains ("extension"     ))
                {
                    base = getAttribute (child, "base");
                    complexType (child);
                }
                else if (name.contains ("attribute"))
                {
                    String a = getAttribute (child, "name");
                    if (! attributes.contains (a))
                    {
                        attributes.add (a);
                        if (getAttribute (child, "use").equals ("required")) required.add (a);
                    }
                }
            }
        }

        public void sequence (Node node)
        {
            for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
            {
                String name = child.getNodeName ();
                if      (name.contains ("choice" )) sequence (child);
                else if (name.contains ("all"    )) sequence (child);
                else if (name.contains ("group"  ))
                {
                    children.add (">" + getAttribute (child, "ref"));
                }
                else if (name.contains ("element"))
                {
                    String tag  = getAttribute (child, "name");
                    String type = getAttribute (child, "type");
                    children.add (tag);

                    // For the purposes of NeuroML, the 4 cases where the tag->type mapping is ambiguous don't matter,
                    // because none of them involve more than one element type in sequence.
                    /*
                    if (alias.containsKey (tag))
                    {
                        String original = alias.get (tag);
                        if (! original.equals (type)) System.out.println ("ambiguous tag: " + tag + " -> " + type + " was " + original);
                    }
                    */
                    alias.put (tag, type);
                }
            }
        }

        public void resolve ()
        {
            if (resolved) return;
            resolved = true;

            List<String> newChildren = new ArrayList<String> ();
            SequencerElement baseElement = elements.get (base);  // base could be null, but in that case the return value should be null
            if (baseElement != null)
            {
                baseElement.resolve ();
                newChildren.addAll (baseElement.children);
                for (String a : baseElement.attributes)
                {
                    if (! attributes.contains (a)) attributes.add (a);
                }
                for (String a : baseElement.required)
                {
                    if (! required.contains (a)) required.add (a);
                }
            }
            for (String c : children)
            {
                if (c.startsWith (">"))
                {
                    SequencerElement groupElement = elements.get (c);
                    if (groupElement != null)
                    {
                        groupElement.resolve ();
                        newChildren.addAll (groupElement.children);
                    }
                }
                else
                {
                    newChildren.add (c);
                }
            }
            children = newChildren;
        }

        public void dump ()
        {
            if (! children.isEmpty ())
            {
                System.out.println ("  children:");
                for (String c : children) System.out.println ("    " + c);
            }
            if (! attributes.isEmpty ())
            {
                System.out.println ("  attributes:");
                for (String a : attributes)
                {
                    System.out.print ("    " + a);
                    if (required.contains (a)) System.out.print (" (required)");
                    System.out.println ();
                }
            }
        }
    }

    public void dump ()
    {
        for (Entry<String,SequencerElement> e : elements.entrySet ())
        {
            System.out.println (e.getKey ());
            e.getValue ().dump ();
        }
        System.out.println ("Alias:");
        for (Entry<String,String> e : alias.entrySet ())
        {
            System.out.println (e.getKey () + " = " + e.getValue ());
        }
    }
}
