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
import com.yuepong.workflow.page.pager.TaskTodoPager;
import com.yuepong.workflow.param.*;
import com.yuepong.workflow.utils.Operations;
import com.yuepong.workflow.utils.ProcessStatus;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.activiti.bpmn.model.Process;
import org.activiti.bpmn.model.*;
import org.activiti.engine.*;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.history.HistoricTaskInstance;
import org.activiti.engine.history.HistoricVariableInstance;
import org.activiti.engine.impl.cmd.NeedsActiveTaskCmd;
import org.activiti.engine.impl.interceptor.Command;
import org.activiti.engine.impl.interceptor.CommandContext;
import org.activiti.engine.impl.persistence.entity.ExecutionEntity;
import org.activiti.engine.impl.persistence.entity.TaskEntity;
import org.activiti.engine.impl.persistence.entity.TaskEntityManagerImpl;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @author Apr
 * @Description <p> ?????????????????? </p>
 */
@Controller
@Api(tags = "????????????")
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

    @ApiOperation(value = "????????????????????????????????????", notes = "?????????????????????, ???????????????????????????????????????????????????????????????")
    @PostMapping("/user/permission")
    public ResponseEntity<?> getUserPermission(@RequestBody TaskCompleteParam tc) {
        try{
            boolean approve = false, back = false;
            //1 ??????????????????1,2
            List<SysTask> sysTasks = sysTaskMapper.selectBySId(tc.getDataId());
            List<SysTask> activeTasks = sysTasks.stream()
                    .filter(head -> Arrays.asList("1", "2").contains(head.getStatus()))
                    .collect(Collectors.toList());
            //2. ?????????1,2 ??????instance, ???????????????????????????
            if(Objects.nonNull(activeTasks) && !activeTasks.isEmpty()){
                SysTask st = activeTasks.get(0);
                String processInstanceId = st.getTaskId();
                Task current = taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult();

                tc.setCommand("1");
                approve = permissionCheck(tc, current);
                tc.setCommand("5");
                back = permissionCheck(tc, current);
                //???????????????????????????, ??????????????????false;
                Collection<FlowElement> allNodes = getNodes(current.getProcessDefinitionId());
                if(Objects.nonNull(allNodes)){
                    List<FlowElement> currentElements = allNodes.stream().filter(node -> node.getId().equals(current.getTaskDefinitionKey())).collect(Collectors.toList());
                    FlowNode currentElement = (FlowNode)currentElements.get(0);
                    //FIXME?????????get0???????????????????????????????????????????????????????????????get??????
                    SequenceFlow prevNode = currentElement.getIncomingFlows().get(0);
                    if(!isStartNode(prevNode)){
                        back = false;
                    }
                }
            }

            return ResponseResult.success("????????????", PermissionResult.newInstance(approve, back)).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
            ex.printStackTrace();
			return ResponseResult.error(ex.getMessage()).response();
		}

    }

    @ApiOperation(value = "?????????????????????????????????", notes = "????????????ID?????????????????????????????????")
    @GetMapping("/user/monitor/{user_id}")
    public ResponseEntity<?> getUserMonitor(@PathVariable String user_id) {
        try{
            int done, create;
            AtomicInteger todo= new AtomicInteger(0);

            //done
            LambdaQueryWrapper<SysTaskExt> condition = new LambdaQueryWrapper();
            condition.eq(SysTaskExt::getUser, user_id);
            condition.like(SysTaskExt::getNode, "Activity_%");
            done = sysTaskExtMapper.selectCount(condition);

            //create
            //LambdaQueryWrapper<SysTaskExt> condition1 = new LambdaQueryWrapper();
            //condition1.eq(SysTaskExt::getUser, user_id);
            //condition1.like(SysTaskExt::getNode, "startNode%");
            //create = sysTaskExtMapper.selectCount(condition1);
            create = sysTaskExtMapper.selectCreatedCount(user_id);

            // to-do
            LambdaQueryWrapper<SysFlow> c = new LambdaQueryWrapper();
            //c.eq(SysFlow::getSysDisable, 0);// ??????????????????
            List<SysFlow> enabledFlows = sysFlowMapper.selectList(c);
            List<String> enabledFlowIds = new ArrayList<>();
            if(Objects.nonNull(enabledFlows)){
                for(SysFlow enabledFlow : enabledFlows){
                    enabledFlowIds.add(enabledFlow.getId());
                }
            }

            LambdaQueryWrapper<SysFlowExt> condition2 = new LambdaQueryWrapper();
            condition2.eq(SysFlowExt::getOperation, user_id);
            List<SysFlowExt> customUserTasks = sysFlowExtMapper.selectList(condition2);

            if(Objects.nonNull(customUserTasks) && !customUserTasks.isEmpty()){
                customUserTasks.stream().forEach(customUserTask -> {
                    if(enabledFlowIds.contains(customUserTask.getHId())){
                        List<Task> actTasks = taskService.createTaskQuery().active().taskDefinitionKey(customUserTask.getNode()).orderByTaskCreateTime().desc().list();
                        if(Objects.nonNull(actTasks) && !actTasks.isEmpty()){
                            actTasks.stream().forEach(actTask ->{
                                if(Objects.nonNull(actTask)){
                                    todo.getAndIncrement();
                                }
                            });
                        }
                    }
                });
            }

            return ResponseResult.success("????????????", MonitorResult.newInstance(done, todo.get(), create)).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), user_id).response();
		} catch (Exception ex) {
            ex.printStackTrace();
			return ResponseResult.error(ex.getMessage()).response();
		}

    }

    @ApiOperation(value = "?????????????????????????????????", notes = "????????????ID?????????????????????????????????")
    @PostMapping("/user/monitor")
    public ResponseEntity<?> getUserMonitorNew(@RequestBody TaskTodoParam ttp) {
        try{
            int done, create;
            AtomicInteger todo= new AtomicInteger(0);

            //done
            Calendar now = Calendar.getInstance();
            now.add(Calendar.DAY_OF_MONTH, -30);
            LambdaQueryWrapper<SysTaskExt> condition = new LambdaQueryWrapper();
            condition.eq(SysTaskExt::getUser, ttp.getUserId());
            condition.gt(SysTaskExt::getTime, now.getTime().getTime());
            condition.like(SysTaskExt::getNode, "Activity_%");
            done = sysTaskExtMapper.selectCount(condition);

            //create
            //LambdaQueryWrapper<SysTaskExt> condition1 = new LambdaQueryWrapper();
            //.eq(SysTaskExt::getUser, ttp.getUserId());
            //condition1.like(SysTaskExt::getNode, "startNode%");
            //create = sysTaskExtMapper.selectCount(condition1);
            create = sysTaskExtMapper.selectCreatedCount(ttp.getUserId());

            // to-do
            LambdaQueryWrapper<SysFlow> c = new LambdaQueryWrapper();
            //c.eq(SysFlow::getSysDisable, 0);// ??????????????????
            List<SysFlow> enabledFlows = sysFlowMapper.selectList(c);
            List<String> enabledFlowIds = new ArrayList<>();
            if(Objects.nonNull(enabledFlows)){
                for(SysFlow enabledFlow : enabledFlows){
                    enabledFlowIds.add(enabledFlow.getId());
                }
            }

            LambdaQueryWrapper<SysFlowExt> condition2 = new LambdaQueryWrapper();
            condition2.eq(SysFlowExt::getOperation, ttp.getUserId());
            List<SysFlowExt> customUserTasks = sysFlowExtMapper.selectList(condition2);

            if(Objects.nonNull(customUserTasks) && !customUserTasks.isEmpty()){
                customUserTasks.stream().forEach(customUserTask -> {
                    if(enabledFlowIds.contains(customUserTask.getHId())){
                        List<Task> actTasks = taskService.createTaskQuery().active().taskDefinitionKey(customUserTask.getNode()).orderByTaskCreateTime().desc().list();
                        if(Objects.nonNull(actTasks) && !actTasks.isEmpty()){
                            actTasks.stream().forEach(actTask ->{
                                if(Objects.nonNull(actTask)){
                                    todo.getAndIncrement();
                                }
                            });
                        }
                    }
                });
            }

            String[] roles = ttp.getRole();
            if(Objects.nonNull(roles) && roles.length>0 ){
                LambdaQueryWrapper<SysFlowExt> condition3 = new LambdaQueryWrapper();
                condition3.in(SysFlowExt::getOperation, roles);
                List<SysFlowExt> customRoleTasks = sysFlowExtMapper.selectList(condition3);

                if(Objects.nonNull(customRoleTasks) && !customRoleTasks.isEmpty()){
                    customRoleTasks.stream().forEach(customRoleTask -> {
                        if(enabledFlowIds.contains(customRoleTask.getHId())){
                            List<Task> actTasks = taskService.createTaskQuery().active().taskDefinitionKey(customRoleTask.getNode()).orderByTaskCreateTime().desc().list();
                            if(Objects.nonNull(actTasks) && !actTasks.isEmpty()){
                                actTasks.stream().forEach(actTask ->{
                                    if(Objects.nonNull(actTask)){
                                        todo.getAndIncrement();
                                    }
                                });
                            }
                        }
                    });
                }
            }

            return ResponseResult.success("????????????", MonitorResult.newInstance(done, todo.get(), create)).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), ttp).response();
		} catch (Exception ex) {
            ex.printStackTrace();
			return ResponseResult.error(ex.getMessage()).response();
		}

    }

    @ApiOperation(value = "?????????????????????")
    @GetMapping("/task/isLast/{data_id}")
    public ResponseEntity<?> getIsLastNode(@PathVariable String data_id) {
	    try{
	        boolean isLast;

	        List<SysTask> taskHeads = sysTaskMapper.selectActedBySId(data_id);
            if(Objects.isNull(taskHeads) || taskHeads.isEmpty())
                return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR, "????????????????????????ID", data_id).response();

            //1. ??????????????????????????????
            SysTask taskHead = taskHeads.get(0);
            String processInstanceId = taskHead.getTaskId();
            Task t = taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult();
            if(Objects.isNull(t))
                return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR, "??????????????????", data_id).response();

            Collection<FlowElement> allNodes = getNodes(t.getProcessDefinitionId());
            List<FlowElement> currentElements = allNodes.stream().filter(node -> node.getId().equals(t.getTaskDefinitionKey())).collect(Collectors.toList());
            FlowNode currentElement = (FlowNode)currentElements.get(0);
            FlowElement node = currentElement.getOutgoingFlows().get(0).getTargetFlowElement();
            isLast = (node instanceof EndEvent);

            return ResponseResult.success("????????????", isLast).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
            ex.printStackTrace();
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    @ApiOperation(value = "????????????", notes = "??????????????????????????????")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "type", value = "??????????????????", dataType = "String", paramType = "query", example = ""),
            @ApiImplicitParam(name = "route", value = "??????", dataType = "String", paramType = "query", example = ""),
            @ApiImplicitParam(name = "userId", value = "??????ID", dataType = "String", paramType = "query", example = ""),
            @ApiImplicitParam(name = "dataId", value = "??????ID", dataType = "String", paramType = "query", example = ""),
    })
    @PostMapping("/task/create")
    @Transactional
    public ResponseEntity<?> taskCreate(@RequestBody TaskParam tp) {
        try{
            List<SysTask> taskHeads = sysTaskMapper.selectEnabledBySId(tp.getDataId());
            if(Objects.nonNull(taskHeads) && !taskHeads.isEmpty())
                return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR, "?????????????????????????????????????????????, ??????????????????", tp).response();

            //????????????
            LambdaQueryWrapper<SysFlow> lambdaQuery = new QueryWrapper<SysFlow>().lambda();
            lambdaQuery.eq(SysFlow::getSysModel, tp.getType());
            lambdaQuery.eq(SysFlow::getSysDisable, false);
            SysFlow flow = sysFlowMapper.selectOne(lambdaQuery);
            if(Objects.isNull(flow))
                return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR, "???????????????????????????????????????", tp).response();

            String def_id = getProcessDefIdByProcessId(flow.getDeploymentId());
            if(Objects.isNull(def_id))
                return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR, "??????????????????????????????", tp).response();


            Map<String, Object> variables = Optional.ofNullable(tp.getConditions()).orElse(new HashMap<>());
            variables.put("creator", tp.getUserId());
            variables.put("creatorName", tp.getUserName());
            ProcessInstance processInstance = runtimeService.startProcessInstanceById(def_id, tp.getType(), variables);//??????????????????????????????????????????
            runtimeService.setProcessInstanceName(processInstance.getId(), tp.getDataId());

            LambdaQueryWrapper<SysFlowExt> lambdaQueryFlowExt = new QueryWrapper<SysFlowExt>().lambda();
            lambdaQueryFlowExt.eq(SysFlowExt::getHId, flow.getId());
            lambdaQueryFlowExt.isNotNull(SysFlowExt::getField);
            lambdaQueryFlowExt.isNotNull(SysFlowExt::getConditions);
            lambdaQueryFlowExt.isNotNull(SysFlowExt::getValue);
            List<SysFlowExt> flowExtList = sysFlowExtMapper.selectList(lambdaQueryFlowExt);
            initGateways(processInstance, flowExtList);

            //????????????1
            SysTask sysTask = new SysTask();
            String taskId = UUID.randomUUID().toString();
            sysTask.setId(taskId);
            sysTask.setSKey(tp.getType());
            sysTask.setSId(tp.getDataId());
            sysTask.setTaskId(processInstance.getId());
            sysTask.setRoute(tp.getRoute());
            sysTask.setStatus(ProcessStatus.ACTIVE.getCode());
            sysTaskMapper.insert(sysTask);

            //????????????2: ??????
            SysTaskExt startNode = new SysTaskExt();
            String startNodeId = UUID.randomUUID().toString();
            startNode.setId(startNodeId);
            startNode.setHId(taskId);
            String startNodeKey = getStartKey(def_id);
            startNode.setNode(startNodeKey);
            startNode.setUser(tp.getUserId());
            startNode.setUserName(tp.getUserName());
            startNode.setRecord("");
            startNode.setOpinion("");
            startNode.setTime(System.currentTimeMillis()+"");
            startNode.setOperTime("");
            sysTaskExtMapper.insert(startNode);

            return ResponseResult.success("????????????", processInstance.getId()).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), tp).response();
		} catch (Exception ex) {
            ex.printStackTrace();
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,ex.getMessage(), tp).response();
		}
    }

    @PostMapping("task/complete")
    @ApiOperation(value = "???????????????????????????", notes = "???????????????????????????")
    public ResponseEntity<?> taskComplete(@RequestBody TaskCompleteParam param) {
        boolean isLast = false;

        try{
            //0. ?????????????????????
            List<SysTask> taskHeads = sysTaskMapper.selectActedBySId(param.getDataId());
            if(Objects.isNull(taskHeads) || taskHeads.isEmpty())
                return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR, "??????????????????ID", param).response();

            //1. ??????????????????????????????
            SysTask taskHead = taskHeads.get(0);
            String processInstanceId = taskHead.getTaskId();
            Task current = taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult();

            if(Objects.isNull(current))
                return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR, "????????????????????????", param).response();

            //2. ????????????
            if(!permissionCheck(param, current))
                 return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR, "??????????????????", null).response();

            LambdaQueryWrapper<SysTask> lambdaQuery = new QueryWrapper<SysTask>().lambda();
            lambdaQuery.eq(SysTask::getTaskId, processInstanceId);
            SysTask task = sysTaskMapper.selectOne(lambdaQuery);

            //??????zhuangtai
            if(!ProcessStatus.ACTIVE.getCode().equals(task.getStatus()))
                 return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR, "??????????????????????????????", null).response();

            //3. ????????????
            if(Operations.APPROVE.getCode().equals(param.getCommand())){//3.1 ?????????
                Task lastPoint = taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult();
                //3.1 ????????????????????????
                taskService.addComment(current.getId(), processInstanceId, Operations.APPROVE.getMsg());
                HashMap<String, Object> variables = new HashMap<>(1);
                variables.put("msg", "????????????");
                taskService.complete(current.getId());

                //3.2 ????????????Task????????????????????????????????????
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
                currentNode.setUserName(param.getUserName());
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

                    //???????????????status
                    task.setStatus(ProcessStatus.COMPLETE.getCode());

                    //??????end??????;
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
                    endNode.setUserName("");
                    endNode.setRecord("");//param.getOpinion()
                    endNode.setOpinion("");//Operations.APPROVE.getCode()
                    endNode.setTime(System.currentTimeMillis()+"");
                    endNode.setOperTime("0");

                    sysTaskExtMapper.insert(endNode);
                }

            } else if(Operations.RECALL.getCode().equals(param.getCommand())){//3.2 ?????????
                //????????????????????????ID
                LambdaQueryWrapper<SysTaskExt> lambdaQuery2 = new QueryWrapper<SysTaskExt>().lambda();
                lambdaQuery2.eq(SysTaskExt::getHId, task.getId());
                lambdaQuery2.orderByDesc(SysTaskExt::getTime).last("limit 1");
                List<SysTaskExt> tasksList = sysTaskExtMapper.selectList(lambdaQuery2);
                SysTaskExt currentNode = tasksList.get(0);//?????????????????????
                Task currentTask = taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult();

                Collection<FlowElement> allNodes = getNodes(current.getProcessDefinitionId());

                List<FlowElement> currentElements = allNodes.stream().filter(node -> node.getId().equals(currentTask.getTaskDefinitionKey())).collect(Collectors.toList());
                FlowNode currentElement = (FlowNode)currentElements.get(0);
                //FIXME:?????????????????????????????????????????????????????????0????????????????
                FlowElement prevNode = currentElement.getIncomingFlows().get(0).getSourceFlowElement();
                if(prevNode instanceof StartEvent)
                    return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR, "???????????????????????????????????????, ????????????", param.getCommand()).response();

                //????????????????????????
                String executionEntityId =processEngine.getManagementService().executeCommand(new DeleteTaskCommand(currentTask.getId()));
                //???????????????????????????
                processEngine.getManagementService().executeCommand(new JumpCommand((FlowNode)prevNode, executionEntityId));

                //????????????Task????????????????????????????????????
                SysTaskExt newNode = new SysTaskExt();
                String newNodeId = UUID.randomUUID().toString();
                newNode.setId(newNodeId);
                newNode.setHId(currentNode.getHId());
                newNode.setNode(currentTask.getTaskDefinitionKey());
                newNode.setUser(param.getUserId());
                newNode.setUserName(param.getUserName());
                newNode.setRecord(param.getOpinion());
                newNode.setOpinion(Operations.RECALL.getCode());
                Long now = System.currentTimeMillis();
                Long timeBetween = now - Long.parseLong(currentNode.getTime());
                newNode.setTime(now.toString());
                newNode.setOperTime(timeBetween.toString());
                sysTaskExtMapper.insert(newNode);

                task.setStatus(ProcessStatus.ACTIVE.getCode());
            } else if(Operations.REJECT.getCode().equals(param.getCommand())){//3.3 ??????
                //????????????????????????ID
                LambdaQueryWrapper<SysTaskExt> lambdaQuery2 = new QueryWrapper<SysTaskExt>().lambda();
                lambdaQuery2.eq(SysTaskExt::getHId, task.getId());
                lambdaQuery2.orderByDesc(SysTaskExt::getTime).last("limit 1");
                List<SysTaskExt> tasksList = sysTaskExtMapper.selectList(lambdaQuery2);
                SysTaskExt currentNode = tasksList.get(0);//?????????????????????
                String currentTaskDefKey = currentNode.getNode();
                Task currentTask = taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult();

                Collection<FlowElement> allNodes = getNodes(current.getProcessDefinitionId());

                List<FlowElement> startElement = allNodes.stream().filter(node -> node instanceof StartEvent).collect(Collectors.toList());
                FlowNode startTaskNode = (FlowNode)startElement.get(0);
                FlowElement firstNode = startTaskNode.getOutgoingFlows().get(0).getTargetFlowElement();//?????????

                // ????????????????????????
                String executionEntityId =processEngine.getManagementService().executeCommand(new DeleteTaskCommand(currentTask.getId()));
                // ???????????????????????????
                // TODO:org.activiti.engine.ActivitiException: ?????????????????????????????????????????????
                processEngine.getManagementService().executeCommand(new JumpCommand((FlowNode)firstNode, executionEntityId));

                //????????????Task????????????????????????????????????
                SysTaskExt newNode = new SysTaskExt();
                String newNodeId = UUID.randomUUID().toString();
                newNode.setId(newNodeId);
                newNode.setHId(currentNode.getHId());
                newNode.setNode(startTaskNode.getId());
                newNode.setUser(param.getUserId());
                newNode.setUserName(param.getUserName());
                newNode.setRecord(param.getOpinion());
                newNode.setOpinion(Operations.REJECT.getCode());
                Long now = System.currentTimeMillis();
                Long timeBetween = now - Long.parseLong(currentNode.getTime());
                newNode.setTime(now.toString());
                newNode.setOperTime(timeBetween.toString());
                sysTaskExtMapper.insert(newNode);

                task.setStatus(ProcessStatus.ACTIVE.getCode());
            } else if(Operations.CANCEL1.getCode().equals(param.getCommand())){//3.4 ??????
                //????????????????????????ID
                LambdaQueryWrapper<SysTaskExt> lambdaQuery2 = new QueryWrapper<SysTaskExt>().lambda();
                lambdaQuery2.eq(SysTaskExt::getHId, task.getId());
                lambdaQuery2.orderByDesc(SysTaskExt::getTime).last("limit 1");
                List<SysTaskExt> tasksList = sysTaskExtMapper.selectList(lambdaQuery2);
                SysTaskExt currentNode = tasksList.get(0);//?????????????????????
                String currentTaskDefKey = currentNode.getNode();
                Task currentTask = taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult();

                Collection<FlowElement> allNodes = getNodes(current.getProcessDefinitionId());

                List<FlowElement> startElement = allNodes.stream().filter(node -> node instanceof EndEvent).collect(Collectors.toList());
                FlowElement endTaskNode = startElement.get(0);

                //????????????????????????
                String executionEntityId =processEngine.getManagementService().executeCommand(new DeleteTaskCommand(currentTask.getId()));
                //???????????????????????????
                processEngine.getManagementService().executeCommand(new JumpCommand((FlowNode)endTaskNode, executionEntityId));

                //????????????Task????????????????????????????????????
                SysTaskExt newNode = new SysTaskExt();
                String newNodeId = UUID.randomUUID().toString();
                newNode.setId(newNodeId);
                newNode.setHId(currentNode.getHId());
                newNode.setNode(endTaskNode.getId());
                newNode.setUser(param.getUserId());
                newNode.setUserName(param.getUserName());
                newNode.setRecord(param.getOpinion());
                newNode.setOpinion(Operations.CANCEL1.getCode());
                Long now = System.currentTimeMillis();
                Long timeBetween = now - Long.parseLong(currentNode.getTime());
                newNode.setTime(now.toString());
                newNode.setOperTime(timeBetween.toString());
                sysTaskExtMapper.insert(newNode);

                task.setStatus(ProcessStatus.SHUTDOWN.getCode());
            } else if(Operations.CANCEL2.getCode().equals(param.getCommand())){//3.4 ??????
                //????????????????????????ID
                LambdaQueryWrapper<SysTaskExt> lambdaQuery2 = new QueryWrapper<SysTaskExt>().lambda();
                lambdaQuery2.eq(SysTaskExt::getHId, task.getId());
                lambdaQuery2.orderByDesc(SysTaskExt::getTime).last("limit 1");
                List<SysTaskExt> tasksList = sysTaskExtMapper.selectList(lambdaQuery2);
                SysTaskExt currentNode = tasksList.get(0);//?????????????????????
                String currentTaskDefKey = currentNode.getNode();
                Task currentTask = taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult();

                Collection<FlowElement> allNodes = getNodes(current.getProcessDefinitionId());

                List<FlowElement> startElement = allNodes.stream().filter(node -> node instanceof EndEvent).collect(Collectors.toList());
                FlowElement endTaskNode = startElement.get(0);

                //????????????????????????
                String executionEntityId =processEngine.getManagementService().executeCommand(new DeleteTaskCommand(currentTask.getId()));
                //???????????????????????????
                processEngine.getManagementService().executeCommand(new JumpCommand((FlowNode)endTaskNode, executionEntityId));

                //????????????Task????????????????????????????????????
                SysTaskExt newNode = new SysTaskExt();
                String newNodeId = UUID.randomUUID().toString();
                newNode.setId(newNodeId);
                newNode.setHId(currentNode.getHId());
                newNode.setNode(endTaskNode.getId());
                newNode.setUser(param.getUserId());
                newNode.setUserName(param.getUserName());
                newNode.setRecord(param.getOpinion());
                newNode.setOpinion(Operations.CANCEL2.getCode());
                Long now = System.currentTimeMillis();
                Long timeBetween = now - Long.parseLong(currentNode.getTime());
                newNode.setTime(now.toString());
                newNode.setOperTime(timeBetween.toString());
                sysTaskExtMapper.insert(newNode);

                task.setStatus(ProcessStatus.SHUTDOWN.getCode());
            } else {
                return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR, "????????????????????????", param.getCommand()).response();
            }

            sysTaskMapper.updateById(task);

            return ResponseResult.success("????????????", isLast).response();
		} catch (BizException be) {
            return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
            ex.printStackTrace();
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    @GetMapping("task/active/{instance_id}")
    @ApiOperation(value = "???????????????????????????", notes = "???????????????????????????")
    public ResponseEntity<?> Suspended(@PathVariable String instance_id){
        try {
            List<SysTask> tasks = sysTaskMapper.selectByTaskId(instance_id);
            if(tasks==null || tasks.isEmpty()) {
                return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,"??????????????????", null).response();
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

            return ResponseResult.success("????????????", active?"??????":"??????").response();
        } catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    /**
     * ???????????????????????????????????????????????????
     *
     * @param param 
     * @return boolean
     * @author apr
     * @date 2021/11/9 9:16
     */
    private boolean permissionCheck(TaskCompleteParam param, Task currentTask) {
        boolean match =false;

        LambdaQueryWrapper<SysFlowExt> condition = new QueryWrapper<SysFlowExt>().lambda();
        condition.eq(SysFlowExt::getNode, currentTask.getTaskDefinitionKey());
        List<SysFlowExt> taskNodes = sysFlowExtMapper.selectList(condition);

        if(Objects.nonNull(taskNodes) && !taskNodes.isEmpty()) {
            for(SysFlowExt node : taskNodes) {
                LambdaQueryWrapper<SysFlow> flowQuery = new QueryWrapper<SysFlow>().lambda();
                flowQuery.eq(SysFlow::getId, node.getHId());
                flowQuery.eq(SysFlow::getSysDisable, false);
                SysFlow flow = sysFlowMapper.selectOne(flowQuery);
                if(Objects.nonNull(flow)){
                    String type = node.getUserType();
                    String values = node.getOperation();
                    //System.err.println("type="+type+"  values="+values);
                    if(Objects.nonNull(values)){
                        List<String> passCode = Arrays.asList(values.split(","));
                        if("user".equals(type) ){
                            match = (passCode.contains(param.getUserId()));
                        }else if("role".equals(type)){
                            match = (!Collections.disjoint(Arrays.asList(param.getRole()), passCode));
                        }
                    }
                }
            }
        }

        //?????????????????????
        if(!match && Operations.CANCEL2.getCode().equals(param.getCommand())){
            Collection<FlowElement> allNodes = getNodes(currentTask.getProcessDefinitionId());
            List<FlowElement> currentElements = allNodes.stream().filter(node -> node.getId().equals(currentTask.getTaskDefinitionKey())).collect(Collectors.toList());
            FlowNode currentElement = (FlowNode)currentElements.get(0);
            SequenceFlow prevNode = currentElement.getIncomingFlows().get(0);
            if(isStartNode(prevNode)){
                //???????????????
                ProcessInstance processInstance = runtimeService.createProcessInstanceQuery().processInstanceId(currentTask.getProcessInstanceId()).singleResult();
                Object creator = runtimeService.getVariable(processInstance.getId(), "creator");
                match = param.getUserId().equals(creator.toString());
            }
        }

        return match;
    }

    @ApiOperation(value = "???????????????????????????", notes = "???????????????????????????")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "userId", value = "??????id", dataType = "String", paramType = "query", example = ""),
            @ApiImplicitParam(name = "pageIndex", value = "??????", dataType = "String", paramType = "query", example = ""),
            @ApiImplicitParam(name = "pageSize", value = "?????????", dataType = "String", paramType = "query", example = "")
    })
    @GetMapping("/task/user")
    public ResponseEntity<?> getTaskByUser(@RequestParam String userId, @RequestParam @Nullable Long pageIndex,
                                           @RequestParam @Nullable Long pageSize) {//"032bf875-99b0-4c85-91c0-e128fc759565"
        try{
            TaskTodoPager<TaskTodo> p;
            List<TaskTodo> tasks = new ArrayList<>();

            //??????????????????
            LambdaQueryWrapper<SysFlow> c = new LambdaQueryWrapper();
            //c.eq(SysFlow::getSysDisable, 0);
            List<SysFlow> enabledFlows = sysFlowMapper.selectList(c);
            List<String> enabledFlowIds = new ArrayList<>();
            if(Objects.nonNull(enabledFlows)){
                //enabledFlowIds = enabledFlows.stream().map(SysFlow::getId).collect(Collectors.toList());
                for(SysFlow enabledFlow : enabledFlows){
                    enabledFlowIds.add(enabledFlow.getId());
                }
            }

            LambdaQueryWrapper<SysFlowExt> condition = new LambdaQueryWrapper();
            condition.eq(SysFlowExt::getOperation, userId);
            //condition.orderByDesc(SysFlowExt::getId).last("LIMIT "+ p.getFirst() + ", " + p.getPageSize());
            List<SysFlowExt> customUserTasks = sysFlowExtMapper.selectList(condition);

            if(Objects.nonNull(customUserTasks) && !customUserTasks.isEmpty()){
                customUserTasks.stream().forEach(customUserTask -> {
                    if(enabledFlowIds.contains(customUserTask.getHId())){
                        List<Task> actTasks = taskService.createTaskQuery().active().taskDefinitionKey(customUserTask.getNode()).orderByTaskCreateTime().desc().list();
                        if(Objects.nonNull(actTasks) && !actTasks.isEmpty()){
                            actTasks.stream().forEach(actTask ->{
                                LambdaQueryWrapper<SysTask> taskCondition = new LambdaQueryWrapper<>();
                                taskCondition.eq(SysTask::getTaskId, actTask.getProcessInstanceId());
                                SysTask sysTask = sysTaskMapper.selectOne(taskCondition);

                                if(Objects.nonNull(actTask)){
                                    String creator = getVariableByInstanceId("creator", actTask.getProcessInstanceId());
                                    String creatorName = getVariableByInstanceId("creatorName", actTask.getProcessInstanceId());
                                    TaskTodo tt = new TaskTodo(
                                        actTask.getId(),
                                        actTask.getProcessInstanceId(),
                                        actTask.getName()==null?customUserTask.getNode():actTask.getName(),
                                        customUserTask.getOperation(),
                                        customUserTask.getOperName(),
                                        creator,
                                        creatorName,
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

            p = new TaskTodoPager(null, pageIndex, pageSize, new Long(tasks.size()));
            int startIndex = p.getFirst().intValue();
            int endIndex = p.getFirst().intValue() + p.getPageSize().intValue();
            List<TaskTodo> taskPage = tasks.subList(
                    startIndex>tasks.size()?tasks.size():startIndex,
                    endIndex>tasks.size()?tasks.size():endIndex
            );
            p.setData(taskPage);

            return ResponseResult.success("????????????", p).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
            ex.printStackTrace();
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    @ApiOperation(value = "???????????????????????????", notes = "???????????????????????????")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "userId", value = "??????id", dataType = "String", paramType = "query", example = ""),
            @ApiImplicitParam(name = "role", value = "????????????id", dataType = "String", paramType = "query", example = ""),
            @ApiImplicitParam(name = "pageIndex", value = "??????", dataType = "String", paramType = "query", example = ""),
            @ApiImplicitParam(name = "pageSize", value = "?????????", dataType = "String", paramType = "query", example = "")
    })
    @PostMapping("/task/user")
    public ResponseEntity<?> getTaskByUserRole(@RequestBody TaskTodoParam ttp) {//"032bf875-99b0-4c85-91c0-e128fc759565"
        try{
            TaskTodoPager<TaskTodo> p;
            List<TaskTodo> tasks = new ArrayList<>();

            //??????????????????
            LambdaQueryWrapper<SysFlow> c = new LambdaQueryWrapper();
            //c.eq(SysFlow::getSysDisable, 0);
            List<SysFlow> enabledFlows = sysFlowMapper.selectList(c);
            List<String> enabledFlowIds = new ArrayList<>();
            if(Objects.nonNull(enabledFlows)){
                //enabledFlowIds = enabledFlows.stream().map(SysFlow::getId).collect(Collectors.toList());
                for(SysFlow enabledFlow : enabledFlows){
                    enabledFlowIds.add(enabledFlow.getId());
                }
            }

            List<SysFlowExt> customUserTasks = new ArrayList();

            LambdaQueryWrapper<SysFlowExt> condition1 = new LambdaQueryWrapper();
            condition1.eq(SysFlowExt::getOperation, ttp.getUserId());
            condition1.eq(SysFlowExt::getUserType, "user");
            List<SysFlowExt> customUserTasks1 = sysFlowExtMapper.selectList(condition1);
            if( Objects.nonNull(customUserTasks1) )
                customUserTasks.addAll(customUserTasks1);

            String[] roles = ttp.getRole();
            if(Objects.nonNull(roles) && roles.length>0){
                LambdaQueryWrapper<SysFlowExt> condition2 = new LambdaQueryWrapper();
                condition2.in(SysFlowExt::getOperation, roles);
                condition2.eq(SysFlowExt::getUserType, "role");
                List<SysFlowExt> customUserTasks2 = sysFlowExtMapper.selectList(condition2);
                if( Objects.nonNull(customUserTasks2) )
                    customUserTasks.addAll(customUserTasks2);
            }

            if(!customUserTasks.isEmpty()){
                customUserTasks.stream().forEach(customUserTask -> {
                    if(enabledFlowIds.contains(customUserTask.getHId())){
                        List<Task> actTasks = taskService.createTaskQuery().active().taskDefinitionKey(customUserTask.getNode()).orderByTaskCreateTime().desc().list();
                        if(Objects.nonNull(actTasks) && !actTasks.isEmpty()){
                            actTasks.stream().forEach(actTask ->{
                                LambdaQueryWrapper<SysTask> taskCondition = new LambdaQueryWrapper<>();
                                taskCondition.eq(SysTask::getTaskId, actTask.getProcessInstanceId());
                                SysTask sysTask = sysTaskMapper.selectOne(taskCondition);

                                if(Objects.nonNull(actTask)){
                                    String creator = getVariableByInstanceId("creator", actTask.getProcessInstanceId());
                                    String creatorName = getVariableByInstanceId("creatorName", actTask.getProcessInstanceId());
                                    TaskTodo tt = new TaskTodo(
                                        actTask.getId(),
                                        actTask.getProcessInstanceId(),
                                        actTask.getName()==null?customUserTask.getNode():actTask.getName(),
                                        customUserTask.getOperation(),
                                        customUserTask.getOperName(),
                                        creator,
                                        creatorName,
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

            Collections.sort(tasks);

            p = new TaskTodoPager(null, ttp.getPageIndex(), ttp.getPageSize(), new Long(tasks.size()));
            int startIndex = p.getFirst().intValue();
            int endIndex = p.getFirst().intValue() + p.getPageSize().intValue();
            List<TaskTodo> taskPage = tasks.subList(
                    startIndex>tasks.size()?tasks.size():startIndex,
                    endIndex>tasks.size()?tasks.size():endIndex
            );
            p.setData(taskPage);

            return ResponseResult.success("????????????", p).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
            ex.printStackTrace();
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    @ApiOperation(value = "????????????id????????????(??????)??????")
    @GetMapping("/task/sysTaskList/{id}")
    public ResponseEntity<?> getTaskByHeaderId(@PathVariable String id) {
        try{
            LambdaQueryWrapper<SysTaskExt> lambdaQuery2 = new QueryWrapper<SysTaskExt>().lambda();
            lambdaQuery2.eq(SysTaskExt::getHId, id);
            lambdaQuery2.orderByDesc(SysTaskExt::getTime);
            List<SysTaskExt> tasksList = sysTaskExtMapper.selectList(lambdaQuery2);

            return ResponseResult.success("????????????", tasksList).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), id).response();
		} catch (Exception ex) {
            ex.printStackTrace();
            return ResponseResult.obtain(CodeMsgs.SYSTEM_BASE_ERROR,ex.getMessage(), id).response();
		}
    }

    @ApiOperation(value = "????????????id????????????(??????)??????")
    @GetMapping("/task/list/{head_id}/{show_history}")
    public ResponseEntity<?> getTaskWithByHeaderId(@PathVariable String head_id, @PathVariable boolean show_history) {
        try{
            LambdaQueryWrapper<SysTask> lambdaQuery = new QueryWrapper<SysTask>().lambda();
            lambdaQuery.eq(SysTask::getId, head_id);
            SysTask taskHeader = sysTaskMapper.selectOne(lambdaQuery);
            String dataId = taskHeader.getSId();

            List<SysTask> taskHeaders = new ArrayList();
            if(show_history){
                LambdaQueryWrapper<SysTask> lambdaQuery1 = new QueryWrapper<SysTask>().lambda();
                lambdaQuery1.eq(SysTask::getSId, dataId);
                taskHeaders = sysTaskMapper.selectList(lambdaQuery1);
            }else{
                taskHeaders.add(taskHeader);
            }

            Map<String, List<SysTaskExt>> m = new HashMap<String, List<SysTaskExt>>();
            taskHeaders.stream().forEach(header -> {
                LambdaQueryWrapper<SysTaskExt> lambdaQuery2 = new QueryWrapper<SysTaskExt>().lambda();
                lambdaQuery2.eq(SysTaskExt::getHId, header.getId());
                lambdaQuery2.orderByDesc(SysTaskExt::getTime);
                List<SysTaskExt> tasksList = sysTaskExtMapper.selectList(lambdaQuery2);

                m.put(header.getTaskId(), tasksList);
            });

            List<SysTaskExtResult> result = new ArrayList();
            m.forEach((k,v) -> {
                v.stream().forEach(taskExt -> {
                    if(taskExt.getNode().startsWith("startNode")){
                        result.add(SysTaskExtResult.newInstance(taskExt, "??????"));
                    }else if(taskExt.getNode().startsWith("Event_")){
                        result.add(SysTaskExtResult.newInstance(taskExt, "??????"));
                    }else{
                        HistoricTaskInstance nodeInfo = historyService.createHistoricTaskInstanceQuery().processInstanceId(k).taskDefinitionKey(taskExt.getNode()).singleResult();
                        if(Objects.nonNull(nodeInfo))
                            result.add(SysTaskExtResult.newInstance(taskExt, nodeInfo.getName()));
                    }
                });
            });

            Collections.sort(result);
            return ResponseResult.success("????????????",result).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), head_id).response();
		} catch (Exception ex) {
            ex.printStackTrace();
            return ResponseResult.obtain(CodeMsgs.SYSTEM_BASE_ERROR,ex.getMessage(), head_id).response();
		}
    }

    @ApiOperation(value = "????????????????????????")
    @GetMapping("/task/startStatus/{data_id}")
    public ResponseEntity<?> getRunStatusByDataId(@PathVariable String data_id) {//"032bf875-99b0-4c85-91c0-e128fc759565"
        try{
            boolean isStart = false;
            List<SysTask> tasksHeader = sysTaskMapper.selectEnabledBySId(data_id);
            if(Objects.nonNull(tasksHeader) && !tasksHeader.isEmpty()){
                LambdaQueryWrapper<SysTaskExt> lambdaQuery2 = new QueryWrapper<SysTaskExt>().lambda();
                lambdaQuery2.eq(SysTaskExt::getHId, tasksHeader.get(0).getId());
                List<SysTaskExt> tasksList = sysTaskExtMapper.selectList(lambdaQuery2);

                isStart =  (Objects.nonNull(tasksList) && tasksList.size() > 1);
            }

            return ResponseResult.success("????????????", isStart).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), data_id).response();
		} catch (Exception ex) {
            ex.printStackTrace();
            return ResponseResult.obtain(CodeMsgs.SYSTEM_BASE_ERROR,ex.getMessage(), data_id).response();
		}
    }

        @ApiOperation(value = "??????????????????????????????")
    @GetMapping("/task/beginStatus/{data_id}")
    public ResponseEntity<?> getStartStatusByDataId(@PathVariable String data_id) {//"032bf875-99b0-4c85-91c0-e128fc759565"
        try{
            boolean isStart = false;
            List<SysTask> tasksHeader = sysTaskMapper.selectEnabledBySId(data_id);
            if(Objects.nonNull(tasksHeader) && !tasksHeader.isEmpty()){
                LambdaQueryWrapper<SysTaskExt> lambdaQuery2 = new QueryWrapper<SysTaskExt>().lambda();
                lambdaQuery2.eq(SysTaskExt::getHId, tasksHeader.get(0).getId());
                List<SysTaskExt> tasksList = sysTaskExtMapper.selectList(lambdaQuery2);

                isStart =  (Objects.nonNull(tasksList) && tasksList.size() == 1);
            }

            return ResponseResult.success("????????????", isStart).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), data_id).response();
		} catch (Exception ex) {
            ex.printStackTrace();
            return ResponseResult.obtain(CodeMsgs.SYSTEM_BASE_ERROR,ex.getMessage(), data_id).response();
		}
    }

    @ApiOperation(value = "sys_task????????????")
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
                return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,"??????????????????", sysTask).response();

            //?????????????????????
            List<SysTask> running = taskHeads.stream().filter(head ->  Arrays.asList("1", "2").contains(head.getStatus())).collect(Collectors.toList());
            result = running;
            //??????????????????
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

            return ResponseResult.success("????????????", result).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), sysTask).response();
		} catch (Exception ex) {
            ex.printStackTrace();
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    /**
     * ?????????????????????????????????Key
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
     * ?????????????????????????????????
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
     * ????????????????????????????????????
     *
     * @param flowNode 
     * @return org.activiti.bpmn.model.FlowElement
     * @author apr
     * @date 2021/10/28 11:21
     */
    private FlowElement getPrevNode(FlowNode flowNode) {
        List<SequenceFlow> inFlows = flowNode.getIncomingFlows();//??????????????????????????????
        for (SequenceFlow ingoingFlow : inFlows) {//??????????????????
            FlowElement sourceFlowElement = ingoingFlow.getSourceFlowElement();//????????????????????????
            if(sourceFlowElement instanceof UserTask){//???????????????????????????
                return sourceFlowElement;//????????????
            }
        }
        return null;
    }
    
    /**
     * ????????????????????????????????????
     *
     * @param flowNode 
     * @return org.activiti.bpmn.model.FlowElement
     * @author apr
     * @date 2021/10/28 11:21
     */
    private FlowElement getNextNode(FlowNode flowNode) {
        List<SequenceFlow> outFlows = flowNode.getOutgoingFlows();//??????????????????????????????
        for (SequenceFlow outgoingFlow : outFlows) {//??????????????????
            FlowElement targetFlowElement = outgoingFlow.getTargetFlowElement();//????????????????????????
            if(targetFlowElement instanceof UserTask){//???????????????????????????
                return targetFlowElement;//????????????
            }
        }
        return null;
    }

    /**
     * ????????????id??????????????????id
     *
     * @param process_id
     * @return java.lang.String
     * @author apr
     * @date 2021/10/27 14:43
     */
    private String getProcessDefIdByProcessId(String process_id){
           List<ProcessDefinition> list = processEngine.getRepositoryService()//???????????????????????????????????????Service
                    .createProcessDefinitionQuery()
                    .deploymentId(process_id)
                    .list();

            if(list==null || list.isEmpty() || list.get(0)==null)
                return null;
            return list.get(0).getId();
    }


    /**
     * ??????????????????????????????????????????, ?????????
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
	 * ?????????????????????????????????
	 * ???????????????NeedsActiveTaskCmd??????????????????????????????????????????????????????????????????????????????????????????Command??????
	 */
	public class DeleteTaskCommand extends NeedsActiveTaskCmd<String> {
		public DeleteTaskCommand(String taskId){
			super(taskId);
		}
		@Override
		public String execute(CommandContext commandContext, TaskEntity currentTask){
			//??????????????????
			TaskEntityManagerImpl taskEntityManager = (TaskEntityManagerImpl)commandContext.getTaskEntityManager();
			//??????????????????????????????????????????????????????
			ExecutionEntity executionEntity = currentTask.getExecution();
			//??????????????????,????????????
			taskEntityManager.deleteTask(currentTask, "jumpReason", false, false);
			return executionEntity.getId();
		}
		@Override
		public String getSuspendedTaskException() {
			return "???????????????????????????";
		}
	}

    /**
	 * ?????????????????????????????????id?????????????????????
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
			//?????????????????????????????????
			List<SequenceFlow> flows = flowElement.getIncomingFlows();
			if(flows==null || flows.size()<1){
				throw new BizException("?????????????????????????????????????????????");
			}
			//?????????????????????????????????????????????????????????????????????????????????????????????????????????
			ExecutionEntity executionEntity = commandContext.getExecutionEntityManager().findById(executionId);
			executionEntity.setCurrentFlowElement(flows.get(0));
			commandContext.getAgenda().planTakeOutgoingSequenceFlowsOperation(executionEntity, true);
			return null;
		}
	}

	private String getVariableByInstanceId(String key, String instId, String defaultVal){
        String value = defaultVal;

        List<HistoricVariableInstance> varList = processEngine.getHistoryService()
                    .createHistoricVariableInstanceQuery()
                    .processInstanceId(instId)
                    .variableName(key)
                    .list();
        if(Objects.nonNull(varList) && !varList.isEmpty()){
            value = String.valueOf(varList.get(0).getValue());
        }

        return value;
    }
    private String getVariableByInstanceId(String key, String instId){
        return getVariableByInstanceId(key, instId, null);
    }

    private boolean isStartNode(SequenceFlow currentElement){
        FlowElement prevNode = currentElement.getSourceFlowElement();
        if(prevNode != null && prevNode instanceof Gateway)//ExclusiveGateway
            return isStartNode(((Gateway)prevNode).getIncomingFlows().get(0));
        else
            return prevNode instanceof StartEvent;
    }
}
