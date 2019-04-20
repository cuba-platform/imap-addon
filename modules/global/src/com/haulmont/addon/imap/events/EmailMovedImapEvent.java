/*
 * Copyright (c) 2008-2019 Haulmont.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.haulmont.addon.imap.events;

import com.haulmont.addon.imap.entity.ImapFolder;
import com.haulmont.addon.imap.entity.ImapMessage;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Event triggered when IMAP message was moved to different folder
 */
public class EmailMovedImapEvent extends BaseImapEvent {

    private final String oldFolderName;
    private final ImapFolder oldFolder;

    @SuppressWarnings("WeakerAccess")
    @Deprecated
    public EmailMovedImapEvent(ImapMessage message, String oldFolderName) {
        super(message);
        this.oldFolder = null;
        this.oldFolderName = oldFolderName;
    }

    public EmailMovedImapEvent(ImapMessage message, ImapFolder oldFolder) {
        super(message);
        this.oldFolder = oldFolder;
        this.oldFolderName = oldFolder.getName();
    }

    @SuppressWarnings("unused")
    public String getNewFolderName() {
        return message.getFolder().getName();
    }

    @SuppressWarnings("unused")
    public String getOldFolderName() {
        return oldFolder != null ? oldFolder.getName() : oldFolderName;
    }

    @SuppressWarnings("unused")
    public ImapFolder getNewFolder() {
        return message.getFolder();
    }

    @SuppressWarnings("unused")
    public ImapFolder getOldFolder() {
        return oldFolder;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).
                append("oldFolderName", oldFolderName).
                append("message", message).
                toString();
    }
}
