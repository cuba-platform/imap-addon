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

package com.haulmont.addon.imap.web.imapmailbox;

import com.haulmont.addon.imap.entity.*;
import com.haulmont.addon.imap.exception.ImapException;
import com.haulmont.addon.imap.service.ImapService;
import com.haulmont.addon.imap.web.imapmailbox.helper.FolderRefresher;
import com.haulmont.bali.util.ParamsMap;
import com.haulmont.cuba.core.global.AppBeans;
import com.haulmont.cuba.core.global.Messages;
import com.haulmont.cuba.core.global.Metadata;
import com.haulmont.cuba.core.global.PersistenceHelper;
import com.haulmont.cuba.gui.Notifications;
import com.haulmont.cuba.gui.ScreenBuilders;
import com.haulmont.cuba.gui.UiComponents;
import com.haulmont.cuba.gui.WindowManager;
import com.haulmont.cuba.gui.components.*;
import com.haulmont.cuba.gui.components.actions.BaseAction;
import com.haulmont.cuba.gui.components.data.value.ContainerValueSource;
import com.haulmont.cuba.gui.data.CollectionDatasource;
import com.haulmont.cuba.gui.data.Datasource;
import com.haulmont.cuba.gui.data.HierarchicalDatasource;
import com.haulmont.cuba.gui.data.impl.AbstractDatasource;
import com.haulmont.cuba.gui.data.impl.DatasourceImplementation;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

@SuppressWarnings({"CdiInjectionPointsInspection", "SpringJavaAutowiredFieldsWarningInspection"})
public class ImapMailBoxEdit extends AbstractEditor<ImapMailBox> {

    @Inject
    private Datasource<ImapMailBox> mailBoxDs;

    @Inject
    private Datasource<ImapSimpleAuthentication> authenticationDs;

    @Inject
    private HierarchicalDatasource<ImapFolder, UUID> foldersDs;

    @Inject
    private CollectionDatasource<ImapFolderEvent, UUID> eventsDs;

    @Inject
    private CollectionDatasource<ImapEventHandler, UUID> handlersDs;

    @Inject
    private ImapService service;

    @Inject
    private FolderRefresher folderRefresher;

    @Inject
    private Metadata metadata;

    @Inject
    private UiComponents componentsFactory;

    @Inject
    private Notifications notifications;

    @Inject
    private ScreenBuilders screenBuilders;

    @Inject
    private Logger log;

    @Inject
    private FieldGroup mainParams;

    @Inject
    private FieldGroup advancedParams;

    @Inject
    private FieldGroup proxyParams;

    @Inject
    private CheckBox useTrashFolderChkBox;

    @Inject
    private CheckBox useCustomEventsGeneratorChkBox;

    @Inject
    private LookupField<String> customEventsGeneratorClassLookup;

    @Inject
    private CheckBox useProxyChkBox;

    @Inject
    private TreeTable<ImapFolder> foldersTable;

    @Inject
    private BoxLayout selectedFolderPanel;

    @Inject
    private GridLayout editEventsGrid;

    @Inject
    private CheckBox allEventsChkBox;

    @Inject
    private TextField<String> cubaFlagTextField;

    @Inject
    private PickerField trashFolderPickerField;

    @Inject
    private Button checkConnectionBtn;

    private boolean connectionEstablished = false;

    public void checkTheConnection() {
        setEnableForButtons(false);
        try {
            refreshFolders(folderRefresher.refreshFolders(getItem()));
            notifications.create(Notifications.NotificationType.HUMANIZED)
                    .withCaption(getMessage("connectionSucceed"))
                    .show();
        } catch (ImapException e) {
            log.error("Connection Error", e);
            notifications.create(Notifications.NotificationType.ERROR)
                    .withCaption(getMessage("connectionFailed"))
                    .withDescription(e.getMessage())
                    .show();
        }
    }

    public void refreshFolders() {
        refreshFolders(folderRefresher.refreshFolders(getItem()));
    }

    public void enableFolder() {
        ImapFolder folder = foldersTable.getSingleSelected();
        changeSelection(folder, true);
    }

    public void enableFolderWithChildren() {
        ImapFolder folder = foldersTable.getSingleSelected();
        changeSelectionWithChildren(folder, true);
    }

