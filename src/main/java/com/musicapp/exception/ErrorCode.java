package com.musicapp.exception;

/**
 * CODE-5 FIX: Centralises all error code strings as enum constants.
 * Eliminates magic string literals scattered across AuthService/UserService.
 */
public enum ErrorCode {
    BLANK_FIELDS("blank_fields"),
    USERNAME_TAKEN("username_taken"),
    EMAIL_TAKEN("email_taken"),
    MISMATCH("mismatch"),
    INPUT_TOO_LONG("input_too_long"),
    WEAK_PASSWORD("weak_pass"),
    INVALID_TOKEN("invalid_token"),
    OLD_WRONG("old_wrong");

    private final String code;

    ErrorCode(String code) { this.code = code; }

    public String getCode() { return code; }

    @Override
    public String toString() { return code; }
}
