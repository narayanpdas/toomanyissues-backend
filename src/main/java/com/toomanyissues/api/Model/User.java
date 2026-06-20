package com.toomanyissues.api.Model;

import com.toomanyissues.api.Controller.Requests.UserRegisterRequest;
import com.toomanyissues.api.Controller.Requests.UserUpdateRequest;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "users")
@Setter @Getter @NoArgsConstructor @AllArgsConstructor
public class User implements UserDetails {
    // Fields Decided by Server
    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    private String id;
    private String role="USER";

    // Fields Asked
    private String name;
    @Column(unique = true) private String username;
    private String password;
    private String email;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "user_preferences",
            joinColumns = @JoinColumn(name="id")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<String> preferences;
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "user_primary_languages",
            joinColumns = @JoinColumn(name="id")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<String> primaryLanguages;
    @Column(name = "ai_points_used")
    private Integer aiPointsUsed = 0;

    @Column(name = "last_ai_request_date")
    private LocalDate lastAiRequestDate;

    public void fromRequest(UserRegisterRequest userRequest,
                            String hashedPassword) {
        this.name = userRequest.name();
        this.username = userRequest.username();
        this.password = hashedPassword;
        this.email = userRequest.email();
        this.preferences = userRequest.preferences();
        this.primaryLanguages = userRequest.primaryLanguages();
    }
    public void fromRequest(String id,
                            UserRegisterRequest userRequest,
                            String hashedPassword) {
        this.id = id;
        this.name = userRequest.name();
        this.username = userRequest.username();
        this.password = hashedPassword;
        this.email = userRequest.email();
        this.preferences = userRequest.preferences();
        this.primaryLanguages = userRequest.primaryLanguages();
    }
    public void fromRequest(String id,
                            String email,
                            String username,
                            UserUpdateRequest userUpdateRequest) {
        this.id = id;
        this.name = userUpdateRequest.name();
        this.username = username;
        this.password = userUpdateRequest.password();
        this.email = email;
        this.preferences = userUpdateRequest.preferences();
        this.primaryLanguages = userUpdateRequest.primaryLanguages();


    }


    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + this.role.toUpperCase()));
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
