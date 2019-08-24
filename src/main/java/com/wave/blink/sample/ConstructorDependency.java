package com.wave.blink.sample;

import com.wave.blink.di.BlinkInject;
import com.wave.blink.di.BlinkService;

/**
 * TODO: add comment here
 * <p>
 * User: vipultiwari
 * Date: 24-08-2019
 * Time: 16:19
 */
@BlinkService
public class ConstructorDependency {

    public void printDetail() {
        System.out.println("In ConstructorDependency#printDetail");
    }
}
