package com.haulmont.addon.imap.web.imapmailbox;

import com.haulmont.addon.imap.entity.*;
import com.haulmont.addon.imap.exception.ImapException;
import com.haulmont.addon.imap.web.imapmailbox.helper.FolderRefresher;
import com.haulmont.bali.util.ParamsMap;
import com.haulmont.addon.imap.entity.ImapAuthenticationMethod;
import com.haulmont.addon.imap.entity.ImapSimpleAuthentication;
import com.haulmont.cuba.core.global.*;
import com.haulmont.cuba.gui.WindowManager;
import com.haulmont.cuba.gui.components.*;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.cuba.gui.components.actions.BaseAction;
import com.haulmont.cuba.gui.data.CollectionDatasource;
import com.haulmont.cuba.gui.data.Datasource;
import com.haulmont.cuba.gui.data.HierarchicalDatasource;
import com.haulmont.cuba.gui.executors.BackgroundTask;
import com.haulmont.cuba.gui.executors.BackgroundTaskHandler;
import com.haulmont.cuba.gui.executors.BackgroundWorker;
import com.haulmont.cuba.gui.executors.TaskLifeCycle;
import com.haulmont.cuba.gui.xml.layout.ComponentsFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings({"CdiInjectionPointsInspection", "SpringJavaAutowiredFieldsWarningInspection"})
public class ImapMailBoxEdit extends AbstractEditor<ImapMailBox> {

    private final static Logger log = LoggerFactory.getLogger(ImapMailBoxEdit.class);

    @Inject
    private FieldGroup mainParams;

    @Inject
    private FieldGroup pollingParams;

    @Inject
    private FieldGroup proxyParams;

    @Inject
    private Button selectTrashFolderButton;

    @Inject
    private CheckBox useTrashFolderChkBox;

    @Inject
    private CheckBox useProxyChkBox;

    @Inject
    private TreeTable<ImapFolder> foldersTable;

    @Inject
    protected BoxLayout selectedFolderPanel;

    @Inject
    protected ScrollBoxLayout editEventsContainer;

    @Inject
    protected GridLayout editEventsGrid;

    @Inject
    protected CheckBox allEventsChkBox;

    @Inject
    private FolderRefresher folderRefresher;

    @Inject
    private Metadata metadata;

    @Inject
    private Datasource<ImapMailBox> mailBoxDs;

    @Inject
    private HierarchicalDatasource<ImapFolder, UUID> foldersDs;

    @Inject
    private CollectionDatasource<ImapFolderEvent, UUID> eventsDs;

    @Inject
    private BackgroundWorker backgroundWorker;

    @Inject
    private ComponentsFactory componentsFactory;

    private boolean connectionEstablished = false;

    public void checkTheConnection() {
        setEnableForButtons(false);
        try {
            refreshFolders(folderRefresher.refreshFolders(getItem()));
            showNotification(getMessage("connectionSucceed"), NotificationType.HUMANIZED);
        } catch (ImapException e) {
            log.error("Connection Error", e);
            showNotification(getMessage("connectionFailed"), NotificationType.ERROR);
        }
    }

