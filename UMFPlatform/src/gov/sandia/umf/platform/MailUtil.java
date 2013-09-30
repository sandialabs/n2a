/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform;

import org.apache.log4j.Logger;

import replete.mail.Mailer;
import replete.util.DateUtil;
import replete.util.ExceptionUtil;
import replete.util.User;

public class MailUtil {
    private static Logger logger = Logger.getLogger(MailUtil.class);

    public static void sendErrorMail(Throwable t) {
        final String dev = "dtrumbo";
        final String user = User.getName();
        try {
            Mailer.setDefaultFormat("text/plain");
            String bod ="Date: " + DateUtil.toLongString(System.currentTimeMillis()) + "\n" + ExceptionUtil.toCompleteString(t, 6);
            Mailer.sendEmail(user + "@sandia.gov", dev + "@sandia.gov", null, "N2A Application Error", bod);
            Mailer.sendEmail(user + "@sandia.gov", "cewarr@sandia.gov", null, "N2A Application Error", bod);
        } catch(Exception e) {
            logger.error("Could not send email for error.", e);
            //e.printStackTrace();
        }
    }
}
