package spec.imap.core.events

import com.haulmont.addon.imap.ImapTestContainer
import com.haulmont.addon.imap.api.ImapAPI
import com.haulmont.addon.imap.api.ImapFlag
import com.haulmont.addon.imap.core.ImapEventsTestListener
import com.haulmont.addon.imap.core.ImapOperations
import com.haulmont.addon.imap.dao.ImapDao
import com.haulmont.addon.imap.entity.ImapAuthenticationMethod
import com.haulmont.addon.imap.entity.ImapEventType
import com.haulmont.addon.imap.entity.ImapFolder
import com.haulmont.addon.imap.entity.ImapFolderEvent
import com.haulmont.addon.imap.entity.ImapMailBox
import com.haulmont.addon.imap.entity.ImapMessage
import com.haulmont.addon.imap.entity.ImapSimpleAuthentication
import com.haulmont.addon.imap.events.BaseImapEvent
import com.haulmont.addon.imap.events.EmailAnsweredImapEvent
import com.haulmont.addon.imap.events.EmailDeletedImapEvent
import com.haulmont.addon.imap.events.EmailFlagChangedImapEvent
import com.haulmont.addon.imap.events.EmailMovedImapEvent
import com.haulmont.addon.imap.events.EmailSeenImapEvent
import com.haulmont.addon.imap.events.NewEmailImapEvent
import com.haulmont.addon.imap.sync.ImapSyncManager
import com.haulmont.addon.imap.sync.events.ImapEvents
import com.haulmont.cuba.core.global.AppBeans
import com.icegreen.greenmail.imap.ImapHostManager
import com.icegreen.greenmail.store.StoredMessage
import com.icegreen.greenmail.user.GreenMailUser
import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetup
import com.sun.mail.imap.IMAPFolder
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

class ImapEventsSpec extends Specification {

    @SuppressWarnings("SpellCheckingInspection")
    private static final String USER_PASSWORD = "abcdef123"
    @SuppressWarnings("SpellCheckingInspection")
    private static final String USER_NAME = "hascode"
    private static final String EMAIL_USER_ADDRESS = "hascode@localhost"
    private static final String LOCALHOST = "127.0.0.1"

    private static final String EMAIL_TO = "someone@localhost.com"
    private static final String EMAIL_SUBJECT = "Test E-Mail"
    private static final String EMAIL_TEXT = "This is a test e-mail."
    private static final long START_EMAIL_UID = 1L
    private static final String CUBA_FLAG = "cuba-flag"

    private static final AtomicInteger counter = new AtomicInteger(0)

    @Shared @ClassRule
    public ImapTestContainer cont = ImapTestContainer.Common.INSTANCE

    private ImapEventsTestListener eventListener
    private ImapEvents imapEvents
    private ImapDao imapDao

    private GreenMail mailServer
    private GreenMailUser user
    private ImapMailBox mailBoxConfig
    private ImapFolder INBOX

    void setup() {
        ImapSyncManager.TRACK_FOLDER_ACTIVATION = false
        eventListener = AppBeans.get(ImapEventsTestListener)
        imapEvents = AppBeans.get(ImapEvents)
        imapDao = AppBeans.get(ImapDao)

        mailServer = new GreenMail(new ServerSetup(9143 + counter.incrementAndGet(), null, ServerSetup.PROTOCOL_IMAP))
        mailServer.start()
        user = mailServer.setUser(EMAIL_USER_ADDRESS, USER_NAME, USER_PASSWORD)

        mailBoxConfig = mailbox(mailServer, user)
    }

    void cleanup() {
        mailServer.stop()
    }

