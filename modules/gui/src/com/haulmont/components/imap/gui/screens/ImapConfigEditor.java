package com.haulmont.components.imap.gui.screens;

import com.haulmont.components.imap.config.AuthenticationMethod;
import com.haulmont.components.imap.config.ImapConfig;
import com.haulmont.components.imap.config.SecureConnectionType;
import com.haulmont.cuba.gui.components.*;

import javax.inject.Inject;
import java.util.Map;

public class ImapConfigEditor extends AbstractWindow {

    @Inject
    private ImapConfig config;

    @Inject
    private TextField imapHostName;

    @Inject
    private TextField imapPort;

    @Inject
    private OptionsGroup imapAuthenticationMethod;

    @Inject
    private CheckBox imapUseSecureConnection;

    @Inject
    private OptionsGroup imapSecureConnectionType;

    @Inject
    private FieldGroup params;

    @Override
    public void init(Map<String, Object> params) {
        imapAuthenticationMethod.setOptionsEnum(AuthenticationMethod.class);
        imapSecureConnectionType.setOptionsEnum(SecureConnectionType.class);

        imapHostName.setValue(config.getHostname());
        imapPort.setValue(config.getPort());
        imapAuthenticationMethod.setValue(config.getAuthenticationMethod());
        imapSecureConnectionType.setValue(config.getSecureConnectionType());
        imapUseSecureConnection.setValue(config.getSecureConnectionType() != null);

        FieldGroup.FieldConfig imapSecureConnectionTypeField = this.params.getFieldNN("imapSecureConnectionTypeField");
        imapSecureConnectionTypeField.setVisible(imapUseSecureConnection.getValue());
        imapUseSecureConnection.addValueChangeListener(e -> {
            boolean visible = Boolean.TRUE.equals(e.getValue());
            imapSecureConnectionTypeField.setVisible(visible);
            if (!visible) {
                imapSecureConnectionType.setValue(null);
                config.setSecureConnectionType(null);
            }
        });

        imapHostName.addValueChangeListener(e -> config.setHostname((String) e.getValue()));
        imapPort.addValueChangeListener(e -> config.setPort((String) e.getValue()));
        imapAuthenticationMethod.addValueChangeListener(e -> config.setAuthenticationMethod((AuthenticationMethod) e.getValue()));
        imapSecureConnectionType.addValueChangeListener(e -> config.setSecureConnectionType((SecureConnectionType) e.getValue()));

    }


}