    public void disableFolder() {
        ImapFolder folder = foldersTable.getSingleSelected();
        changeSelection(folder, false);
    }

    public void disableFolderWithChildren() {
        ImapFolder folder = foldersTable.getSingleSelected();
        changeSelectionWithChildren(folder, false);
    }

    private void changeSelectionWithChildren(ImapFolder folder, boolean selection) {
        changeSelection(folder, selection);
        if (folder != null) {
            for (ImapFolder childFolder : foldersDs.getItems()) {
                if (childFolder != null && folder.equals(childFolder.getParent())) {
                    changeSelectionWithChildren(childFolder, selection);
                }
            }
        }
    }

    private void changeSelection(ImapFolder folder, boolean selection) {
        if (folder != null
                && Boolean.TRUE.equals(folder.getSelectable())
                && Boolean.TRUE.equals(folder.getSelected()) != selection) {

            folder.setSelected(selection);
            foldersDs.modifyItem(folder);
        }
    }

    @Override
    public void init(Map<String, Object> params) {
        setupFolders();
        setupEvents();

        PickerField.LookupAction trashFolderLookupAction = PickerField.LookupAction.create(trashFolderPickerField);
        trashFolderPickerField.addAction(trashFolderLookupAction);
        trashFolderLookupAction.setLookupScreen("imap$Folder.lookup");
        trashFolderLookupAction.setLookupScreenOpenType(WindowManager.OpenType.THIS_TAB);
        trashFolderLookupAction.setLookupScreenParamsSupplier(() -> ParamsMap.of("mailBox", getItem()));
        //noinspection unchecked
        trashFolderLookupAction.setAfterLookupSelectionHandler(items ->
                ((DatasourceImplementation) mailBoxDs).modified(getItem())
        );
    }

    private void setupFolders() {
        foldersTable.addGeneratedColumn("selected", folder -> {
            CheckBox checkBox = componentsFactory.create(CheckBox.class);
            checkBox.setValueSource(
                    new ContainerValueSource(foldersTable.getInstanceContainer(folder), "selected")
            );
            checkBox.setEditable(Boolean.TRUE.equals(folder.getSelectable() && !Boolean.TRUE.equals(folder.getDisabled())));
            checkBox.setFrame(getFrame());
            checkBox.setWidth("20");
            return checkBox;
        });

        foldersTable.addGeneratedColumn("name", folder -> {
            Label<String> label = componentsFactory.create(Label.NAME);
            label.setHtmlEnabled(true);

            if (Boolean.TRUE.equals(folder.getDisabled())) {
                label.setValue("<strike>" + folder.getName() + "</strike>");
            } else if (Boolean.TRUE.equals(folder.getUnregistered())) {
                label.setValue("<span>* " + folder.getName() + "</span>");
            } else {
                label.setValue("<span>" + folder.getName() + "</span>");
            }

            return label;
        });

        makeEventsInfoColumn();
    }

