package com.haulmont.components.imap.service;

import com.haulmont.components.imap.dto.MailFolderDto;
import com.haulmont.components.imap.dto.MailMessageDto;
import com.haulmont.components.imap.entity.ImapMessageRef;
import com.haulmont.components.imap.entity.MailBox;
import com.haulmont.cuba.core.entity.FileDescriptor;

import javax.mail.Flags;
import javax.mail.MessagingException;
import java.io.Serializable;
import java.util.Collection;

public interface ImapAPIService {
    String NAME = "mailcomponent_ImapAPIService";

    void testConnection(MailBox box) throws MessagingException;
    Collection<MailFolderDto> fetchFolders(MailBox box) throws MessagingException;

    MailMessageDto fetchMessage(ImapMessageRef messageRef) throws MessagingException;
    Collection<MailMessageDto> fetchMessages(Collection<ImapMessageRef> messageRefs) throws MessagingException;
    Collection<FileDescriptor> fetchAttachments(ImapMessageRef msg) throws MessagingException;

    void moveMessage(ImapMessageRef msg, String folderName) throws MessagingException;
    void deleteMessage(ImapMessageRef messageRef) throws MessagingException;
    void markAsRead(ImapMessageRef messageRef) throws MessagingException;
    void markAsImportant(ImapMessageRef messageRef) throws MessagingException;
    void setFlag(ImapMessageRef messageRef, Flag flag, boolean set) throws MessagingException;

    class Flag implements Serializable {
        private final Flags flags;
        private final String name;

        public static final Flag SEEN = new Flag(Flags.Flag.SEEN);
        public static final Flag ANSWERED = new Flag(Flags.Flag.ANSWERED);
        public static final Flag DRAFT = new Flag(Flags.Flag.DRAFT);
        public static final Flag DELETED = new Flag(Flags.Flag.DELETED);
        public static final Flag IMPORTANT = new Flag(Flags.Flag.FLAGGED);
        public static final Flag RECENT = new Flag(Flags.Flag.RECENT);

        public Flag(String name) {
            this(new Flags(name), name);
        }

        private Flag(Flags.Flag systemFlag) {
            this(new Flags(systemFlag), systemFlag.toString());
        }

        private Flag(Flags flags, String name) {
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

}