package com.example.crudoperation.controller;


import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.example.crudoperation.entity.Student;
import com.example.crudoperation.repository.StudentRepository;

@RestController
@RequestMapping("/students")
public class StudentController {

    @Autowired
    private StudentRepository repository;


    @PostMapping
    public Student createStudent(@RequestBody Student student) {
        return repository.save(student);
    }


    @GetMapping
    public List<Student> getAllStudents() {
        return repository.findAll();
    }


    @GetMapping("/{id}")
    public Optional<Student> getStudentById(
            @PathVariable Long id) {
        return repository.findById(id);
    }


    @PutMapping("/{id}")
    public Student updateStudent(
            @PathVariable Long id,
            @RequestBody Student student) {

        Student existingStudent = repository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Student not found"));

        existingStudent.setName(student.getName());
        existingStudent.setEmail(student.getEmail());
        existingStudent.setCourse(student.getCourse());

        return repository.save(existingStudent);
    }


    @DeleteMapping("/{id}")
    public String deleteStudent(@PathVariable Long id) {

        if (!repository.existsById(id)) {
            return "Student not found";
        }

        repository.deleteById(id);

        return "Student deleted successfully";
    }
}