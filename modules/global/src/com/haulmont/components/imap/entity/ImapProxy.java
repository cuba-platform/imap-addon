package com.haulmont.components.imap.entity;

import javax.persistence.Entity;
import javax.persistence.Table;
import com.haulmont.cuba.core.entity.StandardEntity;
import javax.persistence.Column;
import com.haulmont.chile.core.annotations.NamePattern;

@NamePattern("%s:%s|host,port")
@Table(name = "IMAPCOMPONENT_IMAP_PROXY")
@Entity(name = "imapcomponent$ImapProxy")
public class ImapProxy extends StandardEntity {
    private static final long serialVersionUID = -5839274142110060210L;

    @Column(name = "HOST")
    protected String host;

    @Column(name = "PORT")
    protected Integer port;

    @Column(name = "WEB_PROXY")
    protected Boolean webProxy;

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }


    public void setHost(String host) {
        this.host = host;
    }

    public String getHost() {
        return host;
    }

    public void setWebProxy(Boolean webProxy) {
        this.webProxy = webProxy;
    }

    public Boolean getWebProxy() {
        return webProxy;
    }



}