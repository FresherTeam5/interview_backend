package com.baseProject.myBaseProject.constant;

public final class Message {
    public static final String TEMPLATE_NOT_FOUND = "Interview template not found";
    public static final String TEMPLATE_VERSION_CONFLICT = "Interview template changed; reload it and try again";
    public static final String TEMPLATE_ARCHIVED = "Interview template is archived";
    public static final String TEMPLATE_INVALID_ANALYSIS = "Job analysis did not match the required contract";
    public static final String TEMPLATE_INSUFFICIENT_JD = "Please provide a job description with responsibilities or job requirements";
    public static final String TEMPLATE_ALREADY_CONFIRMED = "Confirmed interview templates cannot be edited";
    public static final String TEMPLATE_CONFIRM_REQUIRED = "Confirm the interview template before using or publishing it";
    public static final String TEMPLATE_REVIEW_NOT_ALLOWED =
            "Interview template cannot enter the requested moderation state";
    public static final String TEMPLATE_APPROVAL_REQUIRED =
            "Interview template must be approved before it can be published";

    // Interview sessions
    public static final String INTERVIEW_SESSION_NOT_FOUND = "Interview session not found";
    public static final String INTERVIEW_SESSION_OPTION_INVALID = "Interview session option is not supported";
    public static final String INTERVIEW_SESSION_IDEMPOTENCY_CONFLICT = "Idempotency key was already used with different session options";
    public static final String INTERVIEW_SESSION_PREPARATION_NOT_RETRYABLE = "Only failed interview preparation can be retried";
    public static final String INTERVIEW_SESSION_PREPARATION_FAILED = "Failed to prepare interview session";
    public static final String INTERVIEW_PLAN_INVALID = "AI returned an invalid interview plan";
    public static final String INTERVIEW_REPLY_INVALID = "AI returned an invalid interview reply";

    public static final String INTERVIEW_ASSESSMENT_INVALID = "AI returned an invalid interview assessment";
    public static final String INTERVIEW_SCORING_FAILED = "Failed to score interview session";
    public static final String INTERVIEW_SCORING_NOT_RETRYABLE = "Only failed interview scoring can be retried";
    public static final String INTERVIEW_REPORT_NOT_AVAILABLE = "Interview report is not available";

    public static final String INTERVIEW_SESSION_NOT_STARTABLE = "Interview session is not ready to start";
    public static final String INTERVIEW_SESSION_NOT_IN_PROGRESS = "Interview session is not in progress";
    public static final String INTERVIEW_SESSION_MODE_MISMATCH = "Operation is not supported for this interview session mode";
    public static final String INTERVIEW_TURN_OUT_OF_SEQUENCE = "Interview answer does not match the current interviewer turn";
    public static final String INTERVIEW_TURN_IDEMPOTENCY_CONFLICT = "Idempotency key was already used for a different answer";
    public static final String INTERVIEW_TURN_PROCESSING = "The interviewer is already processing this answer";
    public static final String INTERVIEW_TURN_NOT_RETRYABLE = "Only a failed candidate turn can be retried";
    public static final String INTERVIEW_SESSION_NOT_CANCELLABLE =
            "Only an interview that has not started can be cancelled";
    public static final String INTERVIEW_ADMIN_OPERATION_INVALID =
            "Administrator operation is not allowed for the current interview status";
    public static final String PROFILE_CONFIRM_REQUIRED = "Confirm the candidate profile before creating an interview session";

    // Speech
    public static final String SPEECH_NOT_ENABLED = "Interview speech is not enabled";
    public static final String SPEECH_CONFIG_ERROR = "Speech service is not configured properly";
    public static final String SPEECH_INVALID_AUDIO = "Audio file is missing or unsupported";
    public static final String SPEECH_AUDIO_TOO_LARGE = "Audio file exceeds the allowed size";
    public static final String SPEECH_PROVIDER_UNAVAILABLE = "Speech service is temporarily unavailable";
    public static final String SPEECH_PROVIDER_TIMEOUT = "Speech service request timed out";
    public static final String SPEECH_PROVIDER_ERROR = "Speech service rejected the request";
    public static final String SPEECH_TRANSCRIPTION_FAILED = "Speech service returned an empty transcription";
    public static final String SPEECH_SYNTHESIS_FAILED = "Speech service returned empty audio";
    public static final String SPEECH_TURN_NOT_SYNTHESIZABLE = "Interview turn cannot be converted to speech";

