# Blink
Blink is a lightweight and fast dependency injection framework. Blink uses [classindex](https://github.com/atteo/classindex) and [jgrapht](https://github.com/jgrapht/jgrapht) to manage dependency.

In order to make class injectable class need to be annotated with `@BlinkService`

```java
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
```

Sample code to load application context
```java
public class Application {

    public static void main(String[] args) {
        BlinkApplicationContext applicationContext = new BlinkApplicationContext();

        SampleFactoryBean sampleFactoryBean = applicationContext.getBean(SampleFactoryBean.class);
        System.out.println(sampleFactoryBean.greeting());

        Service service = applicationContext.getBean(Service.class);
        service.printDependency();
    }
}
```

Blink supports the following feature
* Setter Injection
* Constructor Injection
* Bean Factory
* Auto property load

#### Setter Injection
For a setter, injection field needs to be annotated with `@BlinkInject`.

```java
    @BlinkInject
    private SetterDependency setterDependency;
```
#### Constructor Injection
For a Constructor, Injection Constructor needs to be annotated with `@BlinkInject`.

```java
    @BlinkInject
    public Service(ConstructorDependency constructorDependency){
        this.constructorDependency = constructorDependency;
    }
```

#### Bean Factory
Factory class can be used to create an injectable bean of third party class.

```java
@BlinkFactory
public class BeanFactory {

    @BlinkBean
    AmazonS3 provideAmazonS3(ApplicationConfiguration configuration){
        return AmazonS3ClientBuilder.standard().withRegion(configuration.getAwsRegionName()).build();
    }
}
```    

#### Auto property load
Blink can automatically load application configuration and map them to java bean.

```java
@BlinkConfiguration(filename = "application.properties") //application.properties should be available in class path
public class ApplicationConfig {

    @BlinkValue(property = "name")
    private String name;

    public String getName() {
        return name;
    }
}
```
application.properties
```
name=You are using blink
```


