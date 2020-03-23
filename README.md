# IMAP

<p>
    <a href="http://www.apache.org/licenses/LICENSE-2.0"><img src="https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat" alt="license" title=""></a>
    <a href="https://travis-ci.org/cuba-platform/imap-addon"><img src="https://travis-ci.org/cuba-platform/imap-addon.svg?branch=master" alt="Build Status" title=""></a>
</p>


- [Overview](#overview)
- [Installation](#installation)
  - [From the Marketplace](#from-the-marketplace)
  - [By Coordinates](#by-coordinates)
- [Add-on Configuration](#add-on-configuration)
- [Usage](#usage)
  - [Component Functionalities](#component-functionalities)
    - [IMAP Configuration](#imap-configuration)
    - [IMAP Message Browser](#imap-message-browser)
  - [Registering EventListeners to Interact with IMAP Events](#registering-eventlisteners-to-interact-with-imap-events)
  - [Creating Handlers for IMAP Events](#creating-handlers-for-imap-events)
  - [Using API](#using-api)
  - [Registering Custom IMAP Implementation Class](#registering-custom-imap-implementation-class)

# Overview

The IMAP-addon provides a readily available instrument for integrating email messaging into any CUBA-based application
via the IMAP protocol. The main model of the component is designed to interact with incoming emails via Spring application events.

The component includes the following set of functionalities:
* Integration between any IMAP servers and CUBA applications.
* Basic API methods to work with the main email server functionalities:
    * Connecting to servers;
    * Retrieving and viewing emails;
    * Searching for emails;
    * Operating custom flags.
* Predefined events for implementing custom business logic and processing various updates.
* User interface for configuring IMAP connection settings and events.

See [sample application](https://github.com/cuba-platform/imap-addon-demo) using this component.
See [webinar](https://www.youtube.com/watch?v=u6U0Y4zNxMc) on the CUBA Platform channel.

# Installation

The add-on can be added to your project in one of the ways described below. Installation from the Marketplace is the simplest way. The last version of the add-on compatible with the used version of the platform will be installed.
Also, you can install the add-on by coordinates choosing the required version of the add-on from the table.

In case you want to install the add-on by manual editing or by building from sources see the complete add-ons installation guide in [CUBA Platform documentation](https://doc.cuba-platform.com/manual-latest/manual.html#app_components_usage).

## From the Marketplace

1. Open your application in CUBA Studio. Check the latest version of CUBA Studio on the [CUBA Platform site](https://www.cuba-platform.com/download/previous-studio/).
2. Go to *CUBA -> Marketplace* in the main menu.

 ![marketplace](img/marketplace.png)

3. Find the *IMAP* add-on there.

 ![addons](img/addons.png)

4. Click *Install* and apply the changes.
The add-on corresponding to the used platform version will be installed.

## By coordinates

1. Open your application in CUBA Studio. Check the latest version of CUBA Studio on the [CUBA Platform site](https://www.cuba-platform.com/download/previous-studio/).
2. Go to *CUBA -> Marketplace* in the main menu.
3. Click the icon in the upper-right corner.

 ![by-coordinates](img/by-coordinates.png)

4. Paste the add-on coordinates in the corresponding field as follows:

 `com.haulmont.addon.imap:imap-global:<add-on version>`

 where `<add-on version>` is compatible with the used version of the CUBA platform.

| Platform Version | Add-on Version |
|------------------|----------------|
| 7.2.x            | 1.5.0          |
| 7.1.x            | 1.4.0          |
| 7.0.x            | 1.3.1          |
| 6.10.x           | 1.2.0          |
| 6.9.x            | 1.1.1          |
| 6.8.x            | 1.0.1          |

5. Click *Install* and apply the changes. The add-on will be installed to your project.

# Add-on Configuration

Before using the add-on do the following:

1. Open `app.properties` file of your application and configure the following application properties:

```xml
#IMAP configuration
imap.encryption.key = HBXv3Q70IlmBMiW4EMyPHw==
imap.encryption.iv = DYOKud/GWV5boeGvmR/ttg==

#Enable scheduling
cuba.schedulingActive=true
```

2. Run the application and go to  *Administration -> Scheduled tasks* screen. Configure `imap_ImapScheduler -> syncImap` scheduled task.

# Usage

## Component Functionalities

### IMAP Configuration <a name="imap-configuration"></a>

*IMAP Configuration Browser* is designed to add and manage mailboxes from which emails are retrieved. The browser is
available from *Menu: Administration → IMAP → IMAP Configuration*.

![IMAP Configuration Menu Item](img/imap_conf_item.png)

Creating a new configuration is available by clicking the *Create* button.

![Creating IMAP Configuration](img/creating-IMAP-conf.png)

#### IMAP Configuration Editor <a name="imap-configuration-editor"></a>

*IMAP Configuration Editor* comprises two main sections to fill in: *Basic* and *Advanced*.

![IMAP Configuration Editor](img/IMAP-conf-editor.png)

**Basic**

The *Basic* section enables to configure the main attributes for connecting to an email server via IMAP. The description of
all fields is provided below.

* *Name*: specify a name for connection.
* *Host*: specify a host name or IP address of an email server.
* *Port*: provide a port number to connect to an email server. The default value is *143*.
* *Secure Connection*: select an option for secure connection if required. Available values: *STARTTLS*, *SSL/TLS*.
* *Root Certificate*: upload a client certificate if required.
* *Username*: provide a username to connect to an email server.
* *Password*: provide a user password to connect to an email server.

The *Basic* section comprises the *Connect* button. After providing all required information, use the button to upload
a list of folders from the email server to work with.

**Advanced**

The *Advanced* section provides a set of additional options for connecting to an email server.

* *Custom Flag*: specify a custom flag.
* *Use trash folder to remove emails*: if checked, then it is possible to specify a trash folder of a current mailbox
(the *Trash Folder* field becomes available). The setting works as follows: if an email is moved to the specified folder on
the email server, `EmailDeletedImapEvent` occurs (for more details, see [Event Types](#event-types)).
* *Use custom event generator class*: if checked, it is possible to specify a custom class that defines additional logic
for connecting to an IMAP server, handling events, etc. If the current setting is enabled, the *Event Generator Class*
lookup field becomes available. To learn how to register a custom event generator class, please refer to
[Registering Custom IMAP Implementation Class](#registering-custom-imap-implementation-class).
* *Use proxy*: if checked, proxy settings become available (see the description below).

 **Proxy Configuration**

* *Proxy Host*: provide a host name or IP address of a proxy server.
* *Proxy Port*: provide a port to connect to a proxy server.
* *Use web proxy*: if checked, web proxy is used.

**Table of Folders**

Once connection to the provided email server is successfully established, the table of folders becomes available.

![Table of Folders](img/folders.png)

The table shows a list of folders from the email server you are connected to. In order to enable/disable some folders,
use the checkboxes in the second column. If some folder is disabled, then messages from it are not retrieved.

For each folder you can select a set of IMAP events by using the *Events* table and register custom logic for them
(for more details, please refer to [Configuration](#configuration)).

### IMAP Message Browser

All emails from connected mailboxes are displayed in *IMAP Message Browser* (available from *Menu: Administration → IMAP → IMAP Message Browser*).

![IMAP Message Browser](img/Imap-message-browser.png)

Selecting an email and clicking *View* opens it for reading. Email Screen contains all general details of an email:
date, author, subject, etc., and two tabs: *Body* and *Attachments*.

On the *Body* tab, the whole text of an email is displayed.

The *Attachments* tab comprises the table of attachments and the button to download required elements.

![Attachments](img/email-attachments.png)

## Registering EventListeners to Interact with IMAP Events

In order to make your application react to IMAP events, you can register the `@Component` methods as Event listeners by using
the `@EventListener` annotation. The example of how to set up an event listener is provided below.

```java
import org.springframework.context.event.EventListener;

@Service(EmailReceiveService.NAME)
public class EmailReceiveServiceBean implements EmailReceiveService {

    @EventListener
    @Override
    public void receiveEmail(NewEmailImapEvent event) {
      // handles IMAP event
    }
}
```

Another option is to create `@Component` with a method having the required event type as the only parameter.

```java
public class EmailReceiver {
    String NAME = "ceuia_EmailReceiver";

    public void receiveEmail(NewEmailImapEvent event) {
        // handle IMAP event
    }
}
```

Once it is done, the selected method (in the example, it is `receiveEmail`) should be registered on a particular folder
for a given IMAP connection. This should be done at runtime using the IMAP configuration UI (see [Creating Handlers for
IMAP Events](#imap-handlers)).
After that, the method will be invoked every time, when the configured event occurs.

## Creating Handlers for IMAP Events

After registering EventListeners, it is required to create handlers for IMAP events related to a particular folder and
mailbox (for more information see [IMAP Connection](#imap-configuration)). The table of folders comprises several columns,
each representing a certain event type (e.g. New, Seen, Replied, etc.). Clicking gear icons opens *IMAP Folder Event Editor*.

![IMAP Folder Event Editor](img/imap-folder-event-editor.png)

There you can specify required beans and methods for them.

### Event types <a name="event-types"></a>

All events contain the `ImapMessage` object that can be used to determine where an event occurs (mailbox, folder, message).

The application component supports the following kinds of IMAP events:

* `NewEmailImapEvent` is triggered for a folder having an event of the `ImapEventType.NEW_EMAIL` type enabled,
when a new message appears in the folder on the IMAP server.
* `EmailSeenImapEvent` is triggered for a folder having an event of the `ImapEventType.EMAIL_SEEN` type enabled,
when a message is marked with the `javax.mail.Flags.Flag.SEEN` IMAP flag.
* `EmailAnsweredImapEvent` is triggered for a folder having an event of the `ImapEventType.NEW_ANSWER` type enabled,
when a message is replied (usually it happens when a message is marked with the `javax.mail.Flags.Flag.ANSWERED` IMAP flag).
* `EmailFlagChangedImapEvent` is triggered for a folder having an event of the `ImapEventType.FLAGS_UPDATED` type enabled,
when a standard or custom IMAP flag is changed for a message.
The event contains a `Map` of all changed flags and their actual state (set or unset).
* `EmailDeletedImapEvent` is triggered for a folder having an event of the `ImapEventType.EMAIL_DELETED` type enabled,
when a message is completely deleted from a folder on the IMAP server side, it is **not** related to the IMAP flag `javax.mail.Flags.Flag.DELETED`.
Such events are also triggered when a message is moved to a trash folder (if it is configured for a mailbox) on the server.
* `EmailMovedImapEvent` is triggered for a folder having an event of the `ImapEventType.EMAIL_MOVED` type enabled,
when a message is moved to another folder on the IMAP server.
**Note**: the standard implementation tracks only folders which are selected in the `ImapMailBox` configuration,
but does not count a trash folder, if one is configured.
* `NewThreadImapEvent` is not implemented yet.

## Using API

The component provides the following API to interact with the IMAP server:

* `ImapAPI` methods:
    * `Collection<ImapFolderDto> fetchFolders(ImapMailBox)` — retrieves all folders preserving the tree structure.
    * `Collection<ImapFolderDto> fetchFolders(ImapMailBox, String...)` — retrieves folders with the specified names.
    The result is not structured as a tree.
    * `List<ImapFolderDto> fetchMessage(ImapMessage)` — fetches a single message using a reference.
    * `void moveMessage(ImapMessage, String)` — moves a message to a different folder on the IMAP server side.
    * `void deleteMessage(ImapMessage)` — deletes a message from a folder.
    * `void setFlag(ImapMessage, ImapFlag, boolean)` — sets or unsets a specified flag for a message.
* `ImapAttachmentsAPI` methods:
    * `Collection<ImapMessageAttachment> fetchAttachments(ImapMessage)` — retrieves attachments included in a message.
    The result contains only meta data, no content.
    * `InputStream openStream(ImapMessageAttachment)` and `byte[] loadFile(ImapMessageAttachment` — retrieve the content
    of a message attachment.

## Registering Custom IMAP Implementation Class <a name="registering-custom-imap-implementation-class"></a>

In order to configure custom logic for a specific mailbox (e.g for applying IMAP extensions and custom communication
mechanisms), it is required to register a custom IMAP implementation class in the source code of your application.
The example of how to register such class is given below.

```java
@Component("demo_SimpleSingleFolderNewMessagesEventsGenerator")
public class SimpleSingleFolderNewMessagesEventsGenerator implements ImapEventsGenerator {
...
}
```

After registering a class implementing `ImapEventsGenerator`, it appears in the dropdown list of the *Event Generator Class*
field in *IMAP Configuration Editor* (for more details, see [IMAP Configuration Editor](#imap-configuration-editor)).

![Event Generator Class Field](img/event-generator-class.png)
