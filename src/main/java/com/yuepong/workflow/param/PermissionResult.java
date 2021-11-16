package com.yuepong.workflow.param;

import lombok.Data;

import java.security.Permissions;

/**
 * PermissionResult
 * 审批与撤回权限
 * <br/>
 *
 * @author apr
 * @date 2021/11/16 16:06:06
 **/
@Data
public class PermissionResult{
    private boolean approve;
    private boolean back;

    public static PermissionResult newInstance(boolean approve, boolean back){
        PermissionResult p = new PermissionResult();
        p.setApprove(approve);
        p.setBack(back);
        return p;
    }
}
