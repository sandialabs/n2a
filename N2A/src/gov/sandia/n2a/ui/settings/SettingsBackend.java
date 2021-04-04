/*
Copyright 2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.settings;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.host.Host;
import gov.sandia.n2a.plugins.extpoints.Settings;
import gov.sandia.n2a.ui.Lay;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.net.URL;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.Box;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.border.LineBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

public abstract class SettingsBackend implements Settings, ChangeListener
{
    protected String    key;  // Short name used as key to Host.backend
    protected String    iconFileName;
    protected ImageIcon icon;

    protected JScrollPane            scrollPane;
    protected DefaultListModel<Host> model  = new DefaultListModel<Host> ();
    protected JList<Host>            list   = new JList<Host> (model);
    protected JPanel                 editor = new JPanel ();

    /**
        Derived class should do the following in ctor:
        * Set key
        * Set iconFileName
    **/
    public SettingsBackend ()
    {
        Host.addChangeListener (this);
    }

    public abstract void bind (MNode parent);
    public abstract JPanel getEditor ();

    @Override
    public ImageIcon getIcon ()
    {
        if (icon == null)
        {
            URL imageURL = getClass ().getResource (iconFileName);
            if (imageURL != null) icon = new ImageIcon (imageURL);
        }
        return icon;  // Can be null, if we fail to load the image.
    }

    @SuppressWarnings("serial")
    @Override
    public Component getPanel ()
    {
        if (scrollPane != null) return scrollPane;

        JPanel view = new JPanel ();
        scrollPane = new JScrollPane (view);

        for (Host h : Host.getHosts ()) model.addElement (h);
        list.setSelectionMode (ListSelectionModel.SINGLE_SELECTION);
        list.addListSelectionListener (new ListSelectionListener ()
        {
            public void valueChanged (ListSelectionEvent e)
            {
                if (e.getValueIsAdjusting ()) return;
                Host h = (Host) list.getSelectedValue ();
                if (h == null) return;
                bind (h.config.childOrCreate ("backend", key));
            }
        });
        list.setCellRenderer (new DefaultListCellRenderer ()
        {
            @Override
            public Component getListCellRendererComponent (JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
            {
                Host h = (Host) value;
                String name = h.name;
                if (h.config.get ("backend", key).equals ("0")) name = "<html><s>" + name + "</s></html>";
                return super.getListCellRendererComponent (list, name, index, isSelected, cellHasFocus);
            }
        });

        InputMap inputMap = list.getInputMap ();
        inputMap.put (KeyStroke.getKeyStroke ("DELETE"),     "toggleEnable");
        inputMap.put (KeyStroke.getKeyStroke ("BACK_SPACE"), "toggleEnable");
        inputMap.put (KeyStroke.getKeyStroke ("SPACE"),      "toggleEnable");

        ActionMap actionMap = list.getActionMap ();
        actionMap.put ("toggleEnable", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                Host h = (Host) list.getSelectedValue ();
                if (h == null) return;

                boolean disabled = h.config.get ("backend", key).equals ("0");
                if (disabled) h.config.set (null, "backend", key);
                else          h.config.set ("0",  "backend", key);

                list.repaint ();
            }
        });


        JPanel panelList = Lay.BL ("C", list, "pref=[100,200]");
        panelList.setBorder (LineBorder.createBlackLineBorder ());
        panelList = (JPanel) Lay.eb (Lay.BL ("C", panelList), "5");

        editor = getEditor ();

        Lay.BLtg (view, "N",
            Lay.BL ("W",
                Lay.BxL ("H",
                    Lay.BL ("N", panelList),
                    Box.createHorizontalStrut (5),
                    Lay.BL ("N", editor)
                )
            )
        );

        if (list.getModel ().getSize () > 0) list.setSelectedIndex (0);

        return scrollPane;
    }

    @Override
    public Component getInitialFocus (Component panel)
    {
        return list;
    }

    @Override
    public void stateChanged (ChangeEvent e)
    {
        model.removeAllElements ();
        for (Host h : Host.getHosts ()) model.addElement (h);
    }
}
