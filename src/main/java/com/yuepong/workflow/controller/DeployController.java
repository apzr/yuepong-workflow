package com.yuepong.workflow.controller;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
;
import com.yuepong.jdev.api.bean.ResponseResult;
import com.yuepong.jdev.code.CodeMsgs;
import com.yuepong.jdev.exception.BizException;
import com.yuepong.workflow.dto.ModelAttr;
import com.yuepong.workflow.dto.ModelQueryParam;
import com.yuepong.workflow.utils.BpmnConverterUtil;
import com.yuepong.workflow.utils.RestMessgae;
import io.swagger.annotations.*;
import org.activiti.bpmn.converter.BpmnXMLConverter;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.editor.constants.ModelDataJsonConstants;
import org.activiti.editor.language.json.converter.BpmnJsonConverter;
import org.activiti.engine.ActivitiException;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.DeploymentBuilder;
import org.activiti.engine.repository.Model;
import org.activiti.engine.repository.ModelQuery;
import org.apache.commons.lang3.StringEscapeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipInputStream;

/**
 * @author Alex Hu
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

    @PostMapping(path = "deploy")
    @ApiOperation(value = "根据bpmnName部署流程",notes = "根据bpmnName部署流程，需要bpmn/png两个文件")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "bpmnName",value = "设计的流程图名称",dataType = "String",paramType = "query",example = "myProcess")
    })
    public RestMessgae deploy(@RequestParam("bpmnName") String bpmnName){

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
//            ModelQuery query = repositoryService.createModelQuery().modelId(modelQueryParam.getId());
//            Model model = query.singleResult();
            byte[] bpmnBytes = repositoryService.getModelEditorSource(modelQueryParam.getId());
            if(null == bpmnBytes) {
                throw new BizException("模型数据为空");
            }
            JsonNode modelNode = objectMapper.readTree(bpmnBytes);
            BpmnModel bpmnModel = new BpmnJsonConverter().convertToBpmnModel(modelNode);
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
    public RestMessgae saveModelXml(@PathVariable String modelId, @RequestBody ModelAttr modelAttr) {
        RestMessgae restMessgae;
        try {
            if(Objects.nonNull(modelId)){//编辑
                //初始化一个空模型
                Model model = repositoryService.getModel(modelId);
                if(Objects.isNull(model))
                    throw new BizException("没有找到模型");

                String xml = modelAttr.getBpmn_xml();
                String svg = modelAttr.getSvg_xml();

                String bpmnXml = BpmnConverterUtil.converterXmlToJson(xml).toString();

                JSONObject jsonObject = JSONObject.parseObject(bpmnXml);
                JSONObject properties = jsonObject.getJSONObject("properties");
                String modelName = properties.getString("name");
                String modelKey = properties.getString("process_id");
                String modelCategory = properties.getString("processCategory")==null?properties.getString("processCategory"):"default_category";;
                String modelDescription = properties.getString("documentation");
                int revision = 1;//TODO:版本+1


                model.setName(modelName);
                model.setKey(modelKey);
                model.setCategory(modelCategory);

                ObjectNode modelNode = objectMapper.createObjectNode();
                modelNode.put(ModelDataJsonConstants.MODEL_NAME,modelName);
                modelNode.put(ModelDataJsonConstants.MODEL_DESCRIPTION, modelDescription);
                modelNode.put(ModelDataJsonConstants.MODEL_REVISION, revision);
                model.setMetaInfo(modelNode.toString());

                repositoryService.saveModel(model);

                //repositoryService.createModelQuery().
                repositoryService.addModelEditorSource(model.getId(), bpmnXml.getBytes("utf-8"));
                repositoryService.addModelEditorSourceExtra(model.getId(), svg.getBytes("utf-8"));
            }else{//新增
                String xml = modelAttr.getBpmn_xml();
                String svg = modelAttr.getSvg_xml();

                String bpmnXml = BpmnConverterUtil.converterXmlToJson(xml).toString();

                JSONObject jsonObject = JSONObject.parseObject(bpmnXml);
                JSONObject properties = jsonObject.getJSONObject("properties");
                String modelName = properties.getString("name");
                String modelKey = properties.getString("process_id");
                String modelCategory = properties.getString("processCategory")==null?properties.getString("processCategory"):"default_category";;
                String modelDescription = properties.getString("documentation");
                int revision = 1;

                //初始化一个空模型
                Model model = repositoryService.newModel();
                model.setName(modelName);
                model.setKey(modelKey);
                model.setCategory(modelCategory);

                ObjectNode modelNode = objectMapper.createObjectNode();
                modelNode.put(ModelDataJsonConstants.MODEL_NAME,modelName);
                modelNode.put(ModelDataJsonConstants.MODEL_DESCRIPTION, modelDescription);
                modelNode.put(ModelDataJsonConstants.MODEL_REVISION, revision);
                model.setMetaInfo(modelNode.toString());

                repositoryService.saveModel(model);

                //repositoryService.createModelQuery().
                repositoryService.addModelEditorSource(model.getId(), bpmnXml.getBytes("utf-8"));
                repositoryService.addModelEditorSourceExtra(model.getId(), svg.getBytes("utf-8"));
            }


            restMessgae = RestMessgae.success("操作成功", null);
        } catch (Exception e) {
            restMessgae = RestMessgae.fail("操作失败", e.getMessage());
        }

        return  restMessgae;
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
