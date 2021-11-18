package com.yuepong.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuepong.workflow.dto.SysFlowExt;
import com.yuepong.workflow.mapper.*;
import org.activiti.engine.*;
import org.activiti.engine.TaskService;
import org.apache.ibatis.annotations.Param;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * BaseService
 * <p>
 * <br/>
 *
 * @author apr
 * @date 2021/11/18 15:08:31
 **/
public class BaseService {

    @Autowired
    protected ProcessEngine processEngine;

    @Autowired
    protected RepositoryService repositoryService;

    @Autowired
    protected RuntimeService runtimeService;

    @Autowired
    protected TaskService taskService;

    @Autowired
    protected HistoryService historyService;



    @Autowired
    protected SysTaskMapper sysTaskMapper;

    @Autowired
    protected SysTaskExtMapper sysTaskExtMapper;

    @Autowired
    protected SysFlowMapper sysFlowMapper;

    @Autowired
    protected SysFlowExtMapper sysFlowExtMapper;

    @Autowired
    protected BpmnByteMapper bpmnByteMapper;



    @Autowired
    protected ObjectMapper objectMapper;

}
