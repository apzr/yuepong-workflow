package com.yuepong.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuepong.workflow.dto.SysFlowExt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SysFlowMapper
 * <p>
 * <br/>
 *
 * @author apr
 * @date 2021/10/26 16:25:29
 **/
@Mapper
@Component
public interface SysFlowExtMapper extends BaseMapper<SysFlowExt> {
    /**
     * findNodesByHID
     *
     * @param hid
     * @return java.util.List<com.yuepong.workflow.dto.SysFlowExt>
     * @author apr
     * @date 2021/10/27 10:05
     */
    List<SysFlowExt> findNodesByHID(@Param("hid") String hid);
}
