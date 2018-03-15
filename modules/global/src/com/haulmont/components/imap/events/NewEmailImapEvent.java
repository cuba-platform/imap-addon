package com.haulmont.components.imap.events;

import com.haulmont.components.imap.entity.ImapMessageRef;
//import com.haulmont.components.imap.entity.ImapMailBox;

public class NewEmailImapEvent extends BaseImapEvent {

    public NewEmailImapEvent(ImapMessageRef messageRef) {
        super(messageRef);
    }

    /*public ImapMailBox getMailBox() {
        return messageRef.getFolder().getMailBox();
    }

    public String getFolderName() {
        return messageRef.getFolder().getName();
    }*/

    public Long getMessageId() {
        return messageRef.getMsgUid();
    }
}
