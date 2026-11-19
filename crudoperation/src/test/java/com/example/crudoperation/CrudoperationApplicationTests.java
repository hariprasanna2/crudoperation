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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import com.jayway.jsonpath.JsonPath;
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

    private String token;

    @BeforeEach
    void clearStudents() throws Exception {
        repository.deleteAll();
        String response = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"test-admin\",\"password\":\"Admin-test-password-42\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        token = JsonPath.read(response, "$.accessToken");
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    @Test
    void createsReadsUpdatesAndDeletesStudent() throws Exception {
        String location = perform(post("/students")
                        .contentType(MediaType.APPLICATION_JSON).content(STUDENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Hari Prasanna"))
                .andReturn().getResponse().getHeader("Location");
        assertNotNull(location);
        perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("hari@example.com"));
        perform(get("/students"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        perform(put(location).contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Hari Updated","email":"updated@example.com","course":"Spring"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Hari Updated"))
                .andExpect(jsonPath("$.course").value("Spring"));
        perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("updated@example.com"));
        perform(delete(location)).andExpect(status().isNoContent());
        perform(get(location)).andExpect(status().isNotFound());
        assertEquals(0, repository.count());
    }

    @Test
    void returnsNotFoundForMissingStudents() throws Exception {
        perform(get("/students/999999")).andExpect(status().isNotFound());
        perform(put("/students/999999")
                        .contentType(MediaType.APPLICATION_JSON).content(STUDENT))
                .andExpect(status().isNotFound());
        perform(delete("/students/999999")).andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidStudentDetailsBeforeSaving() throws Exception {
        for (String invalid : new String[] {
                "{}",
                STUDENT.replace("Hari Prasanna", "   "),
                STUDENT.replace("hari@example.com", "not-an-email"),
                STUDENT.replace("Java", ""),
                STUDENT.replace("Hari Prasanna", "x".repeat(256))}) {
            perform(post("/students").contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(0, repository.count());
    }

    @Test
    void invalidUpdateLeavesExistingStudentUnchanged() throws Exception {
        Student saved = repository.save(new Student(null, "Hari", "hari@example.com", "Java"));
        perform(put("/students/" + saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(STUDENT.replace("hari@example.com", "bad-email")))
                .andExpect(status().isBadRequest());
        Student unchanged = repository.findById(saved.getId()).orElseThrow();
        assertEquals("Hari", unchanged.getName());
        assertEquals("hari@example.com", unchanged.getEmail());
    }

    @Test
    void rejectsMalformedJsonAndInvalidIds() throws Exception {
        perform(post("/students").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest());
        perform(get("/students/0")).andExpect(status().isBadRequest());
        perform(get("/students/-1")).andExpect(status().isBadRequest());
        perform(get("/students/not-a-number")).andExpect(status().isBadRequest());
        perform(delete("/students/0")).andExpect(status().isBadRequest());
        perform(put("/students/0").contentType(MediaType.APPLICATION_JSON).content(STUDENT))
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
        perform(post("/students").contentType(MediaType.APPLICATION_JSON)
                        .content(STUDENT.replace("Hari Prasanna", "  Hari Prasanna  ").replace("Java", " Java ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Hari Prasanna"))
                .andExpect(jsonPath("$.course").value("Java"));
    }
}
