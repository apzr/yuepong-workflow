## SpringBoot2集成Activiti7
SpringBoot2集成Activiti7、Swagger、Druid

#### 1.环境 
- IDEA
- Spring Boot 2.1.13
>由于Activiti 7.1.M6依赖的spring-core是5.1.x，所以不能使用依赖于spring-core 5.2.x的Spring Boot 2.2.x
- Activiti 7.1.0.M6
- Swagger 2.9.2
- Druid 1.1.20
- mysql 5.7.33
- mybatis 2.1.1
- JAVA 8

#### 2.pom.xml
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>2.1.13.RELEASE</version>
        <relativePath/> <!-- lookup parent from repository -->
    </parent>
    <groupId>com.yuepong.workflow</groupId>
    <artifactId>yuepong-workflow</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>yuepong-workflow</name>
    <description>Yuepong Workflow</description>

    <properties>
        <java.version>1.8</java.version>
        <activiti.version>7.1.0.M6</activiti.version>
        <mybatis.version>2.1.1</mybatis.version>
        <swagger.version>2.9.2</swagger.version>
        <druid.version>1.1.20</druid.version>
        <groovy.version>3.0.2</groovy.version>
    </properties>
    <dependencyManagement>
        <dependencies>
            <!-- https://mvnrepository.com/artifact/org.activiti.dependencies/activiti-dependencies -->
            <dependency>
                <groupId>org.activiti.dependencies</groupId>
                <artifactId>activiti-dependencies</artifactId>
                <version>${activiti.version}</version>
                <type>pom</type>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter</artifactId>
            <version>${mybatis.version}</version>
        </dependency>

        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-boot-starter</artifactId>
            <version>3.4.0</version>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-generator</artifactId>
            <version>3.4.0</version>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-starter-security -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>

        <dependency>
            <groupId>mysql</groupId>
            <artifactId>mysql-connector-java</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- https://mvnrepository.com/artifact/com.alibaba/druid -->
        <dependency>
            <groupId>com.alibaba</groupId>
            <artifactId>druid</artifactId>
            <version>${druid.version}</version>
        </dependency>

        <!--   测试会用到fastjson     -->
        <dependency>
            <groupId>com.alibaba</groupId>
            <artifactId>fastjson</artifactId>
            <version>1.2.66</version>
        </dependency>

        <!--        简化log引用等-->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>

        <dependency>
            <groupId>org.activiti</groupId>
            <artifactId>activiti-spring-boot-starter</artifactId>
            <version>${activiti.version}</version>
        </dependency>

        <!-- 生成流程图 -->
        <dependency>
            <groupId>org.activiti</groupId>
            <artifactId>activiti-image-generator</artifactId>
            <version>${activiti.version}</version>
        </dependency>

        <!-- https://mvnrepository.com/artifact/org.activiti/activiti-bpmn-model -->
		<dependency>
			<groupId>org.activiti</groupId>
			<artifactId>activiti-bpmn-model</artifactId>
			<version>${activiti.version}</version>
		</dependency>
		<!--&lt;!&ndash; https://mvnrepository.com/artifact/org.activiti/activiti-bpmn-converter &ndash;&gt;-->
		<dependency>
			<groupId>org.activiti</groupId>
			<artifactId>activiti-bpmn-converter</artifactId>
			<version>${activiti.version}</version>
		</dependency>
        <!-- https://mvnrepository.com/artifact/org.activiti/activiti-json-converter -->
        <dependency>
            <groupId>org.activiti</groupId>
            <artifactId>activiti-json-converter</artifactId>
            <version>${activiti.version}</version>
        </dependency>


        <!-- https://mvnrepository.com/artifact/math.geom2d/javaGeom -->
        <dependency>
            <groupId>math.geom2d</groupId>
            <artifactId>javaGeom</artifactId>
            <version>0.11.1</version>
        </dependency>

        <!--
        URL： https://mvnrepository.com/artifact/org.codehaus.groovy/groovy-all
        说明：非必须，测试groovy脚本用
        -->
        <!--        <dependency>-->
        <!--            <groupId>org.codehaus.groovy</groupId>-->
        <!--            <artifactId>groovy-all</artifactId>-->
        <!--            <version>${groovy.version}</version>-->
        <!--            <type>pom</type>-->
        <!--        </dependency>-->

        <!--swagger依赖-->
        <!--  https://mvnrepository.com/artifact/io.springfox/springfox-swagger2 -->
        <dependency>
            <groupId>io.springfox</groupId>
            <artifactId>springfox-swagger2</artifactId>
            <version>${swagger.version}</version>
            <exclusions>
                <exclusion>
                    <groupId>io.swagger</groupId>
                    <artifactId>swagger-annotations</artifactId>
                </exclusion>
                <exclusion>
                    <groupId>io.swagger</groupId>
                    <artifactId>swagger-models</artifactId>
                </exclusion>
            </exclusions>
        </dependency>
        <!-- 解决进入swagger页面报类型转换错误，排除2.9.2中的引用，
        手动增加1.5.21以上版本，这里选1.6.0版本-->
        <dependency>
            <groupId>io.swagger</groupId>
            <artifactId>swagger-annotations</artifactId>
            <version>1.6.0</version>
        </dependency>
        <dependency>
            <groupId>io.swagger</groupId>
            <artifactId>swagger-models</artifactId>
            <version>1.6.0</version>
        </dependency>

        <!-- https://mvnrepository.com/artifact/io.springfox/springfox-swagger-ui -->
        <dependency>
            <groupId>io.springfox</groupId>
            <artifactId>springfox-swagger-ui</artifactId>
            <version>${swagger.version}</version>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>

        <dependency>
            <groupId>com.yuepong.jdev</groupId>
            <artifactId>jdev-api-base</artifactId>
			<version>1.0-SNAPSHOT</version>
        </dependency>
		<dependency>
            <groupId>com.yuepong.jdev</groupId>
            <artifactId>jdev-common</artifactId>
            <version>1.0-SNAPSHOT</version>
        </dependency>

    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>

        <!--设置maven打包.xml文件和properties文件到源目录，否则maven默认不打包，只打包class文件>
        <resources>
          <resource>
            <directory>src/main/java</directory>
            <includes>
              <include>**/*.properties</include>
              <include>**/*.xml</include>
            </includes>
            <filtering>false</filtering>
          </resource>
          <resource>
            <directory>src/main/resources</directory>
          </resource>
        </resources-->
    </build>
