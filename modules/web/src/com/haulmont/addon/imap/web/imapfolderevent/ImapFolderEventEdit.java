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

package com.haulmont.addon.imap.web.imapfolderevent;

import com.haulmont.addon.imap.entity.ImapEventHandler;
import com.haulmont.addon.imap.entity.ImapEventType;
import com.haulmont.addon.imap.entity.ImapFolderEvent;
import com.haulmont.addon.imap.service.ImapService;
import com.haulmont.cuba.core.global.Metadata;
import com.haulmont.cuba.gui.Notifications;
import com.haulmont.cuba.gui.UiComponents;
import com.haulmont.cuba.gui.components.AbstractEditor;
import com.haulmont.cuba.gui.components.Button;
import com.haulmont.cuba.gui.components.LookupField;
import com.haulmont.cuba.gui.components.Table;
import com.haulmont.cuba.gui.components.data.value.ContainerValueSource;
import com.haulmont.cuba.gui.data.CollectionDatasource;
import com.haulmont.cuba.gui.data.Datasource;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("CdiInjectionPointsInspection")
public class ImapFolderEventEdit extends AbstractEditor<ImapFolderEvent> {

    @Inject
    protected Datasource<ImapFolderEvent> imapFolderEventDs;

    @Inject
    private CollectionDatasource<ImapEventHandler, UUID> handlersDs;

    @Inject
    private Table<ImapEventHandler> handlersTable;

    @Inject
    private Button addHandlerBtn;

    @Inject
    private Button upHandlerBtn;

    @Inject
    private Button downHandlerBtn;

    @Inject
    private ImapService service;

    @Inject
    private UiComponents componentsFactory;

    @Inject
    private Metadata metadata;

    @Inject
    private Notifications notifications;

    @Override
    protected void postInit() {
        super.postInit();

        ImapFolderEvent folderEvent = getItem();

        ImapEventType eventType = folderEvent.getEvent();
        Map<String, List<String>> availableBeans = eventType != null
                ? service.getAvailableBeans(eventType.getEventClass()) : Collections.emptyMap();
        List<String> beanNames = new ArrayList<>(availableBeans.keySet());
        long maxHandlersCount = availableBeans.values().stream().mapToLong(Collection::size).sum();

        removeMissedHandlers(availableBeans);
        enableAddButton(maxHandlersCount);

        Map<ImapEventHandler, LookupField> handlerMethodLookupFields = new HashMap<>();
        if (folderEvent.getEventHandlers() != null) {
            for (ImapEventHandler eventHandler : folderEvent.getEventHandlers()) {
                handlerMethodLookupFields.put(eventHandler, makeBeanMethodLookup(availableBeans, eventHandler));
            }
        }

        addHandlersCollectionChangeListeners(availableBeans, maxHandlersCount, handlerMethodLookupFields);
        setHandlerChangeListeners(availableBeans);

        generateColumns(availableBeans, beanNames, handlerMethodLookupFields);

        addAfterCloseListener(e -> {

            @SuppressWarnings("unchecked")
            Datasource<ImapFolderEvent> parentDs = getParentDs();
            if (parentDs != null && parentDs.getItem() != null) {
                List<ImapEventHandler> eventHandlersInParentDs = parentDs.getItem().getEventHandlers();
                eventHandlersInParentDs.clear();
                eventHandlersInParentDs.addAll(folderEvent.getEventHandlers());
            }
        });
    }

    private void generateColumns(Map<String, List<String>> availableBeans,
                                 List<String> beanNames, Map<ImapEventHandler,
            LookupField> handlerMethodLookupFields) {

        handlersTable.addGeneratedColumn("beanName", eventHandler -> {
            LookupField<String> lookup = componentsFactory.create(LookupField.class);
            lookup.setValueSource(
                    new ContainerValueSource(handlersTable.getInstanceContainer(eventHandler), "beanName")
            );
            lookup.setWidth("250px");
            lookup.setFrame(getFrame());
            lookup.setOptionsList(beanNames);
            return lookup;
        });
        handlersTable.addGeneratedColumn("methodName", eventHandler -> {
            LookupField<String> lookup = handlerMethodLookupFields.get(eventHandler);
            lookup = lookup != null ? lookup : makeBeanMethodLookup(availableBeans, eventHandler);
            lookup.setValueSource(
                    new ContainerValueSource(handlersTable.getInstanceContainer(eventHandler), "methodName")
            );
            lookup.setOptionsList(
                    methodNames(availableBeans, eventHandler.getBeanName())
            );

            return lookup;
        });
    }

    private void addHandlersCollectionChangeListeners(Map<String, List<String>> availableBeans,
                                                      long maxHandlersCount, Map<ImapEventHandler,
            LookupField> handlerMethodLookupFields) {

        handlersDs.addItemChangeListener(e -> {
            ImapEventHandler handler = e.getItem();
            if (handler == null) {
                return;
            }

            List<ImapEventHandler> eventHandlers = getItem().getEventHandlers();
            int index = eventHandlers.indexOf(handler);
            upHandlerBtn.setEnabled(index != 0);
            downHandlerBtn.setEnabled(index != eventHandlers.size() - 1);
        });
        handlersDs.addCollectionChangeListener(e -> {
            if (e.getOperation() == CollectionDatasource.Operation.REMOVE) {
                for (ImapEventHandler handler : e.getItems()) {
                    handlerMethodLookupFields.remove(handler);
                }
                enableAddButton(maxHandlersCount);
            } else if (e.getOperation() == CollectionDatasource.Operation.ADD) {
                for (ImapEventHandler handler : e.getItems()) {
                    if (!handlerMethodLookupFields.containsKey(handler)) {
                        handlerMethodLookupFields.put(handler, makeBeanMethodLookup(availableBeans, handler));
                    }
                }
                enableAddButton(maxHandlersCount);
            }
        });
    }

