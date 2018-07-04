package com.haulmont.addon.imap.core;

import com.haulmont.addon.imap.entity.ImapMailBox;

import java.util.Objects;
import java.util.UUID;

public class MailboxKey {
    final UUID id;
    final String host;
    final int port;
    final String userName;

    public MailboxKey(ImapMailBox mailBox) {
        this(mailBox.getId(), mailBox.getHost(), mailBox.getPort(), mailBox.getAuthentication().getUsername());
    }

    MailboxKey(UUID id, String host, int port, String userName) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.userName = userName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MailboxKey that = (MailboxKey) o;
        return port == that.port &&
                Objects.equals(host, that.host) &&
                Objects.equals(userName, that.userName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port, userName);
    }

    @Override
    public String toString() {
        return String.format("%s:%d[user:%s]", host, port, userName);
    }
}