    def "new message events"() {
        given: "3 messages in INBOX, one of which contains our preconfigured custom flag"
        deliverDefaultMessage(EMAIL_SUBJECT + 0, START_EMAIL_UID)
        deliverDefaultMessage(EMAIL_SUBJECT + 1, START_EMAIL_UID + 1, new Flags(CUBA_FLAG))
        deliverDefaultMessage(EMAIL_SUBJECT + 2, START_EMAIL_UID + 2)
        and: "INBOX is configured to handle new message events"
        INBOX = inbox(mailBoxConfig, [ImapEventType.NEW_EMAIL])
        imapEvents.init(mailBoxConfig)

        Thread.sleep(100)

        when: "check for new messages"
        eventListener.events.clear()
        imapEvents.handleNewMessages(INBOX)

        then: "2 new messages are in database"
        ImapMessage newMessage1 = imapDao.findMessageByUid(INBOX.getUuid(), START_EMAIL_UID)
        newMessage1 != null
        newMessage1.getImapFlags().contains(CUBA_FLAG)
        imapDao.findMessageByUid(INBOX.getUuid(), START_EMAIL_UID + 1) == null
        ImapMessage newMessage2 = imapDao.findMessageByUid(INBOX.getUuid(), START_EMAIL_UID + 2)
        newMessage2 != null
        newMessage2.getImapFlags().contains(CUBA_FLAG)

        and: "2 events with type 'NEW_EMAIL' are fired"
        def imapEvents = eventListener.events
        imapEvents.size() == 2
        imapEvents.every {it instanceof NewEmailImapEvent}
        imapEvents.count {it.message.folder == INBOX && it.message.msgUid == START_EMAIL_UID} == 1
        imapEvents.count {it.message.folder == INBOX && it.message.msgUid == START_EMAIL_UID + 2} == 1
    }

    def "message has been read"() {
        given: "3 messages in INBOX, two of which are seen"
        deliverDefaultMessage(EMAIL_SUBJECT + 0, START_EMAIL_UID, new Flags(Flags.Flag.SEEN))
        deliverDefaultMessage(EMAIL_SUBJECT + 1, START_EMAIL_UID + 1, new Flags(Flags.Flag.SEEN))
        deliverDefaultMessage(EMAIL_SUBJECT + 2, START_EMAIL_UID + 2)
        and: "INBOX is configured to handle seen message events"
        INBOX = inbox(mailBoxConfig, [ImapEventType.EMAIL_SEEN])
        and: "2 messages in database, first of them is marked as seen"
        ImapMessage message1 = defaultMessage(START_EMAIL_UID, EMAIL_SUBJECT + 0, INBOX)
        message1.setImapFlags(new Flags(Flags.Flag.SEEN))
        ImapMessage message2 = defaultMessage(START_EMAIL_UID + 1, EMAIL_SUBJECT + 1, INBOX)
        message2.msgNum = 2
        message2.setImapFlags(new Flags(CUBA_FLAG))
        cont.persistence().runInTransaction() { em ->
            em.persist(message1)
            em.persist(message2)
            em.flush()
        }
        and: "sync was initialized"
        imapEvents.init(mailBoxConfig)

        Thread.sleep(100)

        when: "check for modified messages"
        eventListener.events.clear()
        imapEvents.handleChangedMessages(INBOX)

        then: "both messages have 'SEEN' flag in database"
        imapDao.findMessageByUid(INBOX.getUuid(), START_EMAIL_UID).getImapFlags().contains(Flags.Flag.SEEN)
        imapDao.findMessageByUid(INBOX.getUuid(), START_EMAIL_UID + 1).getImapFlags().contains(Flags.Flag.SEEN)

        and: "1 event with type 'EMAIL_SEEN' is fired"
        def imapEvents = eventListener.events
        imapEvents.size() == 1
        imapEvents.every {it instanceof EmailSeenImapEvent}
        imapEvents[0].message.folder == INBOX
        imapEvents[0].message.msgUid == START_EMAIL_UID + 1
    }

