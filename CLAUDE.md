# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 构建命令

**强制要求 Java 17**，Maven 位于 `D:/tool/apache-maven-3.9.5/bin/mvn`，JDK 17 位于 `D:/tool/jdk-17`。

```bash
# 设置环境
export JAVA_HOME="D:/tool/jdk-17"
export PATH="D:/tool/jdk-17/bin:$PATH"

# 编译全部模块（跳过测试）
"$MAVEN_HOME/bin/mvn" install -DskipTests -q

# 编译单个模块及其依赖
"$MAVEN_HOME/bin/mvn" install -pl talkai-chat -am -DskipTests

# 以 local profile 启动单个服务（推荐用于开发调试）
"$MAVEN_HOME/bin/mvn" spring-boot:run -f talkai-server/talkai-chat/pom.xml -Dspring-boot.run.profiles=local

# 或以 java -jar 运行（更可靠，避免 spring-boot:run 的 Lombok 重编译问题）
"$JAVA_HOME/bin/java" -jar -Dspring.profiles.active=local talkai-server/talkai-chat/target/talkai-chat-1.0.0-SNAPSHOT.jar

# 前端
cd talkai-web && npm run dev    # 开发服务器，端口 5173
cd talkai-web && npm run build  # 生产构建
```

## 模块架构

| 模块 | 端口 | 功能 |
|------|------|------|
| talkai-common | - | 共享库：JWT 工具、统一响应 `R<T>`、`UserContext`(ThreadLocal) |
| talkai-gateway | 8080 | API 网关 + JWT 认证过滤器 |
| talkai-auth | 8081 | 注册/登录，BCrypt，JWT 签发 |
| talkai-chat | 8082 | 多轮对话引擎，SSE 流式输出，工具调用编排 |
| talkai-model | 8083 | LLM 统一代理（DeepSeek / OpenAI） |
| talkai-agent | 8086 | MCP 服务器 + 工具执行（天气、搜索、数据库查询） |
| talkai-user/knowledge/search/file | 8084-8088 | 存根模块（仅 Application.java） |

`talkai-common` 通过 `@SpringBootApplication(scanBasePackages = "com.talkai")` 被所有模块共享，无需显式依赖。

## 配置体系

**两种运行模式**，通过 Spring profile 切换：

- **local 模式**：禁用 Nacos 自动配置，Gateway 直接路由到 `http://localhost:808X`，服务间通过硬编码 URL 通信。对应 `application-local.yml`。
- **Nacos 模式**（生产）：通过 Nacos 做服务发现和配置中心，Gateway 使用 `lb://service-name` 路由。

关键环境变量：`MYSQL_ADDR`、`MYSQL_USER`、`MYSQL_PASSWORD`、`REDIS_ADDR`、`DEEPSEEK_API_KEY`、`JWT_SECRET`。

## 核心数据流

### SSE 聊天流（最复杂的链路）

```
前端(fetch SSE) → Gateway(JWT验证) → ChatService
  → ModelService(代理DeepSeek/OpenAI) → 外部LLM API
  → ChatService(解析tool_calls) → AgentService(执行工具)
  → ChatService(工具结果回填，递归调用LLM) → 前端(流式输出)
```

### 工具调用机制

ChatService 启动时从 Agent 加载工具定义（`POST /api/agent/tools`），每次发消息时带上 `tools` + `tool_choice: "auto"`。LLM 返回的 `delta.tool_calls` 被累积解析，工具通过 `POST /api/agent/tool-call` 执行，结果作为 `role: "tool"` 消息回填给 LLM，最多递归 5 轮。

### JWT 认证

Gateway 的 `JwtAuthFilter`（order=-100）拦截除白名单外的所有请求：
- 白名单：`/api/auth/register`、`/api/auth/login`、`/api/agent/`
- 验证通过后转发 `X-User-Id` 和 `X-Username` 头给下游

## 数据库

MySQL `talkai` 库，三张核心表：`user`、`conversation`、`message`。使用 MyBatis-Plus，`deleted` 字段做逻辑删除。初始化脚本在 `sql/init.sql`，Docker Compose 会自动执行。
