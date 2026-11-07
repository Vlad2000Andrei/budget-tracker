package com.budgettracker.backend.repository;

import com.budgettracker.backend.jooq.tables.records.OauthAuthorizationCodesRecord;
import com.budgettracker.backend.jooq.tables.records.OauthRefreshTokensRecord;
import com.budgettracker.backend.model.OAuthAuthorizationCode;
import com.budgettracker.backend.model.OAuthRefreshToken;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.budgettracker.backend.jooq.Tables.OAUTH_AUTHORIZATION_CODES;
import static com.budgettracker.backend.jooq.Tables.OAUTH_REFRESH_TOKENS;

@Repository
public class OAuthRepository {

    private final DSLContext dsl;

    @Autowired
    public OAuthRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Transactional
    public void saveAuthorizationCode(OAuthAuthorizationCode authCode) {
        LocalDateTime now = LocalDateTime.now();
        dsl.insertInto(OAUTH_AUTHORIZATION_CODES)
                .set(OAUTH_AUTHORIZATION_CODES.CODE, authCode.getCode())
                .set(OAUTH_AUTHORIZATION_CODES.USER_ID, authCode.getUserId())
                .set(OAUTH_AUTHORIZATION_CODES.CLIENT_ID, authCode.getClientId())
                .set(OAUTH_AUTHORIZATION_CODES.REDIRECT_URI, authCode.getRedirectUri())
                .set(OAUTH_AUTHORIZATION_CODES.CODE_CHALLENGE, authCode.getCodeChallenge())
                .set(OAUTH_AUTHORIZATION_CODES.CODE_CHALLENGE_METHOD, authCode.getCodeChallengeMethod())
                .set(OAUTH_AUTHORIZATION_CODES.EXPIRES_AT, authCode.getExpiresAt())
                .set(OAUTH_AUTHORIZATION_CODES.CREATED_AT, now)
                .execute();
    }

    public Optional<OAuthAuthorizationCode> findAuthorizationCode(String code) {
        return dsl.selectFrom(OAUTH_AUTHORIZATION_CODES)
                .where(OAUTH_AUTHORIZATION_CODES.CODE.eq(code))
                .fetchOptional()
                .map(this::mapRecordToAuthCode);
    }

    @Transactional
    public void deleteAuthorizationCode(String code) {
        dsl.deleteFrom(OAUTH_AUTHORIZATION_CODES)
                .where(OAUTH_AUTHORIZATION_CODES.CODE.eq(code))
                .execute();
    }

    @Transactional
    public void saveRefreshToken(OAuthRefreshToken refreshToken) {
        LocalDateTime now = LocalDateTime.now();
        dsl.insertInto(OAUTH_REFRESH_TOKENS)
                .set(OAUTH_REFRESH_TOKENS.TOKEN_HASH, refreshToken.getTokenHash())
                .set(OAUTH_REFRESH_TOKENS.USER_ID, refreshToken.getUserId())
                .set(OAUTH_REFRESH_TOKENS.CLIENT_ID, refreshToken.getClientId())
                .set(OAUTH_REFRESH_TOKENS.EXPIRES_AT, refreshToken.getExpiresAt())
                .set(OAUTH_REFRESH_TOKENS.REVOKED_AT, refreshToken.getRevokedAt())
                .set(OAUTH_REFRESH_TOKENS.CREATED_AT, now)
                .execute();
    }

    public Optional<OAuthRefreshToken> findRefreshTokenByHash(String tokenHash) {
        return dsl.selectFrom(OAUTH_REFRESH_TOKENS)
                .where(OAUTH_REFRESH_TOKENS.TOKEN_HASH.eq(tokenHash))
                .fetchOptional()
                .map(this::mapRecordToRefreshToken);
    }

    @Transactional
    public void revokeRefreshToken(String tokenHash) {
        dsl.update(OAUTH_REFRESH_TOKENS)
                .set(OAUTH_REFRESH_TOKENS.REVOKED_AT, LocalDateTime.now())
                .where(OAUTH_REFRESH_TOKENS.TOKEN_HASH.eq(tokenHash))
                .execute();
    }

    private OAuthAuthorizationCode mapRecordToAuthCode(OauthAuthorizationCodesRecord record) {
        return OAuthAuthorizationCode.builder()
                .code(record.getCode())
                .userId(record.getUserId())
                .clientId(record.getClientId())
                .redirectUri(record.getRedirectUri())
                .codeChallenge(record.getCodeChallenge())
                .codeChallengeMethod(record.getCodeChallengeMethod())
                .expiresAt(record.getExpiresAt())
                .createdAt(record.getCreatedAt())
                .build();
    }

    private OAuthRefreshToken mapRecordToRefreshToken(OauthRefreshTokensRecord record) {
        return OAuthRefreshToken.builder()
                .tokenHash(record.getTokenHash())
                .userId(record.getUserId())
                .clientId(record.getClientId())
                .expiresAt(record.getExpiresAt())
                .revokedAt(record.getRevokedAt())
                .createdAt(record.getCreatedAt())
                .build();
    }
}
