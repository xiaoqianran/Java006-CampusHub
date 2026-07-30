package com.shiqian.resource.controller;

import com.shiqian.resource.dto.ArchivePreviewVO;
import com.shiqian.resource.dto.FileDownloadVO;
import com.shiqian.resource.dto.FileUploadVO;
import com.shiqian.resource.dto.IndexConsistencyVO;
import com.shiqian.resource.dto.ResourceVersionVO;
import com.shiqian.resource.dto.SignedFileUrlVO;
import com.shiqian.resource.dto.TextFilePreviewVO;
import com.shiqian.resource.vo.AdminLogVO;
import com.shiqian.resource.vo.CategoryVO;
import com.shiqian.resource.vo.ContentReviewRecordVO;
import com.shiqian.resource.vo.ResourceAttachmentVO;
import com.shiqian.resource.vo.ResourceVO;
import com.shiqian.resource.vo.SensitiveWordVO;
import com.shiqian.resource.vo.TagVO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ControllerResponseContractTest {

    private static final List<Class<?>> CONTROLLERS = List.of(
            ResourceController.class,
            ResourceFileController.class,
            CategoryController.class,
            TagController.class,
            AdminLogController.class,
            ContentModerationController.class,
            JimengIngestController.class);

    private static final List<Class<?>> RESPONSE_MODELS = List.of(
            ResourceVO.class,
            ResourceAttachmentVO.class,
            CategoryVO.class,
            TagVO.class,
            AdminLogVO.class,
            SensitiveWordVO.class,
            ContentReviewRecordVO.class,
            ResourceVersionVO.class,
            FileDownloadVO.class,
            FileUploadVO.class,
            SignedFileUrlVO.class,
            TextFilePreviewVO.class,
            ArchivePreviewVO.class,
            IndexConsistencyVO.class);

    @Test
    void controllerSignaturesMustNotExposePersistenceEntities() {
        for (Class<?> controller : CONTROLLERS) {
            for (Method method : controller.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers())) continue;
                assertNoEntityType(
                        method.getGenericReturnType().getTypeName(),
                        controller.getSimpleName() + "#" + method.getName());
            }
        }
    }

    @Test
    void responseModelsMustNotContainPersistenceEntities() {
        for (Class<?> model : RESPONSE_MODELS) {
            for (Field field : model.getDeclaredFields()) {
                assertNoEntityType(
                        field.getGenericType().getTypeName(),
                        model.getSimpleName() + "." + field.getName());
            }
        }
    }

    private void assertNoEntityType(String typeName, String location) {
        assertFalse(
                typeName.contains(".entity."),
                () -> location + " 仍然暴露数据库 Entity: " + typeName);
    }
}
