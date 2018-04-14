package com.haulmont.addon.imap.sync.events;

import com.haulmont.addon.imap.config.ImapConfig;
import com.haulmont.addon.imap.core.ImapHelper;
import com.haulmont.addon.imap.entity.ImapEventHandler;
import com.haulmont.addon.imap.entity.ImapEventType;
import com.haulmont.addon.imap.entity.ImapFolder;
import com.haulmont.addon.imap.entity.ImapFolderEvent;
import com.haulmont.addon.imap.events.BaseImapEvent;
import com.haulmont.cuba.core.Persistence;
import com.haulmont.cuba.core.global.AppBeans;
import com.haulmont.cuba.core.global.Events;
import com.haulmont.cuba.core.global.Metadata;
import com.haulmont.cuba.security.app.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import javax.inject.Inject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@SuppressWarnings("CdiInjectionPointsInspection")
abstract class ImapEventsPublisher {

    private final static Logger log = LoggerFactory.getLogger(ImapEventsPublisher.class);

    @Inject
    ImapHelper imapHelper;

    @Inject
    Persistence persistence;

    @Inject
    Metadata metadata;

    @Inject
    Events events;

    @Inject
    Authentication authentication;

    @Inject
    ImapConfig imapConfig;

    void fireEvents(ImapFolder cubaFolder, Collection<BaseImapEvent> imapEvents) {
        log.info("Fire events {} for {}", imapEvents, cubaFolder);

        filterEvents(cubaFolder, imapEvents);

        log.debug("Filtered events for {}: {}", cubaFolder, imapEvents);

        imapEvents.forEach(event -> {
            events.publish(event);

            ImapEventType.getByEventType(event.getClass()).stream()
                    .map(cubaFolder::getEvent)
                    .filter(Objects::nonNull)
                    .map(ImapFolderEvent::getEventHandlers)
                    .filter(handlers -> !CollectionUtils.isEmpty(handlers))
                    .forEach(handlers -> invokeAttachedHandlers(event, cubaFolder, handlers));

        });
    }

    private void filterEvents(ImapFolder cubaFolder, Collection<BaseImapEvent> imapEvents) {
        for (ImapEventType eventType : ImapEventType.values()) {
            if (!cubaFolder.hasEvent(eventType)) {
                imapEvents.removeIf(event -> eventType.getEventClass().equals(event.getClass()));
            }
        }
    }

    private void invokeAttachedHandlers(BaseImapEvent event, ImapFolder cubaFolder, List<ImapEventHandler> handlers) {
        log.trace("{}: invoking handlers {} for event {}", cubaFolder.getName(), handlers, event);

        handlers.forEach(handler -> {
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
        });

    }
}
