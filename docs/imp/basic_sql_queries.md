# SQL Interview Jump-Start Cheat Sheet

A quick, practical reference for the SQL concepts asked most in interviews —
joins, aggregation, window functions, CTEs, and null handling.

---

## Table of Contents
1. [The Basic Joins](#1-the-basic-joins)
2. [Essential Interview Queries & Concepts](#2-essential-interview-queries--concepts)
   - [GROUP BY & HAVING](#a-group-by--having)
   - [Window Functions — Ranking](#b-window-functions--ranking)
   - [Window Functions — LAG & LEAD](#c-window-functions--lag--lead)
   - [Common Table Expressions (CTEs)](#d-common-table-expressions-ctes)
   - [Self Join](#e-self-join)
   - [UNION vs UNION ALL](#f-union-vs-union-all)
   - [COALESCE](#g-coalesce)
   - [CASE WHEN](#h-case-when)

---

# 1. The Basic Joins

### Sample Setup

**Users** table (Left)

| id | name    |
|----|---------|
| 1  | Alice   |
| 2  | Bob     |
| 3  | Charlie |

**Orders** table (Right)

| order_id | user_id | product |
|----------|---------|---------|
| 101      | 1       | Laptop  |
| 102      | 2       | Phone   |
| 103      | 4       | Book    |

---

### A. INNER JOIN

Returns **only matching records** present in BOTH tables.

```sql
SELECT Users.name, Orders.product
FROM Users
INNER JOIN Orders ON Users.id = Orders.user_id;
```

**Result:**

| name  | product |
|-------|---------|
| Alice | Laptop  |
| Bob   | Phone   |

---

### B. LEFT OUTER JOIN (LEFT JOIN)

Returns **ALL records from the left table** + matching records from the right.
Unmatched right-side rows return `NULL`.

```sql
SELECT Users.name, Orders.product
FROM Users
LEFT JOIN Orders ON Users.id = Orders.user_id;
```

**Result:**

| name    | product |
|---------|---------|
| Alice   | Laptop  |
| Bob     | Phone   |
| Charlie | NULL    |

---

### C. RIGHT OUTER JOIN (RIGHT JOIN)

Returns **ALL records from the right table** + matching records from the left.

```sql
SELECT Users.name, Orders.product
FROM Users
RIGHT JOIN Orders ON Users.id = Orders.user_id;
```

**Result:**

| name  | product |
|-------|---------|
| Alice | Laptop  |
| Bob   | Phone   |
| NULL  | Book    |

---

### D. FULL OUTER JOIN

Returns **ALL records from both tables**, filling missing matches with `NULL`.

```sql
SELECT Users.name, Orders.product
FROM Users
FULL OUTER JOIN Orders ON Users.id = Orders.user_id;
```

**Result:**

| name    | product |
|---------|---------|
| Alice   | Laptop  |
| Bob     | Phone   |
| Charlie | NULL    |
| NULL    | Book    |

---

### E. Key Interview Use Case — Finding Unmatched Data

**Find all users who have NEVER placed an order** (LEFT JOIN + `WHERE IS NULL`):

```sql
SELECT Users.name
FROM Users
LEFT JOIN Orders ON Users.id = Orders.user_id
WHERE Orders.order_id IS NULL;
```

**Result:**

| name    |
|---------|
| Charlie |

> **Interview tip:** "LEFT JOIN + WHERE right_key IS NULL" is the standard pattern
> to find rows in table A with no match in table B.

---

# 2. Essential Interview Queries & Concepts

## A. GROUP BY & HAVING

Filtering aggregated results.

> **WHERE** filters rows *before* grouping. **HAVING** filters groups *after* aggregation.

**Find departments with total salary expenditure greater than $200,000:**

```sql
SELECT department_id, SUM(salary) AS total_salary
FROM employees
WHERE status = 'Active'      -- filters individual rows first
GROUP BY department_id
HAVING SUM(salary) > 200000; -- filters grouped results
```

---

## B. Window Functions — Ranking

Ranking rows within groups. Key difference between the three:

| Function       | Behaviour on ties | Example sequence |
|----------------|-------------------|------------------|
| `ROW_NUMBER()` | Always sequential | 1, 2, 3, 4       |
| `RANK()`       | Skips after ties  | 1, 2, 2, 4       |
| `DENSE_RANK()` | No gaps on ties   | 1, 2, 2, 3       |

**Get the top 3 highest-paid employees in each department:**

```sql
WITH RankedEmployees AS (
    SELECT name, department_id, salary,
           DENSE_RANK() OVER (
               PARTITION BY department_id
               ORDER BY salary DESC
           ) AS salary_rank
    FROM employees
)
SELECT *
FROM RankedEmployees
WHERE salary_rank <= 3;
```

---

## C. Window Functions — LAG & LEAD

Comparing a row to the previous or next row.

- **`LAG()`** → looks at the *previous* row
- **`LEAD()`** → looks at the *next* row

**Find month-over-month revenue growth:**

```sql
SELECT month, revenue,
       LAG(revenue, 1) OVER (ORDER BY month) AS previous_month_revenue,
       revenue - LAG(revenue, 1) OVER (ORDER BY month) AS monthly_variance
FROM monthly_sales;
```

---

## D. Common Table Expressions (CTEs)

A named, temporary result set that makes complex queries readable.

**Find employees earning more than the company average salary:**

```sql
WITH CompanyAverage AS (
    SELECT AVG(salary) AS avg_sal FROM employees
)
SELECT name, salary
FROM employees, CompanyAverage
WHERE salary > CompanyAverage.avg_sal;
```

> **Interview tip:** CTEs (`WITH ...`) improve readability over nested subqueries
> and can be referenced multiple times in the same query.

---

## E. Self Join

Joining a table to itself — perfect for hierarchies (employee → manager).

**List each employee alongside their manager:**

```sql
SELECT emp.name AS Employee, mgr.name AS Manager
FROM employees emp
LEFT JOIN employees mgr ON emp.manager_id = mgr.id;
```

---

## F. UNION vs UNION ALL

| Operator    | Behaviour                        | Speed   |
|-------------|----------------------------------|---------|
| `UNION ALL` | Combines rows, keeps duplicates  | Faster  |
| `UNION`     | Combines rows, removes duplicates (distinct check) | Slower |

```sql
SELECT email FROM customers
UNION ALL
SELECT email FROM subscribers;
```

> **Interview tip:** Use `UNION ALL` unless you specifically need duplicates removed —
> it avoids the extra sort/distinct step.

---

## G. COALESCE (Handling NULLs)

Returns the **first non-null value** from a list of arguments.

```sql
SELECT name,
       COALESCE(phone, email, 'No Contact Provided') AS primary_contact
FROM users;
```

**Logic:** if `phone` is null, try `email`; if that's null too, use the literal string.

---

## H. CASE WHEN (Conditional If-Else Logic)

Categorise rows into buckets inline.

```sql
SELECT name, salary,
       CASE
           WHEN salary >= 100000 THEN 'High Tier'
           WHEN salary >= 50000  THEN 'Mid Tier'
           ELSE 'Entry Tier'
       END AS salary_bracket
FROM employees;
```

---

# Quick Revision Table

| Concept        | Purpose                                   |
|----------------|-------------------------------------------|
| INNER JOIN     | Only matching rows in both tables         |
| LEFT JOIN      | All left rows + matches (NULL if none)    |
| RIGHT JOIN     | All right rows + matches                  |
| FULL OUTER     | All rows from both, NULLs where unmatched |
| LEFT + IS NULL | Rows in A with no match in B              |
| GROUP BY       | Aggregate rows into groups                |
| HAVING         | Filter groups after aggregation           |
| ROW_NUMBER     | Sequential rank, no ties                  |
| RANK           | Skips numbers after ties                  |
| DENSE_RANK     | No gaps after ties                        |
| LAG / LEAD     | Compare to previous / next row            |
| CTE (WITH)     | Named temp result for readability         |
| SELF JOIN      | Table joined to itself (hierarchies)      |
| UNION ALL      | Merge rows, keep duplicates (fast)        |
| UNION          | Merge rows, remove duplicates             |
| COALESCE       | First non-null value                      |
| CASE WHEN      | Inline if-else categorisation             |

---

*Master joins + GROUP BY/HAVING + window functions — they cover most SQL interview questions.*
