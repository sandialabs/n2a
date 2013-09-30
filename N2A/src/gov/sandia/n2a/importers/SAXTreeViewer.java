/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.importers;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.swing.JFrame;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

public class SAXTreeViewer extends JFrame 
{
    // TODO?:  Swing-related variables and methods, including
    //   setting up a JTree and basic content pane

    public void buildTree(DefaultTreeModel treeModel, DefaultMutableTreeNode base, String xmlURI)
        throws IOException, SAXException 
    {
        String featureURI = "";
        XMLReader reader = null;        // Create instances needed for parsing
        try {
            reader = XMLReaderFactory.createXMLReader( ); 
            JTreeHandler jTreeHandler =
                new JTreeHandler(treeModel, base);
            // Register content handler
            reader.setContentHandler(jTreeHandler);
            // Register error handler
            reader.setErrorHandler(jTreeHandler);
            // Turn on validation
            featureURI = "http://xml.org/sax/features/validation";
            reader.setFeature(featureURI, true);
            // Turn on schema validation, as well
            featureURI = "http://apache.org/xml/features/validation/schema";
            reader.setFeature(featureURI, true);
            // Parse
            InputSource inputSource = new InputSource(xmlURI);
            reader.parse(inputSource);
        } catch (SAXNotRecognizedException e) {
            System.err.println("The parser class " + reader.getClass().getName() +
                  " does not recognize the feature URI '" + featureURI + "'");
            System.exit(-1);
        } catch (SAXNotSupportedException e) {
            System.err.println("The parser class " + reader.getClass().getName() +
                  " does not support the feature URI '" + featureURI + "'");
                System.exit(-1);    
        }
    }

    public static void main(String[] args) 
    {
        try {
            if (args.length != 1) {
                System.out.println("Usage: java javaxml3.SAXTreeViewer " +
                   "[XML Document]");
                return;
            }
            SAXTreeViewer viewer = new SAXTreeViewer(); 
//            viewer.init(args[0]); 
            viewer.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace( );
            } 
        }
    }

class JTreeHandler implements ContentHandler, ErrorHandler 
{
    /** Tree Model to add nodes to */
    private DefaultTreeModel treeModel;
    /** Current node to add sub-nodes to */
    private DefaultMutableTreeNode current;
    /** Hold onto the locator for location information */
    private Locator locator;
    /** Store URI to prefix mappings */
    private Map namespaceMappings;

    public JTreeHandler(DefaultTreeModel treeModel,
                    DefaultMutableTreeNode base) 
    {
        this.treeModel = treeModel;
        this.current = base;
        this.namespaceMappings = new HashMap();
    }
    
    // ContentHandler callback implementations
    public void setDocumentLocator(Locator locator) {
        // Save this for later use
        this.locator = locator;
    }

    public void startDocument( ) throws SAXException {
        // No visual events occur here
    }
      
    public void endDocument( ) throws SAXException {
        // No visual events occur here
    }
    
    public void processingInstruction(String target, String data)
               throws SAXException        
    {
         DefaultMutableTreeNode pi =
             new DefaultMutableTreeNode("PI (target = '" + target +
                                        "', data = '" + data + "')");
         current.add(pi);
    }

    public void startPrefixMapping(String prefix, String uri) 
    {
        // No visual events occur here.
        namespaceMappings.put(uri, prefix);
    }
  
    public void endPrefixMapping(String prefix) 
    {
        // No visual events occur here.
        for (Iterator i = namespaceMappings.keySet().iterator(); i.hasNext(); ) 
        { 
            String uri = (String)i.next( );
            String thisPrefix = (String)namespaceMappings.get(uri);
            if (prefix.equals(thisPrefix)) {
                namespaceMappings.remove(uri);
                break; 
            }
        } 
    }  
    
