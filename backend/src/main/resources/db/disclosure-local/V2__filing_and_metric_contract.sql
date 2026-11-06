alter table disclosure_filing add column business_year integer;
alter table disclosure_filing add column report_code varchar(5);
alter table disclosure_financial_metric rename column statement_type to basis;
alter table disclosure_financial_metric drop constraint uq_disclosure_financial_metric;
alter table disclosure_financial_metric add constraint uq_disclosure_financial_metric
    unique (filing_id, metric_type, basis, period_type, account_id);