    def "message has been answered"() {
        given: "3 messages in INBOX, all of them are answered"
        deliverDefaultMessage(EMAIL_SUBJECT + 0, START_EMAIL_UID, new Flags(Flags.Flag.ANSWERED))
        deliverDefaultMessage(EMAIL_SUBJECT + 1, START_EMAIL_UID + 1, new Flags(Flags.Flag.ANSWERED))
        deliverDefaultMessage(EMAIL_SUBJECT + 2, START_EMAIL_UID + 2, new Flags(Flags.Flag.ANSWERED))
        and: "INBOX is configured to handle answer message events"
        INBOX = inbox(mailBoxConfig, [ImapEventType.NEW_ANSWER])
        and: "2 messages in database, first of them is answered as answered"
        ImapMessage message1 = defaultMessage(START_EMAIL_UID + 1, EMAIL_SUBJECT + 1, INBOX)
        message1.setImapFlags(new Flags(Flags.Flag.ANSWERED))
        message1.msgNum = 2
        ImapMessage message2 = defaultMessage(START_EMAIL_UID + 2, EMAIL_SUBJECT + 2, INBOX)
        message2.msgNum = 3
        message2.setImapFlags(new Flags(CUBA_FLAG))
        cont.persistence().runInTransaction() { em ->
            em.persist(message1)
            em.persist(message2)
            em.flush()
        }
        and: "sync was initialized"
        imapEvents.init(mailBoxConfig)

        Thread.sleep(100)

        when: "check for modified messages"
        eventListener.events.clear()
        imapEvents.handleChangedMessages(INBOX)

        then: "both message have 'ANSWERED' flag in database"
        imapDao.findMessageByUid(INBOX.getUuid(), START_EMAIL_UID + 1).getImapFlags().contains(Flags.Flag.ANSWERED)
        imapDao.findMessageByUid(INBOX.getUuid(), START_EMAIL_UID + 2).getImapFlags().contains(Flags.Flag.ANSWERED)

        and: "1 event with type 'NEW_ANSWER' is fired"
        def imapEvents = eventListener.events
        imapEvents.size() == 1
        imapEvents.every {it instanceof EmailAnsweredImapEvent}
        imapEvents[0].message.folder == INBOX
        imapEvents[0].message.msgUid == START_EMAIL_UID + 2
    }

    def "message has been deleted"() {
        given: "1 messages in INBOX"
        deliverDefaultMessage(EMAIL_SUBJECT + 0, START_EMAIL_UID)
        and: "INBOX is configured to handle deleted message events"
        INBOX = inbox(mailBoxConfig, [ImapEventType.EMAIL_DELETED])
        and: "3 messages in database"
        ImapMessage message1 = defaultMessage(START_EMAIL_UID, EMAIL_SUBJECT + 0, INBOX)
        message1.setImapFlags(new Flags(CUBA_FLAG))
        message1.msgNum = 1
        ImapMessage message2 = defaultMessage(START_EMAIL_UID + 1, EMAIL_SUBJECT + 1, INBOX)
        message2.msgNum = 2
        message2.setImapFlags(new Flags(CUBA_FLAG))
        ImapMessage message3 = defaultMessage(START_EMAIL_UID + 2, EMAIL_SUBJECT + 2, INBOX)
        message3.msgNum = 3
        message2.setImapFlags(new Flags(CUBA_FLAG))
        cont.persistence().runInTransaction() { em ->
            em.persist(message1)
            em.persist(message2)
            em.persist(message3)
            em.flush()
        }
        and: "sync was initialized"
        imapEvents.init(mailBoxConfig)

        Thread.sleep(100)

        when: "check for missed messages"
        eventListener.events.clear()
        imapEvents.handleMissedMessages(INBOX)

        then: "there is only 1 message remaining in database"
        imapDao.findMessageByUid(INBOX.getUuid(), START_EMAIL_UID) != null
        imapDao.findMessageByUid(INBOX.getUuid(), START_EMAIL_UID + 1) == null
        imapDao.findMessageByUid(INBOX.getUuid(), START_EMAIL_UID + 2) == null

        and: "2 events with type 'EMAIL_DELETED' are fired"
        def imapEvents = eventListener.events
        imapEvents.size() == 2
        imapEvents.every {it instanceof EmailDeletedImapEvent}
        imapEvents.count {it.message.folder == INBOX && it.message.msgUid == START_EMAIL_UID + 1} == 1
        imapEvents.count {it.message.folder == INBOX && it.message.msgUid == START_EMAIL_UID + 2} == 1
    }

