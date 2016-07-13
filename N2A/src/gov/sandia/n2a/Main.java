/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a;

import gov.sandia.umf.platform.UMF;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;

import javax.swing.JOptionPane;


/**
    Redacted from gov.sandia.umf.platform.BootstrapLoader
**/
public class Main
{
    public static void main (String[] args)
    {
        try
        {
            ArrayList<String> augmentedArgs = new ArrayList<String> ();
            for (int i = 0; i < args.length; i++) augmentedArgs.add (args[i]);
            augmentedArgs.add ("--plugin");
            augmentedArgs.add ("gov.sandia.n2a.N2APlugin");
            augmentedArgs.add ("--plugin");
            augmentedArgs.add ("gov.sandia.n2a.backend.internal.InternalPlugin");
            augmentedArgs.add ("--plugin");
            augmentedArgs.add ("gov.sandia.n2a.backend.xyce.XycePlugin");
            augmentedArgs.add ("--plugin");
            augmentedArgs.add ("gov.sandia.n2a.backend.stpu.STPUPlugin");
            augmentedArgs.add ("--plugin");
            augmentedArgs.add ("gov.sandia.n2a.backend.c.PluginC");
            augmentedArgs.add ("--product");
            augmentedArgs.add ("gov.sandia.n2a.N2AProductCustomization");

            UMF.main (augmentedArgs.toArray (args));
        }
        catch (Throwable t)
        {
            StringWriter sw = new StringWriter ();
            PrintWriter pw = new PrintWriter (sw);
            t.printStackTrace (pw);
            String trace = sw.toString ().replaceAll ("\\t", "    ");

            JOptionPane.showMessageDialog
            (
                null,
                "Could not start N2A due to the following error:\n\n" + trace,
                "Critical Error",
                JOptionPane.ERROR_MESSAGE
            );
            System.exit (1);
        }
    }
}
