package com.yuepong.workflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yuepong.jdev.api.bean.ResponseResult;
import com.yuepong.jdev.code.CodeMsgs;
import com.yuepong.jdev.exception.BizException;
import com.yuepong.workflow.dto.SysFlow;
import com.yuepong.workflow.dto.SysFlowExt;
import com.yuepong.workflow.dto.SysTask;
import com.yuepong.workflow.dto.SysTaskExt;
import com.yuepong.workflow.mapper.SysFlowExtMapper;
import com.yuepong.workflow.mapper.SysFlowMapper;
import com.yuepong.workflow.mapper.SysTaskExtMapper;
import com.yuepong.workflow.mapper.SysTaskMapper;
import com.yuepong.workflow.utils.RestMessgae;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.activiti.bpmn.model.*;
import org.activiti.engine.*;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Apr
 * @Description <p> 任务相关接口 </p>
 */
@Controller
@Api(tags = "任务相关接口")
public class TaskController {

    ProcessEngine processEngine = ProcessEngines.getDefaultProcessEngine();

    private final TaskService taskService;

    @Autowired
    RepositoryService repositoryService;

    @Autowired
    RuntimeService runtimeService;

    @Autowired
    SysTaskMapper sysTaskMapper;

    @Autowired
    SysTaskExtMapper sysTaskExtMapper;

