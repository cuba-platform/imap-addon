package com.haulmont.components.imap.entity;

import javax.mail.Flags;
import javax.persistence.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haulmont.cuba.core.entity.annotation.OnDeleteInverse;
import com.haulmont.cuba.core.global.DeletePolicy;

import javax.validation.constraints.NotNull;
import com.haulmont.cuba.core.entity.StandardEntity;
import com.haulmont.cuba.core.entity.BaseUuidEntity;
import com.haulmont.chile.core.annotations.NamePattern;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;

@NamePattern("/%s from |msgUid")
@Table(name = "IMAPCOMPONENT_IMAP_MESSAGE")
@Entity(name = "imapcomponent$ImapMessage")
public class ImapMessage extends StandardEntity {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final long serialVersionUID = -295396787486211720L;

    @OnDeleteInverse(DeletePolicy.CASCADE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "FOLDER_ID")
    protected ImapFolder folder;

    @Lob
    @Column(name = "FLAGS")
    protected String flags;

    @NotNull
    @Column(name = "IS_ATL", nullable = false)
    protected Boolean attachmentsLoaded = false;

    @NotNull
    @Column(name = "MSG_UID", nullable = false)
    protected Long msgUid;

    @Column(name = "THREAD_ID")
    protected Long threadId;

    @Column(name = "REFERENCE_ID")
    protected String referenceId;

    @NotNull
    @Column(name = "CAPTION", nullable = false)
    protected String caption;

    @Transient
    private List<InternalFlag> internalFlags = Collections.emptyList();

    public String getFlags() {
        return flags;
    }

    void setFlags(String flags) {
        this.flags = flags;
    }

    public void setImapFlags(Flags flags) {
        Flags.Flag[] systemFlags = flags.getSystemFlags();
        String[] userFlags = flags.getUserFlags();
        List<InternalFlag> internalFlags = new ArrayList<>(systemFlags.length + userFlags.length);
        for (Flags.Flag systemFlag : systemFlags) {
            internalFlags.add(new InternalFlag(new Flags(systemFlag)));
        }
        for (String userFlag : userFlags) {
            internalFlags.add(new InternalFlag(userFlag));
        }
        try {
            if (!internalFlags.equals(this.internalFlags)) {
                this.internalFlags = internalFlags;
                this.flags = objectMapper.writeValueAsString(internalFlags);
            }
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    public Flags getImapFlags() {
        try {
            this.internalFlags = objectMapper.readValue(this.flags, new TypeReference<List<InternalFlag>>() {});
        } catch (IOException e) {
            e.printStackTrace();
        }

        Flags flags = new Flags();
        for (InternalFlag internalFlag : this.internalFlags) {
            if (internalFlag.isSystem()) {
                flags.add(internalFlag.getSystemFlag());
            } else {
                flags.add(internalFlag.getCustomName());
            }
        }
        return flags;
    }


    public void setAttachmentsLoaded(Boolean attachmentsLoaded) {
        this.attachmentsLoaded = attachmentsLoaded;
    }

    public Boolean getAttachmentsLoaded() {
        return attachmentsLoaded;
    }


    public ImapFolder getFolder() {
        return folder;
    }

    public void setFolder(ImapFolder folder) {
        this.folder = folder;
    }


    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }


    public void setThreadId(Long threadId) {
        this.threadId = threadId;
    }

    public Long getThreadId() {
        return threadId;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public String getCaption() {
        return caption;
    }


    public void setMsgUid(Long msgUid) {
        this.msgUid = msgUid;
    }

    public Long getMsgUid() {
        return msgUid;
    }

    private static class InternalFlag implements Serializable {
        private boolean system;
        private String customName;
        private Flags systemFlag;

        public InternalFlag(Flags systemFlag) {
            this.systemFlag = systemFlag;
            this.system = true;
        }

        public InternalFlag(String customName) {
            this.customName = customName;
            this.system = false;
        }

        public boolean isSystem() {
            return system;
        }

        public void setSystem(boolean system) {
            this.system = system;
        }

        public String getCustomName() {
            return customName;
        }

        public void setCustomName(String customName) {
            this.customName = customName;
        }

        public Flags getSystemFlag() {
            return systemFlag;
        }

        public void setSystemFlag(Flags systemFlag) {
            this.systemFlag = systemFlag;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            InternalFlag that = (InternalFlag) o;
            return system == that.system &&
                    Objects.equals(customName, that.customName) &&
                    Objects.equals(systemFlag, that.systemFlag);
        }

        @Override
        public int hashCode() {

            return Objects.hash(system, customName, systemFlag);
        }
    }

}