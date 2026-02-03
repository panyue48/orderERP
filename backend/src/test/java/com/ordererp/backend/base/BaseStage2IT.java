package com.ordererp.backend.base;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alibaba.excel.EasyExcel;
import com.ordererp.backend.base.excel.ProductExcelRow;
import com.ordererp.backend.base.service.BaseExcelService;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
/**
 * 第二阶段（Base Data）集成测试：基础资料（商品/仓库/往来单位）中的“商品”为代表的 CRUD 规则 + Excel 导出能力。
 *
 * 技术栈/运行方式：
 * - JUnit 5
 * - Spring Boot Test + MockMvc：用 HTTP 方式走 Controller -> Service -> Repository 全链路
 * - Testcontainers MySQL：启动临时 MySQL 容器，避免依赖本机 erp_data
 * - Flyway：启动时迁移建表并写入必要的菜单/权限/初始化数据
 *
 * 这一类测试关注点：
 * 1) 逻辑删除下的唯一键策略：同编码删除后是否能“复活”旧记录（而不是插入新行导致唯一键冲突）
 * 2) Excel 导出链路是否能生成有效的 xlsx 内容（避免线上出现“导出失败/下载失败”）
 */
class BaseStage2IT {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.36")
            .withDatabaseName("erp_data")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void mysqlProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("app.datasource.expected-database", () -> "erp_data");
        registry.add("app.datasource.fail-on-mismatch", () -> "true");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    BaseExcelService excelService;

    @Test
    void admin_can_create_delete_and_revive_product_by_code() throws Exception {
        // 用例：商品“逻辑删除 + 复活”策略是否正确
        //
        // 背景：
        // - base_product.product_code 有唯一约束（uk），逻辑删除不会释放唯一值
        // - 若直接 insert，会因为唯一约束冲突而失败
        // - 当前工程实现是：若存在同 code 的 deleted=1 记录，则在 create 时复用该记录并把 deleted 置回 0（复活）
        //
        // 测试步骤：
        // 1) admin 创建商品 SKU-TC-REVIVE-001，记录返回 id1
        // 2) admin 删除该商品（逻辑删除）
        // 3) 再用相同 productCode 创建商品，期望返回同一个 id（id2==id1），表示“复活成功”
        String token = loginAndGetToken("admin", "123456");

        JsonNode created1 = createProduct(token, "SKU-TC-REVIVE-001", "测试商品1");
        long id1 = created1.get("id").asLong();
        assertTrue(id1 > 0);

        mockMvc.perform(delete("/api/base/products/{id}", id1).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        JsonNode created2 = createProduct(token, "SKU-TC-REVIVE-001", "测试商品1-复活");
        long id2 = created2.get("id").asLong();
        assertEquals(id1, id2, "should revive logically deleted record with same productCode");
    }

    @Test
    void excel_export_can_generate_xlsx_bytes() throws Exception {
        // 用例：Excel 导出能力是否可用（能生成非空 xlsx bytes）
        //
        // 为什么不直接测 Controller 的 /export：
        // - /export 直接写入 HttpServletResponse 的 OutputStream
        // - 在 MockMvc 场景下，流关闭时机/容器实现差异可能导致“Can not close IO”这类非业务问题
        // - 所以这里测试“核心导出链路”：BaseExcelService 能正确生成数据行，并能被 EasyExcel 正常写成 xlsx
        //
        // 测试步骤：
        // 1) admin 登录并创建一个商品，确保导出结果中有我们预期的行
        // 2) 调用 excelService.exportProducts 拿到导出行
        // 3) 用 EasyExcel 把行写进 ByteArrayOutputStream，最后断言 bytes 非空
        String token = loginAndGetToken("admin", "123456");

        createProduct(token, "SKU-TC-EXPORT-001", "导出测试商品");

        var rows = excelService.exportProducts(null);
        assertTrue(rows.size() >= 1);
        assertTrue(rows.stream().anyMatch(r -> "SKU-TC-EXPORT-001".equals(r.getProductCode())));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out, ProductExcelRow.class).sheet("Products").doWrite(rows);
        byte[] bytes = out.toByteArray();
        assertTrue(bytes.length > 100, "xlsx bytes should not be empty");
    }

    private JsonNode createProduct(String token, String productCode, String productName) throws Exception {
        // 测试辅助：通过 HTTP 调用创建商品接口（走完整鉴权 + Controller + Service + JPA 落库）
        //
        // 说明：
        // - 这里故意只传必填字段（productCode/productName），其余字段走默认值
        // - 若后续字段校验增强，这里是最容易暴露“最小创建”是否仍可用的地方
        MvcResult res = mockMvc.perform(post("/api/base/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode": "%s",
                                  "productName": "%s",
                                  "unit": "个",
                                  "status": 1
                                }
                                """.formatted(productCode, productName)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        // 测试辅助：登录并获取 token
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(res.getResponse().getContentAsString());
        JsonNode tokenNode = node.get("token");
        assertNotNull(tokenNode, "token should exist");
        return tokenNode.asText();
    }
}