    def "message flags are changed"() {
        given: "1st message in INBOX with SEEN and CUBA flag"
        Flags flags = new Flags(Flags.Flag.SEEN)
        flags.add(CUBA_FLAG)
        deliverDefaultMessage(EMAIL_SUBJECT + 0, START_EMAIL_UID, flags)
        and: "2nd message in INBOX with CUBA flag"
        deliverDefaultMessage(EMAIL_SUBJECT + 1, START_EMAIL_UID + 1, new Flags(CUBA_FLAG))
        and: "3rd message in INBOX with ANSWERED, SEEN, CUBA and some other 1 custom flags"
        flags = new Flags(Flags.Flag.SEEN)
        flags.add(CUBA_FLAG)
        flags.add(Flags.Flag.ANSWERED)
        String newCustomFlag = "i-am-new-here"
        flags.add(newCustomFlag)
        deliverDefaultMessage(EMAIL_SUBJECT + 2, START_EMAIL_UID + 2, flags)

        and: "INBOX is configured to handle update message events"
        INBOX = inbox(mailBoxConfig, [ImapEventType.FLAGS_UPDATED])

        and: "1st message in database has only CUBA flag"
        ImapMessage message1 = defaultMessage(START_EMAIL_UID, EMAIL_SUBJECT + 0, INBOX)
        message1.setImapFlags(new Flags(CUBA_FLAG))
        cont.persistence().runInTransaction() { em ->
            em.persist(message1)
            em.flush()
        }
        and: "2nd message in database has CUBA, FLAGGED and some other old custom flags"
        ImapMessage message2 = defaultMessage(START_EMAIL_UID + 1, EMAIL_SUBJECT + 1, INBOX)
        message2.msgNum = 2
        flags = new Flags(CUBA_FLAG)
        flags.add(Flags.Flag.FLAGGED)
        String oldCustomFlag = "i-am-old"
        flags.add(oldCustomFlag)
        message2.setImapFlags(flags)
        cont.persistence().runInTransaction() { em ->
            em.persist(message2)
            em.flush()
        }
        and: "3rd message in database has CUBA, SEEN and FLAGGED flags"
        ImapMessage message3 = defaultMessage(START_EMAIL_UID + 2, EMAIL_SUBJECT + 2, INBOX)
        message3.msgNum = 3
        flags = new Flags(CUBA_FLAG)
        flags.add(Flags.Flag.FLAGGED)
        flags.add(Flags.Flag.SEEN)
        message3.setImapFlags(flags)
        cont.persistence().runInTransaction() { em ->
            em.persist(message3)
            em.flush()
        }
        and: "sync was initialized"
        imapEvents.init(mailBoxConfig)

        Thread.sleep(100)

        when: "check for modified messages"
        eventListener.events.clear()
        imapEvents.handleChangedMessages(INBOX)
        Flags msg1Flags = imapDao.findMessageByUid(INBOX.getUuid(), START_EMAIL_UID).getImapFlags()
        Flags msg2Flags = imapDao.findMessageByUid(INBOX.getUuid(), START_EMAIL_UID + 1).getImapFlags()
        Flags msg3Flags = imapDao.findMessageByUid(INBOX.getUuid(), START_EMAIL_UID + 2).getImapFlags()
        Collection<BaseImapEvent> imapEvents = eventListener.events

        then: "1st message has CUBA and SEEN flags"
        msg1Flags.contains(Flags.Flag.SEEN)
        msg1Flags.contains(CUBA_FLAG)
        msg1Flags.systemFlags.length == 1
        msg1Flags.userFlags.length == 1

        and: "2nd message has only CUBA flag"
        msg2Flags.contains(CUBA_FLAG)
        msg2Flags.systemFlags.length == 0
        msg2Flags.userFlags.length == 1

        then: "3rd message has CUBA, SEEN, ANSWERED and new custom flags"
        msg3Flags.contains(Flags.Flag.SEEN)
        msg3Flags.contains(Flags.Flag.ANSWERED)
        msg3Flags.contains(CUBA_FLAG)
        msg3Flags.contains(newCustomFlag)
        msg3Flags.systemFlags.length == 2
        msg3Flags.userFlags.length == 2

        and: "3 event with type 'FLAGS_UPDATED' are fired for all messages"
        imapEvents.size() == 3
        imapEvents.every {it instanceof EmailFlagChangedImapEvent}
        imapEvents.count {it.message.folder == INBOX && it.message.msgUid == START_EMAIL_UID} == 1
        imapEvents.count {it.message.folder == INBOX && it.message.msgUid == START_EMAIL_UID + 1} == 1
        imapEvents.count {it.message.folder == INBOX && it.message.msgUid == START_EMAIL_UID + 2} == 1

        and: "event for 1st message contains only SEEN set"
        Map<ImapFlag, Boolean> msg1ChangedFlags = imapEvents.find {it.message.msgUid == START_EMAIL_UID}.changedFlagsWithNewValue
        msg1ChangedFlags.size() == 1
        msg1ChangedFlags.containsKey(ImapFlag.SEEN)
        msg1ChangedFlags.get(ImapFlag.SEEN)

        and: "event for 2nd message contains unset for FLAGGED and old custom flags"
        Map<ImapFlag, Boolean> msg2ChangedFlags = imapEvents.find {it.message.msgUid == START_EMAIL_UID + 1}.changedFlagsWithNewValue
        msg2ChangedFlags.size() == 2
        msg2ChangedFlags.containsKey(ImapFlag.IMPORTANT)
        !msg2ChangedFlags.get(ImapFlag.IMPORTANT)
        msg2ChangedFlags.containsKey(new ImapFlag(oldCustomFlag))
        !msg2ChangedFlags.get(new ImapFlag(oldCustomFlag))

        and: "event for 3rd message contains unset for FLAGGED flag and set for ANSWERED and new custom flags"
        Map<ImapFlag, Boolean> msg3ChangedFlags = imapEvents.find {it.message.msgUid == START_EMAIL_UID + 2}.changedFlagsWithNewValue
        msg3ChangedFlags.size() == 3
        msg3ChangedFlags.containsKey(ImapFlag.IMPORTANT)
        !msg3ChangedFlags.get(ImapFlag.IMPORTANT)
        msg3ChangedFlags.containsKey(new ImapFlag(newCustomFlag))
        msg3ChangedFlags.get(new ImapFlag(newCustomFlag))
        msg3ChangedFlags.containsKey(ImapFlag.ANSWERED)
        msg3ChangedFlags.get(ImapFlag.ANSWERED)
    }

