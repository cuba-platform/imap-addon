package com.haulmont.components.imap.service;

import com.haulmont.components.imap.events.BaseImapEvent;
import com.haulmont.cuba.core.app.AbstractBeansMetadata;
import com.haulmont.cuba.core.app.scheduled.MethodInfo;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service(ImapService.NAME)
public class ImapServiceBean implements ImapService {

    private Map<Class<? extends BaseImapEvent>, AbstractBeansMetadata> beanMetas = new HashMap<>();

    @Override
    public Map<String, List<String>> getAvailableBeans(Class<? extends BaseImapEvent> eventClass) {
        beanMetas.putIfAbsent(eventClass, new AbstractBeansMetadata() {
            @Override
            protected boolean isMethodAvailable(Method method) {
                return method.getParameterTypes().length == 1
                        && method.getParameterTypes()[0].isAssignableFrom(eventClass)
                        && BaseImapEvent.class.isAssignableFrom(method.getParameterTypes()[0]);
            }
        });
        return beanMetas.get(eventClass).getAvailableBeans().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream().map(MethodInfo::getName).collect(Collectors.toList())
                ));
    }
}
