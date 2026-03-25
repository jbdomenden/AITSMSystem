package backend.repository

data class PagedResult<T>(
    val items: List<T>,
    val total: Long
)
