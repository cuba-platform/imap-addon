package com.haulmont.addon.imap.service;

import com.haulmont.addon.imap.events.BaseImapEvent;

import java.util.List;
import java.util.Map;

public interface ImapService {
    String NAME = "imap_ImapService";

    /**
     * Return information about beans and their methods that can be attached to IMAP folder event.
     * @return  map of bean names to lists of their methods
     */
    Map<String, List<String>> getAvailableBeans(Class<? extends BaseImapEvent> eventClass);

    /**
     * Return bean names and class names of custom IMAP Event Generators
     * @return  map of bean name to class name
     */
    Map<String, String> getAvailableEventsGenerators();

}
