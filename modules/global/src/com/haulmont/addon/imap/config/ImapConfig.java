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

package com.haulmont.addon.imap.config;

import com.haulmont.cuba.core.config.Config;
import com.haulmont.cuba.core.config.Property;
import com.haulmont.cuba.core.config.Source;
import com.haulmont.cuba.core.config.SourceType;
import com.haulmont.cuba.core.config.defaults.DefaultBoolean;
import com.haulmont.cuba.core.config.defaults.DefaultInt;

@SuppressWarnings("unused")
@Source(type = SourceType.DATABASE)
public interface ImapConfig extends Config {

    @Property("imap.server.trustAllCertificates")
    @DefaultBoolean(false)
    boolean getTrustAllCertificates();
    void setTrustAllCertificates(boolean value);

    @Property("imap.updateBatchSize")
    @DefaultInt(100)
    int getUpdateBatchSize();
    void setUpdateBatchSize(int value);

    @Property("imap.clearCustomFlags")
    @DefaultBoolean(false)
    boolean getClearCustomFlags();
    void setClearCustomFlags(boolean value);

    @Property("imap.debug")
    @DefaultBoolean(false)
    boolean getDebug();
    void setDebug(boolean value);

    @Property("imap.timeoutSeconds")
    @DefaultInt(5)
    int getTimeoutSeconds();
    void setTimeoutSeconds(int value);

    @Property("imap.syncTimeout")
    @DefaultInt(5)
    int getSyncTimeout();
    void setSyncTimeout(int value);

    @Property("imap.eventsBatchSize")
    @DefaultInt(20)
    int getEventsBatchSize();
    void setEventsBatchSize(int value);

}
