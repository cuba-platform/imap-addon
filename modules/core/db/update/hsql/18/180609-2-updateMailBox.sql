alter table IMAP_MAIL_BOX add column NAME varchar(255) ^
update IMAP_MAIL_BOX set NAME = '' where NAME is null ;
alter table IMAP_MAIL_BOX alter column NAME set not null ;
