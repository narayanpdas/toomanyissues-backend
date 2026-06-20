package com.toomanyissues.api.Service;

import com.toomanyissues.api.Controller.Reponses.UserDetailsResponse;
import com.toomanyissues.api.Controller.Reponses.UserResponse;
import com.toomanyissues.api.Controller.Requests.UserRegisterRequest;
import com.toomanyissues.api.Controller.Requests.UserUpdateRequest;
import com.toomanyissues.api.ErrorHandling.exceptions.SomethingWentWrong;
import com.toomanyissues.api.ErrorHandling.exceptions.UserDoesNotExistException;
import com.toomanyissues.api.Model.RefreshTokenModel;
import com.toomanyissues.api.Model.User;
import com.toomanyissues.api.Security.JwtService;
import com.toomanyissues.api.Security.RefreshTokenService;
import com.toomanyissues.api.repository.UserRepository;
import com.toomanyissues.api.ErrorHandling.exceptions.UserAlreadyExistsException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {
    UserRepository userRepository;
    PasswordEncoder passwordEncoder;
    JwtService jwtService;
    AuthenticationManager authenticationManager;
    RefreshTokenService refreshTokenService;
    private static final int DAILY_LIMIT = 15;
    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager,
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.refreshTokenService = refreshTokenService;
    }
    public Boolean doesUsernameExists(String username) {
        return userRepository.existsByUsername(username);
    }
    public boolean doesEmailExists(String email) {
        return userRepository.existsByEmail(email);
    }
    public User registerUser(UserRegisterRequest user) {
        Boolean usernameExists = doesUsernameExists(user.username());
        Boolean emailExists = doesEmailExists(user.email());

        if (usernameExists ||  emailExists) {
            throw new UserAlreadyExistsException(
                    "username "+user.username()+" or email "+ user.email() +" already exists");
        }
        else {
            User newUser = new User();
            newUser.fromRequest(user,passwordEncoder.encode(user.password()));
            return userRepository.save(newUser);
        }

    }

    public Optional<UserResponse> refreshUser(String refreshToken) {
        Optional<RefreshTokenModel> rtm = refreshTokenService.verifyExpiration(refreshToken);
        if(rtm.isEmpty()){
            return Optional.empty();
        }
        User user = rtm.get().getUser();
        String jwtToken = jwtService.generateToken(user);
        System.out.println("New JWT Token: " + jwtToken);
        return Optional.of(new UserResponse(
                user.getUsername(),
                jwtToken,
                refreshToken,
                Instant.now().plusMillis(jwtService.getEXPIRATION_TIME()+500), // Manual Network Delay
                rtm.get().getExpirationTime(),
                "Login Successful",
                user.getRole()));
    }
    public UserResponse loginUser(String username, String password) {
        authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
        );
        User user = userRepository.findByUsername(username)
                .orElseThrow();
        String jwtToken = jwtService.generateToken(user);
        RefreshTokenModel refreshToken = refreshTokenService.createRefreshToken(user);
        System.out.println("jwtToken: "+jwtToken);
        return new UserResponse(
                    user.getUsername(),
                    jwtToken,
                    refreshToken.getRefreshToken(),
                    Instant.now().minusMillis(900000),
                    refreshToken.getExpirationTime(),
                    "Login Successful",
                    user.getRole());
    }
    public UserDetailsResponse getCurrentUser(User user) {
        User currentUser = userRepository.findById(user.getId()).orElseThrow();
        return new  UserDetailsResponse(
                currentUser.getName(),
                currentUser.getUsername(),
                currentUser.getEmail(),
                currentUser.getPreferences(),
                currentUser.getPrimaryLanguages()
        );
    }
    public UserDetailsResponse updateCurrentUser(String username, UserUpdateRequest user) {
        try {
            User u = userRepository.findByUsername(username).orElse(null);
            if (u != null) {
                User updatedUser = new User();
                updatedUser.setId(u.getId());
                updatedUser.setUsername(username);
                updatedUser.setEmail(u.getEmail());
                if (user.password() != null) {
                    updatedUser.setPassword(passwordEncoder.encode(user.password()));
                } else updatedUser.setPassword(u.getPassword());
                if (user.name() == null) {
                    updatedUser.setUsername(u.getUsername());
                }
                else updatedUser.setName(user.name());
                if (user.preferences() == null) {
                    updatedUser.setPreferences(u.getPreferences());
                }
                else updatedUser.setPreferences(user.preferences());
                if (user.primaryLanguages() == null) {
                    updatedUser.setPrimaryLanguages(u.getPrimaryLanguages());
                }
                else updatedUser.setPrimaryLanguages(user.primaryLanguages());
                userRepository.save(updatedUser);
                return new UserDetailsResponse(
                        updatedUser.getName(),
                        updatedUser.getUsername(),
                        updatedUser.getEmail(),
                        updatedUser.getPreferences(),
                        updatedUser.getPrimaryLanguages()
                );
            }
            else throw new UserDoesNotExistException("USER_DOES_NOT_EXIST");
        }
        catch (Exception e){
            throw new SomethingWentWrong(e.getMessage());
        }

    }
    @Transactional
    public boolean deductUserPoints(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        LocalDate today = LocalDate.now();
        if (user.getLastAiRequestDate() == null || !user.getLastAiRequestDate().isEqual(today)) {
            user.setAiPointsUsed(0);
            user.setLastAiRequestDate(today);
        }
        if (user.getAiPointsUsed() >= DAILY_LIMIT) {
            return false;
        }
        user.setAiPointsUsed(user.getAiPointsUsed() + 1);
        userRepository.save(user);
        return true;
    }

    // ADMIN ONLY
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }
}
