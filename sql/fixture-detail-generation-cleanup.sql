-- Apply once to an existing database before deploying the generation cleanup scheduler.
-- The small job receipt remains after its generation is purged. It is used to prevent
-- a delayed Redis Stream delivery from publishing the same job a second time.
ALTER TABLE fixture_detail_published_job
    DROP FOREIGN KEY fk_fixture_detail_published_job_generation;
