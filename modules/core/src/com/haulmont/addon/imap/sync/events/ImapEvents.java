package com.haulmont.addon.imap.sync.events;

import com.haulmont.addon.imap.entity.*;
import com.haulmont.addon.imap.events.BaseImapEvent;
import com.haulmont.addon.imap.sync.ImapFolderSyncAction;
import com.haulmont.addon.imap.sync.ImapFolderSyncEvent;
import com.haulmont.addon.imap.sync.events.standard.ImapStandardEventsGenerator;
import com.haulmont.cuba.core.global.AppBeans;
import com.haulmont.cuba.core.global.Events;
import com.haulmont.cuba.core.sys.AppContext;
import com.haulmont.cuba.security.app.Authentication;
import com.sun.mail.imap.IMAPMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.inject.Inject;
import javax.mail.Message;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

@Component("imap_Events")
public class ImapEvents {

    private final static Logger log = LoggerFactory.getLogger(ImapEvents.class);

    private final Events events;
    private final Authentication authentication;
    private final ImapStandardEventsGenerator standardEventsGenerator;

    @SuppressWarnings("CdiInjectionPointsInspection")
    @Inject
    public ImapEvents(Events events,
                      Authentication authentication,
                      @Qualifier(ImapStandardEventsGenerator.NAME) ImapStandardEventsGenerator standardEventsGenerator) {
        this.events = events;
        this.authentication = authentication;
        this.standardEventsGenerator = standardEventsGenerator;
    }

    public void handleNewMessages(ImapFolder cubaFolder) {
        events.publish(new ImapFolderSyncEvent(
                new ImapFolderSyncAction(cubaFolder.getId(), ImapFolderSyncAction.Type.NEW))
        );
        fireEvents( cubaFolder, getSyncImplementation(cubaFolder.getMailBox()).generateForNewMessages(cubaFolder) );
    }

    public void handleChangedMessages(ImapFolder cubaFolder) {
        events.publish(new ImapFolderSyncEvent(
                new ImapFolderSyncAction(cubaFolder.getId(), ImapFolderSyncAction.Type.CHANGED))
        );
        fireEvents( cubaFolder, getSyncImplementation(cubaFolder.getMailBox()).generateForChangedMessages(cubaFolder) );
    }

    public void handleMissedMessages(ImapFolder cubaFolder) {
        events.publish(new ImapFolderSyncEvent(
                new ImapFolderSyncAction(cubaFolder.getId(), ImapFolderSyncAction.Type.MISSED))
        );
        fireEvents( cubaFolder, getSyncImplementation(cubaFolder.getMailBox()).generateForMissedMessages(cubaFolder) );
    }

    public void handleNewMessages(ImapFolder cubaFolder, Message[] newMessages) {
        events.publish(new ImapFolderSyncEvent(
                new ImapFolderSyncAction(cubaFolder.getId(), ImapFolderSyncAction.Type.NEW))
        );
        Collection<IMAPMessage> imapMessages = new ArrayList<>(newMessages.length);
        for (Message message : newMessages) {
            imapMessages.add((IMAPMessage) message);
        }
        fireEvents(
                cubaFolder,
                getSyncImplementation(cubaFolder.getMailBox()).generateForNewMessages(cubaFolder, imapMessages)
        );
    }

    public void handleChangedMessages(ImapFolder cubaFolder, Message[] changedMessages) {
        events.publish(new ImapFolderSyncEvent(
                new ImapFolderSyncAction(cubaFolder.getId(), ImapFolderSyncAction.Type.CHANGED))
        );
        Collection<IMAPMessage> imapMessages = new ArrayList<>(changedMessages.length);
        for (Message message : changedMessages) {
            imapMessages.add((IMAPMessage) message);
        }
        fireEvents(
                cubaFolder,
                getSyncImplementation(cubaFolder.getMailBox()).generateForChangedMessages(cubaFolder, imapMessages)
        );
    }

