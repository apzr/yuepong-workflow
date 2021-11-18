package com.yuepong.workflow.service;

import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.runtime.ProcessInstance;

import java.util.List;

/**
 * TaskInfoService
 * <p>
 * <br/>
 *
 * @author apr
 * @date 2021/11/18 15:03:09
 **/
public interface ProcessService{

    ProcessInstance getInstanceByInstId(String instId);

    List<ProcessInstance> getInstanceByDeployment(String deploymentId);

    HistoricProcessInstance getHisInstanceByInstId(String deploymentId);
}
