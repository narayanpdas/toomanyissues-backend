package com.toomanyissues.api.ErrorHandling.exceptions;

public class SomethingWentWrong extends RuntimeException {
    public SomethingWentWrong(String message) {
        super(message);
    }
}
