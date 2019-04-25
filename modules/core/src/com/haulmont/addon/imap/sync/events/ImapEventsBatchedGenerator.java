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

package com.haulmont.addon.imap.sync.events;

import com.haulmont.addon.imap.api.ImapEventsGenerator;
import com.haulmont.addon.imap.entity.ImapFolder;
import com.haulmont.addon.imap.events.BaseImapEvent;

import java.util.Collection;

public abstract class ImapEventsBatchedGenerator implements ImapEventsGenerator {

    protected int batchSize;

    @Override
    public final Collection<? extends BaseImapEvent> generateForNewMessages(ImapFolder cubaFolder) {
        return generateForNewMessages(cubaFolder, batchSize);
    }

    @Override
    public final Collection<? extends BaseImapEvent> generateForChangedMessages(ImapFolder cubaFolder) {
        return generateForChangedMessages(cubaFolder, batchSize);
    }

    @Override
    public final Collection<? extends BaseImapEvent> generateForMissedMessages(ImapFolder cubaFolder) {
        return generateForMissedMessages(cubaFolder, batchSize);
    }

    protected abstract Collection<? extends BaseImapEvent> generateForNewMessages(ImapFolder cubaFolder, int batchSize);
    protected abstract Collection<? extends BaseImapEvent> generateForChangedMessages(ImapFolder cubaFolder, int batchSize);
    protected abstract Collection<? extends BaseImapEvent> generateForMissedMessages(ImapFolder cubaFolder, int batchSize);
}
