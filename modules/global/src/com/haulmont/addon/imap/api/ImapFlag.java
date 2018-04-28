package com.haulmont.addon.imap.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.mail.Flags;
import java.io.Serializable;
import java.util.Arrays;

/**
 * Unified IMAP Flag
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImapFlag implements Serializable {
    private final SystemFlag systemFlag;
    private final String name;

    /**
     * Corresponds to standard {@link javax.mail.Flags.Flag#SEEN} flag
     */
    @SuppressWarnings("unused")
    public static final ImapFlag SEEN = new ImapFlag(SystemFlag.SEEN);
    /**
     * Corresponds to standard {@link javax.mail.Flags.Flag#ANSWERED} flag
     */
    @SuppressWarnings("unused")
    public static final ImapFlag ANSWERED = new ImapFlag(SystemFlag.ANSWERED);
    /**
     * Corresponds to standard {@link javax.mail.Flags.Flag#DRAFT} flag
     */
    @SuppressWarnings("unused")
    public static final ImapFlag DRAFT = new ImapFlag(SystemFlag.DRAFT);
    /**
     * Corresponds to standard {@link javax.mail.Flags.Flag#DELETED} flag
     */
    @SuppressWarnings("unused")
    public static final ImapFlag DELETED = new ImapFlag(SystemFlag.DELETED);
    /**
     * Corresponds to standard {@link javax.mail.Flags.Flag#FLAGGED} flag
     */
    @SuppressWarnings("unused")
    public static final ImapFlag IMPORTANT = new ImapFlag(SystemFlag.IMPORTANT);
    /**
     * Corresponds to standard {@link javax.mail.Flags.Flag#RECENT} flag
     */
    @SuppressWarnings("unused")
    public static final ImapFlag RECENT = new ImapFlag(SystemFlag.RECENT);

    /**
     * Constructs custom flag with specified name
     * @param name custom flag name
     */
    public ImapFlag(String name) {
        this(null, name);
    }

    /**
     * Constructs standard flag with specified {@link SystemFlag} value
     * @param systemFlag standard flag
     */
    public ImapFlag(SystemFlag systemFlag) {
        this(systemFlag, null);
    }

    @JsonCreator
    private ImapFlag(@JsonProperty("systemFlag") SystemFlag systemFlag, @JsonProperty("name") String name) {
        this.systemFlag = systemFlag;
        this.name = name;
    }

    /**
     * @return name of custom flag or null for standard
     */
    public String getName() {
        return name;
    }

    /**
     * @return {@link SystemFlag} value of standard flag or null for custom
     */
    @SuppressWarnings("unused")
    public SystemFlag getSystemFlag() {
        return systemFlag;
    }

    /**
     * convert to java.mail {@link javax.mail.Flags} object
     */
    public Flags imapFlags() {
        return systemFlag != null ? new Flags(systemFlag.systemFlag) : new Flags(name);
    }

    @Override
    public String toString() {
        return "ImapFlag{" +
                "systemFlag=" + systemFlag +
                ", name='" + name + '\'' +
                '}';
    }

    /**
     * Standard IMAP Flags
     */
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

        /**
         * convert from java.mail {@link javax.mail.Flags.Flag}
         */
        public static SystemFlag valueOf(Flags.Flag systemFlag) {
            return Arrays.stream(values()).filter(f -> f.systemFlag.equals(systemFlag)).findFirst().orElse(null);
        }
    }
}
