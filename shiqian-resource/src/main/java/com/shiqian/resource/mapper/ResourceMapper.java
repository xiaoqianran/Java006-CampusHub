package com.shiqian.resource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.resource.entity.Resource;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ResourceMapper extends BaseMapper<Resource> {

    @Select("""
            SELECT *
            FROM t_resource
            WHERE id = #{id}
              AND deleted = 0
            FOR UPDATE
            """)
    Resource selectByIdForUpdate(@Param("id") Long id);

    @Select("""
            SELECT *
            FROM t_resource
            WHERE external_source = #{source}
              AND external_id = #{externalId}
            ORDER BY id DESC
            LIMIT 1
            """)
    Resource selectByExternalIdIncludingDeleted(
            @Param("source") String source,
            @Param("externalId") String externalId);

    @Select("""
            <script>
            SELECT external_id
            FROM t_resource
            WHERE external_source = #{source}
              AND external_id IN
              <foreach collection="externalIds" item="externalId" open="(" separator="," close=")">
                #{externalId}
              </foreach>
            </script>
            """)
    List<String> selectExistingExternalIdsIncludingDeleted(
            @Param("source") String source,
            @Param("externalIds") List<String> externalIds);

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
