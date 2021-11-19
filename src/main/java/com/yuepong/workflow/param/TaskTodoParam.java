package com.yuepong.workflow.param;

import com.yuepong.workflow.dto.SysTask;
import lombok.Data;

import java.util.Map;

/**
 * TaskTodo
 * 待办任务
 * <br/>
 *
 * @author apr
 * @date 2021/11/01 11:34:57
 **/
@Data
public class TaskTodoParam {
    public String userId;
    public Long pageIndex;
    public Long pageSize;
    public String[] role;
}
