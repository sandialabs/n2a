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

import gov.sandia.n2a.db.MNode;

public class ExportBibTeX extends ExportBibliography
{
    @Override
    public String getName ()
    {
        return "BibTeX";
    }

    @Override
    public void process (MNode references, Writer writer) throws IOException
    {
        String nl = String.format ("%n");

        for (MNode r : references)
        {
            writer.write ("@" + r.get ("form") + "{" + r.key () + "," + nl);
            for (MNode c : r) writer.write ("  " + c.key () + "={" + c.get ().replace ("\n", nl) + "}," + nl);
            writer.write ("}" + nl);
        }
    }

    @Override
    public boolean accept (Path source)
    {
        if (Files.isDirectory (source)) return true;
        String name = source.getFileName ().toString ();
        int lastDot = name.lastIndexOf ('.');
        if (lastDot >= 0)
        {
            String suffix = name.substring (lastDot);
            if (suffix.equalsIgnoreCase (".bib"))    return true;
            if (suffix.equalsIgnoreCase (".bibtex")) return true;
        }
        return false;
    }

    @Override
    public String suffix ()
    {
        return ".bib";
    }
}
