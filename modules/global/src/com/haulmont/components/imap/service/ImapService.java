package com.haulmont.components.imap.service;

import com.haulmont.components.imap.events.BaseImapEvent;

import java.util.List;
import java.util.Map;

public interface ImapService {
    String NAME = "imapcomponent_ImapService";

    /**
     * Return information about beans and their methods that can be attached to IMAP folder event.
     * @return  map of bean names to lists of their methods
     */
    Map<String, List<String>> getAvailableBeans(Class<? extends BaseImapEvent> eventClass);

}