    private void removeMissedHandlers(Map<String, List<String>> availableBeans) {
        List<ImapEventHandler> missedHandlers = handlersDs.getItems().stream()
                .filter(bm ->
                        !availableBeans.containsKey(bm.getBeanName()) || !availableBeans.get(bm.getBeanName()).contains(bm.getMethodName())
                ).collect(Collectors.toList());
        if (!missedHandlers.isEmpty()) {
            List<String> beanMethods = missedHandlers.stream()
                    .map(handler -> String.format("%s#%s", handler.getBeanName(), handler.getMethodName()))
                    .collect(Collectors.toList());
            notifications.create(Notifications.NotificationType.HUMANIZED)
                    .withCaption(formatMessage("missedHandlersWarning", beanMethods))
                    .show();
            for (ImapEventHandler handler : missedHandlers) {
                handlersDs.removeItem(handler);
            }
        }
    }

    private void setHandlerChangeListeners(Map<String, List<String>> availableBeans) {
        handlersDs.addItemPropertyChangeListener(event -> {
            ImapEventHandler item = event.getItem();
            if (Objects.equals("beanName", event.getProperty())) {
                if (event.getValue() != null) {

                    String beanName = event.getValue().toString();
                    List<String> methods = availableBeans.get(beanName);
                    if (handlersDs.getItems().stream()
                            .filter(bm -> bm != item && beanName.equals(bm.getBeanName()))
                            .count() == methods.size()) {

                        notifications.create(Notifications.NotificationType.HUMANIZED)
                                .withCaption(getMessage("beanNameConflictWarning"))
                                .show();
                        item.setBeanName(event.getPrevValue() != null ? event.getPrevValue().toString() : null);
                    } else {
                        handlersDs.modifyItem(item);
                        item.setMethodName(null);
                    }

                } else {
                    handlersDs.modifyItem(item);
                    item.setMethodName(null);
                }
            }
            if (Objects.equals("methodName", event.getProperty()) && event.getValue() != null) {
                String methodName = event.getValue().toString();
                handlersDs.getItems().stream()
                        .filter(bm -> bm != item && methodName.equals(bm.getMethodName()) && item.getBeanName().equals(bm.getBeanName()))
                        .findFirst().ifPresent(bm -> {

                    notifications.create(Notifications.NotificationType.HUMANIZED)
                            .withCaption(getMessage("methodNameConflictWarning"))
                            .show();
                    item.setMethodName(event.getPrevValue() != null ? event.getPrevValue().toString() : null);
                });
            }

        });
    }

    private LookupField makeBeanMethodLookup(Map<String, List<String>> availableBeans, ImapEventHandler eventHandler) {
        LookupField<String> lookup = componentsFactory.create(LookupField.class);
        lookup.setValueSource(
                new ContainerValueSource(handlersTable.getInstanceContainer(eventHandler), "methodName")
        );
        lookup.setWidth("250px");
        String beanName = eventHandler.getBeanName();
        lookup.setFrame(getFrame());
        lookup.setOptionsList(methodNames(availableBeans, beanName));
        return lookup;
    }

    private List<String> methodNames(Map<String, List<String>> availableBeans, String beanName) {
        return Optional.ofNullable(beanName)
                .map(availableBeans::get)
                .orElse(Collections.emptyList());
    }

    public void addHandler() {
        ImapEventHandler handler = metadata.create(ImapEventHandler.class);
        handler.setEvent(getItem());
        handlersDs.addItem(handler);
    }

    public void removeHandler() {
        if (handlersDs.getItem() != null) {
            handlersDs.removeItem(handlersDs.getItem());
        }
    }

    public void moveUpHandler() {
        ImapEventHandler handler = handlersDs.getItem();
        if (handler != null) {
            List<ImapEventHandler> eventHandlers = getItem().getEventHandlers();
            int index = eventHandlers.indexOf(handler);
            if (index != 0) {
                eventHandlers.remove(index);
                eventHandlers.add(index - 1, handler);
                handlersDs.refresh();
                handlersDs.setItem(handler);
            }
        }
    }

    public void moveDownHandler() {
        ImapEventHandler handler = handlersDs.getItem();
        if (handler != null) {
            List<ImapEventHandler> eventHandlers = getItem().getEventHandlers();
            int index = eventHandlers.indexOf(handler);
            if (index != eventHandlers.size() - 1) {
                eventHandlers.add(index + 2, handler);
                eventHandlers.remove(index);
                handlersDs.refresh();
                handlersDs.setItem(handler);
            }
        }
    }

    private void enableAddButton(long maxHandlersCount) {
        addHandlerBtn.setEnabled(handlersDs.size() < maxHandlersCount);
    }

}