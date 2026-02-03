package com.ordererp.backend.system;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 第一阶段（System Core）集成测试：认证（JWT）、鉴权（RBAC/权限点）、动态菜单路由。
 *
 * 技术栈/运行方式：
 * - JUnit 5：测试框架
 * - Spring Boot Test + MockMvc：启动真实 Spring 容器，但用 MockMvc 直接调用 Controller（无需真正起
 * 8080 端口）
 * - Testcontainers MySQL：启动临时 MySQL 容器，测试数据独立、可重复
 * - Flyway：应用启动时自动执行 db/migration 迁移脚本，包含 sys_user/sys_role/sys_menu 等初始化数据
 *
 * 这一类测试主要回答三个问题：
 * 1) 未登录是否会被拦截？
 * 2) 登录后是否能拿到 token，并使用 token 访问受保护资源？
 * 3) 不同用户（不同角色/权限）是否能正确放行/拒绝对应接口？
 * 
 * //测试方法：进入backend目录打开命令行运行：mvn -U test -Dtest=SystemStage1IT
 * 
 */
class SystemStage1IT {
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

    @Test
    void unauthorized_requests_are_rejected() throws Exception {
        // 用例：未登录访问“必须登录”的接口应该被拦截（401 或 403 都算拦截成功）
        //
        // 说明：
        // - 本项目是无状态（JWT）认证，未携带 Bearer token 时通常会被 Spring Security 拦截。
        // - 不同的 entrypoint / exception handler 配置可能导致返回 401（未认证）或 403（拒绝访问）。
        // - 这里我们只断言是 4xx，表达“没登录就不能访问”的安全底线。
        mockMvc.perform(get("/api/system/user/profile"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void login_and_profile_work_for_admin() throws Exception {
        // 用例：管理员账号能登录并访问个人信息
        //
        // 测试目标：
        // 1) /api/auth/login 能够根据用户名密码签发 JWT
        // 2) 携带 Authorization: Bearer <token> 后，/api/system/user/profile 能返回当前用户信息
        //
        // 数据来源：
        // - 用户数据来自 Flyway 迁移脚本 V1__init.sql 的初始化（admin/123456）。
        String token = loginAndGetToken("admin", "123456");

        mockMvc.perform(get("/api/system/user/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"username\":\"admin\"")));
    }

    @Test
    void menu_routers_are_returned_for_admin() throws Exception {
        // 用例：登录后能拿到动态菜单路由（前端动态路由依赖）
        //
        // 测试目标：
        // - /api/system/menu/routers 根据“当前登录用户”返回可访问菜单树
        // - 返回结果中应包含关键路由（例如 /dashboard、/system）
        //
        // 说明：
        // - 这里不是精确校验整棵树（避免菜单调整导致测试频繁变更）
        // - 采用“包含关键 path”的方式验证主干能力可用。
        String token = loginAndGetToken("admin", "123456");

        MvcResult res = mockMvc.perform(get("/api/system/menu/routers").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        assertTrue(jsonContainsPath(root, "/dashboard"), "routers should contain /dashboard");
        assertTrue(jsonContainsPath(root, "/system"), "routers should contain /system");
    }

    @Test
    void rbac_blocks_forbidden_actions_for_manager() throws Exception {
        // 用例：经理账号已登录，但执行“没有权限”的操作应返回 403
        //
        // 测试目标：
        // - manager/123456 能登录（说明认证没问题）
        // - 但 manager 角色没有 base:product:add 权限
        // - 调用 POST /api/base/products 应被 @PreAuthorize 拦截，返回 403
        String token = loginAndGetToken("manager", "123456");

        mockMvc.perform(post("/api/base/products")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "productCode": "SKU-MGR-001",
                          "productName": "mgr",
                          "unit": "个",
                          "status": 1
                        }
                        """))
                .andExpect(status().isForbidden());
    }

    @Test
    void perms_endpoint_returns_authorities_for_manager() throws Exception {
        // 用例：/api/system/user/perms 能返回当前用户权限点集合
        //
        // 测试目标：
        // - 返回结果包含 manager 应有的权限（例如 dashboard:view）
        // - 不包含 manager 不应有的权限（例如 base:product:add）
        //
        // 说明：
        // - 这是前端“按钮级别权限控制（hasPerm）”的基础数据来源之一。
        String token = loginAndGetToken("manager", "123456");

        MvcResult res = mockMvc.perform(get("/api/system/user/perms").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String body = res.getResponse().getContentAsString();
        assertTrue(body.contains("dashboard:view"), "manager should have dashboard:view");
        assertFalse(body.contains("base:product:add"), "manager should not have base:product:add");
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        // 测试辅助方法：调用登录接口并解析 token
        //
        // 为什么要抽成方法：
        // - 多个用例都需要 token
        // - 统一对“token 非空”做断言，避免后续用例因为 token 为空而产生误导性失败
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("\"token\":\"\""))))
                .andReturn();

        JsonNode node = objectMapper.readTree(res.getResponse().getContentAsString());
        JsonNode tokenNode = node.get("token");
        assertNotNull(tokenNode, "token should exist");
        String token = tokenNode.asText();
        assertFalse(token.isBlank(), "token should not be blank");
        return token;
    }

    private static boolean jsonContainsPath(JsonNode node, String path) {
        // 小工具：递归查找路由树里是否出现过某个 path
        // 用于“菜单路由返回值是否包含关键路由”的弱校验（更稳健，不易受菜单细节变化影响）。
        if (node == null)
            return false;
        if (node.isArray()) {
            for (JsonNode n : node) {
                if (jsonContainsPath(n, path))
                    return true;
            }
            return false;
        }
        if (node.isObject()) {
            JsonNode p = node.get("path");
            if (p != null && path.equals(p.asText()))
                return true;
            JsonNode children = node.get("children");
            return jsonContainsPath(children, path);
        }
        return false;
    }
}
