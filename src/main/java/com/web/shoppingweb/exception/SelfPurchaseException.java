package com.web.shoppingweb.exception;

public class SelfPurchaseException extends IllegalArgumentException {

    public SelfPurchaseException(String message) {
        super(message);
    }
}
