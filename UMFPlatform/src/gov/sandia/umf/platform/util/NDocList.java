/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.util;

import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.util.NDocList.NDocGlob;

import java.util.ArrayList;
import java.util.List;

public class NDocList extends ArrayList<NDocGlob> {
    public class NDocGlob {
        public String id;
        public NDoc bean;
        @Override
        public String toString() {
            return bean.toString();
        }
    }

    public void add(NDoc bean) {
        add(size(), bean);
    }
    public void add(int index, NDoc bean) {
        if(bean == null) {
            throw new NullPointerException("Cannot add null bean.");
        }
        NDocGlob bg = new NDocGlob();
        bg.id = bean.getId();
        bg.bean = bean;
        add(index, bg);
    }

    public List<NDoc> getBeans() {
        List<NDoc> beans = new ArrayList<NDoc>();
        for(NDocGlob bg : this) {
            beans.add(bg.bean);
        }
        return beans;
    }
    public boolean contains(NDoc bean) {
        return indexOf(bean) != -1;
    }
    public int indexOf(NDoc bean) {
        int i = 0;
        for(NDocGlob bg : this) {
            if(bg.bean == bean) {
                return i;
            }
            if(bg.id.equals(bean.getId())) {  // NPE?? TODO
                return i;
            }
            i++;
        }
        return -1;
    }

    public void remove(NDoc bean) {
        int i = indexOf(bean);
        if(i != -1) {
            remove(i);
        }
    }
}
