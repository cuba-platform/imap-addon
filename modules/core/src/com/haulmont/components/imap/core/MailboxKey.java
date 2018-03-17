package com.haulmont.components.imap.core;

import java.util.Objects;

public class MailboxKey {
    private String host;
    private int port;

    public MailboxKey() {
    }

    public MailboxKey(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MailboxKey that = (MailboxKey) o;
        return port == that.port &&
                Objects.equals(host, that.host);
    }

    @Override
    public int hashCode() {

        return Objects.hash(host, port);
    }

    @Override
    public String toString() {
        return host + ":" + port;
    }
}
