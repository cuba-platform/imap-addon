package com.haulmont.components.imap.web.mailfolder;

import com.haulmont.components.imap.entity.MailFolder;
import com.haulmont.cuba.gui.components.AbstractEditor;
import com.haulmont.components.imap.entity.MailFolder;

public class MailFolderEdit extends AbstractEditor<MailFolder> {
    @Override
    protected void postInit() {
        super.postInit();
    }

    @Override
    protected boolean preCommit() {
        getDsContext().addBeforeCommitListener(context -> context.getCommitInstances().add(getItem()));
        return super.preCommit();
    }
}