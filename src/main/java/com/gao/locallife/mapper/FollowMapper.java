package com.gao.locallife.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gao.locallife.entity.Follow;
import org.apache.ibatis.annotations.Mapper;

/** 关注关系 Mapper：单表 CRUD 由 BaseMapper 提供，复杂 SQL 后续在对应模块加。 */
@Mapper
public interface FollowMapper extends BaseMapper<Follow> {
}
