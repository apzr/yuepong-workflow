package com.yuepong.workflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.google.common.collect.Lists;
import com.yuepong.jdev.api.bean.ResponseResult;
import com.yuepong.jdev.code.CodeMsgs;
import com.yuepong.jdev.exception.BizException;
import com.yuepong.workflow.dto.*;
import com.yuepong.workflow.mapper.*;
import com.yuepong.workflow.page.pager.ModelPager;
import com.yuepong.workflow.page.pager.ProcessInstancePager;
import com.yuepong.workflow.param.*;
import com.yuepong.workflow.utils.BpmnConverterUtil;
import com.yuepong.workflow.utils.ProcessStatus;
import com.yuepong.workflow.utils.Utils;
import io.swagger.annotations.*;
import org.activiti.bpmn.converter.BpmnXMLConverter;
import org.activiti.bpmn.model.*;
import org.activiti.bpmn.model.Process;
import org.activiti.editor.language.json.converter.BpmnJsonConverter;
import org.activiti.engine.*;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.history.HistoricProcessInstanceQuery;
import org.activiti.engine.history.HistoricTaskInstance;
import org.activiti.engine.history.HistoricVariableInstance;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.Model;
import org.activiti.engine.repository.ModelQuery;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.ProcessInstance;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Apr
 */
@Transactional
@Controller
@Api(tags="模型 Model")
@RequestMapping("/model")
public class DeployController {

    ProcessEngine processEngine = ProcessEngines.getDefaultProcessEngine();

    private final RepositoryService repositoryService;

    @Autowired
    RuntimeService runtimeService;

    @Autowired
    TaskService taskService;

    @Autowired
    HistoryService historyService;

    @Autowired
    SysTaskMapper sysTaskMapper;

    @Autowired
    SysTaskExtMapper sysTaskExtMapper;

    @Autowired
    SysFlowMapper sysFlowMapper;

    @Autowired
    SysFlowExtMapper sysFlowExtMapper;

    @Autowired
    BpmnByteMapper bpmnByteMapper;

    @Autowired
    ObjectMapper objectMapper;

