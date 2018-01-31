alter table MAILCOMPONENT_MAIL_MESSAGE add column FROM_ varchar(255) ;
alter table MAILCOMPONENT_MAIL_MESSAGE add column TO_LIST varchar(255) ;
alter table MAILCOMPONENT_MAIL_MESSAGE add column CC_LIST varchar(255) ;
alter table MAILCOMPONENT_MAIL_MESSAGE add column BCC_LIST varchar(255) ;
alter table MAILCOMPONENT_MAIL_MESSAGE add column SUBJECT varchar(255) ;
alter table MAILCOMPONENT_MAIL_MESSAGE add column FLAGS_LIST varchar(255) ;
alter table MAILCOMPONENT_MAIL_MESSAGE add column DATE_ timestamp ;
