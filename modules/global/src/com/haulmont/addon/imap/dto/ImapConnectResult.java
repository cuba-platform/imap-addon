package com.haulmont.addon.imap.dto;

import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.exception.ImapException;

import java.io.Serializable;
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
public class ImapConnectResult implements Serializable {
    private static final long serialVersionUID = -2217624132287086972L;

    private ImapMailBox mailBox;

    private boolean success;
    private ImapException failure;

    private boolean customFlagSupported;

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
