package com.wave.blink.sample;

import com.wave.blink.di.BlinkConfiguration;
import com.wave.blink.di.BlinkValue;

/**
 * TODO: add comment here
 * <p>
 * User: vipultiwari
 * Date: 24-08-2019
 * Time: 16:23
 */
@BlinkConfiguration(filename = "application.properties")
public class ApplicationConfig {

    @BlinkValue(property = "name")
    private String name;

    public String getName() {
        return name;
    }
}
