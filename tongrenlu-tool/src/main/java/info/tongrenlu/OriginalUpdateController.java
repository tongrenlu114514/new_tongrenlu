package info.tongrenlu;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/original-update")
@Slf4j
public class OriginalUpdateController {

    private final OriginalUpdateJob job;

    @GetMapping("/status")
    public OriginalUpdateJob.JobStatus status() {
        return job.status();
    }

    @PostMapping("/trigger")
    public Map<String, Boolean> trigger() {
        job.trigger();
        return Map.of("triggered", true);
    }

    @PostMapping("/pause")
    public Map<String, Boolean> pause() {
        job.pause();
        return Map.of("paused", true);
    }

    @PostMapping("/resume")
    public Map<String, Boolean> resume() {
        job.resume();
        return Map.of("resumed", true);
    }
}
