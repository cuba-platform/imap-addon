package com.haulmont.addon.imap.service;

import com.haulmont.addon.imap.events.BaseImapEvent;
import com.haulmont.cuba.core.app.AbstractBeansMetadata;
import com.haulmont.cuba.core.app.scheduled.MethodInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service(ImapService.NAME)
public class ImapServiceBean implements ImapService {

    private final static Logger LOG = LoggerFactory.getLogger(ImapServiceBean.class);

    private Map<Class<? extends BaseImapEvent>, AbstractBeansMetadata> beanMetas = new HashMap<>();

    @Override
    public Map<String, List<String>> getAvailableBeans(Class<? extends BaseImapEvent> eventClass) {
        LOG.info("Get available beans to handle {}", eventClass);
        beanMetas.putIfAbsent(eventClass, new AbstractBeansMetadata() {
            @Override
            protected boolean isMethodAvailable(Method method) {
                return method.getParameterTypes().length == 1
                        && method.getParameterTypes()[0].isAssignableFrom(eventClass)
                        && BaseImapEvent.class.isAssignableFrom(method.getParameterTypes()[0]);
            }
        });
        Map<String, List<MethodInfo>> availableBeans = beanMetas.get(eventClass).getAvailableBeans();
        LOG.debug("{} can be handled by {}", eventClass, availableBeans);
        return availableBeans.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream().map(MethodInfo::getName).collect(Collectors.toList())
                ));
    }
}
