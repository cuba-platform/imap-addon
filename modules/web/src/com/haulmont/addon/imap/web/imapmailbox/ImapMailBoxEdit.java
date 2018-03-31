package com.haulmont.addon.imap.web.imapmailbox;

import com.haulmont.addon.imap.entity.*;
import com.haulmont.addon.imap.web.imapmailbox.helper.FolderRefresher;
import com.haulmont.bali.util.ParamsMap;
import com.haulmont.addon.imap.entity.ImapAuthenticationMethod;
import com.haulmont.addon.imap.entity.ImapSimpleAuthentication;
import com.haulmont.cuba.core.global.*;
import com.haulmont.cuba.gui.WindowManager;
import com.haulmont.cuba.gui.components.*;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.cuba.gui.components.actions.BaseAction;
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
import javax.mail.MessagingException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private BackgroundWorker backgroundWorker;

    @Inject
    private ComponentsFactory componentsFactory;

    public void checkTheConnection() {
        try {
            boolean refresh = folderRefresher.refreshFolders(getItem());
            log.debug("refreshed folders from IMAP, need to refresh datasource - {}", refresh);
            if (refresh) {
                foldersDs.refresh();
//                foldersTable.repaint();
            }
            showNotification("Connection succeed", NotificationType.HUMANIZED);
        } catch (MessagingException e) {
            log.error("Connection Error", e);
            showNotification("Connection failed", NotificationType.ERROR);
        }
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
        foldersTable.addGeneratedColumn("selected", folder -> {
            CheckBox checkBox = componentsFactory.createComponent(CheckBox.class);
            checkBox.setDatasource(foldersTable.getItemDatasource(folder), "selected");
            checkBox.setEditable(Boolean.TRUE.equals(folder.getSelectable() && !Boolean.TRUE.equals(folder.getDisabled())));
            return checkBox;
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
                imapFolder.setEvents(events = new ArrayList<>(ImapEventType.values().length));
            }
            events.add(imapEvent);
            foldersDs.modifyItem(imapFolder);
            return true;
        } else if (!value && imapFolder.hasEvent(eventType)) {
            imapFolder.getEvents().remove(imapFolder.getEvent(eventType));
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
                        openEditor(event, WindowManager.OpenType.DIALOG);
                    }
                }
        ));

        foldersTable.addGeneratedColumn("eventsInfo", folder -> {
            HBoxLayout hbox = componentsFactory.createComponent(HBoxLayout.class);
            if (folder.getEvents() != null) {

                for (ImapFolderEvent event : folder.getEvents()) {
                    Button button = componentsFactory.createComponent(Button.class);
                    button.setCaption(AppBeans.get(Messages.class).getMessage(event.getEvent()));
                    button.setAction(imapEventActions.get(event.getEvent()));
                    hbox.add(button);
                }
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
        if (!PersistenceHelper.isNew(getItem())) {
            BackgroundTaskHandler taskHandler = backgroundWorker.handle(new FoldersRefreshTask());
            taskHandler.execute();
        }
        addCloseWithCommitListener(() -> {
            ImapMailBox mailBox = getItem();
            System.out.println(mailBox);
        });
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

    private class FoldersRefreshTask extends BackgroundTask<Integer, Boolean> {

        public FoldersRefreshTask() {
            super(0, ImapMailBoxEdit.this);
        }

        @Override
        public Boolean run(TaskLifeCycle<Integer> taskLifeCycle) {
            try {
                return folderRefresher.refreshFolders(getItem());
            } catch (MessagingException e) {
                throw new RuntimeException("Can't refresh folders", e);
            }
        }

        @Override
        public void canceled() {
            // Do something in UI thread if the task is canceled
        }

        @Override
        public void done(Boolean refresh) {
            log.debug("refreshed folders from IMAP, need to refresh datasource - {}", refresh);
            if (refresh) {
                foldersDs.refresh();
                foldersTable.repaint();
            }
        }

        @Override
        public void progress(List<Integer> changes) {
            // Show current progress in UI thread
        }
    }
}