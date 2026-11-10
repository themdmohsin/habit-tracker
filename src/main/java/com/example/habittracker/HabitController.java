package com.example.habittracker;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/habits")
public class HabitController {

    private final List<Habit> habits = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong();
    private final Clock clock = Clock.systemDefaultZone();

    @GetMapping
    public List<HabitView> list() {
        LocalDate today = LocalDate.now(clock);
        return habits.stream().map(h -> view(h, today)).toList();
    }

    @PostMapping
    public ResponseEntity<HabitView> create(@RequestBody Map<String, String> body) {
        String name = body == null ? null : body.get("name");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String category = body.get("category");
        if (category == null || category.isBlank()) {
            category = "General";
        }
        LocalDate today = LocalDate.now(clock);
        Habit habit = new Habit(sequence.incrementAndGet(), name.trim(), category.trim(), today);
        habits.add(habit);
        return ResponseEntity.status(HttpStatus.CREATED).body(view(habit, today));
    }

    @PostMapping("/{id}/check-in")
    public ResponseEntity<HabitView> checkIn(@PathVariable long id) {
        Optional<Habit> found = find(id);
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        LocalDate today = LocalDate.now(clock);
        found.get().getCheckIns().add(today);
        return ResponseEntity.ok(view(found.get(), today));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        Optional<Habit> found = find(id);
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        habits.remove(found.get());
        return ResponseEntity.noContent().build();
    }

    private Optional<Habit> find(long id) {
        return habits.stream().filter(h -> h.getId() == id).findFirst();
    }

    private static HabitView view(Habit h, LocalDate today) {
        Set<LocalDate> dates = h.getCheckIns();
        return new HabitView(h.getId(), h.getName(), h.getCategory(), h.getCreatedAt(),
                dates.contains(today), streak(dates, today), dates.size());
    }

    /** Consecutive days ending today, or ending yesterday if today is not yet done. */
    static int streak(Set<LocalDate> checkIns, LocalDate today) {
        LocalDate day = checkIns.contains(today) ? today : today.minusDays(1);
        int count = 0;
        while (checkIns.contains(day)) {
            count++;
            day = day.minusDays(1);
        }
        return count;
    }
}
