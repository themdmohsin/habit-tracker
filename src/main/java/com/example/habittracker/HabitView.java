package com.example.habittracker;

import java.time.LocalDate;

public record HabitView(long id, String name, String category, LocalDate createdAt,
                        boolean doneToday, int streak, int totalCheckIns) {
}
