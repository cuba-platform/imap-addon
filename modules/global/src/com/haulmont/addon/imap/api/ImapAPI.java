package com.haulmont.addon.imap.api;

import com.haulmont.addon.imap.dto.ImapFolderDto;
import com.haulmont.addon.imap.dto.ImapMessageDto;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.entity.ImapMessage;

import java.util.Collection;
import java.util.List;

/**
 * Gateway for E-mail servers communication via IMAP protocol
 * <br>
 * Provides operations for
 * <ul>
 *     <li>
 *     IMAP <strong>mailbox</strong> (in terms of IMAP it is <i>server</i>)
 *     <strong>folders</strong> (in terms of IMAP it is <i>mailbox</i>) retrieval
 *     </li>
 *     <li>
 *     Messages retrieval
 *     </li>
 *     <li>
 *     Messages modification
 *     </li>
 * </ul>
 * <br>
 * Connection details for mailbox are specified in {@link ImapMailBox} object.
 * <br>
 * Folder is uniquely defined by its mailbox and full name (considering tree structure of folders),
 * it is represented by {@link com.haulmont.addon.imap.entity.ImapFolder} object
 * <br>
 * Message is uniquely defined by its folder and UID, it is represented by {@link ImapMessage} object,
 * UID is specified in {@link ImapMessage#getMsgUid()} property
 *
 */
public interface ImapAPI {
    String NAME = "imap_ImapAPI";

    /**
     * Retrieve all folders of mailbox preserving tree structure
     *
     * @param mailBox IMAP mailbox connection details
     * @return        root folders of IMAP mailbox, each folder can contain nested child folders forming tree structure
     */
    Collection<ImapFolderDto> fetchFolders(ImapMailBox mailBox);
    /**
     * Retrieve folders of mailbox with specified full names, result is presented in flat structure
     * hiding parent\child relationship
     *
     * @param mailBox       IMAP mailbox connection details
     * @param folderNames   full names of folders to retrieve
     * @return              folders of IMAP mailbox with specified full names,
     *                      result is ordered according to order of names input
     */
    List<ImapFolderDto> fetchFolders(ImapMailBox mailBox, String... folderNames);

    /**
     * Retrieve single message
     *
     * @param message reference object for IMAP message
     * @return        fully fetched message or null if there is no message with such UID in corresponding folder
     * @throws com.haulmont.addon.imap.exception.ImapException if wrong folder or mailbox connection details are specified in parameter
     */
    ImapMessageDto fetchMessage(ImapMessage message);


    /**
     * Move message in different folder, if folder is the same - nothing changed,
     * if folder with specified full name doesn't exist - results in throwing {@link com.haulmont.addon.imap.exception.ImapException}
     *
     * @param message       reference object for IMAP message
     * @param folderName    full name of new folder
     * @throws com.haulmont.addon.imap.exception.ImapException for wrong folder
     */
    void moveMessage(ImapMessage message, String folderName);
    /**
     * Delete message
     *
     * @param message       reference object for IMAP message
     */
    void deleteMessage(ImapMessage message);
    /**
     * Change meta data flag for message, flag can be either standard or custom one
     *
     * @param message       reference object for IMAP message
     * @param flag          flag to change
     * @param set           if true - set the flag, if false - clear the flag
     */
    void setFlag(ImapMessage message, ImapFlag flag, boolean set);
}
