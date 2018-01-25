alter table MAILCOMPONENT_MAIL_MESSAGE add column VERSION integer ^
update MAILCOMPONENT_MAIL_MESSAGE set VERSION = 0 where VERSION is null ;
alter table MAILCOMPONENT_MAIL_MESSAGE alter column VERSION set not null ;
alter table MAILCOMPONENT_MAIL_MESSAGE add column UPDATE_TS timestamp ;
alter table MAILCOMPONENT_MAIL_MESSAGE add column UPDATED_BY varchar(50) ;
