package com.toomanyissues.api.ErrorHandling.exceptions;

public class TokenExpired extends RuntimeException {
    public TokenExpired(String message) {
        super(message);
    }
}
