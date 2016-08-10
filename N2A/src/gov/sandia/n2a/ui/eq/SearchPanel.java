/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq;

import gov.sandia.umf.platform.db.AppData;
import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.awt.Component;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedList;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import replete.util.Lay;

public class SearchPanel extends JPanel
{
    protected JTextField              textQuery;
    protected JButton                 buttonClear;
    protected JList<MNode>            list;
    protected DefaultListModel<MNode> model;

    public class SearchThread extends Thread
    {
        public String query;
        public boolean stop;
        public List<MNode> results = new LinkedList<MNode> ();

        public SearchThread (String query)
        {
            this.query = query.toLowerCase ();
        }

        @Override
        public void run ()
        {
            for (MNode i : AppData.getInstance ().models)
            {
                if (i.key ().toLowerCase ().contains (query)) results.add (i);
                if (stop) return;
            }

            // Update of list should be atomic with respect to other ui events.
            EventQueue.invokeLater (new Runnable ()
            {
                public void run ()
                {
                    model.clear ();
                    for (MNode record : results) model.addElement (record);
                }
            });
        }
    }
    protected SearchThread thread;

    public void fireRecordSelected ()
    {
        System.out.println ("record selected");
    }

    public SearchPanel ()
    {
        list = new JList<MNode> (model = new DefaultListModel<MNode> ());
        list.setCellRenderer (new MNodeRenderer ());
        list.getSelectionModel ().addListSelectionListener (new ListSelectionListener ()
        {
            public void valueChanged (ListSelectionEvent arg0)
            {
                fireRecordSelected ();
            }
        });
        list.addMouseListener (new MouseAdapter ()
        {
            @Override
            public void mousePressed (MouseEvent e)
            {
                int index = list.getSelectedIndex ();
                if (e.getClickCount () > 1 && index != -1)
                {
                    fireRecordSelected ();
                }
            }
        });
        list.addKeyListener (new KeyAdapter ()
        {
            @Override
            public void keyPressed (KeyEvent e)
            {
                if (e.getKeyCode () == KeyEvent.VK_ENTER)
                {
                    if (list.getSelectedIndex () != -1)
                    {
                        fireRecordSelected ();
                    }
                    e.consume ();
                }
                else if (e.getKeyCode () == KeyEvent.VK_UP)
                {
                    if (list.getSelectedIndex() == 0) textQuery.requestFocusInWindow();
                }
            }
        });

        // Enable the user to navigate down to the list using the down-arrow key.
        // This needs to be a named object so it can be attached to both the text
        // field and the clear button.
        KeyListener downToTableListener = new KeyAdapter ()
        {
            @Override
            public void keyPressed (KeyEvent e)
            {
                if (e.getKeyCode () == KeyEvent.VK_DOWN)
                {
                    if (model.size () != 0)
                    {
                        list.requestFocusInWindow ();
                        if (list.getSelectedIndex () == -1)
                        {
                            list.setSelectedIndex (0);
                        }
                    }
                }
            }
        };

        textQuery = new JTextField ();
        textQuery.addKeyListener (downToTableListener);

        buttonClear = new JButton (ImageUtil.getImage ("remove.gif"));
        buttonClear.addKeyListener (downToTableListener);
        buttonClear.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                textQuery.setText ("");
            }
        });

        Lay.BLtg (this,
            "N", Lay.BL (
                "C", textQuery,
                "E", buttonClear,
                "eb=10,hgap=7,opaque=false"
            ),
            "C", Lay.sp (list),
            "opaque=false"
        );
    }

    public void search ()
    {
        if (thread != null)
        {
            thread.stop = true;
            try
            {
                thread.join ();
            }
            catch (InterruptedException e)
            {
            }
        }

        String query = textQuery.getText ();
        if (query == null) query = "";
        thread = new SearchThread (query.trim ());
        thread.start ();
    }

    public class MNodeRenderer extends JLabel implements ListCellRenderer<MNode>
    {
        public Component getListCellRendererComponent (JList<? extends MNode> list, MNode node, int index, boolean isSelected, boolean cellHasFocus)
        {
            String name = node.key ();
            if (name.isEmpty ()) name = node.get ();
            setText (name);
            return this;
        }
    }
}
