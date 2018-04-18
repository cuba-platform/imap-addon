[![license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](http://www.apache.org/licenses/LICENSE-2.0)

# CUBA Platform Component - IMAP Email Client

This application component can be used to extend the capabilities of a [CUBA.Platform](https://www.cuba-platform.com/) application so that it can retrieve Emails via the [IMAP protocol](https://tools.ietf.org/html/rfc3501).

The main model to interact with incoming Emails is via Spring application Events. The application developer registeres Hook methods as an `@EventListener` which will invoked when Events in the IMAP mailbox happen (e.g. new Email received).

Besides the Event based programming model it is also possible to directly interact with the corresponding API methods.

**Please note that component still in development and not stable.**

## Installation

1. Open component in CUBA studio and invoke Run > Install app component
1. Open your application in CUBA studio and in project properties in 'Advanced' tab enable 'Use local Maven repository'
1. Select a version of the add-on which is compatible with the platform version used in your project:

| Platform Version | Add-on Version |
| ---------------- | -------------- |
| 6.7.x            | 0.1-SNAPSHOT |


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

or you can create `@Service` methods which will have only one attribute with correct event type
```
import org.springframework.context.event.EventListener;

public interface EmailReceiveService {
    String NAME = "ceuia_EmailReceiveService";

    void receiveEmail(NewEmailImapEvent event);
}
```

Once this is done, the method (in this case `receiveEmail` has to be registered on a particular Folder for a given IMAP connection. This has to be done at runtime via the IMAP configuration UI.

After that the method will get invoked every time when an event occurs.

#### Event types

The application component allows the following kind of IMAP events:

* EmailAnsweredImapEvent
* EmailSeenImapEvent
* EmailFlagChangedImapEvent
* NewEmailImapEvent
* EmailDeletedImapEvent
* EmailMovedImapEvent
* NewThreadImapEvent

