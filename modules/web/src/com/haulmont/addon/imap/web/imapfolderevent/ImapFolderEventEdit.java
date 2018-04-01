package com.haulmont.addon.imap.web.imapfolderevent;

import com.haulmont.addon.imap.entity.ImapEventType;
import com.haulmont.addon.imap.service.ImapService;
import com.haulmont.cuba.gui.components.*;
import com.haulmont.addon.imap.entity.ImapFolderEvent;
import com.haulmont.cuba.gui.data.Datasource;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.*;

public class ImapFolderEventEdit extends AbstractEditor<ImapFolderEvent> {

    private final static Logger log = LoggerFactory.getLogger(ImapFolderEventEdit.class);

    @Inject
    protected LookupField beanNameField;

    @Inject
    protected LookupField methodNameField;

    @Inject
    protected Label beanNameLabel;

    @Inject
    protected Label methodNameLabel;

    @Inject
    protected Datasource<ImapFolderEvent> imapFolderEventDs;

    @Inject
    protected ImapService service;

    @Inject
    protected BoxLayout methodNameHbox;

    private Map<String, List<String>> availableBeans;

    //List holds an information about methods of selected bean
    protected List<String> availableMethods = new ArrayList<>();

    @Override
    protected void postInit() {
        super.postInit();

        ImapFolderEvent folderEvent = getItem();

        initBeans(folderEvent.getEvent());

        String beanName = folderEvent.getBeanName();
        if (StringUtils.isNotEmpty(beanName)) {
            initMethods(beanName);
        }

        if (StringUtils.isNotEmpty(folderEvent.getMethodName())) {
            setInitialMethodNameValue(folderEvent);
        }

        imapFolderEventDs.addItemPropertyChangeListener(event -> {
            if (Objects.equals("event", event.getProperty())) {
                initBeans(event.getItem().getEvent());
            }
        });

        beanNameField.addValueChangeListener(e -> {
            methodNameField.setValue(null);
            if (e.getValue() == null) {
                methodNameField.setOptionsList(Collections.emptyList());
                methodNameField.setRequired(false);
            } else {
                initMethods(e.getValue().toString());
                methodNameField.setRequired(true);
            }
        });

        methodNameField.addValueChangeListener(e -> {
            String methodName = (e.getValue() != null) ? e.getValue().toString() : null;
            imapFolderEventDs.getItem().setMethodName(methodName);
        });
    }

    private void initBeans(ImapEventType eventType) {
        log.debug("Init beans for event {}", eventType);
        availableBeans = eventType != null
                ? service.getAvailableBeans(eventType.getEventClass()) : Collections.emptyMap();
        beanNameField.setOptionsList(new ArrayList<>(availableBeans.keySet()));
    }

    private void initMethods(String beanName) {
        log.debug("Init methods of bean {} for event {}", beanName, getItem());
        availableMethods = availableBeans.get(beanName);

        if (availableMethods != null) {
            HashMap<String, Object> optionsMap = new HashMap<>();
            for (String availableMethod : availableMethods) {
                optionsMap.put(availableMethod, availableMethod);
            }
            methodNameField.setOptionsMap(optionsMap);
        }
    }

    private void setInitialMethodNameValue(ImapFolderEvent event) {
        log.debug("Set method name {} for event {}", event.getMethodName());
        if (availableMethods == null) {
            return;
        }

        for (String availableMethod : availableMethods) {
            if (event.getMethodName().equals(availableMethod)) {
                methodNameField.setValue(availableMethod);
                break;
            }
        }
    }
}