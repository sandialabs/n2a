/*
Copyright 2013,2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.ui.images.ImageUtil;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedList;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;

import sun.swing.DefaultLookup;
import replete.util.Lay;

public class SearchPanel extends JPanel
{
    protected JTextField              textQuery;
    protected JButton                 buttonClear;
    protected JList<MNode>            list;
    protected DefaultListModel<MNode> model;
    protected ModelEditPanel          modelPanel;
    protected int                     lastSelection = -1;

    // Retrieve records matching the filter text, and deliver them to the model.
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
            for (MNode i : AppData.models)
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

    public void hideSelection ()
    {
        lastSelection = list.getSelectedIndex ();
        list.clearSelection ();
    }

    public void removeDoc (MNode doc)
    {
        int index = model.indexOf (doc);
        if (index >= 0)
        {
            model.remove (index);
            if (index == lastSelection) lastSelection = Math.min (model.size () - 1, index);
        }
    }

    public void insertDoc (MNode doc)
    {
        int index = model.indexOf (doc);
        if (index < 0)
        {
            model.insertElementAt (doc, 0);
            lastSelection = 0;
        }
    }

    public SearchPanel (ModelEditPanel container)
    {
        modelPanel = container;

        list = new JList<MNode> (model = new DefaultListModel<MNode> ());
        list.setSelectionMode (ListSelectionModel.SINGLE_SELECTION);
        list.setDragEnabled (true);
        list.setCellRenderer (new MNodeRenderer ());
        list.addMouseListener (new MouseAdapter ()
        {
            public void mouseClicked (MouseEvent e)
            {
                modelPanel.recordSelected ();
                e.consume ();
            }

            public void mouseReleased (MouseEvent e)
            {
                e.consume ();
            }
        });
        list.addKeyListener (new KeyAdapter ()
        {
            @Override
            public void keyPressed (KeyEvent e)
            {
                int keycode = e.getKeyCode ();
                if (keycode == KeyEvent.VK_ENTER)
                {
                    modelPanel.recordSelected ();
                    e.consume ();
                }
                else if (keycode == KeyEvent.VK_DELETE  ||  keycode == KeyEvent.VK_BACK_SPACE)
                {
                    if (e.isControlDown ())
                    {
                        int   index    = list.getSelectedIndex ();
                        MNode deleteMe = list.getSelectedValue ();
                        if (deleteMe == null) return;
                        modelPanel.recordDeleted (deleteMe);
                        model.remove (index);
                        index = Math.min (model.size () - 1, index);
                        list.setSelectedIndex (index);
                        ((MDoc) deleteMe).delete ();
                    }
                }
                else if (keycode == KeyEvent.VK_INSERT)
                {
                    modelPanel.createRecord ();
                }
            }
        });
        list.addFocusListener (new FocusListener ()
        {
            public void focusGained (FocusEvent e)
            {
                if (list.getSelectedIndex () < 0)
                {
                    if (lastSelection < 0  ||  lastSelection >= model.getSize ()) list.setSelectedIndex (0);
                    else                                                          list.setSelectedIndex (lastSelection);
                }
            }

            public void focusLost (FocusEvent e)
            {
                hideSelection ();
            }
        });

        textQuery = new JTextField ();
        textQuery.addKeyListener (new KeyAdapter ()
        {
            public void keyReleased (KeyEvent e)
            {
                search ();
            }
        });

        buttonClear = new JButton (ImageUtil.getImage ("backspace.png"));
        buttonClear.setPreferredSize (new Dimension (22, 22));
        buttonClear.setOpaque (false);
        buttonClear.setFocusable (false);
        buttonClear.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                textQuery.setText ("");
                search ();
            }
        });

        Lay.BLtg (this,
            "N", Lay.BL (
                "C", textQuery,
                "E", buttonClear,
                "eb=2,hgap=2"
            ),
            "C", Lay.sp (list)
        );

        search ();  // This will safely block until the models dir is loaded. If that takes too long for comfort, other arrangements are possible.
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
        thread = new SearchThread (query.trim ());
        thread.start ();
    }

    protected static class MNodeRenderer extends JTextField implements ListCellRenderer<MNode>
    {
        protected static DefaultHighlighter.DefaultHighlightPainter painter;

        public MNodeRenderer ()
        {
            painter = new DefaultHighlighter.DefaultHighlightPainter (DefaultLookup.getColor (this, ui, "List.selectionBackground"));
            setBorder (new EmptyBorder (0, 0, 0, 0));
        }

        public Component getListCellRendererComponent (JList<? extends MNode> list, MNode node, int index, boolean isSelected, boolean cellHasFocus)
        {
            String name = node.key ();
            if (name.isEmpty ()) name = node.get ();
            setText (name);

            if (isSelected)
            {
                Highlighter h = getHighlighter ();
                h.removeAllHighlights ();
                try
                {
                    h.addHighlight (0, name.length (), painter);
                }
                catch (BadLocationException e)
                {
                }
            }

            return this;
        }
    }
}
