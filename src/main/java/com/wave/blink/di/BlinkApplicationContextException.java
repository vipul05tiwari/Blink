package com.wave.blink.di;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO: add comment here
 * <p>
 * User: vipultiwari
 * Date: 29-04-2019
 * Time: 18:59
 */
public class BlinkApplicationContextException extends RuntimeException {

    private Logger logger = LoggerFactory.getLogger(BlinkApplicationContextException.class);

    private String errorMessage;
    private Throwable cause;

    public BlinkApplicationContextException(String errorMessage){
        super(errorMessage);
        this.errorMessage = errorMessage;
        logger.error("Note : Since this dependency injection uses compile time binding" +
                ", please rebuild application is any changes done from last build");
    }

    public BlinkApplicationContextException(String errorMessage, Throwable cause){
        super(errorMessage, cause);
        this.errorMessage = errorMessage;
        this.cause = cause;
        logger.error("Note : Since this dependency injection uses compile time binding" +
                ", please rebuild application if any changes done from last build");
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
