/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.connect.orientdb.test;

import gov.sandia.umf.platform.connect.orientdb.ui.ConnectionManager;
import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.connect.orientdb.ui.OrientConnectDetails;
import gov.sandia.umf.platform.connect.orientdb.ui.OrientDatasource;

import com.orientechnologies.orient.core.record.ORecord;
import com.orientechnologies.orient.core.record.ORecordListener;
import com.orientechnologies.orient.core.record.impl.ODocument;

public class OrientDbVersioning {
    public static void main(String[] args) {
        OrientConnectDetails details = new OrientConnectDetails(
            "N2A Remote",
            "remote:dtrumbo1.srn.sandia.gov/n2a",
            "admin",
            "admin"
        );
        ConnectionManager.getInstance().connect(details);
        OrientDatasource source = ConnectionManager.getInstance().getDataModel();

        NDoc doc = source.getById("gov.sandia.umf.n2a$Model", "#10:3");
        System.out.println(doc);

        /*

        NDoc wheel = new NDoc("Wheel");
        wheel.set("something", System.currentTimeMillis());
        wheel.save();


        ODocument odoc = new ODocument();
        odoc.field("hello", 234234);
        odoc.save();

        if(true) {
            return;
        }


//        NDoc car = source.getById("Car", "#19:0");
//        car.set("wheel", wheel);
//        car.save();

        NDoc car = source.getById("Car", "#19:0");
        NDoc car2 = source.getById("Car", "#19:0");
        car.getSource().addListener(new ORecordListener() {
            public void onEvent(ORecord<?> arg0, EVENT arg1) {
                System.out.println(arg0 + "====" + arg1);
            }
        });

        debug("CAR's WHEEL", (NDoc) car.get("wheel"));
        debug("CAR AFTER LOAD", car);
        debug("CAR2 AFTER LOAD", car2);

        car.set("whyhello", "there222222222");

        debug("CAR AFTER SET", car);

        car.save();

        debug("*CAR AFTER SAVE", car);
        debug("*CAR2 AFTER SAVE", car2);

        car2.getSource().reload();
//        car2.getSource().reset();
//        car2.getSource().undo();
        car2.save();

        debug("CAR2 AFTER SAVE", car2);

        */
    }

    private static void debug(String label, NDoc doc) {
        System.out.println("Debugging '" + label + "': ");
        System.out.println(" -- NDOC HC: " + doc.hashCode());
        System.out.println(" -- NDOC TS: " + doc.toString());
        System.out.println(" -- ODOC HC: " + doc.getSource().hashCode());
        System.out.println(" -- ODOC TS: " + doc.getSource().toString());
        System.out.println(" -- ODOC/NDOC ID: " + doc.getSource().getIdentity());
        System.out.println(" -- ODOC VERS: " + doc.getSource().getVersion());
        System.out.println(" -- ODOC isDirty: " + doc.getSource().isDirty());
        System.out.println(" -- ODOC isEmbedded: " + doc.getSource().isEmbedded());
        System.out.println(" -- ODOC isEmpty: " + doc.getSource().isEmpty());
        System.out.println(" -- ODOC isLazyLoad: " + doc.getSource().isLazyLoad());
        System.out.println(" -- ODOC isOrdered: " + doc.getSource().isOrdered());
        System.out.println(" -- ODOC isPinned: " + doc.getSource().isPinned());
        System.out.println(" -- ODOC isTrackingChanges: " + doc.getSource().isTrackingChanges());
        System.out.println(" -- ODOC status: " + doc.getSource().getInternalStatus());
    }
}
