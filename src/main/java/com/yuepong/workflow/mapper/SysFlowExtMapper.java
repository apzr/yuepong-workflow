package com.yuepong.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuepong.workflow.dto.SysFlowExt;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;

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

}
