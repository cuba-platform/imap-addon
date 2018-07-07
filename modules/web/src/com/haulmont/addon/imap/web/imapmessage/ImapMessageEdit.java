package com.haulmont.addon.imap.web.imapmessage;

import com.haulmont.addon.imap.dto.ImapMessageDto;
import com.haulmont.addon.imap.entity.ImapMessageAttachment;
import com.haulmont.addon.imap.service.ImapAPIService;
import com.haulmont.cuba.core.entity.Entity;
import com.haulmont.cuba.core.global.FileTypesHelper;
import com.haulmont.cuba.gui.components.AbstractEditor;
import com.haulmont.addon.imap.entity.ImapMessage;
import com.haulmont.cuba.gui.components.Label;
import com.haulmont.cuba.gui.components.Table;
import com.haulmont.cuba.gui.data.CollectionDatasource;
import com.haulmont.cuba.gui.data.Datasource;
import com.haulmont.cuba.gui.executors.BackgroundTask;
import com.haulmont.cuba.gui.executors.BackgroundTaskHandler;
import com.haulmont.cuba.gui.executors.BackgroundWorker;
import com.haulmont.cuba.gui.executors.TaskLifeCycle;
import com.haulmont.cuba.web.AppUI;
import com.haulmont.cuba.web.toolkit.ui.CubaFileDownloader;
import com.vaadin.server.StreamResource;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("CdiInjectionPointsInspection")
public class ImapMessageEdit extends AbstractEditor<ImapMessage> {

    @Inject
    private ImapAPIService imapAPI;
    @Inject
    private Table<ImapMessageAttachment> attachmentsTable;
    @Inject
    private CollectionDatasource<ImapMessageAttachment, UUID> imapDemoAttachmentsDs;
    @Inject
    private Datasource<ImapMessageDto> imapMessageDtoDs;
    @Inject
    private Label bodyContent;
    @Inject
    private BackgroundWorker backgroundWorker;

    @Override
    public void setItem(Entity item) {
        super.setItem(item);

        ImapMessage imapMessage = (ImapMessage) item;

        initBody(imapMessage);
        initAttachments(imapMessage);
    }

    public void downloadAttachment() {
        attachmentsTable.getSelected().forEach(attachment -> {
            CubaFileDownloader fileDownloader = AppUI.getCurrent().getFileDownloader();

            StreamResource resource = new StreamResource(
                    () -> new ByteArrayInputStream(imapAPI.loadFile(attachment)), attachment.getName());

            resource.setMIMEType(FileTypesHelper.getMIMEType(attachment.getName()));
            fileDownloader.downloadFile(resource);

        });
    }

    private void initBody(ImapMessage msg) {
        BackgroundTaskHandler taskHandler = backgroundWorker.handle(new InitBodyTask(msg));
        taskHandler.execute();
    }

    private void initAttachments(ImapMessage msg) {
        if (!Boolean.TRUE.equals(msg.getAttachmentsLoaded())) {
            BackgroundTaskHandler taskHandler = backgroundWorker.handle(new InitAttachmentTask(msg));
            taskHandler.execute();
        }
    }

    private class InitBodyTask extends BackgroundTask<Integer, ImapMessageDto> {
        private final ImapMessage message;

        InitBodyTask(ImapMessage message) {
            super(0, ImapMessageEdit.this);
            this.message = message;
        }

        @Override
        public ImapMessageDto run(TaskLifeCycle<Integer> taskLifeCycle) {
            return imapAPI.fetchMessage(message);
        }

        @Override
        public void canceled() {
            // Do something in UI thread if the task is canceled
        }

        @Override
        public void done(ImapMessageDto dto) {
            imapMessageDtoDs.setItem(dto);
            bodyContent.setHtmlEnabled(dto.getHtml());
            bodyContent.setValue(dto.getBody());
        }

        @Override
        public void progress(List<Integer> changes) {
            // Show current progress in UI thread
        }
    }

    private class InitAttachmentTask extends BackgroundTask<Integer, Void> {
        private final ImapMessage msg;

        InitAttachmentTask(ImapMessage msg) {
            super(0, ImapMessageEdit.this);
            this.msg = msg;
        }

        @Override
        public Void run(TaskLifeCycle<Integer> taskLifeCycle) {
            imapAPI.fetchAttachments(msg);
            return null;
        }

        @Override
        public void canceled() {
            // Do something in UI thread if the task is canceled
        }

        @Override
        public void done(Void ignore) {
            imapDemoAttachmentsDs.refresh();
        }

        @Override
        public void progress(List<Integer> changes) {
            // Show current progress in UI thread
        }
    }
}