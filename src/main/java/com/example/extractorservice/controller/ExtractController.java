package com.example.extractorservice.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.extractorservice.controller.request.ExtractWorkflowRequest;
import com.example.extractorservice.service.ExtractorService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping({ "/api", "/api/v1" })
@Tag(name = "Extractor API", description = "API to manage bucket objects (upload, download, update, delete, list, share)")
public class ExtractController {

    private final ExtractorService extractorService;

    public ExtractController(ExtractorService extractorService) {
        this.extractorService = extractorService;
    }

    /**
     * Upload an object to the bucket.
     * 
     * CURL sample :
     * curl -X POST "http://localhost:8080/api/objects" \
     * -F "remote=my-bucket/path/in/bucket/file.txt" \
     * -F "file=@/path/to/local/file.txt"
     * 
     * @param remote - destination path in the bucket (bucket/key)
     * @param file   - local file content sent as multipart
     */
    @Operation(summary = "Upload an object")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Object created"),
            @ApiResponse(responseCode = "400", description = "Invalid remote path"),
            @ApiResponse(responseCode = "404", description = "Bucket/object not found")
    })
    @PostMapping(value = "/objects", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public void upload(@RequestParam String remote, @RequestPart("file") MultipartFile file) {
        try {
            extractorService.upload(remote, file.getBytes());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read uploaded file", e);
        }
    }

    /**
     * Download an object from the bucket.
     * 
     * CURL sample :
     * curl -X GET
     * "http://localhost:8080/api/objects/download?remote=my-bucket/path/in/bucket/file.txt"
     * --output downloaded-file.txt
     * 
     * @param remote - source path in the bucket (bucket/key)
     * @return raw object bytes
     */
    @Operation(summary = "Download an object", description = "Downloads object content as binary data")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Object downloaded successfully"),
            @ApiResponse(responseCode = "404", description = "Object not found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    @GetMapping(value = "/objects/download", params = "remote", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public byte[] download(
            @Parameter(description = "Path of the object in the bucket", example = "bucket/path/in/bucket/file.txt", required = true) @RequestParam String remote) {
        return extractorService.download(remote);
    }

    /**
     * Update an existing object in the bucket.
     * 
     * CURL sample :
     * curl -X PUT "http://localhost:8080/api/objects" \
     * -F "remote=my-bucket/path/in/bucket/file.txt" \
     * -F "file=@/path/to/local/updated-file.txt"
     * 
     * @param remote - object path in the bucket to replace (bucket/key)
     * @param file   - new file content sent as multipart
     */
    @Operation(summary = "Update an object", description = "Replaces an existing object content in the bucket")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Object updated successfully"),
            @ApiResponse(responseCode = "404", description = "Object not found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    @PutMapping(value = "/objects", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@RequestParam String remote, @RequestPart("file") MultipartFile file) {
        try {
            extractorService.update(remote, file.getBytes());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read uploaded file", e);
        }
    }

    /**
     * Delete an object or a prefix from the bucket.
     * 
     * CURL sample :
     * curl -X DELETE
     * "http://localhost:8080/api/objects?remote=my-bucket/path/in/bucket/file.txt&recursive=false"
     * 
     * @param remote    - object or prefix path in the bucket (bucket/key or
     *                  bucket/prefix)
     * @param recursive - whether to delete recursively for prefixes
     */
    @Operation(summary = "Delete an object or prefix", description = "Deletes an object or prefix from the bucket")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deletion completed successfully"),
            @ApiResponse(responseCode = "404", description = "Object not found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    @DeleteMapping(value = "/objects", params = "remote")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @Parameter(description = "Path of the object or prefix in the bucket", example = "bucket/path/in/bucket/file.txt", required = true) @RequestParam String remote,
            @Parameter(description = "Whether the deletion should be recursive (for prefixes)", example = "false") @RequestParam(defaultValue = "false") boolean recursive) {
        extractorService.delete(remote, recursive);
    }

    /**
     * List objects under a prefix in the bucket.
     * 
     * CURL sample :
     * curl -X GET
     * "http://localhost:8080/api/objects?path=my-bucket/path/in/bucket/"
     * 
     * @param path - prefix path in the bucket (bucket/prefix)
     * @return list of object keys
     */
    @Operation(summary = "List objects in a prefix", description = "Returns a list of objects located under a prefix")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of objects returned successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    @GetMapping(value = "/objects", params = "path")
    public List<String> list(
            @Parameter(description = "Prefix path in the bucket", example = "bucket/path/in/bucket", required = true) @RequestParam String path) {
        return extractorService.list(path);
    }

    /**
     * Generate a temporary shared URL for an object.
     * 
     * CURL sample :
     * curl -X POST
     * "http://localhost:8080/api/objects/share?remote=my-bucket/path/in/bucket/file.txt&expirationTime=3600"
     * 
     * @param remote         - object path in the bucket (bucket/key)
     * @param expirationTime - link expiration time in seconds
     * @return shared URL
     */
    @Operation(summary = "Share an object", description = "Generates a temporary public URL to access an object")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Shared URL generated successfully"),
            @ApiResponse(responseCode = "404", description = "Object not found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    @PostMapping(value = "/objects/share", params = { "remote", "expirationTime" })
    public String share(
            @Parameter(description = "Path of the object in the bucket", example = "bucket/path/in/bucket/file.txt", required = true) @RequestParam String remote,
            @Parameter(description = "Expiration time in seconds", example = "3600") @RequestParam int expirationTime) {
        return extractorService.share(remote, expirationTime);
    }

    /**
     * Execute the complete sprint 1 extract workflow:
     * download a file from a public or pre-signed URL,
     * upload the same bytes to the bucket,
     * then return a new shared URL.
     *
     * @param request request body containing the public or pre-signed source
     *                URL
     * @return shared URL for the uploaded file
     */
    @Operation(summary = "Execute the complete sprint 1 extract workflow", description = "Downloads a file from a public or pre-signed URL, uploads it to the bucket, then returns the new shared URL")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Workflow completed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters", content = @Content),
            @ApiResponse(responseCode = "404", description = "Source object not found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    @PostMapping(value = "/workflows/extract", consumes = MediaType.APPLICATION_JSON_VALUE)
    public String executeWorkflow(@RequestBody ExtractWorkflowRequest request) {
        if (request == null || request.url() == null || request.url().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "url is required");
        }

        return extractorService.executeWorkflow(request.url());
    }
}
