package br.com.educare.api.lesson

/** Published (synchronously, inside the transaction) when a teacher moves a lesson directly. */
data class LessonTimeChanged(val lessonId: Long)

/** Published (synchronously, inside the transaction) when a lesson is canceled. */
data class LessonCanceled(val lessonId: Long)
