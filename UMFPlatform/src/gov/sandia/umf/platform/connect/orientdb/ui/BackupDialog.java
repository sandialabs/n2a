/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.connect.orientdb.ui;

import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TreeSet;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JList;

import com.orientechnologies.orient.core.command.OCommandOutputListener;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.db.tool.ODatabaseExport;
import com.orientechnologies.orient.core.db.tool.ODatabaseImport;
import replete.gui.windows.EscapeDialog;
import replete.util.Lay;

public class BackupDialog extends EscapeDialog
{
    private static final long serialVersionUID = 1L;

    class BackupEntry implements Comparable<BackupEntry>
    {
        public String date;
        public File   file;
        public BackupEntry (File file, String date)
        {
            this.file = file;
            this.date = date;
        }
        @Override
        public String toString ()
        {
            return date.substring (0, 4) + "-" + date.substring (4, 6) + "-" + date.substring (6, 8) + " at " + date.substring (8, 10) + ":" + date.substring (10, 12) + " (" + file.length () / 1024 + "K)";
        }
        @Override
        public int compareTo (BackupEntry that)
        {
            return that.date.compareTo (date);  // most recent first
        }
    }

    public JList <BackupEntry> list = new JList <BackupEntry> ();

    public BackupDialog (JFrame parent)
    {
        super (parent, "Backup Manager", true);
        setIconImage (ImageUtil.getImage ("repo.gif").getImage ());

        JButton Backup = new JButton ("Backup", ImageUtil.getImage ("saveall.gif"));
        Backup.setMnemonic (KeyEvent.VK_B);
        Backup.addActionListener
        (
            new ActionListener ()
            {
                public void actionPerformed (ActionEvent e)
                {
                    backup ();
                    closeDialog ();
                }
            }
        );

        JButton Restore = new JButton ("Restore", ImageUtil.getImage ("load.gif"));
        Restore.addActionListener
        (
            new ActionListener ()
            {
                public void actionPerformed (ActionEvent e)
                {
                    restore ();
                    closeDialog ();
                }
            }
        );

        JButton Delete = new JButton ("Delete", ImageUtil.getImage ("remove.gif"));
        Delete.setMnemonic (KeyEvent.VK_D);
        Delete.addActionListener
        (
            new ActionListener ()
            {
                public void actionPerformed (ActionEvent e)
                {
                    delete ();
                }
            }
        );

        JButton Cancel = new JButton ("Done", ImageUtil.getImage ("cancel.gif"));
        Cancel.addActionListener
        (
            new ActionListener ()
            {
                public void actionPerformed (ActionEvent e)
                {
                    closeDialog ();
                }
            }
        );

        getRootPane ().setDefaultButton (Cancel);

        populateList ();
        Lay.BLtg
        (
            this,
            "C", Lay.p (Lay.sp (list), "eb=5lr"),
            "S", Lay.BL
            (
                "N", Lay.lb ("<html>WARNING: If you click Restore, your working repository will be erased and replaced by the selected backup. You will lose all work done after the time of the backup.</html>", "eb=5tlr,pref=[100,50]"),
                "C", Lay.FL ("R", Backup, Restore, Delete, Cancel)
            )
        );
        pack ();
        setLocationRelativeTo (getParent ());
    }

    public void populateList ()
    {
        ConnectionManager connections = ConnectionManager.getInstance ();
        OrientConnectDetails details = connections.getConnectDetails ();

        String[] parts = details.location.split (":", 2);
        String location = parts.length == 2 ? parts[1] : parts[0];

        File dbDir = new File (location).getParentFile ();
        File[] backupFiles = dbDir.listFiles ();

        TreeSet<BackupEntry> entries = new TreeSet<BackupEntry> ();
        if (backupFiles != null)
        {
            for (File backupFile : backupFiles)
            {
                if (backupFile.isDirectory ()) continue;
                String backupName = backupFile.getName ();
                parts = backupName.split ("_", 2);
                if (parts.length != 2) continue;
                parts = parts[1].split ("\\.", 2);  // remove suffix
                if (parts.length != 2) continue;
                String date = parts[0];
                entries.add (new BackupEntry (backupFile, date));
            }
        }

        int index = list.getSelectedIndex ();
        DefaultListModel<BackupEntry> model = new DefaultListModel<BackupEntry> ();
        for (BackupEntry e : entries)
        {
            model.addElement (e);
        }
        list.setModel (model);
        list.setSelectedIndex (Math.min (index, model.size () - 1));
    }

    public void backup ()
    {
        System.out.println ("backup");
        try
        {
            ConnectionManager    connections = ConnectionManager.getInstance ();
            ODatabaseDocumentTx  db          = connections.getDataModel ().getDB ();
            OrientConnectDetails details     = connections.getConnectDetails ();

            String[] parts = details.location.split (":", 2);
            String location = parts.length == 2 ? parts[1] : parts[0];
            File dbDir = new File (location);
            String fileName = dbDir.getName () + "_" + new SimpleDateFormat ("yyyyMMddHHmmss", Locale.ROOT).format (new Date ()) + ".gz";
            fileName = new File (dbDir.getParentFile (), fileName).getAbsolutePath ();

            OCommandOutputListener listener = new OCommandOutputListener ()
            {
                public void onMessage (String arg0)
                {
                    System.out.println (arg0);
                }
            };

            ODatabaseExport exporter = new ODatabaseExport (db, fileName, listener);
            exporter.exportDatabase ();
        }
        catch (IOException error)
        {
            System.out.println (error.toString ());
        }
        populateList ();
        list.setSelectedIndex (0);
        System.out.println ("backup done");
    }

    public void restore ()
    {
        BackupEntry restoreMe = list.getSelectedValue ();
        if (restoreMe == null) return;

        System.out.println ("restore " + restoreMe.toString ());

        // TODO: This assumes db is local
        OrientConnectDetails details = ConnectionManager.getInstance ().getConnectDetails ();
        ODatabaseDocumentTx db = new ODatabaseDocumentTx (details.location);
        db.open (details.user, details.password);
        db.drop ();  // erase everything, including directory in which db files are stored. TODO: Is OrientDB smart enough to propagate this to other pooled DB objects?
        db.create ();

        OCommandOutputListener listener = new OCommandOutputListener ()
        {
            public void onMessage (String arg0)
            {
                System.out.println (arg0);
            }
        };

        try
        {
            ODatabaseImport importer = new ODatabaseImport (db, restoreMe.file.getAbsolutePath (), listener);
            importer.importDatabase ();
        }
        catch (IOException error)
        {
            System.out.println (error.toString ());
        }

        System.out.println ("restore done");
    }

    public void delete ()
    {
        BackupEntry deleteMe = list.getSelectedValue ();
        if (deleteMe == null) return;
        deleteMe.file.delete ();
        populateList ();
    }
}
