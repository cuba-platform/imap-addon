package com.haulmont.components.imap.entity;

import javax.persistence.*;

import com.haulmont.cuba.core.entity.StandardEntity;
import com.haulmont.chile.core.annotations.Composition;
import com.haulmont.cuba.core.entity.annotation.OnDelete;
import com.haulmont.cuba.core.global.DeletePolicy;
import java.util.List;

import com.haulmont.chile.core.annotations.NamePattern;
import com.haulmont.cuba.core.entity.FileDescriptor;
import javax.validation.constraints.NotNull;

@NamePattern("%s:%s|host,port")
@Table(name = "MAILCOMPONENT_MAIL_BOX")
@Entity(name = "mailcomponent$MailBox")
public class MailBox extends StandardEntity {
    private static final long serialVersionUID = -1001337267552497620L;

    @Column(name = "HOST", nullable = false)
    protected String host;

    @Column(name = "PORT", nullable = false)
    protected Integer port = 993;

    @Column(name = "SECURE_MODE")
    protected String secureMode;

    @OnDelete(DeletePolicy.CASCADE)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ROOT_CERTIFICATE_ID")
    protected FileDescriptor rootCertificate;

    @OnDelete(DeletePolicy.CASCADE)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CLIENT_CERTIFICATE_ID")
    protected FileDescriptor clientCertificate;

    @Column(name = "AUTHENTICATION_METHOD", nullable = false)
    protected String authenticationMethod;

    @Composition
    @OnDelete(DeletePolicy.CASCADE)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "AUTHENTICATION_ID")
    protected MailSimpleAuthentication authentication;

    @Column(name = "POLL_INTERVAL", nullable = false)
    protected Integer pollInterval;

    @Column(name = "PROCESSING_TIMEOUT")
    protected Integer processingTimeout = 30;

    @Column(name = "CUBA_FLAG")
    protected String cubaFlag = "cuba-imap";

    @Column(name = "TRASH_FOLDER_NAME")
    protected String trashFolderName;

    @NotNull
    @Column(name = "UPDATE_SLICE_SIZE", nullable = false)
    protected Integer updateSliceSize = 1000;

    @OnDelete(DeletePolicy.CASCADE)
    @Composition
    @OneToMany(mappedBy = "mailBox", cascade = { CascadeType.PERSIST, CascadeType.REMOVE}, orphanRemoval = true)
    protected List<MailFolder> folders;

    @Transient
    private Boolean newEntity = false;

    public void setUpdateSliceSize(Integer updateSliceSize) {
        this.updateSliceSize = updateSliceSize;
    }

    public Integer getUpdateSliceSize() {
        return updateSliceSize;
    }


    public void setProcessingTimeout(Integer processingTimeout) {
        this.processingTimeout = processingTimeout;
    }

    public Integer getProcessingTimeout() {
        return processingTimeout;
    }

    public void setCubaFlag(String cubaFlag) {
        this.cubaFlag = cubaFlag;
    }

    public String getCubaFlag() {
        return cubaFlag;
    }

    public void setTrashFolderName(String trashFolderName) {
        this.trashFolderName = trashFolderName;
    }

    public String getTrashFolderName() {
        return trashFolderName;
    }


    public MailAuthenticationMethod getAuthenticationMethod() {
        return authenticationMethod == null ? null : MailAuthenticationMethod.fromId(authenticationMethod);
    }

    public void setAuthenticationMethod(MailAuthenticationMethod authenticationMethod) {
        this.authenticationMethod = authenticationMethod == null ? null : authenticationMethod.getId();
    }


    public MailSecureMode getSecureMode() {
        return secureMode == null ? null : MailSecureMode.fromId(secureMode);
    }

    public void setSecureMode(MailSecureMode secureMode) {
        this.secureMode = secureMode == null ? null : secureMode.getId();
    }


    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }


    public void setRootCertificate(FileDescriptor rootCertificate) {
        this.rootCertificate = rootCertificate;
    }

    public FileDescriptor getRootCertificate() {
        return rootCertificate;
    }

    public void setClientCertificate(FileDescriptor clientCertificate) {
        this.clientCertificate = clientCertificate;
    }

    public FileDescriptor getClientCertificate() {
        return clientCertificate;
    }


    public void setAuthentication(MailSimpleAuthentication authentication) {
        this.authentication = authentication;
    }

    public MailSimpleAuthentication getAuthentication() {
        return authentication;
    }




    public void setHost(String host) {
        this.host = host;
    }

    public String getHost() {
        return host;
    }

    public void setPollInterval(Integer pollInterval) {
        this.pollInterval = pollInterval;
    }

    public Integer getPollInterval() {
        return pollInterval;
    }

    public void setFolders(List<MailFolder> folders) {
        this.folders = folders;
    }

    public List<MailFolder> getFolders() {
        return folders;
    }

    public Boolean getNewEntity() {
        return newEntity;
    }

    public void setNewEntity(Boolean newEntity) {
        this.newEntity = newEntity;
    }
}