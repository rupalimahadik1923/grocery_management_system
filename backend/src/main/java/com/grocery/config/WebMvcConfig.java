package com.grocery.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Resolve frontend folder relative to current working directory or project root
        File frontendDir = new File("frontend");
        if (!frontendDir.exists()) {
            frontendDir = new File("../frontend");
        }

        String location = "file:" + frontendDir.getAbsolutePath().replace("\\", "/") + "/";

        registry.addResourceHandler("/**")
                .addResourceLocations(location, "classpath:/static/", "classpath:/public/")
                .setCachePeriod(0);
    }
}
