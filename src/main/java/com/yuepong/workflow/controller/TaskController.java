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
import io.swagger.annotations.*;
import org.activiti.bpmn.model.*;
import org.activiti.bpmn.model.Process;
import org.activiti.engine.*;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.history.HistoricVariableInstance;
import org.activiti.engine.impl.cmd.NeedsActiveTaskCmd;
import org.activiti.engine.impl.interceptor.Command;
import org.activiti.engine.impl.interceptor.CommandContext;
import org.activiti.engine.impl.persistence.entity.ExecutionEntity;
import org.activiti.engine.impl.persistence.entity.TaskEntity;
import org.activiti.engine.impl.persistence.entity.TaskEntityManagerImpl;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.runtime.ProcessInstanceQuery;
import org.activiti.engine.task.Task;
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

    @ApiOperation(value = "创建任务", notes = "根据流程创建一个任务")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "type", value = "流程类型代码", dataType = "String", paramType = "query", example = ""),
            @ApiImplicitParam(name = "route", value = "路由", dataType = "String", paramType = "query", example = ""),
            @ApiImplicitParam(name = "userId", value = "用户ID", dataType = "String", paramType = "query", example = ""),
            @ApiImplicitParam(name = "dataId", value = "数据ID", dataType = "String", paramType = "query", example = ""),
    })
    @PostMapping("/task/create")
    @Transactional
    public ResponseEntity<?> taskCreate(@RequestBody TaskParam tp) {
        try{
            List<SysTask> taskHeads = sysTaskMapper.selectEnabledBySId(tp.getDataId());
            if(Objects.nonNull(taskHeads) && !taskHeads.isEmpty())
                return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR, "当前业务下已存在运行的流程实例, 无法重复发起", tp).response();

            //启动流程
            LambdaQueryWrapper<SysFlow> lambdaQuery = new QueryWrapper<SysFlow>().lambda();
            lambdaQuery.eq(SysFlow::getSysModel, tp.getType());
            lambdaQuery.eq(SysFlow::getSysDisable, false);
            SysFlow flow = sysFlowMapper.selectOne(lambdaQuery);
            if(Objects.isNull(flow))
                return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR, "当前业务未绑定或未激活流程", tp).response();

            String def_id = getProcessDefIdByProcessId(flow.getDeploymentId());
            if(Objects.isNull(def_id))
                return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR, "未获取到流程定义信息", tp).response();


            Map<String, Object> variables = Optional.ofNullable(tp.getConditions()).orElse(new HashMap<>());
            variables.put("creator", tp.getUserId());
            ProcessInstance processInstance = runtimeService.startProcessInstanceById(def_id, tp.getType(), variables);//对某一个流程启用一个流程实例
            runtimeService.setProcessInstanceName(processInstance.getId(), tp.getDataId());

            LambdaQueryWrapper<SysFlowExt> lambdaQueryFlowExt = new QueryWrapper<SysFlowExt>().lambda();
            lambdaQueryFlowExt.eq(SysFlowExt::getHId, flow.getId());
            lambdaQueryFlowExt.isNotNull(SysFlowExt::getField);
            lambdaQueryFlowExt.isNotNull(SysFlowExt::getConditions);
            lambdaQueryFlowExt.isNotNull(SysFlowExt::getValue);
            List<SysFlowExt> flowExtList = sysFlowExtMapper.selectList(lambdaQueryFlowExt);
            initGateways(processInstance, flowExtList);

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
            List<SysTask> taskHeads = sysTaskMapper.selectActedBySId(param.getDataId());
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
                HashMap<String, Object> variables = new HashMap<>(1);
                variables.put("msg", "完成任务");
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
                currentNode.setUser(param.getUserId());
                currentNode.setRecord(param.getOpinion());
                currentNode.setOpinion(Operations.APPROVE.getCode());
                Long now = System.currentTimeMillis();
                Long timeBetween = now - Long.parseLong(lastNode.getTime());
                currentNode.setTime(now.toString());
                currentNode.setOperTime(timeBetween.toString());

                sysTaskExtMapper.insert(currentNode);
                task.setStatus(ProcessStatus.ACTIVE.getCode());

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

                    endNode.setUser("");//system
                    endNode.setRecord("");//param.getOpinion()
                    endNode.setOpinion("");//Operations.APPROVE.getCode()
                    endNode.setTime(System.currentTimeMillis()+"");
                    endNode.setOperTime("0");

                    sysTaskExtMapper.insert(endNode);
                }

            } else if(Operations.RECALL.getCode().equals(param.getCommand())){//3.2 上一步
                //获取当前最新节点ID
                LambdaQueryWrapper<SysTaskExt> lambdaQuery2 = new QueryWrapper<SysTaskExt>().lambda();
                lambdaQuery2.eq(SysTaskExt::getHId, task.getId());
                lambdaQuery2.orderByDesc(SysTaskExt::getTime).last("limit 1");
                List<SysTaskExt> tasksList = sysTaskExtMapper.selectList(lambdaQuery2);
                SysTaskExt currentNode = tasksList.get(0);//倒数第一个节点
                Task currentTask = taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult();

                Collection<FlowElement> allNodes = getNodes(current.getProcessDefinitionId());

                List<FlowElement> currentElements = allNodes.stream().filter(node -> node.getId().equals(currentTask.getTaskDefinitionKey())).collect(Collectors.toList());
                FlowNode currentElement = (FlowNode)currentElements.get(0);
                //FIXME:这里如果是汇聚点的话会造成只能回退到第0个元素来源?
                FlowElement prevNode = currentElement.getIncomingFlows().get(0).getSourceFlowElement();
                if(prevNode instanceof StartEvent)
                    return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR, "当前流程节点为首个用户节点, 无法驳回", param.getCommand()).response();

                //删除当前运行任务
                String executionEntityId =processEngine.getManagementService().executeCommand(new DeleteTaskCommand(currentTask.getId()));
                //流程执行到来源节点
                processEngine.getManagementService().executeCommand(new JumpCommand((FlowNode)prevNode, executionEntityId));

                //自定义表Task记录表表插入一条新的记录
                SysTaskExt newNode = new SysTaskExt();
                String newNodeId = UUID.randomUUID().toString();
                newNode.setId(newNodeId);
                newNode.setHId(currentNode.getHId());
                newNode.setNode(currentTask.getTaskDefinitionKey());
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

                // 删除当前运行任务
                String executionEntityId =processEngine.getManagementService().executeCommand(new DeleteTaskCommand(currentTask.getId()));
                // 流程执行到来源节点
                // TODO:org.activiti.engine.ActivitiException: 操作错误，目标节点没有来源连线
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
            String values = taskNode.getOperation();
            if(Objects.nonNull(values)){
                List<String> passCode = Arrays.asList(values.split(","));
                if("user".equals(type) ){
                    match = passCode.contains(param.getUserId());
                }else if("role".equals(type)){
                    match = Collections.disjoint(Arrays.asList(param.getRole()), passCode);
                }
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

            //筛选禁用启用
            LambdaQueryWrapper<SysFlow> c = new LambdaQueryWrapper();
            c.eq(SysFlow::getSysDisable, 0);
            List<SysFlow> enabledFlows = sysFlowMapper.selectList(c);
            List<String> enabledFlowIds = new ArrayList<>();
            if(Objects.nonNull(enabledFlows)){
                //enabledFlowIds = enabledFlows.stream().map(SysFlow::getId).collect(Collectors.toList());
                for(SysFlow enabledFlow : enabledFlows){
                    enabledFlowIds.add(enabledFlow.getId());
                }
            }

            LambdaQueryWrapper<SysFlowExt> condition = new LambdaQueryWrapper();
            condition.eq(SysFlowExt::getOperation, user_id);
            List<SysFlowExt> customUserTasks = sysFlowExtMapper.selectList(condition);
            if(Objects.nonNull(customUserTasks) && !customUserTasks.isEmpty()){
                customUserTasks.stream().forEach(customUserTask -> {
                    if(enabledFlowIds.contains(customUserTask.getHId())){
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

            return ResponseResult.success("请求成功", tasksList).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), id).response();
		} catch (Exception ex) {
            ex.printStackTrace();
            return ResponseResult.obtain(CodeMsgs.SYSTEM_BASE_ERROR,ex.getMessage(), id).response();
		}
    }

    @ApiOperation(value = "查询是否开始流程")
    @GetMapping("/task/startStatus/{data_id}")
    public ResponseEntity<?> getStartStatusByDataId(@PathVariable String data_id) {//"032bf875-99b0-4c85-91c0-e128fc759565"
        try{
            boolean isStart = false;
            List<SysTask> tasksHeader = sysTaskMapper.selectEnabledBySId(data_id);
            if(Objects.nonNull(tasksHeader) && !tasksHeader.isEmpty()){
                LambdaQueryWrapper<SysTaskExt> lambdaQuery2 = new QueryWrapper<SysTaskExt>().lambda();
                lambdaQuery2.eq(SysTaskExt::getHId, tasksHeader.get(0).getId());
                List<SysTaskExt> tasksList = sysTaskExtMapper.selectList(lambdaQuery2);

                isStart =  (Objects.nonNull(tasksList) && tasksList.size() > 1);
            }

            return ResponseResult.success("请求成功", isStart).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), data_id).response();
		} catch (Exception ex) {
            ex.printStackTrace();
            return ResponseResult.obtain(CodeMsgs.SYSTEM_BASE_ERROR,ex.getMessage(), data_id).response();
		}
    }

    @ApiOperation(value = "sys_task条件查询")
    @PostMapping("/task/sys_task/search")
    public ResponseEntity<?> getSysTask(@RequestBody SysTaskQueryParam sysTask) {
        try{
            List<SysTask> result = new ArrayList<>();

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
            if(Objects.nonNull(sysTask.getStatus()))
                 lambdaQuery2.in(SysTask::getStatus, sysTask.getStatus());

            List<SysTask> taskHeads = sysTaskMapper.selectList(lambdaQuery2);
            if(Objects.isNull(taskHeads) || taskHeads.isEmpty())
                return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,"未获取到数据", sysTask).response();

            //返回最新的一条
            List<SysTask> running = taskHeads.stream().filter(head ->  Arrays.asList("1", "2").contains(head.getStatus())).collect(Collectors.toList());
            result = running;
            //返回历史最新
            if(Objects.isNull(result) || result.isEmpty()){
                HistoricProcessInstance newer = null;
                for(SysTask task : taskHeads){
                    HistoricProcessInstance current = historyService.createHistoricProcessInstanceQuery().processInstanceId(task.getTaskId()).singleResult();
                    if(Objects.nonNull(current)){
                         if(Objects.isNull(newer) || current.getEndTime().after( newer.getEndTime() )){
                            newer = current;
                        }
                    }
                }
                if(Objects.nonNull(newer)){
                    for(SysTask task : taskHeads){
                         if(task.getTaskId().equals(newer.getId())){
                            result.add(task);
                        }
                    }
                }
            }

            return ResponseResult.success("请求成功", result).response();
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
     * 发起流程是的变量：如网关条件, 发起人
     *
     * @param variables
     * @param flowExtList
     * @return void
     * @author apr
     * @date 2021/11/11 10:16
     */
    private void initVariables(HashMap<String, Object> variables, List<SysFlowExt> flowExtList) {

        flowExtList.stream().forEach(flowExt -> {
            String[] condition = new String[]{ flowExt.getField(), flowExt.getConditions(), flowExt.getValue() };
            variables.put(flowExt.getNode(), Arrays.toString(condition));
        });

    }

    /**
     * initGateways
     *
     * @param processInstance
     * @param gateWayList
     * @return void
     * @author apr
     * @date 2021/11/11 14:29
     */
    private void initGateways(ProcessInstance processInstance, List<SysFlowExt> gateWayList) {
        try {
            BpmnModel bpmnModel = repositoryService.getBpmnModel(processInstance.getProcessDefinitionId());
            Process p = bpmnModel.getProcessById(processInstance.getProcessDefinitionKey());

            gateWayList.stream().forEach(gateWay -> {
                //System.out.println(gateWay.getNode()+"->"+gateWay.getNextNode() + "==>" +gateWay.getField()+gateWay.getConditions()+gateWay.getValue());
                ExclusiveGateway metaGateway = (ExclusiveGateway) p.getFlowElement(gateWay.getNode());
                //do sth.
                List<SequenceFlow> lines = metaGateway.getOutgoingFlows();
                lines.stream().filter(line -> line.getTargetRef().equals(gateWay.getNextNode()))
                        .forEach(matchLine -> {
                            //System.out.println("line: "+ matchLine.getSourceRef()+"->"+matchLine.getTargetRef()+"~exp: "+ matchLine.getConditionExpression());
                            matchLine.setConditionExpression("${ " + gateWay.getField() + gateWay.getConditions() + gateWay.getValue() + " }");
                            p.removeFlowElement(matchLine.getId());
                            p.addFlowElement(matchLine);
                        });
            });
        } catch (Exception e) {
            System.out.println("gateway var failed");
        }
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
				throw new BizException("操作错误，目标节点没有来源连线");
			}
			//随便选一条连线来执行，时当前执行计划为，从连线流转到目标节点，实现跳转
			ExecutionEntity executionEntity = commandContext.getExecutionEntityManager().findById(executionId);
			executionEntity.setCurrentFlowElement(flows.get(0));
			commandContext.getAgenda().planTakeOutgoingSequenceFlowsOperation(executionEntity, true);
			return null;
		}
	}

}
