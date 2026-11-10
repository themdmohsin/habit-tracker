package com.example.habittracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({HabitController.class, InfoController.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class HabitControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listIsInitiallyEmpty() throws Exception {
        mockMvc.perform(get("/habits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void postCreatesHabit() throws Exception {
        mockMvc.perform(post("/habits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Read\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Read"));
    }

    @Test
    void blankNameReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/habits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    private void createHabit() throws Exception {
        mockMvc.perform(post("/habits")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Read\"}"));
    }

    @Test
    void checkInMarksDoneTodayWithStreakOne() throws Exception {
        createHabit();
        mockMvc.perform(post("/habits/1/check-in"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doneToday").value(true))
                .andExpect(jsonPath("$.streak").value(1))
                .andExpect(jsonPath("$.totalCheckIns").value(1));
    }

    @Test
    void checkInTwiceSameDayKeepsStreakOne() throws Exception {
        createHabit();
        mockMvc.perform(post("/habits/1/check-in"));
        mockMvc.perform(post("/habits/1/check-in"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.streak").value(1))
                .andExpect(jsonPath("$.totalCheckIns").value(1));
    }

    @Test
    void deleteReturns204ThenNotFound() throws Exception {
        createHabit();
        mockMvc.perform(delete("/habits/1")).andExpect(status().isNoContent());
        mockMvc.perform(delete("/habits/1")).andExpect(status().isNotFound());
    }

    @Test
    void checkInUnknownIdReturns404() throws Exception {
        mockMvc.perform(post("/habits/99/check-in")).andExpect(status().isNotFound());
    }

    @Test
    void defaultCategoryIsGeneral() throws Exception {
        createHabit();
        mockMvc.perform(get("/habits"))
                .andExpect(jsonPath("$[0].category").value("General"))
                .andExpect(jsonPath("$[0].doneToday").value(false))
                .andExpect(jsonPath("$[0].streak").value(0));
    }

    @Test
    void infoReturnsPodAndVersion() throws Exception {
        mockMvc.perform(get("/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("3.0"))
                .andExpect(jsonPath("$.pod").isNotEmpty());
    }

    @Test
    void streakCountsConsecutiveDaysEndingTodayOrYesterday() {
        LocalDate today = LocalDate.of(2026, 11, 10);
        assertEquals(0, HabitController.streak(Set.of(), today));
        assertEquals(3, HabitController.streak(
                Set.of(today, today.minusDays(1), today.minusDays(2)), today));
        assertEquals(2, HabitController.streak(
                Set.of(today.minusDays(1), today.minusDays(2)), today));
        assertEquals(1, HabitController.streak(
                Set.of(today, today.minusDays(2)), today));
        assertEquals(0, HabitController.streak(Set.of(today.minusDays(2)), today));
    }
}