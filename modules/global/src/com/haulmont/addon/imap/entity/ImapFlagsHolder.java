package com.haulmont.addon.imap.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haulmont.addon.imap.api.ImapFlag;
import com.haulmont.chile.core.annotations.MetaClass;
import com.haulmont.cuba.core.entity.StandardEntity;
import com.haulmont.cuba.core.entity.annotation.UnavailableInSecurityConstraints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.Flags;
import javax.persistence.Column;
import javax.persistence.Lob;
import javax.persistence.MappedSuperclass;
import javax.persistence.Transient;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@MappedSuperclass
@MetaClass(name = "imap$ImapFlagsHolder")
@UnavailableInSecurityConstraints
public class ImapFlagsHolder extends StandardEntity {

    private final static Logger log = LoggerFactory.getLogger(ImapFlagsHolder.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Lob
    @Column(name = "FLAGS")
    private String flags;

    @Transient
    private List<ImapFlag> internalFlags = Collections.emptyList();

    public String getFlags() {
        return flags;
    }

    void setFlags(String flags) {
        this.flags = flags;
    }

    public void setImapFlags(Flags flags) {
        Flags.Flag[] systemFlags = flags.getSystemFlags();
        String[] userFlags = flags.getUserFlags();
        List<ImapFlag> internalFlags = new ArrayList<>(systemFlags.length + userFlags.length);
        for (Flags.Flag systemFlag : systemFlags) {
            internalFlags.add(new ImapFlag(ImapFlag.SystemFlag.valueOf(systemFlag)));
        }
        for (String userFlag : userFlags) {
            internalFlags.add(new ImapFlag(userFlag));
        }
        try {
            if (!internalFlags.equals(this.internalFlags)) {
                log.debug("Convert imap flags {} to raw string", internalFlags);
                this.flags = OBJECT_MAPPER.writeValueAsString(internalFlags);
                this.internalFlags = internalFlags;
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Can't convert flags " + internalFlags, e);
        }
    }

    public Flags getImapFlags() {
        try {
            log.debug("Parse imap flags from raw string {}", flags);
            if (flags == null) {
                return new Flags();
            }
            this.internalFlags = OBJECT_MAPPER.readValue(this.flags, new TypeReference<List<ImapFlag>>() {});
        } catch (IOException e) {
            throw new RuntimeException("Can't parse flags from string " + flags, e);
        }

        Flags flags = new Flags();
        for (ImapFlag internalFlag : this.internalFlags) {
            flags.add(internalFlag.imapFlags());
        }
        return flags;
    }
}
