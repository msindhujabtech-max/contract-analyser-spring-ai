# Hibernate / JPA Entity Mapping Examples

Ready-to-use, interview-friendly examples of the three relationship mappings —
with the resulting database tables and the key rules to remember.

---

## Table of Contents
1. [One-to-One (User & UserProfile)](#1-one-to-one-mapping)
2. [One-to-Many / Many-to-One (Department & Employee)](#2-one-to-many--many-to-one-mapping)
3. [Many-to-Many (Student & Course)](#3-many-to-many-mapping)
4. [Quick Rules Summary](#quick-rules-summary)

---

## Quick concepts before the code

| Term | Meaning |
|------|---------|
| **Owning side** | The entity that holds the foreign key / declares `@JoinColumn` or `@JoinTable` |
| **Inverse side** | The other entity; uses `mappedBy` to point back to the owning field |
| **`mappedBy`** | Says "the relationship is already mapped by this field on the other side" — no extra FK column |
| **`cascade`** | Propagates operations (save/delete) from parent to child |
| **`orphanRemoval`** | Deletes a child when it's removed from the parent's collection |
| **`fetch`** | `LAZY` (load on access) or `EAGER` (load immediately) |

---

# 1. One-to-One Mapping

**Scenario:** Each `User` has exactly one `UserProfile`, and vice versa.

### Resulting tables

```
users                          user_profiles
+----+----------+-----------+  +----+---------+--------------+
| id | username | profile_id|  | id | address | phone_number |
+----+----------+-----------+  +----+---------+--------------+
| 1  | alice    | 10        |  | 10 | Chennai | 99999-88888  |
+----+----------+-----------+  +----+---------+--------------+
         (FK profile_id points to user_profiles.id)
```

### User (Owning Side — has the foreign key)

```java
package com.example.model;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "profile_id", referencedColumnName = "id")  // FK lives here
    private UserProfile profile;

    // getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public UserProfile getProfile() { return profile; }
    public void setProfile(UserProfile profile) { this.profile = profile; }
}
```

### UserProfile (Inverse Side — uses `mappedBy`)

```java
package com.example.model;

import jakarta.persistence.*;

@Entity
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String address;
    private String phoneNumber;

    @OneToOne(mappedBy = "profile")   // points to the 'profile' field in User
    private User user;

    // getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
}
```

> **Key point:** The side with `@JoinColumn` owns the FK. The other side uses
> `mappedBy` and gets no extra column.

---

# 2. One-to-Many / Many-to-One Mapping

**Scenario:** One `Department` has many `Employee`s; each `Employee` belongs to one `Department`.

### Resulting tables

```
departments               employees
+----+-------------+       +----+-------+---------------+
| id | name        |       | id | name  | department_id |
+----+-------------+       +----+-------+---------------+
| 1  | Engineering |       | 1  | Alice | 1             |
+----+-------------+       | 2  | Bob   | 1             |
                           +----+-------+---------------+
              (FK department_id lives on the "many" side)
```

### Department (The "One" Side)

```java
package com.example.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "departments")
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @OneToMany(mappedBy = "department", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Employee> employees = new ArrayList<>();

    // Helper methods keep BOTH sides of the relationship in sync
    public void addEmployee(Employee employee) {
        employees.add(employee);
        employee.setDepartment(this);
    }

    public void removeEmployee(Employee employee) {
        employees.remove(employee);
        employee.setDepartment(null);
    }

    // getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<Employee> getEmployees() { return employees; }
    public void setEmployees(List<Employee> employees) { this.employees = employees; }
}
```

### Employee (The "Many" Side — owns the foreign key)

```java
package com.example.model;

import jakarta.persistence.*;

@Entity
@Table(name = "employees")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne(fetch = FetchType.LAZY)     // LAZY: load department only when accessed
    @JoinColumn(name = "department_id")     // FK column on employees table
    private Department department;

    // getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Department getDepartment() { return department; }
    public void setDepartment(Department department) { this.department = department; }
}
```

> **Key points:**
> - The **"many"** side (`Employee`) always owns the FK via `@JoinColumn`.
> - The **"one"** side (`Department`) uses `mappedBy = "department"`.
> - `orphanRemoval = true` deletes an employee removed from the department's list.
> - The `addEmployee`/`removeEmployee` helpers keep both sides consistent (important!).

---

# 3. Many-to-Many Mapping

**Scenario:** A `Student` takes many `Course`s; a `Course` has many `Student`s.

### Resulting tables (note the join table)

```
students          courses            student_course (join table)
+----+-------+    +----+---------+    +------------+-----------+
| id | name  |    | id | title   |    | student_id | course_id |
+----+-------+    +----+---------+    +------------+-----------+
| 1  | Alice |    | 1  | Math    |    |     1      |     1     |
| 2  | Bob   |    | 2  | Java    |    |     1      |     2     |
+----+-------+    +----+---------+    |     2      |     1     |
                                      +------------+-----------+
```

### Student (Owning Side — declares the join table)

```java
package com.example.model;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "students")
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToMany(cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @JoinTable(
        name = "student_course",                          // join table name
        joinColumns = @JoinColumn(name = "student_id"),    // FK to this entity
        inverseJoinColumns = @JoinColumn(name = "course_id")  // FK to the other entity
    )
    private Set<Course> courses = new HashSet<>();

    // Helper methods keep both sides in sync
    public void addCourse(Course course) {
        this.courses.add(course);
        course.getStudents().add(this);
    }

    public void removeCourse(Course course) {
        this.courses.remove(course);
        course.getStudents().remove(this);
    }

    // getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Set<Course> getCourses() { return courses; }
    public void setCourses(Set<Course> courses) { this.courses = courses; }
}
```

### Course (Inverse Side — uses `mappedBy`)

```java
package com.example.model;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "courses")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @ManyToMany(mappedBy = "courses")   // 'courses' = field name in Student
    private Set<Student> students = new HashSet<>();

    // getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Set<Student> getStudents() { return students; }
    public void setStudents(Set<Student> students) { this.students = students; }
}
```

> **Key points:**
> - `@ManyToMany` needs a **join table** declared with `@JoinTable` on the owning side.
> - The inverse side uses `mappedBy`.
> - Use `Set` (not `List`) to prevent duplicate associations.

---

# Quick Rules Summary

| Relationship | Owning side (FK / JoinTable) | Inverse side (`mappedBy`) | DB structure |
|--------------|------------------------------|----------------------------|--------------|
| **One-to-One** | side with `@JoinColumn` | `@OneToOne(mappedBy=...)` | FK column on owning table |
| **One-to-Many / Many-to-One** | the "many" side (`@ManyToOne` + `@JoinColumn`) | the "one" side (`@OneToMany(mappedBy=...)`) | FK column on the "many" table |
| **Many-to-Many** | side with `@JoinTable` | `@ManyToMany(mappedBy=...)` | separate join table with 2 FKs |

**Universal tips:**
- The **owning side** always has the foreign key / join table.
- The **inverse side** always uses `mappedBy`.
- Add helper methods (`addX`/`removeX`) to keep both sides in sync.
- Prefer `FetchType.LAZY` to avoid loading unneeded data (watch for N+1).
- Use `Set` for many-to-many to avoid duplicates.

---

*These three mappings cover the vast majority of JPA relationship interview questions.*
