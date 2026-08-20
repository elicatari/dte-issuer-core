create role dte_app login password 'app-secret' nosuperuser nocreatedb nocreaterole nobypassrls;
grant connect on database dte_issuer to dte_app;
grant usage on schema public to dte_app;