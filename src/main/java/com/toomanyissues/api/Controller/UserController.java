package com.toomanyissues.api.Controller;


import com.toomanyissues.api.Controller.Reponses.CheckResponse;
import com.toomanyissues.api.Controller.Reponses.UserDetailsResponse;
import com.toomanyissues.api.Controller.Reponses.UserResponse;
import com.toomanyissues.api.Controller.Requests.RefreshTokenRequest;
import com.toomanyissues.api.Controller.Requests.UserLoginCredentials;
import com.toomanyissues.api.Controller.Requests.UserRegisterRequest;
import com.toomanyissues.api.Controller.Requests.UserUpdateRequest;
import com.toomanyissues.api.Model.User;
import com.toomanyissues.api.Service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController()
public class UserController {
    UserService userService;
    public UserController(UserService userService) {
        this.userService = userService;
    }
    @PostMapping("/api/auth/register")
    public ResponseEntity<User> registerUser(@Valid @RequestBody UserRegisterRequest user) {
        User newUser = userService.registerUser(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(newUser);
    }
    @PostMapping("/api/auth/login")
    public ResponseEntity<UserResponse> loginUser(@Valid @RequestBody UserLoginCredentials userCredentials) {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(userService.loginUser(userCredentials.username(),
                                    userCredentials.password()));
    }
    @PostMapping("/api/auth/refresh-user")
    public ResponseEntity<?> refreshToken(@Valid @RequestBody RefreshTokenRequest refreshTokenRequest) {
        System.out.println("refreshToken :"+refreshTokenRequest.toString());
        var x = userService.refreshUser(refreshTokenRequest.refreshToken());
        if(x.isEmpty()){
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("{error: Token expired or invalid. Please sign in again.}");
        }
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(x.get());
    }
    @GetMapping("/api/auth/check-email")
    public ResponseEntity<?> checkEmail(@RequestParam("email") String email) {
        boolean exists = userService.doesEmailExists(email);
        if(exists){
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .cacheControl(CacheControl.noStore().mustRevalidate())
                    .body(new CheckResponse(true,"email already exist"));
        }
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new CheckResponse(false, email));
    }
    @GetMapping("/api/auth/check-username")
    public ResponseEntity<?> checkUserName(@RequestParam("username") String username) {
        boolean exists = userService.doesUsernameExists(username);
        if(exists){
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .cacheControl(CacheControl.noStore().mustRevalidate())
                    .body(new CheckResponse(true,"Username already exist"));
        }
        return ResponseEntity
                .status(HttpStatus.OK)
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .body(new CheckResponse(false,username));
    }

    @GetMapping("/api/users/me")
    public ResponseEntity<UserDetailsResponse> getCurrentUser(@AuthenticationPrincipal User currentUser) {
        System.out.println(currentUser+ " "+ currentUser.getUsername());
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(userService.getCurrentUser(currentUser));
    }
    @PutMapping("/api/users/me")
    public ResponseEntity<UserDetailsResponse> updateCurrentUser(@AuthenticationPrincipal User currentUser,
                                                          @Valid @RequestBody UserUpdateRequest user) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(userService.updateCurrentUser(currentUser.getUsername(),user));
    }
    @GetMapping("admin/users/allusers")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(userService.getAllUsers());
    }
}