    //todo: fix this test
    /*@SuppressWarnings("GroovyAccessibility")
    def "message has been moved"() {
        given: "other folder and trash folder exist for mailbox"
        ImapHostManager imapManager = mailServer.managers.imapHostManager
        imapManager.createMailbox(user, "other-folder")
        imapManager.createMailbox(user, "trash-folder")
        and: "mailbox has trash folder configured"
        mailBoxConfig.trashFolderName = "trash-folder"
        cont.persistence().runInTransaction() { em ->
            em.merge(mailBoxConfig)
            em.flush()
        }
        and: "INBOX is configured to handle moved and deleted message events"
        INBOX = inbox(mailBoxConfig, [ImapEventType.EMAIL_MOVED, ImapEventType.EMAIL_DELETED], true)
        and: "other folder is configured"
        def otherFolder = imapFolder(mailBoxConfig, "other-folder", true)
        and: "1 message in INBOX, 1 message in other folder and 1 message in trash-folder folder"
        deliverDefaultMessage(EMAIL_SUBJECT + 0, START_EMAIL_UID)
        deliverDefaultMessage(EMAIL_SUBJECT + 1, START_EMAIL_UID + 1, null, "moved-message")
        deliverDefaultMessage(EMAIL_SUBJECT + 2, START_EMAIL_UID + 2, null, "deleted-message")
        def imapMessages = getDefaultMessages("INBOX")

        ImapMessage message1 = defaultMessage(START_EMAIL_UID, EMAIL_SUBJECT + 0, INBOX)
        message1.msgNum = 1
        ImapMessage message2 = defaultMessage(START_EMAIL_UID + 1, EMAIL_SUBJECT + 1, INBOX)
        message2.msgNum = 2
        message2.messageId = imapMessages.find { it.messageNumber == 2 }.getHeader(ImapOperations.MESSAGE_ID_HEADER)[0]
        ImapMessage message3 = defaultMessage(START_EMAIL_UID + 2, EMAIL_SUBJECT + 2, INBOX)
        message3.msgNum = 3
        message3.messageId = imapMessages.find { it.messageNumber == 3 }.getHeader(ImapOperations.MESSAGE_ID_HEADER)[0]
        cont.persistence().runInTransaction() { em ->
            em.persist(message1)
            em.persist(message2)
            em.persist(message3)
            em.flush()
        }
        AppBeans.get(ImapAPI).moveMessage(message2, "other-folder")
        AppBeans.get(ImapAPI).moveMessage(message3, "trash-folder")
        cont.persistence().runInTransaction() { em ->
            INBOX.disabled = false
            em.merge(INBOX)
            otherFolder.disabled = false
            em.merge(otherFolder)
            em.flush()
        }
        cont.persistence().runInTransaction() { em -> INBOX = em.reload(INBOX, "imap-folder-full")}

        and: "sync was initialized"
        imapEvents.init(mailBoxConfig)

        Thread.sleep(1000)

        when: "check for missed messages"
        eventListener.events.clear()
        imapEvents.handleMissedMessages(INBOX)

        then: "there is only 1 message remaining in database"
        imapDao.findMessageByUid(INBOX.getUuid(), START_EMAIL_UID) != null
        imapDao.findMessageByUid(INBOX.getUuid(), START_EMAIL_UID + 1) == null
        imapDao.findMessageByUid(INBOX.getUuid(), START_EMAIL_UID + 2) == null

        and: "1 event with type 'EMAIL_DELETED' and 1 event with type 'EMAIL_MOVED' are fired"
        def imapEvents = eventListener.events
        imapEvents.size() == 2
        imapEvents.count {
            it instanceof EmailDeletedImapEvent &&
                    it.message.folder == INBOX &&
                    it.message.msgUid == START_EMAIL_UID + 2
        } == 1
        imapEvents.count {
            it instanceof EmailMovedImapEvent &&
                    it.message.folder == INBOX &&
                    it.message.msgUid == START_EMAIL_UID + 1 &&
                    it.newFolderName == "other-folder" &&
                    it.oldFolderName == "INBOX"
        } == 1
    }*/

