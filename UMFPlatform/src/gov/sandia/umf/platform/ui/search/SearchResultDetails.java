/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.search;

import javax.swing.ImageIcon;

public class SearchResultDetails {
    private ImageIcon icon;
    private String title;
    private String description;
    private String owner;
    private Long lastModified;

    public ImageIcon getIcon() {
        return icon;
    }
    public String getTitle() {
        return title;
    }
    public String getDescription() {
        return description;
    }
    public String getOwner() {
        return owner;
    }
    public Long getLastModified() {
        return lastModified;
    }
    public void setIcon(ImageIcon icon) {
        this.icon = icon;
    }
    public void setTitle(String title) {
        this.title = title;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public void setOwner(String owner) {
        this.owner = owner;
    }
    public void setLastModified(Long lastModified) {
        this.lastModified = lastModified;
    }

}
