package com.wave.blink.sample;

import com.wave.blink.di.BlinkApplicationContext;

/**
 * TODO: add comment here
 * <p>
 * User: vipultiwari
 * Date: 24-08-2019
 * Time: 16:02
 */
public class Application {

    public static void main(String[] args) {
        BlinkApplicationContext applicationContext = new BlinkApplicationContext();

        SampleFactoryBean sampleFactoryBean = applicationContext.getBean(SampleFactoryBean.class);
        System.out.println(sampleFactoryBean.greeting());

        Service service = applicationContext.getBean(Service.class);
        service.printDependency();
    }
}
