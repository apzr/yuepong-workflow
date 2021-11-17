package com.yuepong.workflow.param;

import lombok.Data;

/**
 * PermissionResult
 * 审批与撤回权限
 * <br/>
 *
 * @author apr
 * @date 2021/11/16 16:06:06
 **/
@Data
public class MonitorResult {
    private int done;
    private int todo;
    private int create;

    public static MonitorResult newInstance(int done, int todo, int create){
        MonitorResult p = new MonitorResult();

        p.setDone(done);
        p.setTodo(todo);
        p.setCreate(create);

        return p;
    }
}
