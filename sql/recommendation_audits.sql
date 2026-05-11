-- Persistent audit trail for deterministic recommendation decisions.
--
-- Run in Supabase SQL Editor before deploying the API change.
-- The Spring API writes through PostgreSQL JDBC; browser Supabase clients
-- should not receive table privileges.

CREATE TABLE IF NOT EXISTS public.recommendation_audits (
    request_id TEXT PRIMARY KEY,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    request_json JSONB NOT NULL,
    response_json JSONB,
    evaluated_promo_version_ids TEXT[] NOT NULL DEFAULT '{}',
    engine_version TEXT NOT NULL,
    latency_ms BIGINT NOT NULL CHECK (latency_ms >= 0),
    success BOOLEAN NOT NULL,
    error_type TEXT,
    error_message TEXT
);

CREATE INDEX IF NOT EXISTS idx_recommendation_audits_requested_at
    ON public.recommendation_audits (requested_at DESC);

CREATE INDEX IF NOT EXISTS idx_recommendation_audits_promo_versions
    ON public.recommendation_audits USING GIN (evaluated_promo_version_ids);

ALTER TABLE public.recommendation_audits ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE public.recommendation_audits FROM anon, authenticated;