    public void startElement(String namespaceURI, String localName,
            String qName, Attributes atts)
            throws SAXException 
    {
        DefaultMutableTreeNode element =
            new DefaultMutableTreeNode("Element: " + localName);
        current.add(element);
        current = element;
        //Determine namespace
        if (namespaceURI.length() > 0) {
            String prefix =
                (String)namespaceMappings.get(namespaceURI);
            if (prefix.equals("")) {
                prefix = "[None]";
            }
            DefaultMutableTreeNode namespace =
                new DefaultMutableTreeNode("Namespace: prefix = '" +
                prefix + "', URI = '" + namespaceURI + "'");
            current.add(namespace);
        }
        // Process attributes
        for (int i=0; i<atts.getLength( ); i++) {
            DefaultMutableTreeNode attribute =
                new DefaultMutableTreeNode("Attribute (name = '" +
                  atts.getLocalName(i) +
                  "', value = '" +
                  atts.getValue(i) + "')");
            String attURI = atts.getURI(i);
            if (attURI.length( ) > 0) {
                String attPrefix = (String)namespaceMappings.get(attURI);
                if (attPrefix.equals("")) {
                    attPrefix = "[None]";
                }
                DefaultMutableTreeNode attNamespace =
                    new DefaultMutableTreeNode("Namespace: prefix = '" +
                    attPrefix + "', URI = '" + attURI + "'");
                attribute.add(attNamespace);
            }
            current.add(attribute);
        }
    }

    public void endElement(String namespaceURI, String localName, String qName)
        throws SAXException 
    {
        // Walk back up the tree
        current = (DefaultMutableTreeNode)current.getParent(); 
    }
    
    public void characters(char[] ch, int start, int length)
          throws SAXException 
    {
        String s = new String(ch, start, length);
        DefaultMutableTreeNode data =
            new DefaultMutableTreeNode("Character Data: '" + s + "'");
          current.add(data);
    }
    
    public void ignorableWhitespace(char[] ch, int start, int length)
          throws SAXException {
    
          // This is ignorable, so don't display it
    }

    public void skippedEntity(String name) throws SAXException 
    {
        DefaultMutableTreeNode skipped =
          new DefaultMutableTreeNode("Skipped Entity: '" + name + "'");
        current.add(skipped);
  }
    
    public void warning(SAXParseException exception)
          throws SAXException 
    {
        try {
            FileWriter fw = new FileWriter("error.log");
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write("**Warning**\n");
            bw.write("\tLine: ");
            bw.write(exception.getLineNumber( )); bw.write("\n\tURI: "); bw.write(exception.getSystemId( )); bw.write("\n\tMessage: "); bw.write(exception.getMessage( )); bw.write("\n\n");
            bw.flush( ); bw.close( ); fw.close( );
                   } catch (IOException e) {
                     throw new SAXException("Could not write to log file", e);
            }
    }

    public void error(SAXParseException exception)
    	      throws SAXException {
    	try {
    	FileWriter fw = new FileWriter("error.log"); BufferedWriter bw = new BufferedWriter(fw); bw.write("**Error**\n");
    	bw.write("\tLine: "); bw.write(exception.getLineNumber( )); bw.write("\n\tURI: "); bw.write(exception.getSystemId( )); bw.write("\n\tMessage: "); bw.write(exception.getMessage( )); bw.write("\n\n");
    	bw.flush( );
    	bw.close( );
    	fw.close( );
    	       } catch (IOException e) {
    	         throw new SAXException("Could not write to log file", e);
    	}
    }

    public void fatalError(SAXParseException exception)
          throws SAXException 
    {
        try {
            FileWriter fw = new FileWriter("error.log"); BufferedWriter bw = new BufferedWriter(fw); bw.write("**Fatal Error**\n"); bw.write("\tLine: "); bw.write(exception.getLineNumber( )); bw.write("\n\tURI: "); bw.write(exception.getSystemId( )); bw.write("\n\tMessage: "); bw.write(exception.getMessage( )); bw.write("\n\n");
            bw.flush( );
            bw.close( );
            fw.close( );
            // Bail out
            throw new SAXException("Fatal Error! Check the log!");
        } catch (IOException e) {
             throw new SAXException("Could not write to log file", e);
        }
    }

}
