/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform;

import java.io.PrintWriter;
import java.io.StringWriter;

import javax.swing.JOptionPane;


/**
 * This class is intended to be the class listed in
 * a deployment JAR's manifest file for Main-Class.
 * The purpose of this class is to catch every
 * exception or error that occurs while launching the
 * JAR.  This is important because when a JAR fails
 * to launch properly, there is rarely an indication
 * of what went wrong.  The primary and most important
 * use case for this file is this:  if the JAR was not
 * packaged correctly and classes are referenced that
 * do not exist in the JAR, a hard stop would normally
 * occur sometimes without any indication of the failure.
 * The OS may report that the JAR encountered an error
 * upon launch, but cannot report the exception or
 * error thrown from within the JAR.
 *
 * Using this class as the entry point into a JAR file
 * ensures that at least *something* is reported back
 * to the user if a disastrous failure occurs.
 *
 * This class is self-contained, meaning that it only
 * uses built-in libraries that exist within the standard
 * JVM.  This is so this class itself cannot fail.
 *
 * This class is a template and cannot be "imported" and
 * used.  Copy this class into your project and replace
 * the code in main as needed.
 *
 * @author Derek Trumbo
 */

public class BootstrapLoader {

    public static void main(String[] args) {
        try {

            UMF.main(args);

            /**
             * Your code here.
             *
             * Depending on your deployment strategy (i.e. how you have
             * your deployment JARs arranged), this may be a call to
             * some class's main method, or may be an execution of a JAR
             * file, causing the OS to call the main method of the class
             * listed inside that JAR file's manifest file (Main-Class).
             */

        } catch(Throwable t) {
            t.printStackTrace();

            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            t.printStackTrace(pw);

            String trace = sw.toString().replaceAll("\\t", "    ");

            String errMsg = "Could not start application.  Are all " +
                "required classes/JARs on the classpath?\n----------\n" +
                trace;

            JOptionPane.showMessageDialog(null, errMsg, "Critical Error",
                JOptionPane.ERROR_MESSAGE);

            System.exit(1);
        }
    }
}
