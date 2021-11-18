package com.yuepong.workflow.service.impl;

import com.yuepong.workflow.service.BaseService;
import com.yuepong.workflow.service.ProcessService;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * TaskInfoService
 * <p>
 * <br/>
 *
 * @author apr
 * @date 2021/11/18 15:03:09
 **/
@Service
public class ProcessServiceImpl extends BaseService implements ProcessService {

    public ProcessInstance getInstanceByInstId(String instId){
        return runtimeService.createProcessInstanceQuery()
                .processInstanceId(instId)
                .singleResult();
    }

    public List<ProcessInstance> getInstanceByDeployment(String deploymentId) {
        return runtimeService.createProcessInstanceQuery()
                .deploymentId(deploymentId).list();
    }

    @Override
    public HistoricProcessInstance getHisInstanceByInstId(String instId) {
        return historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(instId)
                .singleResult();
    }

}
