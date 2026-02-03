package com.ordererp.backend.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

/**
 * Fail fast if the backend is started with an outdated/cached classpath that is missing EasyExcel.
 * Without this, the app may boot but "export/template/import" endpoints will throw at runtime (500).
 */
@Configuration
public class ExcelDependencyGuardConfig {
    private static final Logger log = LoggerFactory.getLogger(ExcelDependencyGuardConfig.class);

    @PostConstruct
    public void checkEasyExcelPresent() {
        try {
            Class.forName("com.alibaba.excel.EasyExcel");
            log.info("EasyExcel dependency detected.");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "EasyExcel dependency is missing from the runtime classpath. " +
                            "Please reload Maven dependencies and restart the backend, " +
                            "or run the packaged jar (spring-boot jar includes BOOT-INF/lib).",
                    e);
        }
    }
}

