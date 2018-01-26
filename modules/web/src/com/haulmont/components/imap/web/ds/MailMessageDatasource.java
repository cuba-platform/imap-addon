package com.haulmont.components.imap.web.ds;

import com.haulmont.components.imap.dto.MailMessageDto;
import com.haulmont.components.imap.entity.MailMessage;
import com.haulmont.components.imap.service.ImapService;
import com.haulmont.cuba.core.global.AppBeans;
import com.haulmont.cuba.core.global.DataManager;
import com.haulmont.cuba.core.global.LoadContext;
import com.haulmont.cuba.gui.data.impl.CustomCollectionDatasource;

import javax.mail.MessagingException;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public class MailMessageDatasource extends CustomCollectionDatasource<MailMessageDto, UUID> {

    private ImapService service = AppBeans.get(ImapService.class);

    private DataManager dm = AppBeans.get(DataManager.class);

    @Override
    protected Collection<MailMessageDto> getEntities(Map<String, Object> params) {
        try {
            return service.fetchMessages(dm.loadList(LoadContext.create(MailMessage.class).setQuery(
                    LoadContext.createQuery("select m from mailcomponent$MailMessage m where m.seen = true order by m.seenTime desc").setMaxResults(20))
                    .setView("mailMessage-full"))
            );
        } catch (MessagingException e) {
            throw new RuntimeException("fetch messages exception", e);
        }
    }

}