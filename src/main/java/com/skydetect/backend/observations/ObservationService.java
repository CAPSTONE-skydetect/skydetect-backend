package com.skydetect.backend.observations;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
class ObservationService {
    private final ObservationRepository records;
    private final ObjectMapper json;

    ObservationService(ObservationRepository records, ObjectMapper json) {
        this.records = records;
        this.json = json;
    }

    Map<String, Object> save(long ownerId, String clientId, ObservationController.Request request) {
        if (!clientId.matches("[A-Za-z0-9_-]{1,100}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "기록 ID가 올바르지 않습니다.");
        }
        Map<String, Object> analysis = new LinkedHashMap<>(request.analysis());
        if (!List.of("bird", "drone", "uncertain").contains(java.util.Objects.toString(analysis.get("label"), ""))
                || !List.of("upload", "live").contains(java.util.Objects.toString(analysis.get("source"), ""))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "분석 결과와 출처가 필요합니다.");
        }
        // 사용자 입력으로 서버 관리 필드를 덮어쓰지 못하게 한다.
        analysis.keySet().removeAll(List.of("id", "serverSaved", "savedAt", "observationLocation", "observationMemo"));
        String snapshot = json.writeValueAsString(analysis);
        if (snapshot.getBytes(StandardCharsets.UTF_8).length > 1_000_000) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "분석 기록은 1MB 이내여야 합니다.");
        }
        String id = ownerId + "_" + clientId;
        ObservationRecord record = records.findById(id).orElseGet(() -> {
            ObservationRecord fresh = new ObservationRecord();
            fresh.id = id;
            fresh.clientId = clientId;
            fresh.ownerId = ownerId;
            Object observedAt = analysis.get("createdAt");
            fresh.createdAt = observedAt instanceof Number time && time.longValue() > 0
                    ? time.longValue() : System.currentTimeMillis();
            return fresh;
        });
        record.location = request.location() == null ? "" : request.location().strip();
        record.memo = request.memo() == null ? "" : request.memo().strip();
        record.analysisJson = snapshot;
        record.savedAt = System.currentTimeMillis();
        return view(records.save(record));
    }

    @Transactional(readOnly = true)
    List<Map<String, Object>> list(long ownerId) {
        return records.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream().map(this::view).toList();
    }

    void delete(long ownerId, String clientId) {
        // ID에 소유자를 포함해 다른 사용자의 기록은 조회·삭제할 수 없다.
        records.findById(ownerId + "_" + clientId).ifPresent(records::delete);
    }

    void clear(long ownerId) {
        records.deleteByOwnerId(ownerId);
    }

    private Map<String, Object> view(ObservationRecord record) {
        Map<String, Object> result = json.readValue(record.analysisJson, new TypeReference<LinkedHashMap<String, Object>>() {});
        result.put("id", record.clientId);
        result.put("createdAt", record.createdAt);
        result.put("savedAt", record.savedAt);
        result.put("observationLocation", record.location);
        result.put("observationMemo", record.memo);
        result.put("serverSaved", true);
        return result;
    }
}
