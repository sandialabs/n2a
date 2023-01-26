/*
Copyright 2021-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.ref;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MNode.Visitor;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.plugins.extpoints.Export;

public abstract class ExportBibliography implements Export
{
    @Override
    public void export (MNode document, Path destination) throws Exception
    {
        MNode references = new MVolatile ();
        if (document.child ("$meta") == null  &&  document.child ("form") != null)  // document is a reference.
        {
            references.set (document, document.key ());
        }
        else  // document is a model
        {
            // Iterate over model, collecting all references and their contexts.
            document.visit (new Visitor ()
            {
                public boolean visit (MNode node)
                {
                    if (! node.key ().equals ("$ref")) return true;

                    String[] keyPath = node.parent ().keyPath ();
                    String location = keyPath[0];
                    for (int i = 1; i < keyPath.length; i++) location += "." + keyPath[i];

                    for (MNode r : node)
                    {
                        MNode n = references.childOrCreate (r.key (), "note");
                        String note = n.get ();
                        if (! note.isEmpty ()) note += "\n";
                        note += location + " -- " + r.get ();
                        n.set (note);
                    }
                    return false;
                }
            });

            // Fill in reference records
            for (MNode r : references)
            {
                MNode doc = AppData.references.child (r.key ());
                if (doc == null) continue;
                r.mergeUnder (doc);

                String docNote = doc.get ("note");
                if (docNote.isEmpty ()) continue;
                String note = r.get ("note");
                if (note.isEmpty ()) continue;
                docNote += "\n" + note;
                r.set (note, "note");
            }
        }

        try (Writer writer = Files.newBufferedWriter (destination))
        {
            export (references, writer);
        }
    }

    public abstract void export (MNode references, Writer writer) throws IOException;
}
