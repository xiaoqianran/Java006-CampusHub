package com.shiqian.resource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shiqian.resource.entity.Tag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TagMapper extends BaseMapper<Tag> {

    @Select("""
            SELECT *
            FROM t_tag
            WHERE name = #{name}
            LIMIT 1
            """)
    Tag selectByNameIncludingDeleted(@Param("name") String name);

    @Update("""
            UPDATE t_tag
            SET deleted = 0, status = 1, update_time = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int restoreById(@Param("id") Long id);
}
