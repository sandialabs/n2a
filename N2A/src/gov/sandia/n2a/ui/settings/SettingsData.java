/*
Copyright 2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.settings;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.plugins.extpoints.Settings;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.images.ImageUtil;
import java.awt.Component;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

@SuppressWarnings("serial")
public class SettingsData extends JPanel implements Settings
{
    protected JTable          table;
    protected MNodeTableModel model;
    protected JScrollPane     scrollPane;

    public SettingsData ()
    {
        setName ("Data");  // Necessary to fulfill Settings interface.

        model      = new MNodeTableModel ();
        table      = new JTable (model);
        scrollPane = new JScrollPane (table);

        Lay.BLtg (this,
            "N", scrollPane
        );
    }

    @Override
    public ImageIcon getIcon ()
    {
        return ImageUtil.getImage ("repo.gif");
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

    public class MNodeTableModel extends AbstractTableModel
    {
        MNode repos;

        public MNodeTableModel ()
        {
            repos = AppData.state.childOrCreate ("Repos");
        }

        @Override
        public int getRowCount ()
        {
            return repos.size ();
        }

        @Override
        public int getColumnCount ()
        {
            return 2;
        }

        @Override
        public Object getValueAt (int rowIndex, int columnIndex)
        {
            MNode repo = repos.child (String.valueOf (rowIndex));
            if (repo == null) return null;
            switch (columnIndex)
            {
                case 0: return rowIndex == 0;
                case 1: return repo.get ();
            }
            return null;
        }
    }
}