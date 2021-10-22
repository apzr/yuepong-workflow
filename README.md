## SpringBoot2集成Activiti7
SpringBoot2集成Activiti7、Swagger、Druid

#### 1.环境 
- IDEA
- Spring Boot 2.1.13
>由于Activiti 7.1.M6依赖的spring-core是5.1.x，所以不能使用依赖于spring-core 5.2.x的Spring Boot 2.2.x
- Activiti 7.1.M6
- Swagger 2.9.2
- Druid 1.1.20
- mysql 8.1.19
- JAVA 11

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

        <dependency>
            <groupId>org.activiti</groupId>
            <artifactId>activiti-spring-boot-starter</artifactId>
            <version>${activiti.version}</version>
        </dependency>

        <!-- Activiti生成流程图 -->
        <dependency>
            <groupId>org.activiti</groupId>
            <artifactId>activiti-image-generator</artifactId>
            <version>${activiti.version}</version>
        </dependency>

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

    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>

</project>

```
#### 4.MySQL脚本
例子中使用了 org.springframework.security，所以数据库中，需要先建库建表：
MySQL用的版本是8.0.19， 建库：
```SQL
SELECT version();
+-----------+
| version() |
+-----------+
| 8.0.19    |
+-----------+

DROP DATABASE IF EXISTS ACTIVITI7;
CREATE DATABASE ACTIVITI7;
```
建表
```SQL
USE ACTIVITI7;

DROP TABLE IF EXISTS  users ;
CREATE TABLE users (
  username VARCHAR(20) NOT NULL,
  PASSWORD VARCHAR(150) NOT NULL,
  enabled TINYINT(1) DEFAULT NULL,
  PRIMARY KEY (username)
) ENGINE=INNODB DEFAULT CHARSET=utf8 ;

DROP TABLE IF EXISTS authorities;
CREATE TABLE authorities (
  id BIGINT(20) NOT NULL AUTO_INCREMENT,
  username VARCHAR(20) NOT NULL,
  authority VARCHAR(50) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=INNODB DEFAULT CHARSET=utf8;
```

#### 4.application.yml
```yaml
spring:
  #Activiti property configuration
  activiti:
    database-schema-update: true
    job-executor-activate: true # asyncExecutorEnabled属性设置设置true后将代替那些老的Job executor
    history-level: full
    db-history-used: true
    #async-executor-activate: true
    check-process-definitions: true # 自动部署验证设置:true-开启（默认）、false-关闭

  datasource:
    url: jdbc:mysql://localhost:3306/activiti7?useUnicode=true&characterEncoding=utf-8&useSSL=true&serverTimezone=UTC&nullCatalogMeansCurrent=true
    username: root
    password: password
    driver-class-name: com.mysql.cj.jdbc.Driver
    type: com.alibaba.druid.pool.DruidDataSource
    initialization-mode: always
    initialSize: 5
    minIdle: 5
    maxActive: 20
    maxWait: 60000
    timeBetweenEvictionRunsMillis: 60000
    minEvictableIdleTimeMillis: 300000
    validationQuery: SELECT 1 FROM DUAL
    testWhileIdle: true
    testOnBorrow: false
    testOnReturn: false
    poolPreparedStatements: true
#    filters: stat,wall,log4j
    maxPoolPreparedStatementPerConnectionSize: 20
    useGlobalDataSourceStat: true
    connectionProperties: druid.stat.mergeSql=true;druid.stat.slowSqlMillis=500

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


swagger:
  enabled: true
```
特别说明：  
check-process-definitions：自动部署验证，若为true则会判断该定义是否已经部署，没部署则部署，否则不部署；若为false则不管流程是否已经部署都重新部署一遍！