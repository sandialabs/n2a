/*
Copyright 2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.ref;

import java.io.IOException;
import java.io.Writer;
import gov.sandia.n2a.db.MNode;

public class ExportBibTeX extends ExportBibliography
{
    @Override
    public String getName ()
    {
        return "BibTeX";
    }

    @Override
    public void export (MNode references, Writer writer) throws IOException
    {
        String nl = String.format ("%n");

        for (MNode r : references)
        {
            writer.write ("@" + r.get ("form") + "{" + r.key () + "," + nl);
            for (MNode c : r) writer.write ("  " + c.key () + "={" + c.get ().replace ("\n", nl) + "}," + nl);
            writer.write ("}" + nl);
        }
    }
}
