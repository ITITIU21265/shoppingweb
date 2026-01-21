package com.web.shoppingweb.exception;

public class DuplicateResourceException extends RuntimeException{
    public DuplicateResourceException(string message){
        super(message);
    }

    public DuplicateResourceException(string message, Throwable cause){
        super(message, cause);
    }
}