    private void setupEvents() {

        ImapEventType[] eventTypes = ImapEventType.values();
        Map<CheckBox, ImapEventType> eventCheckBoxes = new HashMap<>(eventTypes.length);
        AtomicBoolean eventsChanging = new AtomicBoolean(false);
        editEventsGrid.setRows(eventTypes.length + 1);
        for (int i = 0; i < eventTypes.length; i++) {
            ImapEventType eventType = eventTypes[i];
            String eventName = AppBeans.get(Messages.class).getMessage(eventType);

            Label<String> label = componentsFactory.create(Label.NAME);
            label.setValue(eventName);
            editEventsGrid.add(label, 0, i + 1);

            CheckBox checkBox = componentsFactory.create(CheckBox.class);
            checkBox.setAlignment(Alignment.MIDDLE_CENTER);
            checkBox.setFrame(getFrame());
            checkBox.setDescription(eventName);
            checkBox.setId(eventName + "_chkBox");
            checkBox.addValueChangeListener(e -> {
                if (eventsChanging.get()) {
                    return;
                }

                ImapFolder selectedFolder = foldersTable.getSingleSelected();

                if (selectedFolder == null) {
                    return;
                }

                eventsChanging.set(true);
                toggleEvent(Boolean.TRUE.equals(e.getValue()), selectedFolder, eventType);

                allEventsChkBox.setValue(eventCheckBoxes.keySet().stream().allMatch(CheckBox::isChecked));

                eventsChanging.set(false);
            });
            eventCheckBoxes.put(checkBox, eventType);
            editEventsGrid.add(checkBox, 1, i + 1);
        }

        allEventsChkBox.addValueChangeListener(e -> {
            if (eventsChanging.get()) {
                return;
            }

            ImapFolder selectedFolder = foldersTable.getSingleSelected();

            if (selectedFolder == null) {
                return;
            }

            eventsChanging.set(true);
            eventCheckBoxes.forEach((checkbox, eventType) -> {
                Boolean value = e.getValue();
                checkbox.setValue(value);
                toggleEvent(Boolean.TRUE.equals(e.getValue()), selectedFolder, eventType);
            });

            eventsChanging.set(false);
        });

        foldersDs.addItemChangeListener(e -> {
            ImapFolder folder = e.getItem();
            if (!selectedFolderPanel.isVisible() && folder != null) {
                selectedFolderPanel.setVisible(true);
            }
            if (selectedFolderPanel.isVisible() && (folder == null)) {
                selectedFolderPanel.setVisible(false);
            }

            eventsChanging.set(true);

            if (folder != null) {
                eventCheckBoxes.forEach((checkBox, eventType) -> checkBox.setValue(folder.hasEvent(eventType)));
                allEventsChkBox.setValue(eventCheckBoxes.keySet().stream().allMatch(CheckBox::isChecked));
            }

            eventsChanging.set(false);
        });
    }

    private void toggleEvent(boolean value, ImapFolder imapFolder, ImapEventType eventType) {
        if (value && !imapFolder.hasEvent(eventType)) {
            ImapFolderEvent imapEvent = metadata.create(ImapFolderEvent.class);
            imapEvent.setEvent(eventType);
            imapEvent.setFolder(imapFolder);
            List<ImapFolderEvent> events = imapFolder.getEvents();
            if (events == null) {
                events = new ArrayList<>(ImapEventType.values().length);
                imapFolder.setEvents(events);
            }
            events.add(imapEvent);
            eventsDs.addItem(imapEvent);
            foldersDs.modifyItem(imapFolder);
        } else if (!value && imapFolder.hasEvent(eventType)) {
            ImapFolderEvent event = imapFolder.getEvent(eventType);
            imapFolder.getEvents().remove(event);
            eventsDs.removeItem(event);
            foldersDs.modifyItem(imapFolder);
        }
    }

    private void makeEventsInfoColumn() {
        Map<ImapEventType, BaseAction> imapEventActions = Arrays.stream(ImapEventType.values()).collect(Collectors.toMap(
                Function.identity(),
                eventType -> new BaseAction("event-" + eventType.getId()) {
                    @Override
                    public void actionPerform(Component component) {
                        ImapFolder selectedFolder = foldersTable.getSingleSelected();
                        if (selectedFolder == null) {
                            return;
                        }

                        ImapFolderEvent event = selectedFolder.getEvent(eventType);
                        if (event == null) {
                            return;
                        }
                        eventsDs.setItem(event);
                        if (event.getEventHandlers() == null) {
                            event.setEventHandlers(new ArrayList<>());
                        }
                        List<ImapEventHandler> existingHandlers = new ArrayList<>(event.getEventHandlers());
                        AbstractEditor eventEditor = openEditor(
                                event, WindowManager.OpenType.DIALOG, Collections.emptyMap(), eventsDs
                        );
                        eventEditor.addAfterCloseListener(e -> {
                            for (int i = 0; i < event.getEventHandlers().size(); i++) {
                                ImapEventHandler handler = event.getEventHandlers().get(i);
                                if (handler.getHandlingOrder() == null) {
                                    handlersDs.addItem(handler);
                                } else {
                                    handlersDs.modifyItem(handler);
                                }
                                handler.setHandlingOrder(i);
                            }

                            for (ImapEventHandler handler : existingHandlers) {
                                if (!event.getEventHandlers().contains(handler)) {
                                    handlersDs.removeItem(handler);
                                }
                            }
                        });
                    }
                }
        ));

        for (ImapEventType eventType : ImapEventType.values()) {
            foldersTable.addGeneratedColumn(AppBeans.get(Messages.class).getMessage(eventType), folder -> {
                HBoxLayout hbox = componentsFactory.create(HBoxLayout.class);
                hbox.setWidthFull();
                hbox.setFrame(getFrame());
                if (folder.hasEvent(eventType)) {
                    LinkButton button = componentsFactory.create(LinkButton.class);
                    button.setAction(imapEventActions.get(eventType));
                    button.setCaption("");
                    button.setIcon("icons/gear.png");
                    button.setAlignment(Alignment.MIDDLE_CENTER);
                    hbox.add(button);
                }
                return hbox;
            });
        }

    }

