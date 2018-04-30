package spec.imap.core.api

import com.haulmont.addon.imap.ImapcomponentTestContainer
import com.haulmont.addon.imap.api.ImapAPI
import com.haulmont.addon.imap.entity.ImapAuthenticationMethod
import com.haulmont.addon.imap.entity.ImapMailBox
import com.haulmont.addon.imap.entity.ImapSimpleAuthentication
import com.haulmont.cuba.core.global.AppBeans
import com.icegreen.greenmail.imap.ImapConstants
import com.icegreen.greenmail.imap.ImapHostManager
import com.icegreen.greenmail.user.GreenMailUser
import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest
import org.junit.ClassRule
import spock.lang.Shared
import spock.lang.Specification


class ImapAPISpec extends Specification {

    private static final String USER_PASSWORD = "abcdef123"
    private static final String USER_NAME = "hascode"
    private static final String EMAIL_USER_ADDRESS = "hascode@localhost"
    private static final String LOCALHOST = "127.0.0.1"
    private static final String D = ImapConstants.HIERARCHY_DELIMITER

    /*private static final String EMAIL_TO = "someone@localhost.com"
    private static final String EMAIL_SUBJECT = "Test E-Mail"
    private static final String EMAIL_TEXT = "This is a test e-mail."*/


    @Shared @ClassRule
    public ImapcomponentTestContainer cont = ImapcomponentTestContainer.Common.INSTANCE

    private ImapAPI imapAPI

    private GreenMail mailServer
    private ImapHostManager imapManager
    private GreenMailUser user
    private ImapMailBox mailBoxConfig

    void setup() {
        imapAPI = AppBeans.get(ImapAPI)

        mailServer = new GreenMail(ServerSetupTest.IMAP)
        mailServer.start()
        imapManager = mailServer.managers.imapHostManager

        user = mailServer.setUser(EMAIL_USER_ADDRESS, USER_NAME, USER_PASSWORD)

        mailBoxConfig = cont.metadata().create(ImapMailBox)
        mailBoxConfig.host = LOCALHOST
        mailBoxConfig.port = mailServer.getImap().port
        mailBoxConfig.setAuthenticationMethod(ImapAuthenticationMethod.SIMPLE)
        mailBoxConfig.authentication = new ImapSimpleAuthentication()
        mailBoxConfig.authentication.password = user.password
        mailBoxConfig.authentication.username = user.login

        cont.persistence().runInTransaction() { em ->
            em.persist(mailBoxConfig.authentication)
            em.persist(mailBoxConfig)
            em.flush()
        }
    }

    def "fetch all folders for mailbox"() {
        given:
        imapManager.createMailbox(user, "root0")
        imapManager.createMailbox(user, "root0${D}child0")

        imapManager.createMailbox(user, "root1${D}child0")
        imapManager.createMailbox(user, "root1${D}child1")
        imapManager.createMailbox(user, "root1${D}child2")
        imapManager.createMailbox(user, "root1${D}child2${D}grandch0")
        imapManager.createMailbox(user, "root1${D}child2${D}grandch1")

        imapManager.createMailbox(user, "root2")

        expect:
        def allFoldersTree = imapAPI.fetchFolders(mailBoxConfig)
        allFoldersTree.size() == 4 // INBOX + 3 root folders created
        // INBOX
        allFoldersTree[0].fullName == imapManager.getInbox(user).name && allFoldersTree[0].canHoldMessages
        // first root folder and its child folder
        allFoldersTree[1].fullName == "root0" && allFoldersTree[1].canHoldMessages
        allFoldersTree[1].children.size() == 1
        allFoldersTree[1].children[0].fullName == "root0${D}child0" && allFoldersTree[1].children[0].canHoldMessages
        allFoldersTree[1].children[0].children.isEmpty()

        // second root folder and its children
        allFoldersTree[2].fullName == "root1" && !allFoldersTree[2].canHoldMessages
        allFoldersTree[2].children.size() == 3
        // first child folder
        allFoldersTree[2].children[0].fullName == "root1${D}child0" && allFoldersTree[2].children[0].canHoldMessages
        allFoldersTree[2].children[0].children.isEmpty()
        // second child folder
        allFoldersTree[2].children[1].fullName == "root1${D}child1" && allFoldersTree[2].children[1].canHoldMessages
        allFoldersTree[2].children[1].children.isEmpty()
        // third child folder and its children
        allFoldersTree[2].children[2].fullName == "root1${D}child2" && allFoldersTree[2].children[2].canHoldMessages
        allFoldersTree[2].children[2].children.size() == 2
        allFoldersTree[2].children[2].children[0].fullName == "root1${D}child2${D}grandch0"
        allFoldersTree[2].children[2].children[0].canHoldMessages
        allFoldersTree[2].children[2].children[1].fullName == "root1${D}child2${D}grandch1"
        allFoldersTree[2].children[2].children[1].canHoldMessages
        // third root folder without children
        allFoldersTree[3].fullName == "root2" && allFoldersTree[3].canHoldMessages && allFoldersTree[3].children.isEmpty()
    }
}