</project>

```
#### 4.MySQL脚本
例子中使用了 org.springframework.security，所以数据库中，需要先建库建表：
MySQL用的版本是5.7.33， 建库：
```SQL
SELECT version();
+-----------+
| version() |
+-----------+
| 5.7.33    |
+-----------+

DROP DATABASE IF EXISTS ACTIVITI7;
CREATE DATABASE ACTIVITI7;
```
建表：在首次运行项目时`/sql/schema.sql`文件会自动初始化自定义的四个表，其余`Activiti`的表会自动创建，不用做多余操作
```SQL
USE ACTIVITI7;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `s_sys_flow_b`  (
  `id` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT 'id',
  `h_id` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT '主表id',
  `node` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT '流程节点',
  `node_type` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '节点类型',
  `node_skip` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '节点类型',
  `field` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '业务变量',
  `conditions` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '条件',
  `value` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '值',
  `operation` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '操作人',
  `oper_name` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '操作人',
  `user_type` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '操作人类型',
  `next_node` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '下一个节点',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = DYNAMIC;

CREATE TABLE IF NOT EXISTS `s_sys_flow_h`  (
  `id` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT 'id',
  `sys_model` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT '系统模块',
  `sys_disable` tinyint(1) NOT NULL DEFAULT 0 COMMENT '激活',
  `flow_id` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT '模型id',
  `deployment_id` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT '部署id',
  `version` int(11) UNSIGNED NULL DEFAULT NULL COMMENT '版本',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = DYNAMIC;

CREATE TABLE IF NOT EXISTS `s_sys_task_b`  (
  `id` varchar(64) CHARACTER SET utf8 COLLATE utf8_bin NOT NULL COMMENT 'id',
  `h_id` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT '主表id',
  `node` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT '流程节点(发起人为开始节点)',
  `user` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '操作人',
  `user_name` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '操作人',
  `user_type` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '操作人类型',
  `record` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '操作记录(1,2,3 同意,作废,打回草稿)',
  `opinion` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '操作意见',
  `time` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '操作时间',
  `oper_time` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '停留时长(从上个节点结束的时间到当前节点操作的时间)',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = DYNAMIC;

CREATE TABLE IF NOT EXISTS `s_sys_task_h`  (
  `id` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT 'uuid',
  `s_key` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT 's_sys_flow_h绑定的系统模块',
  `s_id` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT '业务数据id',
  `task_id` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT '流程启动成功后返回的实例id',
  `route` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL,
  `status` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '完成状态',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;

```

#### 4.application.yml
```yaml
spring:
  profiles:
    active: dev

  #Activiti property configuration
  activiti:
    database-schema-update: true
    job-executor-activate: true # asyncExecutorEnabled属性设置设置true后将代替那些老的Job executor
    history-level: full
    db-history-used: true
    #async-executor-activate: true
    check-process-definitions: true # 自动部署验证设置:true-开启（默认）、false-关闭

    druid:
      initial-size: 1
      max-active: 20
      min-idle: 3
      max-wait: 60000
      pool-prepared-statements: true
      max-pool-prepared-statement-per-connection-size: 20
      filters: stat,wall,slf4j
      connection-properties=druid.stat.mergeSql=true;druid.stat.slowSqlMillis: 5000

logging:
  level:
    com.ascendant: debug
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} %clr(-%5p)  %clr(${PID:- }){magenta} --- %clr([%15.15t]){faint} %highlight(%-80.80logger{300}){cyan} %clr(:) %m %n%wEx"

mybatis-plus:
  mapper-locations: classpath*:mapper/**/*Mapper.xml
  type-aliases-package: com.yuepong.workflow.dto
  configuration:
    #log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
    log-impl: org.apache.ibatis.logging.nologging.NoLoggingImpl

swagger:
  enabled: true
```
特别说明：  
 - `check-process-definitions：`自动部署验证，若为true则会判断该定义是否已经部署，没部署则部署，否则不部署；若为false则不管流程是否已经部署都重新部署一遍！
 - `mvn clean package`打包后执行`java -jar {package name}`启动
 - 项目做得比较赶，没有重构，重构在`refactor`分支进行，遵循只新增不改的原则，保证原接口声明废气但是能用
 - [源码地址](https://github.com/apzr/yuepong-workflow)