package com.ordererp.backend.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

/**
 * 如果后端在运行时 classpath 中缺少 EasyExcel（常见原因：依赖缓存/IDE 未刷新/使用了过期的 classpath），则在启动阶段直接失败（fail fast）。
 * 否则应用可能“看起来能启动”，但导出/模板/导入等接口会在运行时抛异常（500），定位成本更高。
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
