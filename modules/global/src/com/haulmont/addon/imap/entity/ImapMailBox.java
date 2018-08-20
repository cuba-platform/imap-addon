package com.haulmont.addon.imap.entity;

import javax.persistence.*;

import com.haulmont.chile.core.annotations.MetaProperty;
import com.haulmont.cuba.core.entity.StandardEntity;
import com.haulmont.chile.core.annotations.Composition;
import com.haulmont.cuba.core.entity.annotation.OnDelete;
import com.haulmont.cuba.core.global.DeletePolicy;
import java.util.List;
import java.util.stream.Collectors;

import com.haulmont.chile.core.annotations.NamePattern;
import com.haulmont.cuba.core.entity.FileDescriptor;
import com.haulmont.cuba.core.entity.annotation.Listeners;
import javax.validation.constraints.NotNull;

@Listeners({"imap_MailboxListener"})
@NamePattern("%s:%s (%s)|host,port,name")
@Table(name = "IMAP_MAIL_BOX")
@Entity(name = "imap$MailBox")
public class ImapMailBox extends StandardEntity {
    private static final long serialVersionUID = -1001337267552497620L;

    @NotNull
    @Column(name = "NAME", nullable = false)
    protected String name;

    @Column(name = "HOST", nullable = false)
    private String host;

    @Column(name = "PORT", nullable = false)
    private Integer port = 143;

    @Column(name = "SECURE_MODE")
    private String secureMode;

    @OnDelete(DeletePolicy.CASCADE)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ROOT_CERTIFICATE_ID")
    private FileDescriptor rootCertificate;

    @OnDelete(DeletePolicy.CASCADE)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CLIENT_CERTIFICATE_ID")
    private FileDescriptor clientCertificate;

    @Column(name = "AUTHENTICATION_METHOD", nullable = false)
    private String authenticationMethod;

    @Composition
    @OnDelete(DeletePolicy.CASCADE)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "AUTHENTICATION_ID")
    private ImapSimpleAuthentication authentication;

    @OnDelete(DeletePolicy.CASCADE)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PROXY_ID")
    private ImapProxy proxy;

    @Column(name = "CUBA_FLAG")
    private String cubaFlag = "cuba-imap";

    @Column(name = "TRASH_FOLDER_NAME")
    private String trashFolderName;

    @Transient
    @MetaProperty
    private ImapFolder trashFolder;

    @OnDelete(DeletePolicy.CASCADE)
    @Composition
    @OneToMany(mappedBy = "mailBox")
    private List<ImapFolder> folders;

    @Column(name = "EVENTS_GENERATOR_CLASS")
    private String eventsGeneratorClass;

    @NotNull
    @Column(name = "FLAGS_SUPPORTED", nullable = false)
    private Boolean flagsSupported = false;

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }


    public void setEventsGeneratorClass(String eventsGeneratorClass) {
        this.eventsGeneratorClass = eventsGeneratorClass;
    }

    public String getEventsGeneratorClass() {
        return eventsGeneratorClass;
    }

    public Boolean getFlagsSupported() {
        return flagsSupported;
    }

    public void setFlagsSupported(Boolean flagsSupported) {
        this.flagsSupported = flagsSupported;
    }

    public void setProxy(ImapProxy proxy) {
        this.proxy = proxy;
    }

    public ImapProxy getProxy() {
        return proxy;
    }


    public List<ImapFolder> getFolders() {
        return folders;
    }

    public List<ImapFolder> getProcessableFolders() {
        return folders.stream()
                .filter(f -> Boolean.TRUE.equals(f.getSelected()) && !Boolean.TRUE.equals(f.getDisabled()))
                .collect(Collectors.toList());
    }

    public void setFolders(List<ImapFolder> folders) {
        this.folders = folders;
    }

    public ImapSimpleAuthentication getAuthentication() {
        return authentication;
    }

    public void setAuthentication(ImapSimpleAuthentication authentication) {
        this.authentication = authentication;
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

    public ImapFolder getTrashFolder() {
        return trashFolder;
    }

    public void setTrashFolder(ImapFolder trashFolder) {
        this.trashFolder = trashFolder;
        this.trashFolderName = trashFolder != null ? trashFolder.getName() : null;
    }

    public ImapAuthenticationMethod getAuthenticationMethod() {
        return authenticationMethod == null ? null : ImapAuthenticationMethod.fromId(authenticationMethod);
    }

    public void setAuthenticationMethod(ImapAuthenticationMethod authenticationMethod) {
        this.authenticationMethod = authenticationMethod == null ? null : authenticationMethod.getId();
    }

    public ImapSecureMode getSecureMode() {
        return secureMode == null ? null : ImapSecureMode.fromId(secureMode);
    }

    public void setSecureMode(ImapSecureMode secureMode) {
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

    public void setHost(String host) {
        this.host = host;
    }

    public String getHost() {
        return host;
    }

}