    public void handleMissedMessages(ImapFolder cubaFolder, Message[] missedMessages) {
        events.publish(new ImapFolderSyncEvent(
                new ImapFolderSyncAction(cubaFolder.getId(), ImapFolderSyncAction.Type.MISSED))
        );
        Collection<IMAPMessage> imapMessages = new ArrayList<>(missedMessages.length);
        for (Message message : missedMessages) {
            imapMessages.add((IMAPMessage) message);
        }
        fireEvents(
                cubaFolder,
                getSyncImplementation(cubaFolder.getMailBox()).generateForMissedMessages(cubaFolder, imapMessages)
        );
    }

    private ImapEventsGenerator getSyncImplementation(ImapMailBox mailBox) {
        String eventsGeneratorClassName = mailBox.getEventsGeneratorClass();
        if (eventsGeneratorClassName != null) {
            try {
                Class<?> eventsGeneratorClass = Class.forName(eventsGeneratorClassName);
                Map<String, ?> beans = AppContext.getApplicationContext()
                        .getBeansOfType(eventsGeneratorClass);
                if (beans.isEmpty()) {
                    return standardEventsGenerator;
                }
                Map.Entry<String, ?> bean = beans.entrySet().iterator().next();
                if (!(bean.getValue() instanceof ImapEventsGenerator)) {
                    log.warn("Bean {} is not implementation of ImapEventsGenerator interface", bean.getKey());
                    return standardEventsGenerator;
                }
                return (ImapEventsGenerator) bean.getValue();
            } catch (ClassNotFoundException e) {
                log.warn("No such class {}, it is used as imap events generator for mailbox#{}",
                        eventsGeneratorClassName, mailBox.getId()
                );
            }
        }
        return standardEventsGenerator;
    }

    private void fireEvents(ImapFolder cubaFolder, Collection<? extends BaseImapEvent> imapEvents) {
        log.info("Fire events {} for {}", imapEvents, cubaFolder);

        filterEvents(cubaFolder, imapEvents);

        log.debug("Filtered events for {}: {}", cubaFolder, imapEvents);

        for (BaseImapEvent event : imapEvents) {
            events.publish(event);

            ImapEventType.getByEventType(event.getClass()).stream()
                    .map(cubaFolder::getEvent)
                    .filter(Objects::nonNull)
                    .map(ImapFolderEvent::getEventHandlers)
                    .filter(handlers -> !CollectionUtils.isEmpty(handlers))
                    .forEach(handlers -> invokeAttachedHandlers(event, cubaFolder, handlers));
        }
    }

    private void filterEvents(ImapFolder cubaFolder, Collection<? extends   BaseImapEvent> imapEvents) {
        for (ImapEventType eventType : ImapEventType.values()) {
            if (!cubaFolder.hasEvent(eventType)) {
                imapEvents.removeIf(event -> eventType.getEventClass().equals(event.getClass()));
            }
        }
    }

    private void invokeAttachedHandlers(BaseImapEvent event, ImapFolder cubaFolder, List<ImapEventHandler> handlers) {
        log.trace("{}: invoking handlers {} for event {}", cubaFolder.getName(), handlers, event);

        for (ImapEventHandler handler : handlers) {
            Object bean = AppBeans.get(handler.getBeanName());
            if (bean == null) {
                log.warn("No bean {} is available, check the folder {} configuration", handler.getBeanName(), cubaFolder);
                return;
            }
            Class<? extends BaseImapEvent> eventClass = event.getClass();
            try {
                authentication.begin();
                List<Method> methods = Arrays.stream(bean.getClass().getMethods())
                        .filter(m -> m.getName().equals(handler.getMethodName()))
                        .filter(m -> m.getParameterTypes().length == 1 && m.getParameterTypes()[0].isAssignableFrom(eventClass))
                        .collect(Collectors.toList());
                log.trace("{}: methods to invoke: {}", handler, methods);
                if (methods.isEmpty()) {
                    log.warn("No method {} for bean {} is available, check the folder {} configuration",
                            handler.getMethodName(), handler.getBeanName(), cubaFolder);
                }
                for (Method method : methods) {
                    method.invoke(bean, event);
                }
            } catch (InvocationTargetException | IllegalAccessException e) {
                throw new RuntimeException("Can't invoke bean for imap folder event", e);
            } finally {
                authentication.end();
            }
        }

    }
}
