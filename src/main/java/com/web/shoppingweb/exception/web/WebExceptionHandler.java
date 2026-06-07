package com.web.shoppingweb.exception.web;

import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.web.shoppingweb.exception.SelfPurchaseException;

@ControllerAdvice(basePackages = "com.web.shoppingweb.controller.web")
public class WebExceptionHandler {

    @ExceptionHandler(SelfPurchaseException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleSelfPurchaseException(SelfPurchaseException ex, Model model) {
        model.addAttribute("errorMessage", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(Exception.class)
    public String handleGlobalException(Exception ex, Model model) {
        model.addAttribute("errorMessage", ex.getMessage());
        return "error";
    }
}
