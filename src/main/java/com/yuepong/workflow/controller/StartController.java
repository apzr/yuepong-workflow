package com.yuepong.workflow.controller;

import com.yuepong.workflow.utils.RestMessgae;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.bpmn.model.FlowElement;
import org.activiti.bpmn.model.Process;
import org.activiti.engine.*;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.repository.ProcessDefinitionQuery;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.runtime.ProcessInstanceQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.*;

/**
 * @author Apr
 * @Description <p> 启动流程实例 </p>
 */
//@Transactional
//@Controller
//@Api(tags="启动流程实例")
public class StartController {

    private final RuntimeService runtimeService;

    @Autowired
    RepositoryService repositoryService;

    @Autowired
    private TaskService taskService;

    public StartController(RuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @PostMapping(path = "start")
    @ApiOperation(value = "根据流程key启动流程",notes = "每一个流程有对应的一个key这个是某一个流程内固定的写在bpmn内的")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "processKey",value = "流程key",dataType = "String",paramType = "query",example = ""),
             @ApiImplicitParam(name = "businessKey",value = "业务key",dataType = "String",paramType = "query",example = ""),
            @ApiImplicitParam(name = "user",value = "启动流程的用户",dataType = "String",paramType = "query",example = "")
    })
    public RestMessgae start(@RequestParam("user") String userKey,
                             @RequestParam("businessKey") String businessKey,
                             @RequestParam("processKey") String processKey) {
        HashMap<String, Object> variables=new HashMap<>(1);
        variables.put("userKey", userKey);

        RestMessgae restMessgae = new RestMessgae();
        ProcessInstance instance = null;
        try {
            instance = runtimeService.startProcessInstanceByKey(processKey, businessKey, variables);
        } catch (Exception e) {
            restMessgae = RestMessgae.fail("启动失败", e.getMessage());
            e.printStackTrace();
        }

        if (instance != null) {
            Map<String, String> result = new HashMap<>(2);
            // 流程实例ID
            result.put("processID", instance.getId());

            // 流程定义ID
            result.put("processDefinitionKey", instance.getProcessDefinitionId());
            restMessgae = RestMessgae.success("启动成功", result);
        }
        return restMessgae;
    }

    @PostMapping(path = "suspend")
    @ApiOperation(value = "根据实例id挂起流程",notes = "")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "processInstanceId",value = "流程实例ID",dataType = "String",paramType = "query",example = ""),
    })
    public RestMessgae suspend(@RequestParam("processInstanceId") String processInstanceId) {
        RestMessgae restMessgae;

        try {
            runtimeService.suspendProcessInstanceById(processInstanceId);
            restMessgae = RestMessgae.success("挂起成功", null);
        } catch (Exception e) {
            restMessgae = RestMessgae.fail("挂起失败", e.getMessage());
        }

        return restMessgae;
    }

    @PostMapping(path = "active")
    @ApiOperation(value = "根据实例id激活**挂起**的流程",notes = "")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "processInstanceId",value = "流程实例ID",dataType = "String",paramType = "query",example = ""),
    })
    public RestMessgae active(@RequestParam("processInstanceId") String processInstanceId) {
        RestMessgae restMessgae;

        try {
            runtimeService.activateProcessInstanceById(processInstanceId);
            restMessgae = RestMessgae.success("激活成功", null);
        } catch (Exception e) {
            restMessgae = RestMessgae.fail("激活失败", e.getMessage());
        }

        return restMessgae;
    }


    @PostMapping(path = "searchByKey")
    @ApiOperation(value = "根据流程key查询流程实例",notes = "查询流程实例")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "processKey",value = "流程key",dataType = "String",paramType = "query",example = ""),
    })
    public RestMessgae searchProcessInstance(@RequestParam("processKey") String processDefinitionKey){
        RestMessgae restMessgae = new RestMessgae();
        List<ProcessInstance> runningList = new ArrayList<>();
        try {
            ProcessInstanceQuery processInstanceQuery = runtimeService.createProcessInstanceQuery();
            runningList = processInstanceQuery.processDefinitionKey(processDefinitionKey).list();
        } catch (Exception e) {
            restMessgae = RestMessgae.fail("查询失败", e.getMessage());
            e.printStackTrace();
        }

        int size = runningList.size();
        if (size > 0) {
            List<Map<String, String>> resultList = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                ProcessInstance pi = runningList.get(i);
                Map<String, String> resultMap = new HashMap<>(2);
                // 流程实例ID
                resultMap.put("processID", pi.getId());
                // 流程定义ID
                resultMap.put("processDefinitionKey", pi.getProcessDefinitionId());
                resultList.add(resultMap);
            }
            restMessgae = RestMessgae.success("查询成功", resultList);
        }
        return restMessgae;
    }

    @PostMapping(path = "searchNodeByKey")
    @ApiOperation(value = "根据流程key查询流程节点",notes = "查询流程节点")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "processKey",value = "流程key",dataType = "String",paramType = "query",example = "process_1f6t06j5"),
    })
    public RestMessgae searchProcessInstanceNode(@RequestParam("processKey") String processDefinitionKey){
        RestMessgae restMessgae = new RestMessgae();
        List<ProcessInstance> runningList = new ArrayList<>();
        try {
            ProcessDefinitionQuery processDefinitionList = repositoryService.createProcessDefinitionQuery().processDefinitionKey(processDefinitionKey).latestVersion();
            ProcessDefinition processDefinition = processDefinitionList.list().get(0);
            //流程定义id
            String processDefinitionId = processDefinition.getId();
            BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
            Process process = bpmnModel.getProcesses().get(0);
            //获取所有节点
            Collection<FlowElement> flowElements = process.getFlowElements();

            restMessgae = RestMessgae.success("查询成功", flowElements);
        } catch (Exception e) {
            restMessgae = RestMessgae.fail("查询失败", e.getMessage());
            e.printStackTrace();
        }

        return restMessgae;
    }

    @PostMapping(path = "searchByID")
    @ApiOperation(value = "根据流程key查询流程实例",notes = "查询流程实例")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "processID",value = "流程实例ID",dataType = "String",paramType = "query",example = ""),
    })
    public RestMessgae searchByID(@RequestParam("processID") String processDefinitionID){
        RestMessgae restMessgae  = new RestMessgae();
        ProcessInstance pi = null;
        try {
            pi = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(processDefinitionID)
                    .singleResult();
        } catch (Exception e) {
            restMessgae = RestMessgae.fail("查询失败", e.getMessage());
            e.printStackTrace();
        }

        if (pi != null) {
            Map<String, String> resultMap = new HashMap<>(2);
            // 流程实例ID
            resultMap.put("processID", pi.getId());
            // 流程定义ID
            resultMap.put("processDefinitionKey", pi.getProcessDefinitionId());
            restMessgae = RestMessgae.success("查询成功", resultMap);
        }
        return restMessgae;
    }

    @PostMapping(path = "deleteProcessInstanceByKey")
    @ApiOperation(value = "根据流程实例key删除流程实例",notes = "根据流程实例key删除流程实例")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "processKey",value = "流程实例Key",dataType = "String",paramType = "query",example = ""),
    })
    public RestMessgae deleteProcessInstanceByKey(@RequestParam("processKey") String processDefinitionKey){
        RestMessgae restMessgae = new RestMessgae();
        List<ProcessInstance> runningList = new ArrayList<>();
        try {
            ProcessInstanceQuery processInstanceQuery = runtimeService.createProcessInstanceQuery();
            runningList = processInstanceQuery.processDefinitionKey(processDefinitionKey).list();
        } catch (Exception e) {
            restMessgae = RestMessgae.fail("删除失败", e.getMessage());
            e.printStackTrace();
        }

        int size = runningList.size();
        if (size > 0) {
            List<Map<String, String>> resultList = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                ProcessInstance pi = runningList.get(i);
                runtimeService.deleteProcessInstance(pi.getId(),"删除");
            }
            restMessgae = RestMessgae.success("删除成功", resultList);
        }
        return  restMessgae;
    }

    @PostMapping(path = "deleteProcessInstanceByID")
    @ApiOperation(value = "根据流程实例ID删除流程实例",notes = "根据流程实例ID删除流程实例")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "processID",value = "流程实例ID",dataType = "String",paramType = "query",example = ""),
    })
    public RestMessgae deleteProcessInstanceByID(@RequestParam("processID") String processDefinitionID){
        RestMessgae restMessgae = new RestMessgae();
        try {
            runtimeService.deleteProcessInstance(processDefinitionID,"删除" + processDefinitionID);
        } catch (Exception e) {
            restMessgae = RestMessgae.fail("删除失败", e.getMessage());
            return  restMessgae;
        }
        restMessgae = RestMessgae.success("删除成功", "");
        return  restMessgae;
    }
}