    private void refreshFolders(LinkedHashMap<ImapFolder, FolderRefresher.State> foldersWithState) {
        log.debug("refreshed folders from IMAP: {}", foldersWithState);

        List<ImapFolder> folders = getItem().getFolders();
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
            getItem().setFolders(folders);
        } else {
            folders.clear();
            folders.addAll(foldersWithState.keySet());
        }
        setEnableForButtons(true);
        foldersDs.refresh();
    }

    public void selectTrashFolder() {
        ImapMailBox mailBox = getItem();
        log.debug("Open trash folder window for {}", mailBox);
        openEditor(
                "imapcomponent$ImapMailBox.trashFolder",
                mailBox,
                WindowManager.OpenType.THIS_TAB,
                ParamsMap.of("mailBox", mailBox),
                mailBoxDs
        );
    }

    @Override
    public void init(Map<String, Object> params) {
        setEnableForButtons(false);
        foldersTable.addGeneratedColumn("selected", folder -> {
            CheckBox checkBox = componentsFactory.createComponent(CheckBox.class);
            checkBox.setDatasource(foldersTable.getItemDatasource(folder), "selected");
            checkBox.setEditable(Boolean.TRUE.equals(folder.getSelectable() && !Boolean.TRUE.equals(folder.getDisabled())));
            return checkBox;
        });

        foldersTable.addGeneratedColumn("name", folder -> {
            Label label = componentsFactory.createComponent(Label.class);
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

        setupEvents();
    }

    private void setupEvents() {

        ImapEventType[] eventTypes = ImapEventType.values();
        Map<CheckBox, ImapEventType> eventCheckBoxes = new HashMap<>(eventTypes.length);
        AtomicBoolean eventsChanging = new AtomicBoolean(false);
        editEventsGrid.setRows(eventTypes.length + 1);
        for (int i = 0; i < eventTypes.length; i++) {
            ImapEventType eventType = eventTypes[i];
            String eventName = AppBeans.get(Messages.class).getMessage(eventType);

            Label label = componentsFactory.createComponent(Label.class);
            label.setFrame(getFrame());
            label.setValue(eventName);
            editEventsGrid.add(label, 0, i + 1);

            CheckBox checkBox = componentsFactory.createComponent(CheckBox.class);
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
                if (toggleEvent(Boolean.TRUE.equals(e.getValue()), selectedFolder, eventType)) {
                    //todo: repaint only selected folder column
                    foldersTable.repaint();
                }

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
                Object value = e.getValue();
                checkbox.setValue(value);
                toggleEvent(Boolean.TRUE.equals(e.getValue()), selectedFolder, eventType);
            });
            //todo: repaint only selected folder column
            foldersTable.repaint();

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

    private boolean toggleEvent(boolean value, ImapFolder imapFolder, ImapEventType eventType) {
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
            foldersDs.modifyItem(imapFolder);
            return true;
        } else if (!value && imapFolder.hasEvent(eventType)) {
            ImapFolderEvent event = imapFolder.getEvent(eventType);
            imapFolder.getEvents().remove(event);
            foldersDs.modifyItem(imapFolder);
            return true;
        }

        return false;
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
                        openEditor(event, WindowManager.OpenType.DIALOG, Collections.emptyMap(), eventsDs);
                    }
                }
        ));

        foldersTable.addGeneratedColumn("eventsInfo", folder -> {
            HBoxLayout hbox = componentsFactory.createComponent(HBoxLayout.class);
            hbox.setSpacing(true);
            if (folder.getEvents() != null) {

                folder.getEvents().stream().sorted(Comparator.comparing(ImapFolderEvent::getEvent)).forEach(event -> {
                    Button button = componentsFactory.createComponent(Button.class);
                    button.setCaption(AppBeans.get(Messages.class).getMessage(event.getEvent()));
                    button.setAction(imapEventActions.get(event.getEvent()));
                    hbox.add(button);
                });
            }

            return hbox;
        });
    }

    @Override
    protected void initNewItem(ImapMailBox item) {
        item.setAuthenticationMethod(ImapAuthenticationMethod.SIMPLE);
        item.setPollInterval(10 * 60);
        item.setAuthentication(metadata.create(ImapSimpleAuthentication.class));
    }

    @Override
    protected void postInit() {
        setCertificateVisibility();
        setTrashFolderVisibility();
        setProxyVisibility();
        ImapMailBox mailBox = getItem();
        if (!PersistenceHelper.isNew(mailBox)) {
            BackgroundTaskHandler taskHandler = backgroundWorker.handle(new FoldersRefreshTask(mailBox));
            taskHandler.execute();
        }
        getDsContext().addBeforeCommitListener(context -> {
            List<ImapFolderEvent> allEvents = mailBox.getFolders().stream()
                    .flatMap(f -> f.getEvents() != null ? f.getEvents().stream() : Stream.empty())
                    .collect(Collectors.toList());
            context.getCommitInstances().removeIf(entity ->
                    entity instanceof ImapFolderEvent && !allEvents.contains(entity)
            );
        });
    }

    @Override
    protected boolean preCommit() {
        if (!connectionEstablished) {
            showNotification(getMessage("saveWithoutConnectionWarning"), NotificationType.TRAY);
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

    private void setTrashFolderVisibility() {
        FieldGroup.FieldConfig trashFolderNameField = this.pollingParams.getFieldNN("trashFolderNameField");
        boolean visible = getItem().getTrashFolderName() != null;
        log.debug("Set visibility of trash folder controls for {} to {}", getItem(), visible);
        trashFolderNameField.setVisible(visible);
        selectTrashFolderButton.setVisible(visible);
        useTrashFolderChkBox.setValue(visible);

        useTrashFolderChkBox.addValueChangeListener(e -> {
            boolean newVisible = Boolean.TRUE.equals(e.getValue());
            log.debug("Set visibility of trash folder controls for {} to {}", getItem(), visible);
            trashFolderNameField.setVisible(newVisible);
            selectTrashFolderButton.setVisible(newVisible);

            if (!newVisible) {
                getItem().setTrashFolderName(null);
            }
        });
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

    private void setEnableForButtons(boolean enable) {
        connectionEstablished = enable;
        selectTrashFolderButton.setEnabled(enable);
    }

    private class FoldersRefreshTask extends BackgroundTask<Integer, LinkedHashMap<ImapFolder, FolderRefresher.State>> {

        private ImapMailBox mailBox;

        FoldersRefreshTask(ImapMailBox mailBox) {
            super(0, ImapMailBoxEdit.this);
            this.mailBox = mailBox;
        }

        @Override
        public LinkedHashMap<ImapFolder, FolderRefresher.State> run(TaskLifeCycle<Integer> taskLifeCycle) {
            setEnableForButtons(false);
            try {
                return folderRefresher.refreshFolders(mailBox);
            } catch (ImapException e) {
                log.error("Connection Error", e);
                showNotification(getMessage("connectionFailed"), NotificationType.ERROR);
            }
            return new LinkedHashMap<>();
        }

        @Override
        public void canceled() {
            // Do something in UI thread if the task is canceled
        }

        @Override
        public void done(LinkedHashMap<ImapFolder, FolderRefresher.State> result) {
            log.debug("refreshed folders from IMAP: {}", result);
            refreshFolders(result);
        }

        @Override
        public void progress(List<Integer> changes) {
            // Show current progress in UI thread
        }
    }
}