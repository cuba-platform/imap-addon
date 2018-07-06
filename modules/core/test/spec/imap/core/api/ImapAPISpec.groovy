package spec.imap.core.api

import com.haulmont.addon.imap.ImapTestContainer
import com.haulmont.addon.imap.api.ImapAPI
import com.haulmont.addon.imap.api.ImapFlag
import com.haulmont.addon.imap.crypto.Encryptor
import com.haulmont.addon.imap.dto.ImapMessageDto
import com.haulmont.addon.imap.entity.ImapAuthenticationMethod
import com.haulmont.addon.imap.entity.ImapFolder
import com.haulmont.addon.imap.entity.ImapMailBox
import com.haulmont.addon.imap.entity.ImapMessage
import com.haulmont.addon.imap.entity.ImapSimpleAuthentication
import com.haulmont.addon.imap.exception.ImapException
import com.haulmont.cuba.core.global.AppBeans
import com.icegreen.greenmail.imap.ImapConstants
import com.icegreen.greenmail.imap.ImapHostManager
import com.icegreen.greenmail.store.StoredMessage
import com.icegreen.greenmail.user.GreenMailUser
import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetup
import com.sun.mail.imap.IMAPFolder
import org.apache.commons.lang.RandomStringUtils
import org.junit.ClassRule
import spock.lang.Shared
import spock.lang.Specification

import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Session
import javax.mail.Store
import javax.mail.URLName
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import java.util.concurrent.atomic.AtomicInteger


class ImapAPISpec extends Specification {

    @SuppressWarnings("SpellCheckingInspection")
    private static final String USER_PASSWORD = "abcdef123"
    @SuppressWarnings("SpellCheckingInspection")
    private static final String USER_NAME = "hascode"
    private static final String EMAIL_USER_ADDRESS = "hascode@localhost"
    private static final String LOCALHOST = "127.0.0.1"
    private static final String D = ImapConstants.HIERARCHY_DELIMITER

    private static final String EMAIL_TO = "someone@localhost.com"
    private static final String EMAIL_CC = "someone-else@localhost.com"
    private static final String EMAIL_BCC = "hidden-witness@localhost.com"
    private static final String EMAIL_SUBJECT = "Test E-Mail"
    private static final String EMAIL_TEXT = "This is a test e-mail."
    private static final long STARTING_EMAIL_UID = 1L

    private static final AtomicInteger counter = new AtomicInteger(0)

    @Shared @ClassRule
    public ImapTestContainer cont = ImapTestContainer.Common.INSTANCE

    private ImapAPI imapAPI

    private GreenMail mailServer
    private ImapHostManager imapManager
    private GreenMailUser user
    private ImapMailBox mailBoxConfig

    void setup() {
        imapAPI = AppBeans.get(ImapAPI)

        mailServer = new GreenMail(new ServerSetup(3143 + counter.incrementAndGet(), null, ServerSetup.PROTOCOL_IMAP))
        mailServer.start()
        user = mailServer.setUser(EMAIL_USER_ADDRESS, USER_NAME, USER_PASSWORD)
        imapManager = mailServer.managers.imapHostManager
        imapManager.createMailbox(user, "root0")
        imapManager.createMailbox(user, "root0${D}child0")
        imapManager.createMailbox(user, "root1${D}child0")
        imapManager.createMailbox(user, "root1${D}child1")
        imapManager.createMailbox(user, "root1${D}child2")
        imapManager.createMailbox(user, "root1${D}child2${D}grandch0")
        imapManager.createMailbox(user, "root1${D}child2${D}grandch1")
        imapManager.createMailbox(user, "root2")

        mailBoxConfig = mailbox(mailServer, user)
    }

    void cleanup() {
        mailServer.stop()
    }

