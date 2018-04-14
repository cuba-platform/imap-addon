package com.haulmont.addon.imap.entity;

import com.haulmont.chile.core.annotations.NamePattern;
import com.haulmont.cuba.core.entity.StandardEntity;
import com.haulmont.cuba.core.entity.annotation.OnDeleteInverse;
import com.haulmont.cuba.core.global.DeletePolicy;

import javax.persistence.*;
import javax.validation.constraints.NotNull;

@NamePattern("%s#%s|beanName,methodName")
@Table(name = "IMAPCOMPONENT_IMAP_EVENT_HANDLER")
@Entity(name = "imapcomponent$ImapEventHandler")
public class ImapEventHandler extends StandardEntity {

    @NotNull
    @OnDeleteInverse(DeletePolicy.CASCADE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "EVENT_ID")
    protected ImapFolderEvent event;

    @Column(name = "HANDLING_ORDER", nullable = false)
    private Integer handlingOrder;

    @Column(name = "BEAN_NAME", nullable = false)
    @NotNull
    private String beanName;

    @Column(name = "METHOD_NAME", nullable = false)
    @NotNull
    private String methodName;

    public void setHandlingOrder(Integer handlingORDER) {
        this.handlingOrder = handlingORDER;
    }

    public Integer getHandlingOrder() {
        return handlingOrder;
    }

    public ImapFolderEvent getEvent() {
        return event;
    }

    public void setEvent(ImapFolderEvent event) {
        this.event = event;
    }

    public String getBeanName() {
        return beanName;
    }

    public void setBeanName(String beanName) {
        this.beanName = beanName;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }
}
