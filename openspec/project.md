# Project Context

## Purpose
A Spring Boot-based multi-module Maven project focused on sharing and managing fan-created content (music, articles). The platform provides tools for content creators to upload, organize, and share their creative works with the community.

## Tech Stack
- **Backend Framework**: Spring Boot 3.4.3
- **Database**: MySQL 8.2.0
- **ORM Framework**: MyBatis Plus 3.5.11
- **Java Version**: 21/23
- **Build Tool**: Maven 3.6+
- **API Documentation**: Springdoc OpenAPI (Swagger UI at /tongrenlu/swagger-ui.html)

## Project Conventions

### Code Style
- Packages follow `info.tongrenlu` namespace
- UTF-8 encoding throughout the project
- Lombok extensively used for reducing boilerplate code
- Frontend resources split by modules, reusable components preferred, files <200 lines

### Architecture Patterns
- **Three-module Maven structure**:
  - `tongrenlu-web` - Web application layer (Spring Boot Web)
  - `tongrenlu-dao` - Data access layer (MyBatis domain models and mappers)
  - `tongrenlu-tool` - Utility module (data parsing and processing tools)
- **Layered architecture**: Controller → Manager → Service → DAO
- **Soft deletion pattern**: All entities use `del_flg` field (`0=active, 1=deleted`)
- **Unified timestamp management**: `upd_date` field for all time tracking

### Testing Strategy
- Currently no test files present (TODO: add comprehensive test suite)
- Recommended approach:
  - Unit tests for Service and Manager layers
  - Integration tests for Controller layer
  - H2 in-memory database for testing

### Git Workflow
- Single branch workflow: `master` as main branch
- Conventional commit messages (implied from recent commits)
- Chinese documentation partially mixed with English code

## Domain Context

### Content Types
- **Articles**: Creative writings, stories, blog posts
- **Music**: Audio tracks organized by artists and albums
- **Artists**: Creators and music performers

### Database Schema Conventions
- Table prefixes: `m_` (main tables), `r_` (relationship tables), `v_` (views)
- Example tables: `m_article`, `m_track`, `m_artist`, `m_tag`, `r_article_tag`
- Public fields: `upd_date` (update timestamp), `del_flg` (soft deletion)

### File Organization
- SQL files organized by date: `/sql/YYYYMMDD/`
- Music parsing tools support: artist directories (`+` prefix), album info, disc identifiers, MP3 files

## Important Constraints
- **Database password security**: Must not commit hardcoded passwords to version control
- **Port configuration**: Application runs on port 8443 with context-path `/tongrenlu`
- **MySQL version requirement**: 8.2+ required
- **Java version**: Officially Java 21, but Java 23 recommended for development

## External Dependencies
- **Music source integrations**: CloudMusic (NetEase Cloud Music) integration via `CloudMusicAlbum.java`
- **File system**: Local file system for music file storage and parsing
- **No external APIs currently**: Self-contained application architecture
