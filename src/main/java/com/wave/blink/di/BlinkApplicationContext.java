package com.wave.blink.di;

import com.mxgraph.layout.mxCircleLayout;
import com.mxgraph.layout.mxIGraphLayout;
import com.mxgraph.util.mxCellRenderer;
import org.apache.commons.collections4.CollectionUtils;
import org.atteo.classindex.ClassIndex;
import org.jgrapht.DirectedGraph;
import org.jgrapht.alg.CycleDetector;
import org.jgrapht.ext.JGraphXAdapter;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.TopologicalOrderIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TODO: add comment here
 * <p>
 * User: vipultiwari
 * Date: 29-04-2019
 * Time: 17:26
 */
public class BlinkApplicationContext {

    private Logger logger = LoggerFactory.getLogger(BlinkApplicationContext.class);

    private  Map<Class, Set<Field>> classTpInjectFieldMap = new ConcurrentHashMap<>(64);
    private  Map<Class, Object> singletonCache = new ConcurrentHashMap<>(64);
    private DirectedGraph<Class<?>, DefaultEdge> directedGraph;
    private Map<Class<?>, Method> factoryBeanMethodMapping;
    private Map<Class<?>, Constructor> beanConstructorMapping = new HashMap<>();
    private Set<Class<?>> annotatedClassSet;