    def "fetch all folders for mailbox"() {
        when:
        def allFoldersTree = imapAPI.fetchFolders(mailBoxConfig)

        then:
        allFoldersTree.size() == 4 // INBOX + 3 root folders created
        // INBOX
        allFoldersTree[0].fullName == imapManager.getInbox(user).name && allFoldersTree[0].canHoldMessages
        // first root folder and its child folder
        allFoldersTree[1].fullName == "root0" && allFoldersTree[1].canHoldMessages && allFoldersTree[1].parent == null
        allFoldersTree[1].children.size() == 1
        allFoldersTree[1].children[0].fullName == "root0${D}child0"
        allFoldersTree[1].children[0].canHoldMessages
        allFoldersTree[1].children[0].parent == allFoldersTree[1]
        allFoldersTree[1].children[0].children.isEmpty()

        // second root folder and its children
        allFoldersTree[2].fullName == "root1" && !allFoldersTree[2].canHoldMessages && allFoldersTree[2].parent == null
        allFoldersTree[2].children.size() == 3
        // first child folder
        allFoldersTree[2].children[0].fullName == "root1${D}child0"
        allFoldersTree[2].children[0].canHoldMessages
        allFoldersTree[2].children[0].parent == allFoldersTree[2]
        allFoldersTree[2].children[0].children.isEmpty()
        // second child folder
        allFoldersTree[2].children[1].fullName == "root1${D}child1"
        allFoldersTree[2].children[1].canHoldMessages
        allFoldersTree[2].children[1].parent == allFoldersTree[2]
        allFoldersTree[2].children[1].children.isEmpty()
        // third child folder and its children
        allFoldersTree[2].children[2].fullName == "root1${D}child2"
        allFoldersTree[2].children[2].canHoldMessages
        allFoldersTree[2].children[2].parent == allFoldersTree[2]
        allFoldersTree[2].children[2].children.size() == 2
        allFoldersTree[2].children[2].children[0].fullName == "root1${D}child2${D}grandch0"
        allFoldersTree[2].children[2].children[0].canHoldMessages
        allFoldersTree[2].children[2].children[0].parent == allFoldersTree[2].children[2]
        allFoldersTree[2].children[2].children[1].fullName == "root1${D}child2${D}grandch1"
        allFoldersTree[2].children[2].children[1].canHoldMessages
        allFoldersTree[2].children[2].children[1].parent == allFoldersTree[2].children[2]
        // third root folder without children
        allFoldersTree[3].fullName == "root2"
        allFoldersTree[3].canHoldMessages
        allFoldersTree[3].children.isEmpty()
        allFoldersTree[3].parent == null
    }
    def "fetch folders for mailbox by names"() {
        when:
        def someFolders = imapAPI.fetchFolders(mailBoxConfig,
                "root1${D}child1",
                "root1${D}child2${D}grandch1",
                "root2",
                "root2${D}child0",
                "root1",
                "root0${D}child0"
        )

        then:
        someFolders.size() == 5
        someFolders.every{ it.children.isEmpty() }
        someFolders.every{ it.parent == null }
        someFolders.collect{ it.fullName } == ["root1${D}child1", "root1${D}child2${D}grandch1", "root2", "root1", "root0${D}child0"]
    }


    def "fetch single message"() {
        given: "existing message in INBOX"
        deliverDefaultMessage(EMAIL_SUBJECT, STARTING_EMAIL_UID)
        ImapMessage imapMessage = defaultMessage(STARTING_EMAIL_UID)

        when: "fetch the message using ref"
        ImapMessageDto messageDto = imapAPI.fetchMessage(imapMessage)

        then: "all attributes are set properly"
        messageDto != null
        messageDto.uid == STARTING_EMAIL_UID
        messageDto.from == EMAIL_TO
        messageDto.to == "[${EMAIL_USER_ADDRESS}]"
        messageDto.cc == "[${EMAIL_CC}]"
        messageDto.bcc == "[${EMAIL_BCC}]"
        messageDto.subject == EMAIL_SUBJECT
        messageDto.body == EMAIL_TEXT
        messageDto.flags == "[FLAGGED]"

        when: "try to fetch message with wrong UID"
        imapMessage.msgUid += 1

        then: "nothing is found"
        imapAPI.fetchMessage(imapMessage) == null

        when: "try to fetch message with nonexistent folder"
        imapMessage.folder.name = "INBOX3"
        imapAPI.fetchMessage(imapMessage)

        then: "exception is raised"
        thrown RuntimeException

        when: "try to fetch message pointing to mailbox with wrong connection details"
        imapMessage.folder.name = "INBOX"
        mailBoxConfig.authentication.password = USER_PASSWORD + "1"

        cont.persistence().runInTransaction() { em ->
            em.createQuery("update imap\$SimpleAuthentication auth set auth.password = :password where auth.id = " +
                    "( select mb.authentication.id from imap\$MailBox mb where mb.id = :mailBox)")
                    .setParameter("password", AppBeans.get(Encryptor).getEncryptedPassword(mailBoxConfig))
                    .setParameter("mailBox", mailBoxConfig)
                    .executeUpdate()
            em.flush()
        }
        imapAPI.fetchMessage(imapMessage)

        then: "exception is raised"
        thrown ImapException
    }

