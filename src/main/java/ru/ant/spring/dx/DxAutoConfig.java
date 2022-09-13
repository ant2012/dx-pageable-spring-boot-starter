package ru.ant.spring.dx;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(DxContextKeeper.class)
public class DxAutoConfig {
    @Bean
    @ConditionalOnMissingBean
    public DxContextKeeper dxContextKeeper(){
        return new DxContextKeeper();
    }
}
