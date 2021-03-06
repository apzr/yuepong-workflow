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
import org.activiti.bpmn.model.Process;
import org.activiti.bpmn.model.*;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * @author Apr
 */
@Transactional
@Controller
@Api(tags="???????????????")
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

    @ApiOperation(value = "??????(modelId=-1)?????????(modelId!=-1)??????", notes = "???????????????xml???????????????????????????????????????????????????????????????")
    @ApiImplicitParams({
            @ApiImplicitParam(
                    name = "modelId",
                    value = "??????id",
                    dataType = "String",
                    paramType = "query",
                    example = "388d3160-3965-11ec-a578-3c970ef14df2",
                    required = true
            ),
            @ApiImplicitParam(
                    name = "bpmn_xml",
                    value = "BPMN???????????????",
                    dataType = "String",
                    paramType = "query",
                    example = "<bpmn...>...</...bpmn>",
                    required = true
            ),
            @ApiImplicitParam(
                    name = "svg_xml",
                    value = "SVG???????????????",
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
                    return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR, "???????????????????????????????????????, ????????????", modelId).response();
            }
            Utils.initModel(model, jsonBpmnXml, objectMapper);

            //db
            repositoryService.saveModel(model);
            repositoryService.addModelEditorSource(model.getId(), jsonBpmnXml.getBytes(StandardCharsets.UTF_8));
            repositoryService.addModelEditorSourceExtra(model.getId(), svg.getBytes(StandardCharsets.UTF_8));

			return ResponseResult.success("????????????", model.getId()).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), modelId).response();
		} catch (Exception ex) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,ex.getMessage(), null).response();
		}
    }

    /**
     * ??????????????????????????????????????????
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

    @ApiOperation(value = "????????????ID????????????(??????)",notes = "?????????Model, ?????????act_re_model")
    @ApiImplicitParams({ @ApiImplicitParam(
            name = "modelId",
            value = "??????id",
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
                //throw new BizException("?????????????????????????????????");
            }
            // ????????????
            Model modelData = repositoryService.getModel(modelId);
            byte[] bytes = repositoryService.getModelEditorSource(modelData.getId());
            if(null == bytes) {
                throw new BizException("???????????????????????????????????????????????????????????????????????????");
            }
            JsonNode modelNode = objectMapper.readTree(bytes);
            BpmnModel model = new BpmnJsonConverter().convertToBpmnModel(modelNode);
            if (model.getProcesses().size() == 0){
                throw new BizException("??????????????????????????????????????????????????????????????????");
            }
            byte[] bpmnBytes = new BpmnXMLConverter().convertToXML(model);

            //????????????
            String processName = modelData.getName() + ".bpmn20.xml";
            Deployment deployment = repositoryService
                    .createDeployment()
                    .key(modelData.getId())
                    .name(modelData.getName())
                    .addString(processName, new String(bpmnBytes, StandardCharsets.UTF_8))
                    .deploy();
            modelData.setDeploymentId(deployment.getId());
            repositoryService.saveModel(modelData);

			return ResponseResult.success("????????????", deployment.getId()).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
			return ResponseResult.error(ex.getMessage()).response();
		}
	}

    @ApiOperation(
            value = "????????????ID??????model???bpmn????????????????????????????????????",
            notes = "?????????ID??????act_re_model???, ??????EDITOR_SOURCE_VALEU_ID_??????act_ge_bytearray???, ???BYTES_?????????String"
    )
    @ApiImplicitParams({ @ApiImplicitParam(
            name = "id",
            value = "??????id",
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
                        return ResponseResult.success("????????????", result).response();//History return directly
                    }
                }
            }

            if(null == bpmnBytes) {
                throw new BizException("????????????????????????");
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

			return ResponseResult.success("????????????", result).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    @ApiOperation(
            value = "?????????????????????????????????",
            notes = "?????????ID??????act_re_model???, ??????EDITOR_SOURCE_VALEU_ID_??????act_ge_bytearray???, ???BYTES_?????????String"
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
			return ResponseResult.success("????????????", result).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    @ApiOperation(
            value = "????????????????????????",
            notes = "???????????????0?????????1"
    )
    @ApiImplicitParams({
            @ApiImplicitParam(
                    name = "firstResult",
                    value = "????????????????????????",
                    dataType = "String",
                    paramType = "query",
                    example = "0",
                    required = true
            ),
            @ApiImplicitParam(
                    name = "maxResults",
                    value = "??????????????????",
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

			return ResponseResult.success("????????????", models).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
			return ResponseResult.error(ex.getMessage()).response();
		}
	}

    @ApiOperation(
            value = "?????????????????????????????????????????????",
            notes = "???????????????0?????????1"
    )
    @ApiImplicitParams({
            @ApiImplicitParam(
                    name = "category",
                    value = "?????????????????????",
                    dataType = "String",
                    paramType = "query",
                    example = "PECD",
                    required = true
            ),
            @ApiImplicitParam(
                    name = "firstResult",
                    value = "????????????????????????",
                    dataType = "String",
                    paramType = "query",
                    example = "0",
                    required = true
            ),
            @ApiImplicitParam(
                    name = "maxResults",
                    value = "??????????????????",
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
		    //model???id????????????SysFlow???flow_id
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

			return ResponseResult.success("????????????", page).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
			return ResponseResult.error(ex.getMessage()).response();
		}
	}

    @ApiOperation(
        value = "????????????????????????",
        notes = "?????????process"
    )
    @ApiImplicitParams({
            @ApiImplicitParam(
                    name = "firstResult",
                    value = "????????????????????????, ?????????0",
                    dataType = "Integer",
                    paramType = "query",
                    example = "0"
            ),
            @ApiImplicitParam(
                    name = "maxResults",
                    value = "??????????????????, ?????????100",
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
			return ResponseResult.success("????????????", deploymentDTOs).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
            ex.printStackTrace();
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    @ApiOperation(value = "????????????ID???????????????????????????", notes = "?????????????????????????????????????????????, ????????????????????????????????????")
    @ApiImplicitParams({ @ApiImplicitParam(
            name = "deployment_id",
            value = "???????????????ID",
            dataType = "Integer",
            paramType = "query",
            example = "389a29b3-3965-11ec-a578-3c970ef14df2",
            required = true
    ) })
    @ApiResponses({
            @ApiResponse(code=200500, message="??????????????????"),
            @ApiResponse(code=500, message="????????????")
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

			return ResponseResult.success("????????????", result).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    @ApiOperation(value = "?????????????????????????????????????????????", notes = "?????????????????????????????????????????????, ?????????????????????")
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

			return ResponseResult.success("????????????", result).response();
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
    @ApiOperation(value = "?????????????????????", notes = "?????????????????????")
    public ResponseEntity<?> Suspended(@PathVariable String instance_id){
        try {
            ProcessInstance inst = runtimeService.createProcessInstanceQuery().processInstanceId(instance_id).singleResult();
            ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery().processDefinitionId(inst.getProcessDefinitionId()).singleResult();
            boolean active = !processDefinition.isSuspended();

            List<SysTask> tasks = sysTaskMapper.selectByTaskId(instance_id);
            if(tasks==null || tasks.isEmpty()) {
                return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,"??????????????????", null).response();
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

            return ResponseResult.success("????????????", active).response();
        } catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    @ApiOperation(value = "???????????????????????????", notes = "?????????????????????s_sys_flow_h???????????????s_sys_flow_b")
    @ApiImplicitParams({
            @ApiImplicitParam(
                    name = "flow",
                    value = "????????????(???)",
                    dataType = "String",
                    paramType = "query",
                    example = " \"flow\" : { sysModel : \"123\" }",
                    required = true
            ),
            @ApiImplicitParam(
                    name = "nodes",
                    value = "????????????(???)",
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

            return ResponseResult.success("????????????", flow.getId()).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
            ex.printStackTrace();
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    @ApiOperation(value = "????????????????????????",notes = "??????s_sys_flow_h????????????")
    @GetMapping("/flow/list")
    public ResponseEntity<?> flowList() {
        try{
            LambdaQueryWrapper<SysFlow> queryWrapper = new LambdaQueryWrapper<>();
            List<SysFlow> list = sysFlowMapper.selectList(queryWrapper);
            return ResponseResult.success("????????????", list).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
            ex.printStackTrace();
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    @ApiOperation(value = "??????????????????", notes = "???????????????????????????????????????????????????????????????, ?????????????????????????????????????????????????????????")
    @ApiImplicitParams({ @ApiImplicitParam(
            name = "flow_id",
            value = "??????id",
            dataType = "String",
            paramType = "query",
            example = "388d3160-3965-11ec-a578-3c970ef14df2"
    ) })
    @GetMapping("/enable/{deploymentId_id}")
    public ResponseEntity<?> enableFlow(@PathVariable String deploymentId_id) {
        try{
            LambdaQueryWrapper<SysFlow> lambdaQuery = new QueryWrapper<SysFlow>().lambda();
            lambdaQuery.eq(SysFlow::getDeploymentId, deploymentId_id);
            SysFlow flow = sysFlowMapper.selectOne(lambdaQuery);

            if(Objects.nonNull(flow)){
                SysFlowExt node = checkNodes(flow.getId());
                if(Objects.nonNull(node))
                    return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,"?????????????????????????????????????????????", node).response();

                //??????????????????????????????
                LambdaQueryWrapper<SysFlow> lambdaQuery1 = new QueryWrapper<SysFlow>().lambda();
                lambdaQuery1.eq(SysFlow::getSysModel, flow.getSysModel());
                List<SysFlow> flowListSameType = sysFlowMapper.selectList(lambdaQuery1);
                Optional.ofNullable(flowListSameType).orElse(new ArrayList<>()).forEach(sameTypeFlow -> {
                    Boolean disable = !sameTypeFlow.getId().equals(flow.getId());
                    sameTypeFlow.setSysDisable(disable);
                    sysFlowMapper.updateById(sameTypeFlow);
                });
            }else{
                return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,"???????????????????????????????????????", null).response();
            }

            return ResponseResult.success("????????????", flow).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), deploymentId_id).response();
		} catch (Exception ex) {
            ex.printStackTrace();
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    /*
     * ??????????????????????????????????????????3???????????? ?????????2?????????
     *
     * @param id 
     * @return void
     * @author apr
     * @date 2021/11/26 14:49
     */
    private SysFlowExt checkNodes(String hid) {
        AtomicReference<SysFlowExt> n = new AtomicReference();;
        List<SysFlowExt> nodes = sysFlowExtMapper.findNodesByHID(hid);
        if(Objects.nonNull(nodes)){
            nodes.forEach(node -> {
                if("??????".equals(node.getNodeType())){
                    if(StringUtils.isEmpty(node.getField()) || StringUtils.isEmpty(node.getConditions()) || StringUtils.isEmpty(node.getValue())){
                       n.set(node);
                    }
                }else if("??????".equals(node.getNodeType())){
                    if(StringUtils.isEmpty(node.getUserType()) || StringUtils.isEmpty(node.getOperation()) ){
                       n.set(node);
                    }
                }
            });
        }
        return n.get();
    }

    @ApiOperation(value = "??????????????????", notes = "??????????????????")
    @ApiImplicitParams({ @ApiImplicitParam(
            name = "deploymentId_id",
            value = "??????id",
            dataType = "String",
            paramType = "query",
            example = "388d3160-3965-11ec-a578-3c970ef14df2"
    ) })
    @GetMapping("/disable/{deploymentId_id}")
    public ResponseEntity<?> disableFlow(@PathVariable String deploymentId_id) {
        try{
            LambdaQueryWrapper<SysFlow> lambdaQuery = new QueryWrapper<SysFlow>().lambda();
            lambdaQuery.eq(SysFlow::getDeploymentId, deploymentId_id);
            SysFlow flow = sysFlowMapper.selectOne(lambdaQuery);

            if(Objects.nonNull(flow)){
                flow.setSysDisable(true);
                sysFlowMapper.updateById(flow);
            }else{
                return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,"???????????????????????????????????????", null).response();
            }

            return ResponseResult.success("????????????", flow).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), deploymentId_id).response();
		} catch (Exception ex) {
            ex.printStackTrace();
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    @ApiOperation(value = "??????id, sysModel, sysTable, flowId????????????????????????",notes = "????????????id, sysModel, sysTable, flowId????????????")
    @ApiImplicitParams({
            @ApiImplicitParam(
                    name = "id",
                    value = "??????id",
                    dataType = "String",
                    paramType = "query",
                    example = "63c3b852-f6ef-4ce8-a7a1-1a7f2c0838c6"
            ),
            @ApiImplicitParam(
                    name = "sysModel",
                    value = "??????????????????",
                    dataType = "String",
                    paramType = "query",
                    example = "PECD"
            ),
            @ApiImplicitParam(
                    name = "sysDisable",
                    value = "??????/??????",
                    dataType = "String",
                    paramType = "query",
                    example = "0"
            ),
            @ApiImplicitParam(
                    name = "??????id",
                    value = "??????id",
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

            return ResponseResult.success("????????????", list).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
            ex.printStackTrace();
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    @ApiOperation(value = "??????????????????id??????????????????",notes = "???????????????????????????????????????")
    @ApiImplicitParams({ @ApiImplicitParam(
            name = "flow_id",
            value = "??????id",
            dataType = "String",
            paramType = "query",
            example = "388d3160-3965-11ec-a578-3c970ef14df2"
    ) })
    @GetMapping("/flow/inst/{inst_id}")
    public ResponseEntity<?> flowByInstanceId(@PathVariable String inst_id) {
        try{
            String deploymentId = getDeploymentIdByInstId(inst_id);

            return ResponseResult.success("????????????", flowById(deploymentId)).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
            ex.printStackTrace();
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    @ApiOperation(value = "??????????????????id??????????????????",notes = "???????????????????????????????????????")
    @ApiImplicitParams({ @ApiImplicitParam(
            name = "flow_id",
            value = "??????id",
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

            return ResponseResult.success("????????????", sysModel).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
            ex.printStackTrace();
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    @ApiOperation(value = "????????????id??????????????????",notes = "????????????id??????????????????")
    @ApiImplicitParams({ @ApiImplicitParam(
            name = "flow_id",
            value = "??????id",
            dataType = "String",
            paramType = "query",
            example = "388d3160-3965-11ec-a578-3c970ef14df2"
    ) })
    @GetMapping("/nodes/flow/{flow_id}")
    public ResponseEntity<?> nodesByFlow(@PathVariable String flow_id) {
        try{
            List<SysFlowExt> list = sysFlowExtMapper.findNodesByHID(flow_id);
            return ResponseResult.success("????????????", list).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
            ex.printStackTrace();
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    @ApiOperation(value = "?????????????????????????????????", notes = "???????????????????????????????????????")
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

            return ResponseResult.success("????????????", p).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
            ex.printStackTrace();
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    @ApiOperation(value = "????????????????????????????????????", notes = "????????????????????????????????????")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "userId", value = "??????id", dataType = "String", paramType = "query", example = ""),
            @ApiImplicitParam(name = "pageIndex", value = "??????", dataType = "String", paramType = "query", example = ""),
            @ApiImplicitParam(name = "pageSize", value = "?????????", dataType = "String", paramType = "query", example = "")
    })
    @GetMapping("/process/instanceList/user")
    public ResponseEntity<?> getTaskFinishedByUser(@RequestParam String userId, @RequestParam @Nullable Long pageIndex,
                                                   @RequestParam @Nullable Long pageSize) {
        ProcessInstancePager p = null;
        try{
            List<ProcessInstanceDTO> instanceDTOS = null;

            LambdaQueryWrapper<SysTaskExt> condition = new LambdaQueryWrapper();
            condition.groupBy(SysTaskExt::getHId).select(SysTaskExt::getHId);
            condition.eq(SysTaskExt::getUser, userId);
            condition.like(SysTaskExt::getNode, "Activity%");
            List<SysTaskExt> finishedTasks = sysTaskExtMapper.selectList(condition);

            if(Objects.nonNull(finishedTasks) && !finishedTasks.isEmpty()){
                List<String> hIds = finishedTasks.stream().map(SysTaskExt::getHId).collect(Collectors.toList());
                LambdaQueryWrapper<SysTask> lambdaQuery2 = new QueryWrapper<SysTask>().lambda();
                lambdaQuery2.in(SysTask::getId, hIds);
                List<SysTask> tasksHeadList = sysTaskMapper.selectList(lambdaQuery2);
                Set<String> instanceIds = tasksHeadList.stream().map(SysTask::getTaskId).collect(Collectors.toSet());

                Calendar now = Calendar.getInstance();
                now.add(Calendar.DAY_OF_MONTH, -30);
                long count = historyService.createHistoricProcessInstanceQuery()
                        .processInstanceIds(instanceIds)
                        .startedAfter(now.getTime())
                        .orderByProcessInstanceStartTime().desc()
                        .count();

                //??????
                p = new ProcessInstancePager(null, pageIndex, pageSize, count);
                List<HistoricProcessInstance> instances = historyService.createHistoricProcessInstanceQuery()
                        .processInstanceIds(instanceIds)
                        .startedAfter(now.getTime())
                        .orderByProcessInstanceStartTime().desc()
                        .listPage(p.getFirst().intValue(), p.getPageSize().intValue());

                //??????
                instanceDTOS = fillInstanceDTOByInstance(instances);
                p.setData(instanceDTOS);
            }

            return ResponseResult.success("????????????", p).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
            ex.printStackTrace();
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    /**
     * ????????????
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
            pi.setEndTime(processInstance.getEndTime() == null ? "???" : processInstance.getEndTime().getTime()+"");
            pi.setStatus("??????");

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
                pi.setStatus(actTask.isSuspended() ? "??????" : "??????");

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
     * ??????????????????id??????????????????id
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
     * ????????????id??????????????????id
     *
     * @param deploymentId
     * @return java.lang.String
     * @author apr
     * @date 2021/10/27 14:43
     */
    private String getProcessDefIdByDeploymentId(String deploymentId){
           List<ProcessDefinition> list = processEngine.getRepositoryService()//???????????????????????????????????????Service
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
     * ??????????????????
     *
     * @param dep_id
     * @return boolean
     * @author apr
     * @date 2021/11/12 16:13
     */
    private boolean nodesMatch(String dep_id) {
        LambdaQueryWrapper<SysFlow> lambdaQuery = new QueryWrapper<SysFlow>().lambda();
        lambdaQuery.eq(SysFlow::getDeploymentId, dep_id);
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
