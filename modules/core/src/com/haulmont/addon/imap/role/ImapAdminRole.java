/*
 * Copyright (c) 2008-2020 Haulmont.
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

package com.haulmont.addon.imap.role;

import com.haulmont.addon.imap.entity.*;
import com.haulmont.cuba.security.app.role.AnnotatedRoleDefinition;
import com.haulmont.cuba.security.app.role.annotation.EntityAccess;
import com.haulmont.cuba.security.app.role.annotation.EntityAttributeAccess;
import com.haulmont.cuba.security.app.role.annotation.Role;
import com.haulmont.cuba.security.app.role.annotation.ScreenAccess;
import com.haulmont.cuba.security.entity.EntityOp;
import com.haulmont.cuba.security.role.EntityAttributePermissionsContainer;
import com.haulmont.cuba.security.role.EntityPermissionsContainer;
import com.haulmont.cuba.security.role.ScreenPermissionsContainer;

@Role(name = "IMAP Admin role")
public class ImapAdminRole extends AnnotatedRoleDefinition {

    @EntityAccess(entityClass = ImapSimpleAuthentication.class,
            operations = {EntityOp.CREATE, EntityOp.READ, EntityOp.UPDATE, EntityOp.DELETE})
    @EntityAccess(entityClass = ImapProxy.class,
            operations = {EntityOp.CREATE, EntityOp.READ, EntityOp.UPDATE, EntityOp.DELETE})
    @EntityAccess(entityClass = ImapMessageSync.class,
            operations = {EntityOp.CREATE, EntityOp.READ, EntityOp.UPDATE, EntityOp.DELETE})
    @EntityAccess(entityClass = ImapMessageAttachment.class,
            operations = {EntityOp.CREATE, EntityOp.READ, EntityOp.UPDATE, EntityOp.DELETE})
    @EntityAccess(entityClass = ImapMessage.class,
            operations = {EntityOp.CREATE, EntityOp.READ, EntityOp.UPDATE, EntityOp.DELETE})
    @EntityAccess(entityClass = ImapFolderEvent.class,
            operations = {EntityOp.CREATE, EntityOp.READ, EntityOp.UPDATE, EntityOp.DELETE})
    @EntityAccess(entityClass = ImapFolder.class,
            operations = {EntityOp.CREATE, EntityOp.READ, EntityOp.UPDATE, EntityOp.DELETE})
    @EntityAccess(entityClass = ImapEventHandler.class,
            operations = {EntityOp.CREATE, EntityOp.READ, EntityOp.UPDATE, EntityOp.DELETE})
    @EntityAccess(entityClass = ImapMailBox.class,
            operations = {EntityOp.CREATE, EntityOp.READ, EntityOp.UPDATE, EntityOp.DELETE})
    @Override
    public EntityPermissionsContainer entityPermissions() {
        return super.entityPermissions();
    }

    @EntityAttributeAccess(entityClass = ImapSimpleAuthentication.class, modify = "*")
    @EntityAttributeAccess(entityClass = ImapProxy.class, modify = "*")
    @EntityAttributeAccess(entityClass = ImapMessageSync.class, modify = "*")
    @EntityAttributeAccess(entityClass = ImapMessageAttachment.class, modify = "*")
    @EntityAttributeAccess(entityClass = ImapMessage.class, modify = "*")
    @EntityAttributeAccess(entityClass = ImapFolderEvent.class, modify = "*")
    @EntityAttributeAccess(entityClass = ImapFolder.class, modify = "*")
    @EntityAttributeAccess(entityClass = ImapEventHandler.class, modify = "*")
    @EntityAttributeAccess(entityClass = ImapMailBox.class, modify = "*")
    @Override
    public EntityAttributePermissionsContainer entityAttributePermissions() {
        return super.entityAttributePermissions();
    }

    @ScreenAccess(screenIds = {"administration",
            "imap-component",
            "imap$Folder.lookup",
            "imap$MailBox.edit",
            "imap$MailBox.browse",
            "imap$Message.browse",
            "imap$Message.edit",
            "imap$FolderEvent.edit"})
    @Override
    public ScreenPermissionsContainer screenPermissions() {
        return super.screenPermissions();
    }
}
