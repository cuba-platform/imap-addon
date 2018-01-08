package com.haulmon.components.imap.entity;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Column;

@Table(name = "MAILCOMPONENT_MAIL_SIMPLE_AUTHENTICATION")
@Entity(name = "mailcomponent$MailSimpleAuthentication")
public class MailSimpleAuthentication extends MailAuthentication {
    private static final long serialVersionUID = 2736929814357214863L;

    @Column(name = "USERNAME", nullable = false)
    protected String username;

    @Column(name = "PASSWORD", nullable = false)
    protected String password;

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