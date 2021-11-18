package com.yuepong.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.yuepong.jdev.api.bean.ResponseResult;
import com.yuepong.jdev.code.CodeMsgs;
import com.yuepong.jdev.exception.BizException;
import com.yuepong.workflow.dto.BpmnByte;
import com.yuepong.workflow.param.ModelQueryResult;
import com.yuepong.workflow.service.BaseService;
import com.yuepong.workflow.service.ModelService;
import com.yuepong.workflow.service.ProcessService;
import com.yuepong.workflow.service.TaskService;
import com.yuepong.workflow.utils.BpmnConverterUtil;
import com.yuepong.workflow.utils.Utils;
import org.activiti.bpmn.converter.BpmnXMLConverter;
import org.activiti.bpmn.model.*;
import org.activiti.bpmn.model.Process;
import org.activiti.editor.language.json.converter.BpmnJsonConverter;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.Model;
import org.activiti.engine.runtime.ProcessInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * TaskService
 * <p>
 * <br/>
 *
 * @author apr
 * @date 2021/11/18 15:01:08
 **/
@Service
public class ModelServiceImpl extends BaseService implements ModelService {

    @Autowired
    ProcessService processService;

    public Model saveModel(String modelId, String bpmnXml, String svgXml){
        //bpmn
        String jsonBpmnXml = BpmnConverterUtil.converterXmlToJson(bpmnXml).toString();

        //model
        Model model;

        if("-1".equals(modelId)){
            model = repositoryService.newModel();
        }else{
            model = this.getModelById(modelId);
            model.setVersion(model.getVersion()+1);

            List<ProcessInstance> instanceList = processService.getInstanceByDeployment(model.getDeploymentId());
            if(Objects.isNull(instanceList) || instanceList.isEmpty())
                throw new BizException("当前模型存在未执行完的流程, 无法编辑");
        }

        Utils.initModel(model, jsonBpmnXml, objectMapper);

        //db
        repositoryService.saveModel(model);
        repositoryService.addModelEditorSource(model.getId(), jsonBpmnXml.getBytes(StandardCharsets.UTF_8));
        repositoryService.addModelEditorSourceExtra(model.getId(), svgXml.getBytes(StandardCharsets.UTF_8));

        return model;
    }

    @Override
    public Model getModelById(String modelId) {
        return repositoryService.getModel(modelId);
    }

    @Override
    public Model getModelByDeploymentId(String deploymentId) {
        return repositoryService.createModelQuery()
                .deploymentId(deploymentId)
                .singleResult();
    }

    @Override
    public String getModelXmlByInstId(String instId) {
        byte[] bpmnBytes = null;

        ProcessInstance instance = processService.getInstanceByInstId(instId);

        if (Objects.nonNull(instance)) {
            Model model = this.getModelByDeploymentId(instance.getDeploymentId());
            bpmnBytes = repositoryService.getModelEditorSource(model.getId());
        }else{
            HistoricProcessInstance instanceHistory = historyService.createHistoricProcessInstanceQuery().processInstanceId(instId).singleResult();
            if (Objects.nonNull(instanceHistory)) {
                LambdaQueryWrapper<BpmnByte> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.eq(BpmnByte::getDeploymentId, instanceHistory.getDeploymentId());
                BpmnByte bpmnByte = bpmnByteMapper.selectOne(queryWrapper);
                bpmnBytes = bpmnByte.getBytes();
            }
        }

        return new String(bpmnBytes);
    }

    @Override
    public byte[] getModelSourceById(String modelId){
        return repositoryService.getModelEditorSource(modelId);
    }

    @Override
    public List<FlowElement> getNodesByProcessDefId(String processDefinitionId, String[] className) {
        List<FlowElement> result=null;

//        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
//        if( Objects.nonNull(bpmnModel)){
//            Collection<FlowElement> flowElements = bpmnModel.getMainProcess().getFlowElements();
//            if(Objects.nonNull(flowElements)  && clazz !=null ){
//                result = flowElements.stream()
//                        .filter(flowElement -> flowElement.getClass() instanceof Task || flowElement instanceof Gateway)
//                        .collect(Collectors.toList());
//            }
//        }

        return null;
    }

    @Override
    public List<FlowElement> getNodesByProcessDefId(String processDefinitionId) {
        return getNodesByProcessDefId(processDefinitionId, new String[]{});
    }
}