    // Realtime interview
    public static final String REALTIME_CONFIG_ERROR = "Realtime interview service is not configured properly";
    public static final String REALTIME_NOT_ENABLED = "Realtime interview is not enabled";
    public static final String REALTIME_SESSION_NOT_AVAILABLE = "Realtime session is not available for this interview";
    public static final String REALTIME_VOICE_NOT_SUPPORTED = "Realtime voice is not supported";
    public static final String REALTIME_PROVIDER_UNAVAILABLE = "Realtime interview provider is temporarily unavailable";
    public static final String REALTIME_PROVIDER_TIMEOUT = "Realtime interview provider timed out";
    public static final String REALTIME_PROVIDER_ERROR = "Realtime interview provider rejected the request";
    public static final String REALTIME_CONNECTION_NOT_FOUND = "Realtime connection not found";
    public static final String REALTIME_EVENT_INVALID = "Realtime event payload is invalid";
    public static final String REALTIME_EVENT_SEQUENCE_CONFLICT = "Realtime event sequence was already used";
    public static final String REALTIME_RESUMPTION_NOT_AVAILABLE = "Realtime session cannot be resumed";

    // Job description processing
    public static final String JD_FILE_REQUIRED = "Job description file is required";
    public static final String JD_INVALID_FILE_TYPE = "Job description must be a valid PDF file";
    public static final String JD_FILE_TOO_LARGE = "Job description file exceeds the allowed size";
    public static final String JD_FILE_CORRUPTED = "Job description file is corrupted, encrypted, or unreadable";
    public static final String JD_TOO_MANY_PAGES = "Job description has too many pages";
    public static final String JD_TEXT_TOO_LONG = "Job description text exceeds the allowed length";
    public static final String JD_EMPTY_TEXT = "Job description contains no extractable text";
    public static final String JD_LIMIT_REACHED = "Maximum number of job descriptions reached";
    public static final String JD_NOT_FOUND = "Job description not found";
    public static final String JD_PROCESSING_IN_PROGRESS = "Job description processing is already in progress";
    public static final String JD_PROCESSING_NOT_RETRYABLE = "Only failed job description processing can be retried";
    public static final String JD_FILE_NOT_AVAILABLE = "This job description was created from text and has no file";
    public static final String JD_PROCESSING_FAILED = "Failed to process job description";
    public static final String JD_ANALYSIS_NOT_READY = "Job description analysis is not ready";

    public static final String INVALID_CREDENTIALS = "Invalid email or password";
    public static final String AUTHENTICATION_REQUIRED = "Authentication is required to access this resource";
    public static final String ACCESS_DENIED = "You do not have permission to access this resource";
    public static final String ACCOUNT_DISABLED = "This account has been disabled";
    public static final String MALFORMED_JSON = "Malformed JSON request body";
    public static final String VALIDATION_FAILED = "Validation failed";
    public static final String CONSTRAINT_VIOLATION = "Resource already exists or violates a data constraint";
    public static final String ENDPOINT_NOT_FOUND = "No endpoint found for this request";
    public static final String INTERNAL_ERROR = "Internal server error";
    public static final String EMAIL_NOT_FOUND = "Email not found";
    public static final String USER_NOT_FOUND = "User not found";
    public static final String ADMIN_USER_STATUS_PROTECTED =
            "Administrator accounts cannot be enabled or disabled through this endpoint";
    public static final String MISSING_REFRESH_TOKEN = "Refresh token cookie is missing";
    public static final String INVALID_GOOGLE_TOKEN = "Google ID token is invalid or has expired";
    public static final String GOOGLE_EMAIL_NOT_VERIFIED = "This Google account has no verified email";
    public static final String GOOGLE_LOGIN_NOT_CONFIGURED = "Google login is not configured on this server";
    public static final String ACCOUNT_TOKEN_INVALID = "Account token is invalid or expired";
    public static final String EMAIL_ALREADY_VERIFIED = "Email address is already verified";
    public static final String CURRENT_PASSWORD_INVALID = "Current password is invalid";
    public static final String PASSWORD_NOT_CONFIGURED =
            "This account has no password; use password reset to create one";
    public static final String DEVICE_SESSION_NOT_FOUND = "Login session not found";
    public static final String ACCOUNT_DELETION_NOT_PENDING = "Account deletion is not pending";
    public static final String ACCOUNT_DELETION_ADMIN_FORBIDDEN =
            "Administrator accounts cannot be deleted through this endpoint";

