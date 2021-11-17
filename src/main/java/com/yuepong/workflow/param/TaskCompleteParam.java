package com.yuepong.workflow.param;

import lombok.Data;

import java.util.Map;

/**
 * 完成任务
 * <p>
 * <br/>
 *
 * @author apr
 * @date 2021/11/02 09:42:55
 **/
@Data
public class TaskCompleteParam {
    public String dataId;
    public String opinion;
    public String command;
    public String userId;
    public String userName;
    public String[] role;
    public Map<String, Object> value;
}
