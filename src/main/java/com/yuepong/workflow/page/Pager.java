package com.yuepong.workflow.page;

import lombok.Data;

import java.util.List;
import java.util.Objects;

/**
 * Page
 * <p>
 * <br/>
 *
 * @author apr
 * @date 2021/11/15 09:58:57
 **/
@Data
public abstract class Pager<T> {

    protected List<T> data;

    protected Long pageIndex;

    protected Long pageSize;

    protected Long total;

    protected Long first;

    public Pager() {
    }

    public Pager(List<T> data, Long pageIndex, Long pageSize, Long maxSize) {
        if(Objects.isNull(maxSize) || maxSize < 0){//默认查询所有
            maxSize = 0L;
        }
        if(Objects.isNull(pageIndex) || pageIndex < 0){//默认查询所有
            pageIndex = 0L;
        }
        if(Objects.isNull(pageSize) || pageSize < 0){//默认每页10条
            pageSize = 10L;
        }
        if(pageIndex == 0){
            pageSize = maxSize;
        }

        this.data =  data;
        this.pageIndex = pageIndex;
        this.pageSize = pageSize;
        this.total = maxSize;//new Double( Math.ceil( maxSize / pageSize ) ).longValue();
        if(pageIndex == 0L){//默认查询所有
            first = 0L;
        }else{
            first = (pageIndex-1)*pageSize;
        }
    }
}
