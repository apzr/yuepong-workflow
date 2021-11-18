package com.yuepong.workflow.service;

import org.activiti.engine.task.Task;

/**
 * TaskService
 * <p>
 * <br/>
 *
 * @author apr
 * @date 2021/11/18 15:04:26
 **/
public interface TaskService {

    Task getByInstId(String instId);
}
