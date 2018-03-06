package com.haulmont.components.imap.web.imapfolderevent;

import com.haulmont.components.imap.service.ImapService;
import com.haulmont.cuba.core.entity.Entity;
import com.haulmont.cuba.gui.components.*;
import com.haulmont.components.imap.entity.ImapFolderEvent;
import com.haulmont.cuba.gui.data.Datasource;
import org.apache.commons.lang.StringUtils;

import javax.inject.Inject;
import java.util.*;

public class ImapFolderEventEdit extends AbstractEditor<ImapFolderEvent> {
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
    public void init(Map<String, Object> params) {

        imapFolderEventDs.addItemPropertyChangeListener(event -> {
            if (Objects.equals("event", event.getProperty())) {
                availableBeans = service.getAvailableBeans(event.getItem().getEvent().getEventClass());
                beanNameField.setOptionsList(new ArrayList<>(availableBeans.keySet()));
            }
        });


        beanNameField.addValueChangeListener(e -> {
            methodNameField.setValue(null);
            if (e.getValue() == null) {
                methodNameField.setOptionsList(Collections.emptyList());
                methodNameField.setRequired(false);
            } else {
                availableMethods = availableBeans.get(e.getValue().toString());

                if (availableMethods != null) {
                    HashMap<String, Object> optionsMap = new HashMap<>();
                    for (String availableMethod : availableMethods) {
                        optionsMap.put(availableMethod, availableMethod);
                    }
                    methodNameField.setOptionsMap(optionsMap);
                }
                methodNameField.setRequired(true);
            }
        });

        methodNameField.addValueChangeListener(e -> {
            String methodName = (e.getValue() != null) ? e.getValue().toString() : null;
            imapFolderEventDs.getItem().setMethodName(methodName);
        });

    }

    @Override
    public void setItem(Entity item) {
        super.setItem(item);

        if (StringUtils.isNotEmpty(getItem().getMethodName())) {
            setInitialMethodNameValue(getItem());
        }
    }

    protected void setInitialMethodNameValue(ImapFolderEvent event) {
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