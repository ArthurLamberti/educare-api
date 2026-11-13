package br.com.educare.api.lesson

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import java.time.Instant

/** Scoped to the current school by Hibernate (see TenantOwnedEntity). */
interface LessonRepository : JpaRepository<Lesson, Long>, JpaSpecificationExecutor<Lesson> {

    @EntityGraph(attributePaths = ["teacher", "student", "student.user"])
    override fun findAll(spec: Specification<Lesson>, pageable: Pageable): Page<Lesson>

    /** Overlap with a scheduled lesson of the teacher; `excludeId` skips the lesson being moved. */
    @Query(
        """
        select count(l) > 0 from Lesson l
        where l.status = br.com.educare.api.lesson.LessonStatus.SCHEDULED
          and l.teacher.id = :teacherId and l.id <> :excludeId
          and l.startsAt < :endsAt and l.endsAt > :startsAt
        """,
    )
    fun teacherBusy(teacherId: Long, startsAt: Instant, endsAt: Instant, excludeId: Long): Boolean

    @Query(
        """
        select count(l) > 0 from Lesson l
        where l.status = br.com.educare.api.lesson.LessonStatus.SCHEDULED
          and l.student.id = :studentId and l.id <> :excludeId
          and l.startsAt < :endsAt and l.endsAt > :startsAt
        """,
    )
    fun studentBusy(studentId: Long, startsAt: Instant, endsAt: Instant, excludeId: Long): Boolean

    /** Scheduled lessons of a series starting at or after [from], in order. */
    @Query(
        """
        select l from Lesson l
        where l.series.id = :seriesId and l.status = br.com.educare.api.lesson.LessonStatus.SCHEDULED and l.startsAt >= :from
        order by l.startsAt
        """,
    )
    fun findScheduledInSeries(seriesId: Long, from: Instant): List<Lesson>
}

/** Scoped to the current school by Hibernate (see TenantOwnedEntity). */
interface LessonSeriesRepository : JpaRepository<LessonSeries, Long> {

    @EntityGraph(attributePaths = ["teacher", "student", "student.user"])
    fun findByEndedAtIsNullOrderByCreatedAt(): List<LessonSeries>

    /** Active series that do not yet have lessons up to [date]. */
    fun findByEndedAtIsNullAndGeneratedUntilBefore(date: java.time.LocalDate): List<LessonSeries>
}
