package com.haulmont.components.imap.api;

import javax.mail.Flags;
import java.io.Serializable;

public class ImapFlag implements Serializable {
    private final Flags flags;
    private final String name;

    public static final ImapFlag SEEN = new ImapFlag(Flags.Flag.SEEN);
    public static final ImapFlag ANSWERED = new ImapFlag(Flags.Flag.ANSWERED);
    public static final ImapFlag DRAFT = new ImapFlag(Flags.Flag.DRAFT);
    public static final ImapFlag DELETED = new ImapFlag(Flags.Flag.DELETED);
    public static final ImapFlag IMPORTANT = new ImapFlag(Flags.Flag.FLAGGED);
    public static final ImapFlag RECENT = new ImapFlag(Flags.Flag.RECENT);

    public ImapFlag(String name) {
        this(new Flags(name), name);
    }

    private ImapFlag(Flags.Flag systemFlag) {
        this(new Flags(systemFlag), systemFlag.toString());
    }

    private ImapFlag(Flags flags, String name) {
        this.flags = flags;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    Flags getFlags() {
        return flags;
    }
}
