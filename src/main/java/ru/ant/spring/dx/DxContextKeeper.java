package ru.ant.spring.dx;

import lombok.Getter;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class DxContextKeeper implements ApplicationContextAware {
    @Getter
    private static ApplicationContext ctx;

    @Override
    public void setApplicationContext(@NonNull ApplicationContext ctx) throws BeansException {
        DxContextKeeper.ctx = ctx;
    }
}
