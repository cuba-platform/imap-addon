package com.haulmon.components.imap.web.demo;

import com.haulmon.components.imap.dto.MailMessageDto;
import com.haulmon.components.imap.entity.MailMessage;
import com.haulmon.components.imap.service.ImapService;
import com.haulmont.cuba.core.global.CommitContext;
import com.haulmont.cuba.core.global.DataManager;
import com.haulmont.cuba.core.global.Events;
import com.haulmont.cuba.core.global.LoadContext;
import com.haulmont.cuba.gui.components.AbstractWindow;
import com.haulmont.cuba.gui.events.UiEvent;
import com.haulmont.cuba.gui.executors.*;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.EventListener;

import javax.inject.Inject;
import javax.mail.MessagingException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Demo extends AbstractWindow {

    @Inject
    private ImapService service;

    @Inject
    private BackgroundWorker backgroundWorker;

    @Inject
    private DataManager dm;

    @Inject
    private Events events;

    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    @EventListener
    protected void repeat(Event event) {
        showNewMessage();
    }

    @Override
    public void init(Map<String, Object> params) {
        showNewMessage();
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
                executorService.submit(() -> {
                    try {
                        TimeUnit.SECONDS.sleep(5);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    events.publish(new Event(Demo.this));
                });
            }

            @Override
            public void progress(List<Integer> changes) {
                // Show current progress in UI thread
            }
        };
    }

    public static class Event extends ApplicationEvent implements UiEvent {
        public Event(Object source) {
            super(source);
        }
    }
}