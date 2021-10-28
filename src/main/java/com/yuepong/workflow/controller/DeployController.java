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
import com.yuepong.workflow.utils.BpmnConverterUtil;
import com.yuepong.workflow.utils.RestMessgae;
import com.yuepong.workflow.utils.Utils;
import io.swagger.annotations.*;
import org.activiti.bpmn.converter.BpmnXMLConverter;
import org.activiti.bpmn.model.*;
import org.activiti.bpmn.model.Process;
import org.activiti.editor.language.json.converter.BpmnJsonConverter;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.ProcessEngines;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.DeploymentBuilder;
import org.activiti.engine.repository.Model;
import org.activiti.engine.repository.ProcessDefinition;
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
@Api(tags="部署流程、删除流程")
public class DeployController {

    ProcessEngine processEngine = ProcessEngines.getDefaultProcessEngine();

    private final RepositoryService repositoryService;

    @Autowired
    SysFlowMapper sysFlowMapper;

    @Autowired
    SysFlowExtMapper sysFlowExtMapper;

    @Autowired
    ObjectMapper objectMapper;

    public DeployController(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @GetMapping(path = "/model/deploy/{modelId}")
    @ApiOperation(value = "根据bpmnName部署流程",notes = "根据bpmnName部署流程，需要bpmn/png两个文件")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "bpmnName",value = "设计的流程图名称",dataType = "String",paramType = "query",example = "myProcess")
    })
    public ResponseEntity<?> deploy(@PathVariable String modelId){
		try {
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
                    .name(modelData.getName())
                    .addString(processName, new String(bpmnBytes, StandardCharsets.UTF_8))
                    .deploy();
            modelData.setDeploymentId(deployment.getId());
            repositoryService.saveModel(modelData);

			return ResponseResult.success("请求成功", modelId).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
			return ResponseResult.error(ex.getMessage()).response();
		}
	}

    @PostMapping(path = "deployBpmn")
    @ApiOperation(value = "根据bpmnName部署流程",notes = "根据bpmnName部署流程，需要bpmn/png两个文件")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "bpmnName",value = "设计的流程图名称",dataType = "String",paramType = "query",example = "myProcess")
    })
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

    @PostMapping(path = "deployZIP")
    @ApiOperation(value = "根据ZIP压缩包部署流程",notes = "根据ZIP压缩包部署流程")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "zipName",value = "设计的流程图和图片的压缩包名称",dataType = "String",paramType = "query",example = "myProcess")
    })
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

    @PostMapping(path = "deleteProcess")
    @ApiOperation(value = "根据部署ID删除流程",notes = "根据部署ID删除流程")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "deploymentId",value = "部署ID",dataType = "String",paramType = "query",example = "example")
    })
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

    @PostMapping("/model/search")
    @ApiOperation(value = "根据ID查询model",notes = "根据ID查询model")
    public ResponseEntity<?> getModel(@RequestBody ModelQueryParam modelQueryParam) {
        try {
            byte[] bpmnBytes = repositoryService.getModelEditorSource(modelQueryParam.getId());
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

    @PostMapping(value = "/model/{modelId}/xml/save")
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

			return ResponseResult.success("请求成功", modelId).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), modelId).response();
		} catch (Exception ex) {
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    @GetMapping("/model/list/{firstResult}/{maxResults}")
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

	@GetMapping("/model/process/list")
    @ApiOperation(value = "查询部署列表",notes = "查询部署列表")
    public ResponseEntity<?> processList(@RequestParam @Nullable Integer firstResult, @RequestParam @Nullable Integer maxResults) {
        try {
            if(firstResult==null)
                firstResult = 0;
            if(maxResults==null)
                maxResults=9999;

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

    @GetMapping("/model/process/{process_id}/nodes")
    @ApiOperation(value = "根据部署单条查询其所有Nodes",notes = "根据部署单条查询其所有Nodes")
    public ResponseEntity<?> processListNodes(@PathVariable String process_id) {
        try {
            String processDefinitionId = getProcessDefIdByProcessId(process_id);
            BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
            Collection<FlowElement> flowElements = bpmnModel.getMainProcess().getFlowElements();
            List<FlowElement> result = flowElements.stream().filter(flowElement -> flowElement instanceof Task || flowElement instanceof Gateway).collect(Collectors.toList());
			return ResponseResult.success("请求成功", result).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
			return ResponseResult.error(ex.getMessage()).response();
		}

    }

    @PostMapping("/model/apply")
    @ApiOperation(value = "关联流程和业务节点", notes = "不用传id和关联id")
    public ResponseEntity<?> applyModel(@RequestBody SysInfo sysInfo) {
        try {
            SysFlow flow = sysInfo.getFlow();
            List<SysFlowExt> nodes = sysInfo.getNodes();

            String flowId = UUID.randomUUID().toString();
            flow.setId(flowId);
            sysFlowMapper.insert(flow);

            nodes.forEach(node -> {
                String nodeId = UUID.randomUUID().toString();
                node.setId(nodeId);
                node.setHId(flowId);
                sysFlowExtMapper.insert(node);
            });

            return ResponseResult.success("请求成功", flowId).response();
		} catch (BizException be) {
			return ResponseResult.obtain(CodeMsgs.SERVICE_BASE_ERROR,be.getMessage(), null).response();
		} catch (Exception ex) {
            ex.printStackTrace();
			return ResponseResult.error(ex.getMessage()).response();
		}
    }

    @GetMapping("/model/flow/list")
    @ApiOperation(value = "查询流程列表",notes = "查询流程列表")
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

    @PostMapping("/model/flow/search")
    @ApiOperation(value = "根据流程id, sysModel, sysTable, flowId查询流程",notes = "根据流程id, sysModel, sysTable, flowId查询流程")
    public ResponseEntity<?> flowById(@RequestBody SysFlow sysFlow) {
        try{
            LambdaQueryWrapper<SysFlow> lambdaQuery = new QueryWrapper<SysFlow>().lambda();
            if(Objects.nonNull(sysFlow)){
                if(Objects.nonNull(sysFlow.getId()))
                    lambdaQuery.eq(SysFlow::getId, sysFlow.getId());
                if(Objects.nonNull(sysFlow.getSysModel()))
                    lambdaQuery.eq(SysFlow::getSysModel, sysFlow.getSysModel());
                if(Objects.nonNull(sysFlow.getSysTable()))
                    lambdaQuery.eq(SysFlow::getSysTable, sysFlow.getSysTable());
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

    @GetMapping("/model/nodes/flow/{flow_id}")
    @ApiOperation(value = "根据流程id查询所有节点",notes = "根据流程id查询所有节点")
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
