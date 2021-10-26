package com.yuepong.workflow.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.yuepong.jdev.api.bean.ResponseResult;
import com.yuepong.jdev.code.CodeMsgs;
import com.yuepong.jdev.exception.BizException;
import com.yuepong.workflow.dto.ModelAttr;
import com.yuepong.workflow.dto.ModelQueryParam;
import com.yuepong.workflow.utils.BpmnConverterUtil;
import com.yuepong.workflow.utils.RestMessgae;
import com.yuepong.workflow.utils.Utils;
import io.swagger.annotations.*;
import org.activiti.bpmn.converter.BpmnXMLConverter;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.bpmn.model.ExtensionAttribute;
import org.activiti.bpmn.model.Process;
import org.activiti.editor.language.json.converter.BpmnJsonConverter;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.DeploymentBuilder;
import org.activiti.engine.repository.Model;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.util.*;
import java.util.zip.ZipInputStream;

/**
 * @author Apr
 * @Description <p> 部署流程、删除流程 </p>
 */
@Controller
@Api(tags="部署流程、删除流程")
public class DeployController {

    private final RepositoryService repositoryService;

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
                    .addString(processName, new String(bpmnBytes, "UTF-8"))
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

        if (deployment != null) {
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
            InputStream in = this.getClass().getClassLoader().getResourceAsStream("processes/leaveProcess.zip");
            ZipInputStream zipInputStream = new ZipInputStream(in);
            deployment = repositoryService.createDeployment()
                    .name("请假流程2")
                    //指定zip格式的文件完成部署
                    .addZipInputStream(zipInputStream)
                    .deploy();//完成部署
            zipInputStream.close();
        } catch (Exception e) {
            restMessgae = RestMessgae.fail("部署失败", e.getMessage());
            // TODO 上线时删除
            e.printStackTrace();
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
            @ApiImplicitParam(name = "deploymentId",value = "部署ID",dataType = "String",paramType = "query",example = "")
    })
    public RestMessgae deleteProcess(@RequestParam("deploymentId") String deploymentId){
        RestMessgae restMessgae = new RestMessgae();
        /**不带级联的删除：只能删除没有启动的流程，如果流程启动，就会抛出异常*/
        try {
            repositoryService.deleteDeployment(deploymentId);
        } catch (Exception e) {
            restMessgae = RestMessgae.fail("删除失败", e.getMessage());
            // TODO 上线时删除
            e.printStackTrace();
        }

        /**级联删除：不管流程是否启动，都能删除*/
//        repositoryService.deleteDeployment(deploymentId, true);
        restMessgae = RestMessgae.success("删除成功", null);
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
            repositoryService.addModelEditorSource(model.getId(), jsonBpmnXml.getBytes("utf-8"));
            repositoryService.addModelEditorSourceExtra(model.getId(), svg.getBytes("utf-8"));

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
}
