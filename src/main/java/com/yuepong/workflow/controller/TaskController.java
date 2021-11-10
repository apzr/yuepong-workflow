package com.yuepong.workflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yuepong.jdev.api.bean.ResponseResult;
import com.yuepong.jdev.code.CodeMsgs;
import com.yuepong.jdev.exception.BizException;
import com.yuepong.workflow.dto.*;
import com.yuepong.workflow.mapper.SysFlowExtMapper;
import com.yuepong.workflow.mapper.SysFlowMapper;
import com.yuepong.workflow.mapper.SysTaskExtMapper;
import com.yuepong.workflow.mapper.SysTaskMapper;
import com.yuepong.workflow.utils.Operations;
import com.yuepong.workflow.utils.ProcessStatus;
import com.yuepong.workflow.utils.RestMessgae;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.activiti.api.runtime.model.impl.ProcessDefinitionImpl;
import org.activiti.bpmn.model.*;
import org.activiti.bpmn.model.Process;
import org.activiti.engine.*;
import org.activiti.engine.history.HistoricActivityInstance;
import org.activiti.engine.history.HistoricTaskInstance;
import org.activiti.engine.history.HistoricVariableInstance;
import org.activiti.engine.impl.RepositoryServiceImpl;
import org.activiti.engine.impl.cmd.NeedsActiveTaskCmd;
import org.activiti.engine.impl.interceptor.Command;
import org.activiti.engine.impl.interceptor.CommandContext;
import org.activiti.engine.impl.persistence.entity.ExecutionEntity;
import org.activiti.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.activiti.engine.impl.persistence.entity.TaskEntity;
import org.activiti.engine.impl.persistence.entity.TaskEntityManagerImpl;
import org.activiti.engine.impl.util.ProcessDefinitionUtil;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.Execution;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.runtime.ProcessInstanceQuery;
import org.activiti.engine.task.DelegationState;
import org.activiti.engine.task.Event;
import org.activiti.engine.task.Task;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

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

    @Autowired
    TaskService taskService;

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

    @Autowired
    SysFlowExtMapper sysFlowExtMapper;

    @Autowired
    HistoryService historyService;

//    @PostMapping(path = "findTaskByAssignee")
//    @ApiOperation(value = "根据流程assignee查询当前人的个人任务", notes = "根据流程assignee查询当前人的个人任务")
//    @ApiImplicitParams({
//            @ApiImplicitParam(name = "assignee", value = "代理人（当前用户）", dataType = "String", paramType = "query", example = ""),
//    })
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

//    @PostMapping(path = "completeTask")
//    @ApiOperation(value = "完成任务", notes = "完成任务，任务进入下一个节点")
//    @ApiImplicitParams({
//            @ApiImplicitParam(name = "taskId", value = "任务ID", dataType = "String", paramType = "query", example = ""),
//            @ApiImplicitParam(name = "days", value = "请假天数", dataType = "int", paramType = "query", example = ""),
//    })
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

