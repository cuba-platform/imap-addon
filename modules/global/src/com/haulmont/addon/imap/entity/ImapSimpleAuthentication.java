package com.haulmont.addon.imap.entity;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Column;

@Table(name = "IMAPCOMPONENT_IMAP_SIMPLE_AUTHENTICATION")
@Entity(name = "imapcomponent$ImapSimpleAuthentication")
public class ImapSimpleAuthentication extends ImapAuthentication {
    private static final long serialVersionUID = 2736929814357214863L;

    @Column(name = "USERNAME", nullable = false)
    protected String username;

    @Column(name = "PASSWORD", nullable = false)
    protected String password;

    @SuppressWarnings("unused")
    public void setUsername(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPassword() {
        return password;
    }


}