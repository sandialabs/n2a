/*
Copyright 2013,2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.settings;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.plugins.extpoints.Settings;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.images.ImageUtil;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TreeSet;

import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JList;
import javax.swing.JPanel;

public class SettingsBackup extends JPanel implements Settings
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

    public SettingsBackup ()
    {
        setName ("Backup");  // Necessary to fulfill Settings interface.

        JButton Backup = new JButton ("Backup", ImageUtil.getImage ("saveall.gif"));
        Backup.setMnemonic (KeyEvent.VK_B);
        Backup.addActionListener
        (
            new ActionListener ()
            {
                public void actionPerformed (ActionEvent e)
                {
                    backup ();
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

        RemoveAdded = new JCheckBox ("On restore, remove any new items added since the backup.");

        populateList ();
        Lay.BLtg
        (
            this,
            "C", Lay.p (Lay.sp (list), "eb=5lr"),
            "S", Lay.BL
            (
                "N", RemoveAdded,
                "C", Lay.FL ("R", Backup, Restore, Delete)
            )
        );
    }

    public void populateList ()
    {
        TreeSet<BackupEntry> entries = new TreeSet<BackupEntry> ();
        File dir = new File (AppData.properties.get ("resourceDir"), "backups");
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
        File dir = new File (AppData.properties.get ("resourceDir"), "backups");
        dir.mkdirs ();
        String fileName = new SimpleDateFormat ("yyyyMMddHHmmss", Locale.ROOT).format (new Date ()) + ".zip";
        File destination = new File (dir, fileName);
        AppData.backup (destination);
        populateList ();
    }

    public void restore ()
    {
        BackupEntry restoreMe = list.getSelectedValue ();
        if (restoreMe == null) return;
        AppData.restore (restoreMe.file, RemoveAdded.isSelected ());
    }

    public void delete ()
    {
        BackupEntry deleteMe = list.getSelectedValue ();
        if (deleteMe == null) return;
        deleteMe.file.delete ();
        populateList ();
    }

    @Override
    public ImageIcon getIcon ()
    {
        return ImageUtil.getImage ("saveall.gif");
    }

    @Override
    public Component getPanel ()
    {
        return this;
    }

    @Override
    public Component getInitialFocus (Component panel)
    {
        return panel;
    }
}
