/*
Copyright 2016 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.neuroml;

import gov.sandia.n2a.plugins.extpoints.Importer;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

public class ImportNeuroML implements Importer
{
    @Override
    public String getName ()
    {
        return "NeuroML";
    }

    @Override
    public void process (File source)
    {
        System.out.println ("Trying to load: " + source);
        try
        {
            // Open and parse XML document
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance ();
            factory.setCoalescing (true);
            factory.setIgnoringComments (true);
            factory.setIgnoringElementContentWhitespace (true);
            DocumentBuilder builder = factory.newDocumentBuilder ();
            Document doc = builder.parse (source);

            // Extract models
            dump (System.out, doc, "");
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
    }

    // -----------------------------------------------------------------------
    // The following methods come from the JAXB tutorial (with some modification).

    public void dumpDetail (PrintStream out, Node n)
    {
        out.print (" nodeName=\"" + n.getNodeName () + "\"");

        String val = n.getNamespaceURI ();
        if (val != null) out.print (" uri=\"" + val + "\"");

        val = n.getPrefix ();
        if (val != null) out.print (" pre=\"" + val + "\"");

        val = n.getLocalName ();
        if (val != null) out.print (" local=\"" + val + "\"");

        val = n.getNodeValue ();
        if (val != null)
        {
            out.print (" nodeValue=");
            if (val.trim ().isEmpty ()) out.print("[WS]");  // Whitespace
            else                        out.print("\"" + n.getNodeValue () + "\"");
        }
        out.println ();
    }

    public void dump (PrintStream out, Node n, String prefix)
    {
        out.print (prefix);
        String prefix1 = prefix + " ";
        String prefix2 = prefix + "  ";

        int type = n.getNodeType();
        switch (type)
        {
            case Node.ATTRIBUTE_NODE:
                out.print ("ATTR:");
                dumpDetail (out, n);
                break;

            case Node.CDATA_SECTION_NODE:
                out.print ("CDATA:");
                dumpDetail (out, n);
                break;

            case Node.COMMENT_NODE:
                out.print ("COMM:");
                dumpDetail (out, n);
                break;

            case Node.DOCUMENT_FRAGMENT_NODE:
                out.print ("DOC_FRAG:");
                dumpDetail (out, n);
                break;

            case Node.DOCUMENT_NODE:
                out.print ("DOC:");
                dumpDetail (out, n);
                break;

            case Node.DOCUMENT_TYPE_NODE:
                out.print ("DOC_TYPE:");
                dumpDetail (out, n);
                NamedNodeMap nodeMap = ((DocumentType) n).getEntities ();
                for (int i = 0; i < nodeMap.getLength (); i++)
                {
                    dump (out, nodeMap.item (i), prefix2);
                }
                break;

            case Node.ELEMENT_NODE:
                out.print ("ELEM:");
                dumpDetail (out, n);
                NamedNodeMap atts = n.getAttributes ();
                for (int i = 0; i < atts.getLength (); i++)
                {
                    dump (out, atts.item (i), prefix2);
                }
                break;

            case Node.ENTITY_NODE:
                out.print ("ENT:");
                dumpDetail (out, n);
                break;

            case Node.ENTITY_REFERENCE_NODE:
                out.print ("ENT_REF:");
                dumpDetail (out, n);
                break;

            case Node.NOTATION_NODE:
                out.print ("NOTATION:");
                dumpDetail (out, n);
                break;

            case Node.PROCESSING_INSTRUCTION_NODE:
                out.print ("PROC_INST:");
                dumpDetail (out, n);
                break;

            case Node.TEXT_NODE:
                out.print ("TEXT:");
                dumpDetail (out, n);
                break;

            default:
                out.print ("UNSUPPORTED NODE: " + type);
                dumpDetail (out, n);
                break;
        }

        // Dump children
        if (type != Node.ATTRIBUTE_NODE)  // skip attribute, because child is just a text node with redundant display info
        {
            for (Node child = n.getFirstChild (); child != null; child = child.getNextSibling ())
            {
                dump (out, child, prefix1);
            }
        }
    }
}
