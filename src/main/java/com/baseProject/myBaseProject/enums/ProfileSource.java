package com.baseProject.myBaseProject.enums;

/**
 * Where the current profile content came from. Enforced in the database by
 * {@code chk_candidate_profiles_source}.
 *
 * <p>This is a statistic, not a priority flag: the edited version always wins
 * because the user edits {@code candidate_profiles} directly, while
 * {@code cv_parse_results.raw_json} keeps the untouched AI output.
 */
public enum ProfileSource {
    /** Straight from the AI parse, untouched by the user. */
    AUTO_PARSED,
    /** The user has corrected at least one field. */
    USER_EDITED,
    /** The user created the profile without uploading a CV. */
    MANUAL
}