    @Autowired
    SysFlowMapper sysFlowMapper;


    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping(path = "findTaskByAssignee")
    @ApiOperation(value = "根据流程assignee查询当前人的个人任务", notes = "根据流程assignee查询当前人的个人任务")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "assignee", value = "代理人（当前用户）", dataType = "String", paramType = "query", example = ""),
    })
    public RestMessgae findTaskByAssignee(@RequestParam("assignee") String assignee) {
        RestMessgae restMessgae = new RestMessgae();

        //创建任务查询对象
        List<Task> taskList;
        try {
            taskList = taskService.createTaskQuery()
                    //指定个人任务查询
                    .taskAssignee(assignee)
                    .list();
        } catch (Exception e) {
            restMessgae = RestMessgae.fail("查询失败", e.getMessage());
            e.printStackTrace();
            return restMessgae;
        }

        if (taskList != null && taskList.size() > 0) {
            List<Map<String, String>> resultList = new ArrayList<>();
            for (Task task : taskList) {
                Map<String, String> resultMap = new HashMap<>(7);
                /* 任务ID */
                resultMap.put("taskID", task.getId());

                /* 任务名称 */
                resultMap.put("taskName", task.getName());

                /* 任务的创建时间 */
                resultMap.put("taskCreateTime", task.getCreateTime().toString());

                /* 任务的办理人 */
                resultMap.put("taskAssignee", task.getAssignee());

                /* 流程实例ID */
                resultMap.put("processInstanceId", task.getProcessInstanceId());

                /* 执行对象ID */
                resultMap.put("executionId", task.getExecutionId());

                /* 流程定义ID */
                resultMap.put("processDefinitionId", task.getProcessDefinitionId());
                resultList.add(resultMap);
            }
            restMessgae = RestMessgae.success("查询成功", resultList);
        } else {
            restMessgae = RestMessgae.success("查询成功", null);
        }

        return restMessgae;
    }

    @PostMapping(path = "completeTask")
    @ApiOperation(value = "完成任务", notes = "完成任务，任务进入下一个节点")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "taskId", value = "任务ID", dataType = "String", paramType = "query", example = ""),
            @ApiImplicitParam(name = "days", value = "请假天数", dataType = "int", paramType = "query", example = ""),
    })
    public RestMessgae completeTask(@RequestParam("taskId") String taskId,
                                    @RequestParam("days") int days) {

        RestMessgae restMessgae ;

        try {
            HashMap<String, Object> variables = new HashMap<>(1);
            variables.put("days", days);
            taskService.complete(taskId, variables);
        } catch (Exception e) {
            restMessgae = RestMessgae.fail("提交失败", e.getMessage());
            e.printStackTrace();
            return restMessgae;
        }
        restMessgae = RestMessgae.fail("提交成功", taskId);
        return restMessgae;
    }

    @PostMapping(path = "createTask")
    @ApiOperation(value = "创建任务", notes = "根据流程创建一个任务")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "processId", value = "流程ID", dataType = "String", paramType = "query", example = ""),
    })
    public RestMessgae createTask(@RequestParam("processId") String processId) {

        RestMessgae restMessgae;
        String taskId = "task_"+processId;

        try {
            Task task = taskService.newTask(taskId);
            //TODO: task信息以及关联信息的完善
            taskService.saveTask(task);
        } catch (Exception e) {
            restMessgae = RestMessgae.fail("创建失败", e.getMessage());
            e.printStackTrace();
            return restMessgae;
        }

        restMessgae = RestMessgae.success("创建成功", null);
        return restMessgae;
    }

    @GetMapping("task/create")
    @ApiOperation(value = "创建任务", notes = "根据流程创建一个任务")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "processId", value = "流程ID", dataType = "String", paramType = "query", example = ""),
    })
    @Transactional
    public ResponseEntity<?> taskCreate(@RequestParam String model_id, @RequestParam String business_id, @RequestParam String user_id) {//"032bf875-99b0-4c85-91c0-e128fc759565"
        try{
            LambdaQueryWrapper<SysFlow> lambdaQuery = new QueryWrapper<SysFlow>().lambda();
            lambdaQuery.eq(SysFlow::getSysModel, model_id);
            SysFlow flow = sysFlowMapper.selectOne(lambdaQuery);
            String def_id = getProcessDefIdByProcessId(flow.getFlowId());//e8ac29e2-363b-11ec-b8d8-3c970ef14df2
            ProcessInstance processInstance = runtimeService.startProcessInstanceById(def_id);//对某一个流程启用一个流程实例

            SysTask sysTask = new SysTask();
            String taskId = UUID.randomUUID().toString();
            sysTask.setId(taskId);
            sysTask.setSKey(model_id);
            sysTask.setSId(business_id);
            sysTask.setTaskId(processInstance.getId());
            sysTaskMapper.insert(sysTask);

            SysTaskExt node = new SysTaskExt();
            String nodeId = UUID.randomUUID().toString();
            node.setId(nodeId);
            node.setHId(taskId);
            String nodeKey = getNodeKey(def_id);
            node.setNode(nodeKey);
            node.setUser(user_id);
            node.setRecord("");
            node.setOpinion("");
            node.setTime(System.currentTimeMillis()+"");
            node.setOperTime("");
            sysTaskExtMapper.insert(node);

            return ResponseResult.success("请求成功", processInstance.getId()).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
            ex.printStackTrace();
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    @GetMapping("task/complete")
    @ApiOperation(value = "完成当前节点的任务", notes = "完成当前节点的任务")
    public ResponseEntity<?> taskComplete(@RequestParam String model_id, @RequestParam String business_id, @RequestParam String user_id) {//"032bf875-99b0-4c85-91c0-e128fc759565"
        try{
            //TODO:activiti操作
                //1获取节点信息
                //2比对用户
                   //2.1通过: 执行完成
                   //2.2不通过: 报错
                //3查询下一节点的下一节点(outgoing)是否为网关
                    //3.1是: 比对我们自己记录的条件
                        //3.1.1通过: 执行完成
                        //3.1.2不通过: 报错
                    //3.2否: 执行完成

            //TODO:自定义表操作
                //Task记录表表插入一条新的记录


            return ResponseResult.success("请求成功", null).response();
		} catch (BizException be) {
            return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
            ex.printStackTrace();
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    private String getNodeKey(String processDefinitionId) {
        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
        Collection<FlowElement> flowElements = bpmnModel.getMainProcess().getFlowElements();
        List<FlowElement> result = flowElements.stream().filter(flowElement -> flowElement instanceof StartEvent).collect(Collectors.toList());
        String key = result.get(0).getId();
        return key;
    }

    /**
     * 根据流程id获取流程定义id
     *
     * @param process_id
     * @return java.lang.String
     * @author apr
     * @date 2021/10/27 14:43
     */
    private String getProcessDefIdByProcessId(String process_id){
           List<ProcessDefinition> list = processEngine.getRepositoryService()//与流程定义和部署对象相关的Service
                    .createProcessDefinitionQuery()//创建一个流程定义查询
                    /*指定查询条件,where条件*/
                    .deploymentId(process_id)//使用部署对象ID查询
                    //.processDefinitionId(processDefinitionId)//使用流程定义ID查询
                    //.processDefinitionKey(processDefinitionKey)//使用流程定义的KEY查询
                    //.processDefinitionNameLike(processDefinitionNameLike)//使用流程定义的名称模糊查询
                    /*排序*/
                    //.orderByProcessDefinitionVersion().asc()//按照版本的升序排列
                    //.orderByProcessDefinitionName().desc()//按照流程定义的名称降序排列
                    .list();//返回一个集合列表，封装流程定义
                    //.singleResult();//返回唯一结果集
                    //.count();//返回结果集数量
                    //.listPage(firstResult, maxResults)//分页查询

            if(list==null || list.isEmpty() || list.get(0)==null)
                return null;
            return list.get(0).getId();
    }
}