    // AI & CV parsing
    public static final String AI_SERVICE_UNAVAILABLE = "AI service is temporarily unavailable";
    public static final String AI_TIMEOUT = "AI service request timed out";
    public static final String AI_MALFORMED_OUTPUT = "AI service returned invalid or unparseable output";
    public static final String AI_CONFIG_ERROR = "AI service is not configured properly";
    public static final String AI_ERROR = "AI service execution failed";
    public static final String CV_PARSE_FAILED = "Failed to parse CV content";

    // CV upload
    public static final String UPLOAD_TOO_LARGE = "Uploaded request is too large";
    public static final String CV_FILE_REQUIRED = "CV file is required";
    public static final String CV_INVALID_FILE_TYPE = "CV must be a valid PDF file";
    public static final String CV_FILE_TOO_LARGE = "CV file exceeds the allowed size";
    public static final String CV_FILE_CORRUPTED = "CV file is corrupted, encrypted, or unreadable";
    public static final String CV_TOO_MANY_PAGES = "CV has too many pages";
    public static final String CV_LIMIT_REACHED = "Maximum number of CVs reached";
    public static final String CV_NOT_FOUND = "CV not found";
    public static final String CV_PARSE_IN_PROGRESS = "CV parsing is already in progress";
    public static final String CV_PARSE_NOT_RETRYABLE = "Only failed CV parsing can be retried";
    public static final String STORAGE_UNAVAILABLE = "File storage is temporarily unavailable";

    // Candidate profile
    public static final String PROFILE_NOT_FOUND = "Candidate profile not found";
    public static final String PROFILE_ITEM_NOT_FOUND = "Profile item not found";
    public static final String DUPLICATE_SKILL_NAME = "Profile contains duplicate skill names";
    public static final String PROFILE_VERSION_CONFLICT = "Candidate profile was changed by another request; reload it and try again";
    public static final String NOTIFICATION_NOT_FOUND = "Notification not found";
    public static final String INTERVIEW_FEEDBACK_NOT_FOUND = "Interview feedback not found";
    public static final String INTERVIEW_FEEDBACK_NOT_AVAILABLE =
            "Feedback is available after the interview report is completed";
    public static final String SUPPORT_TICKET_NOT_FOUND = "Support ticket not found";
    public static final String SUPPORT_TICKET_CONTEXT_INVALID =
            "Support ticket session or turn does not belong to the current user";
    public static final String SUPPORT_TICKET_TRANSITION_INVALID =
            "Support ticket status transition is not allowed";
    public static final String SUPPORT_TICKET_CLOSED =
            "Closed support tickets do not accept new public messages";
    public static final String SUPPORT_ASSIGNEE_INVALID =
            "Support ticket can only be assigned to an active administrator";
    public static final String ANNOUNCEMENT_NOT_FOUND = "Announcement not found";
    public static final String ANNOUNCEMENT_STATE_INVALID =
            "Announcement operation is not allowed in its current state";
    public static final String ANNOUNCEMENT_VERSION_CONFLICT =
            "Announcement changed; reload it and try again";
}
