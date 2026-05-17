package com.shiqian.resource.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("时迁 - 资源服务 API")
                        .version("1.0.0")
                        .description("校园资源共享平台 - 资源服务接口文档")
                        .contact(new Contact().name("shiqian").url("https://github.com/shiqian")));
    }
}
