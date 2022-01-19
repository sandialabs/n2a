/*
Copyright 2013-2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.jobs;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import gov.sandia.n2a.ui.images.ImageUtil;
import gov.sandia.n2a.ui.jobs.PanelRun.DisplayThread;

import javax.swing.Icon;
import javax.swing.ImageIcon;

@SuppressWarnings("serial")
public class NodeFile extends NodeBase
{
    protected Path      path;
    protected int       priority;
    protected ImageIcon icon;
    protected boolean   found;  // Flag to indicate if associated file was found during last scan.

    public static final ImageIcon iconIn  = ImageUtil.getImage ("file_in.gif");
    public static final ImageIcon iconOut = ImageUtil.getImage ("file_out.gif");
    public static final ImageIcon iconErr = ImageUtil.getImage ("file_err.gif");
    public static final ImageIcon iconObj = ImageUtil.getImage ("file_obj.gif");

    public NodeFile (Path path)
    {
        this.path = path;
        priority  = 0;
        icon      = iconObj;
        setUserObject (path.getFileName ().toString ());
    }

    @Override
    public Icon getIcon (boolean expanded)
    {
        return icon;
    }

    /**
        Indicates that the file could have an auxiliary columns file.
    **/
    public boolean couldHaveColumns ()
    {
        return true;
    }

    public boolean isGraphable ()
    {
        if (! couldHaveColumns ()) return false;

        // An auxiliary column file is sufficient evidence that this is tabular data.
        Path dir = path.getParent ();
        String fileName = path.getFileName ().toString ();
        boolean result = Files.exists (dir.resolve (fileName + ".columns"));

        // Check beginning of file
        if (! result)
        {
            try (BufferedReader reader = Files.newBufferedReader (path))
            {
                String line = reader.readLine ();
                result = line.startsWith ("$t")  ||  line.startsWith ("Index");
                if (! result)
                {
                    // Try an alternate heuristic: Does the line appear to be a set of tab-delimited fields?
                    // Don't allow spaces, because it could look too much like ordinary text.
                    line = reader.readLine ();  // Get a second line, just to ensure we get past textual headers.
                    if (line != null)
                    {
                        String[] pieces = line.split ("\\t");
                        int columns = 0;
                        for (String p : pieces)
                        {
                            if (p.length () < 20)
                            {
                                try
                                {
                                    Float.parseFloat (p);
                                    columns++;  // If that didn't throw an exception, then p is likely a number.
                                }
                                catch (Exception e) {}
                            }
                        }
                        // At least 3 viable columns, and more than half are interpretable as numbers.
                        result = columns >= 3  &&  (double) columns / pieces.length > 0.7;
                    }
                }
            }
            catch (IOException e) {}
        }
        return result;
    }

    /**
        Allow subclasses to override the default rendering method implemented in PanelRun.DisplayThread
        @return true if overridden. Otherwise false.
    **/
    public boolean render (DisplayThread dt)
    {
        return false;
    }
}
