package com.haulmont.components.imap.web.demo;

import com.haulmont.components.imap.dto.MailMessageDto;
import com.haulmont.components.imap.entity.MailMessage;
import com.haulmont.components.imap.service.ImapService;
import com.haulmont.components.imap.dto.MailMessageDto;
import com.haulmont.components.imap.entity.MailMessage;
import com.haulmont.cuba.core.global.CommitContext;
import com.haulmont.cuba.core.global.DataManager;
import com.haulmont.cuba.core.global.Events;
import com.haulmont.cuba.core.global.LoadContext;
import com.haulmont.cuba.gui.components.AbstractWindow;
import com.haulmont.cuba.gui.components.Timer;
import com.haulmont.cuba.gui.executors.*;
import com.haulmont.cuba.gui.xml.layout.ComponentsFactory;

import javax.inject.Inject;
import javax.mail.MessagingException;
import java.util.List;
import java.util.Map;

public class Demo extends AbstractWindow {

    @Inject
    private ImapService service;

    @Inject
    private BackgroundWorker backgroundWorker;

    @Inject
    private DataManager dm;

    @Inject
    private ComponentsFactory componentsFactory;

    @Override
    public void init(Map<String, Object> params) {
        Timer timer = componentsFactory.createTimer();

        addTimer(timer);

        timer.setDelay(10000);
        timer.setRepeating(true);

        timer.addActionListener(_timer -> showNewMessage());

        timer.start();
    }

    private void showNewMessage() {
        BackgroundTaskHandler taskHandler = backgroundWorker.handle(task());
        taskHandler.execute();
    }

    private BackgroundTask<Integer, Void> task() {
        UIAccessor uiAccessor = backgroundWorker.getUIAccessor();

        return new BackgroundTask<Integer, Void>(10, this) {
            @Override
            public Void run(TaskLifeCycle<Integer> taskLifeCycle) {
                MailMessage newMessage = dm.load(LoadContext.create(MailMessage.class).setQuery(
                        LoadContext.createQuery("select m from mailcomponent$MailMessage m where m.seen is null or m.seen = false").setMaxResults(1))
                        .setView("mailMessage-full"));
                if (newMessage != null) {
                    newMessage.setSeen(true);
                    dm.commit(new CommitContext(newMessage));
                    uiAccessor.access(() -> {
                        try {
                            MailMessageDto mailMessageDto = service.fetchMessage(newMessage);
                            showNotification("New message arrived", mailMessageDto.toString(), NotificationType.HUMANIZED);
                        } catch (MessagingException e) {
                            showNotification(e.getMessage(), NotificationType.ERROR);
                        }
                    });
                }

                return null;
            }

            @Override
            public void canceled() {
                // Do something in UI thread if the task is canceled
            }

            @Override
            public void done(Void result) {
            }

            @Override
            public void progress(List<Integer> changes) {
                // Show current progress in UI thread
            }
        };
    }

}