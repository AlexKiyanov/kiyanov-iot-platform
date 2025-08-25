package com.github.alexkiyanov.iotplatform.controller;

import com.github.alexkiyanov.iotplatform.service.EventProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/producer")
@RequiredArgsConstructor
public class EventProducerController {

    private final EventProducerService eventProducerService;

    @PostMapping("/start")
    public ResponseEntity<String> startProducer() {
        log.info("Запуск продюсера событий");
        return ResponseEntity.ok("Продюсер событий запущен");
    }

    @PostMapping("/stop")
    public ResponseEntity<String> stopProducer() {
        log.info("Остановка продюсера событий");
        return ResponseEntity.ok("Продюсер событий остановлен");
    }

    @GetMapping("/status")
    public ResponseEntity<String> getStatus() {
        return ResponseEntity.ok("Продюсер событий работает");
    }

    @PostMapping("/send-single")
    public ResponseEntity<String> sendSingleEvent() {
        try {
            eventProducerService.produceEvent();
            return ResponseEntity.ok("Событие отправлено");
        } catch (Exception e) {
            log.error("Ошибка при отправке события: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body("Ошибка при отправке события: " + e.getMessage());
        }
    }
}
