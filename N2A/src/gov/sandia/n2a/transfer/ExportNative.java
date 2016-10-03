/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.transfer;

import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.plugins.extpoints.Exporter;
import java.awt.GridBagLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JPanel;

public class ExportNative implements Exporter
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
            JButton button = new JButton ("To Clipboard");
            button.setToolTipText ("Export directly to clipboard, rather than a file.");
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
    public void export (MNode source, File destination, JComponent accessory)
    {
        boolean useClipboard = ((Accessory) accessory).useClipboard;

        try
        {
            if (useClipboard)
            {
                // Treat the exported model as a single sub-part. That way, its name is naturally included.
                // This is suitable for embedding in an email.
                StringWriter writer = new StringWriter ();
                writer.write (String.format ("N2A.schema=1%n"));
                writer.write (String.format (source.key () + "%n"));
                for (MNode n : source) n.write (writer, " ");
                writer.close ();

                Clipboard c = Toolkit.getDefaultToolkit ().getSystemClipboard ();
                StringSelection s = new StringSelection (writer.toString ());
                c.setContents (s, s);
            }
            else
            {
                // Write a standard repository file. See MDoc.save()
                BufferedWriter writer = new BufferedWriter (new FileWriter (destination));
                writer.write (String.format ("N2A.schema=1%n"));
                for (MNode n : source) n.write (writer, "");
                writer.close ();
            }
        }
        catch (IOException e)
        {
        }
    }
}
