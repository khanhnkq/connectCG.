package org.example.connectcg_be.config;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.example.connectcg_be.controller.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiExportTest {

    @Test
    @DisplayName("Generate and export openapi.json from all REST controllers and DTOs")
    void exportOpenApiSpecification() throws Exception {
        OpenApiConfig config = new OpenApiConfig();
        ReflectionTestUtils.setField(config, "serverPort", "8080");
        OpenAPI openAPI = config.customOpenAPI();

        Paths paths = new Paths();
        openAPI.setPaths(paths);

        List<Class<?>> controllerClasses = List.of(
                AuthController.class,
                PostController.class,
                CommentController.class,
                GroupController.class,
                ReportController.class,
                FriendRestController.class,
                FriendRequestController.class,
                FriendSuggestionController.class,
                ChatController.class,
                UserProfileController.class,
                TungNotificationController.class,
                MediaUploadController.class,
                HobbyController.class,
                OnlineStatusController.class,
                AdminUserManagerController.class,
                HealthCheckController.class
        );

        Map<String, Schema> allSchemas = openAPI.getComponents().getSchemas();
        if (allSchemas == null) {
            allSchemas = new LinkedHashMap<>();
            openAPI.getComponents().setSchemas(allSchemas);
        }

        for (Class<?> controllerClass : controllerClasses) {
            RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(controllerClass, RequestMapping.class);
            String basePath = "";
            if (classMapping != null && classMapping.value().length > 0) {
                basePath = classMapping.value()[0];
            } else if (classMapping != null && classMapping.path().length > 0) {
                basePath = classMapping.path()[0];
            }

            String tagName = controllerClass.getSimpleName()
                    .replace("Controller", "")
                    .replace("RestController", "");

            for (Method method : controllerClass.getDeclaredMethods()) {
                if (method.isSynthetic()) continue;

                String httpMethod = null;
                String[] methodPaths = new String[]{""};

                if (method.isAnnotationPresent(GetMapping.class)) {
                    httpMethod = "GET";
                    GetMapping ann = method.getAnnotation(GetMapping.class);
                    if (ann.value().length > 0) methodPaths = ann.value();
                    else if (ann.path().length > 0) methodPaths = ann.path();
                } else if (method.isAnnotationPresent(PostMapping.class)) {
                    httpMethod = "POST";
                    PostMapping ann = method.getAnnotation(PostMapping.class);
                    if (ann.value().length > 0) methodPaths = ann.value();
                    else if (ann.path().length > 0) methodPaths = ann.path();
                } else if (method.isAnnotationPresent(PutMapping.class)) {
                    httpMethod = "PUT";
                    PutMapping ann = method.getAnnotation(PutMapping.class);
                    if (ann.value().length > 0) methodPaths = ann.value();
                    else if (ann.path().length > 0) methodPaths = ann.path();
                } else if (method.isAnnotationPresent(DeleteMapping.class)) {
                    httpMethod = "DELETE";
                    DeleteMapping ann = method.getAnnotation(DeleteMapping.class);
                    if (ann.value().length > 0) methodPaths = ann.value();
                    else if (ann.path().length > 0) methodPaths = ann.path();
                } else if (method.isAnnotationPresent(PatchMapping.class)) {
                    httpMethod = "PATCH";
                    PatchMapping ann = method.getAnnotation(PatchMapping.class);
                    if (ann.value().length > 0) methodPaths = ann.value();
                    else if (ann.path().length > 0) methodPaths = ann.path();
                } else if (method.isAnnotationPresent(RequestMapping.class)) {
                    RequestMapping ann = method.getAnnotation(RequestMapping.class);
                    if (ann.method().length > 0) {
                        httpMethod = ann.method()[0].name();
                    } else {
                        httpMethod = "GET";
                    }
                    if (ann.value().length > 0) methodPaths = ann.value();
                    else if (ann.path().length > 0) methodPaths = ann.path();
                }

                if (httpMethod == null) continue;

                for (String mPath : methodPaths) {
                    String fullPath = normalizePath(basePath, mPath);

                    PathItem pathItem = paths.get(fullPath);
                    if (pathItem == null) {
                        pathItem = new PathItem();
                        paths.put(fullPath, pathItem);
                    }

                    Operation operation = new Operation();
                    operation.addTagsItem(tagName);
                    operation.setOperationId(tagName.toLowerCase() + "_" + method.getName());

                    // Parameters
                    java.lang.reflect.Parameter[] methodParams = method.getParameters();
                    for (java.lang.reflect.Parameter p : methodParams) {
                        if (isFrameworkParam(p.getType())) continue;

                        if (p.isAnnotationPresent(PathVariable.class)) {
                            PathVariable pv = p.getAnnotation(PathVariable.class);
                            String paramName = pv.value().isEmpty() ? (pv.name().isEmpty() ? p.getName() : pv.name()) : pv.value();
                            Parameter param = new Parameter()
                                    .name(paramName)
                                    .in("path")
                                    .required(true)
                                    .schema(resolveSimpleSchema(p.getType()));
                            operation.addParametersItem(param);
                        } else if (p.isAnnotationPresent(RequestParam.class)) {
                            RequestParam rp = p.getAnnotation(RequestParam.class);
                            String paramName = rp.value().isEmpty() ? (rp.name().isEmpty() ? p.getName() : rp.name()) : rp.value();
                            Parameter param = new Parameter()
                                    .name(paramName)
                                    .in("query")
                                    .required(rp.required())
                                    .schema(resolveSimpleSchema(p.getType()));
                            operation.addParametersItem(param);
                        } else if (p.isAnnotationPresent(org.springframework.web.bind.annotation.RequestBody.class)) {
                            Class<?> bodyType = p.getType();
                            registerSchema(bodyType, allSchemas);
                            Schema refSchema = new Schema().$ref("#/components/schemas/" + bodyType.getSimpleName());
                            RequestBody requestBody = new RequestBody()
                                    .required(true)
                                    .content(new Content().addMediaType("application/json", new MediaType().schema(refSchema)));
                            operation.setRequestBody(requestBody);
                        }
                    }

                    // Response
                    ApiResponses responses = new ApiResponses();
                    ApiResponse successResponse = new ApiResponse().description("Thành công");

                    Type genericReturnType = method.getGenericReturnType();
                    Class<?> returnPayloadClass = resolvePayloadClass(genericReturnType);
                    if (returnPayloadClass != null && !returnPayloadClass.equals(Void.class) && !returnPayloadClass.equals(void.class)) {
                        registerSchema(returnPayloadClass, allSchemas);
                        Schema respSchema = new Schema().$ref("#/components/schemas/" + returnPayloadClass.getSimpleName());
                        successResponse.setContent(new Content().addMediaType("application/json", new MediaType().schema(respSchema)));
                    }

                    responses.addApiResponse("200", successResponse);
                    operation.setResponses(responses);

                    switch (httpMethod.toUpperCase()) {
                        case "GET" -> pathItem.setGet(operation);
                        case "POST" -> pathItem.setPost(operation);
                        case "PUT" -> pathItem.setPut(operation);
                        case "DELETE" -> pathItem.setDelete(operation);
                        case "PATCH" -> pathItem.setPatch(operation);
                    }
                }
            }
        }

        String openApiJson = Json.pretty(openAPI);
        assertNotNull(openApiJson);
        assertFalse(openApiJson.isBlank());
        assertTrue(openApiJson.contains("/api/v1/posts"));
        assertTrue(openApiJson.contains("/api/v1/reports"));
        assertTrue(openApiJson.contains("/api/v1/auth"));

        // Write openapi.json to connectCG. (backend) and optionally connectCG (frontend if present)
        Path backendPath = Path.of("openapi.json");
        Files.writeString(backendPath, openApiJson);
        assertTrue(Files.exists(backendPath), "Backend openapi.json must be written");

        Path frontendDir = Path.of("../connectCG");
        if (Files.isDirectory(frontendDir)) {
            Path frontendPath = frontendDir.resolve("openapi.json");
            Files.writeString(frontendPath, openApiJson);
            assertTrue(Files.exists(frontendPath), "Frontend openapi.json must be written");
        }

        System.out.println("Exported OpenAPI specification with " + paths.size() + " endpoints and " + allSchemas.size() + " schemas.");
    }

    private String normalizePath(String base, String sub) {
        String combined = (base == null ? "" : base) + (sub == null ? "" : sub);
        if (!combined.startsWith("/")) combined = "/" + combined;
        return combined.replaceAll("//+", "/").replaceAll("/$", "");
    }

    private boolean isFrameworkParam(Class<?> type) {
        String name = type.getName();
        return name.contains("Authentication") ||
                name.contains("Principal") ||
                name.contains("HttpServlet") ||
                name.contains("Pageable");
    }

    private Schema<?> resolveSimpleSchema(Class<?> type) {
        if (type.equals(Integer.class) || type.equals(int.class) || type.equals(Long.class) || type.equals(long.class)) {
            return new io.swagger.v3.oas.models.media.IntegerSchema();
        }
        if (type.equals(Boolean.class) || type.equals(boolean.class)) {
            return new io.swagger.v3.oas.models.media.BooleanSchema();
        }
        return new io.swagger.v3.oas.models.media.StringSchema();
    }

    private Class<?> resolvePayloadClass(Type type) {
        if (type instanceof ParameterizedType pType) {
            Type raw = pType.getRawType();
            if (raw.equals(ResponseEntity.class)) {
                Type arg = pType.getActualTypeArguments()[0];
                return resolvePayloadClass(arg);
            }
            if (raw.getTypeName().contains("List") || raw.getTypeName().contains("Page")) {
                Type arg = pType.getActualTypeArguments()[0];
                return resolvePayloadClass(arg);
            }
        } else if (type instanceof Class<?> cls) {
            if (cls.getPackageName().startsWith("org.example.connectcg_be.dto") ||
                    cls.getPackageName().startsWith("org.example.connectcg_be.entity")) {
                return cls;
            }
        }
        return null;
    }

    private void registerSchema(Class<?> cls, Map<String, Schema> allSchemas) {
        if (cls == null || !cls.getPackageName().startsWith("org.example.connectcg_be")) return;
        if (!allSchemas.containsKey(cls.getSimpleName())) {
            Map<String, Schema> schemas = ModelConverters.getInstance().readAll(cls);
            allSchemas.putAll(schemas);
        }
    }
}