    @SuppressWarnings("GroovyAssignabilityCheck")
    void deliverDefaultMessage(subject, uid, flags = null, messageId = null) {
        MimeMessage message = new MimeMessage((Session) null)
        message.from = new InternetAddress(EMAIL_TO)
        message.addRecipient(Message.RecipientType.TO, new InternetAddress(EMAIL_USER_ADDRESS))
        message.subject = subject
        message.text = EMAIL_TEXT
        if (flags != null) {
            message.setFlags(flags, true)
        }
        if (messageId != null) {
            message.setHeader(ImapOperations.MESSAGE_ID_HEADER, messageId)
        }

        user.deliver(new StoredMessage.UidAwareMimeMessage(message, uid))
    }

    @SuppressWarnings("GroovyAccessibility")
    ImapMessage defaultMessage(uid, subject, folder = INBOX) {
        ImapMessage imapMessage = cont.metadata().create(ImapMessage)
        imapMessage.msgUid = uid

        imapMessage.msgNum = 1
        imapMessage.caption = subject
        imapMessage.folder = folder

        return imapMessage
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
        mailBox.cubaFlag = CUBA_FLAG
        mailBox.name = "$LOCALHOST:${mailBox.port}"

        cont.persistence().runInTransaction() { em ->
            em.persist(mailBox.authentication)
            em.persist(mailBox)
            em.flush()
        }

        return mailBox
    }

    @SuppressWarnings(["GroovyAssignabilityCheck", "GroovyAccessibility"])
    ImapFolder inbox(ImapMailBox mailBox, eventTypes, disabled = false) {
        ImapFolder imapFolder = imapFolder(mailBox, "INBOX", disabled)

        def events = eventTypes.collect {
            ImapFolderEvent event = cont.metadata().create(ImapFolderEvent)
            event.folder = imapFolder
            event.event = it
            event.eventHandlers = new ArrayList<>()

            return event
        }

        cont.persistence().runInTransaction() { em ->
            events.forEach{ em.persist(it) }
            em.flush()
        }
        imapFolder.setEvents(events)

        return imapFolder
    }

    @SuppressWarnings(["GroovyAssignabilityCheck", "GroovyAccessibility"])
    ImapFolder imapFolder(ImapMailBox mailBox, folderName, disabled = false) {
        ImapFolder imapFolder = cont.metadata().create(ImapFolder)
        imapFolder.name = folderName
        imapFolder.mailBox = mailBox
        imapFolder.selected = true
        imapFolder.disabled = disabled

        cont.persistence().runInTransaction() { em ->
            em.persist(imapFolder)
            em.flush()
        }

        if (mailBox.folders == null) {
            mailBox.folders = new ArrayList<>()
        }
        mailBox.folders.add(imapFolder)

        return imapFolder
    }



    ///

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
}