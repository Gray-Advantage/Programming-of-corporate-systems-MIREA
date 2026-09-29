-- Databases of the backend services on the same PostgreSQL server: each service owns one and creates
-- its tables with Flyway. Runs before schema.sql, which fills the console client's database (teremok).
CREATE DATABASE catalog;
CREATE DATABASE text_work_content;
