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
import com.yuepong.workflow.mapper.SysFlowExtMapper;
import com.yuepong.workflow.mapper.SysFlowMapper;
import com.yuepong.workflow.mapper.SysTaskExtMapper;
import com.yuepong.workflow.mapper.SysTaskMapper;
import com.yuepong.workflow.utils.BpmnConverterUtil;
import com.yuepong.workflow.utils.RestMessgae;
import com.yuepong.workflow.utils.Utils;
import io.swagger.annotations.*;
import org.activiti.bpmn.converter.BpmnXMLConverter;
import org.activiti.bpmn.model.*;
import org.activiti.bpmn.model.Process;
import org.activiti.editor.language.json.converter.BpmnJsonConverter;
import org.activiti.engine.*;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.history.HistoricVariableInstance;
import org.activiti.engine.impl.persistence.entity.SuspensionState;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.DeploymentBuilder;
import org.activiti.engine.repository.Model;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.ProcessInstance;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipInputStream;

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
    ObjectMapper objectMapper;

    public DeployController(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

//    @PostMapping(path = "deployBpmn")
//    @ApiOperation(value = "根据bpmnName部署流程",notes = "根据bpmnName部署流程，需要bpmn/png两个文件")
//    @ApiImplicitParams({
//            @ApiImplicitParam(name = "bpmnName",value = "设计的流程图名称",dataType = "String",paramType = "query",example = "myProcess")
//    })
    public RestMessgae deployBpmn(@RequestParam("bpmnName") String bpmnName){

        RestMessgae restMessgae = new RestMessgae();
        //创建一个部署对象
        DeploymentBuilder deploymentBuilder = repositoryService.createDeployment().name("请假流程");
        Deployment deployment = null;
        try {
            System.out.println(this.getClass().getResource("").getPath());
            System.out.println(this.getClass().getResource("/").getPath());

            deployment = deploymentBuilder
                    .addClasspathResource("processes/"+bpmnName +".bpmn")
                    .addClasspathResource("processes/"+bpmnName +".png")
                    .deploy();
        } catch (Exception e) {
            restMessgae = RestMessgae.fail("部署失败", e.getMessage());
            e.printStackTrace();
        }

        if (Objects.nonNull(deployment)) {
            Map<String, String> result = new HashMap<>(2);
            result.put("deployID", deployment.getId());
            result.put("deployName", deployment.getName());
            restMessgae = RestMessgae.success("部署成功", result);
        }
        return restMessgae;
    }

//    @PostMapping(path = "deployZIP")
//    @ApiOperation(value = "根据ZIP压缩包部署流程",notes = "根据ZIP压缩包部署流程")
//    @ApiImplicitParams({
//            @ApiImplicitParam(name = "zipName",value = "设计的流程图和图片的压缩包名称",dataType = "String",paramType = "query",example = "myProcess")
//    })
    public RestMessgae deployZIP(@RequestParam("zipName") String zipName){
        RestMessgae restMessgae = new RestMessgae();
        Deployment deployment = null;
        try {
            InputStream in = this.getClass().getClassLoader().getResourceAsStream("processes/"+zipName+".zip");
            ZipInputStream zipInputStream = new ZipInputStream(in);
            deployment = repositoryService.createDeployment()
                    .name("请假流程2")
                    //指定zip格式的文件完成部署
                    .addZipInputStream(zipInputStream)
                    .deploy();//完成部署
            zipInputStream.close();
        } catch (Exception e) {
            restMessgae = RestMessgae.fail("部署失败", e.getMessage());
        }
        if (deployment != null) {
            Map<String, String> result = new HashMap<>(2);
            result.put("deployID", deployment.getId());
            result.put("deployName", deployment.getName());
            restMessgae = RestMessgae.success("部署成功", result);
        }
        return restMessgae;
    }

//    @PostMapping(path = "deleteProcess")
//    @ApiOperation(value = "根据部署ID删除流程",notes = "根据部署ID删除流程")
//    @ApiImplicitParams({
//            @ApiImplicitParam(name = "deploymentId",value = "部署ID",dataType = "String",paramType = "query",example = "example")
//    })
    public RestMessgae deleteProcess(@RequestParam("deploymentId") String deploymentId){
        RestMessgae restMessgae ;
        try {
            repositoryService.deleteDeployment(deploymentId);
            restMessgae = RestMessgae.success("删除成功", null);
        } catch (Exception e) {
            restMessgae = RestMessgae.fail("删除失败", e.getMessage());
            e.printStackTrace();
        }

        return  restMessgae;
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
                throw new BizException("模型数据已经被部署过。");
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

            byte[] bpmnBytes = null;
            if(Objects.nonNull(modelQueryParam.getId()))
                bpmnBytes = repositoryService.getModelEditorSource(modelQueryParam.getId());
            else if(Objects.nonNull(modelQueryParam.getInstanceId())){
                ProcessInstance instance = runtimeService.createProcessInstanceQuery().processInstanceId(modelQueryParam.getInstanceId()).singleResult();
                Model model = repositoryService.createModelQuery().deploymentId(instance.getDeploymentId()).singleResult();
                bpmnBytes = repositoryService.getModelEditorSource(model.getId());
            }

            if(null == bpmnBytes) {
                throw new BizException("模型数据为空");
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

			return ResponseResult.success("请求成功", bpmnText).response();
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
    @GetMapping("/list/category/{category}/{firstResult}/{maxResults}")
	public ResponseEntity<?> listModel(@PathVariable String category, @PathVariable int firstResult, @PathVariable int maxResults) {
		try {
		    //model的id等于我们SysFlow的flow_id
            List<Model> models = repositoryService.createModelQuery().modelCategory(category).listPage(firstResult, maxResults);
			return ResponseResult.success("请求成功", models).response();
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
    @GetMapping("/process/{processInstId_or_deploymentId}/nodes")
    public ResponseEntity<?> processListNodes(@PathVariable String processInstId_or_deploymentId) {
        try {
            List<FlowElement> result = new LinkedList();
//            String processDefinitionId = getProcessDefIdByProcessInstId(processInstId_or_deploymentId);
//            if(StringUtils.isEmpty(processDefinitionId))
            String processDefinitionId = getProcessDefIdByDeploymentId(processInstId_or_deploymentId);

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
    @GetMapping("/enable/{flow_id}")
    public ResponseEntity<?> enableFlow(@PathVariable String flow_id) {
        try{
            LambdaQueryWrapper<SysFlow> lambdaQuery = new QueryWrapper<SysFlow>().lambda();
            lambdaQuery.eq(SysFlow::getFlowId, flow_id);
            SysFlow flow = sysFlowMapper.selectOne(lambdaQuery);

            LambdaQueryWrapper<SysFlow> lambdaQuery1 = new QueryWrapper<SysFlow>().lambda();
            lambdaQuery.eq(SysFlow::getSysModel, flow.getSysModel());
            List<SysFlow> flowListSameType = sysFlowMapper.selectList(lambdaQuery1);
            Optional.ofNullable(flowListSameType).orElse(new ArrayList<>()).forEach(sameTypeFlow -> {
                Boolean disable = !sameTypeFlow.getId().equals(flow.getId());
                sameTypeFlow.setSysDisable(disable);
                sysFlowMapper.updateById(sameTypeFlow);
            });

            return ResponseResult.success("请求成功", null).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
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
            LambdaQueryWrapper<SysFlow> lambdaQuery = new QueryWrapper<SysFlow>().lambda();
            lambdaQuery.eq(SysFlow::getFlowId, flow_id);
            List<SysFlow> list = sysFlowMapper.selectList(lambdaQuery);
            if(Objects.isNull(list)|| list.isEmpty()){
                return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,"未查询到数据", null).response();
            }
            SysFlow flow = list.get(0);
            List<SysFlowExt> nodes = sysFlowExtMapper.findNodesByHID(flow.getId());

            SysModel model = new SysModel();
            model.setFlow(flow);
            model.setNodes(nodes);

            return ResponseResult.success("请求成功", model).response();
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
    public ResponseEntity<?> getInstanceList() {//"032bf875-99b0-4c85-91c0-e128fc759565"
        try{
            List<ProcessInstanceDTO> instanceDTOS = new ArrayList<>();

            List<HistoricProcessInstance> instanceHistoryList = historyService.createHistoricProcessInstanceQuery().list();
            instanceHistoryList.stream().forEach(processInstance -> {
                org.activiti.engine.task.Task actTask = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
                if(Objects.nonNull(actTask)){
                    ProcessInstanceDTO pi = new ProcessInstanceDTO();
                    pi.setInstanceId(processInstance.getId());
                    pi.setCreateTime(processInstance.getStartTime().getTime()+"");
                    pi.setCurrentNodeName(actTask.getName());
                    pi.setCurrentNodeId(actTask.getTaskDefinitionKey());
                    pi.setEndTime(processInstance.getEndTime() == null ? "无" : processInstance.getEndTime().getTime()+"");

                    String isActiveStr = "完成";
                    if(Objects.nonNull(actTask)){
                        isActiveStr = actTask.isSuspended() ? "挂起" : "运行";
                    }
                    pi.setStatus(isActiveStr);

                    List<HistoricVariableInstance> varList = processEngine.getHistoryService()
                                .createHistoricVariableInstanceQuery()
                                .processInstanceId(processInstance.getId())
                                //.taskId(actTask.getId())
                                .variableName("userKey")
                                .list();
                    String userKey = "无";
                    if(Objects.nonNull(varList) && !varList.isEmpty()){
                        userKey = String.valueOf(varList.get(0).getValue());
                    }
                    pi.setCreator(userKey);

                    LambdaQueryWrapper<SysFlowExt> flowCondition = new LambdaQueryWrapper<>();
                    flowCondition.eq(SysFlowExt::getNode, actTask.getTaskDefinitionKey());
                    SysFlowExt sysFlow = sysFlowExtMapper.selectOne(flowCondition);
                    pi.setCurrentAssign(sysFlow.getOperation());

                    LambdaQueryWrapper<SysTask> taskCondition = new LambdaQueryWrapper<>();
                    taskCondition.eq(SysTask::getTaskId, actTask.getProcessInstanceId());
                    SysTask sysTask = sysTaskMapper.selectOne(taskCondition);
                    pi.setHeader(sysTask);

                    instanceDTOS.add(pi);
                }
            });

            return ResponseResult.success("请求成功", instanceDTOS).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
            ex.printStackTrace();
			return ResponseResult.error(ex.getMessage()).response();
		}
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

}
