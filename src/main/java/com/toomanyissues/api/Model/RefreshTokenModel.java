package com.toomanyissues.api.Model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name="refresh_token")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class RefreshTokenModel {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;
    @Column(nullable = false,unique = true)
    private String refreshToken;
    @OneToOne
    @JoinColumn(name="user_id",referencedColumnName="id")
    private User user;
    @Column(nullable = false)
    private Instant expirationTime;

}
