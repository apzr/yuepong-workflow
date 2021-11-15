package com.yuepong.workflow.page.pager;

import com.yuepong.workflow.page.Pager;

import java.util.List;

/**
 * ModelPager
 * <p>
 * <br/>
 *
 * @author apr
 * @date 2021/11/15 17:13:02
 **/
public class ModelPager<Model> extends Pager<Model> {
    public ModelPager(List<Model> data, Long pageIndex, Long pageSize, Long maxSize) {
        super(data, pageIndex, pageSize, maxSize);
    }
}
