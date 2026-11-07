-- OAuth 2.1 Authorization Codes Table (PKCE)
CREATE TABLE oauth_authorization_codes (
    code VARCHAR(128) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    client_id VARCHAR(128) NOT NULL,
    redirect_uri TEXT NOT NULL,
    code_challenge VARCHAR(128) NOT NULL,
    code_challenge_method VARCHAR(16) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- OAuth 2.1 Refresh Tokens Table
CREATE TABLE oauth_refresh_tokens (
    token_hash VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    client_id VARCHAR(128) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_oauth_auth_codes_user_id ON oauth_authorization_codes(user_id);
CREATE INDEX idx_oauth_refresh_tokens_user_id ON oauth_refresh_tokens(user_id);