    def "work with flags for single message"() {
        given: "message with standard flag 'FLAGGED' "
        deliverDefaultMessage(EMAIL_SUBJECT, STARTING_EMAIL_UID)
        ImapMessage imapMessage = defaultMessage(STARTING_EMAIL_UID)

        when: "fetch the message"
        ImapMessageDto messageDto = imapAPI.fetchMessage(imapMessage)

        then: "flags of result contain only 'FLAGGED'"
        messageDto.flags == "[FLAGGED]"

        when: "add standard 'SEEN' flag and fetch again"
        imapAPI.setFlag(imapMessage, ImapFlag.SEEN, true)
        messageDto = imapAPI.fetchMessage(imapMessage)

        then: "flags of result are updated"
        messageDto.flags.contains("FLAGGED")
        messageDto.flags.contains("SEEN")

        when: "remove standard 'FLAGGED' flag and fetch again"
        imapAPI.setFlag(imapMessage, ImapFlag.IMPORTANT, false)
        messageDto = imapAPI.fetchMessage(imapMessage)

        then: "only 'SEEN' flag remains"
        messageDto.flags == "[SEEN]"

        when: "add custom flag and fetch again"
        imapAPI.setFlag(imapMessage, new ImapFlag("custom-flag"), true)
        messageDto = imapAPI.fetchMessage(imapMessage)

        then: "flags of result are updated"
        messageDto.flags.contains("SEEN")
        messageDto.flags.contains("custom-flag")

        when: "try to remove nonexistent custom flag"
        imapAPI.setFlag(imapMessage, new ImapFlag("custom-flag1"), false)
        messageDto = imapAPI.fetchMessage(imapMessage)

        then: "nothing is changed"
        messageDto.flags.contains("SEEN")
        messageDto.flags.contains("custom-flag")

        when: "remove custom flag previously set and fetch again"
        imapAPI.setFlag(imapMessage, new ImapFlag("custom-flag"), false)
        messageDto = imapAPI.fetchMessage(imapMessage)

        then: "only 'SEEN' flag remains"
        messageDto.flags == "[SEEN]"
    }
    def "move message to other folder"() {
        given: "single message in INBOX"
        deliverDefaultMessage(EMAIL_SUBJECT + "_moved_to_other", STARTING_EMAIL_UID)
        ImapMessage imapMessage = defaultMessage(STARTING_EMAIL_UID)

        when: "check all messages of INBOX"
        Message[] inboxMessages = getDefaultMessages("INBOX")

        then: "find our original message"
        inboxMessages != null
        inboxMessages.size() == 1
        inboxMessages[0].subject == EMAIL_SUBJECT + "_moved_to_other"
        imapAPI.fetchMessage(imapMessage) != null

        when: "move message to the same folder"
        imapAPI.moveMessage(imapMessage, "INBOX")

        then: "nothing is changed"
        imapAPI.fetchMessage(imapMessage) != null

        when: "move message to other folder"
        imapAPI.moveMessage(imapMessage, "root2")

        then: "INBOX doesn't contain it anymore"
        imapAPI.fetchMessage(imapMessage) == null

        when: "check all messages of target folder"
        IMAPFolder folder = getDefaultImapFolder("root2")
        Message[] messages = folder.messages

        then: "find our original message there"
        messages != null
        messages.size() == 1
        messages[0].subject == EMAIL_SUBJECT + "_moved_to_other"
        folder.getUID(messages[0]) == STARTING_EMAIL_UID

        when: "try to move message to nonexistent folder"
        imapAPI.moveMessage(imapMessage, "root999")

        then: "exception is raised"
        thrown RuntimeException
    }
    def "delete message"() {
        given: "2 messages in INBOX"
        deliverDefaultMessage(EMAIL_SUBJECT, STARTING_EMAIL_UID)
        deliverDefaultMessage(EMAIL_SUBJECT + "_moved", STARTING_EMAIL_UID + 1)
        ImapMessage imapMessage1 = defaultMessage(STARTING_EMAIL_UID)
        ImapMessage imapMessage2 = defaultMessage(STARTING_EMAIL_UID + 1)

        when: "delete first message having no trash folder configured"
        imapAPI.deleteMessage(imapMessage1)

        then: "message is gone from INBOX"
        imapAPI.fetchMessage(imapMessage1) == null

        when: "delete second message having trash folder configured"
        mailBoxConfig.trashFolderName = "root0"
        imapAPI.deleteMessage(imapMessage2)

        then: "message is gone from INBOX"
        imapAPI.fetchMessage(imapMessage2) == null

        when: "get all messages from trash folder"
        IMAPFolder folder = getDefaultImapFolder("root0")
        Message[] messages = folder.messages

        then: "find second message there"
        messages != null
        messages.size() == 1
        messages[0].getSubject() == EMAIL_SUBJECT + "_moved"
        folder.getUID(messages[0]) == STARTING_EMAIL_UID
    }

