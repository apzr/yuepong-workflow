package com.yuepong.workflow;

import org.activiti.engine.*;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.junit.Test;


import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * @author : Alex Hu
 * date : 2020/3/25 上午10:20
 * description :
 */
public class LeaveWithApprovalProcess {
   private String BPMN_PATH = "processes/";

    @Test
    public void testLeaveWithApprovalProcess() {

        //流程定义的 Key
        String processDefinitionKey = "leaveWithApprovalProcess";

        //流程引擎
        ProcessEngine processEngine = ProcessEngineConfiguration
                .createStandaloneInMemProcessEngineConfiguration().buildProcessEngine();

        /**
         * 部署流程
         */
        RepositoryService repositoryService = processEngine.getRepositoryService();
        String bpmnFileName = BPMN_PATH + "leaveWithApprovalProcess.bpmn";
        repositoryService.createDeployment().addInputStream("leaveWithApprovalProcess.bpmn", this.getClass()
                .getClassLoader().getResourceAsStream(bpmnFileName)).deploy();

        //流程定义
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .singleResult();
        //确认流程定义
        assertEquals(processDefinitionKey, definition.getKey());


        /**
         * 启动流程 【1】
         */
        RuntimeService runtimeService = processEngine.getRuntimeService();
        Map<String, Object> variables = new HashMap<String, Object>();
        //申请人名称
        variables.put("applyUser", "安妙依");
        //请假天数
        variables.put("days", 5);
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey
                (processDefinitionKey, variables);
        //确认流程实例
        assertNotNull(processInstance);
        System.out.printf("instance_id=%s;definition_id=%s\n", processInstance.getId(),
                processInstance.getProcessDefinitionId());

        /**
         * 流转到【审批】节点
         */
        TaskService taskService = processEngine.getTaskService();
        Task deptLeaderTask = taskService.createTaskQuery().taskCandidateGroup
                ("deptLeader").singleResult();
        //确认用户任务
        assertNotNull(deptLeaderTask);
        //确认任务名称
        assertEquals("审批", deptLeaderTask.getName());
        //用户 唐三 签收该任务 【2】
        taskService.claim(deptLeaderTask.getId(), "唐三");
        //用户 唐三 签署意见，审批通过
        variables = new HashMap<String, Object>();
        variables.put("approved", "true");
        taskService.complete(deptLeaderTask.getId(), variables);


        /**
         * 流转到【输出审批结果】节点 【3】
         */
        /**
         * 流程结束
         */
        deptLeaderTask = taskService.createTaskQuery().taskCandidateGroup("deptLeader")
                .singleResult();
        //任务流转到下一节点，所以当前节点没有任务
        assertNull(deptLeaderTask);
        //通过历史接口，统计已完成的流程数量
        HistoryService historyService = processEngine.getHistoryService();
        long count = historyService.createHistoricProcessInstanceQuery().count();
        assertEquals(1, count);


    }
}

