-- Run in your dedicated Supabase project's SQL Editor AFTER the backend is healthy.
-- Enable pg_cron, pg_net and pgcrypto in the dashboard first.
-- Create two Vault secrets using the dashboard:
--   safenavi_backend_origin = https://YOUR-SERVICE.onrender.com (no trailing path)
--   safenavi_scheduler_key  = Render SAFE_NAVI_SCHEDULER_KEY (32+ characters)
-- Never put the database password or service_role key in request headers.
-- pg_net's request queue can be read by DB roles: only a short-lived, scoped HMAC
-- signature (not the signing key itself) is transmitted.

select cron.schedule(
  'safenavi-news-due-check',
  '0 * * * *',
  $job$
    with settings as (
      select
        (select decrypted_secret from vault.decrypted_secrets where name='safenavi_backend_origin' limit 1) as origin,
        (select decrypted_secret from vault.decrypted_secrets where name='safenavi_scheduler_key' limit 1) as signing_key,
        floor(extract(epoch from now()))::bigint::text as tick
    )
    select net.http_post(
      url := rtrim(origin, '/') || '/api/v1/internal/news/tick',
      headers := jsonb_build_object(
        'Content-Type', 'application/json',
        'X-SafeNavi-Scheduler-Timestamp', tick,
        'X-SafeNavi-Scheduler-Signature', encode(extensions.hmac(
          convert_to(tick || E'\nPOST\n/api/v1/internal/news/tick', 'UTF8'),
          convert_to(signing_key, 'UTF8'), 'sha256'), 'hex')
      ),
      body := '{}'::jsonb,
      timeout_milliseconds := 120000
    )
    from settings
    where origin ~ '^https://[^/@?#]+/?$' and length(signing_key)>=32
      and exists (
        select 1 from safenavi.news_configuration
        where singleton_id=1
          and (last_ingested_at is null or last_ingested_at::timestamptz + refresh_hours * interval '1 hour' <= now())
          and (ingestion_lease_until is null or ingestion_lease_until::timestamptz <= now())
      );
  $job$
);

-- Re-running cron.schedule with this same name updates this job rather than
-- intentionally creating another schedule. Inspect via SELECT * FROM cron.job;
-- Inspect executions via cron.job_run_details and HTTP results via net._http_response.
-- A completed SQL cron job only means the HTTP request was queued: check HTTP status too.
