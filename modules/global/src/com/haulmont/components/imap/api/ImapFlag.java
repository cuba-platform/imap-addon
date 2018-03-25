package com.haulmont.components.imap.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.mail.Flags;
import java.io.Serializable;
import java.util.Arrays;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImapFlag implements Serializable {
    private final SystemFlag systemFlag;
    private final String name;

    public static final ImapFlag SEEN = new ImapFlag(SystemFlag.SEEN);
    public static final ImapFlag ANSWERED = new ImapFlag(SystemFlag.ANSWERED);
    public static final ImapFlag DRAFT = new ImapFlag(SystemFlag.DRAFT);
    public static final ImapFlag DELETED = new ImapFlag(SystemFlag.DELETED);
    public static final ImapFlag IMPORTANT = new ImapFlag(SystemFlag.IMPORTANT);
    public static final ImapFlag RECENT = new ImapFlag(SystemFlag.RECENT);

    @JsonCreator
    public ImapFlag(@JsonProperty("name") String name) {
        this(null, name);
    }

    @JsonCreator
    public ImapFlag(@JsonProperty("systemFlag") SystemFlag systemFlag) {
        this(systemFlag, null);
    }

    private ImapFlag(SystemFlag systemFlag, String name) {
        this.systemFlag = systemFlag;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public SystemFlag getSystemFlag() {
        return systemFlag;
    }

    public Flags imapFlags() {
        return systemFlag != null ? new Flags(systemFlag.systemFlag) : new Flags(name);
    }

    public enum SystemFlag {
        SEEN(Flags.Flag.SEEN),
        ANSWERED(Flags.Flag.ANSWERED),
        DRAFT(Flags.Flag.DRAFT),
        DELETED(Flags.Flag.DELETED),
        IMPORTANT(Flags.Flag.FLAGGED),
        RECENT(Flags.Flag.RECENT);

        private transient Flags.Flag systemFlag;

        SystemFlag(Flags.Flag systemFlag) {
            this.systemFlag = systemFlag;
        }

        public static SystemFlag valueOf(Flags.Flag systemFlag) {
            return Arrays.stream(values()).filter(f -> f.systemFlag.equals(systemFlag)).findFirst().orElse(null);
        }
    }
}
