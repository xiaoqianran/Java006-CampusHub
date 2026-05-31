package com.shiqian.resource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.resource.entity.Resource;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    @Update("UPDATE t_resource SET deleted=0, status=0 WHERE id=#{id} AND deleted=1")
    int restoreById(@Param("id") Long id);

    @Delete("DELETE FROM t_resource WHERE id=#{id}")
    int physicalDeleteById(@Param("id") Long id);
}
