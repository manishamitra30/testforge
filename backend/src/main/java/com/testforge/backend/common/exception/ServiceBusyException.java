package com.testforge.backend.common.exception;

public class ServiceBusyException extends RuntimeException {

    public ServiceBusyException(String message) {
        super(message);
    }
}