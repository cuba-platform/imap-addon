package com.haulmont.components.imap.service;

import com.haulmont.components.imap.dto.MailFolderDto;
import com.haulmont.components.imap.dto.MailMessageDto;
import com.haulmont.components.imap.entity.MailBox;
import com.haulmont.components.imap.entity.demo.MailMessage;
import javax.mail.MessagingException;
import java.io.Serializable;
import java.util.List;

public interface ImapService {
    String NAME = "mailcomponent_ImapService";

    void testConnection(MailBox box) throws MessagingException;
    List<MailFolderDto> fetchFolders(MailBox box) throws MessagingException;

    MailMessageDto fetchMessage(MessageRef messageRef) throws MessagingException;
    List<MailMessageDto> fetchMessages(List<MessageRef> messageRefs) throws MessagingException;

    void deleteMessage(MessageRef messageRef) throws MessagingException;

    class MessageRef implements Serializable {
        private MailBox mailBox;
        private String folderName;
        private long uid;

        public MailBox getMailBox() {
            return mailBox;
        }

        public void setMailBox(MailBox mailBox) {
            this.mailBox = mailBox;
        }

        public String getFolderName() {
            return folderName;
        }

        public void setFolderName(String folderName) {
            this.folderName = folderName;
        }

        public long getUid() {
            return uid;
        }

        public void setUid(long uid) {
            this.uid = uid;
        }
    }
}