    public BlinkApplicationContext() {

        logger.info("Loading configuration ...");
        Iterable<Class<?>> configClasses = ClassIndex.getAnnotated(BlinkConfiguration.class);
        Set<Class<?>> configClassesSet = new HashSet<Class<?>>((Collection) configClasses);
        try {
            loadConfiguration(configClassesSet);
        } catch (IOException | InstantiationException | IllegalAccessException e) {
            throw new BlinkApplicationContextException("Exception occurred while loading config ", e);
        }

        logger.info("Building application context ...");

        directedGraph = new DefaultDirectedGraph<Class<?>, DefaultEdge>(DefaultEdge.class);

        //Get classes annotated with @TpService
        Iterable<Class<?>> annotatedClass = ClassIndex.getAnnotated(BlinkService.class);
        annotatedClassSet = new HashSet<Class<?>>((Collection) annotatedClass);
        logger.info("Bean annotated with @TpService {}", annotatedClassSet);

        Iterable<Class<?>> factoryClasses = ClassIndex.getAnnotated(BlinkFactory.class);

        logger.info("Detected factory classes {}", factoryClasses);

        //Get method annotated with @TpBean in class annotated with @TpFactory
        factoryBeanMethodMapping = getFactoryBeanMethodMapping();
        logger.info("Bean defined in factory classes with @Bean {}", factoryBeanMethodMapping.keySet());

        Set<Class<?>> beans = new HashSet<>();
        beans.addAll(annotatedClassSet);
        beans.addAll(factoryBeanMethodMapping.keySet());
        beans.addAll(configClassesSet);

        if(beans.size() <= 0){
            logger.info("No bean found, completed bean creation");
            return;
        }

        for (Class<?> bean : beans) {
            directedGraph.addVertex(bean);
        }

        for (Class<?> bean : beans) {
            if( ! directedGraph.containsVertex(bean))
                throw new BlinkApplicationContextException("No Bean found for "+bean+", Please either annotate this class " +
                        "with @TpService or create bean in one of factory class "+factoryClasses);

            if(annotatedClassSet.contains(bean)){
                //Constructor Injection
                Set<Class<?>> annotatedConstructorParameters = getConstructorParametersAnnotatedWith(bean, BlinkInject.class);
                for (Class<?> constructorParameter : annotatedConstructorParameters) {
                    if( ! directedGraph.containsVertex(constructorParameter))
                        throw new BlinkApplicationContextException("["+bean +"] is dependent on ["+constructorParameter+"] " +
                                "but no Bean found for "+constructorParameter+", Please either annotate this class " +
                                "with @TpService or create bean in one of factory class "+factoryClasses);

                    directedGraph.addEdge(bean, constructorParameter);
                }

                //Setter Injection
                Set<Field> annotatedClassFieldSet = getFieldsAnnotatedWith(bean, BlinkInject.class);
                for (Field annotatedClassField : annotatedClassFieldSet) {
                    Class<?> annotatedFieldType = annotatedClassField.getType();
                    if( ! directedGraph.containsVertex(annotatedFieldType))
                        throw new BlinkApplicationContextException("["+bean +"] is dependent on ["+annotatedFieldType+"] " +
                                "but no Bean found for "+annotatedFieldType+", Please either annotate this class " +
                                "with @TpService or create bean in one of factory class "+factoryClasses);

                    directedGraph.addEdge(bean, annotatedFieldType);
                }
            } else if(factoryBeanMethodMapping.containsKey(bean)){
                Method method = factoryBeanMethodMapping.get(bean);
                Set<Class<?>> methodParameterTypeSet = new HashSet<>(Arrays.asList(method.getParameterTypes()));
                if(CollectionUtils.isNotEmpty(methodParameterTypeSet)){
                    for (Class<?> methodParameterType : methodParameterTypeSet) {
                        if( ! directedGraph.containsVertex(methodParameterType))
                            throw new BlinkApplicationContextException("["+bean +"] is dependent on ["+methodParameterType+"] " +
                                    "but no Bean found for "+methodParameterType+", Please either annotate this class " +
                                    "with @TpService or create bean in one of factory class "+factoryClasses);

                        directedGraph.addEdge(bean, methodParameterType);
                    }
                }
            } else {
                if( ! bean.isAnnotationPresent(BlinkConfiguration.class)){
                    throw new BlinkApplicationContextException("No Bean found for "+bean+", Please either annotate this class " +
                            "with @TpService or create bean in one of factory class "+factoryClasses);
                }
            }

        }

        //Check cycles
        CycleDetector<Class<?>, DefaultEdge> cycleDetector
                = new CycleDetector<Class<?>, DefaultEdge>(directedGraph);

        if(cycleDetector.detectCycles()){
            Set<Class<?>> cycleVertices = cycleDetector.findCycles();
            throw new BlinkApplicationContextException("Dependency cycle detected "+cycleVertices);
        }

        //Stack to hold bean in order they will be created
        Stack<Class<?>> stackOfBeans = new Stack<>();

        TopologicalOrderIterator<Class<?>, DefaultEdge> depthFirstIterator
                = new TopologicalOrderIterator<Class<?>, DefaultEdge>(directedGraph);

        while(depthFirstIterator.hasNext()){
            Class<?> next = (Class<?>) depthFirstIterator.next();
            stackOfBeans.push(next);
        }

        while (!stackOfBeans.empty())
            createBeans(stackOfBeans.pop());

    }

    private void loadConfiguration(Set<Class<?>> configClassesSet) throws IOException, InstantiationException, IllegalAccessException {
        for (Class<?> configClass : configClassesSet) {
            BlinkConfiguration configuration = configClass.getAnnotation(BlinkConfiguration.class);
            String filename = configuration.filename();

            InputStream in = this.getClass().getClassLoader().getResourceAsStream(filename);

            Properties properties = new Properties();
            properties.load(in);

            Object propertyBean = createPropertyBean(properties, configClass);
            singletonCache.put(configClass, propertyBean);
        }
    }

