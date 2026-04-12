-- Enable row level security on public tables flagged by Supabase Database Linter.
--
-- This project reads/writes these tables from the Spring API through the
-- PostgreSQL JDBC connection, not directly from browser Supabase REST calls.
-- Keep RLS enabled without anon/authenticated table policies so PostgREST
-- cannot expose the tables directly.
--
-- Run in Supabase SQL Editor.

ALTER TABLE IF EXISTS public.clients ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.api_calls ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.daily_usage ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.extract_runs ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.promotion_versions ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.promotion_current ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE public.clients FROM anon, authenticated;
REVOKE ALL ON TABLE public.api_calls FROM anon, authenticated;
REVOKE ALL ON TABLE public.daily_usage FROM anon, authenticated;
REVOKE ALL ON TABLE public.extract_runs FROM anon, authenticated;
REVOKE ALL ON TABLE public.promotion_versions FROM anon, authenticated;
REVOKE ALL ON TABLE public.promotion_current FROM anon, authenticated;

-- Prevent browser clients from invoking usage helpers through PostgREST RPC.
-- The backend JDBC role can still call these functions as the owner/superuser.
DO $$
BEGIN
    IF to_regprocedure('public.get_daily_usage(uuid)') IS NOT NULL THEN
        EXECUTE 'REVOKE EXECUTE ON FUNCTION public.get_daily_usage(uuid) FROM anon, authenticated';
    END IF;

    IF to_regprocedure('public.increment_daily_usage(uuid)') IS NOT NULL THEN
        EXECUTE 'REVOKE EXECUTE ON FUNCTION public.increment_daily_usage(uuid) FROM anon, authenticated';
    END IF;
END $$;

-- Verification: relrowsecurity should be true for every row.
SELECT schemaname, tablename, rowsecurity
FROM pg_tables
WHERE schemaname = 'public'
  AND tablename IN (
      'clients',
      'api_calls',
      'daily_usage',
      'extract_runs',
      'promotion_versions',
      'promotion_current'
  )
ORDER BY tablename;
