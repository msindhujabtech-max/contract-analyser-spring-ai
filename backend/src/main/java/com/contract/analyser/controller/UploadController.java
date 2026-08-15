package com.contract.analyser.controller;

import com.contract.analyser.service.DocumentIngestionService;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class UploadController {

    private final DocumentIngestionService ingestionService;

    public UploadController(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Map<String, Object>> uploadDocument(@RequestPart("file") FilePart filePart) {
        return Mono.fromCallable(() -> ingestionService.ingestDocument(filePart))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
