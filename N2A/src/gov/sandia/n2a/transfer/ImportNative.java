/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.transfer;

import gov.sandia.n2a.ui.eq.EquationTreePanel;
import gov.sandia.n2a.ui.eq.ModelEditPanel;
import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.db.MVolatile;
import gov.sandia.umf.platform.plugins.extpoints.Importer;

import java.awt.GridBagLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JPanel;

public class ImportNative implements Importer
{
    @Override
    public String getName ()
    {
        return "N2A Native";
    }

    public static class Accessory extends JPanel
    {
        public boolean useClipboard;

        public Accessory (final JFileChooser fc)
        {
            JButton button = new JButton ("From Clipboard");
            button.setToolTipText ("Import directly from clipboard, rather than a file.");
            button.addActionListener (new ActionListener ()
            {
                public void actionPerformed (ActionEvent e)
                {
                    useClipboard = true;
                    fc.approveSelection ();
                }
            });
            setLayout (new GridBagLayout ());
            add (button);
        }
    }

    @Override
    public JComponent getAccessory (final JFileChooser fc)
    {
        return new Accessory (fc);
    }

    @Override
    public void process (File source, JComponent accessory)
    {
        boolean useClipboard = ((Accessory) accessory).useClipboard;

        EquationTreePanel etp = ModelEditPanel.instance.panelEquations;

        try
        {
            if (useClipboard)
            {
                Clipboard c = Toolkit.getDefaultToolkit ().getSystemClipboard ();
                Transferable t = c.getContents (null);
                try
                {
                    StringReader reader = new StringReader (t.getTransferData (DataFlavor.stringFlavor).toString ());

                    // Parse source. Adapted from MDoc.load()
                    BufferedReader buff = new BufferedReader (reader);
                    reader.mark (64);  // enough to read schema line comfortably
                    String line = buff.readLine ().trim ();
                    String[] pieces = line.split ("=", 2);
                    if (pieces.length < 2  ||  ! pieces[0].equals ("N2A.schema")  ||  ! pieces[1].equals ("1"))
                    {
                        System.err.println ("WARNING: schema version not recognized. Proceeding as if it were.");
                        reader.reset ();  // This may have been an important line of input.
                    }
                    MVolatile temp = new MVolatile ();
                    temp.read (buff);

                    // Import one or more models
                    for (MNode n : temp)
                    {
                        String key = n.key ();
                        MNode model = etp.createNewModel (key);
                        model.merge (n);
                    }
                }
                catch (UnsupportedFlavorException e)
                {
                }
            }
            else
            {
                // Copy file into repository. Adapted from AppData.checkInitialDB()
                MNode doc = etp.createNewModel (source.getName ());
                BufferedReader reader = new BufferedReader (new FileReader (source));
                reader.readLine ();  // dispose of schema line
                doc.read (reader);
                reader.close ();
            }
        }
        catch (IOException e)
        {
        }
    }
}
