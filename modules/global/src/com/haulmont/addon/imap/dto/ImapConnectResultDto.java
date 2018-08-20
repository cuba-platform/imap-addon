package com.haulmont.addon.imap.dto;

import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.exception.ImapException;
import com.haulmont.chile.core.annotations.MetaClass;
import com.haulmont.chile.core.annotations.MetaProperty;
import com.haulmont.chile.core.annotations.NamePattern;
import com.haulmont.cuba.core.entity.BaseUuidEntity;

import java.util.List;

/**
 * describe result for test connection operation holding:
 * <ul>
 *     <li>
 *     mailBox reference
 *     </li>
 *     <li>
 *     result of operation
 *     </li>
 *     <li>
 *     supported features
 *     </li>
 *     <li>
 *     all folders preserving tree structure if test succeeded
 *     </li>
 * </ul>
 */
@NamePattern("%s | mailBox")
@MetaClass(name = "imap$ConnectResultDto")
public class ImapConnectResultDto extends BaseUuidEntity {

    @MetaProperty(mandatory = true)
    private ImapMailBox mailBox;

    @MetaProperty(mandatory = true)
    private boolean success;
    @MetaProperty
    private ImapException failure;

    @MetaProperty(mandatory = true)
    private boolean customFlagSupported;

    @MetaProperty
    private List<ImapFolderDto> allFolders;

    public ImapMailBox getMailBox() {
        return mailBox;
    }

    public void setMailBox(ImapMailBox mailBox) {
        this.mailBox = mailBox;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public ImapException getFailure() {
        return failure;
    }

    public void setFailure(ImapException failure) {
        this.failure = failure;
    }

    public boolean isCustomFlagSupported() {
        return customFlagSupported;
    }

    public void setCustomFlagSupported(boolean customFlagSupported) {
        this.customFlagSupported = customFlagSupported;
    }

    public List<ImapFolderDto> getAllFolders() {
        return allFolders;
    }

    public void setAllFolders(List<ImapFolderDto> allFolders) {
        this.allFolders = allFolders;
    }
}
