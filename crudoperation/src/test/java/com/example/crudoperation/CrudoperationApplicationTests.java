package com.example.crudoperation;

import com.example.crudoperation.entity.Student;
import com.example.crudoperation.repository.StudentRepository;
import com.example.crudoperation.service.StudentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CrudoperationApplicationTests {
    @Autowired private MockMvc mvc;
    @Autowired private StudentRepository repository;
    @Autowired private StudentService service;

    private static final String STUDENT = """
            {"name":"Hari Prasanna","email":"hari@example.com","course":"Java"}
            """;

    @BeforeEach
    void clearStudents() {
        repository.deleteAll();
    }

    @Test
    void createsReadsUpdatesAndDeletesStudent() throws Exception {
        String location = mvc.perform(post("/students")
                        .contentType(MediaType.APPLICATION_JSON).content(STUDENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Hari Prasanna"))
                .andReturn().getResponse().getHeader("Location");
        assertNotNull(location);
        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("hari@example.com"));
        mvc.perform(get("/students"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(put(location).contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Hari Updated","email":"updated@example.com","course":"Spring"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Hari Updated"))
                .andExpect(jsonPath("$.course").value("Spring"));
        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("updated@example.com"));
        mvc.perform(delete(location)).andExpect(status().isNoContent());
        mvc.perform(get(location)).andExpect(status().isNotFound());
        assertEquals(0, repository.count());
    }

    @Test
    void returnsNotFoundForMissingStudents() throws Exception {
        mvc.perform(get("/students/999999")).andExpect(status().isNotFound());
        mvc.perform(put("/students/999999")
                        .contentType(MediaType.APPLICATION_JSON).content(STUDENT))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/students/999999")).andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidStudentDetailsBeforeSaving() throws Exception {
        for (String invalid : new String[] {
                "{}",
                STUDENT.replace("Hari Prasanna", "   "),
                STUDENT.replace("hari@example.com", "not-an-email"),
                STUDENT.replace("Java", ""),
                STUDENT.replace("Hari Prasanna", "x".repeat(256))}) {
            mvc.perform(post("/students").contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(0, repository.count());
    }

    @Test
    void invalidUpdateLeavesExistingStudentUnchanged() throws Exception {
        Student saved = repository.save(new Student(null, "Hari", "hari@example.com", "Java"));
        mvc.perform(put("/students/" + saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(STUDENT.replace("hari@example.com", "bad-email")))
                .andExpect(status().isBadRequest());
        Student unchanged = repository.findById(saved.getId()).orElseThrow();
        assertEquals("Hari", unchanged.getName());
        assertEquals("hari@example.com", unchanged.getEmail());
    }

    @Test
    void rejectsMalformedJsonAndInvalidIds() throws Exception {
        mvc.perform(post("/students").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/students/0")).andExpect(status().isBadRequest());
        mvc.perform(get("/students/-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/students/not-a-number")).andExpect(status().isBadRequest());
        mvc.perform(delete("/students/0")).andExpect(status().isBadRequest());
        mvc.perform(put("/students/0").contentType(MediaType.APPLICATION_JSON).content(STUDENT))
                .andExpect(status().isBadRequest());
        assertEquals(0, repository.count());
    }

    @Test
    void createDoesNotOverwriteStudentWhenAnIdIsSupplied() {
        Student original = repository.save(new Student(null, "Original", "original@example.com", "Java"));
        Student created = service.saveStudent(new Student(original.getId(), "New", "new@example.com", "Spring"));
        assertNotEquals(original.getId(), created.getId());
        assertEquals(2, repository.count());
        assertEquals("Original", repository.findById(original.getId()).orElseThrow().getName());
    }

    @Test
    void trimsNamesAndCourses() throws Exception {
        mvc.perform(post("/students").contentType(MediaType.APPLICATION_JSON)
                        .content(STUDENT.replace("Hari Prasanna", "  Hari Prasanna  ").replace("Java", " Java ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Hari Prasanna"))
                .andExpect(jsonPath("$.course").value("Java"));
    }
}
