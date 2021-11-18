package com.yuepong.workflow.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.yuepong.jdev.exception.BizException;
import com.yuepong.workflow.service.BaseService;
import com.yuepong.workflow.service.DeployService;
import com.yuepong.workflow.service.ModelService;
import org.activiti.bpmn.converter.BpmnXMLConverter;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.editor.language.json.converter.BpmnJsonConverter;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.Model;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * DeployServiceImpl
 * <p>
 * <br/>
 *
 * @author apr
 * @date 2021/11/18 16:05:31
 **/
@Service
public class DeployServiceImpl extends BaseService implements DeployService {

    @Override
    public Deployment deploy(String modelId) {
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
        JsonNode modelNode = null;
        try {
            modelNode = objectMapper.readTree(bytes);
        } catch (IOException e) {
            e.printStackTrace();
        }

        BpmnModel model = new BpmnJsonConverter().convertToBpmnModel(modelNode);
        if (model.getProcesses().size() == 0){
            throw new BizException("数据模型不符合要求，请至少设计一条主线程流。");
        }
        byte[] bpmnBytes = new BpmnXMLConverter().convertToXML(model);

        //部署流程
        String processName = modelData.getName() + ".bpmn20.xml";
        Deployment deployment = repositoryService
                .createDeployment()
                .key(modelData.getId())
                .name(modelData.getName())
                .addString(processName, new String(bpmnBytes, StandardCharsets.UTF_8))
                .deploy();
        modelData.setDeploymentId(deployment.getId());

        repositoryService.saveModel(modelData);

        return deployment;
    }

    @Override
    public List<Deployment> queryByKey(String deploymentKey) {
        return repositoryService.createDeploymentQuery().deploymentKey(deploymentKey).list();
    }
}
