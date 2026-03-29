/*
Copyright 2021-2026 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.plugins.extpoints;

import java.awt.EventQueue;
import java.nio.file.Path;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MNode.Visitor;
import gov.sandia.n2a.db.MPart;
import gov.sandia.n2a.ui.CompoundEdit;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.UndoManager;
import gov.sandia.n2a.ui.eq.undo.AddDoc;

/**
    Marks this importer as specific for models.
**/
public abstract class ImportModel implements Import
{
    @Override
    public void process (Path source, String name) throws Exception
    {
        applyModels (extractModels (source, name));
    }

    /**
        Does all the work of importing the model, and returns a collection of new top-level models.
        The caller is responsible for adding these to the DB. The caller may also use the collection
        of models in some other way.
        @param source Path to the file to be imported.
        @param name A hint for the internal key of the created record. May be null. May be ignored by some importers.
        @return An MNode whose children are the new models, and whose value points to the primary model.
        The primary model is the one that the user should see when the GUI update is done. Usually, this is added
        to the transaction last.
    **/
    public abstract MNode extractModels (Path source, String name) throws Exception;

    /**
        Utility function to create a GUI transaction that adds the given models.
        This function should only be called from a non-EDT thread. It explicitly moves
        GUI updates to the EDT.
        @param models An MNode whose children are the new models, and whose value points to the primary model.
        The primary model is the one that the user should see when the GUI update is done.
    **/
    public static void applyModels (MNode models)
    {
        String primary = models.get ();
        MNode mainModel = models.child (primary);
        models.clear (primary);

        EventQueue.invokeLater (new Runnable ()
        {
            public void run ()
            {
                UndoManager um = MainFrame.undoManager;
                um.addEdit (new CompoundEdit ());
                while (models.size () > 0) addModel (models.iterator ().next (), models, um);
                // Save the best for last. That is, ensure that the main model is the one selected in the UI
                // after all add operations are completed.
                if (mainModel != null) um.apply (new AddDoc (primary, mainModel));
                um.endCompoundEdit ();
            }
        });
    }

    /**
        Subroutine of applyModels().
    **/
    public static void addModel (MNode m, MNode models, UndoManager um)
    {
        String key = m.key ();
        models.clear (key);

        // Scan for any models we may depend on, and add them first.
        // This minimizes redundant equations.
        m.visit (new Visitor ()
        {
            public boolean visit (MNode node)
            {
                String key = node.key ();
                if (key.equals ("$inherit"))
                {
                    String inherit = MPart.parseInheritOne (node.get ());
                    MNode d = models.child (inherit);
                    if (d != null) addModel (d, models, um);
                    return false;
                }
                return true;
            }
        });

        AddDoc add = new AddDoc (key, m);
        add.setSilent ();
        um.apply (add);
    }
}
