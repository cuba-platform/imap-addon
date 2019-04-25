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

package com.haulmont.addon.imap.entity;

import javax.persistence.Entity;
import javax.persistence.Table;
import com.haulmont.cuba.core.entity.StandardEntity;
import javax.persistence.Column;
import com.haulmont.chile.core.annotations.NamePattern;

@NamePattern("%s:%s|host,port")
@Table(name = "IMAP_PROXY")
@Entity(name = "imap$Proxy")
public class ImapProxy extends StandardEntity {
    private static final long serialVersionUID = -5839274142110060210L;

    @Column(name = "HOST")
    private String host;

    @Column(name = "PORT")
    private Integer port;

    @Column(name = "WEB_PROXY")
    private Boolean webProxy;

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }


    public void setHost(String host) {
        this.host = host;
    }

    public String getHost() {
        return host;
    }

    public void setWebProxy(Boolean webProxy) {
        this.webProxy = webProxy;
    }

    public Boolean getWebProxy() {
        return webProxy;
    }



}