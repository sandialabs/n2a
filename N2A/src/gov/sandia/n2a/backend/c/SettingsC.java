/*
Copyright 2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.c;

import gov.sandia.n2a.host.Host;
import gov.sandia.n2a.plugins.extpoints.Settings;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.MTextField;
import gov.sandia.n2a.ui.images.ImageUtil;

import java.awt.Component;
import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.border.LineBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

public class SettingsC implements Settings, ChangeListener
{
    protected JScrollPane            scrollPane;
    protected DefaultListModel<Host> model    = new DefaultListModel<Host> ();
    protected JList<Host>            list     = new JList<Host> (model);
    protected JPanel                 editor   = new JPanel ();
    protected MTextField             fieldCpp = new MTextField (40);

    public SettingsC ()
    {
        Host.addChangeListener (this);
    }

    @Override
    public String getName ()
    {
        return "Backend C";
    }

    @Override
    public ImageIcon getIcon ()
    {
        return ImageUtil.getImage ("BackendC.png");
    }

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
                fieldCpp.bind (h.config.childOrCreate ("c"), "cxx", "g++");
            }
        });

        JPanel panelList = Lay.BL ("C", list, "pref=[100,200]");
        panelList.setBorder (LineBorder.createBlackLineBorder ());
        panelList = (JPanel) Lay.eb (Lay.BL ("C", panelList), "5");

        Lay.BLtg (view, "N",
            Lay.BL ("W",
                Lay.BxL ("H",
                    Lay.BL ("N", panelList),
                    Box.createHorizontalStrut (5),
                    Lay.BL ("N",
                        Lay.BxL (
                            Lay.BL ("W", Lay.FL ("H", new JLabel ("Compiler path"), fieldCpp))
                        )
                    )
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