    public DeployController(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @ApiOperation(value = "保存(modelId=-1)或编辑(modelId!=-1)模型", notes = "根据传入的xml文件生成一个新的模型或者绑定一个现有的模型")
    @ApiImplicitParams({
            @ApiImplicitParam(
                    name = "modelId",
                    value = "模型id",
                    dataType = "String",
                    paramType = "query",
                    example = "388d3160-3965-11ec-a578-3c970ef14df2",
                    required = true
            ),
            @ApiImplicitParam(
                    name = "bpmn_xml",
                    value = "BPMN文件字符串",
                    dataType = "String",
                    paramType = "query",
                    example = "<bpmn...>...</...bpmn>",
                    required = true
            ),
            @ApiImplicitParam(
                    name = "svg_xml",
                    value = "SVG文件字符串",
                    dataType = "String",
                    paramType = "query",
                    example = "<svg...>...</...svg>",
                    required = true
            )
    })
    @PostMapping(value = "/{modelId}/xml/save")
    public ResponseEntity<?>  saveModelXml(@PathVariable String modelId, @RequestBody ModelAttr modelAttr) {
        try {
            //bpmn
            String xml = modelAttr.getBpmn_xml();
            String jsonBpmnXml = BpmnConverterUtil.converterXmlToJson(xml).toString();

            //svg
            String svg = modelAttr.getSvg_xml();

            //model
            Model model;
            if("-1".equals(modelId)){
                model = repositoryService.newModel();
            }else{
                model = repositoryService.getModel(modelId);
                model.setVersion(model.getVersion()+1);

                if(existsUnfinishedProcesses(model.getDeploymentId()))
                    return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR, "当前模型存在未执行完的流程, 无法编辑", modelId).response();
            }
            Utils.initModel(model, jsonBpmnXml, objectMapper);

            //db
            repositoryService.saveModel(model);
            repositoryService.addModelEditorSource(model.getId(), jsonBpmnXml.getBytes(StandardCharsets.UTF_8));
            repositoryService.addModelEditorSourceExtra(model.getId(), svg.getBytes(StandardCharsets.UTF_8));

			return ResponseResult.success("请求成功", model.getId()).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), modelId).response();
		} catch (Exception ex) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,ex.getMessage(), null).response();
		}
    }

    /**
     * 当前模型存在没有运行完的流程
     *
     * @param deploymentId
     * @return boolean
     * @author apr
     * @date 2021/11/11 15:31
     */
    private boolean existsUnfinishedProcesses(String deploymentId) {
        List<ProcessInstance> instanceList = runtimeService.createProcessInstanceQuery().deploymentId(deploymentId).list();
        return Objects.nonNull(instanceList) && !instanceList.isEmpty();
    }

    @ApiOperation(value = "根据模型ID执行部署(发布)",notes = "模型：Model, 模型表act_re_model")
    @ApiImplicitParams({ @ApiImplicitParam(
            name = "modelId",
            value = "模型id",
            dataType = "String",
            paramType = "query",
            example = "388d3160-3965-11ec-a578-3c970ef14df2",
            required = true
    )})
    @GetMapping("/deploy/{modelId}")
    public ResponseEntity<?> deploy(@PathVariable String modelId){
		try {
            List<Deployment> exist = repositoryService.createDeploymentQuery().deploymentKey(modelId).list();
		    if(Objects.nonNull(exist) && !exist.isEmpty()){
                exist.stream().forEach(deployment -> {
                    //repositoryService.deleteDeployment(deployment.getId());
                });
                //throw new BizException("模型数据已经被部署过。");
            }
            // 获取模型
            Model modelData = repositoryService.getModel(modelId);
            byte[] bytes = repositoryService.getModelEditorSource(modelData.getId());
            if(null == bytes) {
                throw new BizException("模型数据为空，请先设计流程并成功保存，再进行发布。");
            }
            JsonNode modelNode = objectMapper.readTree(bytes);
            BpmnModel model = new BpmnJsonConverter().convertToBpmnModel(modelNode);
            if (model.getProcesses().size() == 0){
                throw new BizException("数据模型不符合要求，请至少设计一条主线程流。");
            }
            byte[] bpmnBytes = new BpmnXMLConverter().convertToXML(model);

            //发布流程
            String processName = modelData.getName() + ".bpmn20.xml";
            Deployment deployment = repositoryService
                    .createDeployment()
                    .key(modelData.getId())
                    .name(modelData.getName())
                    .addString(processName, new String(bpmnBytes, StandardCharsets.UTF_8))
                    .deploy();
            modelData.setDeploymentId(deployment.getId());
            repositoryService.saveModel(modelData);

			return ResponseResult.success("请求成功", deployment.getId()).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
			return ResponseResult.error(ex.getMessage()).response();
		}
	}

    @ApiOperation(
            value = "根据模型ID查询model的bpmn内容，一般用于展示流程图",
            notes = "据模型ID查询act_re_model表, 根据EDITOR_SOURCE_VALEU_ID_查询act_ge_bytearray表, 取BYTES_处理成String"
    )
    @ApiImplicitParams({ @ApiImplicitParam(
            name = "id",
            value = "模型id",
            dataType = "String",
            paramType = "query",
            example = "388d3160-3965-11ec-a578-3c970ef14df2",
            required = true
    )})
    @PostMapping("/search")
    public ResponseEntity<?> getModel(@RequestBody ModelQueryParam modelQueryParam) {
        try {
            String currentNodeKey="";

            byte[] bpmnBytes = null;
            if(Objects.nonNull(modelQueryParam.getId()))
                bpmnBytes = repositoryService.getModelEditorSource(modelQueryParam.getId());
            else if(Objects.nonNull(modelQueryParam.getInstanceId())) {
                ProcessInstance instance = runtimeService.createProcessInstanceQuery().processInstanceId(modelQueryParam.getInstanceId()).singleResult();
                if (Objects.nonNull(instance)) {
                    Model model = repositoryService.createModelQuery().deploymentId(instance.getDeploymentId()).singleResult();
                    bpmnBytes = repositoryService.getModelEditorSource(model.getId());

                    org.activiti.engine.task.Task currentTask = taskService.createTaskQuery().processInstanceId(modelQueryParam.getInstanceId()).singleResult();
                    if(Objects.nonNull(currentTask)){
                        currentNodeKey = currentTask.getTaskDefinitionKey();
                    }
                }else{
                    HistoricProcessInstance instanceHistory = historyService.createHistoricProcessInstanceQuery().processInstanceId(modelQueryParam.getInstanceId()).singleResult();
                    if (Objects.nonNull(instanceHistory)) {
                        LambdaQueryWrapper<BpmnByte> queryWrapper = new LambdaQueryWrapper<>();
                        queryWrapper.eq(BpmnByte::getDeploymentId, instanceHistory.getDeploymentId());
                        BpmnByte bpmnByte = bpmnByteMapper.selectOne(queryWrapper);
                        ModelQueryResult result = new ModelQueryResult();

                        result.setXml(new String(bpmnByte.getBytes()));
                        result.setNode(currentNodeKey);
                        return ResponseResult.success("请求成功", result).response();//History return directly
                    }
                }
            }

            if(null == bpmnBytes) {
                throw new BizException("未获取到模型数据");
            }
            JsonNode modelNode = objectMapper.readTree(bpmnBytes);
            BpmnModel bpmnModel = new BpmnJsonConverter().convertToBpmnModel(modelNode);

            //type
            ExtensionAttribute category = new ExtensionAttribute();
            category.setName("processCategory");
            category.setNamespace("http://flowable.org/bpmn");
            category.setNamespacePrefix("flowable");
            String type = modelNode.get("properties").get("processCategory").toString();
            category.setValue( type.replaceAll("^\"","").replaceAll("\"$","")  );

            Process mainProcess;
            if (bpmnModel.getPools().size() > 0) {
                mainProcess = bpmnModel.getProcess(bpmnModel.getPools().get(0).getId());
            } else {
                mainProcess = bpmnModel.getMainProcess();
            }
            mainProcess.addAttribute(category);

            byte[] bpmnXml = new BpmnXMLConverter().convertToXML(bpmnModel);

            String bpmnString = new String(bpmnXml);
            String bpmnText = bpmnString.replaceAll("\\r|\\n","");

            ModelQueryResult result = new ModelQueryResult();
            result.setXml(bpmnText);
            result.setNode(currentNodeKey);

			return ResponseResult.success("请求成功", result).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    @ApiOperation(
            value = "查询流程图各个节点信息",
            notes = "据模型ID查询act_re_model表, 根据EDITOR_SOURCE_VALEU_ID_查询act_ge_bytearray表, 取BYTES_处理成String"
    )
    @PostMapping("/nodes/search")
    public ResponseEntity<?> getModelUsers(@RequestBody ModelQueryParam modelQueryParam) {
        try {
            List<SysFlowExt> result = new ArrayList<>();
            if(Objects.nonNull(modelQueryParam.getId())){//task_id
                LambdaQueryWrapper<SysFlow> flowQuery = new QueryWrapper<SysFlow>().lambda();
                flowQuery.eq(SysFlow::getFlowId, modelQueryParam.getId());
                List<SysFlow> flows = sysFlowMapper.selectList(flowQuery);

                if(Objects.nonNull(flows) && !flows.isEmpty()) {
                    SysFlow flow = flows.get(0);
                    if(Objects.nonNull(flow)){
                        result = sysFlowExtMapper.findNodesByHID(flow.getId());
                    }
                }
            }else if(Objects.nonNull(modelQueryParam.getInstanceId())){//instance_id
                ProcessInstance instance = runtimeService.createProcessInstanceQuery().processInstanceId(modelQueryParam.getInstanceId()).singleResult();
                String deploymentId = "";
                if (Objects.nonNull(instance)) {
                    deploymentId = instance.getDeploymentId();
                }else{
                    HistoricProcessInstance instanceHistory = historyService.createHistoricProcessInstanceQuery().processInstanceId(modelQueryParam.getInstanceId()).singleResult();
                    if (Objects.nonNull(instanceHistory)) {
                        deploymentId = instanceHistory.getDeploymentId();
                    }
                }
                LambdaQueryWrapper<SysFlow> flowQuery = new QueryWrapper<SysFlow>().lambda();
                flowQuery.eq(SysFlow::getDeploymentId, deploymentId);
                SysFlow flow = sysFlowMapper.selectOne(flowQuery);

                if(Objects.nonNull(flow)){
                    result = sysFlowExtMapper.findNodesByHID(flow.getId());
                }
            }
			return ResponseResult.success("请求成功", result).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    @ApiOperation(
            value = "分页查询模型列表",
            notes = "起始参数为0而不是1"
    )
    @ApiImplicitParams({
            @ApiImplicitParam(
                    name = "firstResult",
                    value = "第一条数据的序号",
                    dataType = "String",
                    paramType = "query",
                    example = "0",
                    required = true
            ),
            @ApiImplicitParam(
                    name = "maxResults",
                    value = "查询的总条数",
                    dataType = "String",
                    paramType = "query",
                    example = "100",
                    required = true
            )
    })
    @GetMapping("/list/{firstResult}/{maxResults}")
	public ResponseEntity<?> listModel(@PathVariable int firstResult, @PathVariable int maxResults) {
		try {
            List<Model> models = repositoryService.createModelQuery().listPage(firstResult, maxResults);

			return ResponseResult.success("请求成功", models).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
			return ResponseResult.error(ex.getMessage()).response();
		}
	}

    @ApiOperation(
            value = "根据模型的分类分页查询模型列表",
            notes = "起始参数为0而不是1"
    )
    @ApiImplicitParams({
            @ApiImplicitParam(
                    name = "category",
                    value = "模型分类的代码",
                    dataType = "String",
                    paramType = "query",
                    example = "PECD",
                    required = true
            ),
            @ApiImplicitParam(
                    name = "firstResult",
                    value = "第一条数据的序号",
                    dataType = "String",
                    paramType = "query",
                    example = "0",
                    required = true
            ),
            @ApiImplicitParam(
                    name = "maxResults",
                    value = "查询的总条数",
                    dataType = "String",
                    paramType = "query",
                    example = "100",
                    required = true
            )
    })
    @GetMapping("/list")
	public ResponseEntity<?> listModel(@RequestParam @Nullable String category, @RequestParam @Nullable String name,
                                       @RequestParam @Nullable Long pageIndex, @RequestParam @Nullable Long pageSize) {
		try {
		    //model的id等于我们SysFlow的flow_id
            List<ProcessInstanceDTO> instanceDTOS = new ArrayList<>();
            ModelQuery modelQuery = repositoryService.createModelQuery();
            modelQuery.orderByCreateTime().desc();
            if(Objects.nonNull(category))
                modelQuery.modelCategory(category);
            if(Objects.nonNull(name))
                modelQuery.modelNameLike("%"+name+"%");

            long count = modelQuery.count();
            ModelPager page = new ModelPager(null, pageIndex, pageSize, count);
            List<Model> models = modelQuery.listPage(page.getFirst().intValue(), page.getPageSize().intValue());
            page.setData(models);

			return ResponseResult.success("请求成功", page).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
			return ResponseResult.error(ex.getMessage()).response();
		}
	}

    @ApiOperation(
        value = "查询流程实例列表",
        notes = "流程：process"
    )
    @ApiImplicitParams({
            @ApiImplicitParam(
                    name = "firstResult",
                    value = "第一条数据的序号, 默认为0",
                    dataType = "Integer",
                    paramType = "query",
                    example = "0"
            ),
            @ApiImplicitParam(
                    name = "maxResults",
                    value = "查询的总条数, 默认为100",
                    dataType = "Integer",
                    paramType = "query",
                    example = "100"
            )
    })
    @GetMapping("/process/list")
    public ResponseEntity<?> processList(@RequestParam @Nullable Integer firstResult, @RequestParam @Nullable Integer maxResults) {
        try {
            if(firstResult==null)
                firstResult = 0;
            if(maxResults==null)
                maxResults = 100;

            List<Deployment> deployments = repositoryService.createDeploymentQuery().listPage(firstResult, maxResults);
            List<DeploymentDTO> deploymentDTOs = Lists.newArrayList();
            deployments.forEach(d -> {
                DeploymentDTO dd = new DeploymentDTO(d.getId(), d.getName());
                deploymentDTOs.add(dd);
            });
			return ResponseResult.success("请求成功", deploymentDTOs).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
            ex.printStackTrace();
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    @ApiOperation(value = "根据部署ID查询流程的节点列表", notes = "结果集会过滤掉起始、连线等类型, 只返回任务节点和网关节点")
    @ApiImplicitParams({ @ApiImplicitParam(
            name = "deployment_id",
            value = "流程部署的ID",
            dataType = "Integer",
            paramType = "query",
            example = "389a29b3-3965-11ec-a578-3c970ef14df2",
            required = true
    ) })
    @ApiResponses({
            @ApiResponse(code=200500, message="业务处理失败"),
            @ApiResponse(code=500, message="系统错误")
    })
    @GetMapping("/process/{deploymentId}/nodes")
    public ResponseEntity<?> processListNodes(@PathVariable String deploymentId) {
        try {
            List<FlowElement> result = new LinkedList();
            String processDefinitionId = getProcessDefIdByDeploymentId(deploymentId);

            if(StringUtils.isNotEmpty(processDefinitionId)){
                BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
                if(Objects.nonNull(bpmnModel)){
                    Collection<FlowElement> flowElements = bpmnModel.getMainProcess().getFlowElements();
                    if(Objects.nonNull(flowElements)){
                       result = flowElements.stream()
                               .filter(flowElement -> flowElement instanceof Task || flowElement instanceof Gateway)
                               .collect(Collectors.toList());
                    }
                }
            }

			return ResponseResult.success("请求成功", result).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    @ApiOperation(value = "根据单号查询流程的最新节点列表", notes = "结果集会过滤掉起始、连线等类型, 只返回任务节点")
    @GetMapping("/process/{data_id}/latestNodes")
    public ResponseEntity<?> processLatestNodes(@PathVariable String data_id) {
        try {
            List<SysTaskExtResult> result = new ArrayList();
            List<SysTaskExt> tasks = new ArrayList<>();

            List<SysTask> tasksHeader = sysTaskMapper.selectBySId(data_id);
            Set<String> instanceIds = tasksHeader.stream().map(SysTask::getTaskId).collect(Collectors.toSet());
            if(Objects.nonNull(instanceIds) && !instanceIds.isEmpty()){
                String instanceId = getLatestInstance(instanceIds);

                LambdaQueryWrapper<SysTask> q = new QueryWrapper<SysTask>().lambda();
                q.eq(SysTask::getTaskId, instanceId);
                q.eq(SysTask::getSId, data_id);
                SysTask sysTask = sysTaskMapper.selectOne(q);

                tasks = sysTaskExtMapper.selectLatestNodes( sysTask.getId());
                tasks.stream().forEach(t->{
                    HistoricTaskInstance nodeInfo = historyService.createHistoricTaskInstanceQuery()
                            .processInstanceId(instanceId)
                            .taskDefinitionKey(t.getNode())
                            .singleResult();

                    String nodeName = null;
                    if(Objects.nonNull(nodeInfo))
                        nodeName = nodeInfo.getName();

                    SysTaskExtResult tr = SysTaskExtResult.newInstance(t, nodeName);
                    result.add(tr);
                });
            }

            Collections.sort(result);

			return ResponseResult.success("请求成功", result).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    /**
     * get Latest Instance
     *
     * @param instanceIds 
     * @return java.lang.String
     * @author apr
     * @date 2021/11/18 10:54
     */
    private String getLatestInstance(Set<String> instanceIds) {
        String instanceId = null;

        Date tmp = null;
        for (String instId : instanceIds) {
            HistoricProcessInstance inst = historyService.createHistoricProcessInstanceQuery().processInstanceId(instId).singleResult();
            Date d = inst.getStartTime();
            if(Objects.isNull(tmp) || d.after(tmp)){
                instanceId = instId;
                tmp = d;
            }
        }

        return instanceId;
    }

    @GetMapping("process/active/{instance_id}")
    @ApiOperation(value = "激活或挂起流程", notes = "激活或挂起流程")
    public ResponseEntity<?> Suspended(@PathVariable String instance_id){
        try {
            ProcessInstance inst = runtimeService.createProcessInstanceQuery().processInstanceId(instance_id).singleResult();
            ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery().processDefinitionId(inst.getProcessDefinitionId()).singleResult();
            boolean active = !processDefinition.isSuspended();

            List<SysTask> tasks = sysTaskMapper.selectByTaskId(instance_id);
            if(tasks==null || tasks.isEmpty()) {
                return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,"未获取到流程", null).response();
            }

            SysTask task = tasks.get(0);
            if(active){
                repositoryService.suspendProcessDefinitionById(inst.getProcessDefinitionId());
                task.setStatus(ProcessStatus.SUSPENDED.getCode());
            }else{
                repositoryService.activateProcessDefinitionById(inst.getProcessDefinitionId(),true,new Date());
                task.setStatus(ProcessStatus.ACTIVE.getCode());
            }
            sysTaskMapper.updateById(task);

            return ResponseResult.success("请求成功", active).response();
        } catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    @ApiOperation(value = "关联流程和业务节点", notes = "保存节点主数据s_sys_flow_h和节点记录s_sys_flow_b")
    @ApiImplicitParams({
            @ApiImplicitParam(
                    name = "flow",
                    value = "流程信息(主)",
                    dataType = "String",
                    paramType = "query",
                    example = " \"flow\" : { sysModel : \"123\" }",
                    required = true
            ),
            @ApiImplicitParam(
                    name = "nodes",
                    value = "节点数据(从)",
                    dataType = "String",
                    paramType = "query",
                    example = " \"nodes\" : [{ nodeType : \"123\" }, { nodeType : \"123\" } ]",
                    required = true
            )
    })
    @PostMapping("/apply")
    public ResponseEntity<?> applyModel(@RequestBody SysInfo sysInfo) {
        try {

            SysFlow flow = sysInfo.getFlow();

            Model model = repositoryService.createModelQuery().deploymentId(flow.getDeploymentId()).singleResult();
            flow.setVersion(model.getVersion());

            List<SysFlowExt> nodes = sysInfo.getNodes();

            if(!"-1".equals(flow.getId())){
//                SysFlow flowDb = sysFlowMapper.selectById(flow.getId());
//                flow.setId(flowDb.getId());
//                flow.setSysDisable(flowDb.getSysDisable());
                sysFlowMapper.updateById(flow);
                List<SysFlowExt> oldNodes = sysFlowExtMapper.findNodesByHID(flow.getId());
                if(Objects.nonNull(oldNodes) && !oldNodes.isEmpty()){
                    List<String> nodeIds = oldNodes.stream().map(SysFlowExt::getId).collect(Collectors.toList());
                    sysFlowExtMapper.deleteBatchIds(nodeIds);
                }

                nodes.forEach(node -> {
                    String nodeId = UUID.randomUUID().toString();
                    node.setId(nodeId);
                    node.setHId(flow.getId());
                    sysFlowExtMapper.insert(node);
                });
            }else{
                String flowId = UUID.randomUUID().toString();
                flow.setId(flowId);
                flow.setSysDisable(true);
                sysFlowMapper.insert(flow);

                nodes.forEach(node -> {
                    String nodeId = UUID.randomUUID().toString();
                    node.setId(nodeId);
                    node.setHId(flowId);
                    sysFlowExtMapper.insert(node);
                });
            }

            return ResponseResult.success("请求成功", flow.getId()).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
            ex.printStackTrace();
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    @ApiOperation(value = "查询业务任务列表",notes = "查询s_sys_flow_h任务列表")
    @GetMapping("/flow/list")
    public ResponseEntity<?> flowList() {
        try{
            LambdaQueryWrapper<SysFlow> queryWrapper = new LambdaQueryWrapper<>();
            List<SysFlow> list = sysFlowMapper.selectList(queryWrapper);
            return ResponseResult.success("请求成功", list).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
            ex.printStackTrace();
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    @ApiOperation(value = "激活业务流程", notes = "同意类别代码下同一时间只能有一个流程被激活, 当其被激活是同一类别的其他流程会被禁用")
    @ApiImplicitParams({ @ApiImplicitParam(
            name = "flow_id",
            value = "流程id",
            dataType = "String",
            paramType = "query",
            example = "388d3160-3965-11ec-a578-3c970ef14df2"
    ) })
    @GetMapping("/enable/{deploymentId_id}")
    public ResponseEntity<?> enableFlow(@PathVariable String deploymentId_id) {
        try{
            if(!nodesMatch(deploymentId_id)) {
                return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,"节点配置信息缺失，不允许激活", null).response();
            }

            LambdaQueryWrapper<SysFlow> lambdaQuery = new QueryWrapper<SysFlow>().lambda();
            lambdaQuery.eq(SysFlow::getDeploymentId, deploymentId_id);
            SysFlow flow = sysFlowMapper.selectOne(lambdaQuery);

            if(Objects.nonNull(flow)){
                //执行激活前置条件判断
                LambdaQueryWrapper<SysFlow> lambdaQuery1 = new QueryWrapper<SysFlow>().lambda();
                lambdaQuery1.eq(SysFlow::getSysModel, flow.getSysModel());
                List<SysFlow> flowListSameType = sysFlowMapper.selectList(lambdaQuery1);
                Optional.ofNullable(flowListSameType).orElse(new ArrayList<>()).forEach(sameTypeFlow -> {
                    Boolean disable = !sameTypeFlow.getId().equals(flow.getId());
                    sameTypeFlow.setSysDisable(disable);
                    sysFlowMapper.updateById(sameTypeFlow);
                });
            }else{
                return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,"未查询到实例", deploymentId_id).response();
            }

            return ResponseResult.success("请求成功", null).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), deploymentId_id).response();
		} catch (Exception ex) {
            ex.printStackTrace();
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    @ApiOperation(value = "根据id, sysModel, sysTable, flowId查询业务流程列表",notes = "根据流程id, sysModel, sysTable, flowId查询流程")
    @ApiImplicitParams({
            @ApiImplicitParam(
                    name = "id",
                    value = "任务id",
                    dataType = "String",
                    paramType = "query",
                    example = "63c3b852-f6ef-4ce8-a7a1-1a7f2c0838c6"
            ),
            @ApiImplicitParam(
                    name = "sysModel",
                    value = "系统模块分类",
                    dataType = "String",
                    paramType = "query",
                    example = "PECD"
            ),
            @ApiImplicitParam(
                    name = "sysDisable",
                    value = "激活/禁用",
                    dataType = "String",
                    paramType = "query",
                    example = "0"
            ),
            @ApiImplicitParam(
                    name = "流程id",
                    value = "流程id",
                    dataType = "String",
                    paramType = "query",
                    example = "388d3160-3965-11ec-a578-3c970ef14df2"
            )
    })
    @PostMapping("/flow/search")
    public ResponseEntity<?> flowById(@RequestBody SysFlow sysFlow) {
        try{
            LambdaQueryWrapper<SysFlow> lambdaQuery = new QueryWrapper<SysFlow>().lambda();
            if(Objects.nonNull(sysFlow)){
                if(Objects.nonNull(sysFlow.getId()))
                    lambdaQuery.eq(SysFlow::getId, sysFlow.getId());
                if(Objects.nonNull(sysFlow.getSysModel()))
                    lambdaQuery.eq(SysFlow::getSysModel, sysFlow.getSysModel());
                if(Objects.nonNull(sysFlow.getSysDisable()))
                    lambdaQuery.eq(SysFlow::getSysDisable, sysFlow.getSysDisable());
                if(Objects.nonNull(sysFlow.getFlowId()))
                    lambdaQuery.eq(SysFlow::getFlowId, sysFlow.getFlowId());
                if(Objects.nonNull(sysFlow.getDeploymentId()))
                    lambdaQuery.eq(SysFlow::getDeploymentId, sysFlow.getDeploymentId());
            }
            List<SysFlow> list = sysFlowMapper.selectList(lambdaQuery);

            return ResponseResult.success("请求成功", list).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
            ex.printStackTrace();
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    @ApiOperation(value = "根据流程实例id查询业务流程",notes = "会关联查询出其下的节点数据")
    @ApiImplicitParams({ @ApiImplicitParam(
            name = "flow_id",
            value = "流程id",
            dataType = "String",
            paramType = "query",
            example = "388d3160-3965-11ec-a578-3c970ef14df2"
    ) })
    @GetMapping("/flow/inst/{inst_id}")
    public ResponseEntity<?> flowByInstanceId(@PathVariable String inst_id) {
        try{
            String deploymentId = getDeploymentIdByInstId(inst_id);

            return ResponseResult.success("请求成功", flowById(deploymentId)).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
            ex.printStackTrace();
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    @ApiOperation(value = "根据流程部署id查询业务流程",notes = "会关联查询出其下的节点数据")
    @ApiImplicitParams({ @ApiImplicitParam(
            name = "flow_id",
            value = "流程id",
            dataType = "String",
            paramType = "query",
            example = "388d3160-3965-11ec-a578-3c970ef14df2"
    ) })
    @GetMapping("/flow/{flow_id}")
    public ResponseEntity<?> flowById(@PathVariable String flow_id) {
        try{
            Deployment deployment = repositoryService.createDeploymentQuery().deploymentId(flow_id).singleResult();
            Model model = repositoryService.createModelQuery().modelId(deployment.getKey()).singleResult();
            SysModel sysModel = getLatestNodes(model.getId());

            return ResponseResult.success("请求成功", sysModel).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
            ex.printStackTrace();
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    @ApiOperation(value = "根据流程id查询所有节点",notes = "根据流程id查询所有节点")
    @ApiImplicitParams({ @ApiImplicitParam(
            name = "flow_id",
            value = "流程id",
            dataType = "String",
            paramType = "query",
            example = "388d3160-3965-11ec-a578-3c970ef14df2"
    ) })
    @GetMapping("/nodes/flow/{flow_id}")
    public ResponseEntity<?> nodesByFlow(@PathVariable String flow_id) {
        try{
            List<SysFlowExt> list = sysFlowExtMapper.findNodesByHID(flow_id);
            return ResponseResult.success("请求成功", list).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
            ex.printStackTrace();
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    @ApiOperation(value = "查询当前所有正在的流程", notes = "查询当前所有正在进行的流程")
    @GetMapping("/process/instanceList")
    public ResponseEntity<?> getInstanceList(@RequestParam @Nullable String billNo, @RequestParam @Nullable String instId,
                                             @RequestParam @Nullable String category,@RequestParam @Nullable Long pageIndex,
                                             @RequestParam @Nullable Long pageSize) {//"032bf875-99b0-4c85-91c0-e128fc759565"
        ProcessInstancePager<ProcessInstanceDTO> p;
        try{
            HistoricProcessInstanceQuery instanceHistoryListQuery = historyService.createHistoricProcessInstanceQuery();
            instanceHistoryListQuery.orderByProcessInstanceStartTime().desc();
            if(Objects.nonNull(instId))
                instanceHistoryListQuery.processInstanceId(instId);
            if(Objects.nonNull(category))
                instanceHistoryListQuery.processInstanceBusinessKey(category);
            if(Objects.nonNull(billNo))
                instanceHistoryListQuery.processInstanceName(billNo);

            p = new ProcessInstancePager(null, pageIndex, pageSize, instanceHistoryListQuery.count());
            List<HistoricProcessInstance> instanceHistoryList = instanceHistoryListQuery.listPage(p.getFirst().intValue(), p.getPageSize().intValue());

            p.setData(fillInstanceDTOByInstance(instanceHistoryList));

            return ResponseResult.success("请求成功", p).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
            ex.printStackTrace();
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    @ApiOperation(value = "查询当前用户已完成的实例", notes = "查询当前用户已完成的实例")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "userId", value = "用户id", dataType = "String", paramType = "query", example = ""),
            @ApiImplicitParam(name = "pageIndex", value = "页码", dataType = "String", paramType = "query", example = ""),
            @ApiImplicitParam(name = "pageSize", value = "页容量", dataType = "String", paramType = "query", example = "")
    })
    @GetMapping("/process/instanceList/user")
    public ResponseEntity<?> getTaskFinishedByUser(@RequestParam String userId) {
        try{
            List<ProcessInstanceDTO> instanceDTOS = null;

            LambdaQueryWrapper<SysTaskExt> condition = new LambdaQueryWrapper();
            condition.groupBy(SysTaskExt::getHId).select(SysTaskExt::getHId);
            condition.eq(SysTaskExt::getUser, userId);
            List<SysTaskExt> finishedTasks = sysTaskExtMapper.selectList(condition);

            if(Objects.nonNull(finishedTasks) && !finishedTasks.isEmpty()){
                List<String> hIds = finishedTasks.stream().map(SysTaskExt::getHId).collect(Collectors.toList());
                LambdaQueryWrapper<SysTask> lambdaQuery2 = new QueryWrapper<SysTask>().lambda();
                lambdaQuery2.in(SysTask::getId, hIds);
                List<SysTask> tasksHeadList = sysTaskMapper.selectList(lambdaQuery2);
                Set<String> instanceIds = tasksHeadList.stream().map(SysTask::getTaskId).collect(Collectors.toSet());

                Calendar now = Calendar.getInstance();
                now.add(Calendar.DAY_OF_MONTH, -30);
                List<HistoricProcessInstance> instances = historyService.createHistoricProcessInstanceQuery()
                        .processInstanceIds(instanceIds)
                        .startedAfter(now.getTime())
                        .orderByProcessInstanceStartTime().desc()
                        .list();

                instanceDTOS = fillInstanceDTOByInstance(instances);
            }

            return ResponseResult.success("请求成功", instanceDTOS).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
            ex.printStackTrace();
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    /**
     * 组织数据
     *
     * @param instances
     * @return void
     * @author apr
     * @date 2021/11/17 10:37
     */
    private List<ProcessInstanceDTO> fillInstanceDTOByInstance(List<HistoricProcessInstance> instances) {
        List<ProcessInstanceDTO> instanceDTOS = new ArrayList<>();

        if(instances ==null)
            return instanceDTOS;

        instances.stream().forEach(processInstance -> {
            ProcessInstanceDTO pi = new ProcessInstanceDTO();
            pi.setInstanceId(processInstance.getId());
            pi.setCreateTime(processInstance.getStartTime().getTime()+"");
            pi.setEndTime(processInstance.getEndTime() == null ? "无" : processInstance.getEndTime().getTime()+"");
            pi.setStatus("完成");

            String creator = getVariableByInstanceId("creator", processInstance.getId());
            pi.setCreator(creator);
            String creatorName = getVariableByInstanceId("creatorName", processInstance.getId());
            pi.setCreatorName(creatorName);

            LambdaQueryWrapper<SysTask> taskCondition = new LambdaQueryWrapper<>();
            taskCondition.eq(SysTask::getTaskId, processInstance.getId());
            SysTask sysTask = sysTaskMapper.selectOne(taskCondition);
            if(Objects.nonNull(sysTask)){
                pi.setHeader(sysTask);
                pi.setStatus(ProcessStatus.CODE_TO_MSG(sysTask.getStatus()));
            }

            org.activiti.engine.task.Task actTask = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            if(Objects.nonNull(actTask)){
                pi.setCurrentNodeName(actTask.getName());
                pi.setCurrentNodeId(actTask.getTaskDefinitionKey());
                pi.setStatus(actTask.isSuspended() ? "挂起" : "运行");

                LambdaQueryWrapper<SysFlow> flowCondition = new LambdaQueryWrapper<>();
                flowCondition.eq(SysFlow::getDeploymentId, processInstance.getDeploymentId());
                SysFlow sysFlow = sysFlowMapper.selectOne(flowCondition);
                if(Objects.nonNull(sysFlow)){
                    LambdaQueryWrapper<SysFlowExt> flowExtCondition = new LambdaQueryWrapper<>();
                    flowExtCondition.eq(SysFlowExt::getNode, actTask.getTaskDefinitionKey());
                    flowExtCondition.eq(SysFlowExt::getHId, sysFlow.getId());
                    SysFlowExt sysFlowExt = sysFlowExtMapper.selectOne(flowExtCondition);
                    if(sysFlowExt!=null){
                        pi.setCurrentAssign(sysFlowExt.getOperation());
                        pi.setCurrentAssignName(sysFlowExt.getOperName());
                    }
                }
            }

            instanceDTOS.add(pi);
        });

        return instanceDTOS;
    }

    /**
     * 根据流程实例id获取流程定义id
     *
     * @param processInstanceId
     * @return java.lang.String
     * @author apr
     * @date 2021/10/27 14:43
     */
    private String getDeploymentIdByInstId(String processInstanceId){
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).singleResult();
        return Objects.isNull(processInstance) ? null : processInstance.getDeploymentId();
    }

    /**
     * 根据部署id获取流程定义id
     *
     * @param deploymentId
     * @return java.lang.String
     * @author apr
     * @date 2021/10/27 14:43
     */
    private String getProcessDefIdByDeploymentId(String deploymentId){
           List<ProcessDefinition> list = processEngine.getRepositoryService()//与流程定义和部署对象相关的Service
                    .createProcessDefinitionQuery()
                    .deploymentId(deploymentId)
                    .list();

            if(list==null || list.isEmpty() || list.get(0)==null)
                return null;

            return list.get(0).getId();
    }

    /**
     * getLatestNodes
     *
     * @param modelId
     * @return java.util.List<com.yuepong.workflow.dto.SysFlowExt>
     * @author apr
     * @date 2021/11/12 15:36
     */
    private SysModel getLatestNodes(String modelId) {
       LambdaQueryWrapper<SysFlow> lambdaQuery = new QueryWrapper<SysFlow>().lambda();
       lambdaQuery.eq(SysFlow::getFlowId, modelId);
       lambdaQuery.orderByDesc(SysFlow::getVersion);
       List<SysFlow> flows = sysFlowMapper.selectList(lambdaQuery);
       if(Objects.nonNull(flows)){
           for (int i = 0; i < flows.size(); i++) {
               SysFlow flow = flows.get(i);
               List<SysFlowExt> nodes = sysFlowExtMapper.findNodesByHID(flow.getId());
               if(Objects.nonNull(nodes) && !nodes.isEmpty()){
                    SysModel model = new SysModel();
                    model.setFlow(flow);
                    model.setNodes(nodes);
                    return model;
               }
           }
       }

       return null;
    }

    /**
     * 比对节点匹配
     *
     * @param instance_id
     * @return boolean
     * @author apr
     * @date 2021/11/12 16:13
     */
    private boolean nodesMatch(String instance_id) {
        LambdaQueryWrapper<SysFlow> lambdaQuery = new QueryWrapper<SysFlow>().lambda();
        lambdaQuery.eq(SysFlow::getDeploymentId, instance_id);
        SysFlow sysFlow = sysFlowMapper.selectOne(lambdaQuery);
        return Objects.nonNull(sysFlow);
//        List<SysFlowExt> nodes = sysFlowExtMapper.findNodesByHID(sysFlow.getId());
//
//
//        List<FlowElement> result = new LinkedList();
//        String processDefinitionId = getProcessDefIdByDeploymentId(instance_id);
//
//        if(StringUtils.isNotEmpty(processDefinitionId)){
//            BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
//            if(Objects.nonNull(bpmnModel)){
//                Collection<FlowElement> flowElements = bpmnModel.getMainProcess().getFlowElements();
//                if(Objects.nonNull(flowElements)){
//                   result = flowElements.stream()
//                           .filter(flowElement -> flowElement instanceof Task || flowElement instanceof Gateway)
//                           .collect(Collectors.toList());
//                }
//            }
//        }
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
}