    @SuppressWarnings("GroovyAssignabilityCheck")
    void deliverDefaultMessage(subject, uid, actor = user) {
        MimeMessage message = new MimeMessage((Session) null)
        message.from = new InternetAddress(EMAIL_TO)
        message.addRecipient(Message.RecipientType.TO, new InternetAddress(EMAIL_USER_ADDRESS))
        message.addRecipient(Message.RecipientType.CC, new InternetAddress(EMAIL_CC))
        message.addRecipient(Message.RecipientType.BCC, new InternetAddress(EMAIL_BCC))
        message.subject = subject
        message.text = EMAIL_TEXT
        message.setFlag(Flags.Flag.FLAGGED, true)
        actor.deliver(new StoredMessage.UidAwareMimeMessage(message, uid))
    }

    @SuppressWarnings("GroovyAccessibility")
    ImapMessage defaultMessage(uid, mailbox = mailBoxConfig) {
        ImapMessage imapMessage = cont.metadata().create(ImapMessage)
        imapMessage.msgUid = uid
        ImapFolder imapFolder = cont.metadata().create(ImapFolder)
        imapFolder.name = "INBOX"
        imapFolder.mailBox = mailbox
        imapMessage.folder = imapFolder

        return imapMessage
    }

    Message[] getDefaultMessages(folderName) {
        Folder folder = getDefaultImapFolder(folderName)
        return folder.getMessages()
    }

    @SuppressWarnings("GroovyAssignabilityCheck")
    IMAPFolder getDefaultImapFolder(folderName) {
        Properties props = new Properties()
        Session session = Session.getInstance(props)
        URLName urlName = new URLName("imap", LOCALHOST,
                mailBoxConfig.port, null, user.getLogin(),
                user.getPassword())
        Store store = session.getStore(urlName)
        store.connect()
        IMAPFolder folder = store.getFolder(folderName)
        folder.open(Folder.READ_ONLY)
        return folder
    }

    @SuppressWarnings("GroovyAccessibility")
    ImapMailBox mailbox(mailServer, user) {
        ImapMailBox mailBox = cont.metadata().create(ImapMailBox)
        mailBox.host = LOCALHOST
        mailBox.port = mailServer.getImap().port
        mailBox.setAuthenticationMethod(ImapAuthenticationMethod.SIMPLE)
        mailBox.authentication = new ImapSimpleAuthentication()
        mailBox.authentication.password = user.password
        mailBox.authentication.username = user.login
        mailBox.name = "$LOCALHOST:${mailBox.port}:${RandomStringUtils.random(3)}"

        cont.persistence().runInTransaction() { em ->
            em.persist(mailBox.authentication)
            em.persist(mailBox)
            em.flush()
        }

        return mailBox
    }
}