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
import com.yuepong.workflow.utils.Operations;
import com.yuepong.workflow.utils.RestMessgae;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.activiti.bpmn.model.*;
import org.activiti.bpmn.model.Process;
import org.activiti.engine.*;
import org.activiti.engine.history.HistoricActivityInstance;
import org.activiti.engine.impl.cmd.NeedsActiveTaskCmd;
import org.activiti.engine.impl.interceptor.Command;
import org.activiti.engine.impl.interceptor.CommandContext;
import org.activiti.engine.impl.persistence.entity.ExecutionEntity;
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

    @Autowired
    HistoryService historyService;

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
            taskService.saveTask(task);
        } catch (Exception e) {
            restMessgae = RestMessgae.fail("创建失败", e.getMessage());
            e.printStackTrace();
            return restMessgae;
        }

        restMessgae = RestMessgae.success("创建成功", null);
        return restMessgae;
    }

    @GetMapping("task/user/{user_id}")
    @ApiOperation(value = "查询当前用户的任务", notes = "查询当前用户的任务")
    @Transactional
    public ResponseEntity<?> getTaskByUser(@PathVariable String user_id) {//"032bf875-99b0-4c85-91c0-e128fc759565"
        try{
            List<Task> tasks = taskService.createTaskQuery().active().taskAssignee(user_id).list();
            return ResponseResult.success("请求成功", tasks).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
            ex.printStackTrace();
			return ResponseResult.error(ex.getMessage()).response();
		}
    }


    @GetMapping("model/process/instanceList")
    @ApiOperation(value = "查询当前所有正在进行的流程", notes = "查询当前所有正在进行的流程")
    @Transactional
    public ResponseEntity<?> getInstanceList() {//"032bf875-99b0-4c85-91c0-e128fc759565"
        try{
            List<ProcessInstance> instanceList = runtimeService.createProcessInstanceQuery().active().list();
            return ResponseResult.success("请求成功", instanceList).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
            ex.printStackTrace();
			return ResponseResult.error(ex.getMessage()).response();
		}
    }


    @GetMapping("task/create")
    @ApiOperation(value = "创建任务", notes = "根据流程创建一个任务")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "processId", value = "流程ID", dataType = "String", paramType = "query", example = ""),
    })
    @Transactional
    public ResponseEntity<?> taskCreate(@RequestParam String model_id, @RequestParam String business_id, @RequestParam String user_id) {//"032bf875-99b0-4c85-91c0-e128fc759565"
        try{
            //启动流程
            LambdaQueryWrapper<SysFlow> lambdaQuery = new QueryWrapper<SysFlow>().lambda();
            lambdaQuery.eq(SysFlow::getSysModel, model_id);
            SysFlow flow = sysFlowMapper.selectOne(lambdaQuery);
            if(Objects.isNull(flow))
                return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR, "该业务未绑定流程", model_id).response();

            String def_id = getProcessDefIdByProcessId(flow.getFlowId());//e8ac29e2-363b-11ec-b8d8-3c970ef14df2
            //String processDefinitionKey="proc_def_key"+def_id;
            ProcessInstance processInstance = runtimeService.startProcessInstanceById(def_id, business_id);//对某一个流程启用一个流程实例

            //自定义表1
            SysTask sysTask = new SysTask();
            String taskId = UUID.randomUUID().toString();
            sysTask.setId(taskId);
            sysTask.setSKey(model_id);
            sysTask.setSId(business_id);
            sysTask.setTaskId(processInstance.getId());
            sysTaskMapper.insert(sysTask);

            //自定义表2: 开始
            SysTaskExt startNode = new SysTaskExt();
            String startNodeId = UUID.randomUUID().toString();
            startNode.setId(startNodeId);
            startNode.setHId(taskId);
            String startNodeKey = getStartKey(def_id);
            startNode.setNode(startNodeKey);
            startNode.setUser(user_id);
            startNode.setRecord("");
            startNode.setOpinion("");
            startNode.setTime(System.currentTimeMillis()+"");
            startNode.setOperTime("");
            sysTaskExtMapper.insert(startNode);

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
    public ResponseEntity<?> taskComplete(@RequestParam String instance_id, @RequestParam String user_id, @RequestParam String opinion, @RequestParam String value) {//"032bf875-99b0-4c85-91c0-e128fc759565"
        try{
            //
            //1获取节点信息
            Task current = taskService.createTaskQuery().processInstanceId(instance_id).singleResult();
            //2比对用户
            if(Objects.isNull(current.getAssignee()) || user_id.equals(current.getAssignee())){//2.1通过: 执行完成
                String processInstanceId = current.getProcessInstanceId();

                if(Operations.APPROVE.getCode().equals(opinion)){
                    taskService.addComment(current.getId(), processInstanceId, Operations.APPROVE.getMsg());
                    taskService.complete(current.getId());

                    //自定义表Task记录表表插入一条新的记录
                    LambdaQueryWrapper<SysTask> lambdaQuery = new QueryWrapper<SysTask>().lambda();
                    lambdaQuery.eq(SysTask::getTaskId, instance_id);
                    SysTask task = sysTaskMapper.selectOne(lambdaQuery);

                    LambdaQueryWrapper<SysTaskExt> lambdaQuery2 = new QueryWrapper<SysTaskExt>().lambda();
                    lambdaQuery2.eq(SysTaskExt::getHId, task.getId());
                    lambdaQuery2.orderByDesc(SysTaskExt::getTime).last("limit 1");
                    SysTaskExt lastNode = sysTaskExtMapper.selectOne(lambdaQuery2);

                    SysTaskExt currentNode = new SysTaskExt();
                    String startNodeId = UUID.randomUUID().toString();
                    currentNode.setId(startNodeId);
                    currentNode.setHId(lastNode.getHId());
                    current = taskService.createTaskQuery().processInstanceId(instance_id).singleResult();
                    currentNode.setNode(current.getId());
                    currentNode.setUser(Optional.ofNullable(current.getAssignee()).orElse(""));
                    currentNode.setRecord(Operations.APPROVE.getMsg());
                    currentNode.setOpinion(Operations.APPROVE.getCode());
                    Long now = System.currentTimeMillis();
                    Long timeBetween = now - Long.parseLong(lastNode.getTime());
                    currentNode.setTime(now.toString());
                    currentNode.setOperTime(timeBetween.toString());
                    sysTaskExtMapper.insert(currentNode);
                } else if(Operations.CANCEL.getCode().equals(opinion)){
                    return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR, "非法操作命令代码, 只能传option=1, 同意", opinion).response();
                } else if(Operations.REJECT.getCode().equals(opinion)){
                    //自定义表Task记录表表插入一条新的记录
                    LambdaQueryWrapper<SysTask> lambdaQuery = new QueryWrapper<SysTask>().lambda();
                    lambdaQuery.eq(SysTask::getTaskId, instance_id);
                    SysTask task = sysTaskMapper.selectOne(lambdaQuery);
                    //获取当前最新节点ID
                    LambdaQueryWrapper<SysTaskExt> lambdaQuery2 = new QueryWrapper<SysTaskExt>().lambda();
                    lambdaQuery2.eq(SysTaskExt::getHId, task.getId());
                    lambdaQuery2.orderByDesc(SysTaskExt::getTime).last("limit 2");
                    List<SysTaskExt> tasksList = sysTaskExtMapper.selectList(lambdaQuery2);
                    SysTaskExt currentNode = tasksList.get(0);//倒数第一个节点
                    String currentTaskId = currentNode.getNode();

                    //删除最新节点任务
//                    current = taskService.createTaskQuery().taskId(currentTaskId).singleResult();
//                    taskService.deleteTask(currentTaskId);

                    SysTaskExt currentNodeNew = tasksList.get(1);//倒数第二个节点
                    String currentTaskIdNew = currentNodeNew.getNode();
                    Collection<FlowElement> allNodes = getNodes(current.getProcessDefinitionId());
                    List<FlowElement> currentElement = allNodes.stream().filter(node -> node.getId().equals(currentTaskIdNew)).collect(Collectors.toList());
                    FlowElement element = currentElement.get(0);
                    //获取目标节点定义
                    //FlowNode targetNode = (FlowNode)process.getFlowElement(element.getId());
                    //删除当前运行任务
                    String executionEntityId =processEngine.getManagementService().executeCommand(new DeleteTaskCommand(currentTaskId));
                    //流程执行到来源节点
                    processEngine.getManagementService().executeCommand(new JumpCommand((FlowNode)element, executionEntityId));

                    //自定义表2: 开始
                    SysTaskExt newNode = new SysTaskExt();
                    String newNodeId = UUID.randomUUID().toString();
                    newNode.setId(newNodeId);
                    newNode.setHId(currentNode.getHId());
                    newNode.setNode(element.getId());
                    newNode.setUser(user_id);
                    newNode.setRecord(Operations.REJECT.getMsg());
                    newNode.setOpinion(Operations.REJECT.getCode());
                    Long now = System.currentTimeMillis();
                    Long timeBetween = now - Long.parseLong(currentNode.getTime());
                    newNode.setTime(now.toString());
                    newNode.setOperTime(timeBetween.toString());
                    sysTaskExtMapper.insert(newNode);

                    return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR, "非法操作命令代码, 只能传option=1, 同意", opinion).response();
                } else {
                    return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR, "非法操作命令代码", opinion).response();
                }

                return ResponseResult.success("请求成功", null).response();
            }else{//2.2不通过: 报错
                return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR, "操作用户不匹配", null).response();
            }
            //待确认:是否需要
            // 3查询下一节点的下一节点(outgoing)是否为网关
                //3.1是: 比对我们自己记录的条件
                    //3.1.1通过: 执行完成
                    //3.1.2不通过: 报错
                //3.2否: 执行完成
		} catch (BizException be) {
            return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
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
