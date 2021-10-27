package com.yuepong.workflow.mapper;

import cn.hutool.system.UserInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuepong.workflow.dto.SysFlow;
import com.yuepong.workflow.dto.SysFlowExt;
import org.apache.ibatis.annotations.*;
import org.apache.ibatis.mapping.FetchType;
import org.apache.ibatis.type.JdbcType;
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
public interface SysFlowMapper extends BaseMapper<SysFlow> {

}
