/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.ensemble.domains;

import gov.sandia.umf.platform.ensemble.params.ParameterSet;
import gov.sandia.umf.platform.ui.ensemble.ParameterKeyPath;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.ImageIcon;

import replete.util.StringUtil;


public class ParameterDomain {


    ////////////
    // FIELDS //
    ////////////

    private String name;
    private ImageIcon icon;
    private List<ParameterDomain> subdomains;
    private List<Parameter> params;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    // This class should have always just been a standard map.
    public ParameterDomain() {
        this(null, null);
    }
    public ParameterDomain(String name) {
        this(name, null);
    }
    public ParameterDomain(String name, ImageIcon icon) {
        this.name = name;
        this.icon = icon;
        subdomains = new ArrayList<ParameterDomain>();
        params = new ArrayList<Parameter>();
    }
    public ParameterDomain(ParameterSet set) {  // Uses a PS Map with Array-Based Path Keys
        subdomains = new ArrayList<ParameterDomain>();
        params = new ArrayList<Parameter>();
        copyFrom(this, set);
    }
    private void copyFrom(ParameterDomain parent, ParameterSet set) {
        for(Object K : set.keySet()) {
            ParameterKeyPath P = (ParameterKeyPath) K;
            ParameterDomain dum = parent;
            for(int s = 0; s < P.size() - 1; s++) {
                Object S = P.get(s);
                ParameterDomain subdum = dum.getSubdomain((String) S);
                if(subdum == null) {
                    subdum = new ParameterDomain((String) S);
                    dum.addSubdomain(subdum);
                }
                dum = subdum;
            }
            Object paramKey = P.get(P.size() - 1);
            Object value = set.get(K);
            Parameter param = new Parameter(paramKey, value);
            dum.addParameter(param);
        }
    }


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    // Accessors

    public String getName() {
        return name;
    }
    public ImageIcon getIcon() {
        return icon;
    }
    public List<ParameterDomain> getSubdomains() {
        return subdomains;
    }
    public List<Parameter> getParameters() {
        return params;
    }
    public Map<Object, Parameter> getParameterMap() {
        Map<Object, Parameter> map = new LinkedHashMap<Object, Parameter>();
        for(Parameter param : params) {
            map.put(param.getKey(), param);
        }
        return map;
    }

    public Object getValue (String name)
    {
        return getValue (name, "");
    }

    public Object getValue (String name, Object defaultValue)
    {
        for (Parameter param : params)
        {
            if (param.getKey().equals (name))
            {
                return param.getDefaultValue ();
            }
        }
        return defaultValue;
    }

    // Should really be a map but it's not for right now.
    public Parameter getParameter(String name) {
        for(Parameter param : params) {
            if(param.getKey().equals(name)) {
                return param;
            }
        }
        return null;
    }

    // Should really be a map but it's not for right now.
    public ParameterDomain getSubdomain(String name) {
        for(ParameterDomain subdomain : subdomains) {
            if(subdomain.getName().equals(name)) {
                return subdomain;
            }
        }
        return null;
    }

    // Mutators

    public void setName(String name) {
        this.name = name;
    }
    public void setIcon(ImageIcon icon) {
        this.icon = icon;
    }
    public void addParameter(Parameter param) {
        params.add(param);
    }
    public void addSubdomain(ParameterDomain subdomain) {
        subdomains.add(subdomain);
    }
    public void clear() {
        subdomains.clear();
        params.clear();
    }


    /////////////
    // HELPERS //
    /////////////

    public static Map<Object, Object> flattenDomains(ParameterDomain domains) {
        Map<Object, Object> map = new LinkedHashMap<Object, Object>();
        for(ParameterDomain domain : domains.getSubdomains()) {
            ParameterKeyPath path = new ParameterKeyPath(domain.getName());
            flattenDomains(map, domain, path);
        }
        return map;
    }

    public static void flattenDomains(Map<Object, Object> flattened, ParameterDomain domain, ParameterKeyPath prefix) {
        for(Parameter param : domain.getParameters()) {
            ParameterKeyPath path = prefix.plus(param.getKey());
            flattened.put(path, param.getDefaultValue());
        }
        for(ParameterDomain subDomain : domain.getSubdomains()) {
            ParameterKeyPath path = prefix.plus(subDomain.getName());
            flattenDomains(flattened, subDomain, path);
        }
    }

    public void list() {
        list(0);
    }
    private void list(int level) {
        String sp = StringUtil.spaces(level * 4);
        String sp2 = StringUtil.spaces((level + 1) * 4);
        System.out.println(sp + "[" + name + "]");
        for(Parameter param : params) {
            System.out.println(sp2 + param.getKey() + " = " + param.getDefaultValue());
        }
        for(ParameterDomain domain : subdomains) {
            domain.list(level + 1);
        }
    }


    ////////////////
    // OVERRIDDEN //
    ////////////////

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if(this == obj) {
            return true;
        }
        if(obj == null) {
            return false;
        }
        if(getClass() != obj.getClass()) {
            return false;
        }
        ParameterDomain other = (ParameterDomain) obj;
        if(name == null) {
            if(other.name != null) {
                return false;
            }
        } else if(!name.equals(other.name)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return name;
    }
}
