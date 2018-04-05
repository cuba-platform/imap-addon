package com.haulmont.addon.imap.service;

import com.haulmont.addon.imap.events.BaseImapEvent;
import com.haulmont.cuba.core.app.AbstractBeansMetadata;
import com.haulmont.cuba.core.app.scheduled.MethodInfo;
import com.haulmont.cuba.core.app.scheduled.MethodParameterInfo;
import com.haulmont.cuba.core.global.AppBeans;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service(ImapService.NAME)
public class ImapServiceBean implements ImapService {

    private final static Logger log = LoggerFactory.getLogger(ImapServiceBean.class);

    private Map<Class<? extends BaseImapEvent>, AbstractBeansMetadata> beanMetas = new HashMap<>();

    @Override
    public Map<String, List<String>> getAvailableBeans(Class<? extends BaseImapEvent> eventClass) {
        log.info("Get available beans to handle {}", eventClass);
        beanMetas.putIfAbsent(eventClass, new AbstractBeansMetadata() {
            @Override
            protected boolean isMethodAvailable(Method method) {
                return method.getParameterTypes().length == 1
                        && method.getParameterTypes()[0].isAssignableFrom(eventClass)
                        && BaseImapEvent.class.isAssignableFrom(method.getParameterTypes()[0])
                        && method.getAnnotation(EventListener.class) == null;
            }

            @Override
            protected List<MethodInfo> getAvailableMethods(String beanName) {
                List<MethodInfo> interfacesMethods = super.getAvailableMethods(beanName);
                List<MethodInfo> methods = new ArrayList<>(interfacesMethods);
                Object bean = AppBeans.get(beanName);

                for (Method method : bean.getClass().getMethods()) {
                    if (!method.getDeclaringClass().equals(Object.class) && isMethodAvailable(method)) {
                        List<MethodParameterInfo> methodParameters = getMethodParameters(method);
                        MethodInfo methodInfo = new MethodInfo(method.getName(), methodParameters);
                        addMethod(methods, methodInfo);
                    }
                }

                return methods;
            }
        });
        Map<String, List<MethodInfo>> availableBeans = beanMetas.get(eventClass).getAvailableBeans();
        log.debug("{} can be handled by {}", eventClass, availableBeans);
        return availableBeans.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream().map(MethodInfo::getName).collect(Collectors.toList())
                ));
    }
}
