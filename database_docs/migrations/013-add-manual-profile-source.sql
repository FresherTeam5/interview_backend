ALTER TABLE candidate_profiles
    ADD CONSTRAINT chk_candidate_profiles_source CHECK (
        source IN ('AUTO_PARSED', 'USER_EDITED', 'MANUAL'));
