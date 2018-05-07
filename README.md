[![license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](http://www.apache.org/licenses/LICENSE-2.0)

# CUBA Platform Component - IMAP Email Client

This application component can be used to extend the capabilities of a [CUBA.Platform](https://www.cuba-platform.com/) application so that it can retrieve Emails via the [IMAP protocol](https://tools.ietf.org/html/rfc3501).

The main model to interact with incoming Emails is via Spring application Events. The application developer registers Hook methods as an `@EventListener` which will invoked when Events in the IMAP mailbox happen (e.g. new Email received).

Besides the Event based programming model it is also possible to directly interact with the corresponding API methods.

**Please note that component still in development and not stable.**

## Installation

1. Open component in CUBA studio and invoke Run > Install app component
1. Open your application in CUBA studio and in project properties in 'Advanced' tab enable 'Use local Maven repository'
1. Select a version of the add-on which is compatible with the platform version used in your project:

| Platform Version | Add-on Version |
| ---------------- | -------------- |
| 6.8.x            | 0.1-SNAPSHOT |


The latest version is: 0.1-SNAPSHOT

Add custom application component to your project:

* Artifact group: `com.haulmont.addon.imap`
* Artifact name: `imap-global`
* Version: *add-on version*


## Usage

### IMAP encryption configuration options

It is required to configure the following application properties in the `app.properties` of the application:

IMAP mail box password encryption keys:
```
imap.encryption.key = HBXv3Q70IlmBMiW4EMyPHw==
imap.encryption.iv = DYOKud/GWV5boeGvmR/ttg==
```


### Register EventListeners to interact with IMAP events
In order to react to IMAP events in your application, you can register `@Component` methods as Event listener through the `@EventListener` Annotation. 

```
import org.springframework.context.event.EventListener;

@Service(EmailReceiveService.NAME)
public class EmailReceiveServiceBean implements EmailReceiveService {

    @EventListener
    @Override
    public void receiveEmail(NewEmailImapEvent event) {
      // handle IMAP event
    }
}
```

or you can create `@Component` with method having only one parameter with correct event type
```
public class EmailReceiver {
    String NAME = "ceuia_EmailReceiver";

    public void receiveEmail(NewEmailImapEvent event) {
        // handle IMAP event
    }
}
```

Once this is done, the method (in this case `receiveEmail` has to be registered on a particular Folder for a given IMAP connection. This has to be done at runtime via the IMAP configuration UI.

After that the method will get invoked every time when an event occurs.

#### Event types

All events contain `ImapMessage` object that can be used to determine where an event occurs (mail box, folder, message)

The application component allows the following kind of IMAP events:

* `NewEmailImapEvent` is triggered for folder having event with type `ImapEventType.NEW_EMAIL` enabled 
when new message arrives to the folder on IMAP server
* `EmailSeenImapEvent` is triggered for folder having event with type `ImapEventType.EMAIL_SEEN` enabled 
when message is marked with IMAP flag `javax.mail.Flags.Flag.SEEN` 
* `EmailAnsweredImapEvent` is triggered for folder having event with type `ImapEventType.NEW_ANSWER` enabled 
when message is replied (usually it happens through marking message with IMAP flag `javax.mail.Flags.Flag.ANSWERED`)
* `EmailFlagChangedImapEvent` is triggered for folder having event with type `ImapEventType.FLAGS_UPDATED` enabled 
when message gets any IMAP flag changed, including both standard and custom flags. 
Event contains `Map` holding changed flags accompanied with actual state (set or unset)
* `EmailDeletedImapEvent` is triggered for folder having event with type `ImapEventType.EMAIL_DELETED` enabled 
when message is completely deleted from folder on IMAP server, it is **not** related to IMAP flag `javax.mail.Flags.Flag.DELETED`. 
Such events are also triggered when message is moved to trash folder on server in case `ImapMailBox` was configured with trash folder in place 
* `EmailMovedImapEvent` is triggered for folder having event with type `ImapEventType.EMAIL_MOVED` enabled 
when message is moved to other folder on IMAP server. 
**NOTICE**: standard implementation tracks only folders which are selected in `ImapMailBox` configuration not counting trash folder if such was configured
* `NewThreadImapEvent` is not implemented yet

### Using API

Component provides following API to interact with IMAP server:

* `ImapAPI` having methods:
    * `Collection<ImapFolderDto> fetchFolders(ImapMailBox)` - allows to retrieve all folders preserving tree structure. 
    * `Collection<ImapFolderDto> fetchFolders(ImapMailBox, String...)` - allows to retrieve folders with specified names. 
    Result does not preserve tree structure
    * `List<ImapFolderDto> fetchMessage(ImapMessage)` - allows to fetch single message using reference
    * `List<ImapMessageDto> fetchMessages(List<ImapMessage>)` - allows to fetch multiple messages using references.
    Supports fetching from multiple folders and mail boxes
    * `void moveMessage(ImapMessage, String)` - allows to move message to different folder on IMAP server
    * `void deleteMessage(ImapMessage)` - allows to completely delete message from the folder
    * `void setFlag(ImapMessage, ImapFlag, boolean)` - allows to change specified flag of the message, flag can be set or unset
* `ImapAttachmentsAPI` having methods:
    * `Collection<ImapMessageAttachment> fetchAttachments(ImapMessage)` - allows to retrieve attachments of the message. 
    Result contains only meta data, no content
    * `InputStream openStream(ImapMessageAttachment)` and `byte[] loadFile(ImapMessageAttachment` - allow to retrieve content of the message attachment