package com.yuepong.workflow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author Apr
 */
@SpringBootApplication
@MapperScan("com.yuepong.workflow.mapper")
public class YuepongWorkflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(YuepongWorkflowApplication.class, args);
    }

}
