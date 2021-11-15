package com.yuepong.workflow.page.pager;

import com.yuepong.workflow.page.Pager;
import lombok.Data;

import java.util.List;

/**
 * TaskTodo
 * 待办任务
 * <br/>
 *
 * @author apr
 * @date 2021/11/01 11:34:57
 **/
@Data
public class TaskTodoPager<TaskTodo> extends Pager<TaskTodo> {
    public TaskTodoPager(List<TaskTodo> data, Long pageIndex, Long pageSize, Long maxSize) {
        super(data, pageIndex, pageSize, maxSize);
    }
}
