alter table MAILCOMPONENT_MAIL_MESSAGE add constraint FK_MAILCOMPONENT_MAIL_MESSAGE_MAIL_BOX foreign key (MAIL_BOX_ID) references MAILCOMPONENT_MAIL_BOX(ID);
create index IDX_MAILCOMPONENT_MAIL_MESSAGE_MAIL_BOX on MAILCOMPONENT_MAIL_MESSAGE (MAIL_BOX_ID);
