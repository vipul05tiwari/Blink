package com.wave.blink.sample;

/**
 * TODO: add comment here
 * <p>
 * User: vipultiwari
 * Date: 24-08-2019
 * Time: 16:05
 */
public class SampleFactoryBean {

    private String name;

    public SampleFactoryBean(String name) {
        this.name = name;
    }

    public String greeting(){
        return "Hello, "+name;
    }
}
