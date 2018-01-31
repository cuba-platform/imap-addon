-- alter table MAILCOMPONENT_MAIL_EVENT_TYPE add column EVENT_TYPE varchar(50) ^
-- update MAILCOMPONENT_MAIL_EVENT_TYPE set EVENT_TYPE = <default_value> ;
-- alter table MAILCOMPONENT_MAIL_EVENT_TYPE alter column EVENT_TYPE set not null ;
alter table MAILCOMPONENT_MAIL_EVENT_TYPE add column EVENT_TYPE varchar(50) ;
alter table MAILCOMPONENT_MAIL_EVENT_TYPE drop column NAME cascade ;
alter table MAILCOMPONENT_MAIL_EVENT_TYPE drop column DESCRIPTION cascade ;
