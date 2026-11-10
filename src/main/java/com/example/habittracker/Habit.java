package com.example.habittracker;

import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Habit {

    private final long id;
    private final String name;
    private final String category;
    private final LocalDate createdAt;
    private final Set<LocalDate> checkIns = ConcurrentHashMap.newKeySet();

    public Habit(long id, String name, String category, LocalDate createdAt) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public LocalDate getCreatedAt() {
        return createdAt;
    }

    public Set<LocalDate> getCheckIns() {
        return checkIns;
    }
}
