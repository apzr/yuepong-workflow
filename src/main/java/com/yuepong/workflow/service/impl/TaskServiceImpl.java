package com.yuepong.workflow.service.impl;

import com.yuepong.workflow.service.BaseService;
import com.yuepong.workflow.service.TaskService;
import org.activiti.engine.task.Task;
import org.springframework.stereotype.Service;

/**
 * TaskService
 * <p>
 * <br/>
 *
 * @author apr
 * @date 2021/11/18 15:04:26
 **/
@Service
public class TaskServiceImpl extends BaseService implements TaskService {

    @Override
    public Task getByInstId(String instId) {
        Task currentTask = taskService.createTaskQuery().processInstanceId(instId).singleResult();
        return currentTask;
    }
}
