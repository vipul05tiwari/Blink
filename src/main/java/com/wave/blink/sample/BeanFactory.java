package com.wave.blink.sample;

import com.wave.blink.di.BlinkBean;
import com.wave.blink.di.BlinkFactory;

/**
 * TODO: add comment here
 * <p>
 * User: vipultiwari
 * Date: 24-08-2019
 * Time: 16:04
 */
@BlinkFactory
public class BeanFactory {

    @BlinkBean
    public SampleFactoryBean provideSampleFactoryBean(ApplicationConfig config){
        return new SampleFactoryBean(config.getName());
    }
}
