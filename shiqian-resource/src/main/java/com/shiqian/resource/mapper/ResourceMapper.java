package com.shiqian.resource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.resource.entity.Resource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ResourceMapper extends BaseMapper<Resource> {

    @Select("""
            <script>
            SELECT *
            FROM t_resource
            WHERE deleted = 1
            <if test="keyword != null and keyword != ''">
              AND (
                title LIKE CONCAT('%', #{keyword}, '%')
                OR summary LIKE CONCAT('%', #{keyword}, '%')
                OR description LIKE CONCAT('%', #{keyword}, '%')
                OR content_markdown LIKE CONCAT('%', #{keyword}, '%')
              )
            </if>
            ORDER BY update_time DESC
            </script>
            """)
    Page<Resource> selectRecyclePage(Page<Resource> page, @Param("keyword") String keyword);
}
