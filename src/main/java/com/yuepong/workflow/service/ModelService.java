package com.yuepong.workflow.service;

import org.activiti.bpmn.model.FlowElement;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.Model;

import java.util.List;

/**
 * TaskService
 * <p>
 * <br/>
 *
 * @author apr
 * @date 2021/11/18 15:01:08
 **/
public interface ModelService {

    /**
     * 根据bpmn保存模型
     *
     * @param modelId 模型id
     * @param bpmnXml bpmn文件
     * @param svgXml svg文件
     * @return org.activiti.engine.repository.Model
     * @author apr
     * @date 2021/11/18 15:26
     */
    Model saveModel(String modelId, String bpmnXml, String svgXml);

    Model getModelById(String modelId);

    Model getModelByDeploymentId(String instId);

    String getModelXmlByInstId(String deploymentId);

    byte[] getModelSourceById(String modelId);

    List<FlowElement> getNodesByProcessDefId(String processDefinitionId, String[] filter);

    List<FlowElement> getNodesByProcessDefId(String processDefinitionId);
}
