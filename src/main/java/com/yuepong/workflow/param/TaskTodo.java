package com.yuepong.workflow.param;

import com.yuepong.workflow.dto.SysTask;
import lombok.Data;

/**
 * TaskTodo
 * 待办任务
 * <br/>
 *
 * @author apr
 * @date 2021/11/01 11:34:57
 **/
@Data
public class TaskTodo implements Comparable<TaskTodo>{
    private String id;//任务id
    private String procInstId;//任务id
    private String node;//审批环节
    private String executor;//执行人id
    private String executorName;//执行人名
    private String creator ;//发起人
    private String creatorName ;//发起人
    private String createTime;//创建时间
    private String costTime;//停留时间

    private SysTask header;

    public TaskTodo(){};

    public TaskTodo(String id, String procInstId, String node, String executor,
                    String executorName, String creator, String creatorName, String create, String cost,SysTask header){
        this.id = id;
        this.procInstId = procInstId;
        this.node = node;
        this.executor = executor;
        this.executorName = executorName;
        this.creator = creator;
        this.creatorName = creatorName;
        this.createTime = create;
        this.costTime = cost;
        this.header = header;
    }

    @Override
    public int compareTo(TaskTodo s) {
        try{
            Long.parseLong(this.createTime);
            Long.parseLong(s.getCreateTime());
        }catch(Exception e){
            return -1;
        }

        if(this.createTime.equals(s.getCreateTime()))
            return 0;
        else if(Long.parseLong(this.createTime) > Long.parseLong(s.getCreateTime()))
            return -1;
        else
            return 1;
    }
}
