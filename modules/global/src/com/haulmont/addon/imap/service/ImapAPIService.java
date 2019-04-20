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

package com.haulmont.addon.imap.service;

import com.haulmont.addon.imap.api.ImapFlag;
import com.haulmont.addon.imap.dto.ImapConnectResult;
import com.haulmont.addon.imap.dto.ImapFolderDto;
import com.haulmont.addon.imap.dto.ImapMessageDto;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.entity.ImapMessage;
import com.haulmont.addon.imap.entity.ImapMessageAttachment;

import java.util.Collection;

@SuppressWarnings("unused")
public interface ImapAPIService {
    String NAME = "imap_ImapAPIService";

    ImapConnectResult testConnection(ImapMailBox box);
    Collection<ImapFolderDto> fetchFolders(ImapMailBox box);

    ImapMessageDto fetchMessage(ImapMessage message);

    void moveMessage(ImapMessage msg, String folderName);
    void deleteMessage(ImapMessage message);
    void setFlag(ImapMessage message, ImapFlag flag, boolean set);

    Collection<ImapMessageAttachment> fetchAttachments(ImapMessage message);
    byte[] loadFile(ImapMessageAttachment attachment);
}