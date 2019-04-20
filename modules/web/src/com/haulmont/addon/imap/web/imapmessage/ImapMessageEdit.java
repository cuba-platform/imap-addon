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

package com.haulmont.addon.imap.web.imapmessage;

import com.haulmont.addon.imap.dto.ImapMessageDto;
import com.haulmont.addon.imap.entity.ImapMessage;
import com.haulmont.addon.imap.entity.ImapMessageAttachment;
import com.haulmont.addon.imap.service.ImapAPIService;
import com.haulmont.cuba.core.entity.Entity;
import com.haulmont.cuba.core.global.FileTypesHelper;
import com.haulmont.cuba.gui.components.*;
import com.haulmont.cuba.gui.data.CollectionDatasource;
import com.haulmont.cuba.gui.data.Datasource;
import com.haulmont.cuba.gui.executors.BackgroundTask;
import com.haulmont.cuba.gui.executors.BackgroundTaskHandler;
import com.haulmont.cuba.gui.executors.BackgroundWorker;
import com.haulmont.cuba.gui.executors.TaskLifeCycle;
import com.haulmont.cuba.web.AppUI;
import com.haulmont.cuba.web.widgets.CubaFileDownloader;
import com.vaadin.server.StreamResource;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

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
    private ScrollBoxLayout bodyContentScroll;
    @Inject
    private BrowserFrame bodyContentHtml;
    @Inject
    private ProgressBar progressBar;
    @Inject
    private Button downloadBtn;
    @Inject
    private TabSheet tabSheet;
    @Inject
    private BackgroundWorker backgroundWorker;

    @Override
    public void setItem(Entity item) {
        super.setItem(item);

        ImapMessage imapMessage = (ImapMessage) item;

        AtomicInteger loadProgress = new AtomicInteger(0);
        initBody(imapMessage, loadProgress);
        initAttachments(imapMessage, loadProgress);
    }

    @Override
    protected void postInit() {
        tabSheet.addSelectedTabChangeListener(event -> downloadBtn.setEnabled(imapDemoAttachmentsDs.size() > 0));
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

    private void initBody(ImapMessage msg, AtomicInteger loadProgress) {
        BackgroundTaskHandler taskHandler = backgroundWorker.handle(new InitBodyTask(msg, loadProgress));
        taskHandler.execute();
    }

    private void initAttachments(ImapMessage msg, AtomicInteger loadProgress) {
        if (!Boolean.TRUE.equals(msg.getAttachmentsLoaded())) {
            BackgroundTaskHandler taskHandler = backgroundWorker.handle(new InitAttachmentTask(msg, loadProgress));
            taskHandler.execute();
        } else {
            hideProgressBar(loadProgress);
        }
    }

    private void hideProgressBar(AtomicInteger loadProgress) {
        if (loadProgress.incrementAndGet() == 2) {
            progressBar.setVisible(false);
        }
    }

    private class InitBodyTask extends BackgroundTask<Integer, ImapMessageDto> {
        private final ImapMessage message;
        private final AtomicInteger loadProgress;

        InitBodyTask(ImapMessage message, AtomicInteger loadProgress) {
            super(0, ImapMessageEdit.this);
            this.message = message;
            this.loadProgress = loadProgress;
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
            if (Boolean.TRUE.equals(dto.getHtml()) ) {
                byte[] bytes = dto.getBody().getBytes(StandardCharsets.UTF_8);
                bodyContentHtml.setSource(com.haulmont.cuba.gui.components.StreamResource.class)
                        .setStreamSupplier(() -> new ByteArrayInputStream(bytes))
                        .setMimeType("text/html");
                bodyContent.setHtmlEnabled(true);
                bodyContent.setValue(dto.getBody());
                bodyContentHtml.setVisible(true);
            } else {
                bodyContent.setValue(dto.getBody());
                bodyContentScroll.setVisible(true);
            }
            hideProgressBar(loadProgress);
        }

        @Override
        public void progress(List<Integer> changes) {
            // Show current progress in UI thread
        }
    }

    private class InitAttachmentTask extends BackgroundTask<Integer, Integer> {
        private final ImapMessage msg;
        private final AtomicInteger loadProgress;

        InitAttachmentTask(ImapMessage msg, AtomicInteger loadProgress) {
            super(0, ImapMessageEdit.this);
            this.msg = msg;
            this.loadProgress = loadProgress;
        }

        @Override
        public Integer run(TaskLifeCycle<Integer> taskLifeCycle) {
            return imapAPI.fetchAttachments(msg).size();
        }

        @Override
        public void canceled() {
            // Do something in UI thread if the task is canceled
        }

        @Override
        public void done(Integer attachmentCount) {
            imapDemoAttachmentsDs.refresh();
            hideProgressBar(loadProgress);
        }

        @Override
        public void progress(List<Integer> changes) {
            // Show current progress in UI thread
        }
    }
}