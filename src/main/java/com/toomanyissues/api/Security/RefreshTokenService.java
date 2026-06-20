package com.toomanyissues.api.Security;

import com.toomanyissues.api.ErrorHandling.exceptions.TokenExpired;
import com.toomanyissues.api.Model.RefreshTokenModel;
import com.toomanyissues.api.Model.User;
import com.toomanyissues.api.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final long refreshExpireTime;
    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               @Value("${app.refreshTokenTimeout}") long refreshExpireTime) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshExpireTime = refreshExpireTime;
    }
    public RefreshTokenModel createRefreshToken(User user) {
        refreshTokenRepository.deleteByUser(user);
        RefreshTokenModel refreshToken = new RefreshTokenModel();
        refreshToken.setUser(user);
        refreshToken.setRefreshToken(UUID.randomUUID().toString());
        refreshToken.setExpirationTime(Instant.now().plusMillis(refreshExpireTime));
        return refreshTokenRepository.save(refreshToken);
    }
    @Transactional(noRollbackFor = TokenExpired.class)
    public Optional<RefreshTokenModel> verifyExpiration(String token) {
            RefreshTokenModel rTm = refreshTokenRepository
                    .findByRefreshToken(token)
                    .orElseThrow(
                    ()-> new RuntimeException("No Token Found")
            );
            if(rTm.getExpirationTime().compareTo(Instant.now()) < 0){
                refreshTokenRepository.delete(rTm);
                return Optional.empty();
            }

            return Optional.of(rTm);
    }
}
