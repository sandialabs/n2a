/*
Copyright 2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.settings;

import gov.sandia.n2a.host.Host;
import gov.sandia.n2a.host.Remote;
import gov.sandia.n2a.host.Host.Factory;
import gov.sandia.n2a.plugins.ExtensionPoint;
import gov.sandia.n2a.plugins.PluginManager;
import gov.sandia.n2a.plugins.extpoints.Settings;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.MTextField;
import gov.sandia.n2a.ui.images.ImageUtil;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JComboBox;
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

@SuppressWarnings("serial")
public class SettingsHost implements Settings
{
    protected JScrollPane            scrollPane;
    protected DefaultListModel<Host> model        = new DefaultListModel<Host> ();
    protected JList<Host>            list         = new JList<Host> (model);
    protected MTextField             fieldName;
    protected JComboBox<String>      comboClass   = new JComboBox<String> ();
    protected JPanel                 editor;  // The panel returned by Host for editing itself.
    protected JPanel                 editorHolder = new JPanel ();

    public interface NameChangeListener
    {
        public void nameChanged (String newName);
    }

    @Override
    public String getName ()
    {
        return "Hosts";
    }

    @Override
    public ImageIcon getIcon ()
    {
        return ImageUtil.getImage ("connect.gif");
    }

    @Override
    public Component getPanel ()
    {
        if (scrollPane != null) return scrollPane;

        JPanel view = new JPanel ();
        scrollPane = new JScrollPane (view);
        scrollPane.getVerticalScrollBar ().setUnitIncrement (15);  // About one line of text. Typically, one "click" of the wheel does 3 steps, so about 45px or 3 lines of text.

        for (Host h : Host.getHosts ()) if (h instanceof Remote) model.addElement (h);

        list.setSelectionMode (ListSelectionModel.SINGLE_SELECTION);

        InputMap inputMap = list.getInputMap ();
        inputMap.put (KeyStroke.getKeyStroke ("INSERT"),            "add");
        inputMap.put (KeyStroke.getKeyStroke ("ctrl shift EQUALS"), "add");
        inputMap.put (KeyStroke.getKeyStroke ("DELETE"),            "delete");
        inputMap.put (KeyStroke.getKeyStroke ("BACK_SPACE"),        "delete");

        ActionMap actionMap = list.getActionMap ();
        actionMap.put ("add", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                // Create new record
                String hostname = Host.uniqueName ();
                Host h = Host.create (hostname, "");  // Get default class. The user can change it later.

                // Focus record in UI
                int index = list.getSelectedIndex ();
                if (index < 0) index = model.getSize ();
                model.add (index, h);
                list.setSelectedIndex (index);  // Assumption: this triggers a selection change event, which will in turn call displayRecord().
            }
        });
        actionMap.put ("delete", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                // Remove current record
                int index = list.getSelectedIndex ();
                Host h = list.getSelectedValue ();
                if (h == null) return;
                Host.remove (h, true);

                // Focus another record, or clear UI
                model.remove (index);
                index = Math.min (index, model.size () - 1);
                if (index >= 0) list.setSelectedIndex (index);  // triggers selection change event, resulting in call to displayRecord()
            }
        });

        list.addListSelectionListener (new ListSelectionListener ()
        {
            public void valueChanged (ListSelectionEvent e)
            {
                if (e.getValueIsAdjusting ()) return;
                displayRecord ();
            }
        });

        fieldName = new MTextField (null, "", "", 20, true);
        fieldName.addChangeListener (new ChangeListener ()
        {
            public void stateChanged (ChangeEvent e)
            {
                Host current = list.getSelectedValue ();
                current.name = fieldName.getText ();
                list.repaint ();
                if (editor instanceof NameChangeListener) ((NameChangeListener) editor).nameChanged (current.name);
            }
        });

        for (ExtensionPoint ext : PluginManager.getExtensionsForPoint (Factory.class))
        {
            Factory f = (Factory) ext;
            if (f.isRemote ()) comboClass.addItem (f.className ());
        }
        comboClass.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                String newClass = comboClass.getSelectedItem ().toString ();

                // Change the class. This requires destroying the current instance and constructing
                // a new one that is bound to the same record.
                int index = list.getSelectedIndex ();
                Host h = list.getSelectedValue ();
                if (h == null) return;
                String originalClass = h.getClassName ();
                if (newClass.equals (originalClass)) return;

                Host.remove (h, false);
                Host h2 = Host.create (h.name, newClass);
                h.transferJobsTo (h2);

                model.set (index, h2);
                displayRecord ();
            }
        });

        editorHolder.setLayout (new BorderLayout ());

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
                            Lay.BL ("W", Lay.FL ("H", Lay.lb ("Name"), fieldName)),
                            Lay.BL ("W", Lay.FL ("H", Lay.lb ("Class"), comboClass)),
                            editorHolder
                        )
                    )
                )
            )
        );
        scrollPane.setFocusCycleRoot (true);

        if (list.getModel ().getSize () > 0) list.setSelectedIndex (0);

        return scrollPane;
    }

    @Override
    public Component getInitialFocus (Component panel)
    {
        return list;
    }

    // Update editor side with currently-selected item in list.
    public void displayRecord ()
    {
        Host h = list.getSelectedValue ();
        editorHolder.removeAll ();
        if (h == null)  // This can happen during delete.
        {
            fieldName.bind (null, null);
            return;
        }
        fieldName.bind (h.config.parent (), h.name);
        comboClass.setSelectedItem (h.getClassName ());

        editor = h.getEditor ();
        editorHolder.add (editor, BorderLayout.CENTER);
        editor.revalidate ();
        editor.repaint ();
    }
}