//    @PostMapping(path = "createTask")
//    @ApiOperation(value = "创建任务", notes = "根据流程创建一个任务")
//    @ApiImplicitParams({
//            @ApiImplicitParam(name = "processId", value = "流程ID", dataType = "String", paramType = "query", example = ""),
//    })
    public RestMessgae createTask(@RequestParam("processId") String processId) {

        RestMessgae restMessgae;
        String taskId = "task_"+processId;

        try {
            Task task = taskService.newTask(taskId);
            taskService.saveTask(task);
        } catch (Exception e) {
            restMessgae = RestMessgae.fail("创建失败", e.getMessage());
            e.printStackTrace();
            return restMessgae;
        }

        restMessgae = RestMessgae.success("创建成功", null);
        return restMessgae;
    }

    @ApiOperation(value = "创建任务", notes = "根据流程创建一个任务")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "type", value = "流程类型代码", dataType = "String", paramType = "query", example = ""),
            @ApiImplicitParam(name = "route", value = "路由", dataType = "String", paramType = "query", example = ""),
            @ApiImplicitParam(name = "userId", value = "用户ID", dataType = "String", paramType = "query", example = ""),
            @ApiImplicitParam(name = "dataId", value = "数据ID", dataType = "String", paramType = "query", example = ""),
    })
    @PostMapping("/task/create")
    @Transactional
    public ResponseEntity<?> taskCreate(@RequestBody TaskParam tp) {//"032bf875-99b0-4c85-91c0-e128fc759565"
        try{
            //启动流程
            LambdaQueryWrapper<SysFlow> lambdaQuery = new QueryWrapper<SysFlow>().lambda();
            lambdaQuery.eq(SysFlow::getSysModel, tp.getType());
            lambdaQuery.eq(SysFlow::getSysDisable, false);
            SysFlow flow = sysFlowMapper.selectOne(lambdaQuery);
            if(Objects.isNull(flow))
                return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR, "该业务未绑定或未激活流程", tp).response();

            HashMap<String, Object> variables=new HashMap<>(1);
            variables.put("userKey", tp.getUserId());//发起人 存于act_hi_varinst 
            String def_id = getProcessDefIdByProcessId(flow.getFlowId());//e8ac29e2-363b-11ec-b8d8-3c970ef14df2
            if(Objects.isNull(def_id))
                return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR, "未获取到流程", tp).response();
            //String processDefinitionKey="proc_def_key"+def_id;
            ProcessInstance processInstance = runtimeService.startProcessInstanceById(def_id, tp.getType(), variables);//对某一个流程启用一个流程实例

            //自定义表1
            SysTask sysTask = new SysTask();
            String taskId = UUID.randomUUID().toString();
            sysTask.setId(taskId);
            sysTask.setSKey(tp.getType());
            sysTask.setSId(tp.getDataId());
            sysTask.setTaskId(processInstance.getId());
            sysTask.setRoute(tp.getRoute());
            sysTask.setStatus(ProcessStatus.ACTIVE.getCode());
            sysTaskMapper.insert(sysTask);

            //自定义表2: 开始
            SysTaskExt startNode = new SysTaskExt();
            String startNodeId = UUID.randomUUID().toString();
            startNode.setId(startNodeId);
            startNode.setHId(taskId);
            String startNodeKey = getStartKey(def_id);
            startNode.setNode(startNodeKey);
            startNode.setUser(tp.getUserId());
            startNode.setRecord("");
            startNode.setOpinion("");
            startNode.setTime(System.currentTimeMillis()+"");
            startNode.setOperTime("");
            sysTaskExtMapper.insert(startNode);

            return ResponseResult.success("请求成功", processInstance.getId()).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), tp).response();
		} catch (Exception ex) {
            ex.printStackTrace();
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,ex.getMessage(), tp).response();
		}
    }

    @PostMapping("task/complete")
    @ApiOperation(value = "完成当前节点的任务", notes = "完成当前节点的任务")
    public ResponseEntity<?> taskComplete(@RequestBody TaskCompleteParam param) {
        boolean isLast = false;

        try{
            //0. 获取任务头信息
            List<SysTask> taskHeads = sysTaskMapper.selectBySId(param.getDataId());
            if(Objects.isNull(taskHeads) || taskHeads.isEmpty())
                return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR, "未获取到实例ID", param).response();

            //1. 获取任务当前节点信息
            SysTask taskHead = taskHeads.get(0);
            String processInstanceId = taskHead.getTaskId();
            Task current = taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult();

            if(Objects.isNull(current))
                return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR, "未获取到当前任务", param).response();

            //2. 验证用户
            if(!permissionCheck(param, current))
                 return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR, "用户无权处理", null).response();

            LambdaQueryWrapper<SysTask> lambdaQuery = new QueryWrapper<SysTask>().lambda();
            lambdaQuery.eq(SysTask::getTaskId, processInstanceId);
            SysTask task = sysTaskMapper.selectOne(lambdaQuery);

            //验证zhuangtai
            if(!ProcessStatus.ACTIVE.getCode().equals(task.getStatus()))
                 return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR, "当前流程不是激活状态", null).response();

            //3. 处理任务
            if(Operations.APPROVE.getCode().equals(param.getCommand())){//3.1 下一步
                Task lastPoint = taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult();
                //3.1 完成系统任务节点
                taskService.addComment(current.getId(), processInstanceId, Operations.APPROVE.getMsg());
                //HashMap<String, Object> variables = new HashMap<>(1);
                //variables.put("custom", "customVariable");
                taskService.complete(current.getId());

                //3.2 自定义表Task记录表表插入一条新的记录
                task.setStatus(ProcessStatus.ACTIVE.getCode());

                LambdaQueryWrapper<SysTaskExt> lambdaQuery2 = new QueryWrapper<SysTaskExt>().lambda();
                lambdaQuery2.eq(SysTaskExt::getHId, task.getId());
                lambdaQuery2.orderByDesc(SysTaskExt::getTime).last("limit 1");
                SysTaskExt lastNode = sysTaskExtMapper.selectOne(lambdaQuery2);

                SysTaskExt currentNode = new SysTaskExt();
                String startNodeId = UUID.randomUUID().toString();
                currentNode.setId(startNodeId);
                currentNode.setHId(lastNode.getHId());
                currentNode.setNode(lastPoint.getTaskDefinitionKey());
                currentNode.setUser(Optional.ofNullable(lastPoint.getAssignee()).orElse(""));
                currentNode.setRecord(param.getOpinion());
                currentNode.setOpinion(Operations.APPROVE.getCode());
                Long now = System.currentTimeMillis();
                Long timeBetween = now - Long.parseLong(lastNode.getTime());
                currentNode.setTime(now.toString());
                currentNode.setOperTime(timeBetween.toString());

                sysTaskExtMapper.insert(currentNode);

                Task newPoint = taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult();
                if(Objects.isNull(newPoint)){
                    isLast = true;

                    //任务头设置status
                    task.setStatus(ProcessStatus.COMPLETE.getCode());

                    //插入end节点;
                    Collection<FlowElement> allNodes = getNodes(lastPoint.getProcessDefinitionId());
                    List<FlowElement> currentElements = allNodes.stream().filter(node -> node instanceof EndEvent).collect(Collectors.toList());
                    FlowNode currentElement = (FlowNode)currentElements.get(0);

                    SysTaskExt endNode = new SysTaskExt();
                    String endNodeId = UUID.randomUUID().toString();
                    endNode.setId(endNodeId);
                    endNode.setHId(lastNode.getHId());

                    if(Objects.nonNull(lastPoint)){
                        endNode.setNode(currentElement.getId());
                    } else {
                        endNode.setNode("end");
                    }

                    endNode.setUser("system");
                    endNode.setRecord(param.getOpinion());
                    endNode.setOpinion(Operations.APPROVE.getCode());
                    endNode.setTime(System.currentTimeMillis()+"");
                    endNode.setOperTime("0");

                    sysTaskExtMapper.insert(endNode);
                }
                task.setStatus(ProcessStatus.ACTIVE.getCode());
            } else if(Operations.RECALL.getCode().equals(param.getCommand())){//3.2 上一步
                //获取当前最新节点ID
                LambdaQueryWrapper<SysTaskExt> lambdaQuery2 = new QueryWrapper<SysTaskExt>().lambda();
                lambdaQuery2.eq(SysTaskExt::getHId, task.getId());
                lambdaQuery2.orderByDesc(SysTaskExt::getTime).last("limit 1");
                List<SysTaskExt> tasksList = sysTaskExtMapper.selectList(lambdaQuery2);
                SysTaskExt currentNode = tasksList.get(0);//倒数第一个节点
                //String currentTaskDefKey = currentNode.getNode();
                Task currentTask = taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult();

                Collection<FlowElement> allNodes = getNodes(current.getProcessDefinitionId());

                List<FlowElement> currentElements = allNodes.stream().filter(node -> node.getId().equals(currentTask.getTaskDefinitionKey())).collect(Collectors.toList());
                FlowNode currentElement = (FlowNode)currentElements.get(0);
                FlowElement prevNode = currentElement.getIncomingFlows().get(0).getSourceFlowElement();//上一个

                //获取目标节点定义
                //FlowNode targetNode = (FlowNode)process.getFlowElement(element.getId());
                //删除当前运行任务
                String executionEntityId =processEngine.getManagementService().executeCommand(new DeleteTaskCommand(currentTask.getId()));
                //流程执行到来源节点
                processEngine.getManagementService().executeCommand(new JumpCommand((FlowNode)prevNode, executionEntityId));

                //自定义表Task记录表表插入一条新的记录
                SysTaskExt newNode = new SysTaskExt();
                String newNodeId = UUID.randomUUID().toString();
                newNode.setId(newNodeId);
                newNode.setHId(currentNode.getHId());
                newNode.setNode(prevNode.getId());
                newNode.setUser(param.getUserId());
                newNode.setRecord(param.getOpinion());
                newNode.setOpinion(Operations.RECALL.getCode());
                Long now = System.currentTimeMillis();
                Long timeBetween = now - Long.parseLong(currentNode.getTime());
                newNode.setTime(now.toString());
                newNode.setOperTime(timeBetween.toString());
                sysTaskExtMapper.insert(newNode);

                task.setStatus(ProcessStatus.ACTIVE.getCode());
            } else if(Operations.REJECT.getCode().equals(param.getCommand())){//3.3 起点
                //获取当前最新节点ID
                LambdaQueryWrapper<SysTaskExt> lambdaQuery2 = new QueryWrapper<SysTaskExt>().lambda();
                lambdaQuery2.eq(SysTaskExt::getHId, task.getId());
                lambdaQuery2.orderByDesc(SysTaskExt::getTime).last("limit 1");
                List<SysTaskExt> tasksList = sysTaskExtMapper.selectList(lambdaQuery2);
                SysTaskExt currentNode = tasksList.get(0);//倒数第一个节点
                String currentTaskDefKey = currentNode.getNode();
                Task currentTask = taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult();

                Collection<FlowElement> allNodes = getNodes(current.getProcessDefinitionId());

                List<FlowElement> startElement = allNodes.stream().filter(node -> node instanceof StartEvent).collect(Collectors.toList());
                FlowNode startTaskNode = (FlowNode)startElement.get(0);
                FlowElement firstNode = startTaskNode.getOutgoingFlows().get(0).getTargetFlowElement();//第一个

                //获取目标节点定义
                //FlowNode targetNode = (FlowNode)process.getFlowElement(element.getId());
                //删除当前运行任务
                String executionEntityId =processEngine.getManagementService().executeCommand(new DeleteTaskCommand(currentTask.getId()));
                //流程执行到来源节点//TODO:org.activiti.engine.ActivitiException: 操作错误，目标节点没有来源连线
                processEngine.getManagementService().executeCommand(new JumpCommand((FlowNode)firstNode, executionEntityId));

                //自定义表Task记录表表插入一条新的记录
                SysTaskExt newNode = new SysTaskExt();
                String newNodeId = UUID.randomUUID().toString();
                newNode.setId(newNodeId);
                newNode.setHId(currentNode.getHId());
                newNode.setNode(startTaskNode.getId());
                newNode.setUser(param.getUserId());
                newNode.setRecord(param.getOpinion());
                newNode.setOpinion(Operations.REJECT.getCode());
                Long now = System.currentTimeMillis();
                Long timeBetween = now - Long.parseLong(currentNode.getTime());
                newNode.setTime(now.toString());
                newNode.setOperTime(timeBetween.toString());
                sysTaskExtMapper.insert(newNode);

                task.setStatus(ProcessStatus.ACTIVE.getCode());
            } else if(Operations.CANCEL.getCode().equals(param.getCommand())){//3.4 终点
                //获取当前最新节点ID
                LambdaQueryWrapper<SysTaskExt> lambdaQuery2 = new QueryWrapper<SysTaskExt>().lambda();
                lambdaQuery2.eq(SysTaskExt::getHId, task.getId());
                lambdaQuery2.orderByDesc(SysTaskExt::getTime).last("limit 1");
                List<SysTaskExt> tasksList = sysTaskExtMapper.selectList(lambdaQuery2);
                SysTaskExt currentNode = tasksList.get(0);//倒数第一个节点
                String currentTaskDefKey = currentNode.getNode();
                Task currentTask = taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult();

                Collection<FlowElement> allNodes = getNodes(current.getProcessDefinitionId());

                List<FlowElement> startElement = allNodes.stream().filter(node -> node instanceof EndEvent).collect(Collectors.toList());
                FlowElement endTaskNode = startElement.get(0);

                //获取目标节点定义
                //FlowNode targetNode = (FlowNode)process.getFlowElement(element.getId());
                //删除当前运行任务
                String executionEntityId =processEngine.getManagementService().executeCommand(new DeleteTaskCommand(currentTask.getId()));
                //流程执行到来源节点
                processEngine.getManagementService().executeCommand(new JumpCommand((FlowNode)endTaskNode, executionEntityId));

                //自定义表Task记录表表插入一条新的记录
                SysTaskExt newNode = new SysTaskExt();
                String newNodeId = UUID.randomUUID().toString();
                newNode.setId(newNodeId);
                newNode.setHId(currentNode.getHId());
                newNode.setNode(endTaskNode.getId());
                newNode.setUser(param.getUserId());
                newNode.setRecord(param.getOpinion());
                newNode.setOpinion(Operations.CANCEL.getCode());
                Long now = System.currentTimeMillis();
                Long timeBetween = now - Long.parseLong(currentNode.getTime());
                newNode.setTime(now.toString());
                newNode.setOperTime(timeBetween.toString());
                sysTaskExtMapper.insert(newNode);

                task.setStatus(ProcessStatus.SHUTDOWN.getCode());
            }  else {
                return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR, "非法操作命令代码", param.getCommand()).response();
            }

            sysTaskMapper.updateById(task);

            return ResponseResult.success("处理完成", isLast).response();
		} catch (BizException be) {
            return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
            ex.printStackTrace();
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    @GetMapping("task/active/{instance_id}")
    @ApiOperation(value = "激活或挂起流程实例", notes = "激活或挂起流程实例")
    public ResponseEntity<?> Suspended(@PathVariable String instance_id){
        try {
            List<SysTask> tasks = sysTaskMapper.selectByTaskId(instance_id);
            if(tasks==null || tasks.isEmpty()) {
                return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,"未获取到流程", null).response();
            }
            SysTask task = tasks.get(0);

            ProcessInstance processInstance = runtimeService.createProcessInstanceQuery().processInstanceId(instance_id).singleResult();
            boolean active = !processInstance.isSuspended();

            if(active){
                runtimeService.suspendProcessInstanceById(instance_id);
                task.setStatus(ProcessStatus.SUSPENDED.getCode());
            }else{
                runtimeService.activateProcessInstanceById(instance_id);
                task.setStatus(ProcessStatus.ACTIVE.getCode());
            }
            sysTaskMapper.updateById(task);

            return ResponseResult.success("请求成功", active?"挂起":"恢复").response();
        } catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    /**
     * 获取当前用户是否有处理该任务的权限
     *
     * @param param 
     * @return boolean
     * @author apr
     * @date 2021/11/9 9:16
     */
    private boolean permissionCheck(TaskCompleteParam param, Task currentTask) {
        boolean match = false;

        LambdaQueryWrapper<SysFlowExt> condition = new QueryWrapper<SysFlowExt>().lambda();
        condition.eq(SysFlowExt::getNode, currentTask.getTaskDefinitionKey());
        List<SysFlowExt> taskNodes = sysFlowExtMapper.selectList(condition);

        if(Objects.nonNull(taskNodes) && !taskNodes.isEmpty()) {
            SysFlowExt taskNode = taskNodes.get(0);
            String type = taskNode.getUserType();
            if("user".equals(type)){
                match = taskNode.getOperation().indexOf(param.getUserId())>-1;
            }else if("role".equals(type)){
                //TODO:包含
                match = Arrays.asList(param.getRole()).contains(taskNode.getOperation());
            }
        }

        return match;
    }

    @ApiOperation(value = "查询当前用户的任务", notes = "查询当前用户的任务")
    @ApiImplicitParams({ @ApiImplicitParam(
            name = "user_id",
            value = "用户id",
            dataType = "String",
            paramType = "query",
            example = ""
    ) })
    @GetMapping("/task/user/{user_id}")
    public ResponseEntity<?> getTaskByUser(@PathVariable String user_id) {//"032bf875-99b0-4c85-91c0-e128fc759565"
        try{
            List<TaskTodo> tasks = new ArrayList<>();

            LambdaQueryWrapper<SysFlowExt> condition = new LambdaQueryWrapper();
            condition.eq(SysFlowExt::getOperation, user_id);
            List<SysFlowExt> customUserTasks = sysFlowExtMapper.selectList(condition);
            if(Objects.nonNull(customUserTasks) && !customUserTasks.isEmpty()){
                customUserTasks.stream().forEach(customUserTask -> {
                    List<Task> actTasks = taskService.createTaskQuery().active().taskDefinitionKey(customUserTask.getNode()).list();
                    if(Objects.nonNull(actTasks) && !actTasks.isEmpty()){
                        actTasks.stream().forEach(actTask ->{
                            //.taskId(actTask.getId())
                            List<HistoricVariableInstance> varList = processEngine.getHistoryService()
                                        .createHistoricVariableInstanceQuery()
                                        .processInstanceId(actTask.getProcessInstanceId())
                                        //.taskId(actTask.getId())
                                        .variableName("userKey")
                                        .list();
                            String userKey = "无";
                            if(Objects.nonNull(varList) && !varList.isEmpty()){
                                userKey = String.valueOf(varList.get(0).getValue());
                            }

                            LambdaQueryWrapper<SysTask> taskCondition = new LambdaQueryWrapper<>();
                            taskCondition.eq(SysTask::getTaskId, actTask.getProcessInstanceId());
                            SysTask sysTask = sysTaskMapper.selectOne(taskCondition);

                            if(Objects.nonNull(actTask)){
                                TaskTodo tt = new TaskTodo(
                                    actTask.getId(),
                                    actTask.getProcessInstanceId(),
                                    actTask.getName()==null?customUserTask.getNode():actTask.getName(),
                                    customUserTask.getOperation(),
                                    userKey,
                                    actTask.getCreateTime().getTime()+"",
                                    System.currentTimeMillis()-actTask.getCreateTime().getTime()+"",
                                    sysTask
                                );

                                tasks.add(tt);
                            }
                        });
                    }
                });
            }

            return ResponseResult.success("请求成功", tasks).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
            ex.printStackTrace();
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    @ApiOperation(value = "根据主表id查询任务(节点)列表")
    @GetMapping("/task/sysTaskList/{id}")
    public ResponseEntity<?> getTaskByHeaderId(@PathVariable String id) {//"032bf875-99b0-4c85-91c0-e128fc759565"
        try{
            LambdaQueryWrapper<SysTaskExt> lambdaQuery2 = new QueryWrapper<SysTaskExt>().lambda();
            lambdaQuery2.eq(SysTaskExt::getHId, id);
            lambdaQuery2.orderByDesc(SysTaskExt::getTime);
            List<SysTaskExt> tasksList = sysTaskExtMapper.selectList(lambdaQuery2);
//            tasksList.stream().forEach(sysTaskExt ->{
//                String act_id = sysTaskExt.getNode();
//                HistoricActivityInstance activityInstance = historyService.createHistoricActivityInstanceQuery().activityId(act_id).singleResult();
//                if(Objects.nonNull(activityInstance)){
//                    sysTaskExt.setNode(activityInstance.getActivityName());
//                }
//            });

            return ResponseResult.success("请求成功", tasksList).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), id).response();
		} catch (Exception ex) {
            ex.printStackTrace();
            return ResponseResult.obtain(CodeMsgs.SYSTEM_BASE_ERROR,ex.getMessage(), id).response();
		}
    }

    @ApiOperation(value = "sys_task条件查询")
    @PostMapping("/task/sys_task/search")
    public ResponseEntity<?> getSysTask(@RequestBody SysTaskQueryParam sysTask) {
        try{

            LambdaQueryWrapper<SysTask> lambdaQuery2 = new QueryWrapper<SysTask>().lambda();
            if(Objects.nonNull(sysTask.getId()))
                 lambdaQuery2.eq(SysTask::getId, sysTask.getId());
            if(Objects.nonNull(sysTask.getSId()))
                 lambdaQuery2.eq(SysTask::getSId, sysTask.getSId());
            if(Objects.nonNull(sysTask.getSKey()))
                 lambdaQuery2.eq(SysTask::getSKey, sysTask.getSKey());
            if(Objects.nonNull(sysTask.getRoute()))
                 lambdaQuery2.eq(SysTask::getRoute, sysTask.getRoute());
            if(Objects.nonNull(sysTask.getTaskId()))
                 lambdaQuery2.eq(SysTask::getTaskId, sysTask.getTaskId());

            List<SysTask> taskHeads = sysTaskMapper.selectList(lambdaQuery2);

            if(Objects.isNull(taskHeads) || taskHeads.isEmpty())
                return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,"未获取到数据", sysTask).response();

            return ResponseResult.success("请求成功", taskHeads).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), sysTask).response();
		} catch (Exception ex) {
            ex.printStackTrace();
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    /**
     * 获取流程定义开始节点的Key
     *
     * @param processDefinitionId 
     * @return java.lang.String
     * @author apr
     * @date 2021/10/28 11:16
     */
    private String getStartKey(String processDefinitionId) {
        Collection<FlowElement> flowElements = getNodes(processDefinitionId);
        List<FlowElement> result = flowElements.stream().filter(flowElement -> flowElement instanceof StartEvent).collect(Collectors.toList());
        String key = result.get(0).getId();
        return key;
    }

    /**
     * 获取流程定义的所有节点
     *
     * @param processDefinitionId
     * @return java.lang.String
     * @author apr
     * @date 2021/10/28 11:16
     */
    private Collection<FlowElement> getNodes(String processDefinitionId) {
        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
        Collection<FlowElement> flowElements = bpmnModel.getMainProcess().getFlowElements();
        return flowElements;
    }

    /**
     * 获取当前节点的上一个节点
     *
     * @param flowNode 
     * @return org.activiti.bpmn.model.FlowElement
     * @author apr
     * @date 2021/10/28 11:21
     */
    private FlowElement getPrevNode(FlowNode flowNode) {
        List<SequenceFlow> inFlows = flowNode.getIncomingFlows();//获取当前节点输入连线
        for (SequenceFlow ingoingFlow : inFlows) {//遍历输出连线
            FlowElement sourceFlowElement = ingoingFlow.getSourceFlowElement();//获取输出节点元素
            if(sourceFlowElement instanceof UserTask){//排除非用户任务接点
                return sourceFlowElement;//获取节点
            }
        }
        return null;
    }
    
    /**
     * 获取当前节点的下一个节点
     *
     * @param flowNode 
     * @return org.activiti.bpmn.model.FlowElement
     * @author apr
     * @date 2021/10/28 11:21
     */
    private FlowElement getNextNode(FlowNode flowNode) {
        List<SequenceFlow> outFlows = flowNode.getOutgoingFlows();//获取当前节点输出连线
        for (SequenceFlow outgoingFlow : outFlows) {//遍历输出连线
            FlowElement targetFlowElement = outgoingFlow.getTargetFlowElement();//获取输出节点元素
            if(targetFlowElement instanceof UserTask){//排除非用户任务接点
                return targetFlowElement;//获取节点
            }
        }
        return null;
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
                    .createProcessDefinitionQuery()
                    .deploymentId(process_id)
                    .list();

            if(list==null || list.isEmpty() || list.get(0)==null)
                return null;
            return list.get(0).getId();
    }

    /**
	 * 删除当前运行时任务命令
	 * 这里继承了NeedsActiveTaskCmd，主要是很多跳转业务场景下，要求不能时挂起任务。可以直接继承Command即可
	 */
	public class DeleteTaskCommand extends NeedsActiveTaskCmd<String> {
		public DeleteTaskCommand(String taskId){
			super(taskId);
		}
		@Override
		public String execute(CommandContext commandContext, TaskEntity currentTask){
			//获取所需服务
			TaskEntityManagerImpl taskEntityManager = (TaskEntityManagerImpl)commandContext.getTaskEntityManager();
			//获取当前任务的来源任务及来源节点信息
			ExecutionEntity executionEntity = currentTask.getExecution();
			//删除当前任务,来源任务
			taskEntityManager.deleteTask(currentTask, "jumpReason", false, false);
			return executionEntity.getId();
		}
		@Override
		public String getSuspendedTaskException() {
			return "挂起的任务不能跳转";
		}
	}

    /**
	 * 根据提供节点和执行对象id，进行跳转命令
	 */
	public class JumpCommand implements Command<Void> {
		private FlowNode flowElement;
		private String executionId;
		public JumpCommand(FlowNode flowElement, String executionId){
			this.flowElement = flowElement;
			this.executionId = executionId;
		}
		@Override
		public Void execute(CommandContext commandContext){
			//获取目标节点的来源连线
			List<SequenceFlow> flows = flowElement.getIncomingFlows();
			if(flows==null || flows.size()<1){
				throw new ActivitiException("操作错误，目标节点没有来源连线");
			}
			//随便选一条连线来执行，时当前执行计划为，从连线流转到目标节点，实现跳转
			ExecutionEntity executionEntity = commandContext.getExecutionEntityManager().findById(executionId);
			executionEntity.setCurrentFlowElement(flows.get(0));
			commandContext.getAgenda().planTakeOutgoingSequenceFlowsOperation(executionEntity, true);
			return null;
		}
	}

}
