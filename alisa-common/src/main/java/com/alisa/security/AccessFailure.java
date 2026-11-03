package com.alisa.security;
public class AccessFailure extends RuntimeException {
    private final int status;
    public AccessFailure(int status, String message) { super(message); this.status = status; }
    public int status() { return status; }
}
