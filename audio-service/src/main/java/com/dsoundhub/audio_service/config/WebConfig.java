package com.dsoundhub.audio_service.config;

import com.dsoundhub.audio_service.security.BanCheckInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Konfigurasi Web MVC untuk audio-service.
 * Mendaftarkan BanCheckInterceptor pada semua endpoint /api/**
 * agar setiap request dari user yang di-ban dapat ditolak secara real-time.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final BanCheckInterceptor banCheckInterceptor;

    public WebConfig(BanCheckInterceptor banCheckInterceptor) {
        this.banCheckInterceptor = banCheckInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(banCheckInterceptor)
                .addPathPatterns("/api/**");
    }
}
