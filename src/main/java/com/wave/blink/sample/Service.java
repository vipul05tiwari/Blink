package com.wave.blink.sample;

import com.wave.blink.di.BlinkInject;
import com.wave.blink.di.BlinkService;

/**
 * TODO: add comment here
 * <p>
 * User: vipultiwari
 * Date: 24-08-2019
 * Time: 16:10
 */
@BlinkService
public class Service {

    @BlinkInject
    private SetterDependency setterDependency;

    private ConstructorDependency constructorDependency;

    @BlinkInject
    public Service(ConstructorDependency constructorDependency){
        this.constructorDependency = constructorDependency;
    }

    public void printDependency(){
        constructorDependency.printDetail();
        setterDependency.printDetail();
    }
}
