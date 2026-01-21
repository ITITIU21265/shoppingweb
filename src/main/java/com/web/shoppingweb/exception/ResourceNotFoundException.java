package com.web.shoppingweb.exception;

public class ResourceNotFoundException extends RuntimeException{
    public ResourceNotFoundException(string message){
        super(message);
    }
    public ResourceNotFoundException(tring message, Throwable cause){
        super(message, cause);
    }
}
