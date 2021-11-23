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
 - check-process-definitions：自动部署验证，若为true则会判断该定义是否已经部署，没部署则部署，否则不部署；若为false则不管流程是否已经部署都重新部署一遍！
 - mvn clean package打包jar运行
 - 项目做得比较赶, 没有重构, 重构在refactor分支进行, 遵循只新增不改的原则, 保证原接口声明废气但是能用
 - 源码地址https://github.com/apzr/yuepong-workflow