    @Override
    protected void initNewItem(ImapMailBox item) {
        item.setAuthenticationMethod(ImapAuthenticationMethod.SIMPLE);
        item.setAuthentication(metadata.create(ImapSimpleAuthentication.class));
    }

    @Override
    protected void postInit() {
        setCertificateVisibility();
        setTrashFolderControls();
        setEventGeneratorControls();
        setProxyVisibility();
        ImapMailBox mailBox = getItem();
        if (PersistenceHelper.isNew(mailBox)) {
            getComponentNN("foldersPane").setVisible(false);
            setEnableForButtons(false);
        } else {
            checkConnectionBtn.setVisible(false);
            setEnableForButtons(true);
        }
        ((AbstractDatasource) mailBoxDs).setModified(false);
        ((AbstractDatasource) authenticationDs).setModified(false);
    }

    @Override
    protected boolean preCommit() {
        if (!connectionEstablished) {
            notifications.create(Notifications.NotificationType.TRAY)
                    .withCaption(getMessage("saveWithoutConnectionWarning"))
                    .show();
        }
        return connectionEstablished;
    }

    private void setCertificateVisibility() {
        FieldGroup.FieldConfig mailBoxRootCertificateField = this.mainParams.getFieldNN("mailBoxRootCertificateField");
        mailBoxRootCertificateField.setVisible(getItem().getSecureMode() != null);
        mailBoxDs.addItemPropertyChangeListener(event -> {
            if (Objects.equals("secureMode", event.getProperty())) {
                mailBoxRootCertificateField.setVisible(event.getValue() != null);
            }
        });
    }

    private void setTrashFolderControls() {
        FieldGroup.FieldConfig field = this.advancedParams.getFieldNN("trashFolderNameField");
        String trashFolderName = getItem().getTrashFolderName();
        boolean visible = trashFolderName != null;
        log.debug("Set visibility of trash folder controls for {} to {}", getItem(), visible);
        trashFolderPickerField.setRequired(visible);
        if (visible) {
            trashFolderPickerField.setValue(getItem().getFolders().stream()
                    .filter(f -> f.getName().equals(trashFolderName)).findFirst().orElse(null)
            );
        }
        field.setVisible(visible);
        useTrashFolderChkBox.setValue(visible);

        useTrashFolderChkBox.addValueChangeListener(e -> {
            boolean newVisible = Boolean.TRUE.equals(e.getValue());
            log.debug("Set visibility of trash folder controls for {} to {}", getItem(), visible);
            trashFolderPickerField.setRequired(newVisible);
            field.setVisible(newVisible);

            if (!newVisible) {
                getItem().setTrashFolder(null);
                trashFolderPickerField.setValue(null);
            }
        });
    }

    private void setEventGeneratorControls() {
        Map<String, String> availableEventsGenerators = service.getAvailableEventsGenerators();
        FieldGroup.FieldConfig field = this.advancedParams.getFieldNN("customEventsGeneratorClassField");
        String eventsGeneratorClass = getItem().getEventsGeneratorClass();
        boolean visible = eventsGeneratorClass != null
                && availableEventsGenerators.values().stream().anyMatch(clz -> clz.equals(eventsGeneratorClass));
        log.debug("Set visibility of custom event generator controls for {} to {}", getItem(), visible);
        customEventsGeneratorClassLookup.setRequired(visible);
        field.setVisible(visible);
        useCustomEventsGeneratorChkBox.setValue(visible);

        if (eventsGeneratorClass != null && !visible) {
            log.warn("No such bean {} for event generator interface, discard it", eventsGeneratorClass);
            useCustomEventsGeneratorChkBox.setEditable(false);
            useCustomEventsGeneratorChkBox.setEnabled(false);
            getItem().setEventsGeneratorClass(null);
        } else {
            customEventsGeneratorClassLookup.setOptionsMap(availableEventsGenerators);
            useCustomEventsGeneratorChkBox.addValueChangeListener(e -> {
                boolean newVisible = Boolean.TRUE.equals(e.getValue());
                log.debug("Set visibility of custom event generator controls for {} to {}", getItem(), visible);
                customEventsGeneratorClassLookup.setRequired(newVisible);
                field.setVisible(newVisible);

                if (!newVisible) {
                    getItem().setEventsGeneratorClass(null);
                }
            });
        }
    }

