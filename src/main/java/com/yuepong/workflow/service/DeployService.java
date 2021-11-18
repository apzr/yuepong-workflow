package com.yuepong.workflow.service;

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
public interface DeployService {

    /**
     * 部署模型
     *
     * @param modelId 模型id
     * @return org.activiti.engine.repository.Deployment
     * @author apr
     * @date 2021/11/18 15:27
     */
    Deployment deploy(String modelId);

    List<Deployment> queryByKey(String deploymentKey);
}
