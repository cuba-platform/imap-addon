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

package com.haulmont.addon.imap.dto;

import com.haulmont.chile.core.annotations.MetaClass;
import com.haulmont.chile.core.annotations.MetaProperty;
import com.haulmont.chile.core.annotations.NamePattern;
import com.haulmont.cuba.core.entity.BaseUuidEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * encapsulates IMAP folder details:
 * <ul>
 *     <li>
 *     name
 *     </li>
 *     <li>
 *     full name
 *     </li>
 *     <li>
 *     tree structure place using children and parent
 *     </li>
 *     <li>
 *     flag to determine whether this folder can contain messages
 *     </li>
 * </ul>
 */
@NamePattern("%s |fullName")
@MetaClass(name = "imap$FolderDto")
public class ImapFolderDto extends BaseUuidEntity {

    @MetaProperty(mandatory = true)
    private String name;

    @MetaProperty(mandatory = true)
    private String fullName;

    @MetaProperty(mandatory = true)
    private Boolean canHoldMessages;

    @MetaProperty
    private List<ImapFolderDto> children;

    @MetaProperty
    private ImapFolderDto parent;

    public List<ImapFolderDto> getChildren() {
        return children;
    }

    public void setChildren(List<ImapFolderDto> children) {
        this.children = children;
    }

    public ImapFolderDto getParent() {
        return parent;
    }

    public void setParent(ImapFolderDto parent) {
        this.parent = parent;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Boolean getCanHoldMessages() {
        return canHoldMessages;
    }

    public void setCanHoldMessages(Boolean canHoldMessages) {
        this.canHoldMessages = canHoldMessages;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).
                append("name", name).
                append("fullName", fullName).
                append("canHoldMessages", canHoldMessages).
                append("children", children).
                toString();
    }

    public static List<ImapFolderDto> flattenList(Collection<ImapFolderDto> folderDtos) {
        List<ImapFolderDto> result = new ArrayList<>(folderDtos.size());

        for (ImapFolderDto folderDto : folderDtos) {
            addFolderWithChildren(result, folderDto);
        }

        return result;
    }

    private static void addFolderWithChildren(List<ImapFolderDto> foldersList, ImapFolderDto folder) {
        foldersList.add(folder);
        List<ImapFolderDto> children = folder.getChildren();
        if (children != null) {
            for (ImapFolderDto childFolder : children) {
                addFolderWithChildren(foldersList, childFolder);
            }
        }
    }
}
