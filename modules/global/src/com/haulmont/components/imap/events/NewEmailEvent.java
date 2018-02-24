package com.haulmont.components.imap.events;

import com.haulmont.components.imap.entity.ImapMessageRef;
import com.haulmont.components.imap.entity.MailBox;

public class NewEmailEvent extends BaseImapEvent {

    public NewEmailEvent(ImapMessageRef messageRef) {
        super(messageRef);
    }

    public MailBox getMailBox() {
        return messageRef.getFolder().getMailBox();
    }

    public String getFolderName() {
        return messageRef.getFolder().getName();
    }

    public Long getMessageId() {
        return messageRef.getMsgUid();
    }
}
