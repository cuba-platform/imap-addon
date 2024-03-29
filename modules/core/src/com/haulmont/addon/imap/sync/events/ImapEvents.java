/*
 * Copyright (c) 2008-2019 Haulmont.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.haulmont.addon.imap.sync.events;

import com.haulmont.addon.imap.api.ImapEventsGenerator;
import com.haulmont.addon.imap.dao.ImapDao;
import com.haulmont.addon.imap.entity.*;
import com.haulmont.addon.imap.events.BaseImapEvent;
import com.haulmont.bali.util.ReflectionHelper;
import com.haulmont.cuba.core.global.AppBeans;
import com.haulmont.cuba.core.global.Events;
import com.haulmont.cuba.core.sys.AppContext;
import com.haulmont.cuba.security.app.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.inject.Inject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

@Component("imap_Events")
public class ImapEvents {

    private final static Logger log = LoggerFactory.getLogger(ImapEvents.class);

    @Inject
    private Events events;

    @Inject
    private Authentication authentication;

    @Inject
    private ImapDao imapDao;

    public void init(ImapMailBox mailBox) {
        getEventsGeneratorImplementation(mailBox).init(mailBox);
    }

    public void shutdown(ImapMailBox mailBox) {
        getEventsGeneratorImplementation(mailBox).shutdown(mailBox);
    }

    public void handleNewMessages(ImapFolder cubaFolder) {
        fireEvents(cubaFolder, getEventsGeneratorImplementation(cubaFolder.getMailBox()).generateForNewMessages(cubaFolder));
    }

    public void handleChangedMessages(ImapFolder cubaFolder) {
        fireEvents(cubaFolder, getEventsGeneratorImplementation(cubaFolder.getMailBox()).generateForChangedMessages(cubaFolder));
    }

    public void handleMissedMessages(ImapFolder cubaFolder) {
        fireEvents(cubaFolder, getEventsGeneratorImplementation(cubaFolder.getMailBox()).generateForMissedMessages(cubaFolder));
    }

    private ImapEventsGenerator getEventsGeneratorImplementation(ImapMailBox mailBox) {
        String eventsGeneratorClassName = mailBox.getEventsGeneratorClass();
        if (eventsGeneratorClassName != null) {
            Class<?> eventsGeneratorClass = ReflectionHelper.getClass(eventsGeneratorClassName);
            Map<String, ?> beans = AppContext.getApplicationContext()
                    .getBeansOfType(eventsGeneratorClass);
            if (beans.isEmpty()) {
                return getStandartEventsGenerator();
            }
            Map.Entry<String, ?> bean = beans.entrySet().iterator().next();
            if (!(bean.getValue() instanceof ImapEventsGenerator)) {
                log.warn("Bean {} is not implementation of ImapEventsGenerator interface", bean.getKey());
                return getStandartEventsGenerator();
            }
            return (ImapEventsGenerator) bean.getValue();
        }
        return getStandartEventsGenerator();
    }

    private ImapEventsGenerator getStandartEventsGenerator() {
        return AppBeans.get(ImapStandardEventsGenerator.NAME);
    }

    private void fireEvents(ImapFolder cubaFolder, Collection<? extends BaseImapEvent> imapEvents) {
        log.trace("Fire events {} for {}", imapEvents, cubaFolder);

        if (imapEvents.isEmpty()) {
            return;
        }

        filterEvents(cubaFolder, imapEvents);

        UUID folderId = cubaFolder.getId();
        log.debug("Filtered events for {}: {}", cubaFolder, imapEvents);
        authentication.begin();
        try {
            ImapFolder freshFolder = imapDao.findFolder(folderId);
            for (BaseImapEvent event : imapEvents) {
                log.trace("firing event {}", event);
                events.publish(event);

                List<ImapEventHandler> eventHandlers = ImapEventType.getByEventType(event.getClass()).stream()
                        .map(freshFolder::getEvent)
                        .filter(Objects::nonNull)
                        .map(ImapFolderEvent::getEventHandlers)
                        .filter(handlers -> !CollectionUtils.isEmpty(handlers))
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList());
                log.trace("firing event {} using handlers {}", event, eventHandlers);
                invokeAttachedHandlers(event, freshFolder, eventHandlers);

                log.trace("finish processing event {}", event);
            }
        } finally {
            authentication.end();
        }
    }

    private void filterEvents(ImapFolder cubaFolder, Collection<? extends BaseImapEvent> imapEvents) {
        for (ImapEventType eventType : ImapEventType.values()) {
            if (!cubaFolder.hasEvent(eventType)) {
                imapEvents.removeIf(event -> eventType.getEventClass().equals(event.getClass()));
            }
        }
    }

    private void invokeAttachedHandlers(BaseImapEvent event, ImapFolder cubaFolder, List<ImapEventHandler> handlers) {
        log.trace("{}: invoking handlers {} for event {}", cubaFolder.getName(), handlers, event);

        for (ImapEventHandler handler : handlers) {
            if (!AppBeans.containsBean(handler.getBeanName())) {
                log.warn("No bean {} is available, check the folder {} configuration", handler.getBeanName(), cubaFolder);
                return;
            }
            Object bean = AppBeans.get(handler.getBeanName());
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
