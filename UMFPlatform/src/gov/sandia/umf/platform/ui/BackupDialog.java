/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui;

import gov.sandia.umf.platform.UMF;
import gov.sandia.umf.platform.db.AppData;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TreeSet;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JList;

import replete.gui.windows.EscapeDialog;
import replete.util.Lay;

public class BackupDialog extends EscapeDialog
{
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
    JCheckBox RemoveAdded;

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

        RemoveAdded = new JCheckBox ("On restore, remove any new items added since the backup.");

        getRootPane ().setDefaultButton (Cancel);

        populateList ();
        Lay.BLtg
        (
            this,
            "C", Lay.p (Lay.sp (list), "eb=5lr"),
            "S", Lay.BL
            (
                "N", RemoveAdded,
                "C", Lay.FL ("R", Backup, Restore, Delete, Cancel)
            )
        );
        pack ();
        setLocationRelativeTo (getParent ());
    }

    public void populateList ()
    {
        TreeSet<BackupEntry> entries = new TreeSet<BackupEntry> ();
        File dir = new File (UMF.getAppResourceDir (), "backups");
        File[] backupFiles = dir.listFiles ();
        if (backupFiles != null)
        {
            for (File backupFile : backupFiles)
            {
                if (backupFile.isDirectory ()) continue;
                String backupName = backupFile.getName ();
                String[] parts = backupName.split ("\\.", 2);  // remove suffix
                if (parts.length != 2) continue;
                String date = parts[0];
                entries.add (new BackupEntry (backupFile, date));
            }
        }

        DefaultListModel<BackupEntry> model = new DefaultListModel<BackupEntry> ();
        for (BackupEntry e : entries)
        {
            model.insertElementAt (e, 0);  // effectively reverses the order, putting newest first
        }
        list.setModel (model);
        list.setSelectedIndex (0);
    }

    public void backup ()
    {
        File dir = new File (UMF.getAppResourceDir (), "backups");
        dir.mkdirs ();
        String fileName = new SimpleDateFormat ("yyyyMMddHHmmss", Locale.ROOT).format (new Date ()) + ".zip";
        File destination = new File (dir, fileName);
        AppData.getInstance ().backup (destination);
        populateList ();
    }

    public void restore ()
    {
        BackupEntry restoreMe = list.getSelectedValue ();
        if (restoreMe == null) return;
        AppData.getInstance ().restore (restoreMe.file, RemoveAdded.isSelected ());
    }

    public void delete ()
    {
        BackupEntry deleteMe = list.getSelectedValue ();
        if (deleteMe == null) return;
        deleteMe.file.delete ();
        populateList ();
    }
}
