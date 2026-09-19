package com.cosmos.cosmos_backend.auth.domain.entity;

import com.cosmos.cosmos_backend.auth.domain.OAuthProvider;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_oauth_accounts")
@Getter
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserOauthAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userOauthAccountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OAuthProvider oauthProvider;

    @Column(nullable = false)
    private String providerUserId;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public UserOauthAccount(User user, OAuthProvider oauthProvider, String providerUserId) {
        this.user = user;
        this.oauthProvider = oauthProvider;
        this.providerUserId = providerUserId;
    }
}
