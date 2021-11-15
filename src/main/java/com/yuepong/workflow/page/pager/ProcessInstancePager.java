package com.yuepong.workflow.page.pager;

import com.yuepong.workflow.page.Pager;

import java.util.List;

/**
 * ProcessInstancePage
 * <p>
 * <br/>
 *
 * @author apr
 * @date 2021/11/15 15:05:08
 **/
public class ProcessInstancePager<ProcessInstanceDTO>  extends Pager<ProcessInstanceDTO> {

    public ProcessInstancePager(List<ProcessInstanceDTO> data, Long pageIndex, Long pageSize, Long maxSize) {
        super(data, pageIndex, pageSize, maxSize);
    }
}
