/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.wp;

import replete.util.BasicDataModel;

public class WorkpaneModel extends BasicDataModel {
/*

    ////////////
    // FIELDS //
    ////////////

    // State

    private List<WorkpaneRecord> myWork = new ArrayList<WorkpaneRecord>();
    private List<WorkpaneRecord> recent = new ArrayList<WorkpaneRecord>();

//    public

    public void init() {
        for(WorkpaneRecord record : myWork) {
            initRecord(record);
        }
        for(WorkpaneRecord record : recent) {
            initRecord(record);
        }
        fireDataModelChangedNotifier();
    }

    private void initRecord(WorkpaneRecord record) {
        try {
            String pkgName = PartX.class.getPackage().getName();
            Class<? extends BeanBase> clazz;
                clazz = (Class<? extends BeanBase>) Class.forName(pkgName + "." + record.getBeanClass());
            BeanBase b = DataModelFactory.getInstance().getDataModel().get(clazz, record.getId());
            record.setBean(b);
        } catch(ClassNotFoundException e) {
            record.setError(true);
        }
    }

    // Serialization
    private Object readResolve() {
        batchChangeLock = new BatchChangeLock();
        dataModelChangedNotifier = new ChangeNotifier(this);
        return this;
    }


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    // Accessors

    public List<WorkpaneRecord> getMyWork() {
        return myWork;
    }
    public List<WorkpaneRecord> getRecent() {
        return recent;
    }

    // Mutators

    public void addMyWork(BeanBase bean) {
        WorkpaneRecord wpr = new WorkpaneRecord(bean);
        myWork.add(wpr);
        fireDataModelChangedNotifier();
    }
    public void addRecent(BeanBase bean, boolean top) {
        for(int i = recent.size() - 1; i >= 0; i--) {
            if(recent.get(i).getId().equals(bean.getId())) {
                recent.remove(i);
            }
        }
        WorkpaneRecord wpr = new WorkpaneRecord(bean);
        if(top) {
            recent.add(0, wpr);
        } else {
            recent.add(wpr);
        }
        fireDataModelChangedNotifier();
    }
    public void removeMyWork(int[] idxs) {
        for(int i = idxs.length - 1; i >= 0; i--) {
            myWork.remove(idxs[i]);
        }
        fireDataModelChangedNotifier();
    }
    public void removeRecent(int[] idxs) {
        for(int i = idxs.length - 1; i >= 0; i--) {
            recent.remove(idxs[i]);
        }
        fireDataModelChangedNotifier();
    }
    public boolean isInMyWork(BeanBase b) {
        boolean alreadyIn = false;
        for(int i = myWork.size() - 1; i >= 0; i--) {
            if(myWork.get(i).getId().equals(b.getId())) {
                alreadyIn = true;
            }
        }
        return alreadyIn;
    }
    public void setInMyWork(BeanBase b, boolean in) {
        try {
            if(in) {
                if(!isInMyWork(b)) {
                    myWork.add(new WorkpaneRecord(b));
                }
            } else {
                for(int i = myWork.size() - 1; i >= 0; i--) {
                    if(myWork.get(i).getId().equals(b.getId())) {
                        myWork.remove(i);
                    }
                }
            }
        } finally {
            fireDataModelChangedNotifier();
        }
    }
    public void updateRecords(BeanBase b) {
        try {
            for(WorkpaneRecord record : myWork) {
                if(record.getBean() == b) {
                    record.update(b);
                }
            }
            for(WorkpaneRecord record : recent) {
                if(record.getBean() == b) {
                    record.update(b);
                }
            }
        } finally {
            fireDataModelChangedNotifier();
        }
    }

    public void removeTempRecords() {
        try {
            for(int i = myWork.size() - 1; i >= 0; i--) {
                if(myWork.get(i).getId() <= 0) {
                    myWork.remove(i);
                }
            }
            for(int i = recent.size() - 1; i >= 0; i--) {
                if(recent.get(i).getId() <= 0) {
                    recent.remove(i);
                }
            }
        } finally {
            fireDataModelChangedNotifier();
        }
    }

    public void removeRecord(BeanBase bean) {
        try {
            for(int i = myWork.size() - 1; i >= 0; i--) {
                if(myWork.get(i).getId().equals(bean.getId()) && myWork.get(i).getBeanClass().equals(bean.getClass().getSimpleName())) {
                    myWork.remove(i);
                }
            }
            for(int i = recent.size() - 1; i >= 0; i--) {
                if(recent.get(i).getId().equals(bean.getId()) && recent.get(i).getBeanClass().equals(bean.getClass().getSimpleName())) {
                    recent.remove(i);
                }
            }
        } finally {
            fireDataModelChangedNotifier();
        }
    }*/
}