    private void setProxyVisibility() {
        FieldGroup.FieldConfig proxyHostField = this.proxyParams.getFieldNN("proxyHostField");
        FieldGroup.FieldConfig proxyPortField = this.proxyParams.getFieldNN("proxyPortField");
        FieldGroup.FieldConfig webProxyChkBox = this.proxyParams.getFieldNN("webProxyChkBox");
        boolean visible = getItem().getProxy() != null;
        log.debug("Set visibility of proxy controls for {} to {}", getItem(), visible);
        proxyHostField.setVisible(visible);
        proxyHostField.setRequired(visible);
        proxyPortField.setVisible(visible);
        proxyPortField.setRequired(visible);
        webProxyChkBox.setVisible(visible);
        useProxyChkBox.setValue(visible);
        proxyParams.setVisible(visible);
        proxyParams.getParent().setVisible(visible);

        useProxyChkBox.addValueChangeListener(e -> {
            boolean newVisible = Boolean.TRUE.equals(e.getValue());
            log.debug("Set visibility of proxy folder controls for {} to {}", getItem(), visible);
            if (!newVisible) {
                getItem().setProxy(null);
            } else {
                getItem().setProxy(metadata.create(ImapProxy.class));
            }
            proxyHostField.setVisible(newVisible);
            proxyHostField.setRequired(newVisible);
            proxyPortField.setVisible(newVisible);
            proxyPortField.setRequired(newVisible);
            webProxyChkBox.setVisible(newVisible);
            proxyParams.setVisible(newVisible);
            proxyParams.getParent().setVisible(newVisible);
        });
    }

    private void refreshFolders(LinkedHashMap<ImapFolder, FolderRefresher.State> foldersWithState) {
        log.debug("refreshed folders from IMAP: {}", foldersWithState);

        ImapMailBox mailBox = getItem();
        List<ImapFolder> folders = mailBox.getFolders();
        foldersWithState.forEach((folder, state) -> {
            switch (state) {
                case NEW:
                    folder.setDisabled(false);
                    folder.setUnregistered(true);
                    break;
                case DELETED:
                    folder.setDisabled(true);
                    folder.setUnregistered(false);
                    break;
                case UNCHANGED:
                    folder.setDisabled(false);
                    folder.setUnregistered(false);
                    break;
            }
        });
        if (folders == null) {
            folders = new ArrayList<>(foldersWithState.keySet());
            mailBox.setFolders(folders);
            for (ImapFolder folder : folders) {
                foldersDs.addItem(folder);
                foldersDs.setItem(folder);
                if (folder.getEvents() != null) {
                    for (ImapFolderEvent event : folder.getEvents()) {
                        eventsDs.addItem(event);
                    }
                }
            }
            foldersDs.setItem(null);
            foldersTable.setSelected((ImapFolder) null);
        } else {
            folders.clear();
            folders.addAll(foldersWithState.keySet());
            foldersWithState.entrySet().stream().filter(e -> e.getValue() == FolderRefresher.State.NEW).forEach(e ->
                    foldersDs.addItem(e.getKey())
            );
            foldersDs.refresh();
        }
        setEnableForButtons(true);
        getComponentNN("foldersPane").setVisible(true);
    }

    private void setEnableForButtons(boolean enable) {
        connectionEstablished = enable;
        trashFolderPickerField.setEnabled(enable);
        ImapMailBox mailBox = getItem();
        boolean supportsFlag = mailBox.getFlagsSupported().equals(Boolean.TRUE);
        cubaFlagTextField.setVisible(supportsFlag);
        cubaFlagTextField.setRequired(supportsFlag);
        if (!supportsFlag) {
            mailBox.setCubaFlag(null);
        }
    }

}
