package com.shiqian.resource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shiqian.resource.entity.ResourceVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ResourceVersionMapper extends BaseMapper<ResourceVersion> {

    @Select("""
            SELECT COALESCE(MAX(version_number), 0)
            FROM t_resource_version
            WHERE resource_id = #{resourceId}
            """)
    int selectMaxVersion(@Param("resourceId") Long resourceId);
}
