alter table disclosure_company rename column corp_code to dart_corp_code;
alter table disclosure_company rename column corp_name to company_name;
alter table disclosure_company rename column corp_cls to dart_corp_cls;
alter table disclosure_company rename constraint uq_disclosure_company_corp_code
    to uq_disclosure_company_dart_corp_code;
alter table disclosure_company add column closing_month integer not null default 12
    check (closing_month between 1 and 12);

alter table disclosure_filing rename column rcept_no to dart_rcept_no;
alter table disclosure_filing rename column report_nm to report_title;
alter table disclosure_filing rename column flr_nm to dart_submitter_name;
alter table disclosure_filing rename column rcept_dt to received_date;
alter table disclosure_filing rename column rm to dart_remarks;
alter table disclosure_filing rename column business_year to fiscal_year;
alter table disclosure_filing rename column report_code to dart_report_code;
alter table disclosure_filing rename constraint uq_disclosure_filing_rcept_no
    to uq_disclosure_filing_dart_rcept_no;

alter table disclosure_financial_metric rename column account_id to dart_account_id;
alter table disclosure_financial_metric rename column account_nm to account_name;
alter table disclosure_financial_metric drop constraint uq_disclosure_financial_metric;
alter table disclosure_financial_metric add constraint uq_disclosure_financial_metric
    unique nulls not distinct (filing_id, metric_type, basis, period_type, dart_account_id);
