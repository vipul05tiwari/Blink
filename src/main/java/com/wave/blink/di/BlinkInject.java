package com.wave.blink.di;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * TODO: add comment here
 * <p>
 * User: vipultiwari
 * Date: 29-04-2019
 * Time: 17:22
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.CONSTRUCTOR})
public @interface BlinkInject {

}
