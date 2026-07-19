CREATE EXTENSION IF NOT EXISTS vector;

DO $$
BEGIN
    IF to_regclass('public.retrieval_benchmark_run') IS NOT NULL THEN
        ALTER TABLE retrieval_benchmark_run
            DROP CONSTRAINT IF EXISTS retrieval_benchmark_run_mode_check;
        ALTER TABLE retrieval_benchmark_run
            ADD CONSTRAINT retrieval_benchmark_run_mode_check
                CHECK (mode IN ('KEYWORD', 'HYBRID', 'HYBRID_RERANK'));
    END IF;
END
$$;