    private Object createPropertyBean(Properties properties, Class<?> configClass) throws IllegalAccessException, InstantiationException {
        Object configInstance = configClass.newInstance();

        Field[] configFields = configClass.getDeclaredFields();
        for (Field configField : configFields) {
            if(configField.isAnnotationPresent(BlinkValue.class)){
                BlinkValue fieldAnnotation = configField.getAnnotation(BlinkValue.class);
                String configFieldSetterMethodName = getSetterMethodNameByFieldName(configField.getName());

                String propertyValue = properties.getProperty(fieldAnnotation.property());

                try {
                    Method annotatedClassMethod = configClass.getMethod(configFieldSetterMethodName, configField.getType());
                    annotatedClassMethod.invoke(configInstance, configField.getType() == int.class ? Integer.parseInt(propertyValue): propertyValue);
                } catch (NoSuchMethodException | InvocationTargetException e) {
                    //In case of exception directly set value*/
                    configField.setAccessible(true);
                    configField.set(configInstance, configField.getType() == int.class ? Integer.parseInt(propertyValue): propertyValue);
                }
            }
        }

        return configInstance;

    }

    private void createBeans(Class<?> bean) {

        logger.info("Creating bean ========> "+ bean);

        if(singletonCache.containsKey(bean)){
            return;
        }

        try {
            if (annotatedClassSet.contains(bean)) {
                createAnnotatedBean(bean);
            } else if (factoryBeanMethodMapping.containsKey(bean)) {
                createFactoryBean(bean);
            }
        }catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
            throw new BlinkApplicationContextException("Exception occurred while creating bean : "+bean, e);
        }
    }

    private void createFactoryBean(Class<?> bean) throws IllegalAccessException, InstantiationException, InvocationTargetException {
        Method method = factoryBeanMethodMapping.get(bean);

        Object methodDeclaringClassInstance = method.getDeclaringClass().newInstance();

        Class<?>[] parameterTypes = method.getParameterTypes();

        Object[] parameterInstance = new Object[0];

        if(parameterTypes != null && parameterTypes.length > 0){
            parameterInstance = new Object[parameterTypes.length];
            for (int i = 0; i < parameterTypes.length; i++) {
                parameterInstance[i] = singletonCache.get(parameterTypes[i]);
            }
        }

        invokeMethod(bean, method, methodDeclaringClassInstance, parameterInstance);
    }

    private void invokeMethod(Class<?> bean, Method method, Object methodDeclaringClassInstance, Object[] parameterInstance) throws InvocationTargetException, IllegalAccessException {
        Object beanInstance = null;
        try{
            beanInstance = method.invoke(methodDeclaringClassInstance, parameterInstance);
        } catch (IllegalAccessException e){
            method.setAccessible(true);
            beanInstance = method.invoke(methodDeclaringClassInstance, parameterInstance);
        }

        if (beanInstance != null)
            singletonCache.put(bean, beanInstance);
    }

    private void createAnnotatedBean(Class<?> annotatedClass) throws IllegalAccessException, InstantiationException, InvocationTargetException {
        Object annotatedClassInstance = createInstance(annotatedClass);
        Set<Field> fields = classTpInjectFieldMap.get(annotatedClass);
        if(fields.size() <= 0) {
            // does not have any dependent bean, create instance and register with cache
            singletonCache.put(annotatedClass, annotatedClassInstance);
            return;
        }

        for (Field injectAnnotatedField : fields) {
            Object injectAnnotatedFieldInstance = singletonCache.get(injectAnnotatedField.getType());

            String fieldSetterMethodName = getSetterMethodNameByFieldName(injectAnnotatedField.getName());

            try {
                Method annotatedClassMethod = annotatedClass.getMethod(fieldSetterMethodName, injectAnnotatedField.getType());
                annotatedClassMethod.invoke(annotatedClassInstance, injectAnnotatedFieldInstance);
            } catch (NoSuchMethodException | InvocationTargetException e) {
                //In case of exception directly set value*/
                injectAnnotatedField.setAccessible(true);
                injectAnnotatedField.set(annotatedClassInstance, injectAnnotatedFieldInstance);
            }
        }

        singletonCache.put(annotatedClass, annotatedClassInstance);
    }

    private Object createInstance(Class<?> annotatedClass) throws InstantiationException, IllegalAccessException, InvocationTargetException {
        Constructor constructor = beanConstructorMapping.get(annotatedClass);
        if(constructor == null){
            return annotatedClass.newInstance();
        }

        Class<?>[] parameterTypes = constructor.getParameterTypes();

        Object[] parameterInstance = new Object[0];

        if(parameterTypes != null && parameterTypes.length > 0){
            parameterInstance = new Object[parameterTypes.length];
            for (int i = 0; i < parameterTypes.length; i++) {
                parameterInstance[i] = singletonCache.get(parameterTypes[i]);
            }
        }

        return constructor.newInstance(parameterInstance);
    }

    private Set<Field> getFieldsAnnotatedWith(Class<?> clazz, Class<? extends Annotation> annotation) {

        if (classTpInjectFieldMap.containsKey(clazz)) {
            return classTpInjectFieldMap.get(clazz);
        }

        Field[] fields = clazz.getDeclaredFields();
        Set<Field> fieldSet = new HashSet<>();
        for (Field field : fields) {
            if (field.isAnnotationPresent(annotation)) {
                fieldSet.add(field);
            }
        }

        classTpInjectFieldMap.put(clazz, fieldSet);

        return fieldSet;
    }

    private Set<Class<?>> getConstructorParametersAnnotatedWith(Class<?> clazz, Class<? extends Annotation> annotation) {
        Constructor<?>[] declaredConstructors = clazz.getDeclaredConstructors();

        Constructor<?> annotatedConstructor = null;
        int constructorCount = 0;
        for (Constructor constructor : declaredConstructors) {
            if(constructor.isAnnotationPresent(annotation)){
                if(annotatedConstructor == null){
                    annotatedConstructor = constructor;
                }
                constructorCount ++;
            }
        }

        if(constructorCount == 0){
            return new HashSet<Class<?>>();
        }

        if(constructorCount > 1){
            throw new BlinkApplicationContextException("More than one Constructor marked with @TpInject for bean "+clazz);
        }

        beanConstructorMapping.put(clazz, annotatedConstructor);

        Class<?>[] parameterTypes = annotatedConstructor.getParameterTypes();

        return new HashSet<Class<?>>(Arrays.asList(parameterTypes));
    }

    private  Map<Class<?>, Method>  getFactoryBeanMethodMapping() {

        Map<Class<?>, Method> factoryBeanMethodMapping = new HashMap<>();

        Iterable<Class<?>> beanFactories = ClassIndex.getAnnotated(BlinkFactory.class);
        for (Class<?> beanFactory : beanFactories) {
            // iterate though the list of methods declared in the class represented by klass variable, and add those annotated with the specified annotation
            final List<Method> allMethods = new ArrayList<Method>(Arrays.asList(beanFactory.getDeclaredMethods()));
            for (final Method method : allMethods) {
                if (method.isAnnotationPresent(BlinkBean.class)) {
                    factoryBeanMethodMapping.put(method.getReturnType(), method);
                }
            }
        }
        return factoryBeanMethodMapping;
    }

    public void printGraph() throws IOException {

        JGraphXAdapter<Class<?>, DefaultEdge> graphAdapter =
                new JGraphXAdapter<>(directedGraph);
        mxIGraphLayout layout = new mxCircleLayout(graphAdapter);
        layout.execute(graphAdapter.getDefaultParent());

        BufferedImage image =
                mxCellRenderer.createBufferedImage(graphAdapter, null, 2, Color.WHITE, true, null);
        File imgFile = new File("src/test/resources/graph.png");
        ImageIO.write(image, "PNG", imgFile);
    }

    private String getSetterMethodNameByFieldName(String fieldName) {
        return "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
    }

    /**
     * Returns bean of given type if found in container else return null.
     *
     * @param beanClass
     * @param <T>
     * @return
     */
    public <T> T getBean(Class beanClass) {

        if (singletonCache.containsKey(beanClass)) {
            System.out.println("Instance returned from applicationScope cache");
            T inst = (T) beanClass.cast(singletonCache.get(beanClass));
            return inst;
        }

        return null;
    }

}
