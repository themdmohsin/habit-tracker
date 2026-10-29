package com.example.habittracker;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/habits")
public class HabitController {

    private final List<Habit> habits = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong();

    @GetMapping
    public List<Habit> list() {
        return habits;
    }

    @PostMapping
    public ResponseEntity<Habit> create(@RequestBody Map<String, String> body) {
        String name = body == null ? null : body.get("name");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Habit habit = new Habit(sequence.incrementAndGet(), name);
        habits.add(habit);
        return ResponseEntity.status(HttpStatus.CREATED).body(habit);
    }
}
