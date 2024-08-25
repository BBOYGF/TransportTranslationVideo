package com.app;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * 获取spring容器工具类
 *
 * @Author guofan
 * @Create 2022/2/21
 */
@Component
public final class AppContext {
    public static ApplicationContext applicationContext;

    public AppContext() {

    }

    public static <T> T getBean(Class<T> requiredType) {
        if (applicationContext == null) {
            applicationContext = SpringApplication.run(Main.class);
        }
        T bean = applicationContext.getBean(requiredType);
        return bean;
    }


}
